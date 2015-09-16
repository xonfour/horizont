package module.simplesync.control;

import framework.constants.GenericControlInterfaceCommands;
import framework.constants.ModuleRight;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.DatabaseException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.Port;
import framework.model.ProsumerPort;
import framework.model.event.DataElementEvent;
import framework.model.event.ProviderStateEvent;
import framework.model.event.type.LogEventLevelType;
import framework.model.type.DataElementType;
import framework.model.type.ModuleStateType;
import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.PersistentConfigurationHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import module.iface.AbstractProsumer;
import module.iface.DataElementEventListener;
import module.iface.ErrorCode;
import module.iface.Provider;
import module.simplesync.constants.SimpleSyncConstants;
import module.simplesync.model.SyncJob;
import module.simplesync.model.type.SyncJobType;

import org.apache.commons.io.IOUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import db.iface.ComponentConfigurationController;

/**
 * Module implementing a simple (well actually not so simple anymore) prosumer that tries to synchronize two file systems at connected provider in a smart way. 
 *
 * @author Stefan Werner
 */
public class SimpleSyncModule extends AbstractProsumer implements DataElementEventListener {

	// TODO: Move everything to SimpleSyncConstants.
	private static final String[] CONFIG_ELEMET_PATH = { "config" };
	private static final String CONFIG_PROP_KEY___COMPARE_CONTENT = "compare_content";
	private static final String CONFIG_PROP_KEY___SYNC_DELETE = "sync_delete";
	private static final String CONFIG_PROP_KEY___SYNC_ONLY_EXISTING_ON_2 = "sync_only_existing_on_2";
	private static final String CONFLICT_SUFFIX = "CONFLICT";
	private static final String DB_DOMAIN1 = SimpleSyncModule.PORT1_ID;
	private static final String DB_DOMAIN2 = SimpleSyncModule.PORT2_ID;
	private static final String DELETED_SUFFIX = "DELETED";
	private static final String DOMAIN_CONFIG = "config";
	private static final String PORT1_ID = "storage1";
	private static final String PORT2_ID = "storage2";
	private static final String[] TMP_STORAGE_BASEPATH = { "simple_sync_module_tmp" };
	private static final String TMPPORT_ID = "tmp_storage";

