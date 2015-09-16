package controlinterface.swinguiadvanced.control;

import db.iface.ComponentConfigurationController;
import framework.control.ControlInterfaceConnector;
import framework.control.LogConnector;

/**
 * Calls {@link SwingAdvancedControlInterface} to display a simple user interface (setup wizard).
 *
 * @author Stefan Werner
 */
public class SwingSimpleControlInterface extends SwingAdvancedControlInterface {

	/**
	 * Instantiates a new swing simple control interface.
	 *
	 * @param connector CI connector
	 * @param ciConfiguration the CI configuration
	 * @param logConnector the log connector
	 */
	public SwingSimpleControlInterface(final ControlInterfaceConnector connector, final ComponentConfigurationController ciConfiguration, final LogConnector logConnector) {
		super(connector, ciConfiguration, logConnector);
	}

	@Override
	public void startup() {
		super.realStartup(true);
	}
}
