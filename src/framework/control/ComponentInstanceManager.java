package framework.control;

import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import module.iface.AbstractProsumer;
import module.iface.AbstractProsumerProvider;
import module.iface.AbstractProvider;
import module.iface.Module;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import controlinterface.iface.AbstractControlInterface;
import controlinterface.iface.ControlInterface;
import controlinterface.iface.GeneralEventListener;
import db.iface.BaseConfigurationController;
import db.iface.ComponentConfigurationController;
import framework.constants.Constants;
import framework.exception.DatabaseException;
import framework.model.event.ConnectionUpdateEvent;
import framework.model.event.DataElementEvent;
import framework.model.event.GeneralEvent;
import framework.model.event.LogEvent;
import framework.model.event.ModuleActivityEvent;
import framework.model.event.ModuleUpdateEvent;
import framework.model.event.PortUpdateEvent;
import framework.model.event.SystemStateEvent;
import framework.model.event.type.GeneralEventType;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.LogEventSourceType;

/**
 * Created, instantiates, manages and removes component (control interfaces and modules) instances. It also delivers events registered listeners (control
 * interfaces).
 * <p>
 * TODO: Redesign event delivery system. Currently every control interface has its own event queue an delivery thread, even if it has no listeners registered.
 *
 * @author Stefan Werner
 */
final class ComponentInstanceManager {

	/**
	 * Handler to forward events to registered listeners.
	 */
	private class EventHandlerThread extends Thread {

		private final Map<GeneralEventListener, Set<GeneralEventType>> listeners;
		private final BlockingQueue<GeneralEvent> queue;

		/**
		 * Instantiates a new event handler thread.
		 *
		 * @param listeners the map listeners and requested event types
		 * @param queue the event queue to read
		 */
		private EventHandlerThread(final Map<GeneralEventListener, Set<GeneralEventType>> listeners, final BlockingQueue<GeneralEvent> queue) {
			this.listeners = listeners;
			this.queue = queue;
		}

		@Override
		public void run() {
			while (!Thread.interrupted()) {
				GeneralEvent event;
				try {
					event = this.queue.take();
					if (event != null) {
						for (final GeneralEventListener listener : this.listeners.keySet()) {
							final Set<GeneralEventType> types = this.listeners.get(listener);
							if ((types != null) && (types.contains(GeneralEventType.GENERAL_EVENT) || types.contains(getEventType(event)))) {
								try {
									listener.onGeneralEvent(event);
								} catch (final Exception e) {
									ComponentInstanceManager.this.logConnector.log(e);
								}
							}
						}
					}
				} catch (final InterruptedException e) {
					break;
				}
			}
		}
	}

	private final ComponentAuthorizationManager authManager;
	private final BaseConfigurationController baseConfigController;
	private final Broker broker;
	private final Map<String, BlockingQueue<GeneralEvent>> ciEventQueues = new ConcurrentHashMap<String, BlockingQueue<GeneralEvent>>();
	private final Map<String, Thread> ciEventThreads = new ConcurrentHashMap<String, Thread>();
	private final Map<String, Map<GeneralEventListener, Set<GeneralEventType>>> ciGeneralEventListeners = new ConcurrentHashMap<String, Map<GeneralEventListener, Set<GeneralEventType>>>();
	private final Map<String, ControlInterface> cis = new ConcurrentHashMap<String, ControlInterface>();
	private final Map<String, Class<? extends AbstractControlInterface>> controlInterfaceClasses = new ConcurrentHashMap<String, Class<? extends AbstractControlInterface>>();
	private final ControlInterfaceActionHandler coreCIActionHandler;
	private final ReentrantLock dataLock = new ReentrantLock(true);
	private final LogConnector logConnector;
	private final Map<String, Module> modules = new ConcurrentHashMap<String, Module>();
	private final Map<String, Class<? extends AbstractProsumer>> prosumerClasses = new ConcurrentHashMap<String, Class<? extends AbstractProsumer>>();
	private final Map<String, Class<? extends AbstractProsumerProvider>> prosumerProviderClasses = new ConcurrentHashMap<String, Class<? extends AbstractProsumerProvider>>();
	private final Map<String, Class<? extends AbstractProvider>> providerClasses = new ConcurrentHashMap<String, Class<? extends AbstractProvider>>();
	private final SecureRandom random = new SecureRandom();
	private final TimeLimiter timeLimiter = new SimpleTimeLimiter();

