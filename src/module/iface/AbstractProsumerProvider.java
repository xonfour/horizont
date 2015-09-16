package module.iface;

import db.iface.ComponentConfigurationController;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.control.ProviderConnector;

/**
 * The class AbstractProsumerProvider.
 *
 * @author Stefan Werner
 */
public abstract class AbstractProsumerProvider implements Prosumer, Provider {

	protected final ProsumerConnector prosumerConnector;
	protected final ProviderConnector providerConnector;
	protected final ComponentConfigurationController componentConfiguration;
	protected final LogConnector logConnector;

	/**
	 * Instantiates a new abstract prosumer provider.
	 *
	 * @param prosumerConnector the prosumer connector
	 * @param providerConnector the provider connector
	 * @param componentConfiguration the component configuration
	 * @param logConnector the log connector
	 */
	// Important Note: calling connector methods from constructor will cause an Exception, wait for initialize() call instead
	public AbstractProsumerProvider(final ProsumerConnector prosumerConnector, final ProviderConnector providerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		this.prosumerConnector = prosumerConnector;
		this.providerConnector = providerConnector;
		this.componentConfiguration = componentConfiguration;
		this.logConnector = logConnector;
	}
}
