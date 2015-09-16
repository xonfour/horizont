package db.orientdb.model;

import javax.persistence.Id;
import javax.persistence.Version;

/**
 * Model for persisting a module connection.
 *
 * @author Stefan Werner
 */
public class OrientDBPortTuple {

	@Id
	private Object id;
	private int priority;
	private int prosumerMaxConnections;
	private String prosumerModuleId;
	private String prosumerPortId;
	private int providerMaxConnections;
	private String providerModuleId;
	private String providerPortId;
	@Version
	private Object version;

	/**
	 * Instantiates a new orient db port tuple.
	 */
	public OrientDBPortTuple() {
	}

	/**
	 * Instantiates a new orient db port tuple.
	 *
	 * @param prosumerModuleId the prosumer module ID
	 * @param prosumerPortId the prosumer port ID
	 * @param prosumerMaxConnections the maximum number of prosumer connections
	 * @param providerModuleId the provider module ID
	 * @param providerPortId the provider port ID
	 * @param providerMaxConnections the maximum number of provider connections
	 * @param priority the priority of the connection (may be negative, default = 0)
	 */
	public OrientDBPortTuple(final String prosumerModuleId, final String prosumerPortId, final int prosumerMaxConnections, final String providerModuleId, final String providerPortId, final int providerMaxConnections, final int priority) {
		this.prosumerModuleId = prosumerModuleId;
		this.prosumerPortId = prosumerPortId;
		this.prosumerMaxConnections = prosumerMaxConnections;
		this.providerModuleId = providerModuleId;
		this.providerPortId = providerPortId;
		this.providerMaxConnections = providerMaxConnections;
		this.priority = priority;
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
		if (!(obj instanceof OrientDBPortTuple)) {
			return false;
		}
		final OrientDBPortTuple other = (OrientDBPortTuple) obj;
		if (getProsumerModuleId() == null) {
			if (other.getProsumerModuleId() != null) {
				return false;
			}
		} else if (!getProsumerModuleId().equals(other.getProsumerModuleId())) {
			return false;
		}
		if (getProsumerPortId() == null) {
			if (other.getProsumerPortId() != null) {
				return false;
			}
		} else if (!getProsumerPortId().equals(other.getProsumerPortId())) {
			return false;
		}
		if (getProviderModuleId() == null) {
			if (other.getProviderModuleId() != null) {
				return false;
			}
		} else if (!getProviderModuleId().equals(other.getProviderModuleId())) {
			return false;
		}
		if (getProviderPortId() == null) {
			if (other.getProviderPortId() != null) {
				return false;
			}
		} else if (!getProviderPortId().equals(other.getProviderPortId())) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the priority.
	 *
	 * @return the priority
	 */
	public int getPriority() {
		return this.priority;
	}

	/**
	 * Gets the maximum number of prosumer connections.
	 *
	 * @return the maximum number of prosumer connections
	 */
	public int getProsumerMaxConnections() {
		return this.prosumerMaxConnections;
	}

	/**
	 * Gets the prosumer module ID.
	 *
	 * @return the prosumer module ID
	 */
	public String getProsumerModuleId() {
		return this.prosumerModuleId;
	}

	/**
	 * Gets the prosumer port ID.
	 *
	 * @return the prosumer port ID
	 */
	public String getProsumerPortId() {
		return this.prosumerPortId;
	}

	/**
	 * Gets the maximum number of provider connections.
	 *
	 * @return the maximum number of provider connections
	 */
	public int getProviderMaxConnections() {
		return this.providerMaxConnections;
	}

	/**
	 * Gets the provider module ID.
	 *
	 * @return the provider module ID
	 */
	public String getProviderModuleId() {
		return this.providerModuleId;
	}

	/**
	 * Gets the provider port ID.
	 *
	 * @return the provider port ID
	 */
	public String getProviderPortId() {
		return this.providerPortId;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((getProsumerModuleId() == null) ? 0 : getProsumerModuleId().hashCode());
		result = (prime * result) + ((getProsumerPortId() == null) ? 0 : getProsumerPortId().hashCode());
		result = (prime * result) + ((getProviderModuleId() == null) ? 0 : getProviderModuleId().hashCode());
		result = (prime * result) + ((getProviderPortId() == null) ? 0 : getProviderPortId().hashCode());
		return result;
	}

	/**
	 * Sets the priority.
	 *
	 * @param priority the priority to set
	 */
	public void setPriority(final int priority) {
		this.priority = priority;
	}

	/**
	 * Sets the maximum number of prosumer connections.
	 *
	 * @param prosumerMaxConnections the new maximum number of prosumer connections
	 */
	public void setProsumerMaxConnections(final int prosumerMaxConnections) {
		this.prosumerMaxConnections = prosumerMaxConnections;
	}

	/**
	 * Sets the prosumer module ID.
	 *
	 * @param prosumerModuleId the new prosumer module ID
	 */
	public void setProsumerModuleId(final String prosumerModuleId) {
		this.prosumerModuleId = prosumerModuleId;
	}

	/**
	 * Sets the prosumer port ID.
	 *
	 * @param prosumerPortId the new prosumer port ID
	 */
	public void setProsumerPortId(final String prosumerPortId) {
		this.prosumerPortId = prosumerPortId;
	}

	/**
	 * Sets the maximum number of provider connections.
	 *
	 * @param providerMaxConnections the new maximum number of provider connections
	 */
	public void setProviderMaxConnections(final int providerMaxConnections) {
		this.providerMaxConnections = providerMaxConnections;
	}

	/**
	 * Sets the provider module ID.
	 *
	 * @param providerModuleId the new provider module ID
	 */
	public void setProviderModuleId(final String providerModuleId) {
		this.providerModuleId = providerModuleId;
	}

	/**
	 * Sets the provider port ID.
	 *
	 * @param providerPortId the new provider port ID
	 */
	public void setProviderPortId(final String providerPortId) {
		this.providerPortId = providerPortId;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "OrientDBPortTuple [id=" + this.id + ", version=" + this.version + ", prosumerModuleId=" + this.prosumerModuleId + ", prosumerPortId=" + this.prosumerPortId + ", prosumerMaxConnections=" + this.prosumerMaxConnections + ", providerModuleId=" + this.providerModuleId + ", providerPortId=" + this.providerPortId + ", providerMaxConnections=" + this.providerMaxConnections + ", priority=" + this.priority + "]";
	}
}
