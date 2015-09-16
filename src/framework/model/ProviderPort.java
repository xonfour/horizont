package framework.model;

/**
 * Represents a provider port.
 *
 * @author Stefan Werner
 */
public final class ProviderPort extends Port {

	/**
	 * Instantiates a new provider port.
	 *
	 * @param moduleId the module ID
	 * @param portId the port ID
	 * @param maxConnections the maximum number of concurrent active connections
	 */
	public ProviderPort(final String moduleId, final String portId, final int maxConnections) {
		super(moduleId, portId, maxConnections);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof ProviderPort)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}
}