	/**
	 * Instantiates a new component instance manager.
	 *
	 * @param logConnector the log connector
	 * @param broker the broker
	 * @param coreCIActionHandler the core CI action handler
	 * @param authController the authentication controller
	 * @param baseConfigController the base configuration controller
	 */
	ComponentInstanceManager(final LogConnector logConnector, final Broker broker, final ControlInterfaceActionHandler coreCIActionHandler, final ComponentAuthorizationManager authController, final BaseConfigurationController baseConfigController) {
		this.logConnector = logConnector;
		this.broker = broker;
		this.coreCIActionHandler = coreCIActionHandler;
		this.authManager = authController;
		this.baseConfigController = baseConfigController;
		loadAvailableModuleAndControlInterfaceImpls();
	}

	/**
	 * Adds a general event listener. The types of events the listener is interested in can be given. To receive all events the type
	 * {@link GeneralEventType.GENERAL_EVENT} may be used.
	 *
	 * @param ciId the CI ID
	 * @param generalEventListener the general event listener
	 * @param eventTypes the event types interested in
	 * @return true, if successful
	 */
	boolean addGeneralEventListener(final String ciId, final GeneralEventListener generalEventListener, final GeneralEventType... eventTypes) {
		final Map<GeneralEventListener, Set<GeneralEventType>> listeners = this.ciGeneralEventListeners.get(ciId);
		if ((listeners == null) || (eventTypes == null)) {
			return false;
		} else {
			final Set<GeneralEventType> types = new HashSet<GeneralEventType>();
			if (eventTypes.length == 0) {
				types.add(GeneralEventType.GENERAL_EVENT);
			} else {
				Collections.addAll(types, eventTypes);
			}
			listeners.put(generalEventListener, types);
			return true;
		}
	}

	/**
	 * Adds a new control interface.
	 *
	 * @param ciType the CI type
	 * @param rights the rights
	 * @return the ID
	 */
	String addNewControlInterface(final String ciType, final int rights) {
		if ((rights < 0) || !getAvailableControlInterfaceTypes().contains(ciType)) {
			this.logConnector.log(LogEventLevelType.ERROR, "invalid rights or cannot find requested control interface class type");
			return null;
		}
		this.dataLock.lock();
		final String ciId = generateUniqueComponentId();
		if (ciId != null) {
			final ComponentConfigurationController ciConf = this.baseConfigController.addCIConfiguration(ciType, ciId, ciType + "_" + ciId, rights);
			if (ciConf != null) {
				getControlInterface(ciId, true);
			}
		}
		this.dataLock.unlock();
		return ciId;
	}

	/**
	 * Adds a new module.
	 *
	 * @param moduleType the module type
	 * @param rights the rights
	 * @return the ID
	 */
	String addNewModule(final String moduleType, final int rights) {
		if ((rights < 0) || !getAvailableModuleTypes().contains(moduleType)) {
			return null;
		}
		this.dataLock.lock();
		final String moduleId = generateUniqueComponentId();
		if (moduleId != null) {
			final ComponentConfigurationController modConf = this.baseConfigController.addModuleConfiguration(moduleType, moduleId, moduleType + "_" + moduleId, rights);
			if (modConf != null) {
				getModule(moduleId, true);
			}
		}
		this.dataLock.unlock();
		return moduleId;
	}

	/**
	 * Generates a unique component ID currently consisting of 16 letters and numbers. Uniqueness is checked.
	 *
	 * @return the string
	 */
	private String generateUniqueComponentId() {
		while (true) {
			String id = "";
			for (int i = 0; i < 16; i++) {
				id += Constants.CORE___SESSION_ID_CHARS.charAt(this.random.nextInt(Constants.CORE___SESSION_ID_CHARS.length()));
			}
			if ((this.baseConfigController.getModuleConfiguration(id) == null) && (this.baseConfigController.getCIConfiguration(id) == null)) {
				// yes, this is paranoid
				return id;
			}
			this.logConnector.log(LogEventLevelType.WARNING, "Component id collusion detected. This should theoretically NEVER happen. Retrying...");
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (final InterruptedException e) {
				return null;
			}
		}
	}

	/**
	 * Gets the number of active control interfaces.
	 *
	 * @return the active CI count
	 */
	int getActiveCIsCount() {
		return this.cis.size();
	}

