package module.iface;

import java.util.Map;
import java.util.Set;

import framework.model.Port;

/**
 * Basic interface for all module implementations.
 * <p>
 * USAGE: You must not implement this interface nor the extending interfaces Prosumer/Provider. Instead extend one of the following abstract implementations and
 * provide a constructor accepting the specified objects that does the corresponding super() call:<br>
 * AbstractProsumer -> ProsumerConnector, ComponentConfigurationController, LogConnector<br>
 * AbstractProsumerProvider -> ProsumerConnector, ProviderConnector, ComponentConfigurationController, LogConnector<br>
 * AbstractProvider -> ProviderConnector, ComponentConfigurationController, LogConnector
 * <p>
 * IMPORTANT: Methods in this class must be thread-safe.
 *
 * @author Stefan Werner
 */
public abstract interface Module {

	/**
	 * Enters shutdown. Prepare to stop, finish running operations. Return within {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_MANAGEMENT}.
	 * <p>
	 * IMPORTANT: You must still accept incoming calls but try not to make new ones to other modules yourself. Don't (un)register stream listeners here!
	 */
	public void enterShutdown();

	/**
	 * Enters startup. Prepare to start, establish required data structures. Return within
	 * {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_MANAGEMENT}.
	 * <p>
	 * IMPORTANT: You may already accept incoming calls but try not to make any yourself yet. Don't (un)register stream listeners here!
	 */
	public void enterStartup();

	/**
	 * Exits shutdown. Stop operations and communication to other modules. Return within
	 * {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_MANAGEMENT}.
	 * <p>
	 * IMPORTANT: Don't (un)register stream listeners here!
	 */
	public void exitShutdown();

	/**
	 * Exits startup. Start operations, communication to other modules and accepting calls. Return within
	 * {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_MANAGEMENT}.
	 * <p>
	 * IMPORTANT: Don't (un)register stream listeners here!
	 */
	public void exitStartup();

	/**
	 * Gets supported control interface commands. Called from authorized control interfaces to receive supported control interface commands. Answer within
	 * {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_COMMUNICATION}.
	 * <p>
	 * IMPORTANT: Return value SHOULD NOT be null. It is good practice to return an empty Set because null values are returned on call timeout and may be
	 * interpreted as module error.
	 * 
	 * @return the supported control interface commands (not null)
	 */
	public Set<String> getSupportedControlInterfaceCommands();

	/**
	 * Gets supported module commands for given path. Called from authorized connected modules to receive supported module commands. Propagate to other
	 * connected modules and merge result if possible/appropriate. Answer within {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_COMMUNICATION}.
	 * <p>
	 * IMPORTANT: Return value SHOULD NOT be null. It is good practice to return an empty Set because null values are returned on call timeout and may be
	 * interpreted as module error.
	 * 
	 * @param port the port
	 * @param path the path (may be null, for example to send more general commands)
	 * @return the supported module commands (not null)
	 */
	//
	public Set<String> getSupportedModuleCommands(Port port, String[] path);

	/**
	 * Initialize the module. Return within {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_MANAGEMENT}.
	 */
	public void initialize();

	/**
	 * Checks if module is ready. This does not mean that the module can actually answer calls from other modules. It only means that there is no severe problem
	 * that would stop the hole system from working properly. Return within {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_MANAGEMENT}.
	 *
	 * @return true, if module is ready
	 */
	public boolean isReady();

	/**
	 * Executes given control interface commands. Called from authorized control interfaces. Answer within
	 * {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_COMMUNICATION}. To simplify answering use {@link helper.view.CommandResultHelper}.
	 * <p>
	 * IMPORTANT: Return value SHOULD NOT be null. It is good practice to return an empty Set because null values are returned on call timeout and may be
	 * interpreted as module error.
	 *
	 * @param command the command
	 * @param properties the properties (may be null)
	 * @return the answer/result (not null)
	 */
	public Map<String, String> onControlInterfaceCommand(String command, Map<String, String> properties);

	/**
	 * Executes given module commands. Propagate to other connected modules and merge result if possible/appropriate. Answer within
	 * {@link framework.constants.Constants#TIMEOUT_SECONDS___MODULE_COMMUNICATION}. To simplify answering use {@link helper.view.CommandResultHelper}.
	 * <p>
	 * IMPORTANT: Return value SHOULD NOT be null. It is good practice to return an empty Set because null values are returned on call timeout and may be
	 * interpreted as module error.
	 *
	 * @param port the port
	 * @param command the command
	 * @param path the path (may be null)
	 * @param properties the properties (may be null)
	 * @return the answer/result (not null)
	 */
	public Map<String, String> onModuleCommand(Port port, String command, String[] path, Map<String, String> properties);

	/**
	 * Informs about a given port being connected.
	 *
	 * @param port the port
	 */
	public void onPortConnection(Port port);

	/**
	 * Informs about a given port being disconnected.
	 *
	 * @param port the port
	 */
	public void onPortDisconnection(Port port);
}
