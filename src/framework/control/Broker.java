package framework.control;

import helper.ModulePrioritySorter;
import helper.ObjectValidator;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import module.iface.Module;
import module.iface.Prosumer;
import module.iface.Provider;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import db.iface.BaseConfigurationController;
import framework.constants.Constants;
import framework.exception.BrokerException;
import framework.model.ModuleInputStream;
import framework.model.ModuleOutputStream;
import framework.model.Port;
import framework.model.PortTuple;
import framework.model.PortTuplePriorityComparator;
import framework.model.ProsumerPort;
import framework.model.ProviderPort;
import framework.model.event.ConnectionUpdateEvent;
import framework.model.event.ModuleActivityEvent;
import framework.model.event.type.ConnectionEventType;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.ModuleActivityEventType;
import framework.model.event.type.ModuleUpdateEventType;
import framework.model.event.type.PortUpdateEventType;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.ModuleSummary;
import framework.model.summary.PortSummary;
import framework.model.type.PortType;

/**
 * Manages and controls connections between modules.
 *
 * @author Stefan Werner
 */
public class Broker {

	// currently active data exchange streams
	private final Map<PortTuple, ConcurrentLinkedQueue<ModuleInputStream>> activeInputStreams = new ConcurrentHashMap<PortTuple, ConcurrentLinkedQueue<ModuleInputStream>>();
	private final Map<PortTuple, ConcurrentLinkedQueue<ModuleOutputStream>> activeOutputStreams = new ConcurrentHashMap<PortTuple, ConcurrentLinkedQueue<ModuleOutputStream>>();
	private final BaseConfigurationController baseConfigurationController;
	private ComponentInstanceManager componentInstanceManager;
	private boolean configValid = false;
	// currently connected connection tuples
	private final Set<PortTuple> connectedPortTuples = new HashSet<PortTuple>();
	// module/port structure read/write locks
	private final ReentrantReadWriteLock connectionLock = new ReentrantReadWriteLock(true);
	private final ReadLock connectionReadLock = this.connectionLock.readLock();
	// used to execute background connection management tasks
	private final ExecutorService connectionThread = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(Broker.class.getSimpleName() + "-conn_mgnt-%d").build());
	private final WriteLock connectionWriteLock = this.connectionLock.writeLock();
	private ControlInterfaceActionHandler controlInterfaceActionHandler;
	// currently initialized modules
	private final Set<String> currentlyApprovedModules = new ConcurrentSkipListSet<String>();
	// data structure read/write locks
	private final ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock(true);
	private final ReadLock dataReadLock = this.dataLock.readLock();
	private final WriteLock dataWriteLock = this.dataLock.writeLock();
	// currently disconnected connection tuples
	private final Set<PortTuple> disconnectedPortTuples = new HashSet<PortTuple>();
	// current broker state
	private boolean initialized = false;
	private final LogConnector loggingController;
	private final ModuleActionHandler moduleActionHandler;
	// used to execute background module calls
	private final ExecutorService moduleCallThread = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(Broker.class.getSimpleName() + "-mods_bg-%d").build());
	// currently available ports
	private final Map<String, Map<String, ProsumerPort>> moduleProsumerPorts = new ConcurrentHashMap<String, Map<String, ProsumerPort>>();
	private final Map<String, Map<String, ProviderPort>> moduleProviderPorts = new ConcurrentHashMap<String, Map<String, ProviderPort>>();
	private final Map<String, ExecutorService> moduleThreads = new ConcurrentHashMap<String, ExecutorService>();
	private final Map<Port, Integer> portConnectionCount = new ConcurrentHashMap<Port, Integer>();
	private final Map<ProsumerPort, ProviderPort> prosumerConnectionMap = new ConcurrentHashMap<ProsumerPort, ProviderPort>();
	private final Map<String, Prosumer> prosumerMap = new ConcurrentHashMap<String, Prosumer>();
	// currently connected ports
	private final Map<ProviderPort, Set<ProsumerPort>> providerConnectionMap = new ConcurrentHashMap<ProviderPort, Set<ProsumerPort>>();
	private final Map<String, Provider> providerMap = new ConcurrentHashMap<String, Provider>();
	private boolean running = false;
	// state vars read/write locks
	private final ReentrantLock stateLock = new ReentrantLock(true);
	// used to timeout calls to modules
	private final TimeLimiter timeLimiter = new SimpleTimeLimiter();

	/**
	 * Instantiates a new broker.
	 *
	 * @param baseConfigController the base configuration controller
	 * @param authController the authentication controller
	 * @param loggingController the logging controller
	 */
	Broker(final BaseConfigurationController baseConfigController, final ComponentAuthorizationManager authController, final LogConnector loggingController) {
		this.baseConfigurationController = baseConfigController;
		this.loggingController = loggingController;
		this.moduleActionHandler = new ModuleActionHandler(loggingController, authController, this, this.currentlyApprovedModules, this.providerConnectionMap, this.prosumerConnectionMap, this.prosumerMap, this.providerMap, this.moduleThreads, this.connectionReadLock);
	}

	/**
	 * Adds a new connection.
	 *
	 * @param connectionSummary the connection summary
	 * @return true, if successful
	 */
	boolean addConnection(final ConnectionSummary connectionSummary) {
		if (connectionSummary == null) {
			return false;
		}
		boolean result = false;
		this.dataWriteLock.lock();

		final Map<String, ProsumerPort> prosumerPorts = this.moduleProsumerPorts.get(connectionSummary.getProsumerPortSummary().getModuleId());
		final Map<String, ProviderPort> providerPorts = this.moduleProviderPorts.get(connectionSummary.getProviderPortSummary().getModuleId());
		if ((prosumerPorts != null) && (providerPorts != null)) {
			final ProsumerPort prosumerPort = prosumerPorts.get(connectionSummary.getProsumerPortSummary().getPortId());
			final ProviderPort providerPort = providerPorts.get(connectionSummary.getProviderPortSummary().getPortId());
			if ((prosumerPort != null) && (providerPort != null)) {
				final int prio = connectionSummary.getPriority();
				final PortTuple tuple = new PortTuple(prosumerPort, providerPort, prio);
				this.portConnectionCount.remove(prosumerPort);
				this.portConnectionCount.remove(providerPort);
				announceConnectionUpdate(tuple, ConnectionEventType.ADD);
				if (!connectPorts(tuple)) {
					this.disconnectedPortTuples.add(tuple);
					announceConnectionUpdate(tuple, ConnectionEventType.DISCONNECTED);
					this.baseConfigurationController.addOrUpdatePortConnection(getConnectionSummary(tuple));
				} else {
					final int prosumerCount = getConnectionCount(tuple.getProsumerPort());
					final int providerCount = getConnectionCount(tuple.getProviderPort());
					final PortSummary prosumerPortSummary = new PortSummary(tuple.getProsumerPort().getModuleId(), PortType.PROSUMER, tuple.getProsumerPort().getPortId(), tuple.getProsumerPort().getMaxConnections(), prosumerCount);
					final PortSummary providerPortSummary = new PortSummary(tuple.getProviderPort().getModuleId(), PortType.PROVIDER, tuple.getProviderPort().getPortId(), tuple.getProviderPort().getMaxConnections(), providerCount);
					this.controlInterfaceActionHandler.announcePortUpdate(prosumerPortSummary, PortUpdateEventType.UPDATE);
					this.controlInterfaceActionHandler.announcePortUpdate(providerPortSummary, PortUpdateEventType.UPDATE);
					announceConnectionUpdate(tuple, ConnectionEventType.CONNECTED);
					this.baseConfigurationController.addOrUpdatePortConnection(getConnectionSummary(tuple));
				}
				result = true;
			}
		}
		this.dataWriteLock.unlock();
		return result;
	}

	/**
	 * Adds a new module to internal data structures.
	 *
	 * @param moduleId the module ID
	 * @param module the module to add
	 */
	private void addModuleToDataStructure(final String moduleId, final Module module) {
		this.dataWriteLock.lock();
		if (module instanceof Prosumer) {
			final Prosumer prosumer = (Prosumer) module;
			this.prosumerMap.put(moduleId, prosumer);
			this.moduleProsumerPorts.put(moduleId, new HashMap<String, ProsumerPort>());
		}
		if (module instanceof Provider) {
			final Provider provider = (Provider) module;
			this.providerMap.put(moduleId, provider);
			this.moduleProviderPorts.put(moduleId, new HashMap<String, ProviderPort>());
		}
		if (this.moduleThreads.get(moduleId) == null) {
			this.moduleThreads.put(moduleId, Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(Broker.class.getSimpleName() + "-mod_" + moduleId + "-%d").build()));
		}
		this.currentlyApprovedModules.add(moduleId);
		this.dataWriteLock.unlock();
	}

	/**
	 * Annouces module activity.
	 *
	 * @param event the module event
	 */
	private void annouceModuleActivity(final ModuleActivityEvent event) {
		this.controlInterfaceActionHandler.announceModuleActivity(event);
	}

	/**
	 * Announces connection update.
	 *
	 * @param tuple the port tuple
	 * @param type the event type
	 */
	private void announceConnectionUpdate(final PortTuple tuple, final ConnectionEventType type) {
		this.controlInterfaceActionHandler.announceConnectionUpdate(new ConnectionUpdateEvent(getConnectionSummary(tuple), type));
	}

	/**
	 * Checks current state.
	 */
	private void checkCurrentState() {
		// TODO: Ee don't care for module states by now. The system may start even if some modules are NOT ready. Reconsider?
		this.configValid = true;
		// stateWriteLock.lock();
		// if (initialized) {
		// boolean modState = true;
		// for (Module module : getModules()) {
		// boolean result = false;
		// Module proxy = timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_MANAGEMENT, TimeUnit.SECONDS);
		// try {
		// result = proxy.isReady();
		// } catch (UncheckedTimeoutException e) {
		// String moduleId = getModuleId(module);
		// if (moduleId != null) {
		// try {
		// controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleEventType.FAIL_RESPOND);
		// } catch (BrokerException e2) {
		// loggingController.log(e2);
		// }
		// }
		// } catch (Exception e) {
		// loggingController.log(e);
		// }
		// modState = modState && result;
		// }
		// configValid = modState;
		// } else {
		// configValid = false;
		// }
		// stateWriteLock.unlock();
	}

	/**
	 * Closes all open input or output streams.
	 *
	 * @return true, if successful
	 */
	private boolean closeAllOpenStreams() {
		final boolean result = true;
		this.dataWriteLock.lock();
		for (final ConcurrentLinkedQueue<ModuleInputStream> streams : this.activeInputStreams.values()) {
			for (final ModuleInputStream stream : streams) {
				try {
					stream.close();
				} catch (final Exception e) {
					this.loggingController.log(e);
				}
			}
		}
		for (final ConcurrentLinkedQueue<ModuleOutputStream> streams : this.activeOutputStreams.values()) {
			for (final ModuleOutputStream stream : streams) {
				try {
					stream.close();
				} catch (final Exception e) {
					this.loggingController.log(e);
				}
			}
		}
		this.dataWriteLock.unlock();
		return result;
	}

	/**
	 * Closes all open input or output streams of a certain connection.
	 *
	 * @param portTuple the port tuple
	 * @return true, if successful
	 */
	private boolean closeAllOpenStreams(final PortTuple portTuple) {
		final boolean result = true;
		this.dataWriteLock.lock();
		final ConcurrentLinkedQueue<ModuleOutputStream> oStreams = this.activeOutputStreams.get(portTuple);
		if (oStreams != null) {
			for (final ModuleOutputStream stream : oStreams) {
				try {
					stream.close();
				} catch (final Exception e) {
					this.loggingController.log(e);
				}
			}
		}
		final ConcurrentLinkedQueue<ModuleInputStream> iStreams = this.activeInputStreams.get(portTuple);
		if (iStreams != null) {
			for (final ModuleInputStream stream : iStreams) {
				try {
					stream.close();
				} catch (final Exception e) {
					this.loggingController.log(e);
				}
			}
		}
		this.dataWriteLock.unlock();
		return result;
	}

	/**
	 * Connects two ports, adding a new connection.
	 *
	 * @param portTuple the port tuple
	 * @return true, if successful
	 */
	private boolean connectPorts(final PortTuple portTuple) {

		this.dataWriteLock.lock();
		// check for null values
		if ((portTuple == null) || (portTuple.getProsumerPort() == null) || (portTuple.getProviderPort() == null)) {
			this.dataWriteLock.unlock();
			return false;
		}

		final ProsumerPort prosumerPort = portTuple.getProsumerPort();
		final ProviderPort providerPort = portTuple.getProviderPort();
		Map<String, ProviderPort> providerPorts;
		Set<ProsumerPort> connectedProsumerPorts;

		// check if corresponding prosumer module exists and is currently offering prosumerPort
		final Map<String, ProsumerPort> prosumerPorts = this.moduleProsumerPorts.get(prosumerPort.getModuleId());

		// check if affected ports are already taken by connections with higher priorities
		final TreeSet<PortTuple> existingProsumerTuples = findConnectedPortTuples(prosumerPort.getModuleId(), prosumerPort.getPortId(), PortType.PROSUMER);
		final TreeSet<PortTuple> existingProviderTuples = findConnectedPortTuples(providerPort.getModuleId(), providerPort.getPortId(), PortType.PROVIDER);
		final Set<PortTuple> existingTuplesToDisconnect = new HashSet<PortTuple>();

		// new connections priority is equal to or lower than any other priority of existing connections
		if (((existingProsumerTuples.size() >= 1) && (existingProsumerTuples.last().getPriority() >= portTuple.getPriority())) || ((providerPort.getMaxConnections() >= 0) && (existingProviderTuples.size() >= providerPort.getMaxConnections()) && (existingProviderTuples.last().getPriority() >= portTuple.getPriority()))) {
			this.dataWriteLock.unlock();
			return false;

		} else {
			// add existing connection with lowest priority to set for later disconnection (see below)
			if ((existingProsumerTuples.size() >= 1) && !existingProsumerTuples.last().equals(portTuple)) {
				existingTuplesToDisconnect.add(existingProsumerTuples.last());
			}
			if ((providerPort.getMaxConnections() >= 0) && (existingProviderTuples.size() >= providerPort.getMaxConnections()) && !existingProviderTuples.last().equals(portTuple)) {
				existingTuplesToDisconnect.add(existingProviderTuples.last());
			}
		}

		connectedProsumerPorts = this.providerConnectionMap.get(providerPort);

		if ((this.prosumerMap.get(prosumerPort.getModuleId()) == null) || (prosumerPorts == null) || !prosumerPorts.containsKey(prosumerPort.getPortId()) ||

		(// check if corresponding provider module exists and is currently offering providerPort
		this.providerMap.get(providerPort.getModuleId()) == null) || ((providerPorts = this.moduleProviderPorts.get(providerPort.getModuleId())) == null) || !providerPorts.containsKey(providerPort.getPortId()) ||

		(// check if the current prosumer port may be connect
		prosumerPort.getMaxConnections() < 1) ||

		// check if ports are already connected with each other
		((connectedProsumerPorts != null) && (connectedProsumerPorts.contains(prosumerPort) || (providerPort.getMaxConnections() == 0)))) {
			this.dataWriteLock.unlock();
			return false;
		}

		// EVERYTHING OK, establish connection

		// disconnect existing connections if required (see above)
		for (final PortTuple tuple : existingTuplesToDisconnect) {
			if (tuple != null) {
				if (disconnectPort(tuple.getProsumerPort(), false) == null) {
					this.dataWriteLock.unlock();
					return false;
				}
			}
		}

		boolean notifyProvider = false;

		this.connectionWriteLock.lock();
		if (connectedProsumerPorts == null) {
			notifyProvider = true;
			connectedProsumerPorts = new HashSet<ProsumerPort>();
			this.providerConnectionMap.put(providerPort, connectedProsumerPorts);
		}

		connectedProsumerPorts.add(prosumerPort);
		this.prosumerConnectionMap.put(prosumerPort, providerPort);
		this.connectionWriteLock.unlock();

		// remove connection from disconnected connections Set if included
		this.disconnectedPortTuples.remove(portTuple);

		// add connection to connected connections
		this.connectedPortTuples.add(portTuple);

		// add empty stream set for new connection
		this.activeInputStreams.put(portTuple, new ConcurrentLinkedQueue<ModuleInputStream>());
		this.activeOutputStreams.put(portTuple, new ConcurrentLinkedQueue<ModuleOutputStream>());

		final Prosumer prosumer = this.prosumerMap.get(prosumerPort.getModuleId());
		this.dataWriteLock.unlock();

		if (prosumer != null) {
			this.moduleCallThread.execute(new Runnable() {

				@Override
				public void run() {
					prosumer.onPortConnection(prosumerPort);
				}
			});
		}
		if (notifyProvider) {
			final Provider provider = this.providerMap.get(providerPort.getModuleId());
			if (provider != null) {
				this.moduleCallThread.execute(new Runnable() {

					@Override
					public void run() {
						provider.onPortConnection(providerPort);
					}
				});
			}
		}

		invalidatePortConnectionCounts(portTuple.getProsumerPort());
		announceConnectionUpdate(portTuple, ConnectionEventType.CONNECTED);
		return true;
	}

	/**
	 * Disconnects connection to a given prosumer port, optionally removing connection from database.
	 *
	 * @param prosumerPort the prosumer port to remove connection from
	 * @param removeConnectionsCompletely set to true to remove connections completely (from database)
	 * @return the port tuple
	 */
	private PortTuple disconnectPort(final ProsumerPort prosumerPort, final boolean removeConnectionsCompletely) {
		if (prosumerPort == null) {
			return null;
		}
		PortTuple result = null;
		Prosumer prosumer = null;
		Provider provider = null;
		this.dataWriteLock.lock();
		final ProviderPort connectedProviderPort = this.prosumerConnectionMap.get(prosumerPort);
		if (connectedProviderPort != null) {
			this.connectionWriteLock.lock();
			this.prosumerConnectionMap.remove(prosumerPort);
			this.connectionWriteLock.unlock();
			prosumer = this.prosumerMap.get(prosumerPort.getModuleId());
			final Set<ProsumerPort> otherProsumerPorts = this.providerConnectionMap.get(connectedProviderPort);
			if (otherProsumerPorts != null) {
				otherProsumerPorts.remove(prosumerPort);
				if (otherProsumerPorts.isEmpty()) {
					this.connectionWriteLock.lock();
					this.providerConnectionMap.remove(connectedProviderPort);
					this.connectionWriteLock.unlock();
					provider = this.providerMap.get(connectedProviderPort.getModuleId());
				}
				final Set<PortTuple> tuples = findConnectedPortTuples(prosumerPort.getModuleId(), prosumerPort.getPortId(), PortType.PROSUMER);
				if (tuples.isEmpty()) { // should never happen
					result = new PortTuple(prosumerPort, connectedProviderPort, 0);
				} else {
					result = tuples.iterator().next();
				}
				if (removeConnectionsCompletely) {
					try {
						this.baseConfigurationController.removePortConnection(getConnectionSummary(result));
					} catch (final Exception e) {
						this.loggingController.log(e);
					}
				}
				closeAllOpenStreams(result);
				this.activeInputStreams.remove(result);
				this.activeOutputStreams.remove(result);

				// remove connection from connected connections
				this.connectedPortTuples.remove(result);

				if (removeConnectionsCompletely) {
					invalidatePortConnectionCounts(prosumerPort);
					announceConnectionUpdate(result, ConnectionEventType.REMOVED);
				} else {
					this.disconnectedPortTuples.add(result);
					announceConnectionUpdate(result, ConnectionEventType.DISCONNECTED);
				}

				if (result != null) {
					reevaluateDisconnectedConnections(result.getProsumerPort().getModuleId(), result.getProsumerPort().getPortId(), PortType.PROSUMER);
					reevaluateDisconnectedConnections(result.getProviderPort().getModuleId(), result.getProviderPort().getPortId(), PortType.PROVIDER);
				}
			}
		}
		this.dataWriteLock.unlock();

		if (prosumer != null) {
			final Prosumer fProsumer = prosumer;
			this.moduleCallThread.execute(new Runnable() {

				@Override
				public void run() {
					fProsumer.onPortDisconnection(prosumerPort);
				}
			});
		}
		if (provider != null) {
			final Provider fProvider = provider;
			this.moduleCallThread.execute(new Runnable() {

				@Override
				public void run() {
					fProvider.onPortDisconnection(connectedProviderPort);
				}
			});
		}
		return result;
	}

	/**
	 * Disconnects every connection to a given provider port.
	 *
	 * @param providerPort the provider port
	 * @param removeConnectionsCompletely set to true to remove connections completely (from database)
	 * @return the set of disconnected connections
	 */
	private Set<PortTuple> disconnectPorts(final ProviderPort providerPort, final boolean removeConnectionsCompletely) {
		if (providerPort == null) {
			return null;
		}
		final Set<PortTuple> tuples = new HashSet<PortTuple>();
		this.dataWriteLock.lock();
		final Set<ProsumerPort> connectedProsumerPorts = this.providerConnectionMap.get(providerPort);
		if (connectedProsumerPorts != null) {
			final Set<ProsumerPort> connectedProsumerPortsClone = new HashSet<ProsumerPort>(connectedProsumerPorts);
			for (final ProsumerPort prosumerPort : connectedProsumerPortsClone) {
				final PortTuple tuple = disconnectPort(prosumerPort, removeConnectionsCompletely);
				if (tuple != null) {
					tuples.add(tuple);
				}
			}
		}
		this.dataWriteLock.unlock();
		return tuples;
	}

	/**
	 * Finds connected connections based on module and port.
	 *
	 * @param moduleId the module ID
	 * @param portId the port ID
	 * @param type the type of the port
	 * @return the tree set of connections
	 */
	private TreeSet<PortTuple> findConnectedPortTuples(final String moduleId, final String portId, final PortType type) {
		if ((moduleId == null) || (portId == null) || moduleId.isEmpty() || portId.isEmpty()) {
			return null;
		}
		this.dataReadLock.lock();
		final TreeSet<PortTuple> result = new TreeSet<PortTuple>(new PortTuplePriorityComparator());
		for (final PortTuple tuple : this.connectedPortTuples) {
			if ((type == null) || ((type == PortType.PROVIDER) && tuple.getProviderPort().getModuleId().equals(moduleId) && tuple.getProviderPort().getPortId().equals(portId))) {
				result.add(tuple);
			} else if ((type == null) || ((type == PortType.PROSUMER) && tuple.getProsumerPort().getModuleId().equals(moduleId) && tuple.getProsumerPort().getPortId().equals(portId))) {
				result.add(tuple);
				break;
			}
		}
		this.dataReadLock.unlock();
		return result;
	}

	/**
	 * Finds disconnected connections based on module and port.
	 *
	 * @param moduleId the module ID
	 * @param portId the port ID
	 * @param type the type of the port
	 * @return the sets of connections
	 */
	private Set<PortTuple> findDisconnectedPortTuples(final String moduleId, final String portId, final PortType type) {
		if ((moduleId == null) || (portId == null) || moduleId.isEmpty() || portId.isEmpty()) {
			return null;
		}
		this.dataReadLock.lock();
		final TreeSet<PortTuple> result = new TreeSet<PortTuple>(new PortTuplePriorityComparator());
		for (final PortTuple tuple : this.disconnectedPortTuples) {
			if (((type == PortType.PROVIDER) && tuple.getProviderPort().getModuleId().equals(moduleId) && tuple.getProviderPort().getPortId().equals(portId)) || ((type == PortType.PROSUMER) && tuple.getProsumerPort().getModuleId().equals(moduleId) && tuple.getProsumerPort().getPortId().equals(portId))) {
				result.add(tuple);
			}
		}
		this.dataReadLock.unlock();
		return result;
	}

	/**
	 * Finds existing prosumer port object for given IDs. The port object instance is used as a kind of token so it needs to be reused.
	 *
	 * @param moduleId the module ID
	 * @param portId the port ID
	 * @return the prosumer port found (may be null if none)
	 */
	private ProsumerPort findExistingProsumerPort(final String moduleId, final String portId) {
		final Map<String, ProsumerPort> prosumers = this.moduleProsumerPorts.get(moduleId);
		if (prosumers == null) {
			final Set<PortTuple> disconnectedPortTuples = findDisconnectedPortTuples(moduleId, portId, PortType.PROSUMER);
			if ((disconnectedPortTuples != null) && !disconnectedPortTuples.isEmpty()) {
				return disconnectedPortTuples.iterator().next().getProsumerPort();
			} else {
				return null;
			}
		} else {
			return prosumers.get(portId);
		}
	}

	/**
	 * Finds existing provider port object for given IDs. The port object instance is used as a kind of token so it needs to be reused.
	 *
	 * @param moduleId the module ID
	 * @param portId the port ID
	 * @return the provider port found (may be null if none)
	 */
	private ProviderPort findExistingProviderPort(final String moduleId, final String portId) {
		final Map<String, ProviderPort> providers = this.moduleProviderPorts.get(moduleId);
		if (providers == null) {
			final Set<PortTuple> disconnectedPortTuples = findDisconnectedPortTuples(moduleId, portId, PortType.PROVIDER);
			if ((disconnectedPortTuples != null) && !disconnectedPortTuples.isEmpty()) {
				return disconnectedPortTuples.iterator().next().getProviderPort();
			} else {
				return null;
			}
		} else {
			return providers.get(portId);
		}
	}

	/**
	 * Finds a connection between two ports.
	 *
	 * @param prosumerPort the prosumer port
	 * @param providerPort the provider port
	 * @return the port tuple between the ports (may be null if none)
	 */
	private PortTuple findPortTuple(final ProsumerPort prosumerPort, final ProviderPort providerPort) {
		for (final PortTuple tuple : this.connectedPortTuples) {
			if ((tuple.getProsumerPort() == prosumerPort) && (tuple.getProviderPort() == providerPort)) {
				return tuple;
			}
		}
		for (final PortTuple tuple : this.disconnectedPortTuples) {
			if ((tuple.getProsumerPort() == prosumerPort) && (tuple.getProviderPort() == providerPort)) {
				return tuple;
			}
		}
		return null;
	}

	/**
	 * Gets IDs of active modules
	 *
	 * @return the module IDs
	 */
	Set<String> getActiveModuleIds() {
		this.dataReadLock.lock();
		final Set<String> result = new HashSet<String>();
		result.addAll(this.prosumerMap.keySet());
		result.addAll(this.providerMap.keySet());
		this.dataReadLock.unlock();
		return result;
	}

	/**
	 * Gets the connection count of a certain port (currently connected AND disconnected connections).
	 *
	 * @param port the port
	 * @return the connection count
	 */
	private int getConnectionCount(final Port port) {
		Integer i = null; // portConnectionCount.get(port);
		if (i == null) {
			this.dataReadLock.lock();
			i = 0;
			if (port instanceof ProsumerPort) {
				for (final PortTuple tuple : this.connectedPortTuples) {
					if (port == tuple.getProsumerPort()) {
						i++;
					}
				}
				for (final PortTuple tuple : this.disconnectedPortTuples) {
					if (port == tuple.getProsumerPort()) {
						i++;
					}
				}
			} else if (port instanceof ProviderPort) {
				for (final PortTuple tuple : this.connectedPortTuples) {
					if (port == tuple.getProviderPort()) {
						i++;
					}
				}
				for (final PortTuple tuple : this.disconnectedPortTuples) {
					if (port == tuple.getProviderPort()) {
						i++;
					}
				}
			}
			this.dataReadLock.unlock();
			this.portConnectionCount.put(port, i);
		}
		return i;
	}

	/**
	 * Gets all connection summaries.
	 *
	 * @return the connection summaries
	 */
	Set<ConnectionSummary> getConnectionSummaries() {
		final Set<ConnectionSummary> result = new HashSet<ConnectionSummary>();
		this.dataReadLock.lock();
		for (final PortTuple tuple : this.connectedPortTuples) {
			result.add(getConnectionSummary(tuple));
		}
		for (final PortTuple tuple : this.disconnectedPortTuples) {
			result.add(getConnectionSummary(tuple));
		}
		this.dataReadLock.unlock();
		return result;
	}

	/**
	 * Gets the connection summary of a given connection
	 *
	 * @param tuple the port tuple
	 * @return the connection summary
	 */
	private ConnectionSummary getConnectionSummary(final PortTuple tuple) {
		if (this.connectedPortTuples.contains(tuple)) {
			return getConnectionSummary(tuple.getProsumerPort(), tuple.getProviderPort(), true, tuple.getPriority(), tuple.getDataTransfered(), tuple.getLatestRefreshDate());
		} else {
			return getConnectionSummary(tuple.getProsumerPort(), tuple.getProviderPort(), false, tuple.getPriority(), tuple.getDataTransfered(), tuple.getLatestRefreshDate());
		}
	}

	/**
	 * Gets the connection summary from given data.
	 *
	 * @param prosumerPort the prosumer port
	 * @param providerPort the provider port
	 * @param isActive true, if active
	 * @param priority the priority
	 * @param dataTransfered the data transfered by now
	 * @param lastUpdate the last update date
	 * @return the connection summary
	 */
	private ConnectionSummary getConnectionSummary(final ProsumerPort prosumerPort, final ProviderPort providerPort, final boolean isActive, final int priority, final long dataTransfered, final long lastUpdate) {
		this.dataReadLock.lock();
		final int prosumerCount = getConnectionCount(prosumerPort);
		final int providerCount = getConnectionCount(providerPort);
		final PortSummary prosumerPortSummary = new PortSummary(prosumerPort.getModuleId(), PortType.PROSUMER, prosumerPort.getPortId(), prosumerPort.getMaxConnections(), prosumerCount);
		final PortSummary providerPortSummary = new PortSummary(providerPort.getModuleId(), PortType.PROVIDER, providerPort.getPortId(), providerPort.getMaxConnections(), providerCount);
		this.dataReadLock.unlock();
		return new ConnectionSummary(prosumerPortSummary, providerPortSummary, isActive, priority, dataTransfered, System.currentTimeMillis());
	}

	/**
	 * Gets the module action handler.
	 *
	 * @return the module action handler
	 */
	ModuleActionHandler getModuleActionHandler() {
		return this.moduleActionHandler;
	}

	/**
	 * Gets the ID of a given module.
	 *
	 * @param module the module
	 * @return the module ID
	 */
	private String getModuleId(final Module module) {
		for (final String id : this.prosumerMap.keySet()) {
			final Module mod = this.prosumerMap.get(id);
			if (mod == module) {
				return id;
			}
		}
		for (final String id : this.providerMap.keySet()) {
			final Module mod = this.providerMap.get(id);
			if (mod == module) {
				return id;
			}
		}
		return null;
	}

	/**
	 * Gets all modules.
	 *
	 * @return the modules
	 */
	private Set<Module> getModules() {
		final Set<Module> modules = new HashSet<Module>();
		this.dataReadLock.lock();
		modules.addAll(this.prosumerMap.values());
		modules.addAll(this.providerMap.values());
		this.dataReadLock.unlock();
		return modules;
	}

	/**
	 * Gets all module summaries.
	 *
	 * @return the module summaries
	 */
	Set<ModuleSummary> getModuleSummaries() {
		final Set<ModuleSummary> result = new HashSet<ModuleSummary>();
		this.dataReadLock.lock();
		for (final String moduleId : getActiveModuleIds()) {
			try {
				final ModuleSummary modSum = getModuleSummary(moduleId);
				result.add(modSum);
			} catch (final BrokerException e) {
				this.loggingController.log(e);
			}
		}
		this.dataReadLock.unlock();
		return result;
	}

	/**
	 * Gets the module summary of a given module.
	 *
	 * @param moduleId the module ID
	 * @return the module summary
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	ModuleSummary getModuleSummary(final String moduleId) throws BrokerException {
		if (moduleId == null) {
			throw new BrokerException("invalid moduleId");
		}

		ModuleSummary result = null;

		this.dataReadLock.lock();
		final Module module = this.prosumerMap.get(moduleId) != null ? this.prosumerMap.get(moduleId) : this.providerMap.get(moduleId);

		if (module == null) {
			this.dataReadLock.unlock();
			throw new BrokerException("unknown moduleId");
		}

		final Map<String, ProsumerPort> prosumerPorts = this.moduleProsumerPorts.get(moduleId);
		final Map<String, ProviderPort> providerPorts = this.moduleProviderPorts.get(moduleId);

		final Set<PortSummary> portSummaries = new HashSet<PortSummary>();

		if (prosumerPorts != null) {
			for (final ProsumerPort port : prosumerPorts.values()) {
				final int curConnCount = getConnectionCount(port);
				portSummaries.add(new PortSummary(moduleId, PortType.PROSUMER, port.getPortId(), port.getMaxConnections(), curConnCount));
			}
		}
		if (providerPorts != null) {
			for (final ProviderPort port : providerPorts.values()) {
				final int curConnCount = getConnectionCount(port);
				portSummaries.add(new PortSummary(moduleId, PortType.PROVIDER, port.getPortId(), port.getMaxConnections(), curConnCount));
			}
		}

		final String name = this.baseConfigurationController.getModuleName(moduleId);
		final String type = this.baseConfigurationController.getModuleType(moduleId);
		final int rights = this.baseConfigurationController.getModuleRights(moduleId);
		if ((name == null) || (type == null) || (rights < 0)) {
			throw new BrokerException("invalid module data");
		}
		result = new ModuleSummary(moduleId, name, type, rights, portSummaries);
		this.dataReadLock.unlock();
		return result;
	}

	/**
	 * Initialize.
	 *
	 * @return true, if successful
	 */
	boolean initialize() {
		this.loggingController.log(LogEventLevelType.DEBUG, "initializing...");
		this.stateLock.lock();
		if (!this.initialized && !this.running) {
			this.stateLock.unlock();
			initializeAllRequiredModulesFromDB();
			reestablishAllPortConnectionsFromDB();
			this.initialized = true;
			if (this.initialized) {
				this.loggingController.log(LogEventLevelType.DEBUG, "successfully initialized");
			} else {
				this.loggingController.log(LogEventLevelType.ERROR, "Initialization failed");
			}
			return this.initialized;
		} else {
			this.stateLock.unlock();
			this.loggingController.log(LogEventLevelType.ERROR, "already done -> initialized/running = " + this.initialized + "/" + this.running);
			return false;
		}
	}

	/**
	 * Initialize all required modules from db.
	 *
	 * @return true, if successful
	 */
	private boolean initializeAllRequiredModulesFromDB() {
		if (isRunning()) {
			return false;
		}
		final boolean result = true;
		try {
			for (final String moduleId : this.baseConfigurationController.getModuleConfigurations().keySet()) {
				initializeRequiredModule(moduleId);
			}
		} catch (final Exception e) {
			this.loggingController.log(e);
			return false;
		}
		return result;
	}

	/**
	 * Initialize new module.
	 *
	 * @param moduleId the module id
	 * @param module the module
	 * @return true, if successful
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	boolean initializeNewModule(final String moduleId, final Module module) throws BrokerException {
		if ((moduleId == null) || (module == null)) {
			throw new BrokerException("invalid moduleId or module");
		}
		boolean result = false;
		this.dataWriteLock.lock();
		if (!this.prosumerMap.containsKey(moduleId) && !this.providerMap.containsKey(moduleId)) {
			addModuleToDataStructure(moduleId, module);
			this.dataWriteLock.unlock();
			result = true;
		} else {
			this.dataWriteLock.unlock();
			throw new BrokerException("moduleId already initialized");
		}
		if (result) {
			try {
				this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.ADD);
			} catch (final BrokerException e) {
				this.loggingController.log(e);
			}
			final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_MANAGEMENT, TimeUnit.SECONDS);
			try {
				proxy.initialize();
			} catch (final UncheckedTimeoutException e) {
				this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.FAIL_INIT);
			} catch (final Exception e) {
				this.loggingController.log(e);
			}
		}
		return result;
	}

	/**
	 * Initialize required module.
	 *
	 * @param moduleId the module id
	 * @return true, if successful
	 */
	private boolean initializeRequiredModule(final String moduleId) {
		if ((moduleId == null) || moduleId.isEmpty()) {
			return false;
		}
		this.loggingController.log(LogEventLevelType.DEBUG, "initializing module " + moduleId + "...");
		boolean result = false;
		Module module = null;
		try {
			this.dataWriteLock.lock();
			module = this.prosumerMap.containsKey(moduleId) ? this.prosumerMap.get(moduleId) : this.providerMap.get(moduleId);
			if (module == null) {
				module = this.componentInstanceManager.getModule(moduleId, true);
				if (module != null) {
					addModuleToDataStructure(moduleId, module);
					result = true;
				}
			}
		} catch (final Exception e) {
			this.loggingController.log(e);
		} finally {
			this.dataWriteLock.unlock();
		}

		if ((module != null) && result) {
			try {
				this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.ADD);
			} catch (final BrokerException e) {
				this.loggingController.log(e);
			}
			final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_MANAGEMENT, TimeUnit.SECONDS);
			try {
				proxy.initialize();
			} catch (final UncheckedTimeoutException e1) {
				try {
					this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.FAIL_INIT);
				} catch (final BrokerException e2) {
					this.loggingController.log(e2);
				}
			} catch (final Exception e) {
				this.loggingController.log(e);
			}
		}
		return result;
	}

	/**
	 * Invalidate port connection counts.
	 *
	 * @param port the port
	 */
	private void invalidatePortConnectionCounts(final ProsumerPort port) {
		for (final PortTuple tuple : this.connectedPortTuples) {
			this.portConnectionCount.remove(tuple.getProsumerPort());
			this.portConnectionCount.remove(tuple.getProviderPort());
		}
		for (final PortTuple tuple : this.disconnectedPortTuples) {
			this.portConnectionCount.remove(tuple.getProsumerPort());
			this.portConnectionCount.remove(tuple.getProviderPort());
		}
	}

	/**
	 * Checks if is connected.
	 *
	 * @param moduleId the module id
	 * @param port the port
	 * @return true, if is connected
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	boolean isConnected(final String moduleId, final Port port) throws BrokerException {
		if ((moduleId == null) || !moduleId.equals(port.getModuleId()) || (port == null)) {
			throw new BrokerException("invalid moduleId or port");
		}
		if (!this.initialized) {
			return false;
		}

		boolean result = false;
		this.dataReadLock.lock();
		if (port instanceof ProsumerPort) {
			final ProsumerPort prosumerPort = (ProsumerPort) port;
			if (this.prosumerConnectionMap.get(prosumerPort) != null) {
				result = true;
			}
		} else if (port instanceof ProviderPort) {
			final ProviderPort providerPort = (ProviderPort) port;
			final Set<ProsumerPort> connectedPorts = this.providerConnectionMap.get(providerPort);
			if ((connectedPorts != null) && !connectedPorts.isEmpty()) {
				result = true;
			}
		}
		this.dataReadLock.unlock();
		return result;
	}

	/**
	 * Checks if is running.
	 *
	 * @return true, if is running
	 */
	boolean isRunning() {
		return this.running;
	}

	/**
	 * Checks if is valid port.
	 *
	 * @param moduleId the module id
	 * @param port the port
	 * @return true, if is valid port
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	boolean isValidPort(final String moduleId, final Port port) throws BrokerException {
		if ((moduleId == null) || !moduleId.equals(port.getModuleId()) || (port == null)) {
			throw new BrokerException("invalid moduleId or port");
		}
		boolean result = false;
		this.dataReadLock.lock();
		if (port instanceof ProsumerPort) {
			final Map<String, ProsumerPort> ports = this.moduleProsumerPorts.get(moduleId);
			if ((port != null) && ports.containsValue(port)) {
				result = true;
			}
		} else if (port instanceof ProviderPort) {
			final Map<String, ProviderPort> ports = this.moduleProviderPorts.get(moduleId);
			if ((port != null) && ports.containsValue(port)) {
				result = true;
			}
		}
		this.dataReadLock.unlock();
		return result;
	}

	/**
	 * Reestablish all port connections from db.
	 *
	 * @return true, if successful
	 */
	private boolean reestablishAllPortConnectionsFromDB() {
		boolean result = true;
		this.dataWriteLock.lock();
		try {
			for (final ConnectionSummary summary : this.baseConfigurationController.getPortConnections()) {
				if (!ObjectValidator.checkConnectionSummary(summary)) {
					this.baseConfigurationController.removePortConnection(summary);
					continue;
				}
				ProsumerPort prosumerPort = findExistingProsumerPort(summary.getProsumerPortSummary().getModuleId(), summary.getProsumerPortSummary().getPortId());
				ProviderPort providerPort = findExistingProviderPort(summary.getProviderPortSummary().getModuleId(), summary.getProviderPortSummary().getPortId());
				if (prosumerPort == null) {
					prosumerPort = new ProsumerPort(summary.getProsumerPortSummary().getModuleId(), summary.getProsumerPortSummary().getPortId(), summary.getProsumerPortSummary().getMaxConnections());
				}
				if (providerPort == null) {
					providerPort = new ProviderPort(summary.getProviderPortSummary().getModuleId(), summary.getProviderPortSummary().getPortId(), summary.getProviderPortSummary().getMaxConnections());
				}
				final PortTuple tuple = new PortTuple(prosumerPort, providerPort, summary.getPriority());
				try {
					final boolean thisResult = connectPorts(tuple);
					if (!thisResult) {
						this.disconnectedPortTuples.add(tuple);
					}
				} catch (final Exception e) {
					this.loggingController.log(e);
					result = false;
				}
			}
		} catch (final Exception e) {
			this.loggingController.log(e);
			result = false;
		}
		this.dataWriteLock.unlock();
		return result;
	}

	/**
	 * Reevaluate disconnected connections.
	 *
	 * @param moduleId the module id
	 * @param portId the port id
	 * @param type the type
	 */
	private void reevaluateDisconnectedConnections(final String moduleId, final String portId, final PortType type) {
		try {
			this.connectionThread.execute(new Runnable() {

				@Override
				public void run() {
					if ((moduleId == null) || moduleId.isEmpty() || (portId == null) || portId.isEmpty()) {
						return;
					}
					Broker.this.dataWriteLock.lock();
					final Set<PortTuple> disconnectedPortTuples = findDisconnectedPortTuples(moduleId, portId, type);
					if ((disconnectedPortTuples != null) && !disconnectedPortTuples.isEmpty()) {
						for (final PortTuple tuple : disconnectedPortTuples) {
							// try to reconnect affected connections
							if (connectPorts(tuple) && (type == PortType.PROSUMER)) {
								break; // only a single active prosumer connection is allowed
							}
						}
						checkCurrentState();
					}
					Broker.this.dataWriteLock.unlock();
				}
			});
		} catch (final RejectedExecutionException e) {
			this.loggingController.log(e);
		}
	}

	/**
	 * Register prosumer port.
	 *
	 * @param prosumer the prosumer
	 * @param moduleId the module id
	 * @param portId the port id
	 * @param maxConnections the max connections
	 * @return the prosumer port
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	ProsumerPort registerProsumerPort(final Prosumer prosumer, final String moduleId, final String portId, final int maxConnections) throws BrokerException {
		if ((prosumer == null) || (moduleId == null) || moduleId.isEmpty() || (portId == null) || portId.isEmpty()) {
			throw new BrokerException("parameters invalid");
		}

		this.dataWriteLock.lock();

		// check if module exists and its id is correct
		final Prosumer savedProsumer = this.prosumerMap.get(moduleId);
		final Map<String, ProsumerPort> ports = this.moduleProsumerPorts.get(moduleId);
		if ((savedProsumer == null) || (savedProsumer != prosumer) || (ports == null)) {
			this.dataWriteLock.unlock();
			throw new BrokerException("module and/or moduleId invalid");
		}

		// check if port is not already registered
		if ((ports == null) || ports.containsKey(portId)) {
			this.dataWriteLock.unlock();
			throw new BrokerException("port already registered");
		}

		// announce port add
		final PortSummary summary = new PortSummary(moduleId, PortType.PROSUMER, portId, maxConnections, 0);
		this.controlInterfaceActionHandler.announcePortUpdate(summary, PortUpdateEventType.ADD);

		ProsumerPort port;
		// get port from disconnected connections or create a new one
		final Set<PortTuple> disconnectedPortTuples = findDisconnectedPortTuples(moduleId, portId, PortType.PROSUMER);
		if ((disconnectedPortTuples != null) && !disconnectedPortTuples.isEmpty()) {
			port = disconnectedPortTuples.iterator().next().getProsumerPort();
			ports.put(portId, port);
			reevaluateDisconnectedConnections(moduleId, portId, PortType.PROSUMER);
		} else {
			port = new ProsumerPort(moduleId, portId, maxConnections);
			ports.put(portId, port);
		}

		this.dataWriteLock.unlock();

		return port;
	}

	/**
	 * Register provider port.
	 *
	 * @param provider the provider
	 * @param moduleId the module id
	 * @param portId the port id
	 * @param maxConnections the max connections
	 * @return the provider port
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	ProviderPort registerProviderPort(final Provider provider, final String moduleId, final String portId, final int maxConnections) throws BrokerException {
		if ((provider == null) || (moduleId == null) || moduleId.isEmpty() || (portId == null) || portId.isEmpty()) {
			throw new BrokerException("parameters invalid");
		}

		this.dataWriteLock.lock();

		// check if module exists and its id is correct
		final Provider savedProvider = this.providerMap.get(moduleId);
		final Map<String, ProviderPort> ports = this.moduleProviderPorts.get(moduleId);
		if ((savedProvider == null) || (savedProvider != provider) || (ports == null)) {
			this.dataWriteLock.unlock();
			throw new BrokerException("module and/or moduleId invalid");
		}

		// check if port is not already registered
		if ((ports == null) || ports.containsKey(portId)) {
			this.dataWriteLock.unlock();
			throw new BrokerException("port already registered");
		}

		// announce port add
		final PortSummary summary = new PortSummary(moduleId, PortType.PROVIDER, portId, maxConnections, 0);
		this.controlInterfaceActionHandler.announcePortUpdate(summary, PortUpdateEventType.ADD);

		ProviderPort port;
		// get port from disconnected connections or create a new one
		final Set<PortTuple> disconnectedPortTuples = findDisconnectedPortTuples(moduleId, portId, PortType.PROVIDER);
		if ((disconnectedPortTuples != null) && !disconnectedPortTuples.isEmpty()) {
			port = disconnectedPortTuples.iterator().next().getProviderPort();
			ports.put(portId, port);
			reevaluateDisconnectedConnections(moduleId, portId, PortType.PROVIDER);
		} else {
			port = new ProviderPort(moduleId, portId, maxConnections);
			ports.put(portId, port);
		}
		this.dataWriteLock.unlock();

		return port;
	}

	/**
	 * Removes the connection.
	 *
	 * @param connectionSummary the connection summary
	 * @return true, if successful
	 */
	boolean removeConnection(final ConnectionSummary connectionSummary) {
		if (connectionSummary == null) {
			return false;
		}
		boolean result = false;
		this.dataWriteLock.lock();
		try {
			if (connectionSummary.isActive()) {
				for (final PortTuple tuple : this.connectedPortTuples) {
					if (connectionSummary.getProsumerPortSummary().getModuleId().equals(tuple.getProsumerPort().getModuleId()) && connectionSummary.getProsumerPortSummary().getPortId().equals(tuple.getProsumerPort().getPortId()) && connectionSummary.getProviderPortSummary().getModuleId().equals(tuple.getProviderPort().getModuleId()) && connectionSummary.getProviderPortSummary().getPortId().equals(tuple.getProviderPort().getPortId())) {
						result = disconnectPort(tuple.getProsumerPort(), true) != null;
						if (result) {
							announceConnectionUpdate(tuple, ConnectionEventType.REMOVED);
							final int prosumerCount = getConnectionCount(tuple.getProsumerPort());
							final int providerCount = getConnectionCount(tuple.getProviderPort());
							final PortSummary prosumerPortSummary = new PortSummary(tuple.getProsumerPort().getModuleId(), PortType.PROSUMER, tuple.getProsumerPort().getPortId(), tuple.getProsumerPort().getMaxConnections(), prosumerCount);
							final PortSummary providerPortSummary = new PortSummary(tuple.getProviderPort().getModuleId(), PortType.PROVIDER, tuple.getProviderPort().getPortId(), tuple.getProviderPort().getMaxConnections(), providerCount);
							this.controlInterfaceActionHandler.announcePortUpdate(prosumerPortSummary, PortUpdateEventType.UPDATE);
							this.controlInterfaceActionHandler.announcePortUpdate(providerPortSummary, PortUpdateEventType.UPDATE);
							reevaluateDisconnectedConnections(connectionSummary.getProsumerPortSummary().getModuleId(), connectionSummary.getProsumerPortSummary().getPortId(), PortType.PROSUMER);
							reevaluateDisconnectedConnections(connectionSummary.getProviderPortSummary().getModuleId(), connectionSummary.getProviderPortSummary().getPortId(), PortType.PROVIDER);
						}
						break;
					}
				}
			} else {
				for (final PortTuple tuple : this.disconnectedPortTuples) {
					if (connectionSummary.getProsumerPortSummary().getModuleId().equals(tuple.getProsumerPort().getModuleId()) && connectionSummary.getProsumerPortSummary().getPortId().equals(tuple.getProsumerPort().getPortId()) && connectionSummary.getProviderPortSummary().getModuleId().equals(tuple.getProviderPort().getModuleId()) && connectionSummary.getProviderPortSummary().getPortId().equals(tuple.getProviderPort().getPortId())) {
						result = this.disconnectedPortTuples.remove(tuple);
						this.baseConfigurationController.removePortConnection(connectionSummary);
						if (result) {
							this.controlInterfaceActionHandler.announceConnectionUpdate(new ConnectionUpdateEvent(connectionSummary, ConnectionEventType.REMOVED));
						}
						break;
					}
				}
			}
		} catch (final Exception e) {
			this.loggingController.log(e);
		} finally {
			this.dataWriteLock.unlock();
		}
		return result;
	}

	/**
	 * Removes the input stream.
	 *
	 * @param tuple the tuple
	 * @param inputStream the input stream
	 * @param path the path
	 * @param dataTransfered the data transfered
	 * @return true, if successful
	 */
	public boolean removeInputStream(final PortTuple tuple, final ModuleInputStream inputStream, final String[] path, final long dataTransfered) {
		boolean result = false;
		this.dataWriteLock.lock();
		final ConcurrentLinkedQueue<ModuleInputStream> iStreams = this.activeInputStreams.get(tuple);
		final ConcurrentLinkedQueue<ModuleOutputStream> oStreams = this.activeOutputStreams.get(tuple);
		tuple.setDataTransfered(tuple.getDataTransfered() + dataTransfered);
		tuple.setLatestRefreshDate(System.currentTimeMillis());
		if (iStreams != null) {
			if (iStreams.contains(inputStream)) {
				iStreams.remove(inputStream);
				if (iStreams.isEmpty() && ((oStreams == null) || oStreams.isEmpty())) {
					announceConnectionUpdate(tuple, ConnectionEventType.IDLE);
				}
				result = true;
			}
		}
		this.dataWriteLock.unlock();
		annouceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___INPUTSTREAM_CLOSED, tuple.getProsumerPort().getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, tuple.getProsumerPort().getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.getProviderPort().getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.getProviderPort().getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		final Callable<?> callable = new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				Broker.this.moduleActionHandler.inputStreamClosed(tuple, path);
				return null;
			}
		};
		try {
			this.timeLimiter.callWithTimeout(callable, Constants.TIMEOUT_SECONDS___MODULE_COMMUNICATION, TimeUnit.SECONDS, true);
		} catch (final Exception e) {
			this.loggingController.log(e);
		}
		return result;
	}

	/**
	 * Removes the module.
	 *
	 * @param moduleId the module id
	 * @param removeModuleCompletely the remove module completely
	 * @return true, if successful
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	boolean removeModule(final String moduleId, final boolean removeModuleCompletely) throws BrokerException {
		if (moduleId == null) {
			throw new BrokerException("invalid parameters");
		}
		final boolean result = true;
		this.dataWriteLock.lock();
		final Module module = this.prosumerMap.containsKey(moduleId) ? this.prosumerMap.get(moduleId) : this.providerMap.get(moduleId);
		this.dataWriteLock.unlock();
		if (module == null) {
			throw new BrokerException("unknown moduleId");
		}

		if (module != null) {
			final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_MANAGEMENT, TimeUnit.SECONDS);
			try {
				proxy.enterShutdown();
			} catch (final UncheckedTimeoutException e) {
				this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.FAIL_STOP);
			} catch (final Exception e) {
				this.loggingController.log(e);
			}
			try {
				proxy.exitShutdown();
			} catch (final UncheckedTimeoutException e) {
				this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.FAIL_STOP);
			} catch (final Exception e) {
				this.loggingController.log(e);
			}
			this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.REMOVE);
		}

		this.dataWriteLock.lock();
		this.currentlyApprovedModules.remove(moduleId);
		Map<String, ProsumerPort> prosumerPorts = this.moduleProsumerPorts.get(moduleId);
		Map<String, ProviderPort> providerPorts = this.moduleProviderPorts.get(moduleId);
		if (prosumerPorts != null) {
			prosumerPorts = new HashMap<String, ProsumerPort>(prosumerPorts);
			for (final ProsumerPort port : prosumerPorts.values()) {
				if (port != null) {
					this.moduleActionHandler.removeAllSubscriptionsInternal(moduleId, port);
					unregisterProsumerPort(moduleId, port);
				}
			}
		}
		if (providerPorts != null) {
			providerPorts = new HashMap<String, ProviderPort>(providerPorts);
			for (final ProviderPort port : providerPorts.values()) {
				if (port != null) {
					unregisterProviderPort(moduleId, port);
				}
			}
		}

		final ExecutorService executor = this.moduleThreads.get(moduleId);
		if (executor != null) {
			executor.shutdownNow();
		}
		this.moduleThreads.remove(moduleId);
		this.prosumerMap.remove(moduleId);
		this.providerMap.remove(moduleId);
		this.moduleProsumerPorts.remove(moduleId);
		this.moduleProviderPorts.remove(moduleId);
		this.dataWriteLock.unlock();
		return result;
	}

	/**
	 * Removes the output stream.
	 *
	 * @param tuple the tuple
	 * @param outputStream the output stream
	 * @param path the path
	 * @param dataTransfered the data transfered
	 * @return true, if successful
	 */
	public boolean removeOutputStream(final PortTuple tuple, final ModuleOutputStream outputStream, final String[] path, final long dataTransfered) {
		boolean result = false;
		this.dataWriteLock.lock();
		final ConcurrentLinkedQueue<ModuleInputStream> iStreams = this.activeInputStreams.get(tuple);
		final ConcurrentLinkedQueue<ModuleOutputStream> oStreams = this.activeOutputStreams.get(tuple);
		tuple.setDataTransfered(tuple.getDataTransfered() + dataTransfered);
		tuple.setLatestRefreshDate(System.currentTimeMillis());
		if (oStreams != null) {
			if (oStreams.remove(outputStream)) {
				if (oStreams.isEmpty() && ((iStreams == null) || iStreams.isEmpty())) {
					announceConnectionUpdate(tuple, ConnectionEventType.IDLE);
				}
				result = true;
			}
		}
		this.dataWriteLock.unlock();
		annouceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___OUTPUTSTREAM_CLOSED, tuple.getProsumerPort().getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, tuple.getProsumerPort().getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.getProviderPort().getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.getProviderPort().getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		final Callable<?> callable = new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				Broker.this.moduleActionHandler.outputStreamClosed(tuple, path);
				return null;
			}
		};
		try {
			this.timeLimiter.callWithTimeout(callable, Constants.TIMEOUT_SECONDS___MODULE_COMMUNICATION, TimeUnit.SECONDS, true);
		} catch (final Exception e) {
			this.loggingController.log(e);
		}
		return result;
	}

	/**
	 * Reset config.
	 *
	 * @return true, if successful
	 */
	boolean resetConfig() {
		this.stateLock.lock();
		if (!this.running) {
			this.dataWriteLock.lock();
			this.initialized = false;
			this.configValid = false;
			this.running = false;

			this.connectionWriteLock.lock();
			this.providerConnectionMap.clear();
			this.prosumerConnectionMap.clear();
			this.prosumerMap.clear();
			this.providerMap.clear();
			this.connectionWriteLock.unlock();

			this.disconnectedPortTuples.clear();
			this.moduleProsumerPorts.clear();
			this.moduleProviderPorts.clear();
			this.dataWriteLock.unlock();
			this.stateLock.unlock();
			return true;
		} else {
			this.stateLock.unlock();
			return false;
		}
	}

	/**
	 * Sets the component instance manager.
	 *
	 * @param componentInstanceManager the new component instance manager
	 */
	void setComponentInstanceManager(final ComponentInstanceManager componentInstanceManager) {
		this.componentInstanceManager = componentInstanceManager;
	}

	/**
	 * Sets the control action handler.
	 *
	 * @param controlInterfaceActionHandler the new control action handler
	 */
	void setControlActionHandler(final ControlInterfaceActionHandler controlInterfaceActionHandler) {
		this.controlInterfaceActionHandler = controlInterfaceActionHandler;
		this.moduleActionHandler.setControlInterfaceActionHandler(controlInterfaceActionHandler);
	}

	/**
	 * Shutdown.
	 *
	 * @return true, if successful
	 */
	boolean shutdown() {
		boolean result = false;
		this.stateLock.lock();
		if (this.running) {
			this.dataWriteLock.lock();
			final List<Module> modules = ModulePrioritySorter.getStopOrder(getModules());
			for (final Module module : modules) {
				final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_MANAGEMENT, TimeUnit.SECONDS);
				try {
					proxy.enterShutdown();
				} catch (final UncheckedTimeoutException e1) {
					final String moduleId = getModuleId(module);
					if (moduleId != null) {
						try {
							this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.FAIL_STOP);
						} catch (final BrokerException e2) {
							this.loggingController.log(e2);
						}
					}
				} catch (final Exception e) {
					this.loggingController.log(e);
				}
			}
			this.running = false;
			for (final Module module : getModules()) {
				final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_MANAGEMENT, TimeUnit.SECONDS);
				try {
					proxy.exitShutdown();
				} catch (final UncheckedTimeoutException e1) {
					final String moduleId = getModuleId(module);
					if (moduleId != null) {
						try {
							this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.FAIL_STOP);
						} catch (final BrokerException e2) {
							this.loggingController.log(e2);
						}
					}
				} catch (final Exception e) {
					this.loggingController.log(e);
				}
			}
			closeAllOpenStreams();
			result = true;
			this.dataWriteLock.unlock();
		}
		this.stateLock.unlock();
		return result;
	}

	/**
	 * Startup.
	 *
	 * @return true, if successful
	 */
	boolean startup() {
		boolean result = false;
		this.loggingController.log(LogEventLevelType.DEBUG, "starting broker...");
		this.stateLock.lock();
		if (!this.running) {
			checkCurrentState();
			if (this.initialized && this.configValid) {
				this.loggingController.log(LogEventLevelType.DEBUG, "starting modules...");
				final List<Module> modules = ModulePrioritySorter.getStartOrder(getModules());
				for (final Module module : modules) {
					final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_MANAGEMENT, TimeUnit.SECONDS);
					try {
						proxy.enterStartup();
					} catch (final UncheckedTimeoutException e1) {
						final String moduleId = getModuleId(module);
						if (moduleId != null) {
							try {
								this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.FAIL_START);
							} catch (final BrokerException e2) {
								this.loggingController.log(e2);
							}
						}
					} catch (final Exception e) {
						this.loggingController.log(e);
					}
				}
				this.running = true;
				for (final Module module : getModules()) {
					final Module proxy = this.timeLimiter.newProxy(module, Module.class, Constants.TIMEOUT_SECONDS___MODULE_MANAGEMENT, TimeUnit.SECONDS);
					try {
						proxy.exitStartup();
					} catch (final UncheckedTimeoutException e1) {
						final String moduleId = getModuleId(module);
						if (moduleId != null) {
							try {
								this.controlInterfaceActionHandler.announceModuleUpdate(getModuleSummary(moduleId), ModuleUpdateEventType.FAIL_START);
							} catch (final BrokerException e2) {
								this.loggingController.log(e2);
							}
						}
					} catch (final Exception e) {
						this.loggingController.log(e);
					}
				}
				result = true;
				this.loggingController.log(LogEventLevelType.DEBUG, "successfully started");
			} else {
				this.loggingController.log(LogEventLevelType.ERROR, "Cannot start -> initialized/configValid/running = " + this.initialized + "/" + this.configValid + "/" + this.running);
			}
		}
		this.stateLock.unlock();
		return result;
	}

	/**
	 * Unregister prosumer port.
	 *
	 * @param moduleId the module id
	 * @param prosumerPort the prosumer port
	 * @return true, if successful
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	boolean unregisterProsumerPort(final String moduleId, final ProsumerPort prosumerPort) throws BrokerException {
		if ((moduleId == null) || (prosumerPort == null) || !moduleId.equals(prosumerPort.getModuleId())) {
			throw new BrokerException("invalid moduleId or port");
		}
		boolean result = false;
		this.dataWriteLock.lock();
		// remove the port from available port Set of the corresponding module
		final Map<String, ProsumerPort> prosumerPorts = this.moduleProsumerPorts.get(prosumerPort.getModuleId());
		if ((prosumerPorts != null) && prosumerPorts.containsKey(prosumerPort.getPortId())) {

			// check if port is involved in any active connections, disconnect if so
			final PortTuple tuple = disconnectPort(prosumerPort, false);
			if (tuple != null) {
				// add connection to disconnected connection Set
				this.disconnectedPortTuples.add(tuple);
				announceConnectionUpdate(tuple, ConnectionEventType.DISCONNECTED);
				// an active port was removed, check system's state
				checkCurrentState();
			}

			prosumerPorts.remove(prosumerPort.getPortId());

			// announce port update
			final PortSummary summary = new PortSummary(moduleId, PortType.PROSUMER, prosumerPort.getPortId(), prosumerPort.getMaxConnections(), 0);
			this.controlInterfaceActionHandler.announcePortUpdate(summary, PortUpdateEventType.REMOVE);

			result = true;
		}
		this.dataWriteLock.unlock();
		return result;
	}

	/**
	 * Unregister provider port.
	 *
	 * @param moduleId the module id
	 * @param providerPort the provider port
	 * @return true, if successful
	 * @throws BrokerException if invalid arguments or other error within the broker
	 */
	boolean unregisterProviderPort(final String moduleId, final ProviderPort providerPort) throws BrokerException {
		if ((moduleId == null) || (providerPort == null) || !moduleId.equals(providerPort.getModuleId())) {
			throw new BrokerException("invalid moduleId or port");
		}
		boolean result = false;
		this.dataWriteLock.lock();
		// remove the port from available port Set of the corresponding module
		final Map<String, ProviderPort> providerPorts = this.moduleProviderPorts.get(providerPort.getModuleId());
		if ((providerPorts != null) && providerPorts.containsKey(providerPort.getPortId())) {
			// check if port is involved in any active connections, disconnect if so
			final Set<PortTuple> tuples = disconnectPorts(providerPort, false);
			boolean changed = false;
			for (final PortTuple tuple : tuples) {
				if (tuple != null) {
					changed = true;
					// add connection to disconnected connection Set
					this.disconnectedPortTuples.add(tuple);
					announceConnectionUpdate(tuple, ConnectionEventType.DISCONNECTED);
				}
			}
			providerPorts.remove(providerPort.getPortId());
			if (changed) {
				// active ports were removed, check system's state
				checkCurrentState();
			}
			result = true;
			// announce port update
			final PortSummary summary = new PortSummary(moduleId, PortType.PROVIDER, providerPort.getPortId(), providerPort.getMaxConnections(), 0);
			this.controlInterfaceActionHandler.announcePortUpdate(summary, PortUpdateEventType.REMOVE);
		}
		this.dataWriteLock.unlock();
		return result;
	}

	/**
	 * Updates a connection summary with fresh data from the broker.
	 *
	 * @param connectionSummary the old connection summary
	 * @return the updated connection summary
	 */
	ConnectionSummary updateConnection(final ConnectionSummary connectionSummary) {
		if (connectionSummary == null) {
			return null;
		}
		ConnectionSummary result = null;
		this.dataReadLock.lock();
		try {
			if (connectionSummary.isActive()) {
				for (final PortTuple tuple : this.connectedPortTuples) {
					if (connectionSummary.getProsumerPortSummary().getModuleId().equals(tuple.getProsumerPort().getModuleId()) && connectionSummary.getProsumerPortSummary().getPortId().equals(tuple.getProsumerPort().getPortId()) && connectionSummary.getProviderPortSummary().getModuleId().equals(tuple.getProviderPort().getModuleId()) && connectionSummary.getProviderPortSummary().getPortId().equals(tuple.getProviderPort().getPortId())) {
						result = getConnectionSummary(tuple);
						break;
					}
				}
			} else {
				for (final PortTuple tuple : this.disconnectedPortTuples) {
					if (connectionSummary.getProsumerPortSummary().getModuleId().equals(tuple.getProsumerPort().getModuleId()) && connectionSummary.getProsumerPortSummary().getPortId().equals(tuple.getProsumerPort().getPortId()) && connectionSummary.getProviderPortSummary().getModuleId().equals(tuple.getProviderPort().getModuleId()) && connectionSummary.getProviderPortSummary().getPortId().equals(tuple.getProviderPort().getPortId())) {
						result = getConnectionSummary(tuple);
						break;
					}
				}
			}
		} catch (final Exception e) {
			this.loggingController.log(e);
		} finally {
			this.dataReadLock.unlock();
		}
		return result;
	}

	/**
	 * Wraps an input stream.
	 *
	 * @param prosumerPort the prosumer port
	 * @param providerPort the provider port
	 * @param stream the original stream
	 * @param path the path
	 * @return the wrapped input stream
	 */
	InputStream wrapInputStream(final ProsumerPort prosumerPort, final ProviderPort providerPort, final InputStream stream, final String[] path) {
		ModuleInputStream bStream = null;
		this.dataWriteLock.lock();
		final PortTuple tuple = findPortTuple(prosumerPort, providerPort);
		final ConcurrentLinkedQueue<ModuleInputStream> streams = this.activeInputStreams.get(tuple);
		if ((streams != null) && !streams.contains(stream)) {
			bStream = new ModuleInputStream(stream, this, tuple, path);
			streams.add(bStream);
			announceConnectionUpdate(tuple, ConnectionEventType.BUSY);
		}
		this.dataWriteLock.unlock();
		return bStream;
	}

	/**
	 * Wraps an output stream.
	 *
	 * @param prosumerPort the prosumer port
	 * @param providerPort the provider port
	 * @param stream the original stream
	 * @param path the path
	 * @return the warpped output stream
	 */
	OutputStream wrapOutputStream(final ProsumerPort prosumerPort, final ProviderPort providerPort, final OutputStream stream, final String[] path) {
		ModuleOutputStream bStream = null;
		this.dataWriteLock.lock();
		final PortTuple tuple = findPortTuple(prosumerPort, providerPort);
		final ConcurrentLinkedQueue<ModuleOutputStream> streams = this.activeOutputStreams.get(tuple);
		if ((streams != null) && !streams.contains(stream)) {
			bStream = new ModuleOutputStream(stream, this, tuple, path);
			streams.add(bStream);
			announceConnectionUpdate(tuple, ConnectionEventType.BUSY);
		}
		this.dataWriteLock.unlock();
		return bStream;
	}
}