	/**
	 * Gets a Set of active control interfaces and corresponding types (as two field String array per entry).
	 * <p>
	 * TODO: This should be done in a better way.
	 *
	 * @return the active control interfaces and corresponding types
	 */
	Set<String[]> getActiveControlInterfacesAndTypes() {
		final Set<String[]> result = new HashSet<String[]>();
		for (final String ciId : this.cis.keySet()) {
			final String[] entry = new String[3];
			entry[0] = ciId;
			entry[1] = this.baseConfigController.getCIType(ciId);
			entry[2] = this.baseConfigController.getCIName(ciId);
			result.add(entry);
		}
		return result;
	}

	/**
	 * Gets the active modules count.
	 *
	 * @return the active modules count
	 */
	int getActiveModulesCount() {
		return this.modules.size();
	}

	/**
	 * Gets the available control interface types.
	 *
	 * @return the available control interface types
	 */
	Set<String> getAvailableControlInterfaceTypes() {
		final Set<String> result = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		result.addAll(this.controlInterfaceClasses.keySet());
		return result;
	}

	/**
	 * Gets the available module types.
	 *
	 * @return the available module types
	 */
	Set<String> getAvailableModuleTypes() {
		final Set<String> result = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		result.addAll(this.prosumerClasses.keySet());
		result.addAll(this.prosumerProviderClasses.keySet());
		result.addAll(this.providerClasses.keySet());
		return result;
	}

	/**
	 * Gets a control interface, optionally instantiate it if inactive.
	 *
	 * @param ciId the CI ID
	 * @param instanciateNewIfNecessary set to true to instantiate if necessary
	 * @return the control interface (may be null)
	 */
	ControlInterface getControlInterface(final String ciId, final boolean instanciateNewIfNecessary) {
		if ((ciId == null) || ciId.isEmpty()) {
			return null;
		}
		this.dataLock.lock();
		ControlInterface ci = this.cis.get(ciId);
		if ((ci == null) && instanciateNewIfNecessary) {
			final ComponentConfigurationController modConf = this.baseConfigController.getCIConfiguration(ciId);
			if (modConf != null) {
				String type;
				type = this.baseConfigController.getCIType(ciId);
				final ControlInterfaceConnector conn = new ControlInterfaceConnector(ciId, this.coreCIActionHandler);
				ci = getControlInterfaceInstance(type, ciId, modConf, conn);
				if (ci != null) {
					this.cis.put(ciId, ci);
					final BlockingQueue<GeneralEvent> queue = new LinkedBlockingQueue<GeneralEvent>();
					this.ciEventQueues.put(ciId, queue);
					final Map<GeneralEventListener, Set<GeneralEventType>> listeners = new ConcurrentHashMap<GeneralEventListener, Set<GeneralEventType>>();
					this.ciGeneralEventListeners.put(ciId, listeners);
					final Thread eventThread = new EventHandlerThread(listeners, queue);
					this.ciEventThreads.put(ciId, eventThread);
					eventThread.start();
					this.authManager.updateComponent(ciId, this.baseConfigController.getCIRights(ciId));
					conn.setUsable(true);
					final ControlInterface proxy = this.timeLimiter.newProxy(ci, ControlInterface.class, Constants.TIMEOUT_SECONDS___CI_MANAGEMENT, TimeUnit.SECONDS);
					try {
						proxy.startup();
					} catch (final UncheckedTimeoutException e1) {
						this.logConnector.log(LogEventLevelType.WARNING, "Timeout while starting control interface with id " + ciId + "(" + this.baseConfigController.getCIName(ciId) + ")");
					} catch (final Exception e) {
						this.logConnector.log(e);
					}
				}
			}
		}
		this.dataLock.unlock();
		return ci;
	}

	/**
	 * Gets a new control interface instance.
	 *
	 * @param ciType the CI type
	 * @param ciId the CI ID
	 * @param ciConf the CI configuration
	 * @param conn the CI connector
	 * @return the control interface instance
	 */
	private ControlInterface getControlInterfaceInstance(final String ciType, final String ciId, final ComponentConfigurationController ciConf, final ControlInterfaceConnector conn) {
		if ((ciType == null) || ciType.isEmpty()) {
			return null;
		}
		ControlInterface ci = null;
		if (this.controlInterfaceClasses.containsKey(ciType)) {
			final Class<? extends AbstractControlInterface> impl = this.controlInterfaceClasses.get(ciType);
			if (impl != null) {
				try {
					ci = impl.getConstructor(ControlInterfaceConnector.class, ComponentConfigurationController.class, LogConnector.class).newInstance(conn, ciConf, new LogConnector(this.coreCIActionHandler, LogEventSourceType.CI, ciId));
				} catch (final Exception e) {
					this.logConnector.log(e);
				}
			}
		} else {
			this.logConnector.log(LogEventLevelType.ERROR, "cannot find ci type " + ciType);
		}
		return ci;
	}

