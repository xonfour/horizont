package framework.control;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import controlinterface.iface.GeneralEventListener;
import framework.exception.AuthorizationException;
import framework.exception.ControlInterfaceException;
import framework.exception.WrongControlInterfaceStateException;
import framework.model.event.type.GeneralEventType;
import framework.model.event.type.SystemStateType;
import framework.model.summary.BaseConfigurationSummary;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.ControlInterfaceSummary;
import framework.model.summary.ModuleSummary;

/**
 * Connector to the framework for control interfaces. Here the ID of the control interface is added as a token and the CI's state is checked.
 *
 * @author Stefan Werner
 */
final public class ControlInterfaceConnector {

	private final ControlInterfaceActionHandler actionHandler;
	private final String ownCIId;
	private boolean usable = false;

	/**
	 * Instantiates a new control interface connector.
	 *
	 * @param ciId the CI ID
	 * @param controlInterfaceHandler the control interface handler
	 */
	ControlInterfaceConnector(final String ciId, final ControlInterfaceActionHandler controlInterfaceHandler) {
		this.ownCIId = ciId;
		this.actionHandler = controlInterfaceHandler;
	}

	/**
	 * Adds a module connection.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param connectionSummary the connection summary
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean addConnection(final ConnectionSummary connectionSummary) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.addConnection(this.ownCIId, connectionSummary);
	}

	/**
	 * Adds a new control interface.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciType the CI type
	 * @param rights the rights
	 * @return the CI ID
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public String addControlInterface(final String ciType, final int rights) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.addControlInterface(this.ownCIId, ciType, rights);
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
	 * @param generalEventListener the general event listener
	 * @param desiredEventTypes the desired event types
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean addGeneralEventListener(final GeneralEventListener generalEventListener, final GeneralEventType... desiredEventTypes) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.addGeneralEventListener(this.ownCIId, generalEventListener, desiredEventTypes);
	}

	/**
	 * Adds a new module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param moduleType the module type
	 * @param rights the rights
	 * @return the module summary
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public ModuleSummary addModule(final String moduleType, final int rights) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.addModule(this.ownCIId, moduleType, rights);
	}

	/**
	 * Checks if this connector may be used for calls to the framework. Currently it cannot be used from inside the CI's constructor.
	 *
	 * @throws ControlInterfaceException if in wrong state
	 */
	private void checkCIState() throws ControlInterfaceException {
		if (!this.usable) {
			throw new WrongControlInterfaceStateException("connector methods cannot be called from constructor");
		}
	}

