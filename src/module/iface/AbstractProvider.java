package module.iface;

import db.iface.ComponentConfigurationController;
import framework.control.LogConnector;
import framework.control.ProviderConnector;

/**
 * The class AbstractProvider.
 *
 * @author Stefan Werner
 */
public abstract class AbstractProvider implements Provider {

	protected final ProviderConnector providerConnector;
	protected final ComponentConfigurationController componentConfiguration;
	protected final LogConnector logConnector;

	/**
	 * Instantiates a new abstract provider.
	 *
	 * @param providerConnector the provider connector
	 * @param componentConfiguration the component configuration
	 * @param logConnector the log connector
	 */
	// Important Note: calling connector methods from constructor will cause an Exception, wait for initialize() call instead
	public AbstractProvider(final ProviderConnector providerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		this.providerConnector = providerConnector;
		this.componentConfiguration = componentConfiguration;
		this.logConnector = logConnector;
	}
}
