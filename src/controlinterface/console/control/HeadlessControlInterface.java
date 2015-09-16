package controlinterface.console.control;

import db.iface.ComponentConfigurationController;
import framework.control.ControlInterfaceConnector;
import framework.control.LogConnector;

/**
 * A simple headless (user) interface with autostart enabled.
 *
 * @author Stefan Werner
 */
public class HeadlessControlInterface extends ConsoleControlInterface {

	/**
	 * Instantiates a new headless control interface.
	 *
	 * @param connector the CI connector
	 * @param ciConfiguration the CI configuration
	 * @param logConnector the log connector
	 */
	public HeadlessControlInterface(final ControlInterfaceConnector connector, final ComponentConfigurationController ciConfiguration, final LogConnector logConnector) {
		super(connector, ciConfiguration, logConnector);
		this.headless = true;
	}
}
