package module.iface;

import db.iface.ComponentConfigurationController;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;

/**
 * The class AbstractProsumer.
 *
 * @author Stefan Werner
 */
public abstract class AbstractProsumer implements Prosumer {

	protected final ProsumerConnector prosumerConnector;
	protected final ComponentConfigurationController componentConfiguration;
	protected final LogConnector logConnector;

	/**
	 * Instantiates a new abstract prosumer.
	 *
	 * @param prosumerConnector the prosumer connector
	 * @param componentConfiguration the component configuration
	 * @param logConnector the log connector
	 */
	// Important Note: calling connector methods from constructor will cause an Exception, wait for initialize() call instead
	public AbstractProsumer(final ProsumerConnector prosumerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		this.prosumerConnector = prosumerConnector;
		this.componentConfiguration = componentConfiguration;
		this.logConnector = logConnector;
	}
}