	/**
	 * Exits the system.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP, BROKER_RUNNING
	 * <p>
	 * Required rights: CONTROL_STATE
	 *
	 * @param force set to true to force exit even if components fail to stop
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public void exit(final boolean force) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		this.actionHandler.exit(this.ownCIId, force);
	}

	/**
	 * Exports configuration (database).
	 * <p>
	 * Required rights: MANAGE_DATABASE
	 *
	 * @param out the output stream to write to
	 * @param exportPortConnections set to true to export port connections
	 * @param moduleIdsToExport the module IDs to export
	 * @param ciIdsToExport the CI IDs to export
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean exportConfiguration(final OutputStream out, final boolean exportPortConnections, final Set<String> moduleIdsToExport, final Set<String> ciIdsToExport) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.exportConfiguration(this.ownCIId, out, exportPortConnections, moduleIdsToExport, ciIdsToExport);
	}

	/**
	 * Gets all active control interfaces.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @return the active control interfaces
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Set<ControlInterfaceSummary> getActiveControlInterfaces() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getActiveControlInterfaces(this.ownCIId);
	}

	/**
	 * Gets all active modules.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: READ_MODULES_AND_CONNECTIONS
	 *
	 * @return the active modules
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Set<ModuleSummary> getActiveModules() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getActiveModules(this.ownCIId);
	}

	/**
	 * Gets the available control interface types.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @return the available control interface types
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Set<String> getAvailableControlInterfaceTypes() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getAvailableControlInterfaceTypes(this.ownCIId);
	}

	/**
	 * Gets the available module types.
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @return the available module types
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Set<String> getAvailableModuleTypes() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getAvailableModuleTypes(this.ownCIId);
	}

	/**
	 * Gets the base configuration from a given input stream.
	 * <p>
	 * Required rights: MANAGE_DATABASE
	 *
	 * @param in the input stream to read from
	 * @return the base configuration summary
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public BaseConfigurationSummary getBaseConfiguration(final InputStream in) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getBaseConfiguration(this.ownCIId, in);
	}

	/**
	 * Gets all connections (active and inactive).
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: READ_MODULES_AND_CONNECTIONS
	 *
	 * @return the connections
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Set<ConnectionSummary> getConnections() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getConnections(this.ownCIId);
	}

	/**
	 * Gets the rights of a given control interface.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciId the CI ID
	 * @return the control interface rights
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public int getControlInterfaceRights(final String ciId) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getControlInterfaceRights(this.ownCIId, ciId);
	}

	/**
	 * Gets the current base configuration (from database).
	 * <p>
	 * Required rights: MANAGE_DATABASE
	 *
	 * @return the current base configuration summary
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public BaseConfigurationSummary getCurrentBaseConfiguration() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getCurrentBaseConfiguration(this.ownCIId);
	}

	/**
	 * Gets the current system state.
	 *
	 * @return the current system state
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public SystemStateType getCurrentSystemState() throws ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getCurrentSystemState();
	}

	/**
	 * Gets the rights of a given module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING
	 * <p>
	 * Required rights: READ_MODULES_AND_CONNECTIONS
	 *
	 * @param moduleId the module ID
	 * @return the module rights
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public int getModuleRights(final String moduleId) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getModuleRights(this.ownCIId, moduleId);
	}

	/**
	 * Gets a new localization connector for this control interface.
	 *
	 * @return the new localization connector
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public LocalizationConnector getNewLocalizationConnector() throws ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getNewLocalizationConnector(this.ownCIId);
	}

	/**
	 * Gets the own ID.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @return the own ID
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public String getOwnId() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getOwnId(this.ownCIId);
	}

	/**
	 * Gets the own rights.
	 *
	 * @return the own rights
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public int getOwnRights() throws ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getOwnRights(this.ownCIId);
	}

	/**
	 * Gets the supported control interface commands from a given module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param moduleId the module ID
	 * @return the supported control interface commands (may be null, for example if module does not answer before a timeout occurs)
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Set<String> getSupportedControlInterfaceCommands(final String moduleId) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.getSupportedControlInterfaceCommands(this.ownCIId, moduleId);
	}

	/**
	 * Gets the system's data storage location.
	 * <p>
	 * Required rights: DIRECT_STORAGE_ACCESS
	 *
	 * @return the system data storage location
	 * @throws AuthorizationException if rights are insufficient
	 */
	public String getSystemDataStorageLocation() throws AuthorizationException {
		return this.actionHandler.getSystemDataStorageLocation(this.ownCIId);
	}

