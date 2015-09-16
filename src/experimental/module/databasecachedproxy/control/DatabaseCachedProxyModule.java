package experimental.module.databasecachedproxy.control;

import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.PersistentConfigurationHelper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import module.iface.AbstractProsumerProvider;
import module.iface.DataElementEventListener;
import module.iface.StreamListener;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import db.iface.ComponentConfigurationController;
import framework.constants.GenericControlInterfaceCommandProperties;
import framework.constants.GenericControlInterfaceCommands;
import framework.constants.GenericModuleCommandProperties;
import framework.constants.ModuleRight;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.control.ProviderConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.DatabaseException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.Port;
import framework.model.ProsumerPort;
import framework.model.ProviderPort;
import framework.model.event.DataElementEvent;
import framework.model.event.ProviderStateEvent;
import framework.model.event.type.DataElementEventType;
import framework.model.event.type.LogEventLevelType;
import framework.model.type.DataElementType;
import framework.model.type.ModuleStateType;

public class DatabaseCachedProxyModule extends AbstractProsumerProvider implements DataElementEventListener, StreamListener {

	private class ProsumerPortTuple {
		private final ProsumerPort port;
		private boolean connected = false;
		private boolean ready = false;
		private final int index;

		private ProsumerPortTuple(final ProsumerPort port, final int index) {
			this.port = port;
			this.index = index;
		}
	}

	public static final String COMMAND___SCAN_ALL_AVAILABLE_PROVIDERS = "scan_all_available_providers";
	public static final String COMMAND___INVALIDATE_DB_CACHE = "invalidate_db_cache";
	public static final String COMMAND___IS_AVIALABLE = "is_avialable";
	public static final String COMMAND___GET_PROVIDER_NUM = "get_provider_number";
	public static final String CONFIG_PROPERTY_KEY___NUMBER_OF_PROSUMER_PORTS = "number_of_prosumer_ports";
	public static final String CONFIG_PROPERTY_KEY___ALWAYS_WRITE_TO_BEST_PROVIDER = "always_write_to_best_provider";
	public static final String CONFIG_PROPERTY_KEY___FALLBACK_TO_BEST_PROVIDER = "fallback_to_best_provider";
	private static final String[] DB___CONFIG_DATA_PATH = { "config_data" };
	private static final String DB___DOMAIN___CACHE_PROVIDER_PREFIX = "provider_";
	private static final String DB___DOMAIN___CONFIG = "config";
	public static final int DEFAULT_CONFIG_VALUE___NUMBER_OF_PROSUMER_PORTS = 2;
	public static final boolean DEFAULT_CONFIG_VALUE___ALWAYS_WRITE_TO_BEST_PROVIDER = false;
	public static final boolean DEFAULT_CONFIG_VALUE___FALLBACK_TO_BEST_PROVIDER = true;
	private static final String PORT_ID___PROSUMER_PREFIX = "prosumer_";

