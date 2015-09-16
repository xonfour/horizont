package controlinterface.swinguiadvanced.view;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.util.JConsole;
import db.iface.ComponentConfigurationController;
import framework.control.ControlInterfaceConnector;
import framework.control.LocalizationConnector;
import framework.control.LogConnector;

/**
 * Provides a BeanShell Window for scripting.
 * <p>
 * TODO: Mix of view and a bit of control stuff here?
 */
public class BeanShellWindow extends JFrame {

	private static final long serialVersionUID = 7240522287801316784L;

	/**
	 * Instantiates a new bean shell window.
	 *
	 * @param controlInterfaceConnector the control interface connector
	 * @param componentConfigurationController the component configuration controller
	 * @param logConnector the log connector
	 * @param localizationConnector the localization connector
	 */
	public BeanShellWindow(final ControlInterfaceConnector controlInterfaceConnector, final ComponentConfigurationController componentConfigurationController, final LogConnector logConnector, final LocalizationConnector localizationConnector) {
		setTitle(localizationConnector.getLocalizedString("BeanShell Console"));
		final JConsole console = new JConsole();
		getContentPane().add(console);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setSize(600, 400);
		setVisible(true);
		final Interpreter i = new Interpreter(console);
		try {
			i.set("bsh.system.shutdownOnExit", false);
			i.set("controlInterfaceConnector", controlInterfaceConnector);
			i.set("componentConfigurationController", componentConfigurationController);
			i.set("logConnector", logConnector);
			i.println("You may access all common interfaces using the following variables:\n");
			i.println("controlInterfaceConnector (control interface methods)");
			i.println("componentConfigurationController  (the database)");
			i.println("logConnector (logging)");
			i.println("\nEnter \"desktop()\" to open up the BeanShell Desktop which includes a class and method browser. For more information see \"http://www.beanshell.org/manual/\"\n");
		} catch (final EvalError e) {
			logConnector.log(e);
		}
		(new Thread(i)).start();
	}
}
