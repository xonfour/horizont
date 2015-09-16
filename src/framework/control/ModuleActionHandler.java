package framework.control;

import helper.ObjectValidator;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import module.iface.DataElementEventListener;
import module.iface.Module;
import module.iface.Prosumer;
import module.iface.Provider;
import module.iface.StreamListener;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import framework.constants.Constants;
import framework.constants.ModuleRight;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.ModuleException;
import framework.exception.WrongBrokerStateException;
import framework.exception.WrongModuleStateException;
import framework.model.DataElement;
import framework.model.DataElementEventSubscription;
import framework.model.Port;
import framework.model.PortTuple;
import framework.model.ProsumerPort;
import framework.model.ProviderPort;
import framework.model.event.DataElementEvent;
import framework.model.event.ModuleActivityEvent;
import framework.model.event.ProviderStateEvent;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.ModuleActivityEventType;
import framework.model.type.DataElementType;
import framework.model.type.PortType;

/**
 * Holds all methods for modules and is mainly used by {@link ProsumerConnector} and {@link ProviderConnector}. It also excessively checks module rights with
 * every call.
 */
public final class ModuleActionHandler {

	/**
	 * Internal helper class to store connections between ports and prosumers.
	 */
	private class PortProsumerTuple {

		private final Prosumer prosumer;
		private final ProsumerPort prosumerPort;

		/**
		 * Instantiates a new port prosumer tuple.
		 *
		 * @param port the port
		 * @param prosumer the prosumer
		 */
		public PortProsumerTuple(final ProsumerPort port, final Prosumer prosumer) {
			this.prosumerPort = port;
			this.prosumer = prosumer;
		}
	}

	/**
	 * Internal helper class to store connections between ports and providers.
	 */
	private class PortProviderTuple {

		private final Provider provider;
		private final ProviderPort providerPort;

		/**
		 * Instantiates a new port provider tuple.
		 *
		 * @param port the port
		 * @param provider the provider
		 */
		public PortProviderTuple(final ProviderPort port, final Provider provider) {
			this.providerPort = port;
			this.provider = provider;
		}
	}

	private final ComponentAuthorizationManager authManager;
	private final Broker broker;
	private final ReadLock connectionReadLock;
	private ControlInterfaceActionHandler controlInterfaceActionHandler;
	private final Set<String> currentlyApprovedModules;
	private final LogConnector logConnector;
	private final Map<String, ExecutorService> moduleEventThreads;
	private final Map<ProsumerPort, Set<DataElementEventSubscription>> notificationSubscriptions = new ConcurrentHashMap<ProsumerPort, Set<DataElementEventSubscription>>();
	private final Map<ProsumerPort, ProviderPort> prosumerConnectionMap;
	private final Map<String, Prosumer> prosumerMap;
	private final Map<ProviderPort, Set<ProsumerPort>> providerConnectionMap;
	private final Map<String, Provider> providerMap;
	private final Map<Port, Set<StreamListener>> streamCloseListeners = new ConcurrentHashMap<Port, Set<StreamListener>>();
	private final ReentrantReadWriteLock subscriptionAndListenerLock = new ReentrantReadWriteLock(true);
	private final ReadLock subscriptionAndListenerReadLock = this.subscriptionAndListenerLock.readLock();
	private final WriteLock subscriptionAndListenerWriteLock = this.subscriptionAndListenerLock.writeLock();
	private final TimeLimiter timeLimiter = new SimpleTimeLimiter();

	/**
	 * Instantiates a new module action handler.
	 *
	 * @param logConnector the log connector
	 * @param componentAuthorizationController the component authorization controller
	 * @param broker the broker
	 * @param currentlyApprovedModules the currently approved modules
	 * @param providerConnectionMap the provider connection map
	 * @param prosumerConnectionMap the prosumer connection map
	 * @param prosumerMap the prosumer map
	 * @param providerMap the provider map
	 * @param moduleEventThreads the module threads for delivering signals and events
	 * @param dataReadLock the data read lock
	 */
	ModuleActionHandler(final LogConnector logConnector, final ComponentAuthorizationManager componentAuthorizationController, final Broker broker, final Set<String> currentlyApprovedModules, final Map<ProviderPort, Set<ProsumerPort>> providerConnectionMap, final Map<ProsumerPort, ProviderPort> prosumerConnectionMap, final Map<String, Prosumer> prosumerMap, final Map<String, Provider> providerMap, final Map<String, ExecutorService> moduleEventThreads, final ReadLock dataReadLock) {
		this.logConnector = logConnector;
		this.authManager = componentAuthorizationController;
		this.broker = broker;
		this.currentlyApprovedModules = currentlyApprovedModules;
		this.providerConnectionMap = providerConnectionMap;
		this.prosumerConnectionMap = prosumerConnectionMap;
		this.prosumerMap = prosumerMap;
		this.providerMap = providerMap;
		this.moduleEventThreads = moduleEventThreads;
		this.connectionReadLock = dataReadLock;
	}

