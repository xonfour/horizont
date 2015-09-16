package experimental.module.memorycachedproxy.control;

import framework.constants.GenericControlInterfaceCommands;
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
import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.PersistentConfigurationHelper;
import helper.TextFormatHelper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import module.iface.AbstractProsumerProvider;
import module.iface.DataElementEventListener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import db.iface.ComponentConfigurationController;

public class MemoryCachedProxyModule extends AbstractProsumerProvider implements DataElementEventListener {

	private static final String DB___DOMAIN___CONFIG = "config";
	private static final String[] DB___CONFIG_DATA_PATH = { "config_data" };
	public static final String CONFIG_PROPERTY_KEY___TTL_SECS = "time_to_live_seconds";
	public static final int DEFAULT_CONFIG_VALUE___TTL_SECS = 300;
	public static final boolean DEFAULT_CONFIG_VALUE___EXTEND_ON_ACCESS = false;
	private static final String COMMAND___INVALIDATE_CACHE = "invalidate_cache";
	private static final String CONFIG_PROPERTY_KEY___EXTEND_ON_ACCESS = "extend_on_access";

	private PersistentConfigurationHelper configHelper;
	private int ttlSeconds = MemoryCachedProxyModule.DEFAULT_CONFIG_VALUE___TTL_SECS;
	private final ExecutorService service = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(MemoryCachedProxyModule.class.getSimpleName() + "-%d").build());

	// MODULE COMMUNICATION STUFF

	// id (name) for ProsumerPort
	private static final String PORT_ID___PROSUMER = "prosumer";

	// id (name) for ProviderPort
	private static final String PORT_ID___PROVIDER = "provider";

	// the ProsumerPort we need for communication/authentication within the ProsumerConnector
	private ProsumerPort prosumerPort;

	// the ProviderPort we need for communication/authentication within the ProviderConnector
	private ProviderPort providerPort;

	// stores the bitmask of rights this module has (currently only just for checking event listener rights)
	private final int ownRights = ModuleRight.RIGHT___NON;

	private Cache<String, DataElement> elementCache;
	private Cache<String, Map<String, String>> moduleCommandResultCache;
	private Cache<String, Set<DataElement>> childrenCache;

	// CONSTRUCTOR /////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor call inherited from AbstractProsumerProvider. The constructor must exist exactly in this form. If it does not the module will NOT be usable.
	 *
	 * @param prosumerConnector Connector to be used for communication with connected PROVIDERS over the local PROSUMER PORT(S)
	 * @param providerConnector Connector to be used for communication with connected PROSUMERS over the local PROVIDER PORT(S)
	 * @param componentConfiguration The database storage and module configuration
	 * @param logConnector Connector to be used for logging purposes
	 */
	public MemoryCachedProxyModule(final ProsumerConnector prosumerConnector, final ProviderConnector providerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(prosumerConnector, providerConnector, componentConfiguration, logConnector);
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#checkAndLock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int checkAndLock(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.checkAndLock(this.prosumerPort, path);
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
			return this.prosumerConnector.createFolder(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#delete(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int delete(final ProviderPort port, final String[] path) throws ModuleException {
		invalidatePath(path);
		try {
			return this.prosumerConnector.delete(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#enterShutdown() */
	@Override
	public void enterShutdown() {
		// ignored
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#enterStartup() */
	@Override
	public void enterStartup() {
		invalidateAll();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		invalidateAll();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		// ignored
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getChildElements(framework.model.ProviderPort, java.lang.String[], boolean) */
	@Override
	public Set<DataElement> getChildElements(final ProviderPort port, final String[] path, final boolean recursive) throws ModuleException {
		try {
			final String intPath = TextFormatHelper.getPathString(path);
			if (!recursive) {
				final Set<DataElement> result = this.childrenCache.getIfPresent(intPath);
				if (result != null) {
					return result;
				}
			}
			final Set<DataElement> result = this.prosumerConnector.getChildElements(this.prosumerPort, path, recursive);
			if (result != null) {
				if (!recursive) {
					this.childrenCache.put(intPath, result);
				}
				this.service.execute(new Runnable() {

					@Override
					public void run() {
						for (final DataElement element : result) {
							MemoryCachedProxyModule.this.elementCache.put(TextFormatHelper.getPathString(element.getPath()), element);
						}
					}
				});
			}
			return result;
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getElement(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElement getElement(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			final String intPath = TextFormatHelper.getPathString(path);
			DataElement result = this.elementCache.getIfPresent(intPath);
			if (result != null) {
				return result;
			} else {
				result = this.prosumerConnector.getElement(this.prosumerPort, path);
				if (result != null) {
					this.elementCache.put(intPath, result);
				}
				return result;
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return ImmutableSet.<String> builder().add(GenericControlInterfaceCommands.DEFAULT_SUPPORT___CONFIG).add(MemoryCachedProxyModule.COMMAND___INVALIDATE_CACHE).build();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#getSupportedModuleCommands(framework.model.Port, java.lang.String[]) */
	@Override
	public Set<String> getSupportedModuleCommands(final Port port, final String[] path) {
		if (port == this.prosumerPort) {
			try {
				return this.providerConnector.getSupportedModuleCommands(this.providerPort, path);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return null;
			}
		} else if (port == this.providerPort) {
			try {
				final Set<String> result = new HashSet<String>(this.prosumerConnector.getSupportedModuleCommands(this.prosumerPort, path));
				result.add(MemoryCachedProxyModule.COMMAND___INVALIDATE_CACHE);
				return result;
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return null;
			}
		} else {
			return null;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getType(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElementType getType(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			final DataElement result = this.elementCache.getIfPresent(TextFormatHelper.getPathString(path));
			if (result != null) {
				return result.getType();
			} else {
				return this.prosumerConnector.getType(this.prosumerPort, path);
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		initializeConfig();
		if (this.configHelper.getBoolean(MemoryCachedProxyModule.CONFIG_PROPERTY_KEY___EXTEND_ON_ACCESS, MemoryCachedProxyModule.DEFAULT_CONFIG_VALUE___EXTEND_ON_ACCESS)) {
			this.elementCache = CacheBuilder.newBuilder().expireAfterAccess(this.ttlSeconds, TimeUnit.SECONDS).build();
			this.childrenCache = CacheBuilder.newBuilder().expireAfterAccess(this.ttlSeconds, TimeUnit.SECONDS).build();
			this.moduleCommandResultCache = CacheBuilder.newBuilder().expireAfterAccess(this.ttlSeconds, TimeUnit.SECONDS).build();
		} else {
			this.elementCache = CacheBuilder.newBuilder().expireAfterWrite(this.ttlSeconds, TimeUnit.SECONDS).build();
			this.childrenCache = CacheBuilder.newBuilder().expireAfterWrite(this.ttlSeconds, TimeUnit.SECONDS).build();
			this.moduleCommandResultCache = CacheBuilder.newBuilder().expireAfterWrite(this.ttlSeconds, TimeUnit.SECONDS).build();
		}
		try {
			this.prosumerPort = this.prosumerConnector.registerProsumerPort(this, MemoryCachedProxyModule.PORT_ID___PROSUMER, 1);
			this.providerPort = this.providerConnector.registerProviderPort(this, MemoryCachedProxyModule.PORT_ID___PROVIDER, -1);
			if ((this.ownRights & ModuleRight.RECEIVE_EVENTS) > 0) {
				final String[] rootPath = {};
				this.prosumerConnector.subscribe(this.prosumerPort, rootPath, true, this);
			}
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
		}
	}

	private void initializeConfig() {
		try {
			this.configHelper = new PersistentConfigurationHelper(this.componentConfiguration, MemoryCachedProxyModule.DB___DOMAIN___CONFIG, MemoryCachedProxyModule.DB___CONFIG_DATA_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.WARNING, "no persistent config");
			this.configHelper = new PersistentConfigurationHelper();
		}

		ConfigValue cv;
		String key;

		key = MemoryCachedProxyModule.CONFIG_PROPERTY_KEY___TTL_SECS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueInteger(MemoryCachedProxyModule.DEFAULT_CONFIG_VALUE___TTL_SECS);
			cv.setRangeInteger(1, null);
			cv.setDescriptionString("Keeping cached data elements for at least this amount of time (in seconds).");
			this.configHelper.updateConfigValue(key, cv, true);
		}
		this.ttlSeconds = this.configHelper.getInteger(MemoryCachedProxyModule.CONFIG_PROPERTY_KEY___TTL_SECS, MemoryCachedProxyModule.DEFAULT_CONFIG_VALUE___TTL_SECS);

		key = MemoryCachedProxyModule.CONFIG_PROPERTY_KEY___EXTEND_ON_ACCESS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(MemoryCachedProxyModule.DEFAULT_CONFIG_VALUE___EXTEND_ON_ACCESS);
			cv.setDescriptionString("Extend time to live of cached data on every access (and not only on the first one).");
			this.configHelper.updateConfigValue(key, cv, true);
		}
	}

	private void invalidateAll() {
		this.elementCache.invalidateAll();
		this.childrenCache.invalidateAll();
		this.moduleCommandResultCache.invalidateAll();
	}

	private void invalidatePath(final String[] path) {
		if (path == null) {
			return;
		}
		final String intPath = TextFormatHelper.getPathString(path);
		this.elementCache.invalidate(intPath);
		this.childrenCache.invalidate(intPath);
		this.moduleCommandResultCache.invalidate(intPath);
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
		invalidatePath(srcPath);
		invalidatePath(destPath);
		try {
			return this.prosumerConnector.move(this.prosumerPort, srcPath, destPath);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
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
				this.ttlSeconds = this.configHelper.getInteger(MemoryCachedProxyModule.CONFIG_PROPERTY_KEY___TTL_SECS, MemoryCachedProxyModule.DEFAULT_CONFIG_VALUE___TTL_SECS);
				return CommandResultHelper.getDefaultResultOk();
			}
		} else if (command.equals(MemoryCachedProxyModule.COMMAND___INVALIDATE_CACHE)) {
			invalidateAll();
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
			invalidatePath(event.dataElement.getPath());
			if (event.eventType != DataElementEventType.DELETE) {
				this.elementCache.put(TextFormatHelper.getPathString(event.dataElement.getPath()), event.dataElement);
			}
			this.providerConnector.sendElementEvent(this.providerPort, event.dataElement, event.eventType);
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onModuleCommand(framework.model.Port, java.lang.String, java.lang.String[], java.util.Map) */
	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		if (port == this.prosumerPort) {
			try {
				return this.providerConnector.sendModuleCommand(this.providerPort, command, path, properties);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return null;
			}
		} else if (port == this.providerPort) {
			if (command.equals(MemoryCachedProxyModule.COMMAND___INVALIDATE_CACHE)) {
				if ((path != null) && (path.length > 0)) {
					invalidatePath(path);
					return CommandResultHelper.getDefaultResultOk();
				} else {
					return CommandResultHelper.getDefaultResultFail();
				}
			} else {
				try {
					String key;
					if (path == null) {
						key = command;
					} else {
						key = command + TextFormatHelper.getPathString(path);
					}
					Map<String, String> result = this.moduleCommandResultCache.getIfPresent(key);
					if (result == null) {
						result = this.prosumerConnector.sendModuleCommand(this.prosumerPort, command, path, properties);
						if (result != null) {
							this.moduleCommandResultCache.put(key, result);
						}
					}
					return result;
				} catch (BrokerException | ModuleException | AuthorizationException e) {
					this.logConnector.log(e);
					return null;
				}
			}
		} else {
			return null;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		invalidateAll();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		invalidateAll();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Prosumer#onProviderStateEvent(framework.model.Port, framework.model.event.ProviderStateEvent) */
	@Override
	public void onProviderStateEvent(final Port port, final ProviderStateEvent event) {
		try {
			this.providerConnector.sendState(this.providerPort, event.state);
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
			this.prosumerConnector.requestConnectedProviderStatus(this.prosumerPort);
		} catch (BrokerException | ModuleException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#readData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public InputStream readData(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.readData(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#unlock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int unlock(final ProviderPort port, final String[] path) throws ModuleException {
		try {
			return this.prosumerConnector.unlock(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#writeData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public OutputStream writeData(final ProviderPort port, final String[] path) throws ModuleException {
		invalidatePath(path);
		try {
			return this.prosumerConnector.writeData(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
			return null;
		}
	}
}
