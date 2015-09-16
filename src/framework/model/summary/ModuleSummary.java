package framework.model.summary;

import java.util.Set;

/**
 * Summarizes a module (configuration and ports).
 *
 * @author Stefan Werner
 */
public final class ModuleSummary implements Summary {

	private final String moduleId;
	private final String moduleName;
	private final String moduleType;
	private final int moduleRights;
	private final Set<PortSummary> ports;

	/**
	 * Instantiates a new module summary.
	 *
	 * @param moduleId the module ID
	 * @param moduleName the module name
	 * @param moduleType the module type
	 * @param moduleRights the rights
	 * @param ports the port summaries
	 */
	public ModuleSummary(final String moduleId, final String moduleName, final String moduleType, final int moduleRights, final Set<PortSummary> ports) {
		this.moduleId = moduleId;
		this.moduleName = moduleName;
		this.moduleType = moduleType;
		this.moduleRights = moduleRights;
		this.ports = ports;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ModuleSummary)) {
			return false;
		}
		final ModuleSummary other = (ModuleSummary) obj;
		if (this.moduleId == null) {
			if (other.moduleId != null) {
				return false;
			}
		} else if (!this.moduleId.equals(other.moduleId)) {
			return false;
		}
		if (this.moduleType == null) {
			if (other.moduleType != null) {
				return false;
			}
		} else if (!this.moduleType.equals(other.moduleType)) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the module ID.
	 *
	 * @return the module ID
	 */
	public String getModuleId() {
		return this.moduleId;
	}

	/**
	 * Gets the module name.
	 *
	 * @return the module name
	 */
	public String getModuleName() {
		return this.moduleName;
	}

	/**
	 * Gets the module rights.
	 *
	 * @return the module rights
	 */
	public int getModuleRights() {
		return this.moduleRights;
	}

	/**
	 * Gets the module type.
	 *
	 * @return the module type
	 */
	public String getModuleType() {
		return this.moduleType;
	}

	/**
	 * Gets the ports.
	 *
	 * @return the ports
	 */
	public Set<PortSummary> getPorts() {
		return this.ports;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.moduleId == null) ? 0 : this.moduleId.hashCode());
		result = (prime * result) + ((this.moduleType == null) ? 0 : this.moduleType.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return this.moduleName + " (" + this.moduleType + " / " + this.moduleId + ")";
	}
}