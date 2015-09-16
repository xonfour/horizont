package framework.control;

import java.util.Map;
import java.util.Set;

import module.iface.Provider;
import module.iface.StreamListener;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.Port;
import framework.model.ProviderPort;
import framework.model.event.DataElementEvent;
import framework.model.event.ProviderStateEvent;
import framework.model.event.type.DataElementEventType;

/**
 * Connector to the framework for provider modules. Here the ID of the module is added as a token.
 *
 * @author Stefan Werner
 */
public final class ProviderConnector {

	private final ModuleActionHandler handler;
	private final String moduleId;

	/**
	 * Instantiates a new provider connector.
	 *
	 * @param handler the handler
	 * @param moduleId the module ID
	 */
	public ProviderConnector(final ModuleActionHandler handler, final String moduleId) {
		this.handler = handler;
		this.moduleId = moduleId;
	}

	/**
	 * Adds a stream listener. Listener methods will get called whenever a stream at the given port is closed.
	 * <p>
	 * Required rights: OBSERVE_STREAMS
	 *
	 * @param port the port
	 * @param listener the listener
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.control.ModuleActionHandler#addStreamListener(java.lang.String, framework.model.Port, module.iface.StreamListener)
	 */
	public boolean addStreamListener(final Port port, final StreamListener listener) throws BrokerException, AuthorizationException {
		return this.handler.addStreamListener(this.moduleId, port, listener);
	}

	/**
	 * Gets a new localization connector.
	 *
	 * @return the new localization connector
	 */
	public LocalizationConnector getNewLocalizationConnector() {
		return this.handler.getNewLocalizationConnector(this.moduleId);
	}

	/**
	 * Gets the own rights.
	 *
	 * @return the own rights
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	public int getOwnRights() throws BrokerException {
		return this.handler.getRights(this.moduleId);
	}

	/**
	 * Gets the supported module commands (for a particular path).
	 * <p>
	 * Required rights: SEND_COMMAND
	 *
	 * @param sendingPort the sending port
	 * @param path the path to get commands for (may be null)
	 * @return the supported module commands (may be null, for example if module does not answer before a timeout occurs)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	public Set<String> getSupportedModuleCommands(final Port sendingPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.getSupportedModuleCommands(this.moduleId, sendingPort, path);
	}

	/**
	 * Checks if a given port is connected.
	 *
	 * @param port the port
	 * @return true, if connected
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @see framework.iface.ModuleActionHandler#isConnected(java.lang.String, framework.model.Port)
	 */
	public boolean isConnected(final Port port) throws BrokerException {
		return this.handler.isConnected(this.moduleId, port);
	}

	/**
	 * Checks if broker is running.
	 *
	 * @return true, if running
	 * @see framework.iface.ModuleActionHandler#isRunning()
	 */
	public boolean isRunning() {
		return this.handler.isRunning();
	}

	/**
	 * Registers a provider port.
	 *
	 * @param provider the provider
	 * @param portId the port id
	 * @param maxConnections the maximum number of concurrent active connections
	 * @return the provider port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @see framework.iface.ModuleActionHandler#registerProviderPort(java.lang.String, module.iface.Provider, java.lang.String, int)
	 */
	public ProviderPort registerProviderPort(final Provider provider, final String portId, final int maxConnections) throws BrokerException {
		return this.handler.registerProviderPort(this.moduleId, provider, portId, maxConnections);
	}

	/**
	 * Removes all stream listeners for a given port.
	 * <p>
	 * Required rights: OBSERVE_STREAMS
	 *
	 * @param port the port
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.control.ModuleActionHandler#removeAllStreamListeners(java.lang.String, framework.model.Port)
	 */
	public boolean removeAllStreamListeners(final Port port) throws BrokerException, AuthorizationException {
		return this.handler.removeAllStreamListeners(this.moduleId, port);
	}

	/**
	 * Removes a stream listener for a given port.
	 * <p>
	 * Required rights: OBSERVE_STREAMS
	 *
	 * @param port the port
	 * @param listener the listener
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.control.ModuleActionHandler#removeStreamListener(java.lang.String, framework.model.Port, module.iface.StreamListener)
	 */
	public boolean removeStreamListener(final Port port, final StreamListener listener) throws BrokerException, AuthorizationException {
		return this.handler.removeStreamListener(this.moduleId, port, listener);
	}

	/**
	 * Sends element events to connected prosumers.
	 *
	 * @param sendingProviderPort the sending provider port
	 * @param element the element
	 * @param eventType the event type
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @see framework.iface.ModuleActionHandler#sendElementEvent(java.lang.String, framework.model.ProviderPort, framework.model.DataElement,
	 *      framework.model.event.type.DataElementEventType)
	 */
	public void sendElementEvent(final ProviderPort sendingProviderPort, final DataElement element, final DataElementEventType eventType) throws BrokerException {
		this.handler.sendElementEvent(this.moduleId, sendingProviderPort, new DataElementEvent(element, eventType));
	}

	/**
	 * Sends a module command.
	 * <p>
	 * Required rights: SEND_COMMAND
	 *
	 * @param sendingPort the sending port
	 * @param command the command
	 * @param path the path (may be null)
	 * @param properties the properties (may be null)
	 * @return the answer from module (may be null, for example if module does not answer before a timeout occurs)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#sendModuleCommand(java.lang.String, framework.model.Port, java.lang.String[], java.util.Map)
	 */
	public Map<String, String> sendModuleCommand(final Port sendingPort, final String command, final String[] path, final Map<String, String> properties) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.sendModuleCommand(this.moduleId, command, sendingPort, path, properties);
	}

	/**
	 * Sends state event to connected modules.
	 *
	 * @param sendingPort the sending port
	 * @param moduleState the module state
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 */
	public void sendState(final Port sendingPort, final int moduleState) throws BrokerException {
		this.handler.sendState(this.moduleId, sendingPort, new ProviderStateEvent(moduleState));
	}

	/**
	 * Unregisters a provider port.
	 *
	 * @param providerPort the provider port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @see framework.iface.ModuleActionHandler#unregisterProviderPort(java.lang.String, framework.model.ProviderPort)
	 */
	public void unregisterProviderPort(final ProviderPort providerPort) throws BrokerException {
		this.handler.unregisterProviderPort(this.moduleId, providerPort);
	}
}
