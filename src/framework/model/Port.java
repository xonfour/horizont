package framework.model;

/**
 * Basis for all module ports.
 *
 * @author Stefan Werner
 */
public abstract class Port {

	private final int maxConnections;
	private final String moduleId;
	private final String portId;

	/**
	 * Instantiates a new port.
	 *
	 * @param moduleId the module ID
	 * @param portId the port ID
	 * @param maxConnections the maximum number of concurrent active connections
	 * @throws IllegalArgumentException the illegal argument exception
	 */
	public Port(final String moduleId, final String portId, final int maxConnections) throws IllegalArgumentException {
		this.moduleId = moduleId;
		this.portId = portId;
		this.maxConnections = maxConnections;
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
		if (!(obj instanceof Port)) {
			return false;
		}
		final Port other = (Port) obj;
		if (this.moduleId == null) {
			if (other.moduleId != null) {
				return false;
			}
		} else if (!this.moduleId.equals(other.moduleId)) {
			return false;
		}
		if (this.portId == null) {
			if (other.portId != null) {
				return false;
			}
		} else if (!this.portId.equals(other.portId)) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the maximum number of concurrent active connections.
	 *
	 * @return the maximum number of concurrent active connections
	 */
	public int getMaxConnections() {
		return this.maxConnections;
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
	 * Gets the port ID.
	 *
	 * @return the port ID
	 */
	public String getPortId() {
		return this.portId;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.moduleId == null) ? 0 : this.moduleId.hashCode());
		result = (prime * result) + ((this.portId == null) ? 0 : this.portId.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "Port [moduleId=" + this.moduleId + ", portId=" + this.portId + ", maxConnections=" + this.maxConnections + "]";
	}
}
