package framework.control;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import module.iface.DataElementEventListener;
import module.iface.Prosumer;
import module.iface.StreamListener;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.DataElementEventSubscription;
import framework.model.Port;
import framework.model.ProsumerPort;
import framework.model.ProviderPort;
import framework.model.type.DataElementType;

/**
 * Connector to the framework for prosumer modules. Here the ID of the module is added as a token.
 *
 * @author Stefan Werner
 */
public final class ProsumerConnector {

	private final ModuleActionHandler handler;
	private final String moduleId;

	/**
	 * Instantiates a new prosumer connector.
	 *
	 * @param handler the handler
	 * @param moduleId the module ID
	 */
	public ProsumerConnector(final ModuleActionHandler handler, final String moduleId) {
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
	 * Checks and locks an element at given path. See {@link module.iface.Provider#checkAndLock(ProviderPort, String[])} for more details.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#checkAndLock(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public int checkAndLock(final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.checkAndLock(this.moduleId, sendingProsumerPort, path);
	}

	/**
	 * Creates a folder. If necessary parent folders are created automatically. See {@link module.iface.Provider#createFolder(ProviderPort, String[])} for more
	 * details.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path of the folder to create
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#createFolder(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public int createFolder(final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.createFolder(this.moduleId, sendingProsumerPort, path);
	}

	/**
	 * Deletes an element at a given path. See {@link module.iface.Provider#delete(ProviderPort, String[])} for more details.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#delete(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public int delete(final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.delete(this.moduleId, sendingProsumerPort, path);
	}

	/**
	 * Gets the child elements under a given parent path.
	 * <p>
	 * Required rights: READ_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path of the parent element
	 * @param recursive set to true to get all children recursively (relative depth >= 1)
	 * @return the child elements (null if non existing/error or no children supported, for example within file elements)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#getChildElements(java.lang.String, framework.model.ProsumerPort, java.lang.String[], boolean)
	 */
	public Set<DataElement> getChildElements(final ProsumerPort sendingProsumerPort, final String[] path, final boolean recursive) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.getChildElements(this.moduleId, sendingProsumerPort, path, recursive);
	}

	/**
	 * Gets the element at a given path.
	 * <p>
	 * Required rights: READ_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the element (null if non existing or error)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#getElement(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public DataElement getElement(final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.getElement(this.moduleId, sendingProsumerPort, path);
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
	 * Gets all subscriptions of a given prosumer port.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param prosumerPort the prosumer port
	 * @return the subscriptions
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#getSubscriptions(java.lang.String, framework.model.ProsumerPort)
	 */
	public Set<DataElementEventSubscription> getSubscriptions(final ProsumerPort prosumerPort) throws BrokerException, AuthorizationException {
		return this.handler.getSubscriptions(this.moduleId, prosumerPort);
	}

	/**
	 * Gets all the subscriptions for a prosumer port that include the given path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param prosumerPort the prosumer port
	 * @param path the path
	 * @return the subscriptions
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#getSubscription(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public Set<DataElementEventSubscription> getSubscriptions(final ProsumerPort prosumerPort, final String[] path) throws BrokerException, AuthorizationException {
		return this.handler.getSubscriptions(this.moduleId, prosumerPort, path);
	}

	/**
	 * Gets the supported module commands (for a particular path).
	 * <p>
	 * Required rights: SEND_COMMAND
	 *
	 * @param sendingPort the sending port
	 * @param path the path
	 * @return the supported module commands (may be null, for example if module does not answer before a timeout occurs)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 */
	public Set<String> getSupportedModuleCommands(final Port sendingPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.getSupportedModuleCommands(this.moduleId, sendingPort, path);
	}