	/**
	 * Adds a stream listener. Listener methods will get called whenever a stream at the given port is closed.
	 * <p>
	 * Required rights: OBSERVE_STREAMS
	 *
	 * @param moduleId the module ID
	 * @param port the port
	 * @param listener the listener
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean addStreamListener(final String moduleId, final Port port, final StreamListener listener) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.OBSERVE_STREAMS);
		if (!ObjectValidator.checkArgsNotNull(moduleId, port, listener) || !moduleId.equals(port.getModuleId()) || !this.broker.isValidPort(moduleId, port)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___ADD_STREAM_LISTENER, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, port.getPortId()));
		this.subscriptionAndListenerWriteLock.lock();
		Set<StreamListener> listeners = this.streamCloseListeners.get(port);
		if (listeners == null) {
			listeners = new HashSet<StreamListener>();
			this.streamCloseListeners.put(port, listeners);
		}
		listeners.add(listener);
		this.subscriptionAndListenerWriteLock.unlock();
		return true;
	}

	/**
	 * Announces a module activity.
	 *
	 * @param event the event
	 */
	private void announceModuleActivity(final ModuleActivityEvent event) {
		if (this.controlInterfaceActionHandler != null) {
			this.controlInterfaceActionHandler.announceModuleActivity(event);
		}
	}

	/**
	 * Checks and gets a connected provider for a given prosumer port.
	 *
	 * @param prosumerPort the prosumer port
	 * @return the port provider tuple
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	private PortProviderTuple checkAndGetConnectedProvider(final ProsumerPort prosumerPort) throws BrokerException {
		if (prosumerPort == null) {
			throw new BrokerException("invalid port");
		}
		this.connectionReadLock.lock();
		final ProviderPort providerPort = this.prosumerConnectionMap.get(prosumerPort);
		if (providerPort == null) {
			throw new BrokerException("not connected");
		}
		final Provider provider = this.providerMap.get(providerPort.getModuleId());
		this.connectionReadLock.unlock();
		return new PortProviderTuple(providerPort, provider);
	}

	/**
	 * Checks and locks an element at given path. See {@link module.iface.Provider#checkAndLock(ProviderPort, String[])} for more details.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	int checkAndLock(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA, ModuleRight.WRITE_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___CHECK_AND_LOCK, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		try {
			return tuple.provider.checkAndLock(tuple.providerPort, path);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
	}

	/**
	 * Checks broker state.
	 *
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	private void checkBrokerState() throws BrokerException {
		if (!this.broker.isRunning()) {
			throw new WrongBrokerStateException("no module interaction allowed when broker is not in running state");
		}
	}

	/**
	 * Checks module state. Only approved modules may call methods from this class. Modules become approved after returning from their constructor so most calls
	 * are forbidden from there.
	 *
	 * @param moduleId the module ID
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	private void checkModuleState(final String moduleId) throws BrokerException {
		if (!this.currentlyApprovedModules.contains(moduleId)) {
			throw new WrongModuleStateException("connector methods cannot be called from constructor or after shutdown");
		}
	}

	/**
	 * Creates a folder. If necessary parent folders are created automatically. See {@link module.iface.Provider#createFolder(ProviderPort, String[])} for more
	 * details.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path of the folder to create
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	int createFolder(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA, ModuleRight.WRITE_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___CREATE_FOLDER, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		try {
			return tuple.provider.createFolder(tuple.providerPort, path);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
	}

	/**
	 * Deletes an element at a given path. See {@link module.iface.Provider#delete(ProviderPort, String[])} for more details.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	int delete(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA, ModuleRight.WRITE_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___DELETE, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		try {
			return tuple.provider.delete(tuple.providerPort, path);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
	}

	/**
	 * Gets the child elements under a given parent path.
	 * <p>
	 * Required rights: READ_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path of the parent element
	 * @param recursive set to true to get all children recursively (relative depth >= 1)
	 * @return the child elements (null if non existing/error or no children supported, for example within file elements)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	Set<DataElement> getChildElements(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path, final boolean recursive) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___GET_CHILD_FSELEMENTS, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___RECURSIVE, new Boolean(recursive)));
		try {
			final Set<DataElement> result = tuple.provider.getChildElements(tuple.providerPort, path, recursive);
			if (result == null) {
				return result;
			}
			try {
				return ImmutableSet.copyOf(result);
			} catch (final NullPointerException npe) {
				this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
				this.logConnector.log(npe);
				throw new BrokerException("NULL key/value found in returned Set/Map");
			}
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
	}

	/**
	 * Gets the active connections of a given provider port.
	 *
	 * @param providerPort the provider port
	 * @return the connections
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	private Set<PortProsumerTuple> getConnectedPortTuples(final ProviderPort providerPort) throws BrokerException {
		if (providerPort == null) {
			throw new BrokerException("invalid port");
		}
		final Set<PortProsumerTuple> result = new HashSet<PortProsumerTuple>();
		this.connectionReadLock.lock();
		final Set<ProsumerPort> prosumerPorts = this.providerConnectionMap.get(providerPort);
		if ((prosumerPorts == null) || prosumerPorts.isEmpty()) {
			this.connectionReadLock.unlock();
			throw new BrokerException("not connected");
		} else {
			for (final ProsumerPort prosumerPort : prosumerPorts) {
				final Prosumer prosumer = this.prosumerMap.get(prosumerPort.getModuleId());
				if (prosumer != null) {
					result.add(new PortProsumerTuple(prosumerPort, prosumer));
				}
			}
		}
		this.connectionReadLock.unlock();
		return result;
	}

	/**
	 * Gets the element at a given path.
	 * <p>
	 * Required rights: READ_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the element (null if non existing or error)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	DataElement getElement(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___GET_ELEMENT, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		try {
			return tuple.provider.getElement(tuple.providerPort, path);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
	}

	/**
	 * Gets a new localization connector.
	 *
	 * @param componentId the component ID
	 * @return the new localization connector
	 */
	LocalizationConnector getNewLocalizationConnector(final String componentId) {
		return this.controlInterfaceActionHandler.getNewLocalizationConnector(componentId);
	}