	/**
	 * Gets the event queues for delivering events (one for every control interface, filtering is done in the next step).
	 *
	 * @return the event queues
	 */
	Map<String, BlockingQueue<GeneralEvent>> getEventQueues() {
		return this.ciEventQueues;
	}

	/**
	 * Gets the specific type of a given event.
	 * <p>
	 * TODO: Casts are costly, finde a better solution.
	 *
	 * @param event the event
	 * @return the event type
	 */
	GeneralEventType getEventType(final GeneralEvent event) {
		// this is a bit ugly and may change in the future
		if (event instanceof ConnectionUpdateEvent) {
			return GeneralEventType.CONNECTION_UPDATE;
		} else if (event instanceof DataElementEvent) {
			return GeneralEventType.DATA_ELEMENT;
		} else if (event instanceof LogEvent) {
			return GeneralEventType.LOG;
		} else if (event instanceof ModuleActivityEvent) {
			return GeneralEventType.MODULE_ACTIVITY;
		} else if (event instanceof ModuleUpdateEvent) {
			return GeneralEventType.MODULE_UPDATE;
		} else if (event instanceof PortUpdateEvent) {
			return GeneralEventType.PORT_UPDATE;
		} else if (event instanceof PortUpdateEvent) {
			return GeneralEventType.PROVIDER_STATE;
		} else if (event instanceof SystemStateEvent) {
			return GeneralEventType.SYSTEM_STATE;
		} else {
			return GeneralEventType.GENERAL_EVENT;
		}
	}

	/**
	 * Gets the module.
	 *
	 * @param moduleId the module id
	 * @param instanciateNewIfNeccessary the instanciate new if neccessary
	 * @return the module
	 */
	Module getModule(final String moduleId, final boolean instanciateNewIfNeccessary) {
		if ((moduleId == null) || moduleId.isEmpty()) {
			return null;
		}
		this.dataLock.lock();
		Module module = this.modules.get(moduleId);
		if ((module == null) && instanciateNewIfNeccessary) {
			final ComponentConfigurationController modConf = this.baseConfigController.getModuleConfiguration(moduleId);
			String type;
			try {
				type = modConf.getComponentType();
				if (modConf != null) {
					module = getModuleInstance(type, moduleId, modConf);
					if (module != null) {
						this.modules.put(moduleId, module);
						this.authManager.updateComponent(moduleId, this.baseConfigController.getModuleRights(moduleId));
					}
				}
			} catch (final DatabaseException e) {
				this.logConnector.log(e);
			}
		}
		this.dataLock.unlock();
		return module;
	}

	/**
	 * Gets a new module instance.
	 *
	 * @param moduleType the module type
	 * @param moduleId the module ID
	 * @param moduleConf the module configuration
	 * @return the module instance
	 */
	private Module getModuleInstance(final String moduleType, final String moduleId, final ComponentConfigurationController moduleConf) {
		if ((moduleType == null) || moduleType.isEmpty()) {
			return null;
		}
		Module module = null;
		if (this.prosumerClasses.containsKey(moduleType)) {
			final Class<? extends AbstractProsumer> impl = this.prosumerClasses.get(moduleType);
			if (impl != null) {
				try {
					module = impl.getConstructor(ProsumerConnector.class, ComponentConfigurationController.class, LogConnector.class).newInstance(new ProsumerConnector(this.broker.getModuleActionHandler(), moduleId), moduleConf, new LogConnector(this.coreCIActionHandler, LogEventSourceType.MODULE, moduleId));
				} catch (final Exception e) {
					this.logConnector.log(e);
				}
			}
		} else if (this.prosumerProviderClasses.containsKey(moduleType)) {
			final Class<? extends AbstractProsumerProvider> impl = this.prosumerProviderClasses.get(moduleType);
			if (impl != null) {
				try {
					module = impl.getConstructor(ProsumerConnector.class, ProviderConnector.class, ComponentConfigurationController.class, LogConnector.class).newInstance(new ProsumerConnector(this.broker.getModuleActionHandler(), moduleId), new ProviderConnector(this.broker.getModuleActionHandler(), moduleId), moduleConf, new LogConnector(this.coreCIActionHandler, LogEventSourceType.MODULE, moduleId));
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					this.logConnector.log(e);
				}
			}
		} else if (this.providerClasses.containsKey(moduleType)) {
			final Class<? extends AbstractProvider> impl = this.providerClasses.get(moduleType);
			if (impl != null) {
				try {
					module = impl.getConstructor(ProviderConnector.class, ComponentConfigurationController.class, LogConnector.class).newInstance(new ProviderConnector(this.broker.getModuleActionHandler(), moduleId), moduleConf, new LogConnector(this.coreCIActionHandler, LogEventSourceType.MODULE, moduleId));
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					this.logConnector.log(e);
				}
			}
		} else {
			this.logConnector.log(LogEventLevelType.ERROR, "cannot find module type " + moduleType);
		}
		return module;
	}

