package framework.model.summary;

import java.util.HashSet;
import java.util.Set;

/**
 * Summarizes a base configuration.
 *
 * @author Stefan Werner
 */
public final class BaseConfigurationSummary implements Summary {

	private final Set<ControlInterfaceSummary> ciIds;
	private final boolean hasPortConnections;
	private final Set<ModuleSummary> moduleIds;

	/**
	 * Instantiates a new base configuration summary.
	 *
	 * @param hasPortConnections the has port connections
	 * @param moduleIds the module IDs
	 * @param ciIds the CI IDs
	 */
	public BaseConfigurationSummary(final boolean hasPortConnections, Set<ModuleSummary> moduleIds, Set<ControlInterfaceSummary> ciIds) {
		this.hasPortConnections = hasPortConnections;
		this.moduleIds = moduleIds;
		if (moduleIds == null) {
			moduleIds = new HashSet<ModuleSummary>();
		}
		this.ciIds = ciIds;
		if (ciIds == null) {
			ciIds = new HashSet<ControlInterfaceSummary>();
		}
	}

	/**
	 * Gets the control interface IDs.
	 *
	 * @return the CI IDs
	 */
	public Set<ControlInterfaceSummary> getCiIds() {
		return this.ciIds;
	}

	/**
	 * Gets the module IDs.
	 *
	 * @return the module IDs
	 */
	public Set<ModuleSummary> getModuleIds() {
		return this.moduleIds;
	}

	/**
	 * Checks for control interface configurations.
	 *
	 * @return true, if included
	 */
	public boolean hasCIConfigurations() {
		return !this.ciIds.isEmpty();
	}

	/**
	 * Checks for module configurations.
	 *
	 * @return true, if included
	 */
	public boolean hasModuleConfigurations() {
		return !this.moduleIds.isEmpty();
	}

	/**
	 * Checks for port connections.
	 *
	 * @return true, if included
	 */
	public boolean hasPortConnections() {
		return this.hasPortConnections;
	}
}
