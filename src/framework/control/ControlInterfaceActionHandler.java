package framework.control;

import helper.ObjectValidator;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import module.iface.Module;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import controlinterface.iface.GeneralEventListener;
import framework.constants.Constants;
import framework.constants.ControlInterfaceRight;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.ControlInterfaceException;
import framework.exception.WrongSystemStateException;
import framework.model.event.ConnectionUpdateEvent;
import framework.model.event.GeneralEvent;
import framework.model.event.LogEvent;
import framework.model.event.ModuleActivityEvent;
import framework.model.event.ModuleUpdateEvent;
import framework.model.event.PortUpdateEvent;
import framework.model.event.SystemStateEvent;
import framework.model.event.type.GeneralEventType;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.LogEventSourceType;
import framework.model.event.type.ModuleUpdateEventType;
import framework.model.event.type.PortUpdateEventType;
import framework.model.event.type.SystemStateType;
import framework.model.summary.BaseConfigurationSummary;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.ControlInterfaceSummary;
import framework.model.summary.ModuleSummary;
import framework.model.summary.PortSummary;

/**
 * Holds all methods for control interfaces and is mainly used by the {@link ControlInterfaceConnector}. It also excessively checks system states and control
 * interface rights with every call.
 * <p>
 * TODO:<br>
 * - Log to System.out/err if there are no CIs that have listeners registered.<br>
 * - Reevaluate the announcement system, should we really use Threads/Services for this?
 *
 * @author Stefan Werner
 */
final class ControlInterfaceActionHandler {