	/**
	 * Checks if a given ID corresponds to an active control interface.
	 *
	 * @param ciId the CI ID
	 * @return true, if ID corresponds to an active control interface
	 */
	boolean isActiveControlInterface(final String ciId) {
		if (ciId == null) {
			return false;
		} else {
			return this.cis.containsKey(ciId);
		}
	}

	/**
	 * Checks if a given ID corresponds to an active module.
	 *
	 * @param moduleId the module ID
	 * @return true, if ID corresponds to an active module
	 */
	boolean isActiveModule(final String moduleId) {
		if (moduleId == null) {
			return false;
		} else {
			return this.modules.containsKey(moduleId);
		}
	}

	/**
	 * Loads available module and control interface implementations by using reflection.
	 */
	private void loadAvailableModuleAndControlInterfaceImpls() {
		final Reflections reflections = new Reflections(new ConfigurationBuilder().addUrls(ClasspathHelper.forJavaClassPath()).setScanners(new SubTypesScanner()));

		final Set<Class<? extends AbstractProsumer>> prosumers = reflections.getSubTypesOf(AbstractProsumer.class);
		final Set<Class<? extends AbstractProsumerProvider>> prosumerProviders = reflections.getSubTypesOf(AbstractProsumerProvider.class);
		final Set<Class<? extends AbstractProvider>> providers = reflections.getSubTypesOf(AbstractProvider.class);
		final Set<Class<? extends AbstractControlInterface>> controlInterfaces = reflections.getSubTypesOf(AbstractControlInterface.class);
		for (final Class<? extends AbstractProsumer> impl : prosumers) {
			this.prosumerClasses.put(impl.getName(), impl);
		}
		for (final Class<? extends AbstractProsumerProvider> impl : prosumerProviders) {
			this.prosumerProviderClasses.put(impl.getName(), impl);
		}
		for (final Class<? extends AbstractProvider> impl : providers) {
			this.providerClasses.put(impl.getName(), impl);
		}
		for (final Class<? extends AbstractControlInterface> impl : controlInterfaces) {
			this.controlInterfaceClasses.put(impl.getName(), impl);
		}
	}

	/**
	 * Removes (stops) all control interfaces except a given one. Used at system shutdown.
	 *
	 * @param preservedCIId the ID of the CI to preserve
	 * @return true, if successful
	 */
	boolean removeAllCIsExcept(final String preservedCIId) {
		boolean result = true;
		this.dataLock.lock();
		for (final String ciId : this.cis.keySet()) {
			if (ciId.equals(preservedCIId)) {
				continue;
			}
			result &= removeCI(ciId, false);
		}
		this.dataLock.unlock();
		return result;
	}

	/**
	 * Removes a control interface, optionally also removing from database.
	 *
	 * @param ciId the CI ID
	 * @param removeFromDB set to true to remove from database
	 * @return true, if successful
	 */
	boolean removeCI(final String ciId, final boolean removeFromDB) {
		boolean result = false;
		this.dataLock.lock();
		final ControlInterface ci = this.cis.remove(ciId);
		if (ci != null) {
			this.authManager.removeComponent(ciId);
			final Thread thread = this.ciEventThreads.get(ciId);
			if (thread != null) {
				thread.interrupt();
			}
			this.ciEventThreads.remove(ciId);
			this.ciEventQueues.remove(ciId);
			this.ciGeneralEventListeners.remove(ciId);
			final ControlInterface proxy = this.timeLimiter.newProxy(ci, ControlInterface.class, Constants.TIMEOUT_SECONDS___CI_MANAGEMENT, TimeUnit.SECONDS);
			try {
				proxy.shutdown();
				result = true;
			} catch (final UncheckedTimeoutException e1) {
				this.logConnector.log(LogEventLevelType.WARNING, "Timeout while shutting down control interface with id " + ciId + "(" + this.baseConfigController.getCIName(ciId) + ")");
			} catch (final Exception e) {
				this.logConnector.log(e);
			}
			if (removeFromDB) {
				result = this.baseConfigController.removeCIConfiguration(ciId);
			}
		}
		this.dataLock.unlock();
		return result;
	}