	/**
	 * Imports configuration partly.
	 * <p>
	 * Required rights: MANAGE_DATABASE
	 *
	 * @param in the input stream to read from
	 * @param importPortConnections set to true to import port connections
	 * @param moduleIdsToImport the module IDs to import
	 * @param ciIdsToImport the CI IDs to import
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean importConfiguration(final InputStream in, final boolean importPortConnections, final Set<String> moduleIdsToImport, final Set<String> ciIdsToImport) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.importConfiguration(this.ownCIId, in, importPortConnections, moduleIdsToImport, ciIdsToImport);
	}

	/**
	 * Refreshes a given connection.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: READ_MODULES_AND_CONNECTIONS
	 *
	 * @param connectionSummaryToUpdate the connection summary to update
	 * @return the updated connection summary
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public ConnectionSummary refreshConnection(final ConnectionSummary connectionSummaryToUpdate) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.refreshConnection(this.ownCIId, connectionSummaryToUpdate);
	}

	/**
	 * Removes a connection.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param connectionSummary the connection summary
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean removeConnection(final ConnectionSummary connectionSummary) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.removeConnection(this.ownCIId, connectionSummary);
	}

	/**
	 * Removes a control interface.
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciIdToRemove the CI ID to remove
	 * @param removeFromDB set to true to remove from database
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean removeControlInterface(final String ciIdToRemove, final boolean removeFromDB) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.removeControlInterface(this.ownCIId, ciIdToRemove, removeFromDB);
	}

	/**
	 * Removes an own general event listener.
	 *
	 * @param generalEventListener the general event listener to remove
	 * @return true, if successful
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean removeGeneralEventListener(final GeneralEventListener generalEventListener) throws ControlInterfaceException {
		checkCIState();
		return this.actionHandler.removeGeneralEventListener(this.ownCIId, generalEventListener);
	}

	/**
	 * Removes a module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param moduleId the module ID to remove
	 * @param removeFromDB set to true to remove it from database
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean removeModule(final String moduleId, final boolean removeFromDB) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.removeModule(this.ownCIId, moduleId, removeFromDB);
	}

	/**
	 * Renames a control interface.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciId the CI ID to rename
	 * @param newName the new name
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean renameControlInterface(final String ciId, final String newName) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.renameControlInterface(this.ownCIId, ciId, newName);
	}

	/**
	 * Renames a module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param moduleId the module ID to rename
	 * @param newName the new name
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean renameModule(final String moduleId, final String newName) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.renameModule(this.ownCIId, moduleId, newName);
	}

	/**
	 * Sends control interface command to given module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param moduleId the module ID to send command to
	 * @param command the command
	 * @param properties the properties (may be null)
	 * @return the answer from module (may be null, for example if module does not answer before a timeout occurs)
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public Map<String, String> sendControlInterfaceCommand(final String moduleId, final String command, final Map<String, String> properties) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.sendControlInterfaceCommand(this.ownCIId, moduleId, command, properties);
	}

	/**
	 * Sets the rights of a given control interface.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING
	 * <p>
	 * Required rights: MANAGE_CIS
	 *
	 * @param ciId the CI ID to set rights for
	 * @param newRights the new rights
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean setControlInterfaceRights(final String ciId, final int newRights) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.setControlInterfaceRights(this.ownCIId, ciId, newRights);
	}

	/**
	 * Sets the rights of a given module.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP, BROKER_RUNNING
	 * <p>
	 * Required rights: MANAGE_MODULES_AND_CONNECTIONS
	 *
	 * @param moduleId the module ID to set rights for
	 * @param newRights the new rights
	 * @return true, if successful
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public boolean setModuleRights(final String moduleId, final int newRights) throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		return this.actionHandler.setModuleRights(this.ownCIId, moduleId, newRights);
	}

	/**
	 * Sets the usable state of this connector (used internally).
	 *
	 * @param usable the new usable
	 */
	void setUsable(final boolean usable) {
		this.usable = usable;
	}

	/**
	 * Starts broker.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP, BROKER_RUNNING
	 * <p>
	 * Required rights: CONTROL_STATE
	 *
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public void startBroker() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		this.actionHandler.startBroker(this.ownCIId);
	}

	/**
	 * Stops broker.
	 * <p>
	 * Disallowed in states: SYSTEM_EXITING, SYSTEM_INITIALIZING, BROKER_SHUTTING_DOWN, ROKER_STARTING_UP, BROKER_STOPPED_AND_READY
	 * <p>
	 * Required rights: CONTROL_STATE
	 *
	 * @throws AuthorizationException if rights are insufficient
	 * @throws ControlInterfaceException if in wrong state, illegal arguments given or some other error
	 */
	public void stopBroker() throws AuthorizationException, ControlInterfaceException {
		checkCIState();
		this.actionHandler.stopBroker(this.ownCIId);
	}
}
