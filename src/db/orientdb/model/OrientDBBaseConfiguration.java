package db.orientdb.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Version;

/**
 * Model for persisting a base configuration.
 *
 * @author Stefan Werner
 */
public class OrientDBBaseConfiguration {

	@ManyToMany(cascade = { CascadeType.REMOVE })
	private Map<String, OrientDBComponentConfiguration> ciConfigurations = new HashMap<String, OrientDBComponentConfiguration>();
	private int configVersion = 0;
	@Id
	private Object id;
	@ManyToMany(cascade = { CascadeType.REMOVE })
	private Map<String, OrientDBComponentConfiguration> moduleConfigurations = new HashMap<String, OrientDBComponentConfiguration>();
	@OneToMany(cascade = { CascadeType.REMOVE })
	private Set<OrientDBPortTuple> portConnections = new HashSet<OrientDBPortTuple>();
	@Version
	private Object version;

	/**
	 * Gets all control interface configurations.
	 *
	 * @return the CI configurations
	 */
	public Map<String, OrientDBComponentConfiguration> getCiConfigurations() {
		return this.ciConfigurations;
	}

	/**
	 * Gets the version.
	 *
	 * @return the version
	 */
	public int getConfigVersion() {
		return this.configVersion;
	}

	/**
	 * Gets all module configurations.
	 *
	 * @return the module configurations
	 */
	public Map<String, OrientDBComponentConfiguration> getModuleConfigurations() {
		return this.moduleConfigurations;
	}

	/**
	 * Gets all port connections.
	 *
	 * @return the port connections
	 */
	public Set<OrientDBPortTuple> getPortConnections() {
		return this.portConnections;
	}

	/**
	 * Sets all control interface configurations.
	 *
	 * @param ciConfigurations the CI configurations
	 */
	public void setCiConfigurations(final Map<String, OrientDBComponentConfiguration> ciConfigurations) {
		this.ciConfigurations = ciConfigurations;
	}

	/**
	 * Sets the version.
	 *
	 * @param version the new version
	 */
	public void setConfigVersion(final int version) {
		this.configVersion = version;
	}

	/**
	 * Sets all module configurations.
	 *
	 * @param moduleConfigurations the module configurations
	 */
	public void setModuleConfigurations(final Map<String, OrientDBComponentConfiguration> moduleConfigurations) {
		this.moduleConfigurations = moduleConfigurations;
	}

	/**
	 * Sets all port connections.
	 *
	 * @param portConnections the new port connections
	 */
	public void setPortConnections(final Set<OrientDBPortTuple> portConnections) {
		this.portConnections = portConnections;
	}
}
