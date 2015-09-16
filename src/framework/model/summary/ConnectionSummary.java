package framework.model.summary;

/**
 * Summarizes a connection between two ports.
 *
 * @author Stefan Werner
 */
public final class ConnectionSummary implements Summary {

	private final long dataTransfered;
	private final boolean isActive;
	private final long latestRefreshDate;
	private final int priority;
	private final PortSummary prosumerPortSummary;
	private final PortSummary providerPortSummary;

	/**
	 * Instantiates a new connection summary.
	 *
	 * @param prosumerPortSummary the prosumer port summary
	 * @param providerPortSummary the provider port summary
	 */
	public ConnectionSummary(final PortSummary prosumerPortSummary, final PortSummary providerPortSummary) {
		this.prosumerPortSummary = prosumerPortSummary;
		this.providerPortSummary = providerPortSummary;
		this.isActive = true;
		this.dataTransfered = 0;
		this.priority = 0;
		this.latestRefreshDate = System.currentTimeMillis();
	}

	/**
	 * Instantiates a new connection summary.
	 *
	 * @param prosumerPortSummary the prosumer port summary
	 * @param providerPortSummary the provider port summary
	 * @param isActive the is active
	 * @param priority the priority
	 * @param dataTransfered the data transfered
	 * @param lastUpdate the last update
	 */
	public ConnectionSummary(final PortSummary prosumerPortSummary, final PortSummary providerPortSummary, final boolean isActive, final int priority, final long dataTransfered, final long lastUpdate) {
		this.prosumerPortSummary = prosumerPortSummary;
		this.providerPortSummary = providerPortSummary;
		this.isActive = isActive;
		this.dataTransfered = dataTransfered;
		this.priority = priority;
		this.latestRefreshDate = lastUpdate;
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
		if (!(obj instanceof ConnectionSummary)) {
			return false;
		}
		final ConnectionSummary other = (ConnectionSummary) obj;
		if (this.prosumerPortSummary == null) {
			if (other.prosumerPortSummary != null) {
				return false;
			}
		} else if (!this.prosumerPortSummary.equals(other.prosumerPortSummary)) {
			return false;
		}
		if (this.providerPortSummary == null) {
			if (other.providerPortSummary != null) {
				return false;
			}
		} else if (!this.providerPortSummary.equals(other.providerPortSummary)) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the amount of data transfered.
	 *
	 * @return the amount of data transfered
	 */
	public long getDataTransfered() {
		return this.dataTransfered;
	}

	/**
	 * Gets the late update.
	 *
	 * @return the late update
	 */
	public long getLatestRefreshDate() {
		return this.latestRefreshDate;
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
	 * Gets the prosumer port summary.
	 *
	 * @return the prosumer port summary
	 */
	public PortSummary getProsumerPortSummary() {
		return this.prosumerPortSummary;
	}

	/**
	 * Gets the provider port summary.
	 *
	 * @return the provider port summary
	 */
	public PortSummary getProviderPortSummary() {
		return this.providerPortSummary;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.prosumerPortSummary == null) ? 0 : this.prosumerPortSummary.hashCode());
		result = (prime * result) + ((this.providerPortSummary == null) ? 0 : this.providerPortSummary.hashCode());
		return result;
	}

	/**
	 * Checks if connection is active.
	 *
	 * @return the true, if active
	 */
	public boolean isActive() {
		return this.isActive;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "ConnectionSummary [prosumerPortSummary=" + this.prosumerPortSummary + ", providerPortSummary=" + this.providerPortSummary + ", isActive=" + this.isActive + ", dataTransfered=" + this.dataTransfered + ", priority=" + this.priority + "]";
	}
}
