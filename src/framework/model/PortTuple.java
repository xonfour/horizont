package framework.model;

/**
 * Represents a connection between two ports. Used only inside the framework/broker.
 *
 * @author Stefan Werner
 */
public final class PortTuple {

	private long dataTransfered = 0;
	private long latestRefreshDate = 0;
	private final int priority;
	private final ProsumerPort prosumerPort;
	private final ProviderPort providerPort;

	/**
	 * Instantiates a new port tuple.
	 *
	 * @param prosumerPort the prosumer port
	 * @param providerPort the provider port
	 * @param priority the priority
	 */
	public PortTuple(final ProsumerPort prosumerPort, final ProviderPort providerPort, final int priority) {
		this.prosumerPort = prosumerPort;
		this.providerPort = providerPort;
		this.priority = priority;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof PortTuple)) {
			return false;
		}
		final PortTuple other = (PortTuple) obj;
		if (this.prosumerPort == null) {
			if (other.prosumerPort != null) {
				return false;
			}
		} else if (!this.prosumerPort.equals(other.prosumerPort)) {
			return false;
		}
		if (this.providerPort == null) {
			if (other.providerPort != null) {
				return false;
			}
		} else if (!this.providerPort.equals(other.providerPort)) {
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
	 * Gets the lastest refresh date.
	 *
	 * @return the latest refresh date.
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
	 * Gets the prosumer port.
	 *
	 * @return the prosumer port
	 */
	public ProsumerPort getProsumerPort() {
		return this.prosumerPort;
	}

	/**
	 * Gets the provider port.
	 *
	 * @return the provider port
	 */
	public ProviderPort getProviderPort() {
		return this.providerPort;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.prosumerPort == null) ? 0 : this.prosumerPort.hashCode());
		result = (prime * result) + ((this.providerPort == null) ? 0 : this.providerPort.hashCode());
		return result;
	}

	/**
	 * Sets the data transfered.
	 *
	 * @param dataTransfered the new data transfered
	 */
	public void setDataTransfered(final long dataTransfered) {
		this.dataTransfered = dataTransfered;
	}

	/**
	 * Sets the latest refresh date.
	 *
	 * @param latestRefreshDate the new latest refresh date
	 */
	public void setLatestRefreshDate(final long latestRefreshDate) {
		this.latestRefreshDate = latestRefreshDate;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "PortTuple [prosumerPort=" + this.prosumerPort + ", providerPort=" + this.providerPort + ", priority=" + this.priority + "]";
	}
}