	private static final String PORT_ID___PROVIDER = "provider";
	private PersistentConfigurationHelper configHelper;
	private final List<ProsumerPortTuple> prosumerPortTuples = new CopyOnWriteArrayList<DatabaseCachedProxyModule.ProsumerPortTuple>();
	private ProviderPort providerPort;
	private boolean providerPortConnected = false;
	private final ExecutorService service = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(DatabaseCachedProxyModule.class.getSimpleName() + "-%d").build());
	private int numberOfProsumerPorts = DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___NUMBER_OF_PROSUMER_PORTS;
	private boolean alwaysWriteToBestProvider = DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___ALWAYS_WRITE_TO_BEST_PROVIDER;
	private boolean fallbackToBestProvider = DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___FALLBACK_TO_BEST_PROVIDER;

	private final ReentrantLock stateLock = new ReentrantLock();

	public DatabaseCachedProxyModule(final ProsumerConnector prosumerConnector, final ProviderConnector providerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(prosumerConnector, providerConnector, componentConfiguration, logConnector);
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#checkAndLock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int checkAndLock(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			// this could cause problems when changing connected providers status while holding locks!
			final ProsumerPort prosumerPort = getBestProsumerPort(path, false); // false is OK here because we try to protect an probably existing element
			// somewhere
			return this.prosumerConnector.checkAndLock(prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#createFolder(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int createFolder(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.createFolder(getBestProsumerPort(path, true), path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#delete(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int delete(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			final ProsumerPort prosumerPort = getBestProsumerPort(path, false); // false is OK here since we currently have no way to hide/shadow elements
			// locally -> might change in the future
			final int result = this.prosumerConnector.delete(prosumerPort, path);
			if (result == 0) {
				deleteElementInDB(prosumerPort, path);
			}
			return result;
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	private void deleteElementInDB(final ProsumerPort port, final String[] path) {
		if (path == null) {
			return;
		}
		try {
			int i = 0;
			for (int j = 0; j < this.prosumerPortTuples.size(); j++) {
				final ProsumerPortTuple tuple = this.prosumerPortTuples.get(j);
				if (tuple.port == port) {
					i = j;
					break;
				}
			}
			if (i == 0) {
				return;
			}
			this.componentConfiguration.deleteElement(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, path);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}
	}

	private boolean doElementUpdate(final ProsumerPort port, final int dbIndex, final String[] path) {
		try {
			final String domain = DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + dbIndex;
			final DataElement element = this.prosumerConnector.getElement(port, path);
			if (element != null) {
				this.componentConfiguration.storeElement(domain, element.getPath(), element);
				return true;
			} else {
				return false;
			}
		} catch (BrokerException | ModuleException | IllegalArgumentException | DatabaseException | AuthorizationException e) {
			this.logConnector.log(e);
			return false;
		}
	}

	// best effort full initial recursive sync
	private boolean doFullRecursiveUpdate(final ProsumerPort port, final int dbIndex, final String[] path) {
		try {
			final String domain = DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + dbIndex;
			Set<DataElement> realChildren;
			realChildren = this.prosumerConnector.getChildElements(port, path, false);
			if (realChildren != null) {
				try {
					final Set<DataElement> dbChildren = this.componentConfiguration.getChildElements(domain, path);

					if (dbChildren != null) {
						dbChildren.removeAll(realChildren);
						for (final DataElement dbChild : dbChildren) {
							this.componentConfiguration.deleteElement(domain, dbChild.getPath());
						}
					}
				} catch (IllegalArgumentException | DatabaseException e) {
					this.logConnector.log(e);
				}
				for (final DataElement realChild : realChildren) {
					if (realChild.getType() == DataElementType.FOLDER) {
						doFullRecursiveUpdate(port, dbIndex, realChild.getPath());
					}
					this.componentConfiguration.storeElement(domain, realChild.getPath(), realChild);
				}
				return true;
			} else {
				return false;
			}
		} catch (BrokerException | ModuleException | IllegalArgumentException | DatabaseException | AuthorizationException e) {
			this.logConnector.log(e);
			return false;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#enterShutdown() */
	@Override
	public void enterShutdown() {
		if (this.providerPortConnected) {
			try {
				this.providerConnector.sendState(this.providerPort, 0);
			} catch (final BrokerException e) {
				this.logConnector.log(e);
			}
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#enterStartup() */
	@Override
	public void enterStartup() {
		// no op
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		// no op
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		if (this.providerPortConnected) {
			try {
				this.providerConnector.sendState(this.providerPort, ModuleStateType.READY);
			} catch (final BrokerException e) {
				this.logConnector.log(e);
			}
		}
	}

	private List<ProsumerPortTuple> getAppropriateProsumerPortTuples(final String[] path) {
		final List<ProsumerPortTuple> result = new ArrayList<ProsumerPortTuple>();
		int i = this.numberOfProsumerPorts;
		this.stateLock.lock();
		while (i >= 0) {
			i = getProsumerIndex(path, i);
			if (i < 0) {
				this.stateLock.unlock();
				return result;
			} else if (i < this.prosumerPortTuples.size()) {
				final ProsumerPortTuple tuple = this.prosumerPortTuples.get(i);
				if (tuple.connected && tuple.ready) {
					result.add(tuple);
				}
			}
		}
		this.stateLock.unlock();
		return result;
	}

	private ProsumerPort getBestProsumerPort(final String[] path, final boolean isWrite) throws ModuleException {
		this.stateLock.lock();
		if (!isWrite || !this.alwaysWriteToBestProvider) {
			final ProsumerPort port = getProsumerPort(path);
			if (port != null) {
				this.stateLock.unlock();
				return port;
			}
		}
		if (this.fallbackToBestProvider) {
			for (int i = this.prosumerPortTuples.size() - 1; i >= 0; i--) {
				final ProsumerPortTuple tuple = this.prosumerPortTuples.get(i);
				if (tuple.connected && tuple.ready) {
					this.stateLock.unlock();
					return tuple.port;
				}
			}
		}
		this.stateLock.unlock();
		throw new ModuleException("no suitable provider available");
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getChildElements(framework.model.ProviderPort, java.lang.String[], boolean) */
	@Override
	public Set<DataElement> getChildElements(final ProviderPort port, final String[] path, final boolean recursive) throws ModuleException {
		if (recursive) {
			throw new ModuleException("recursive folder listing not supported in this module");
		}
		final Set<DataElement> result = new HashSet<DataElement>();
		boolean notNull = false;
		for (int i = 0; i < this.numberOfProsumerPorts; i++) {
			try {
				final DataElement parent = this.componentConfiguration.getElement(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, path);
				if ((parent != null) && (parent.getType() == DataElementType.FOLDER)) {
					final Set<DataElement> prosumerResult = this.componentConfiguration.getChildElements(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, path);
					if (prosumerResult != null) {
						notNull = true;
						result.addAll(prosumerResult);
					}
				} else if (parent == null) {
					final ProsumerPortTuple tuple = this.prosumerPortTuples.get(i);
					if (tuple.connected && tuple.ready) {
						final Set<DataElement> finalProsumerResult;
						try {
							finalProsumerResult = this.prosumerConnector.getChildElements(tuple.port, path, false);
						} catch (BrokerException | AuthorizationException e1) {
							this.logConnector.log(e1);
							continue;
						}
						final int finalI = i;
						if (finalProsumerResult != null) {
							this.service.execute(new Runnable() {

								@Override
								public void run() {
									for (final DataElement element : finalProsumerResult) {
										try {
											DatabaseCachedProxyModule.this.componentConfiguration.storeElement(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + finalI, element.getPath(), element);
										} catch (IllegalArgumentException | DatabaseException e) {
											DatabaseCachedProxyModule.this.logConnector.log(e);
										}
									}
								}
							});
							notNull = true;
							result.addAll(finalProsumerResult);
						}
					}
				}
			} catch (IllegalArgumentException | DatabaseException e) {
				throw new ModuleException(e);
			}
		}
		if (notNull) {
			return result;
		} else {
			return null;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getElement(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElement getElement(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			final int i = getProsumerIndex(path, this.numberOfProsumerPorts);
			if (i < 0) {
				return null;
			}
			DataElement result = this.componentConfiguration.getElement(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, path);
			// if (result != null && path.length == 0) {
			// result = new DataElement(path, DataElementType.FOLDER, result.getSize(), result.getModificationDate(), result.getAdditionalProperties());
			// }
			if (result != null) {
				return result;
			} else {
				final List<ProsumerPortTuple> appropriateTuples = getAppropriateProsumerPortTuples(path);
				for (final ProsumerPortTuple tuple : appropriateTuples) {
					try {
						result = this.prosumerConnector.getElement(tuple.port, path);
					} catch (BrokerException | AuthorizationException e) {
						this.logConnector.log(e);
						continue;
					}
					if (result != null) {
						updateElement(tuple.port, result);
						break;
					}
				}
				return result;
			}
		} catch (IllegalArgumentException | DatabaseException e) {
			throw new ModuleException(e);
		}
	}

	private Map<String, String> getModuleCommandResult(final String[] path, final String command, final Map<String, String> properties) {
		final Map<String, String> result = new HashMap<String, String>();
		boolean notNull = false;
		try {
			for (int i = 0; i < this.prosumerPortTuples.size(); i++) {
				final ProsumerPortTuple tuple = this.prosumerPortTuples.get(i);
				DataElement element = null;
				if (path != null) {
					element = this.componentConfiguration.getElement(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, path);
				}
				if ((element != null) || (path == null)) {
					if (tuple.connected && tuple.ready) {
						try {
							final Map<String, String> curResult = this.prosumerConnector.sendModuleCommand(tuple.port, command, path, properties);
							if (curResult != null) {
								result.putAll(curResult);
								if (path != null) {
									this.componentConfiguration.deleteAllElementProperties(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, path);
									this.componentConfiguration.updateElementProperties(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, path, curResult);
								}
								notNull = true;
							}
							continue;
						} catch (BrokerException | ModuleException | AuthorizationException e) {
							this.logConnector.log(e);
						}
					}
					if ((element != null) && element.hasAdditionalProperties()) {
						result.putAll(element.getAdditionalProperties());
						result.put(GenericModuleCommandProperties.KEY___IS_CACHED, GenericModuleCommandProperties.VALUE___TRUE);
						notNull = true;
					}
				}
			}
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			return null;
		}
		if (notNull) {
			return result;
		} else {
			return null;
		}
	}

	private int getProsumerIndex(final ProsumerPort port) throws ModuleException {
		for (int i = this.prosumerPortTuples.size() - 1; i >= 0; i--) {
			if (this.prosumerPortTuples.get(i).port == port) {
				return i;
			}
		}
		throw new ModuleException("invalid port");
	}

	private int getProsumerIndex(final String[] path, final int belowIndex) {
		try {
			final DataElement indexElement = null;
			int i = Math.min(this.numberOfProsumerPorts - 1, belowIndex - 1);
			while ((indexElement == null) && (i >= 0)) {
				if (this.componentConfiguration.getElement(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, path) != null) {
					return i;
				}
				i--;
			}
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}
		return -1;
	}

	private ProsumerPort getProsumerPort(final String[] path) {
		int i = this.numberOfProsumerPorts;
		while (i >= 0) {
			i = getProsumerIndex(path, i);
			if (i < 0) {
				return null;
			}
			final ProsumerPortTuple tuple = this.prosumerPortTuples.get(i);
			if (tuple.connected && (tuple.port != null)) {
				return tuple.port;
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return ImmutableSet.<String> builder().add(GenericControlInterfaceCommands.DEFAULT_SUPPORT___CONFIG).add(DatabaseCachedProxyModule.COMMAND___INVALIDATE_DB_CACHE).add(DatabaseCachedProxyModule.COMMAND___SCAN_ALL_AVAILABLE_PROVIDERS).build();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#getSupportedModuleCommands(framework.model.Port, java.lang.String[]) */
	@Override
	public Set<String> getSupportedModuleCommands(final Port port, final String[] path) {
		if (port == this.providerPort) {
			final Set<String> result = new HashSet<String>();
			if (path != null) {
				final List<ProsumerPortTuple> tuples = getAppropriateProsumerPortTuples(path);
				for (final ProsumerPortTuple tuple : tuples) {
					try {
						result.addAll(this.prosumerConnector.getSupportedModuleCommands(tuple.port, path));
					} catch (BrokerException | ModuleException | AuthorizationException e) {
						this.logConnector.log(e);
					}
				}
			}
			result.add(DatabaseCachedProxyModule.COMMAND___INVALIDATE_DB_CACHE);
			result.add(DatabaseCachedProxyModule.COMMAND___GET_PROVIDER_NUM);
			result.add(DatabaseCachedProxyModule.COMMAND___IS_AVIALABLE);
			return result;
		} else {
			try {
				return this.providerConnector.getSupportedModuleCommands(this.providerPort, path);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return null;
			}
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getType(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElementType getType(final ProviderPort port, final String[] path) throws ModuleException {
		return getElement(port, path).getType();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		initializeConfig();
		try {
			final Set<String> domains = this.componentConfiguration.getElementDomains();
			System.out.println("DOM: " + this.numberOfProsumerPorts + " --- " + domains);
			for (int i = 0; i < this.numberOfProsumerPorts; i++) {
				final String curDomain = DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i;
				this.componentConfiguration.initializeElementDomains(curDomain);
				domains.remove(curDomain);
				System.out.println("REMMT " + curDomain);
			}
			for (final String domain : domains) {
				if (domain.startsWith(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX)) {
					System.out.println("KILLING " + domain);
					this.componentConfiguration.deleteElementDomain(domain);
				}
			}
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}
		try {
			this.stateLock.lock();
			for (int i = 0; i < this.numberOfProsumerPorts; i++) {
				final ProsumerPort prosumerPort = this.prosumerConnector.registerProsumerPort(this, DatabaseCachedProxyModule.PORT_ID___PROSUMER_PREFIX + i, 1);
				final ProsumerPortTuple tuple = new ProsumerPortTuple(prosumerPort, i);
				this.prosumerPortTuples.add(tuple);
			}
			this.providerPort = this.providerConnector.registerProviderPort(this, DatabaseCachedProxyModule.PORT_ID___PROVIDER, -1);
			if ((this.prosumerConnector.getOwnRights() & ModuleRight.RECEIVE_EVENTS) > 0) {
				final String[] rootPath = {};
				for (final ProsumerPortTuple tuple : this.prosumerPortTuples) {
					this.prosumerConnector.subscribe(tuple.port, rootPath, true, this);
					this.prosumerConnector.addStreamListener(tuple.port, this);
				}
			}
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
		} finally {
			this.stateLock.unlock();
		}
	}

	private void initializeConfig() {
		try {
			this.configHelper = new PersistentConfigurationHelper(this.componentConfiguration, DatabaseCachedProxyModule.DB___DOMAIN___CONFIG, DatabaseCachedProxyModule.DB___CONFIG_DATA_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.WARNING, "no persistent config");
			this.configHelper = new PersistentConfigurationHelper();
		}

		ConfigValue cv;
		String key;

		key = DatabaseCachedProxyModule.CONFIG_PROPERTY_KEY___NUMBER_OF_PROSUMER_PORTS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueInteger(DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___NUMBER_OF_PROSUMER_PORTS);
			cv.setRangeInteger(1, null);
			cv.setDescriptionString("Number of prosumer ports to provide.");
			this.configHelper.updateConfigValue(key, cv, true);
		}
		this.numberOfProsumerPorts = this.configHelper.getInteger(key, DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___NUMBER_OF_PROSUMER_PORTS);

		key = DatabaseCachedProxyModule.CONFIG_PROPERTY_KEY___ALWAYS_WRITE_TO_BEST_PROVIDER;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___ALWAYS_WRITE_TO_BEST_PROVIDER);
			cv.setDescriptionString("Always write to best available provider (Note: Move action is NOT supported between different providers currently!).");
			this.configHelper.updateConfigValue(key, cv, true);
		}
		this.alwaysWriteToBestProvider = this.configHelper.getBoolean(key, DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___ALWAYS_WRITE_TO_BEST_PROVIDER);

		key = DatabaseCachedProxyModule.CONFIG_PROPERTY_KEY___FALLBACK_TO_BEST_PROVIDER;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___FALLBACK_TO_BEST_PROVIDER);
			cv.setDescriptionString("Fallback to best available provider if path's original provider is not available.");
			this.configHelper.updateConfigValue(key, cv, true);
		}
		this.fallbackToBestProvider = this.configHelper.getBoolean(key, DatabaseCachedProxyModule.DEFAULT_CONFIG_VALUE___FALLBACK_TO_BEST_PROVIDER);
	}

	private boolean invalidateDB() {
		for (int i = 0; i < this.numberOfProsumerPorts; i++) {
			final String curDomain = DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i;
			try {
				this.componentConfiguration.deleteElementDomain(curDomain);
				this.componentConfiguration.initializeElementDomains(curDomain);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
				return false;
			}
		}
		return true;
	}

	private void invalidatePath(final String[] path) {
		if (path == null) {
			return;
		}
		try {
			for (final ProsumerPortTuple tuple : getAppropriateProsumerPortTuples(path)) {
				this.componentConfiguration.deleteElement(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + tuple.index, path);
			}
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return true;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#move(framework.model.ProviderPort, java.lang.String[], java.lang.String[]) */
	@Override
	public int move(final ProviderPort port, final String[] srcPath, final String[] destPath) throws ModuleException {
		final ProsumerPort prosumerPort = getProsumerPort(srcPath);
		if (prosumerPort != null) {
			try {
				return this.prosumerConnector.move(prosumerPort, srcPath, destPath);
			} catch (BrokerException | AuthorizationException e) {
				throw new ModuleException(e);
			}
		} else {
			throw new ModuleException("no suitable provider available");
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return CommandResultHelper.getDefaultResultFail();
		}
		if (command.equals(GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES)) {
			return this.configHelper.getAllValues(CommandResultHelper.getDefaultResultOk());
		} else if (command.equals(GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES) && (properties != null)) {
			if (this.configHelper.updateAllValues(properties, false)) {
				return CommandResultHelper.getDefaultResultOk();
			}
		} else if (command.equals(DatabaseCachedProxyModule.COMMAND___INVALIDATE_DB_CACHE)) {
			if (invalidateDB()) {
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail();
			}
		} else if (command.equals(DatabaseCachedProxyModule.COMMAND___SCAN_ALL_AVAILABLE_PROVIDERS)) {
			scanAllAvailableProviders();
			return CommandResultHelper.getDefaultResultOk();
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.DataElementEventListener#onElementEvent(framework.model.ProsumerPort, framework.model.event.DataElementEvent) */
	@Override
	public void onElementEvent(final ProsumerPort port, final DataElementEvent event) {
		try {
			if (event.eventType == DataElementEventType.DELETE) {
				deleteElementInDB(port, event.dataElement.getPath());
			} else {
				updateElement(port, event.dataElement);
				final int i = getProsumerIndex(port);
				doFullRecursiveUpdate(port, i, event.dataElement.getPath());
			}
			this.providerConnector.sendElementEvent(this.providerPort, event.dataElement, event.eventType);
		} catch (BrokerException | ModuleException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.StreamListener#onInputStreamClose(framework.model.Port, java.lang.String[]) */
	@Override
	public void onInputStreamClose(final Port port, final String[] path) {
		// no op
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onModuleCommand(framework.model.Port, java.lang.String, java.lang.String[], java.util.Map) */
	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		if (port == this.providerPort) {
			if (command.equals(DatabaseCachedProxyModule.COMMAND___INVALIDATE_DB_CACHE)) {
				if ((path != null) && (path.length > 0)) {
					invalidatePath(path);
					return CommandResultHelper.getDefaultResultOk();
				} else {
					return CommandResultHelper.getDefaultResultFail();
				}
			} else if (command.equals(DatabaseCachedProxyModule.COMMAND___GET_PROVIDER_NUM)) {
				if (path == null) {
					return CommandResultHelper.getDefaultResultFail();
				} else {
					int i;
					i = getProsumerIndex(path, this.numberOfProsumerPorts);
					if (i < this.numberOfProsumerPorts) {
						return CommandResultHelper.getDefaultResultOk(GenericControlInterfaceCommandProperties.KEY___MESSAGE, String.valueOf(i)); // TODO ->
						// ControlInterface
						// weg und
						// hier
						// generische
						// klasse!
					} else {
						return CommandResultHelper.getDefaultResultFail();
					}
				}
			} else if (command.equals(DatabaseCachedProxyModule.COMMAND___IS_AVIALABLE)) {
				if (path == null) {
					return CommandResultHelper.getDefaultResultFail();
				} else {
					final int i = getProsumerIndex(path, this.numberOfProsumerPorts);
					if (i < 0) {
						return CommandResultHelper.getDefaultResultOk(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "false");
					} else {
						return CommandResultHelper.getDefaultResultOk(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "true");
					}
				}
			} else {
				return getModuleCommandResult(path, command, properties);
			}
		} else {
			try {
				return this.providerConnector.sendModuleCommand(this.providerPort, command, path, properties);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return null;
			}
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.StreamListener#onOutputStreamClose(framework.model.Port, java.lang.String[]) */
	@Override
	public void onOutputStreamClose(final Port port, final String[] path) {
		if ((port instanceof ProsumerPort) && (path != null)) {
			try {
				final ProsumerPort prosumerPort = (ProsumerPort) port;
				final int i = getProsumerIndex(prosumerPort);
				doElementUpdate(prosumerPort, i, path);
			} catch (final ModuleException e) {
				this.logConnector.log(e);
			}
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		if (port instanceof ProsumerPort) {
			for (final ProsumerPortTuple tuple : this.prosumerPortTuples) {
				if (tuple.port == port) {
					tuple.connected = true;
					return;
				}
			}
		} else if (port == this.providerPort) {
			this.providerPortConnected = false;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		if (port instanceof ProsumerPort) {
			for (final ProsumerPortTuple tuple : this.prosumerPortTuples) {
				if (tuple.port == port) {
					tuple.connected = false;
					return;
				}
			}
		} else if (port == this.providerPort) {
			this.providerPortConnected = true;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Prosumer#onProviderStateEvent(framework.model.Port, framework.model.event.ProviderStateEvent) */
	@Override
	public void onProviderStateEvent(final Port port, final ProviderStateEvent event) {
		try {
			for (final ProsumerPortTuple tuple : this.prosumerPortTuples) {
				if (tuple.port == port) {
					tuple.ready = (event.state & ModuleStateType.READY) != 0;
					if (tuple.ready) {
						doFullRecursiveUpdate(tuple.port, tuple.index, new String[0]);
					}
					if (this.providerPortConnected) {
						this.providerConnector.sendState(this.providerPort, event.state);
					}
					return;
				}
			}
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#onStateRequest(framework.model.ProviderPort) */
	@Override
	public void onStateRequest(final ProviderPort port) {
		try {
			// this module is always ready because it tries to use cached data if nothing else if available
			this.providerConnector.sendState(this.providerPort, ModuleStateType.READY);
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#readData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public InputStream readData(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			final ProsumerPort prosumerPort = getBestProsumerPort(path, false);
			return this.prosumerConnector.readData(prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	private void scanAllAvailableProviders() {
		for (int i = this.prosumerPortTuples.size() - 1; i >= 0; i--) {
			final ProsumerPort port = this.prosumerPortTuples.get(i).port;
			doFullRecursiveUpdate(port, i, new String[0]);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#unlock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int unlock(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			final ProsumerPort prosumerPort = getBestProsumerPort(path, false);
			return this.prosumerConnector.unlock(prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	private void updateElement(final ProsumerPort port, final DataElement element) {
		try {
			final int i = getProsumerIndex(port);
			if (i >= 0) {
				this.componentConfiguration.storeElement(DatabaseCachedProxyModule.DB___DOMAIN___CACHE_PROVIDER_PREFIX + i, element.getPath(), element);
			}
		} catch (IllegalArgumentException | DatabaseException | ModuleException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#writeData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public OutputStream writeData(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.writeData(getBestProsumerPort(path, true), path);
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
			return null;
		}
	}
}