	private final ExecutorService announcementService = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder().setNameFormat(ControlInterfaceActionHandler.class.getSimpleName() + "-general-announce-%d").build());
	private final ComponentAuthorizationManager authManager;
	private final Broker broker;
	private ComponentInstanceManager componentInstanceManager = null;
	private final Thread connectionUpdateEventAnnouncementThread;
	private final Runnable connectionUpdateEventAnnouncmentHandler = new Runnable() {

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				ConnectionUpdateEvent event;
				try {
					event = ControlInterfaceActionHandler.this.connectionUpdateEventQueue.take();
				} catch (final InterruptedException e) {
					break;
				}
				for (final String ciID : ControlInterfaceActionHandler.this.eventQueues.keySet()) {
					if (ControlInterfaceActionHandler.this.authManager.hasRights(ciID, ControlInterfaceRight.RCV_CONN_UPDATE)) {
						final BlockingQueue<GeneralEvent> queue = ControlInterfaceActionHandler.this.eventQueues.get(ciID);
						if (queue != null) {
							ConnectionUpdateEvent newEvent;
							if (ControlInterfaceActionHandler.this.authManager.hasRights(ciID, ControlInterfaceRight.CAN_MISS_EVENTS)) {
								newEvent = new ConnectionUpdateEvent(event.connectionSummary, event.type, false);
							} else {
								newEvent = new ConnectionUpdateEvent(event.connectionSummary, event.type, true);
							}
							queue.remove(newEvent);
							queue.add(newEvent);
						}
					}
				}
			}
		}
	};
	private final BlockingQueue<ConnectionUpdateEvent> connectionUpdateEventQueue = new LinkedBlockingQueue<ConnectionUpdateEvent>();
	private final Core core;
	private Map<String, BlockingQueue<GeneralEvent>> eventQueues = new HashMap<String, BlockingQueue<GeneralEvent>>();
	private final LogConnector logConnector;
	private final Thread moduleActivityEventAnnouncementThread;
	private final Runnable moduleActivityEventAnnouncmentHandler = new Runnable() {

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				ModuleActivityEvent event;
				try {
					event = ControlInterfaceActionHandler.this.moduleActivityEventQueue.take();
				} catch (final InterruptedException e) {
					break;
				}
				for (final String ciID : ControlInterfaceActionHandler.this.eventQueues.keySet()) {
					if (ControlInterfaceActionHandler.this.authManager.hasRights(ciID, ControlInterfaceRight.RCV_MOD_ACT)) {
						final BlockingQueue<GeneralEvent> queue = ControlInterfaceActionHandler.this.eventQueues.get(ciID);
						if (queue != null) {
							queue.add(event);
						}
					}
				}
			}
		}
	};
	private final BlockingQueue<ModuleActivityEvent> moduleActivityEventQueue = new LinkedBlockingQueue<ModuleActivityEvent>();
	private final ExecutorService stateService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(ControlInterfaceActionHandler.class.getSimpleName() + "-announce-state-%d").build());
	private final TimeLimiter timeLimiter = new SimpleTimeLimiter();

	/**
	 * Instantiates a new control interface action handler.
	 *
	 * @param core the core
	 * @param broker the broker
	 * @param authController the authentication controller
	 */
	ControlInterfaceActionHandler(final Core core, final Broker broker, final ComponentAuthorizationManager authController) {
		this.core = core;
		this.logConnector = new LogConnector(this, LogEventSourceType.FRAMEWORK, Constants.COMPONENT_ID___CORE);
		this.broker = broker;
		this.authManager = authController;
		this.moduleActivityEventAnnouncementThread = new Thread(this.moduleActivityEventAnnouncmentHandler, ControlInterfaceActionHandler.class.getSimpleName() + "-announce-module-activity-event");
		// TODO: Might be a bad idea to reduce priority of this thread (monitoring vs. performance).
		this.moduleActivityEventAnnouncementThread.setPriority(Thread.NORM_PRIORITY - 1);
		this.moduleActivityEventAnnouncementThread.start();
		this.connectionUpdateEventAnnouncementThread = new Thread(this.connectionUpdateEventAnnouncmentHandler, ControlInterfaceActionHandler.class.getSimpleName() + "-announce-connection-update");
		// TODO: Might be a bad idea to reduce priority of this thread (monitoring vs. performance).
		this.connectionUpdateEventAnnouncementThread.setPriority(Thread.NORM_PRIORITY - 1);
		this.connectionUpdateEventAnnouncementThread.start();
	}

	/**
	 * Adds a module connection.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param connectionSummary the connection summary
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean addConnection(final String ciId, final ConnectionSummary connectionSummary) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if (connectionSummary == null) {
			throw new ControlInterfaceException("invalid connection");
		}
		return this.broker.addConnection(connectionSummary);
	}

	/**
	 * Adds a new control interface.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciId the calling CI ID
	 * @param ciType the CI type
	 * @param rights the rights
	 * @return the CI ID
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	String addControlInterface(final String ciId, final String ciType, final int rights) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_CIS);
		if ((ciType == null) || ciType.isEmpty() || (rights < 0)) {
			throw new ControlInterfaceException("invalid ciType/rights");
		}
		if (this.componentInstanceManager == null) {
			return null;
		}
		if (!this.componentInstanceManager.getAvailableControlInterfaceTypes().contains(ciType)) {
			throw new ControlInterfaceException("invalid ciType");
		}
		return this.componentInstanceManager.addNewControlInterface(ciType, rights);
	}

	/**
	 * Adds a general event listener.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING
	 * <p>
	 * Required rights: RCV_CONN_UPDATE, RCV_LOG_EVENT, RCV_MOD_ACT, RCV_MOD_AND_PORT_UPDATE (depends on event type)
	 * <p>
	 * IMPORTANT: You can actually skip desiredEventTypes or just use {@link framework.model.event.type.GeneralEventType.GeneralEvent} to add an "catch all"
	 * listener but this is discouraged to avoid flooding with unnecessary event. Of course CI will only receive events it holds appropriate rights for.
	 *
	 * @param ciId the calling CI ID
	 * @param generalEventListener the general event listener
	 * @param desiredEventTypes the desired event types
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean addGeneralEventListener(final String ciId, final GeneralEventListener generalEventListener, final GeneralEventType... desiredEventTypes) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING);
		if (desiredEventTypes == null) {
			throw new ControlInterfaceException("illegal argument");
		}
		if (desiredEventTypes.length == 0) {
			this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.RCV_CONN_UPDATE, ControlInterfaceRight.RCV_LOG_EVENT, ControlInterfaceRight.RCV_MOD_ACT, ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE);
		} else {
			for (final GeneralEventType type : desiredEventTypes) {
				if (type == null) {
					throw new ControlInterfaceException("illegal argument");
				}
				if (type == GeneralEventType.CONNECTION_UPDATE) {
					this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.RCV_CONN_UPDATE);
				} else if (type == GeneralEventType.DATA_ELEMENT) {
					throw new AuthorizationException("Control Interfaces cannot listen for DataElement events");
				} else if (type == GeneralEventType.LOG) {
					this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.RCV_LOG_EVENT);
				} else if (type == GeneralEventType.MODULE_ACTIVITY) {
					this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.RCV_MOD_ACT);
				} else if (type == GeneralEventType.MODULE_UPDATE) {
					this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE);
				} else if (type == GeneralEventType.PORT_UPDATE) {
					this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE);
				} else if (type == GeneralEventType.PROVIDER_STATE) {
					throw new AuthorizationException("Control Interfaces cannot listen for Provider state events");
				} else if (type == GeneralEventType.GENERAL_EVENT) {
					this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.RCV_CONN_UPDATE, ControlInterfaceRight.RCV_LOG_EVENT, ControlInterfaceRight.RCV_MOD_ACT, ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE);
				}
			}
		}
		if (this.componentInstanceManager == null) {
			return false;
		}
		return this.componentInstanceManager.addGeneralEventListener(ciId, generalEventListener, desiredEventTypes);
	}

	/**
	 * Adds a new module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param moduleType the module type
	 * @param rights the rights
	 * @return the module summary
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	ModuleSummary addModule(final String ciId, final String moduleType, final int rights) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if ((moduleType == null) || moduleType.isEmpty() || (rights < 0)) {
			throw new ControlInterfaceException("invalid moduleType/rights");
		}
		if (this.componentInstanceManager == null) {
			return null;
		}
		if (!this.componentInstanceManager.getAvailableModuleTypes().contains(moduleType)) {
			throw new ControlInterfaceException("invalid moduleType");
		}
		final String moduleId = this.componentInstanceManager.addNewModule(moduleType, rights);
		if (moduleId == null) {
			return null;
		}
		final Module module = this.componentInstanceManager.getModule(moduleId, true);
		if (module != null) {
			try {
				this.broker.initializeNewModule(moduleId, module);
				return this.broker.getModuleSummary(moduleId);
			} catch (final BrokerException e) {
				this.logConnector.log(e);
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * Announces a connection update to event queue.
	 *
	 * @param event the connection update event
	 */
	void announceConnectionUpdate(final ConnectionUpdateEvent event) {
		this.connectionUpdateEventQueue.add(event);
	}

	/**
	 * Announces a log element to event queue.
	 *
	 * @param event the log element event
	 */
	void announceLogElement(final LogEvent event) {
		try {
			this.announcementService.execute(new Runnable() {
				@Override
				public void run() {
					for (final String ciID : ControlInterfaceActionHandler.this.eventQueues.keySet()) {
						if (ControlInterfaceActionHandler.this.authManager.hasRights(ciID, ControlInterfaceRight.RCV_LOG_EVENT)) {
							final BlockingQueue<GeneralEvent> queue = ControlInterfaceActionHandler.this.eventQueues.get(ciID);
							if (queue != null) {
								queue.add(event);
							}
						}
					}
				}
			});
		} catch (final RejectedExecutionException e) {
			// must obviously be ignored here to avoid loop
		}
	}

	/**
	 * Announces a module activity to event queue.
	 *
	 * @param event the module activity event
	 */
	void announceModuleActivity(final ModuleActivityEvent event) {
		this.moduleActivityEventQueue.add(event);
	}

	/**
	 * Announces a module update to event queue.
	 * <p>
	 * TODO: Wrap in an event object.
	 *
	 * @param moduleSummary the module summary
	 * @param type the event type
	 */
	void announceModuleUpdate(final ModuleSummary moduleSummary, final ModuleUpdateEventType type) {
		try {
			this.announcementService.execute(new Runnable() {

				@Override
				public void run() {
					for (final String ciID : ControlInterfaceActionHandler.this.eventQueues.keySet()) {
						if (ControlInterfaceActionHandler.this.authManager.hasRights(ciID, ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE)) {
							final BlockingQueue<GeneralEvent> queue = ControlInterfaceActionHandler.this.eventQueues.get(ciID);
							if (queue != null) {
								ModuleUpdateEvent event;
								if (ControlInterfaceActionHandler.this.authManager.hasRights(ciID, ControlInterfaceRight.CAN_MISS_EVENTS)) {
									event = new ModuleUpdateEvent(moduleSummary, type, false);
								} else {
									event = new ModuleUpdateEvent(moduleSummary, type, true);
								}
								queue.remove(event);
								queue.add(event);
							}
						}
					}
				}
			});
		} catch (final RejectedExecutionException e) {
			this.logConnector.log(e);
		}
	}

	/**
	 * Announces a port update to event queue.
	 * <p>
	 * TODO: Wrap in an event object.
	 *
	 * @param portSummary the port summary
	 * @param type the event type
	 */
	void announcePortUpdate(final PortSummary portSummary, final PortUpdateEventType type) {
		try {
			this.announcementService.execute(new Runnable() {

				@Override
				public void run() {
					for (final String ciID : ControlInterfaceActionHandler.this.eventQueues.keySet()) {
						if (ControlInterfaceActionHandler.this.authManager.hasRights(ciID, ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE)) {
							final BlockingQueue<GeneralEvent> queue = ControlInterfaceActionHandler.this.eventQueues.get(ciID);
							if (queue != null) {
								PortUpdateEvent event;
								if (ControlInterfaceActionHandler.this.authManager.hasRights(ciID, ControlInterfaceRight.CAN_MISS_EVENTS)) {
									event = new PortUpdateEvent(portSummary, type, false);
								} else {
									event = new PortUpdateEvent(portSummary, type, true);
								}
								queue.remove(event);
								queue.add(event);
							}
						}
					}
				}
			});
		} catch (final RejectedExecutionException e) {
			this.logConnector.log(e);
		}
	}

	/**
	 * Announces a system state to event queue.
	 *
	 * @param type the event type
	 */
	void announceSystemState(final SystemStateType type) {
		try {
			this.announcementService.execute(new Runnable() {

				@Override
				public void run() {
					for (final String ciID : ControlInterfaceActionHandler.this.eventQueues.keySet()) {
						final BlockingQueue<GeneralEvent> queue = ControlInterfaceActionHandler.this.eventQueues.get(ciID);
						if (queue != null) {
							queue.add(new SystemStateEvent(type));
						}
					}
				}
			});
		} catch (final RejectedExecutionException e) {
			this.logConnector.log(e);
		}
	}

	/**
	 * Checks system state.
	 *
	 * @param forbiddenStates the forbidden states
	 * @throws ControlInterfaceException if in wrong state
	 */
	private void checkSystemStateNot(final SystemStateType... forbiddenStates) throws ControlInterfaceException {
		for (final SystemStateType state : forbiddenStates) {
			if (this.core.getCurrentSystemState() == state) {
				throw new WrongSystemStateException("wrong system state: " + this.core.getCurrentSystemState().name());
			}
		}
	}

	/**
	 * Exits the system.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP, BROKER_RUNNING
	 * <p>
	 * Required rights: CONTROL_STATE
	 *
	 * @param ciId the calling CI ID
	 * @param force set to true to force exit even if components fail to stop
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	void exit(final String ciId, final boolean force) throws AuthorizationException, ControlInterfaceException {
		if (!force) {
			checkSystemStateNot(SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP, SystemStateType.BROKER_RUNNING);
		}
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.CONTROL_STATE);
		if (this.componentInstanceManager == null) {
			return;
		}
		if (this.broker.isRunning() && !force) {
			return;
		}
		this.stateService.execute(new Runnable() {

			@Override
			public void run() {
				ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.SYSTEM_EXITING);
				if (!force && !ControlInterfaceActionHandler.this.componentInstanceManager.removeAllCIsExcept(ciId)) {
					ControlInterfaceActionHandler.this.logConnector.log(LogEventLevelType.WARNING, "A control interface instance failed to stop. Aborting.");
					ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.SYSTEM_OR_BROKER_ERROR);
				} else {
					// componentInstanceManager.removeCI(ciId, false);
					ControlInterfaceActionHandler.this.core.exit(ciId, force);
					ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.SYSTEM_OR_BROKER_ERROR);
				}
			}
		});
	}

	/**
	 * Exports configuration.
	 * <p>
	 * Required rights: MANAGE_DATABASE
	 *
	 * @param ciId the calling CI ID
	 * @param out the output stream to write to
	 * @param exportPortConnections set to true to export port connections
	 * @param moduleIdsToExport the module IDs to export
	 * @param ciIdsToExport the CI IDs to export
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean exportConfiguration(final String ciId, final OutputStream out, final boolean exportPortConnections, final Set<String> moduleIdsToExport, final Set<String> ciIdsToExport) throws AuthorizationException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_DATABASE);
		return this.core.exportConfiguration(out, exportPortConnections, moduleIdsToExport, ciIdsToExport);
	}

	/**
	 * Gets all active control interfaces.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciId the calling CI ID
	 * @return the active control interfaces
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	Set<ControlInterfaceSummary> getActiveControlInterfaces(final String ciId) throws AuthorizationException, ControlInterfaceException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_CIS);
		if (this.componentInstanceManager == null) {
			return new HashSet<ControlInterfaceSummary>();
		}
		try {
			final Set<String[]> actCIs = this.componentInstanceManager.getActiveControlInterfacesAndTypes();
			final Set<ControlInterfaceSummary> result = new HashSet<ControlInterfaceSummary>();
			for (final String[] ci : actCIs) {
				if ((ci.length < 3) || !ObjectValidator.checkArgsNotNull((Object[]) ci)) {
					continue;
				}
				result.add(new ControlInterfaceSummary(ci[0], ci[1], ci[2], this.authManager.getRights(ci[0])));
			}
			return ImmutableSet.copyOf(result);
		} catch (final NullPointerException npe) {
			this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
			this.logConnector.log(npe);
			throw new ControlInterfaceException("NULL key/value found in returned Set/Map");
		}
	}

	/**
	 * Gets all active modules.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: READ_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @return the active modules
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	Set<ModuleSummary> getActiveModules(final String ciId) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.READ_MODULES_AND_CONNECTIONS);
		final Set<ModuleSummary> result = this.broker.getModuleSummaries();
		try {
			return ImmutableSet.copyOf(result);
		} catch (final NullPointerException npe) {
			this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
			this.logConnector.log(npe);
			throw new ControlInterfaceException("NULL key/value found in returned Set/Map");
		}
	}

	/**
	 * Gets the available control interface types.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciId the calling CI ID
	 * @return the available control interface types
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Set<String> getAvailableControlInterfaceTypes(final String ciId) throws AuthorizationException, ControlInterfaceException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_CIS);
		if (this.componentInstanceManager == null) {
			return new HashSet<String>();
		}
		final Set<String> result = this.componentInstanceManager.getAvailableControlInterfaceTypes();
		try {
			return ImmutableSet.copyOf(result);
		} catch (final NullPointerException npe) {
			this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
			this.logConnector.log(npe);
			throw new ControlInterfaceException("NULL key/value found in returned Set/Map");
		}
	}

	/**
	 * Gets the available module types.
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @return the available module types
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Set<String> getAvailableModuleTypes(final String ciId) throws AuthorizationException, ControlInterfaceException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if (this.componentInstanceManager == null) {
			return new HashSet<String>();
		}
		final Set<String> result = this.componentInstanceManager.getAvailableModuleTypes();
		try {
			return ImmutableSet.copyOf(result);
		} catch (final NullPointerException npe) {
			this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
			this.logConnector.log(npe);
			throw new ControlInterfaceException("NULL key/value found in returned Set/Map");
		}
	}

	/**
	 * Gets the base configuration from a given input stream.
	 * <p>
	 * Required rights: MANAGE_DATABASE
	 *
	 * @param ciId the calling CI ID
	 * @param in the input stream to read from
	 * @return the base configuration summary
	 * @throws AuthorizationException if rights are insufficient
	 */
	BaseConfigurationSummary getBaseConfiguration(final String ciId, final InputStream in) throws AuthorizationException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_DATABASE);
		return this.core.getBaseConfiguration(in);
	}

	/**
	 * Gets all connections (active and inactive).
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: READ_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @return the connections
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	Set<ConnectionSummary> getConnections(final String ciId) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.READ_MODULES_AND_CONNECTIONS);
		final Set<ConnectionSummary> result = this.broker.getConnectionSummaries();
		try {
			return ImmutableSet.copyOf(result);
		} catch (final NullPointerException npe) {
			this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
			this.logConnector.log(npe);
			throw new ControlInterfaceException("NULL key/value found in returned Set/Map");
		}
	}

	/**
	 * Gets the rights of a given control interface.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ownCIId the own CI ID
	 * @param otherCIId the other CI ID
	 * @return the control interface rights
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	int getControlInterfaceRights(final String ownCIId, final String otherCIId) throws AuthorizationException, ControlInterfaceException {
		this.authManager.verifyAllComponentRights(ownCIId, ControlInterfaceRight.MANAGE_CIS);
		if (this.componentInstanceManager == null) {
			return -1;
		}
		if (!this.componentInstanceManager.isActiveControlInterface(otherCIId)) {
			throw new ControlInterfaceException("invalid otherCIId");
		}
		if (otherCIId != null) {
			return this.authManager.getRights(otherCIId);
		} else {
			return -1;
		}
	}

	/**
	 * Gets the current base configuration (from database).
	 * <p>
	 * Required rights: MANAGE_DATABASE
	 *
	 * @param ciId the calling CI ID
	 * @return the current base configuration
	 * @throws AuthorizationException if rights are insufficient
	 */
	BaseConfigurationSummary getCurrentBaseConfiguration(final String ciId) throws AuthorizationException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_DATABASE);
		return this.core.getCurrentBaseConfiguration();
	}

	/**
	 * Gets the current system state.
	 *
	 * @return the current system state
	 */
	SystemStateType getCurrentSystemState() {
		return this.core.getCurrentSystemState();
	}

	/**
	 * Gets the rights of a given module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING
	 * <p>
	 * Required rights: READ_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param moduleId the module ID
	 * @return the module rights
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	int getModuleRights(final String ciId, final String moduleId) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.READ_MODULES_AND_CONNECTIONS);
		if (this.componentInstanceManager == null) {
			return -1;
		}
		if (!this.componentInstanceManager.isActiveModule(moduleId)) {
			throw new ControlInterfaceException("invalid moduleId");
		}
		if (ciId != null) {
			return this.authManager.getRights(moduleId);
		} else {
			return -1;
		}
	}

	/**
	 * Gets a new localization connector for this control interface.
	 *
	 * @param componentId the component id
	 * @return the new localization connector
	 */
	LocalizationConnector getNewLocalizationConnector(final String componentId) {
		return this.core.getNewLocalizationConnector(componentId);
	}

	/**
	 * Gets the own ID.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciId the calling CI ID
	 * @return the own ID
	 * @throws AuthorizationException if rights are insufficient
	 */
	String getOwnId(final String ciId) throws AuthorizationException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_CIS);
		return ciId;
	}

	/**
	 * Gets the own rights.
	 *
	 * @param ciId the calling CI ID
	 * @return the rights
	 */
	int getOwnRights(final String ciId) {
		if (ciId != null) {
			return this.authManager.getRights(ciId);
		} else {
			return -1;
		}
	}

	/**
	 * Gets the supported control interface commands from a given module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param moduleId the module ID
	 * @return the supported control interface commands (may be null, for example if module does not answer before a timeout occurs)
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	Set<String> getSupportedControlInterfaceCommands(final String ciId, final String moduleId) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if ((moduleId == null) || moduleId.isEmpty()) {
			throw new ControlInterfaceException("invalid moduleId");
		}
		if (this.componentInstanceManager == null) {
			return new HashSet<String>();
		}
		final Module module = this.componentInstanceManager.getModule(moduleId, false);
		if (module == null) {
			throw new ControlInterfaceException("invalid moduleId");
		} else {
			final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_COMMUNICATION, TimeUnit.SECONDS);
			try {
				final Set<String> result = proxy.getSupportedControlInterfaceCommands();
				if (result == null) {
					return result;
				}
				try {
					return ImmutableSet.copyOf(result);
				} catch (final NullPointerException npe) {
					this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
					this.logConnector.log(npe);
					throw new ControlInterfaceException("NULL key/value found in returned Set/Map");
				}
			} catch (final UncheckedTimeoutException e1) {
				try {
					final ModuleSummary summary = this.broker.getModuleSummary(moduleId);
					if (summary != null) {
						announceModuleUpdate(summary, ModuleUpdateEventType.FAIL_RESPOND);
					}
				} catch (final BrokerException e) {
					this.logConnector.log(e);
				}
				return null;
			} catch (final Exception e) {
				this.logConnector.log(e);
				throw new ControlInterfaceException("uncaught module exception received");
			}
		}
	}

	/**
	 * Gets the system data storage location.
	 * <p>
	 * Required rights: DIRECT_STORAGE_ACCESS
	 *
	 * @param ciId the calling CI ID
	 * @return the system data storage location
	 * @throws AuthorizationException if rights are insufficient
	 */
	String getSystemDataStorageLocation(final String ciId) throws AuthorizationException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.DIRECT_STORAGE_ACCESS);
		return this.core.getSystemDataStorageLocation();
	}

	/**
	 * Imports configuration partly.
	 * <p>
	 * Required rights: MANAGE_DATABASE
	 *
	 * @param ciId the calling CI ID
	 * @param in the input stream to read from
	 * @param importPortConnections set to true to import port connections
	 * @param moduleIdsToImport the module IDs to import
	 * @param ciIdsToImport the CI IDs to import
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean importConfiguration(final String ciId, final InputStream in, final boolean importPortConnections, final Set<String> moduleIdsToImport, final Set<String> ciIdsToImport) throws AuthorizationException {
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_DATABASE);
		return this.core.importConfiguration(in, importPortConnections, moduleIdsToImport, ciIdsToImport);
	}

	/**
	 * Refreshes a given connection.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: READ_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param connectionSummaryToUpdate the connection summary to update
	 * @return the updated connection summary
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	ConnectionSummary refreshConnection(final String ciId, final ConnectionSummary connectionSummary) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.READ_MODULES_AND_CONNECTIONS);
		if (connectionSummary == null) {
			throw new ControlInterfaceException("invalid connectionSummary");
		}
		return this.broker.updateConnection(connectionSummary);
	}

	/**
	 * Removes a connection.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param connectionSummary the connection summary
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean removeConnection(final String ciId, final ConnectionSummary connectionSummary) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if (connectionSummary == null) {
			throw new ControlInterfaceException("invalid connectionSummary");
		}
		return this.broker.removeConnection(connectionSummary);
	}

	/**
	 * Removes a control interface.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ownCIId the own CI ID
	 * @param otherCIId the other CI ID to remove
	 * @param removeFromDB set to true to remove from database
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean removeControlInterface(final String ownCIId, final String otherCIId, final boolean removeFromDB) throws AuthorizationException, ControlInterfaceException {
		this.authManager.verifyAllComponentRights(ownCIId, ControlInterfaceRight.MANAGE_CIS);
		if ((otherCIId == null) || otherCIId.isEmpty()) {
			throw new ControlInterfaceException("invalid ciId");
		}
		if (this.componentInstanceManager == null) {
			return false;
		}
		if (!this.componentInstanceManager.isActiveControlInterface(otherCIId)) {
			throw new ControlInterfaceException("invalid otherCIId");
		}
		// TODO: We allow CIs to remove itself for now.
		// if (otherCIId.equals(ownCIId)) {
		// throw new ControlInterfaceException("no self modification allowed");
		// }
		return this.componentInstanceManager.removeCI(otherCIId, removeFromDB);
	}

	/**
	 * Removes an own general event listener.
	 *
	 * @param ciId the calling CI ID
	 * @param generalEventListener the general event listener to remove
	 * @return true, if successful
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean removeGeneralEventListener(final String ciId, final GeneralEventListener generalEventListener) throws ControlInterfaceException {
		if (this.componentInstanceManager == null) {
			return false;
		}
		return this.componentInstanceManager.removeGeneralEventListener(ciId, generalEventListener);
	}

	/**
	 * Removes a module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param moduleId the module ID to remove
	 * @param removeFromDB set to true to remove it from database
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean removeModule(final String ciId, final String moduleId, final boolean removeFromDB) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if ((moduleId == null) || moduleId.isEmpty()) {
			throw new ControlInterfaceException("invalid moduleId");
		}
		if (this.componentInstanceManager == null) {
			return false;
		}
		if (!this.componentInstanceManager.isActiveModule(moduleId)) {
			throw new ControlInterfaceException("unknown moduleId");
		}
		if (this.componentInstanceManager == null) {
			return false;
		}
		boolean result = false;
		try {
			result = this.broker.removeModule(moduleId, removeFromDB) && this.componentInstanceManager.removeModule(moduleId, removeFromDB);
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
		return result;
	}

	/**
	 * Renames a control interface.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ownCIId the own CI ID
	 * @param otherCIId the other CI ID to rename
	 * @param newName the new name
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean renameControlInterface(final String ownCIId, final String otherCIId, final String newName) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING);
		this.authManager.verifyAllComponentRights(ownCIId, ControlInterfaceRight.MANAGE_CIS);
		if ((otherCIId == null) || otherCIId.isEmpty() || (newName == null) || newName.isEmpty()) {
			throw new ControlInterfaceException("invalid ciId/newName");
		}
		if (this.componentInstanceManager == null) {
			return false;
		}
		if (!this.componentInstanceManager.isActiveControlInterface(otherCIId)) {
			throw new ControlInterfaceException("invalid ciId");
		}
		if (this.componentInstanceManager.renameControlInterface(otherCIId, newName)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Renames a module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param moduleId the module ID to rename
	 * @param newName the new name
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean renameModule(final String ciId, final String moduleId, final String newName) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if ((moduleId == null) || moduleId.isEmpty() || (newName == null) || newName.isEmpty()) {
			throw new ControlInterfaceException("invalid moduleId/newName");
		}
		if (this.componentInstanceManager == null) {
			return false;
		}
		if (!this.componentInstanceManager.isActiveModule(moduleId)) {
			throw new ControlInterfaceException("invalid moduleId");
		}
		if (this.componentInstanceManager.renameModule(moduleId, newName)) {
			try {
				final ModuleSummary summary = this.broker.getModuleSummary(moduleId);
				if (summary != null) {
					announceModuleUpdate(summary, ModuleUpdateEventType.UPDATE);
				}
			} catch (final BrokerException e) {
				this.logConnector.log(e);
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Sends control interface command to given module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param moduleId the module ID to send command to
	 * @param command the command
	 * @param properties the properties (may be null)
	 * @return the answer from module (may be null, for example if module does not answer before a timeout occurs)
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	Map<String, String> sendControlInterfaceCommand(final String ciId, final String moduleId, final String command, Map<String, String> properties) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if ((moduleId == null) || moduleId.isEmpty() || (command == null) || command.isEmpty()) {
			throw new ControlInterfaceException("invalid moduleId/command");
		}
		try {
			if (properties != null) {
				properties = ImmutableMap.copyOf(properties);
			}
		} catch (final NullPointerException npe) {
			throw new ControlInterfaceException("invalid arguments: NULL key/value found in Set/Map");
		}
		if (this.componentInstanceManager == null) {
			return null;
		}
		final Module module = this.componentInstanceManager.getModule(moduleId, false);
		if (module == null) {
			throw new ControlInterfaceException("unknown moduleId");
		} else {
			final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_COMMUNICATION, TimeUnit.SECONDS);
			try {
				final Map<String, String> result = proxy.onControlInterfaceCommand(command, properties);
				if (result == null) {
					return result;
				}
				try {
					return ImmutableMap.copyOf(result);
				} catch (final NullPointerException npe) {
					this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
					this.logConnector.log(npe);
					throw new ControlInterfaceException("NULL key/value found in returned Set/Map");
				}
			} catch (final UncheckedTimeoutException e1) {
				try {
					final ModuleSummary summary = this.broker.getModuleSummary(moduleId);
					if (summary != null) {
						announceModuleUpdate(summary, ModuleUpdateEventType.FAIL_RESPOND);
					}
				} catch (final BrokerException e) {
					this.logConnector.log(e);
				}
				return null;
			} catch (final Exception e) {
				this.logConnector.log(e);
				throw new ControlInterfaceException("uncaught module exception received");
			}
		}
	}

	/**
	 * Sets the component instance manager.
	 *
	 * @param manager the new component instance manager
	 */
	void setComponentInstanceManager(final ComponentInstanceManager manager) {
		this.componentInstanceManager = manager;
		this.eventQueues = manager.getEventQueues();
	}

	/**
	 * Sets the rights of a given control interface.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ownCIId the own CI ID
	 * @param otherCIId the other CI ID to set rights for
	 * @param newRights the new rights
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean setControlInterfaceRights(final String ownCIId, final String otherCIId, final int newRights) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING);
		this.authManager.verifyAllComponentRights(ownCIId, ControlInterfaceRight.MANAGE_CIS);
		if ((otherCIId == null) || otherCIId.isEmpty() || (newRights < 0)) {
			throw new ControlInterfaceException("invalid otherCIId/newRights");
		}
		if (otherCIId.equals(ownCIId)) {
			throw new ControlInterfaceException("no self modification allowed");
		}
		if (this.componentInstanceManager == null) {
			return false;
		}
		if (!this.componentInstanceManager.isActiveControlInterface(otherCIId)) {
			throw new ControlInterfaceException("unknown otherCIId");
		}
		return this.componentInstanceManager.setCIRights(otherCIId, newRights);
	}

	/**
	 * Sets the rights of a given module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP, SystemStateType.BROKER_RUNNING
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param ciId the calling CI ID
	 * @param moduleId the module ID to set rights for
	 * @param newRights the new rights
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	boolean setModuleRights(final String ciId, final String moduleId, final int newRights) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP, SystemStateType.BROKER_RUNNING);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS);
		if ((moduleId == null) || moduleId.isEmpty() || (newRights < 0)) {
			throw new ControlInterfaceException("invalid moduleId/newRights");
		}
		if (this.componentInstanceManager == null) {
			return false;
		}
		if (!this.componentInstanceManager.isActiveModule(moduleId)) {
			throw new ControlInterfaceException("unknown moduleId");
		}
		if (this.componentInstanceManager.setModuleRights(moduleId, newRights)) {
			try {
				final ModuleSummary summary = this.broker.getModuleSummary(moduleId);
				if (summary != null) {
					announceModuleUpdate(summary, ModuleUpdateEventType.UPDATE);
				}
			} catch (final BrokerException e) {
				this.logConnector.log(e);
			}
			return true;
		}
		return false;
	}

	/**
	 * Starts broker.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP, BROKER_RUNNING
	 * <p>
	 * Required rights: CONTROL_STATE
	 *
	 * @param ciId the calling CI ID
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	void startBroker(final String ciId) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP, SystemStateType.BROKER_RUNNING);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.CONTROL_STATE);
		if ((this.broker != null) && !this.broker.isRunning()) {
			this.stateService.execute(new Runnable() {

				@Override
				public void run() {
					ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.BROKER_STARTING_UP);
					final boolean result = ControlInterfaceActionHandler.this.broker.startup();
					if (result) {
						ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.BROKER_RUNNING);
					} else {
						ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.SYSTEM_OR_BROKER_ERROR);
					}
				}
			});
		}
	}

	/**
	 * Stops broker.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP, BROKER_STOPPED_AND_READY
	 * <p>
	 * Required rights: CONTROL_STATE
	 *
	 * @param ciId the calling CI ID
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	void stopBroker(final String ciId) throws AuthorizationException, ControlInterfaceException {
		checkSystemStateNot(SystemStateType.SYSTEM_EXITING, SystemStateType.SYSTEM_INITIALIZING, SystemStateType.BROKER_SHUTTING_DOWN, SystemStateType.BROKER_STARTING_UP, SystemStateType.BROKER_STOPPED_AND_READY);
		this.authManager.verifyAllComponentRights(ciId, ControlInterfaceRight.CONTROL_STATE);
		if ((this.broker != null) && this.broker.isRunning()) {
			this.stateService.execute(new Runnable() {

				@Override
				public void run() {
					ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.BROKER_SHUTTING_DOWN);
					final boolean result = ControlInterfaceActionHandler.this.broker.shutdown();
					if (result) {
						ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.BROKER_STOPPED_AND_READY);
					} else {
						ControlInterfaceActionHandler.this.core.setCurrentSystemState(SystemStateType.SYSTEM_OR_BROKER_ERROR);
					}
				}
			});
		}
	}
}