	/**
	 * Gets rights of a given module.
	 *
	 * @param moduleId the module ID
	 * @return the rights
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	int getRights(final String moduleId) throws BrokerException {
		checkModuleState(moduleId);
		if (moduleId != null) {
			return this.authManager.getRights(moduleId);
		} else {
			return -1;
		}
	}

	/**
	 * Gets the subscription listeners for a given prosumer port and (recursive) path.
	 *
	 * @param prosumerPort the prosumer port
	 * @param path the path
	 * @return the subscription listeners
	 */
	private Set<DataElementEventListener> getSubscriptionListeners(final ProsumerPort prosumerPort, final String[] path) {
		final Set<DataElementEventListener> result = new HashSet<DataElementEventListener>();
		this.subscriptionAndListenerReadLock.lock();
		final Set<DataElementEventSubscription> subscriptions = this.notificationSubscriptions.get(prosumerPort);
		if (subscriptions != null) {
			for (final DataElementEventSubscription subscription : subscriptions) {
				if (subscription.isIncluded(path)) {
					result.add(subscription.getDataElementEventListener());
				}
			}
		}
		this.subscriptionAndListenerReadLock.unlock();
		return result;
	}

	/**
	 * Gets all subscriptions of a given prosumer port.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @return the subscriptions
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	Set<DataElementEventSubscription> getSubscriptions(final String moduleId, final ProsumerPort prosumerPort) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.RECEIVE_EVENTS);
		if (!ObjectValidator.checkArgsNotNull(moduleId, prosumerPort) || !moduleId.equals(prosumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		Set<DataElementEventSubscription> subscriptions = null;
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___GET_SUBSCRIPTIONS, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER));
		this.subscriptionAndListenerReadLock.lock();
		subscriptions = this.notificationSubscriptions.get(prosumerPort);
		this.subscriptionAndListenerReadLock.unlock();
		try {
			return ImmutableSet.copyOf(subscriptions);
		} catch (final NullPointerException npe) {
			this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
			this.logConnector.log(npe);
			throw new BrokerException("NULL key/value found in returned Set/Map");
		}
	}

	/**
	 * Gets all the subscriptions for a prosumer port that include the given path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @param includedPath the included path
	 * @return the subscriptions
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	Set<DataElementEventSubscription> getSubscriptions(final String moduleId, final ProsumerPort prosumerPort, final String[] includedPath) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.RECEIVE_EVENTS);
		if (!ObjectValidator.checkArgsNotNull(moduleId, prosumerPort, includedPath) || !ObjectValidator.checkPath(includedPath) || !moduleId.equals(prosumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___GET_SUBSCRIPTION, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, includedPath));
		final Set<DataElementEventSubscription> result = new HashSet<DataElementEventSubscription>();
		this.subscriptionAndListenerReadLock.lock();
		final Set<DataElementEventSubscription> subscriptions = this.notificationSubscriptions.get(prosumerPort);
		if (subscriptions != null) {
			for (final DataElementEventSubscription subscription : subscriptions) {
				if (subscription.isIncluded(includedPath)) {
					result.add(subscription);
				}
			}
		}
		this.subscriptionAndListenerReadLock.unlock();
		try {
			return ImmutableSet.copyOf(result);
		} catch (final NullPointerException npe) {
			this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
			this.logConnector.log(npe);
			throw new BrokerException("NULL key/value found in returned Set/Map");
		}
	}

	/**
	 * Gets the supported module commands (for a particular path).
	 * <p>
	 * Required rights: SEND_COMMAND
	 *
	 * @param moduleId the module ID
	 * @param sendingPort the sending port
	 * @param path the path to get commands for (may be null)
	 * @return the supported module commands (may be null, for example if module does not answer before a timeout occurs)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	Set<String> getSupportedModuleCommands(final String moduleId, final Port sendingPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.SEND_COMMAND);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingPort) || ((path != null) && !ObjectValidator.checkPath(path)) || !moduleId.equals(sendingPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		if (sendingPort instanceof ProsumerPort) {
			final ProsumerPort prosumerPort = (ProsumerPort) sendingPort;
			final PortProviderTuple tuple = checkAndGetConnectedProvider(prosumerPort);
			announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___GET_SUPPORTED_COMMANDS, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
			try {
				final Module proxy = this.timeLimiter.newProxy(tuple.provider, Module.class, Constants.TIMEOUT_SECONDS___MODULE_COMMUNICATION, TimeUnit.SECONDS);
				final Set<String> result = proxy.getSupportedModuleCommands(tuple.providerPort, path);
				if (result == null) {
					return result;
				}
				try {
					return ImmutableSet.copyOf(result);
				} catch (final NullPointerException npe) {
					this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
					this.logConnector.log(npe);
					throw new BrokerException("NULL key/value found in returned Set/Map");
				}
			} catch (final UncheckedTimeoutException e1) {
				this.logConnector.log(e1);
				return null;
			} catch (final Exception e) {
				if (e instanceof ModuleException) {
					throw e;
				} else {
					this.logConnector.log(e);
					throw new ModuleException("uncaught module exception received");
				}
			}
		} else if (sendingPort instanceof ProviderPort) {
			final ProviderPort providerPort = (ProviderPort) sendingPort;
			final Set<PortProsumerTuple> tuples = getConnectedPortTuples(providerPort);
			final Set<String> result = new HashSet<String>();
			for (final PortProsumerTuple tuple : tuples) {
				announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___GET_SUPPORTED_COMMANDS, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.prosumerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
				try {
					final Module proxy = this.timeLimiter.newProxy(tuple.prosumer, Module.class, Constants.TIMEOUT_SECONDS___MODULE_COMMUNICATION, TimeUnit.SECONDS);
					final Set<String> curResults = proxy.getSupportedModuleCommands(tuple.prosumerPort, path);
					if (curResults != null) {
						result.addAll(curResults);
					}
				} catch (final Exception e) {
					this.logConnector.log(e);
					// ignored to make sure every connected Prosumer receives the call
				}
			}
			try {
				return ImmutableSet.copyOf(result);
			} catch (final NullPointerException npe) {
				this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
				this.logConnector.log(npe);
				throw new BrokerException("NULL key/value found in returned Set/Map");
			}
		} else {
			throw new BrokerException("invalid sendingPort");
		}
	}

	/**
	 * Gets the type of the element at a given path.
	 * <p>
	 * Required rights: READ_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the element type (null if no such element)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	DataElementType getType(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___GET_TYPE, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		try {
			return tuple.provider.getType(tuple.providerPort, path);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
	}

	/**
	 * Calls stream listeners when a specific input stream is closed.
	 *
	 * @param tuple the tuple
	 * @param path the path
	 */
	void inputStreamClosed(final PortTuple tuple, final String[] path) {
		streamClosed(tuple, path, false);
	}