	/**
	 * Removes a general event listener.
	 *
	 * @param ciId the CI ID to remove listener for
	 * @param generalEventListener the general event listener to remove
	 * @return true, if successful
	 */
	boolean removeGeneralEventListener(final String ciId, final GeneralEventListener generalEventListener) {
		final Map<GeneralEventListener, Set<GeneralEventType>> listeners = this.ciGeneralEventListeners.get(ciId);
		if (listeners == null) {
			return false;
		} else {
			return listeners.remove(generalEventListener) != null;
		}
	}

	/**
	 * Removes a module, optionally also removing from database.
	 *
	 * @param moduleId the module ID
	 * @param removeFromDB set to true to remove from database
	 * @return true, if successful
	 */
	// module states are managed by broker instance
	boolean removeModule(final String moduleId, final boolean removeFromDB) {
		boolean result = true;
		this.dataLock.lock();
		this.authManager.removeComponent(moduleId);
		this.modules.remove(moduleId);
		if (removeFromDB) {
			result = this.baseConfigController.removeModuleConfiguration(moduleId);
		}
		this.dataLock.unlock();
		return result;
	}

	/**
	 * Rename a control interface.
	 *
	 * @param ciId the CI ID
	 * @param newName the new name
	 * @return true, if successful
	 */
	boolean renameControlInterface(final String ciId, final String newName) {
		boolean result = false;
		this.dataLock.lock();
		if (this.cis.containsKey(ciId) && (this.baseConfigController.getCIConfiguration(newName) == null)) {
			result = this.baseConfigController.setCIName(ciId, newName);
		}
		this.dataLock.unlock();
		return result;
	}

	/**
	 * Rename module.
	 *
	 * @param moduleId the module ID
	 * @param newName the new name
	 * @return true, if successful
	 */
	boolean renameModule(final String moduleId, final String newName) {
		boolean result = false;
		this.dataLock.lock();
		if (this.modules.containsKey(moduleId) && (this.baseConfigController.getModuleConfiguration(newName) == null)) {
			result = this.baseConfigController.setModuleName(moduleId, newName);
		}
		this.dataLock.unlock();
		return result;
	}

	/**
	 * Sets rights of a control interface.
	 *
	 * @param ciId the CI ID
	 * @param newRights the new rights
	 * @return true, if successful
	 */
	boolean setCIRights(final String ciId, final int newRights) {
		boolean result = true;
		this.dataLock.lock();
		if (this.cis.containsKey(ciId)) {
			result = this.baseConfigController.setCIRights(ciId, newRights);
			if (result) {
				this.authManager.updateComponent(ciId, newRights);
			}
		}
		this.dataLock.unlock();
		return result;
	}

	/**
	 * Sets rights of a module.
	 *
	 * @param moduleId the module ID
	 * @param newRights the new rights
	 * @return true, if successful
	 */
	boolean setModuleRights(final String moduleId, final int newRights) {
		boolean result = true;
		this.dataLock.lock();
		if (this.modules.containsKey(moduleId)) {
			result = this.baseConfigController.setModuleRights(moduleId, newRights);
			if (result) {
				this.authManager.updateComponent(moduleId, newRights);
			}
		}
		this.dataLock.unlock();
		return result;
	}

	/**
	 * Starts all control interfaces that have valid configurations in database.
	 *
	 * @return true, if successful
	 */
	boolean startCIsFromDB() {
		boolean result = true;
		this.dataLock.lock();
		for (final String ciId : this.baseConfigController.getCIConfigurations().keySet()) {
			boolean curResult = false;
			final ComponentConfigurationController compConf = this.baseConfigController.getCIConfiguration(ciId);
			if ((compConf != null) && compConf.isConfigurationValid()) {
				final ControlInterface ci = getControlInterface(ciId, true);
				if (ci != null) {
					curResult = true;
				}
			}
			result &= curResult;
		}
		this.dataLock.unlock();
		return result;
	}
}