	/**
	 * Gets the type of the element at a given path.
	 * <p>
	 * Required rights: READ_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the element type (null if no such element)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#getType(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public DataElementType getType(final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.getType(this.moduleId, sendingProsumerPort, path);
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
	 * Checks if module is subscribed to events from a given port and path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param prosumerPort the prosumer port
	 * @param path the path
	 * @return true, if subscribed
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#isSubscribed(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public boolean isSubscribed(final ProsumerPort prosumerPort, final String[] path) throws BrokerException, AuthorizationException {
		return this.handler.isSubscribed(this.moduleId, prosumerPort, path);
	}

	/**
	 * Moves an element from one path to another within the same port. If necessary parent folders for destination are created automatically. Will NOT overwrite
	 * an existing destination element. See {@link module.iface.Provider#move(ProviderPort, String[], String[])} for more details.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param srcPath the source path
	 * @param destPath the destination path
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#move(java.lang.String, framework.model.ProsumerPort, java.lang.String[], java.lang.String[])
	 */
	public int move(final ProsumerPort sendingProsumerPort, final String[] srcPath, final String[] destPath) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.move(this.moduleId, sendingProsumerPort, srcPath, destPath);
	}

	/**
	 * Reads data from an element at given path.
	 * <p>
	 * Required rights: READ_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the input stream to read from (null if no such element or no data)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#readData(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public InputStream readData(final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.readData(this.moduleId, sendingProsumerPort, path);
	}

	/**
	 * Registers a prosumer port.
	 *
	 * @param prosumer the prosumer
	 * @param portId the port id
	 * @param maxConnections the maximum number of concurrent active connections
	 * @return the prosumer port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @see framework.iface.ModuleActionHandler#registerProsumerPort(java.lang.String, module.iface.Prosumer, java.lang.String, int)
	 */
	public ProsumerPort registerProsumerPort(final Prosumer prosumer, final String portId, final int maxConnections) throws BrokerException {
		return this.handler.registerProsumerPort(this.moduleId, prosumer, portId, maxConnections);
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
	 * Requests connected provider status (asynchronous call, wait for event).
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @see framework.iface.ModuleActionHandler#getConnectedProviderStatus(java.lang.String, framework.model.ProsumerPort)
	 */
	public void requestConnectedProviderStatus(final ProsumerPort sendingProsumerPort) throws BrokerException, ModuleException {
		this.handler.requestConnectedProviderStatus(this.moduleId, sendingProsumerPort);
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
	 * Subscribes to element events for a specific port and (recursive) path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param prosumerPort the prosumer port
	 * @param path the path
	 * @param recursive the recursive
	 * @param dataElementEventListener the data element event listener
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#subscribe(java.lang.String, framework.model.ProsumerPort, java.lang.String[], boolean)
	 */
	public boolean subscribe(final ProsumerPort prosumerPort, final String[] path, final boolean recursive, final DataElementEventListener dataElementEventListener) throws BrokerException, AuthorizationException {
		return this.handler.subscribe(this.moduleId, prosumerPort, path, recursive, dataElementEventListener);
	}

	/**
	 * Unlocks an element at given path.
	 * <p>
	 * Required rights: READ_DATA, WRITE_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the result code (see Provider interface)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#unlock(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public int unlock(final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.unlock(this.moduleId, sendingProsumerPort, path);
	}

	/**
	 * Unregisters a prosumer port.
	 *
	 * @param prosumerPort the prosumer port
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @see framework.iface.ModuleActionHandler#unregisterProsumerPort(java.lang.String, framework.model.ProsumerPort)
	 */
	public void unregisterProsumerPort(final ProsumerPort prosumerPort) throws BrokerException {
		this.handler.unregisterProsumerPort(this.moduleId, prosumerPort);
	}

	/**
	 * Unsubscribes listeners from a given port and a specific path.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param prosumerPort the prosumer port
	 * @param path the path
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#unsubscribe(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public boolean unsubscribe(final ProsumerPort prosumerPort, final String[] path) throws BrokerException, AuthorizationException {
		return this.handler.unsubscribe(this.moduleId, prosumerPort, path);
	}

	/**
	 * Unsubscribes all listeners from a given port.
	 * <p>
	 * Required rights: RECEIVE_EVENTS
	 *
	 * @param prosumerPort the prosumer port
	 * @return true, if successful
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#unsubscribeAll(java.lang.String, framework.model.ProsumerPort)
	 */
	public boolean unsubscribeAll(final ProsumerPort prosumerPort) throws BrokerException, AuthorizationException {
		return this.handler.unsubscribeAll(this.moduleId, prosumerPort);
	}

	/**
	 * Writes data to an element at given path, creates it if necessary (including parent folders).
	 * <p>
	 * Required rights: WRITE_DATA
	 *
	 * @param sendingProsumerPort the sending prosumer port
	 * @param path the path
	 * @return the output stream to write to (null if read only)
	 * @throws BrokerException if in wrong state, illegal arguments given or some other error
	 * @throws ModuleException if an exception in a connected module occurs (usually wraps such exceptions)
	 * @throws AuthorizationException if rights are insufficient
	 * @see framework.iface.ModuleActionHandler#writeData(java.lang.String, framework.model.ProsumerPort, java.lang.String[])
	 */
	public OutputStream writeData(final ProsumerPort sendingProsumerPort, final String[] path) throws BrokerException, ModuleException, AuthorizationException {
		return this.handler.writeData(this.moduleId, sendingProsumerPort, path);
	}
}