	/**
	 * Checks if a given port is connected.
	 *
	 * @param moduleId the module ID
	 * @param port the port
	 * @return true, if connected
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	boolean isConnected(final String moduleId, final Port port) throws BrokerException {
		checkModuleState(moduleId);
		if (!ObjectValidator.checkArgsNotNull(port)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___IS_CONNECTED, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, port.getPortId()));
		return this.broker.isConnected(moduleId, port);
	}

	/**
	 * Checks if broker is running.
	 *
	 * @return true, if running
	 */
	boolean isRunning() {
		return this.broker.isRunning();
	}

	/**
	 * Checks if module is subscribed to events from a given port and path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @param includedPath the included path
	 * @return true, if subscribed
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean isSubscribed(final String moduleId, final ProsumerPort prosumerPort, final String[] includedPath) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.RECEIVE_EVENTS);
		if (!ObjectValidator.checkArgsNotNull(moduleId, prosumerPort, includedPath) || !ObjectValidator.checkPath(includedPath) || !moduleId.equals(prosumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___IS_SUBSCRIBED, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, includedPath));
		boolean result = false;
		this.subscriptionAndListenerReadLock.lock();
		if (!this.notificationSubscriptions.containsKey(prosumerPort)) {
			result = false;
		} else {
			result = !getSubscriptions(moduleId, prosumerPort, includedPath).isEmpty();
		}
		this.subscriptionAndListenerReadLock.unlock();
		return result;
	}

	/**
	 * Moves an element from one path to another within the same port. If necessary parent folders for destination are created automatically. Will NOT overwrite
	 * an existing destination element. See {@link module.iface.Provider#move(ProviderPort, String[], String[])} for more details.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param srcPath the source path
	 * @param destPath the destination path
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	int move(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] srcPath, final String[] destPath) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA, ModuleRight.WRITE_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, srcPath, destPath) || !ObjectValidator.checkPath(srcPath) || !ObjectValidator.checkPath(destPath) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___MOVE, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, srcPath).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PATH, destPath));
		try {
			return tuple.provider.move(tuple.providerPort, srcPath, destPath);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
	}

	/**
	 * Calls stream listeners when a specific output stream is closed.
	 *
	 * @param tuple the tuple
	 * @param path the path
	 */
	void outputStreamClosed(final PortTuple tuple, final String[] path) {
		streamClosed(tuple, path, true);
	}

