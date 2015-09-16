package controlinterface.iface;

import db.iface.ComponentConfigurationController;
import framework.control.ControlInterfaceConnector;
import framework.control.LogConnector;

/**
 * Extend this class to add a custom control interface.
 * <p>
 * IMPORTANT: You need to provide a corresponding constructor, see the one in this class.
 *
 * @author Stefan Werner
 */
public abstract class AbstractControlInterface implements ControlInterface {

	protected final ComponentConfigurationController ciConfiguration;
	protected final ControlInterfaceConnector connector;
	protected final LogConnector logConnector;

	/**
	 * Instantiates a new abstract control interface.
	 * <p>
	 * IMPORTANT: Calling connector methods from constructor will raise an Exception, wait for startup() call instead.
	 *
	 * @param connector the connector
	 * @param ciConfiguration the ci configuration
	 * @param logConnector the log connector
	 */
	public AbstractControlInterface(final ControlInterfaceConnector connector, final ComponentConfigurationController ciConfiguration, final LogConnector logConnector) {
		this.connector = connector;
		this.ciConfiguration = ciConfiguration;
		this.logConnector = logConnector;
	}
}
