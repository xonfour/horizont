package db.iface;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import framework.model.summary.BaseConfigurationSummary;
import framework.model.summary.ConnectionSummary;

/**
 * Holds the the complete system's configuration, module connections and every component's configuration.
 *
 * @author Stefan Werner
 */
public interface BaseConfigurationController {

	/**
	 * Adds a new control interface configuration.
	 *
	 * @param ciType the CI type
	 * @param ciId the CI ID
	 * @param ciName the CI name
	 * @param rights the rights
	 * @return the component configuration controller
	 */
	public ComponentConfigurationController addCIConfiguration(String ciType, String ciId, String ciName, int rights);

	/**
	 * Adds a new module configuration.
	 *
	 * @param moduleType the module type
	 * @param moduleId the module id
	 * @param moduleName the module name
	 * @param rights the rights
	 * @return the component configuration controller
	 */
	public ComponentConfigurationController addModuleConfiguration(String moduleType, String moduleId, String moduleName, int rights);

	/**
	 * Adds or updates a port connection.
	 *
	 * @param connectionSummary the connection summary to update
	 * @return true, if successful
	 */
	public boolean addOrUpdatePortConnection(ConnectionSummary connectionSummary);

	/**
	 * Exports the complete configuration.
	 *
	 * @param out the output stream to write to
	 * @return true, if successful
	 */
	public boolean exportCompleteConfiguration(OutputStream out);

	/**
	 * Exports the configuration partly.
	 *
	 * @param out the output stream to write to
	 * @param exportPortConnections the port connections to export
	 * @param moduleIdsToExport the module IDs to export
	 * @param ciIdsToExport the CI IDs to export
	 * @return true, if successful
	 */
	public boolean exportConfiguration(OutputStream out, boolean exportPortConnections, Set<String> moduleIdsToExport, Set<String> ciIdsToExport);

	/**
	 * Gets a summary of a given base configuration.
	 *
	 * @param in the input stream to read the base configuration data from
	 * @return the base configuration summary
	 */
	public BaseConfigurationSummary getBaseConfigurationSummary(InputStream in);

	/**
	 * Gets a control interface configuration.
	 *
	 * @param ciId the CI ID
	 * @return the CI configuration
	 */
	public ComponentConfigurationController getCIConfiguration(String ciId);

	/**
	 * Gets all control interface configurations.
	 *
	 * @return the CI configurations
	 */
	public Map<String, ComponentConfigurationController> getCIConfigurations();

	/**
	 * Gets the name of a control interface.
	 *
	 * @param ciId the CI ID
	 * @return the CI name
	 */
	public String getCIName(String ciId);

	/**
	 * Gets the rights of a control interface.
	 *
	 * @param ciId the CI ID
	 * @return the CI rights
	 */
	public int getCIRights(String ciId);

	/**
	 * Gets the type of a control interface.
	 *
	 * @param ciId the CI ID
	 * @return the CI type
	 */
	public String getCIType(String ciId);

	/**
	 * Gets the base configuration summary of the currently used configuration.
	 *
	 * @return the base configuration summary
	 */
	public BaseConfigurationSummary getCurrentBaseConfigurationSummary();

	/**
	 * Gets a module configuration.
	 *
	 * @param moduleId the module ID
	 * @return the module configuration
	 */
	public ComponentConfigurationController getModuleConfiguration(String moduleId);

	/**
	 * Gets all module configurations.
	 *
	 * @return the module configurations
	 */
	public Map<String, ComponentConfigurationController> getModuleConfigurations();

	/**
	 * Gets the name of a module.
	 *
	 * @param moduleId the module ID
	 * @return the module name
	 */
	public String getModuleName(String moduleId);

	/**
	 * Gets the rights of a module.
	 *
	 * @param moduleId the module ID
	 * @return the module rights
	 */
	public int getModuleRights(String moduleId);

	/**
	 * Gets the type of a module.
	 *
	 * @param moduleId the module ID
	 * @return the module type
	 */
	public String getModuleType(String moduleId);

	/**
	 * Gets all port connections.
	 *
	 * @return the port connections
	 */
	public Set<ConnectionSummary> getPortConnections();

	/**
	 * Gets the version of the current configuration.
	 *
	 * @return the version
	 */
	public int getVersion();

	/**
	 * Import a complete configuration.
	 *
	 * @param in the input stream to read configuration from
	 * @param overwrite set to true to overwrite existing configuration elements
	 * @return true, if successful
	 */
	public boolean importCompleteConfiguration(InputStream in, boolean overwrite);

	/**
	 * Import configuration partly.
	 *
	 * @param in the input stream to read configuration from
	 * @param importPortConnections set to true to import port connections
	 * @param moduleIdsToImport the module IDs to import
	 * @param ciIdsToImport the CI IDs to import
	 * @return true, if successful
	 */
	public boolean importConfiguration(InputStream in, boolean importPortConnections, Set<String> moduleIdsToImport, Set<String> ciIdsToImport);

	/**
	 * Removes a control interface configuration.
	 *
	 * @param ciId the CI ID
	 * @return true, if successful
	 */
	public boolean removeCIConfiguration(String ciId);

	/**
	 * Removes a module configuration.
	 *
	 * @param moduleId the module ID
	 * @return true, if successful
	 */
	public boolean removeModuleConfiguration(String moduleId);

	/**
	 * Removes a port connection.
	 *
	 * @param connectionSummary the connection summary
	 * @return true, if successful
	 */
	public boolean removePortConnection(ConnectionSummary connectionSummary);

	/**
	 * Restores a complete configuration, removing existing one.
	 *
	 * @param in the input stream to read configuration from
	 * @return true, if successful
	 */
	public boolean restoreConfiguration(InputStream in);

	/**
	 * Sets a control interface's name.
	 *
	 * @param ciId the CI ID
	 * @param ciName the CI name
	 * @return true, if successful
	 */
	public boolean setCIName(String ciId, String ciName);

	/**
	 * Sets a control interface's rights.
	 *
	 * @param ciId the CI ID
	 * @param rights the rights
	 * @return true, if successful
	 */
	public boolean setCIRights(String ciId, int rights);

	/**
	 * Sets a module's name.
	 *
	 * @param moduleId the module ID
	 * @param moduleName the module name
	 * @return true, if successful
	 */
	public boolean setModuleName(String moduleId, String moduleName);

	/**
	 * Sets a module's rights.
	 *
	 * @param moduleId the module ID
	 * @param rights the rights
	 * @return true, if successful
	 */
	public boolean setModuleRights(String moduleId, int rights);

	/**
	 * Sets all port connections.
	 *
	 * @param connections the connections
	 * @return true, if successful
	 */
	public boolean setPortConnections(Set<ConnectionSummary> connections);

	/**
	 * Sets the version.
	 *
	 * @param version the version
	 * @return true, if successful
	 */
	public boolean setVersion(int version);

	/**
	 * Updates a port connection.
	 *
	 * @param connectionSummary the connection summary
	 * @return true, if successful
	 */
	public boolean updatePortConnection(ConnectionSummary connectionSummary);
}