	/**
	 * Reads data from an element at given path.
	 * <p>
	 * Required rights: READ_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the input stream to read from (null if no such element or no data)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	InputStream readData(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		InputStream in = null;
		try {
			in = tuple.provider.readData(tuple.providerPort, path);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___READ_DATA, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		if (in != null) {
			return this.broker.wrapInputStream(sendingProsumerPort, tuple.providerPort, in, path);
		} else {
			return null;
		}
	}

	/**
	 * Registers a prosumer port.
	 *
	 * @param moduleId the module ID
	 * @param prosumer the prosumer
	 * @param portId the port id
	 * @param maxConnections the maximum number of concurrent active connections
	 * @return the prosumer port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	ProsumerPort registerProsumerPort(final String moduleId, final Prosumer prosumer, final String portId, final int maxConnections) throws BrokerException {
		checkModuleState(moduleId);
		if (!ObjectValidator.checkArgsNotNull(moduleId, prosumer, portId)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___REGISTER_PROSUMER_PORT, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, portId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER));
		final ProsumerPort port = this.broker.registerProsumerPort(prosumer, moduleId, portId, maxConnections);
		if (!ObjectValidator.checkPort(port)) {
			throw new BrokerException("invalid arguments");
		}
		return port;
	}

	/**
	 * Registers a provider port.
	 *
	 * @param moduleId the module ID
	 * @param provider the provider
	 * @param portId the port id
	 * @param maxConnections the maximum number of concurrent active connections
	 * @return the provider port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	ProviderPort registerProviderPort(final String moduleId, final Provider provider, final String portId, final int maxConnections) throws BrokerException {
		checkModuleState(moduleId);
		if (!ObjectValidator.checkArgsNotNull(moduleId, provider, portId)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___REGISTER_PROVIDER_PORT, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, portId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROVIDER));
		final ProviderPort port = this.broker.registerProviderPort(provider, moduleId, portId, maxConnections);
		if (!ObjectValidator.checkPort(port)) {
			throw new BrokerException("invalid port");
		}
		return port;
	}

	/**
	 * Removes all stream listeners for a given port.
	 * <p>
	 * Required rights: OBSERVE_STREAMS
	 *
	 * @param moduleId the module ID
	 * @param port the port
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean removeAllStreamListeners(final String moduleId, final Port port) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.OBSERVE_STREAMS);
		if (!ObjectValidator.checkArgsNotNull(moduleId, port) || !moduleId.equals(port.getModuleId()) || !this.broker.isValidPort(moduleId, port)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___REMOVE_ALL_STREAM_LISTENER, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, port.getPortId()));
		return this.streamCloseListeners.remove(port) != null;
	}

	/**
	 * Removes all subscriptions for a given prosumer port (without checking state, used internally).
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	boolean removeAllSubscriptionsInternal(final String moduleId, final ProsumerPort prosumerPort) throws BrokerException {
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___REMOVE_ALL_SUBSCRIPTIONS, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER));
		boolean result = false;
		this.subscriptionAndListenerWriteLock.lock();
		result = this.notificationSubscriptions.remove(prosumerPort) != null;
		this.subscriptionAndListenerWriteLock.unlock();
		return result;
	}

	/**
	 * Removes a stream listener for a given port.
	 * <p>
	 * Required rights: OBSERVE_STREAMS
	 *
	 * @param moduleId the module ID
	 * @param port the port
	 * @param listener the listener
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean removeStreamListener(final String moduleId, final Port port, final StreamListener listener) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.OBSERVE_STREAMS);
		if (!ObjectValidator.checkArgsNotNull(moduleId, port, listener) || !moduleId.equals(port.getModuleId()) || !this.broker.isValidPort(moduleId, port)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___REMOVE_STREAM_LISTENER, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, port.getPortId()));
		boolean result = false;
		this.subscriptionAndListenerWriteLock.lock();
		final Set<StreamListener> listeners = this.streamCloseListeners.get(port);
		if (listeners != null) {
			result = listeners.remove(listener);
			if (listeners.isEmpty()) {
				this.streamCloseListeners.remove(port);
			}
		}
		listeners.add(listener);
		this.subscriptionAndListenerWriteLock.unlock();
		return result;
	}

	/**
	 * Requests connected provider status (asynchronous call, wait for event).
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 */
	void requestConnectedProviderStatus(final String moduleId, final ProsumerPort sendingProsumerPort) throws BrokerException, ModuleException {
		checkModuleState(moduleId);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___GET_CONN_PROVIDER_STATUS, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER));
		final ExecutorService service = this.moduleEventThreads.get(tuple.providerPort.getModuleId());
		if (service != null) {
			try {
				service.execute(new Runnable() {

					@Override
					public void run() {
						tuple.provider.onStateRequest(tuple.providerPort);
					}
				});
			} catch (final RejectedExecutionException e) {
				this.logConnector.log(e);
			}
		} else {
			throw new BrokerException("unable to execute request");
		}
	}

	/**
	 * Sends element events to connected prosumers.
	 *
	 * @param moduleId the module ID
	 * @param sendingProviderPort the sending provider port
	 * @param event the event
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	void sendElementEvent(final String moduleId, final ProviderPort sendingProviderPort, final DataElementEvent event) throws BrokerException {
		checkModuleState(moduleId);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProviderPort, event) || !ObjectValidator.checkDataElement(event.dataElement) || !moduleId.equals(sendingProviderPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		for (final PortProsumerTuple tuple : getConnectedPortTuples(sendingProviderPort)) {
			final Set<DataElementEventListener> listeners = getSubscriptionListeners(tuple.prosumerPort, event.dataElement.getPath());
			if (this.authManager.hasRights(tuple.prosumerPort.getModuleId(), ModuleRight.RECEIVE_EVENTS) && !listeners.isEmpty()) {
				announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___ELEMENT_EVENT, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProviderPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.prosumerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, event.dataElement.getPath()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___ELEMENT_EVENT_TYPE, event.eventType));
				final ExecutorService service = this.moduleEventThreads.get(tuple.prosumerPort.getModuleId());
				if (service != null) {
					try {
						service.execute(new Runnable() {

							@Override
							public void run() {
								for (final DataElementEventListener listener : listeners) {
									try {
										listener.onElementEvent(tuple.prosumerPort, event);
									} catch (final Exception e) {
										ModuleActionHandler.this.logConnector.log(e);
									}
								}
							}
						});
					} catch (final RejectedExecutionException e) {
						this.logConnector.log(e);
					}
				} else {
					throw new BrokerException("unable to execute request");
				}
			}
		}
	}

	/**
	 * Sends a module command.
	 * <p>
	 * Required rights: SEND_COMMAND
	 *
	 * @param moduleId the module ID
	 * @param command the command
	 * @param sendingPort the sending port
	 * @param path the path (may be null)
	 * @param properties the properties (may be null)
	 * @return the answer from module (may be null, for example if module does not answer before a timeout occurs)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	Map<String, String> sendModuleCommand(final String moduleId, final String command, final Port sendingPort, final String[] path, Map<String, String> properties) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.SEND_COMMAND);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingPort, command) || command.isEmpty() || ((path != null) && !ObjectValidator.checkPath(path)) || !moduleId.equals(sendingPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		try {
			if (properties != null) {
				properties = ImmutableMap.copyOf(properties);
			}
		} catch (final NullPointerException npe) {
			throw new BrokerException("invalid arguments: NULL key/value found in Set/Map");
		}
		if (sendingPort instanceof ProsumerPort) {
			final ProsumerPort prosumerPort = (ProsumerPort) sendingPort;
			final PortProviderTuple tuple = checkAndGetConnectedProvider(prosumerPort);
			announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___SEND_COMMAND, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___COMMAND, command).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PROPERTIES, properties));
			final Module proxy = this.timeLimiter.newProxy(tuple.provider, Module.class, Constants.TIMEOUT_SECONDS___MODULE_COMMUNICATION, TimeUnit.SECONDS);
			try {
				final Map<String, String> result = proxy.onModuleCommand(tuple.providerPort, command, path, properties);
				if (result == null) {
					return result;
				}
				try {
					return ImmutableMap.copyOf(result);
				} catch (final NullPointerException npe) {
					this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
					this.logConnector.log(npe);
					throw new BrokerException("NULL key/value found in returned Set/Map");
				}
			} catch (final UncheckedTimeoutException e1) {
				this.logConnector.log(e1);
				return null;
			} catch (final Exception e) {
				if (e instanceof ModuleException) {
					throw e;
				} else {
					this.logConnector.log(e);
					throw new ModuleException("uncaught module exception received");
				}
			}
		} else if (sendingPort instanceof ProviderPort) {
			final ProviderPort providerPort = (ProviderPort) sendingPort;
			final Set<PortProsumerTuple> tuples = getConnectedPortTuples(providerPort);
			final Map<String, String> result = new HashMap<String, String>();
			for (final PortProsumerTuple tuple : tuples) {
				announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___SEND_COMMAND, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.prosumerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___COMMAND, command).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PROPERTIES, properties));
				try {
					final Module proxy = this.timeLimiter.newProxy(tuple.prosumer, Module.class, Constants.TIMEOUT_SECONDS___MODULE_COMMUNICATION, TimeUnit.SECONDS);
					final Map<String, String> curResult = proxy.onModuleCommand(tuple.prosumerPort, command, path, properties);
					if (curResult != null) {
						result.putAll(curResult);
					}
				} catch (final Exception e) {
					this.logConnector.log(e);
					// ignored to make sure every connected Prosumer receives the command
				}
			}
			try {
				return ImmutableMap.copyOf(result);
			} catch (final NullPointerException npe) {
				this.logConnector.log(LogEventLevelType.ERROR, "NULL key/value found in returned Set/Map");
				this.logConnector.log(npe);
				throw new BrokerException("NULL key/value found in returned Set/Map");
			}
		} else {
			throw new BrokerException("invalid sendingPort");
		}
	}

	/**
	 * Sends state event to connected modules.
	 *
	 * @param moduleId the module ID
	 * @param sendingPort the sending port
	 * @param event the event
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	void sendState(final String moduleId, final Port sendingPort, final ProviderStateEvent event) throws BrokerException {
		checkModuleState(moduleId);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingPort, event) || !moduleId.equals(sendingPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		if (sendingPort instanceof ProviderPort) {
			final ProviderPort providerPort = (ProviderPort) sendingPort;
			final Set<PortProsumerTuple> tuples = getConnectedPortTuples(providerPort);
			for (final PortProsumerTuple tuple : tuples) {
				announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___STATE_CHANGE, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.prosumerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___STATE, event.state));
				final ExecutorService service = this.moduleEventThreads.get(tuple.prosumerPort.getModuleId());
				if (service != null) {
					try {
						service.execute(new Runnable() {

							@Override
							public void run() {
								try {
									tuple.prosumer.onProviderStateEvent(tuple.prosumerPort, event);
								} catch (final Exception e) {
									ModuleActionHandler.this.logConnector.log(e);
								}
							}
						});
					} catch (final RejectedExecutionException e) {
						this.logConnector.log(e);
					}
				}
			}
		} else {
			throw new BrokerException("invalid sendingPort");
		}
	}

	/**
	 * Sets the control interface action handler.
	 *
	 * @param controlInterfaceActionHandler the new control interface action handler
	 */
	void setControlInterfaceActionHandler(final ControlInterfaceActionHandler controlInterfaceActionHandler) {
		this.controlInterfaceActionHandler = controlInterfaceActionHandler;
	}

	/**
	 * Calls stream listeners when a specific input/output stream is closed.
	 *
	 * @param tuple the tuple
	 * @param path the path
	 * @param isOutputStream true, if output stream
	 */
	private void streamClosed(final PortTuple tuple, final String[] path, final boolean isOutputStream) {
		if ((tuple == null) || (path == null)) {
			return;
		}
		this.subscriptionAndListenerReadLock.lock();
		Set<StreamListener> prosumerListeners = this.streamCloseListeners.get(tuple.getProsumerPort());
		Set<StreamListener> providerListeners = this.streamCloseListeners.get(tuple.getProviderPort());
		if (prosumerListeners != null) {
			prosumerListeners = ImmutableSet.copyOf(prosumerListeners);
		}
		if (providerListeners != null) {
			providerListeners = ImmutableSet.copyOf(providerListeners);
		}
		this.subscriptionAndListenerReadLock.unlock();
		if (prosumerListeners != null) {
			for (final StreamListener listener : prosumerListeners) {
				try {
					if (isOutputStream) {
						listener.onOutputStreamClose(tuple.getProsumerPort(), path);
					} else {
						listener.onInputStreamClose(tuple.getProsumerPort(), path);
					}
				} catch (final Exception e) {
					this.logConnector.log(e);
				}
			}
		}
		if (providerListeners != null) {
			for (final StreamListener listener : providerListeners) {
				try {
					if (isOutputStream) {
						listener.onOutputStreamClose(tuple.getProviderPort(), path);
					} else {
						listener.onInputStreamClose(tuple.getProviderPort(), path);
					}
				} catch (final Exception e) {
					this.logConnector.log(e);
				}
			}
		}
	}

	/**
	 * Subscribes to element events for a specific port and (recursive) path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @param path the path
	 * @param recursive the recursive
	 * @param dataElementEventListener the data element event listener
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean subscribe(final String moduleId, final ProsumerPort prosumerPort, final String[] path, final boolean recursive, final DataElementEventListener dataElementEventListener) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.RECEIVE_EVENTS);
		if (!ObjectValidator.checkArgsNotNull(moduleId, prosumerPort, path) || !moduleId.equals(prosumerPort.getModuleId()) || !ObjectValidator.checkPath(path)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___SUBSCRIBE, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___RECURSIVE, new Boolean(recursive)));
		boolean result = false;
		this.subscriptionAndListenerWriteLock.lock();
		Set<DataElementEventSubscription> subscriptions = this.notificationSubscriptions.get(prosumerPort);
		if (subscriptions == null) {
			subscriptions = new HashSet<DataElementEventSubscription>();
			this.notificationSubscriptions.put(prosumerPort, subscriptions);
			subscriptions.add(new DataElementEventSubscription(path, recursive, dataElementEventListener));
			result = true;
		} else {
			if (!isSubscribed(moduleId, prosumerPort, path)) {
				subscriptions.add(new DataElementEventSubscription(path, recursive, dataElementEventListener));
			}
			result = true;
		}
		this.subscriptionAndListenerWriteLock.unlock();
		return result;
	}

	/**
	 * Unlocks an element at given path.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	int unlock(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.READ_DATA, ModuleRight.WRITE_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___UNLOCK, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		try {
			return tuple.provider.unlock(tuple.providerPort, path);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
	}

	/**
	 * Unregisters a prosumer port.
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	void unregisterProsumerPort(final String moduleId, final ProsumerPort prosumerPort) throws BrokerException {
		checkModuleState(moduleId);
		if (!ObjectValidator.checkArgsNotNull(moduleId, prosumerPort) || !moduleId.equals(prosumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___UNREGISTER_PROSUMER_PORT, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER));
		this.streamCloseListeners.remove(prosumerPort);
		this.notificationSubscriptions.remove(prosumerPort);
		this.broker.unregisterProsumerPort(moduleId, prosumerPort);
	}

	/**
	 * Unregisters a provider port.
	 *
	 * @param moduleId the module ID
	 * @param providerPort the provider port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	void unregisterProviderPort(final String moduleId, final ProviderPort providerPort) throws BrokerException {
		checkModuleState(moduleId);
		if (!ObjectValidator.checkArgsNotNull(moduleId, providerPort) || !moduleId.equals(providerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___UNREGISTER_PROVIDER_PORT, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROVIDER));
		this.streamCloseListeners.remove(providerPort);
		this.broker.unregisterProviderPort(moduleId, providerPort);
	}

	/**
	 * Unsubscribes a given listener from a given port.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 * <p>
	 * TODO: Currently unused.
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @param dataElementEventListener the data element event listener
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean unsubscribe(final String moduleId, final ProsumerPort prosumerPort, final DataElementEventListener dataElementEventListener) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.RECEIVE_EVENTS);
		if ((prosumerPort == null) || (moduleId == null) || !moduleId.equals(prosumerPort.getModuleId()) || (dataElementEventListener == null)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___UNSUBSCRIBE, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER));
		boolean result = false;
		this.subscriptionAndListenerWriteLock.lock();
		final Set<DataElementEventSubscription> subscriptions = this.notificationSubscriptions.get(prosumerPort);
		if (subscriptions != null) {
			for (final DataElementEventSubscription subscription : subscriptions) {
				if (subscription.getDataElementEventListener().equals(dataElementEventListener)) {
					subscriptions.remove(subscription);
					result = true;
				}
			}
			if (subscriptions.isEmpty()) {
				this.notificationSubscriptions.remove(prosumerPort);
			}
		}
		this.subscriptionAndListenerWriteLock.unlock();
		return result;
	}

	/**
	 * Unsubscribes listeners from a given port and a specific path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @param path the path
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean unsubscribe(final String moduleId, final ProsumerPort prosumerPort, final String[] path) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.RECEIVE_EVENTS);
		if ((prosumerPort == null) || (moduleId == null) || !moduleId.equals(prosumerPort.getModuleId()) || !ObjectValidator.checkPath(path)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___UNSUBSCRIBE, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		boolean result = false;
		this.subscriptionAndListenerWriteLock.lock();
		final Set<DataElementEventSubscription> subscriptions = this.notificationSubscriptions.get(prosumerPort);
		if (subscriptions != null) {
			for (final DataElementEventSubscription subscription : subscriptions) {
				if (Arrays.equals(subscription.getPath(), path)) {
					subscriptions.remove(subscription);
					result = true;
				}
			}
			if (subscriptions.isEmpty()) {
				this.notificationSubscriptions.remove(prosumerPort);
			}
		}
		this.subscriptionAndListenerWriteLock.unlock();
		return result;
	}

	/**
	 * Unsubscribes a given listener from a given port and a specific path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @param path the path
	 * @param dataElementEventListener the data element event listener
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean unsubscribe(final String moduleId, final ProsumerPort prosumerPort, final String[] path, final DataElementEventListener dataElementEventListener) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.RECEIVE_EVENTS);
		if ((prosumerPort == null) || (moduleId == null) || !moduleId.equals(prosumerPort.getModuleId()) || !ObjectValidator.checkPath(path) || (dataElementEventListener == null)) {
			throw new BrokerException("invalid arguments");
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___UNSUBSCRIBE, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, prosumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		boolean result = false;
		this.subscriptionAndListenerWriteLock.lock();
		final Set<DataElementEventSubscription> subscriptions = this.notificationSubscriptions.get(prosumerPort);
		if (subscriptions != null) {
			for (final DataElementEventSubscription subscription : subscriptions) {
				if (Arrays.equals(subscription.getPath(), path) && subscription.getDataElementEventListener().equals(dataElementEventListener)) {
					subscriptions.remove(subscription);
					result = true;
				}
			}
			if (subscriptions.isEmpty()) {
				this.notificationSubscriptions.remove(prosumerPort);
			}
		}
		this.subscriptionAndListenerWriteLock.unlock();
		return result;
	}

	/**
	 * Unsubscribes all listeners from a given port.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param moduleId the module ID
	 * @param prosumerPort the prosumer port
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 */
	boolean unsubscribeAll(final String moduleId, final ProsumerPort prosumerPort) throws BrokerException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.RECEIVE_EVENTS);
		if (!ObjectValidator.checkArgsNotNull(moduleId, prosumerPort) || !moduleId.equals(prosumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		return removeAllSubscriptionsInternal(moduleId, prosumerPort);
	}

	/**
	 * Writes data to an element at given path, creates it if necessary (including parent folders).
	 * <p>
	 * Required rights: WRITE_DATA
	 *
	 * @param moduleId the module ID
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the output stream to write to (null if read only)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	OutputStream writeData(final String moduleId, final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		checkModuleState(moduleId);
		this.authManager.verifyAllComponentRights(moduleId, ModuleRight.WRITE_DATA);
		checkBrokerState();
		if (!ObjectValidator.checkArgsNotNull(moduleId, sendingProsumerPort, path) || !ObjectValidator.checkPath(path) || !moduleId.equals(sendingProsumerPort.getModuleId())) {
			throw new BrokerException("invalid arguments");
		}
		final PortProviderTuple tuple = checkAndGetConnectedProvider(sendingProsumerPort);
		OutputStream out = null;
		try {
			out = tuple.provider.writeData(tuple.providerPort, path);
		} catch (final Exception e) {
			if (e instanceof ModuleException) {
				throw e;
			} else {
				this.logConnector.log(e);
				throw new ModuleException("uncaught module exception received");
			}
		}
		announceModuleActivity(new ModuleActivityEvent(ModuleActivityEventType.MOD_ACT___WRITE_DATA, moduleId).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORTID, sendingProsumerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PORT_TYPE, PortType.PROSUMER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID, tuple.providerPort.getModuleId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORTID, tuple.providerPort.getPortId()).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_PORT_TYPE, PortType.PROVIDER).addProperty(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH, path));
		if (out != null) {
			return this.broker.wrapOutputStream(sendingProsumerPort, tuple.providerPort, out, path);
		} else {
			return null;
		}
	}
}