	private boolean compareContent = true;
	private PersistentConfigurationHelper config;
	private ExecutorService executor;
	private final int maxConcurrentTransfers = 10;
	private ProsumerPort port1;
	private boolean port1Connected = false;
	private boolean port1Ready = false;
	private ProsumerPort port2;
	private boolean port2Connected = false;
	private boolean port2Ready = false;
	private boolean running = false;
	private boolean started;
	private final ReentrantLock statusLock = new ReentrantLock(true);
	private boolean syncDelete = true;
	private final Runnable syncHandler = new Runnable() {

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					final SyncJob job = SimpleSyncModule.this.syncJobManager.take();
					executeJob(job);
				} catch (final InterruptedException e) {
					break;
				}
			}
		}
	};
	private final SyncJobManager syncJobManager = new SyncJobManager();
	private boolean syncOnlyExistingOn2 = false;
	private final LinkedList<Thread> syncThreads = new LinkedList<Thread>();
	private ProsumerPort tmpPort;
	private boolean tmpPortConnected = false;
	private boolean tmpPortReady = false;
	private boolean tmpStorageCleaned = false;

	/**
	 * Instantiates a new simple sync module.
	 *
	 * @param prosumerConnector the prosumer connector
	 * @param componentConfiguration the component configuration
	 * @param logConnector the log connector
	 */
	public SimpleSyncModule(final ProsumerConnector prosumerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(prosumerConnector, componentConfiguration, logConnector);
	}

	/**
	 * Checks and locks path. We only do one action on a given path at a time. Other actions are postponed.
	 *
	 * @param port the port
	 * @param path the path
	 * @return the result, 0 = OK, 1 = not OK
	 */
	private int checkAndLockPath(final ProsumerPort port, final String[] path) {
		int result = 1;
		try {
			final int internalResult = this.prosumerConnector.checkAndLock(port, path);
			if ((internalResult == 0) || (internalResult == ErrorCode.ENOSYS)) {
				result = 0;
			} else {
				this.logConnector.log(LogEventLevelType.WARNING, "unable to lock " + getPortPathString(port, path));
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.WARNING, "unable to lock " + getPortPathString(port, path));
		}
		return result;
	}

	/**
	 * Checks module rights.
	 *
	 * @return true, if OK
	 */
	private boolean checkRights() {
		int rights;
		try {
			rights = this.prosumerConnector.getOwnRights();
		} catch (final BrokerException e) {
			this.logConnector.log(e);
			return false;
		}
		final int requiredRights = ModuleRight.READ_DATA | ModuleRight.RECEIVE_EVENTS | ModuleRight.WRITE_DATA | ModuleRight.WRITE_DB;
		if ((rights & requiredRights) != requiredRights) {
			this.logConnector.log(LogEventLevelType.ERROR, "insufficient module rights");
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Checks module state.
	 */
	private void checkState() {
		this.statusLock.lock();
		if (this.port1Connected && this.port2Connected && this.port1Ready && this.port2Ready && this.started && !this.running) {
			this.running = true;
			realStartup();
		} else if ((!this.port1Connected || !this.port2Connected || !this.port1Ready || !this.port2Ready || !this.started) && this.running) {
			this.running = false;
			realShutdown();
		}
		this.statusLock.unlock();
	}

	/**
	 * Cleans up temporary storage (if set up).
	 */
	private void cleanUpTmpStorage() {
		this.statusLock.lock();
		try {
			this.prosumerConnector.delete(this.tmpPort, SimpleSyncModule.TMP_STORAGE_BASEPATH);
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.WARNING, "unable to clean (delete) temporary storage");
		}
		this.statusLock.unlock();
	}

	/**
	 * Compare of two elements.
	 *
	 * @param port1 the port1
	 * @param path1 the path1
	 * @param port2 the port2
	 * @param path2 the path2
	 * @return the result, 0 = equal, 1 = not equal, -1 = IO error
	 */
	private int compareContent(final ProsumerPort port1, final String[] path1, final ProsumerPort port2, final String[] path2) {
		int result = -1;
		InputStream in1 = null;
		InputStream in2 = null;
		try {
			in1 = this.prosumerConnector.readData(port1, path1);
			in2 = this.prosumerConnector.readData(port2, path2);
			if ((in1 != null) && (in2 != null)) {
				if (IOUtils.contentEquals(in1, in2)) {
					// TODO: This uses an internal buffer -> does not work without EOF -> own solution?
					// BTW: This is the ONLY remaining reason to keep Apache Commons IO dependency... ;)
					result = 0;
				} else {
					result = 1;
				}
			}
		} catch (IOException | BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
		} finally {
			if (in1 != null) {
				try {
					in1.close();
				} catch (final IOException e) {
					this.logConnector.log(e);
				}
			}
			if (in2 != null) {
				try {
					in2.close();
				} catch (final IOException e) {
					this.logConnector.log(e);
				}
			}
		}
		return result;
	}

	/**
	 * Copies an element.
	 *
	 * @param srcPort the source port
	 * @param srcPath the source path
	 * @param destPort the destination port
	 * @param destPath the destination path
	 * @param type the type
	 * @return true, if successful
	 */
	private boolean copyElement(final ProsumerPort srcPort, final String[] srcPath, final ProsumerPort destPort, final String[] destPath, final DataElementType type) {
		try {
			if (type == DataElementType.FILE) {
				final InputStream in = this.prosumerConnector.readData(srcPort, srcPath);
				final OutputStream out = this.prosumerConnector.writeData(destPort, destPath);
				if ((in != null) && (out != null)) {
					streamCopy(in, out);
					return true;
				} else {
					return false;
				}
			} else if (type == DataElementType.FOLDER) {
				this.prosumerConnector.createFolder(destPort, destPath);
			} else {
				// will retry later if type is unhandled
				return false;
			}
		} catch (IOException | BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return false;
		}
		return true;
	}

	/**
	 * Deletes children of given parent path in database.
	 *
	 * @param domain the domain
	 * @param parentPath the parent path
	 * @return true, if successful
	 */
	private boolean deleteDBChildren(final String domain, final String[] parentPath) {
		try {
			final Set<DataElement> children = this.componentConfiguration.getChildElements(domain, parentPath);
			if ((children == null) | children.isEmpty()) {
				return true;
			} else {
				for (final DataElement child : children) {
					this.componentConfiguration.deleteElement(domain, child.getPath());
				}
				return true;
			}
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Does a full recursive synchronization (best effort, may fail).
	 *
	 * @param port the port
	 * @param basePath the base path
	 * @return true, if successful
	 */
	private boolean doFullRecursiveSync(final ProsumerPort port, final String[] basePath) {
		try {
			Set<DataElement> realChildren;
			try {
				realChildren = this.prosumerConnector.getChildElements(port, basePath, false);
			} catch (final AuthorizationException e1) {
				this.logConnector.log(e1);
				return false;
			}

			if (realChildren != null) {
				try {
					final Set<DataElement> dbChildren = this.componentConfiguration.getChildElements(getDBDomain(port), basePath);

					if (dbChildren != null) {
						dbChildren.removeAll(realChildren);
						for (final DataElement dbChild : dbChildren) {
							final SyncJob job = new SyncJob(port, dbChild, SyncJobType.DELETE, false);
							this.syncJobManager.queueJob(job);
						}
					}
				} catch (IllegalArgumentException | DatabaseException e) {
					this.logConnector.log(e);
				}
				for (final DataElement realChild : realChildren) {
					if (realChild.getType() == DataElementType.FOLDER) {
						doFullRecursiveSync(port, realChild.getPath());
					}
					final SyncJob job = new SyncJob(port, realChild, SyncJobType.INIT, false);
					this.syncJobManager.queueJob(job);
				}
				return true;
			} else {
				return false;
			}
		} catch (BrokerException | ModuleException e) {
			this.logConnector.log(e);
			return false;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#shutdown() */
	@Override
	public void enterShutdown() {
		this.statusLock.lock();
		this.started = false;
		checkState();
		this.statusLock.unlock();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#startup() */
	@Override
	public void enterStartup() {
	}

	/**
	 * Executes synchronization job.
	 *
	 * @param job the job to execute
	 */
	private void executeJob(final SyncJob job) {
		final int result = sync(job);

		// error -> schedule for retry
		if (result == 1) {
			if (job.increaseRetryCount()) {
				this.syncJobManager.requeueJob(job);
			}
		} else if (result == 2) {
			job.postpone();
			this.syncJobManager.requeueJob(job);
		} else if (result == 0) {
			// done
			this.syncJobManager.removeJobFromProcessingList(job);
		} else if (result == -1) {
			// Invalid
			// TODO:: Send command signal/log?
			this.syncJobManager.removeJobFromProcessingList(job);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		this.port1Ready = false;
		this.port2Ready = false;
		this.tmpPortReady = false;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		this.executor.execute(new Runnable() {

			@Override
			public void run() {
				SimpleSyncModule.this.statusLock.lock();
				SimpleSyncModule.this.started = true;
				checkState();
				if (!SimpleSyncModule.this.running) {
					// if module is still not running request state from all connected providers to see if an event was missed
					requestState();
				}
				SimpleSyncModule.this.statusLock.unlock();
			}
		});
	}

	/**
	 * Gets a conflict suffix to move conflicting elements
	 *
	 * @param path the path
	 * @return the conflict suffix
	 */
	private String[] getConflictSuffix(final String[] path) {
		final String[] newPath = Arrays.copyOf(path, path.length);
		newPath[path.length - 1] += "___" + SimpleSyncModule.CONFLICT_SUFFIX + "_" + UUID.randomUUID().toString() + "___" + String.valueOf(System.currentTimeMillis());
		return newPath;
	}

	/**
	 * Gets the corresponding database domain for a given port.
	 *
	 * @param port the port
	 * @return the DB domain
	 */
	private String getDBDomain(final ProsumerPort port) {
		if (port == this.port1) {
			return SimpleSyncModule.DB_DOMAIN1;
		} else {
			return SimpleSyncModule.DB_DOMAIN2;
		}
	}

	/**
	 * Gets the deleted suffix to mark files as deleted.
	 *
	 * @param path the path
	 * @return the deleted suffix
	 */
	private String[] getDeletedSuffix(final String[] path) {
		final String[] newPath = Arrays.copyOf(path, path.length);
		newPath[path.length - 1] += "___" + SimpleSyncModule.DELETED_SUFFIX + "_" + UUID.randomUUID().toString() + "___" + String.valueOf(System.currentTimeMillis());
		return newPath;
	}

	/**
	 * Gets the other port.
	 *
	 * @param port the port
	 * @return the other port
	 */
	private ProsumerPort getOtherPort(final ProsumerPort port) {
		if (port == this.port1) {
			return this.port2;
		} else {
			return this.port1;
		}
	}

	/**
	 * Gets the port path string. Used internally.
	 * <p>
	 * TODO: Do we really need this?
	 *
	 * @param port the port
	 * @param path the path
	 * @return the port path string
	 */
	private String getPortPathString(final ProsumerPort port, final String[] path) {
		String result = port.getPortId() + ":";
		for (int i = 0; i < (path.length - 1); i++) {
			result += path[i] + "/";
		}
		result += path[path.length - 1];
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return ImmutableSet.copyOf(GenericControlInterfaceCommands.DEFAULT_SUPPORT___CONFIG);
	}

	@Override
	public Set<String> getSupportedModuleCommands(final Port port, final String[] path) {
		if (port instanceof ProsumerPort) {
			try {
				final DataElement elem = this.prosumerConnector.getElement((ProsumerPort) port, path);
				if (elem.getType() == DataElementType.FILE) {
					return Sets.newHashSet(SimpleSyncConstants.SUPPORTED_MODULE_COMMANDS_FILES);
				} else {
					return null;
				}
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * Gets a temporary storage path.
	 *
	 * @return the temporary storage path
	 */
	private String[] getTmpStoragePath() {
		this.statusLock.lock();
		if (this.tmpPortConnected && this.tmpPortReady) {
			final String filename = System.currentTimeMillis() + "_" + UUID.randomUUID().toString();
			final String[] result = new String[SimpleSyncModule.TMP_STORAGE_BASEPATH.length + 1];
			System.arraycopy(SimpleSyncModule.TMP_STORAGE_BASEPATH, 0, result, 0, SimpleSyncModule.TMP_STORAGE_BASEPATH.length);
			result[result.length - 1] = filename;
			this.statusLock.unlock();
			return result;
		} else {
			this.statusLock.unlock();
			return null;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		try {
			this.config = new PersistentConfigurationHelper(this.componentConfiguration, SimpleSyncModule.DOMAIN_CONFIG, SimpleSyncModule.CONFIG_ELEMET_PATH);
		} catch (IllegalArgumentException | DatabaseException e1) {
			this.logConnector.log(e1);
			this.config = new PersistentConfigurationHelper();
		}
		String threadNamePrefix = "?";
		try {
			threadNamePrefix = this.componentConfiguration.getComponentName() + "-" + this.getClass().getSimpleName() + "-%d";
		} catch (final DatabaseException e) {
			this.logConnector.log(e);
		}
		this.executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(threadNamePrefix).build());
		this.syncDelete = this.config.getBoolean(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_DELETE, this.syncDelete);
		this.compareContent = this.config.getBoolean(SimpleSyncModule.CONFIG_PROP_KEY___COMPARE_CONTENT, this.compareContent);
		try {
			this.componentConfiguration.initializeElementDomains(SimpleSyncModule.DB_DOMAIN1, SimpleSyncModule.DB_DOMAIN2);
			this.port1 = this.prosumerConnector.registerProsumerPort(this, SimpleSyncModule.PORT1_ID, 1);
			this.port2 = this.prosumerConnector.registerProsumerPort(this, SimpleSyncModule.PORT2_ID, 1);
			this.tmpPort = this.prosumerConnector.registerProsumerPort(this, SimpleSyncModule.TMPPORT_ID, 1);
		} catch (BrokerException | IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			return;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return checkRights();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return null;
		}
		if (command.equals(GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES)) {
			final ConfigValue configValueCompCont = new ConfigValue(SimpleSyncModule.CONFIG_PROP_KEY___COMPARE_CONTENT);
			configValueCompCont.setCurrentValueBoolean(this.compareContent);
			configValueCompCont.setDescriptionString("Try to compare content if both sides outdated.");
			final ConfigValue configValueSyncDel = new ConfigValue(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_DELETE);
			configValueSyncDel.setCurrentValueBoolean(this.syncDelete);
			configValueSyncDel.setDescriptionString("Synchronize deletes.");
			final ConfigValue configValueOnlyExisting = new ConfigValue(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_ONLY_EXISTING_ON_2);
			configValueOnlyExisting.setCurrentValueBoolean(this.syncOnlyExistingOn2);
			configValueOnlyExisting.setDescriptionString("Only synchronize elements that already exist on storage2.");
			return CommandResultHelper.getDefaultResultOk(SimpleSyncModule.CONFIG_PROP_KEY___COMPARE_CONTENT, configValueCompCont.toString(), SimpleSyncModule.CONFIG_PROP_KEY___SYNC_DELETE, configValueSyncDel.toString(), SimpleSyncModule.CONFIG_PROP_KEY___SYNC_ONLY_EXISTING_ON_2, configValueOnlyExisting.toString());
		} else if (command.equals(GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES) && (properties != null)) {
			boolean result = false;
			final ConfigValue configValueCompCont = new ConfigValue(SimpleSyncModule.CONFIG_PROP_KEY___COMPARE_CONTENT, properties.get(SimpleSyncModule.CONFIG_PROP_KEY___COMPARE_CONTENT));
			if (configValueCompCont.isValid()) {
				this.compareContent = configValueCompCont.getCurrentValueBoolean();
				this.config.updateBoolean(SimpleSyncModule.CONFIG_PROP_KEY___COMPARE_CONTENT, this.compareContent);
				result = true;
			}
			final ConfigValue configValueSyncDel = new ConfigValue(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_DELETE, properties.get(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_DELETE));
			if (configValueSyncDel.isValid()) {
				this.syncDelete = configValueSyncDel.getCurrentValueBoolean();
				this.config.updateBoolean(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_DELETE, this.syncDelete);
				result = true;
			}
			final ConfigValue configValueOnlyExisting = new ConfigValue(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_ONLY_EXISTING_ON_2, properties.get(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_ONLY_EXISTING_ON_2));
			if (configValueOnlyExisting.isValid()) {
				this.syncOnlyExistingOn2 = configValueOnlyExisting.getCurrentValueBoolean();
				this.config.updateBoolean(SimpleSyncModule.CONFIG_PROP_KEY___SYNC_ONLY_EXISTING_ON_2, this.syncOnlyExistingOn2);
				result = true;
			}
			if (result) {
				return CommandResultHelper.getDefaultResultOk();
			}
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	@Override
	public void onElementEvent(final ProsumerPort port, final DataElementEvent event) {
		final DataElement element = event.dataElement;
		SyncJob job;
		switch (event.eventType) {
		case ADD:
			job = new SyncJob(port, element, SyncJobType.ADD, true);
			this.syncJobManager.queueJob(job);
			break;
		case DELETE:
			job = new SyncJob(port, element, SyncJobType.DELETE, true);
			this.syncJobManager.queueJob(job);
			break;
		case MODIFY:
			job = new SyncJob(port, element, SyncJobType.MODIFY, true);
			this.syncJobManager.queueJob(job);
			break;
		}
	}

	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return null;
		}
		if (port instanceof ProsumerPort) {
			try {
				final ProsumerPort prosumerPort = (ProsumerPort) port;
				final DataElement elem = this.prosumerConnector.getElement(prosumerPort, path);
				if (elem != null) {
					if (elem.getType() == DataElementType.FILE) {
						final SyncJob job = new SyncJob(prosumerPort, elem, SyncJobType.FORCE_TRANSFER, true);
						this.syncJobManager.queueJob(job);
						return CommandResultHelper.getDefaultResultOk();
					} else {
						return CommandResultHelper.getDefaultResultFail(SimpleSyncConstants.RESULT___FAIL_REASON, SimpleSyncConstants.RESULT___FAIL_REASON___NOT_A_FILE);
					}
				} else {
					return CommandResultHelper.getDefaultResultFail(SimpleSyncConstants.RESULT___FAIL_REASON, SimpleSyncConstants.RESULT___FAIL_REASON___ELEMENT_NOT_FOUND);
				}
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return CommandResultHelper.getDefaultResultFail(SimpleSyncConstants.RESULT___FAIL_REASON, SimpleSyncConstants.RESULT___FAIL_REASON___READ_ERROR);
			}
		} else {
			return CommandResultHelper.getDefaultResultFail();
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#notifyConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		this.statusLock.lock();
		if (port == this.port1) {
			this.port1Connected = true;
		} else if (port == this.port2) {
			this.port2Connected = true;
		} else if (port == this.tmpPort) {
			this.tmpPortConnected = true;
		}
		checkState();
		this.statusLock.unlock();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#notifyDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		this.statusLock.lock();
		if (port == this.port1) {
			this.port1Connected = false;
		} else if (port == this.port2) {
			this.port2Connected = false;
		} else if (port == this.tmpPort) {
			this.tmpPortConnected = false;
		}
		checkState();
		this.statusLock.unlock();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onConnectedModuleStateChangeEvent(framework.model.Port, framework.model.type.ModuleStateEventType) */
	@Override
	public void onProviderStateEvent(final Port port, final ProviderStateEvent event) {
		this.statusLock.lock();
		final int moduleState = event.state;
		final boolean ready = (moduleState & ModuleStateType.READY) == ModuleStateType.READY;
		if ((port == this.port1) && !ready) {
			this.port1Ready = false;
		} else if ((port == this.port1) && ready) {
			this.port1Ready = true;
		} else if ((port == this.port2) && !ready) {
			this.port2Ready = false;
		} else if ((port == this.port2) && ready) {
			this.port2Ready = true;
		} else if ((port == this.tmpPort) && !ready) {
			this.tmpPortReady = false;
			this.tmpStorageCleaned = false;
		} else if ((port == this.tmpPort) && ready) {
			this.tmpPortReady = true;
			if (!this.tmpStorageCleaned) {
				cleanUpTmpStorage();
				this.tmpStorageCleaned = true;
			}
		}
		checkState();
		this.statusLock.unlock();
	}

	/**
	 * Does the real shutdown.
	 */
	private void realShutdown() {
		try {
			this.prosumerConnector.unsubscribeAll(this.port1);
			this.prosumerConnector.unsubscribeAll(this.port2);
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
		}
		setMaxConcurrentTransfers(0);
		this.syncJobManager.stop();
	}

	/**
	 * Does the real startup when this module and connected modules are ready.
	 */
	private void realStartup() {
		final String[] rootPath = {};
		try {
			this.prosumerConnector.subscribe(this.port1, rootPath, true, this);
			this.prosumerConnector.subscribe(this.port2, rootPath, true, this);
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
		}
		if (!doFullRecursiveSync(this.port1, new String[0]) || !doFullRecursiveSync(this.port2, new String[0])) {
			this.logConnector.log(LogEventLevelType.WARNING, "unable to run full initial recursive sync on one or more ports");
		}
		this.syncJobManager.start();
		setMaxConcurrentTransfers(this.maxConcurrentTransfers);
	}

	/**
	 * Requests state from connected modules.
	 */
	private void requestState() {
		try {
			if (this.prosumerConnector.isConnected(this.port1)) {
				this.prosumerConnector.requestConnectedProviderStatus(this.port1);
			}
		} catch (BrokerException | ModuleException e) {
			this.logConnector.log(e);
		}
		try {
			if (this.prosumerConnector.isConnected(this.port2)) {
				this.prosumerConnector.requestConnectedProviderStatus(this.port2);
			}
		} catch (BrokerException | ModuleException e) {
			this.logConnector.log(e);
		}
		try {
			if (this.prosumerConnector.isConnected(this.tmpPort)) {
				this.prosumerConnector.requestConnectedProviderStatus(this.tmpPort);
			}
		} catch (BrokerException | ModuleException e) {
			this.logConnector.log(e);
		}
	}

	/**
	 * Sets the maximum number of concurrent transfers.
	 *
	 * @param max the new maximum number of concurrent transfers
	 */
	private void setMaxConcurrentTransfers(final int max) {
		if (this.syncThreads.size() > max) {
			final int diff = this.syncThreads.size() - max;
			for (int i = 0; i < diff; i++) {
				final Thread t = this.syncThreads.poll();
				if (t != null) {
					t.interrupt();
				}
			}
		} else if (this.syncThreads.size() < max) {
			final int diff = max - this.syncThreads.size();
			for (int i = 0; i < diff; i++) {
				final Thread t = new Thread(this.syncHandler);
				this.syncThreads.add(t);
				t.start();
			}
		}
	}

	/**
	 * Copies two streams.
	 *
	 * @param in the input stream to read from
	 * @param out the output stream to write to
	 * @throws IOException if an I/O exception has occurred
	 * @throws BrokerException if streams get interrupted by broker
	 * @throws ModuleException on another error
	 * @throws AuthorizationException if module is not authorized to read/write data
	 */
	private void streamCopy(final InputStream in, final OutputStream out) throws IOException, BrokerException, ModuleException, AuthorizationException {
		final String[] tmpPath = getTmpStoragePath();

		if (tmpPath != null) {
			// a temporary storage is available -> use it
			try {
				final OutputStream tmpOut = this.prosumerConnector.writeData(this.tmpPort, tmpPath);
				ByteStreams.copy(in, tmpOut);
				try {
					in.close();
				} catch (final IOException e) {
					this.logConnector.log(e);
				}
				try {
					tmpOut.flush();
					tmpOut.close();
				} catch (final IOException e) {
					this.logConnector.log(e);
				}
				final InputStream tmpIn = this.prosumerConnector.readData(this.tmpPort, tmpPath);
				ByteStreams.copy(tmpIn, out);
				try {
					tmpIn.close();
				} catch (final IOException e) {
					this.logConnector.log(e);
				}
				try {
					out.flush();
					out.close();
				} catch (final IOException e) {
					this.logConnector.log(e);
				}
			} finally {
				// always try to delete tmp file
				try {
					this.prosumerConnector.delete(this.tmpPort, tmpPath);
				} catch (BrokerException | ModuleException e) {
					this.logConnector.log(e);
				}
			}
		} else {
			// no temporary storage -> direct copy
			ByteStreams.copy(in, out);
			try {
				in.close();
			} catch (final IOException e) {
				this.logConnector.log(e);
			}
			try {
				out.flush();
				out.close();
			} catch (final IOException e) {
				this.logConnector.log(e);
			}
		}
	}

	/**
	 * The location where the core synchronization is done. Here we decide what to do for each synchronization job based on data inside the job and updated data
	 * from connected modules.
	 * <p>
	 * TODO: Wow, 675 lines of code, no doubt, this IS a monster. Even with commends it is hard to maintain. Cut it down, introduce stable intermediate states.
	 *
	 * @param job the job
	 * @return the result, 0 = OK, 1 = retry later (wait an more and more increasing interval), 2 = postpone for a few seconds, -1 = invalid/outdated
	 */
	private int sync(final SyncJob job) {
		final ProsumerPort srcPort = job.getSourcePort();
		DataElement srcElement = job.getElement();
		int result = 1;

		// try to lock source element
		final int srcLockResult = checkAndLockPath(srcPort, srcElement.getPath());
		// already locked or other error -> retry later
		if (srcLockResult > 0) {
			this.logConnector.log(LogEventLevelType.DEBUG, "already locked or other error -> retry: " + srcElement.toString());
			return 1;
		}

		// refresh source element
		DataElement providerSrcElement = null;
		try {
			providerSrcElement = this.prosumerConnector.getElement(srcPort, srcElement.getPath());
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.DEBUG, "refresh error -> retry: " + srcElement.toString());
			if (srcLockResult == 0) {
				unlock(srcPort, srcElement.getPath());
			}
			return 1;
		}

		// still in external modification? -> if job is from notification we will check again later!
		if ((providerSrcElement != null) && job.isNotificationJob() && (providerSrcElement.getType() == DataElementType.FILE) && (srcElement.getType() == DataElementType.FILE) && !providerSrcElement.equals(srcElement)) {
			this.logConnector.log(LogEventLevelType.DEBUG, "external modification in progress -> postpone: " + srcElement.toString());
			job.setElement(providerSrcElement);
			if (srcLockResult == 0) {
				unlock(srcPort, srcElement.getPath());
			}
			return 2;
		}

		// get corresponding source database element
		DataElement dbSrcElem = null;
		try {
			dbSrcElem = this.componentConfiguration.getElement(getDBDomain(srcPort), srcElement.getPath());
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}

		final ProsumerPort destPort = getOtherPort(srcPort);

		// 1) neither provider element nor database entry exists -> invalid request
		if ((providerSrcElement == null) && (dbSrcElem == null)) {
			result = -1;

			// 2) current source provider element's attributes are known and up to date -> nothing to do
		} else if ((job.getType() != SyncJobType.FORCE_TRANSFER) && (providerSrcElement != null) && (dbSrcElem != null) && providerSrcElement.equals(dbSrcElem)) {
			result = 0;

			// 3) provider element has been updated for transfer is forced -> sync
		} else if ((providerSrcElement != null) && ((job.getType() == SyncJobType.FORCE_TRANSFER) || (dbSrcElem == null) || !providerSrcElement.equals(dbSrcElem))) {

			// try to lock destination element
			int destLockResult = -1;
			try {
				destLockResult = this.prosumerConnector.checkAndLock(destPort, srcElement.getPath());
				// already locked or other error -> retry later
				if ((destLockResult != 0) && (destLockResult != ErrorCode.ENOSYS)) {
					this.logConnector.log(LogEventLevelType.DEBUG, "already locked or other error -> retry: " + srcElement.toString());
					if (srcLockResult == 0) {
						unlock(srcPort, srcElement.getPath());
					}
					return 1;
				}
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				this.logConnector.log(LogEventLevelType.DEBUG, "lock error -> retry: " + srcElement.toString());
				if (srcLockResult == 0) {
					unlock(srcPort, srcElement.getPath());
				}
				return 1;
			}

			// try to get destination element
			DataElement providerDestElement = null;
			try {
				providerDestElement = this.prosumerConnector.getElement(destPort, srcElement.getPath());
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
			}

			// stop if element does not (yet) exist on storage 2 and we only synchronize if it does
			if (this.syncOnlyExistingOn2 && (destPort == this.port2) && (providerDestElement == null)) {
				if (destLockResult == 0) {
					unlock(destPort, srcElement.getPath());
				}
				if (srcLockResult == 0) {
					unlock(srcPort, srcElement.getPath());
				}
				return 0;
			}

			// get corresponding destination database element
			DataElement dbDestElem = null;
			try {
				dbDestElem = this.componentConfiguration.getElement(getDBDomain(destPort), srcElement.getPath());
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}

			// 3.1/2) FILES: destination does not exist OR current destination provider element's attributes are known and up to date and types are equal ->
			// copy
			// FOLDERS: if destination does not exist -> create, -> update db
			if ((providerDestElement == null) || ((dbDestElem != null) && providerDestElement.equals(dbDestElem) && (providerSrcElement.getType() == providerDestElement.getType()))) {
				if ((providerSrcElement.getType() == DataElementType.FILE) && copyElement(srcPort, srcElement.getPath(), destPort, srcElement.getPath(), providerSrcElement.getType())) {
					try {
						// refresh destination element and store attributes in database
						providerDestElement = this.prosumerConnector.getElement(destPort, srcElement.getPath());
						this.componentConfiguration.storeElement(getDBDomain(destPort), srcElement.getPath(), providerDestElement);

						// refresh source element and store attributes in database
						providerSrcElement = this.prosumerConnector.getElement(srcPort, srcElement.getPath());
						if ((dbSrcElem != null) && (dbSrcElem.getType() == DataElementType.FOLDER) && (providerSrcElement.getType() == DataElementType.FILE)) {
							deleteDBChildren(getDBDomain(srcPort), srcElement.getPath());
						}
						this.componentConfiguration.storeElement(getDBDomain(srcPort), srcElement.getPath(), providerSrcElement);
					} catch (IllegalArgumentException | DatabaseException e1) {
						this.logConnector.log(e1);
					} catch (BrokerException | ModuleException | AuthorizationException e2) {
						this.logConnector.log(e2);
						if (destLockResult == 0) {
							unlock(destPort, srcElement.getPath());
						}
						if (srcLockResult == 0) {
							unlock(srcPort, srcElement.getPath());
						}
						return 1;
					}
					result = 0;
				} else if (providerSrcElement.getType() == DataElementType.FOLDER) {
					if (providerDestElement == null) {
						try {
							final int i = this.prosumerConnector.createFolder(destPort, srcElement.getPath());
							if (i == Provider.RESULT_CODE___OK) {
								// refresh destination element and store attributes in database
								providerDestElement = this.prosumerConnector.getElement(destPort, srcElement.getPath());
								this.componentConfiguration.storeElement(getDBDomain(destPort), srcElement.getPath(), providerDestElement);
								doFullRecursiveSync(srcPort, srcElement.getPath());
							}
						} catch (IllegalArgumentException | DatabaseException e1) {
							this.logConnector.log(e1);
						} catch (BrokerException | ModuleException | AuthorizationException e) {
							this.logConnector.log(e);
							if (destLockResult == 0) {
								unlock(destPort, srcElement.getPath());
							}
							if (srcLockResult == 0) {
								unlock(srcPort, srcElement.getPath());
							}
							return 1;
						}
					}

					try {
						// refresh source element and store attributes in database
						providerSrcElement = this.prosumerConnector.getElement(srcPort, srcElement.getPath());
						this.componentConfiguration.storeElement(getDBDomain(srcPort), srcElement.getPath(), providerSrcElement);
					} catch (IllegalArgumentException | DatabaseException e1) {
						this.logConnector.log(e1);
					} catch (BrokerException | ModuleException | AuthorizationException e2) {
						this.logConnector.log(e2);
						if (destLockResult == 0) {
							unlock(destPort, srcElement.getPath());
						}
						if (srcLockResult == 0) {
							unlock(srcPort, srcElement.getPath());
						}
						return 1;
					}
					result = 0;
				}

				// 3.3) destination does exist but is either unknown or modified or different type -> conflict
			} else if ((providerDestElement != null) && (((dbDestElem == null) || !providerDestElement.equals(dbDestElem)) || (providerSrcElement.getType() != providerDestElement.getType()))) {

				// for files: if destination element is unknown and content equals the content of source element -> just update db
				boolean done = false;
				if ((dbDestElem == null) && (providerSrcElement.getType() == DataElementType.FILE) && (providerDestElement.getType() == DataElementType.FILE)) {
					if (this.compareContent) {
						final int i = compareContent(srcPort, srcElement.getPath(), destPort, srcElement.getPath());
						if (i == -1) {
							this.logConnector.log(LogEventLevelType.DEBUG, "read error while comparing content -> retry: " + srcElement);
							if (destLockResult == 0) {
								unlock(destPort, srcElement.getPath());
							}
							if (srcLockResult == 0) {
								unlock(srcPort, srcElement.getPath());
							}
							return 1;
						} else if (i == 0) {
							done = true;
						}
					}
				} else if ((providerSrcElement.getType() == DataElementType.FOLDER) && (providerDestElement.getType() == DataElementType.FOLDER)) {
					done = true;
				}

				if (done) {
					try {
						this.componentConfiguration.storeElement(getDBDomain(srcPort), srcElement.getPath(), providerSrcElement);
						this.componentConfiguration.storeElement(getDBDomain(destPort), srcElement.getPath(), providerDestElement);
						result = 0;
						done = true;
					} catch (IllegalArgumentException | DatabaseException e) {
						this.logConnector.log(e);
						if (destLockResult == 0) {
							unlock(destPort, srcElement.getPath());
						}
						if (srcLockResult == 0) {
							unlock(srcPort, srcElement.getPath());
						}
						return 1;
					}
				}

				if (!done) {

					// newer version keeps its name, older version gets renamed
					// decide which is which
					ProsumerPort newerVersionPort;
					DataElement newerVersionElement;
					ProsumerPort olderVersionPort;
					DataElement oldVersionElement;

					// both elements are unknown -> compare absolute modification dates to find newer version (not accurate but sufficient in this case)
					if ((dbSrcElem == null) && (dbDestElem == null)) {
						final long diff = providerSrcElement.getModificationDate() - providerDestElement.getModificationDate();

						// source is newer
						if (diff > 0) {
							newerVersionPort = srcPort;
							newerVersionElement = providerSrcElement;
							olderVersionPort = destPort;
							oldVersionElement = providerDestElement;

							// destination is newer
						} else {
							newerVersionPort = destPort;
							newerVersionElement = providerDestElement;
							olderVersionPort = srcPort;
							oldVersionElement = providerSrcElement;
						}

						// source element is unknown, destination element is known -> source is newer
					} else if (dbSrcElem == null) {
						newerVersionPort = srcPort;
						newerVersionElement = providerSrcElement;
						olderVersionPort = destPort;
						oldVersionElement = providerDestElement;

						// destination element is unknown, source element is known -> destination is newer
					} else if (dbDestElem == null) {
						newerVersionPort = destPort;
						newerVersionElement = providerDestElement;
						olderVersionPort = srcPort;
						oldVersionElement = providerSrcElement;

						// both elements are known -> compare modification dates relative to database elements
					} else {
						final long srcDiff = providerSrcElement.getModificationDate() - dbSrcElem.getModificationDate();
						final long destDiff = providerDestElement.getModificationDate() - dbDestElem.getModificationDate();

						// source is newer or has the same delta
						if (srcDiff >= destDiff) {
							newerVersionPort = srcPort;
							newerVersionElement = providerSrcElement;
							olderVersionPort = destPort;
							oldVersionElement = providerDestElement;

							// destination is newer
						} else {
							newerVersionPort = destPort;
							newerVersionElement = providerDestElement;
							olderVersionPort = srcPort;
							oldVersionElement = providerSrcElement;
						}
					}

					final String[] conflictPath = getConflictSuffix(srcElement.getPath());

					// try to lock conflicting element
					final int conflictOVLockResult = checkAndLockPath(olderVersionPort, conflictPath);
					// already locked or other error -> retry later
					if (conflictOVLockResult > 0) {
						this.logConnector.log(LogEventLevelType.DEBUG, "already locked or other error -> retry: " + getPortPathString(olderVersionPort, conflictPath));
						if (destLockResult == 0) {
							unlock(destPort, srcElement.getPath());
						}
						if (srcLockResult == 0) {
							unlock(srcPort, srcElement.getPath());
						}
						this.syncJobManager.unlockPath(conflictPath);
						return 1;
					}

					// try to lock conflicting element
					final int conflictNVLockResult = checkAndLockPath(newerVersionPort, conflictPath);
					// already locked or other error -> retry later
					if (conflictNVLockResult > 0) {
						this.logConnector.log(LogEventLevelType.DEBUG, "already locked or other error -> retry: " + getPortPathString(newerVersionPort, conflictPath));
						if (destLockResult == 0) {
							unlock(destPort, srcElement.getPath());
						}
						if (srcLockResult == 0) {
							unlock(srcPort, srcElement.getPath());
						}
						if (conflictOVLockResult == 0) {
							unlock(olderVersionPort, conflictPath);
						}
						this.syncJobManager.unlockPath(conflictPath);
						return 1;
					}

					try {
						// 1: move older version
						if (this.prosumerConnector.move(olderVersionPort, srcElement.getPath(), conflictPath) == 0) {
							try {
								this.componentConfiguration.moveElement(getDBDomain(olderVersionPort), srcElement.getPath(), getDBDomain(olderVersionPort), conflictPath);
							} catch (IllegalArgumentException | DatabaseException e1) {
								this.logConnector.log(e1);
								// move action completed - even if database cannot be written
							}
							// success -> 2: FILES: copy newer version, FOLDERS: create and run recursive sync on it
							boolean createCopyResult = false;
							if (newerVersionElement.getType() == DataElementType.FILE) {
								createCopyResult = copyElement(newerVersionPort, srcElement.getPath(), olderVersionPort, srcElement.getPath(), newerVersionElement.getType());
							} else if (newerVersionElement.getType() == DataElementType.FOLDER) {
								try {
									final int i = this.prosumerConnector.createFolder(olderVersionPort, srcElement.getPath());
									if (i == Provider.RESULT_CODE___OK) {
										// refresh destination element and store attributes in database
										oldVersionElement = this.prosumerConnector.getElement(olderVersionPort, srcElement.getPath());
										this.componentConfiguration.storeElement(getDBDomain(olderVersionPort), srcElement.getPath(), oldVersionElement);
										doFullRecursiveSync(newerVersionPort, srcElement.getPath());
									}
								} catch (IllegalArgumentException | DatabaseException e1) {
									this.logConnector.log(e1);
								} catch (BrokerException | ModuleException e) {
									this.logConnector.log(e);
									if (destLockResult == 0) {
										unlock(destPort, srcElement.getPath());
									}
									if (srcLockResult == 0) {
										unlock(srcPort, srcElement.getPath());
									}
									if (conflictOVLockResult == 0) {
										unlock(olderVersionPort, conflictPath);
									}
									if (conflictNVLockResult == 0) {
										unlock(newerVersionPort, conflictPath);
									}
									this.syncJobManager.unlockPath(conflictPath);
									return 1;
								}
							}
							if (createCopyResult) {
								try {
									// refresh older element and store attributes in database
									final DataElement oldProviderDestElement = this.prosumerConnector.getElement(olderVersionPort, srcElement.getPath());
									this.componentConfiguration.storeElement(getDBDomain(olderVersionPort), srcElement.getPath(), oldProviderDestElement);

									// refresh newer element and store attributes in database
									final DataElement newProviderSrcElement = this.prosumerConnector.getElement(newerVersionPort, srcElement.getPath());
									this.componentConfiguration.storeElement(getDBDomain(newerVersionPort), srcElement.getPath(), newProviderSrcElement);
								} catch (IllegalArgumentException | DatabaseException e) {
									this.logConnector.log(e);
									// sync action completed - even if database cannot be written
								}
							}
							// success -> 3: FILES: copy back conflicting old version with new name, FOLDERS: create and run recursive sync on it
							if (oldVersionElement.getType() == DataElementType.FILE) {
								createCopyResult = createCopyResult && copyElement(olderVersionPort, conflictPath, newerVersionPort, conflictPath, oldVersionElement.getType());
								if (createCopyResult) {
									try {
										// refresh conflict destination element and store attributes in database
										final DataElement conflictProviderDestElement = this.prosumerConnector.getElement(newerVersionPort, conflictPath);
										this.componentConfiguration.storeElement(getDBDomain(newerVersionPort), conflictPath, conflictProviderDestElement);

										// refresh conflict source element and store attributes in database
										final DataElement conflictProviderSrcElement = this.prosumerConnector.getElement(olderVersionPort, conflictPath);
										this.componentConfiguration.storeElement(getDBDomain(olderVersionPort), conflictPath, conflictProviderSrcElement);

									} catch (IllegalArgumentException | DatabaseException e) {
										this.logConnector.log(e);
										// sync action completed - even if database cannot be written
									}
									result = 0;
								}
							} else if (oldVersionElement.getType() == DataElementType.FOLDER) {
								try {
									final int i = this.prosumerConnector.createFolder(newerVersionPort, conflictPath);
									if (i == Provider.RESULT_CODE___OK) {
										// refresh destination element and store attributes in database
										newerVersionElement = this.prosumerConnector.getElement(newerVersionPort, conflictPath);
										this.componentConfiguration.storeElement(getDBDomain(newerVersionPort), conflictPath, newerVersionElement);
										doFullRecursiveSync(olderVersionPort, conflictPath);
									}
								} catch (IllegalArgumentException | DatabaseException | BrokerException | ModuleException e) {
									this.logConnector.log(e);
									// sync action completed - even if database cannot be written
								}
								result = 0;
							}
						}
					} catch (BrokerException | ModuleException | AuthorizationException e) {
						this.logConnector.log(e);
						result = 1;
					}

					if (conflictOVLockResult == 0) {
						unlock(olderVersionPort, conflictPath);
					}
					if (conflictNVLockResult == 0) {
						unlock(newerVersionPort, conflictPath);
					}

					this.syncJobManager.unlockPath(conflictPath);
				}

				// 3.4) every other request is invalid (should never happen)
			} else {
				result = -1;
			}

			if (destLockResult == 0) {
				unlock(destPort, srcElement.getPath());
			}

			// 4) provider element has been deleted -> sync delete
		} else if ((providerSrcElement == null) && (dbSrcElem != null)) {

			// get corresponding destination database element
			DataElement dbDestElem = null;
			try {
				dbDestElem = this.componentConfiguration.getElement(getDBDomain(destPort), srcElement.getPath());
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}

			if (this.syncDelete) {
				// try to lock destination element
				int destLockResult = -1;
				try {
					destLockResult = this.prosumerConnector.checkAndLock(destPort, srcElement.getPath());
					// already locked or other error -> retry later
					if ((destLockResult != 0) && (destLockResult != ErrorCode.ENOSYS)) {
						this.logConnector.log(LogEventLevelType.DEBUG, "already locked or other error -> retry: " + srcElement.toString());
						if (srcLockResult == 0) {
							unlock(srcPort, srcElement.getPath());
						}
						return 1;
					}
				} catch (BrokerException | ModuleException | AuthorizationException e) {
					this.logConnector.log(e);
					this.logConnector.log(LogEventLevelType.DEBUG, "lock error -> retry: " + srcElement.toString());
					if (srcLockResult == 0) {
						unlock(srcPort, srcElement.getPath());
					}
					return 1;
				}

				// try to get destination element
				DataElement providerDestElement = null;
				try {
					providerDestElement = this.prosumerConnector.getElement(destPort, srcElement.getPath());
				} catch (BrokerException | ModuleException | AuthorizationException e) {
					this.logConnector.log(e);
					if (destLockResult == 0) {
						unlock(destPort, srcElement.getPath());
					}
					if (srcLockResult == 0) {
						unlock(srcPort, srcElement.getPath());
					}
					return 1;
				}

				// stop if element does not (yet) exist on storage 2 and we only synchronize if it does
				if (this.syncOnlyExistingOn2 && (destPort == this.port2) && (providerDestElement == null)) {
					if (destLockResult == 0) {
						unlock(destPort, srcElement.getPath());
					}
					if (srcLockResult == 0) {
						unlock(srcPort, srcElement.getPath());
					}
					return 0;
				}

				// 4.1) destination element exists -> check
				if (providerDestElement != null) {
					// 4.1.1) destination element is unmodified or folder and empty -> delete
					boolean emptyFolder = false;
					if (providerDestElement.getType() == DataElementType.FOLDER) {
						try {
							final Set<DataElement> children = this.prosumerConnector.getChildElements(destPort, srcElement.getPath(), false);
							if ((children == null) || children.isEmpty()) {
								emptyFolder = true;
							}
						} catch (BrokerException | ModuleException | AuthorizationException e) {
							this.logConnector.log(e);
							if (destLockResult == 0) {
								unlock(destPort, srcElement.getPath());
							}
							if (srcLockResult == 0) {
								unlock(srcPort, srcElement.getPath());
							}
							return 1;
						}
					}

					if (((dbDestElem != null) && providerDestElement.equals(dbDestElem)) || emptyFolder) {
						try {
							final int i = this.prosumerConnector.delete(destPort, srcElement.getPath());
							if ((i == Provider.RESULT_CODE___OK) || (i == Provider.RESULT_CODE___INVALID_READONLY)) {
								result = 0;
							} else if (i > Provider.RESULT_CODE___OK) {
								if (destLockResult == 0) {
									unlock(destPort, srcElement.getPath());
								}
								if (srcLockResult == 0) {
									unlock(srcPort, srcElement.getPath());
								}
								return 1;
							}
						} catch (BrokerException | ModuleException | AuthorizationException e) {
							this.logConnector.log(e);
							if (destLockResult == 0) {
								unlock(destPort, srcElement.getPath());
							}
							if (srcLockResult == 0) {
								unlock(srcPort, srcElement.getPath());
							}
							return 1;
						}

						// 4.1.2) destination element is modified -> conflict -> move
					} else if ((dbDestElem != null) && !providerDestElement.equals(dbDestElem)) {
						final String[] delPath = getDeletedSuffix(srcElement.getPath());

						// try to lock conflicting element
						final int conflictOVLockResult = checkAndLockPath(destPort, delPath);
						// already locked or other error -> retry later
						if (conflictOVLockResult > 0) {
							this.logConnector.log(LogEventLevelType.DEBUG, "already locked or other error -> retry: " + getPortPathString(destPort, delPath));
							if (destLockResult == 0) {
								unlock(destPort, srcElement.getPath());
							}
							if (srcLockResult == 0) {
								unlock(srcPort, srcElement.getPath());
							}
							this.syncJobManager.unlockPath(delPath);
							return 1;
						}

						// try to lock conflicting element
						final int conflictNVLockResult = checkAndLockPath(srcPort, delPath);
						// already locked or other error -> retry later
						if (conflictNVLockResult > 0) {
							this.logConnector.log(LogEventLevelType.DEBUG, "already locked or other error -> retry: " + getPortPathString(srcPort, delPath));
							if (destLockResult == 0) {
								unlock(destPort, srcElement.getPath());
							}
							if (srcLockResult == 0) {
								unlock(srcPort, srcElement.getPath());
							}
							if (conflictOVLockResult == 0) {
								unlock(destPort, delPath);
							}
							this.syncJobManager.unlockPath(delPath);
							return 1;
						}

						try {
							// 1: move modified destination element
							if (this.prosumerConnector.move(destPort, srcElement.getPath(), delPath) == 0) {
								try {
									this.componentConfiguration.moveElement(getDBDomain(destPort), srcElement.getPath(), getDBDomain(destPort), delPath);
								} catch (IllegalArgumentException | DatabaseException e1) {
									this.logConnector.log(e1);
									// move action completed - even if database cannot be written
								}

								// success -> 2: FILES: copy back moved version with new name, FOLDERS: create and run recursive sync on it
								boolean createCopyResult = false;
								if (providerDestElement.getType() == DataElementType.FILE) {
									createCopyResult = createCopyResult && copyElement(destPort, delPath, srcPort, delPath, providerDestElement.getType());
									if (createCopyResult) {
										try {
											// refresh conflict destination element and store attributes in database
											final DataElement conflictProviderDestElement = this.prosumerConnector.getElement(srcPort, delPath);
											this.componentConfiguration.storeElement(getDBDomain(srcPort), delPath, conflictProviderDestElement);

											// refresh conflict source element and store attributes in database
											final DataElement conflictProviderSrcElement = this.prosumerConnector.getElement(destPort, delPath);
											this.componentConfiguration.storeElement(getDBDomain(destPort), delPath, conflictProviderSrcElement);

										} catch (IllegalArgumentException | DatabaseException e) {
											this.logConnector.log(e);
											// sync action completed - even if database cannot be written
										} catch (BrokerException | ModuleException e) {

										}
										result = 0;
									}
								} else if (providerDestElement.getType() == DataElementType.FOLDER) {
									try {
										final int i = this.prosumerConnector.createFolder(srcPort, delPath);
										if (i == Provider.RESULT_CODE___OK) {
											// refresh destination element and store attributes in database
											srcElement = this.prosumerConnector.getElement(srcPort, delPath);
											this.componentConfiguration.storeElement(getDBDomain(srcPort), delPath, srcElement);
											doFullRecursiveSync(destPort, delPath);
										}
									} catch (IllegalArgumentException | DatabaseException | BrokerException | ModuleException e) {
										this.logConnector.log(e);
										// sync action completed - even if database cannot be written
									}
									result = 0;
								}

								if (conflictOVLockResult == 0) {
									unlock(destPort, delPath);
								}
								if (conflictNVLockResult == 0) {
									unlock(srcPort, delPath);
								}
								this.syncJobManager.unlockPath(delPath);
							}
						} catch (BrokerException | ModuleException | AuthorizationException e) {
							this.logConnector.log(e);
						}
					}

					// 4.2) destination element does not exist (anymore) -> just delete in database
				} else {
					// TODO: Conflict if destination element is modified -> create and sync conflict element.
					result = 0;
				}

				if (destLockResult == 0) {
					unlock(destPort, srcElement.getPath());
				}
			}

			// delete in database
			try {
				boolean b = this.componentConfiguration.deleteElement(getDBDomain(srcPort), srcElement.getPath());
				if (this.syncDelete && (dbDestElem != null)) {
					b = b && this.componentConfiguration.deleteElement(getDBDomain(destPort), srcElement.getPath());
				}
				if (!b) {
					result = 1;
				}
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
				if (srcLockResult == 0) {
					unlock(srcPort, srcElement.getPath());
				}
				return 1;
			}

			// 5) every other request is invalid (should never happen)
		} else {
			result = -1;
		}

		if (srcLockResult == 0) {
			unlock(srcPort, srcElement.getPath());
		}

		return result;
	}

	/**
	 * Unlocks a locked path.
	 *
	 * @param port the port
	 * @param path the path
	 * @return the result, 0 = OK, 1 = not OK
	 */
	private int unlock(final ProsumerPort port, final String[] path) {
		int result = 1;
		try {
			final int internalResult = this.prosumerConnector.unlock(port, path);
			if ((internalResult == 0) || (internalResult == ErrorCode.ENOSYS)) {
				result = 0;
			} else {
				this.logConnector.log(LogEventLevelType.WARNING, "unable to unlock " + getPortPathString(port, path));
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.WARNING, "unable to unlock " + getPortPathString(port, path));
		}
		return result;
	}
}
