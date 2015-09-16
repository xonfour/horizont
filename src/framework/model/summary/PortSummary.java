package framework.model.summary;

import framework.model.type.PortType;

/**
 * Summarizes a port (configuration and status).
 *
 * @author Stefan Werner
 */
public class PortSummary implements Summary {

	private final int currentConnections;
	private final int maxConnections;
	private final String moduleId;
	private final String portId;
	private final PortType type;

	/**
	 * Instantiates a new port summary.
	 *
	 * @param moduleId the module ID
	 * @param type the type
	 * @param portId the port ID
	 */
	public PortSummary(final String moduleId, final PortType type, final String portId) {
		this.moduleId = moduleId;
		this.type = type;
		this.portId = portId;
		if (type == PortType.PROSUMER) {
			this.maxConnections = 1;
		} else if (type == PortType.PROVIDER) {
			this.maxConnections = -1;
		} else {
			this.maxConnections = 0;
		}
		this.currentConnections = 0;
	}

	/**
	 * Instantiates a new port summary.
	 *
	 * @param moduleId the module ID
	 * @param type the type
	 * @param portId the port ID
	 * @param maxConnections the maximum number of concurrent active connections
	 * @param currentConnections the current number of connections
	 */
	public PortSummary(final String moduleId, final PortType type, final String portId, final int maxConnections, final int currentConnections) {
		this.moduleId = moduleId;
		this.type = type;
		this.portId = portId;
		this.maxConnections = maxConnections;
		this.currentConnections = currentConnections;
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
		if (!(obj instanceof PortSummary)) {
			return false;
		}
		final PortSummary other = (PortSummary) obj;
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
		if (this.type != other.type) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the current number of connections.
	 *
	 * @return the current number of connections
	 */
	public int getCurrentConnections() {
		return this.currentConnections;
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

	/**
	 * Gets the type.
	 *
	 * @return the type
	 */
	public PortType getType() {
		return this.type;
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
		result = (prime * result) + ((this.type == null) ? 0 : this.type.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "PortSummary [type=" + this.type + ", portId=" + this.portId + ", moduleId=" + this.moduleId + ", maxConnections=" + this.maxConnections + ", currentConnections=" + this.currentConnections + "]";
	}
}
