package controlinterface.console.control;

import framework.control.ControlInterfaceConnector;
import framework.control.LogConnector;
import framework.exception.AuthorizationException;
import framework.exception.ControlInterfaceException;
import framework.exception.DatabaseException;
import framework.model.event.ConnectionUpdateEvent;
import framework.model.event.GeneralEvent;
import framework.model.event.LogEvent;
import framework.model.event.ModuleActivityEvent;
import framework.model.event.ModuleUpdateEvent;
import framework.model.event.PortUpdateEvent;
import framework.model.event.SystemStateEvent;
import framework.model.event.type.GeneralEventType;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.SystemStateType;
import helper.PersistentConfigurationHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import controlinterface.iface.AbstractControlInterface;
import controlinterface.iface.GeneralEventListener;
import db.iface.ComponentConfigurationController;

/**
 * A simple user interface controllable from a console.
 *
 * @author Stefan Werner
 */
public class ConsoleControlInterface extends AbstractControlInterface implements GeneralEventListener {

	private static final boolean CONFIG_DEFAULT___AUTOSTART = false;
	private static final boolean CONFIG_DEFAULT___PRINT_EVENT = true;
	private static final String CONFIG_DOMAIN = "config";
	private static final String CONFIG_KEY___AUTOSTART = "autostart";
	private static final String CONFIG_KEY___PRINT_EVENT_CU = "print_event_cu";
	private static final String CONFIG_KEY___PRINT_EVENT_L = "print_event_le";
	private static final String CONFIG_KEY___PRINT_EVENT_MA = "print_event_ma";
	private static final String CONFIG_KEY___PRINT_EVENT_MU = "print_event_mu";
	private static final String CONFIG_KEY___PRINT_EVENT_PU = "print_event_pu";
	private static final String CONFIG_KEY___PRINT_EVENT_SS = "print_event_ss";
	private static final String[] CONFIG_PATH = { "config" };

	private PersistentConfigurationHelper configHelper;
	private final Runnable consoleListenerRunnable = new Runnable() {

		@Override
		public void run() {
			final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String s = "";
			while (!Thread.currentThread().isInterrupted()) {
				try {
					s = reader.readLine();
				} catch (final IOException e) {
					println("### Failed to read from console, retrying...");
					try {
						Thread.sleep(1000);
					} catch (final InterruptedException e1) {
						break;
					}
				}
				if (s.startsWith("?") || s.startsWith("h")) {
					printHelp();
				} else if (s.startsWith("a")) {
					toggleAutostart();
				} else if (s.startsWith("e") || s.startsWith("q")) {
					exitSystem();
					break;
				} else if (s.startsWith("i")) {
					startSystem();
				} else if (s.startsWith("o")) {
					stopSystem();
				} else if (s.startsWith("cu")) {
					toggleCU();
				} else if (s.startsWith("l")) {
					toggleL();
				} else if (s.startsWith("ma")) {
					toggleMA();
				} else if (s.startsWith("mu")) {
					toggleMU();
				} else if (s.startsWith("pu")) {
					togglePU();
				} else if (s.startsWith("ss")) {
					toggleSS();
				}
			}
		}
	};
	private Thread consoleListenerThread;
	private boolean firstStart = true;
	protected boolean headless = false;
	private boolean printCU = ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT;
	private boolean printL = ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT;
	private boolean printMA = ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT;
	private boolean printMU = ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT;
	private boolean printPU = ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT;
	private boolean printSS = ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT;
	private boolean systemRunning = false;

	/**
	 * Instantiates a new console control interface.
	 *
	 * @param connector the CI connector
	 * @param ciConfiguration the CI configuration
	 * @param logConnector the log connector
	 */
	public ConsoleControlInterface(final ControlInterfaceConnector connector, final ComponentConfigurationController ciConfiguration, final LogConnector logConnector) {
		super(connector, ciConfiguration, logConnector);
	}

	/**
	 * Checks if autostart is enabled.
	 */
	private void checkAutostart() {
		if (this.headless || (this.firstStart && this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___AUTOSTART, ConsoleControlInterface.CONFIG_DEFAULT___AUTOSTART))) {
			startSystem();
			this.firstStart = false;
		}
	}

	/**
	 * Exits system.
	 */
	private void exitSystem() {
		stopSystem();
		println("### Exiting...");
		try {
			this.connector.exit(true);
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.GeneralEventListener#onGeneralEvent(framework.model.event.GeneralEvent) */
	@Override
	public void onGeneralEvent(final GeneralEvent event) {
		if (event instanceof ConnectionUpdateEvent) {
			final ConnectionUpdateEvent cuEvent = (ConnectionUpdateEvent) event;
			if (this.printCU) {
				println(cuEvent.toString());
			}
		} else if (event instanceof LogEvent) {
			final LogEvent lEvent = (LogEvent) event;
			if (this.printL) {
				println(lEvent.toString());
			}
		} else if (event instanceof ModuleActivityEvent) {
			final ModuleActivityEvent maEvent = (ModuleActivityEvent) event;
			if (this.printMA) {
				println(maEvent.toString());
			}
		} else if (event instanceof ModuleUpdateEvent) {
			final ModuleUpdateEvent muEvent = (ModuleUpdateEvent) event;
			if (this.printMU) {
				println(muEvent.toString());
			}
		} else if (event instanceof PortUpdateEvent) {
			final PortUpdateEvent puEvent = (PortUpdateEvent) event;
			if (this.printPU) {
				println(puEvent.toString());
			}
		} else if (event instanceof SystemStateEvent) {
			final SystemStateEvent ssEvent = (SystemStateEvent) event;
			if (this.printSS) {
				println(ssEvent.toString());
			}
			if (ssEvent.systemStateType == SystemStateType.BROKER_STOPPED_AND_READY) {
				checkAutostart();
			}
		}
	}

	/**
	 * Prints the help to console.
	 */
	private void printHelp() {
		println("### ConsoleControlInterface ###");
		println("USAGE: Enter one command per line");
		println("CMD     Description");
		println("---------------------------------------");
		println("h/?     This help");
		println("a       Toggle autostart");
		println("e/q     Exit");
		println("i       Start broker");
		println("o       Stop broker");
		println("---------------------------------------");
		println("cu      Toggel print connection updates");
		println("l       Toggel print log messages");
		println("ma      Toggel print module activities");
		println("mu      Toggel print module updates");
		println("pu      Toggel print port updates");
		println("ss      Toggel print system states");
	}

	/**
	 * Prints a text line to console.
	 *
	 * @param msg the msg
	 */
	private void println(final String msg) {
		if (!this.headless) {
			System.out.println(msg);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.ControlInterface#shutdown() */
	@Override
	public void shutdown() {
		this.consoleListenerThread.interrupt();
	}

	/**
	 * Starts system.
	 */
	private synchronized void startSystem() {
		println("### Starting system...");
		if (!this.systemRunning) {
			try {
				this.connector.startBroker();
				this.systemRunning = true;
				println("### System started.");
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
			}
		}
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.ControlInterface#startup() */
	@Override
	public void startup() {
		try {
			if (!this.connector.addGeneralEventListener(this, GeneralEventType.GENERAL_EVENT)) {
				this.logConnector.log(LogEventLevelType.ERROR, "Unable to add listener for system events.");
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e, "Error while adding listener for system events");
		}
		try {
			this.configHelper = new PersistentConfigurationHelper(this.ciConfiguration, ConsoleControlInterface.CONFIG_DOMAIN, ConsoleControlInterface.CONFIG_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.configHelper = new PersistentConfigurationHelper();
		}
		this.printCU = this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_CU, ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT);
		this.printL = this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_L, ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT);
		this.printMA = this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_MA, ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT);
		this.printMU = this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_MU, ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT);
		this.printPU = this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_PU, ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT);
		this.printSS = this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_SS, ConsoleControlInterface.CONFIG_DEFAULT___PRINT_EVENT);
		this.consoleListenerThread = new Thread(this.consoleListenerRunnable);
		this.consoleListenerThread.start();
		printHelp();
	}

	/**
	 * Stops system.
	 */
	private synchronized void stopSystem() {
		println("### Stopping system...");
		if (this.systemRunning) {
			try {
				this.connector.stopBroker();
				this.systemRunning = false;
				println("### System stopped.");
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
			}
		}
	}

	/**
	 * Toggles autostart.
	 */
	private void toggleAutostart() {
		this.configHelper.updateBoolean(ConsoleControlInterface.CONFIG_KEY___AUTOSTART, !this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___AUTOSTART, ConsoleControlInterface.CONFIG_DEFAULT___AUTOSTART));
		println("### Autostart set to: " + this.configHelper.getBoolean(ConsoleControlInterface.CONFIG_KEY___AUTOSTART, ConsoleControlInterface.CONFIG_DEFAULT___AUTOSTART));
	}

	/**
	 * Toggles display of connection updates.
	 */
	private void toggleCU() {
		this.printCU = !this.printCU;
		this.configHelper.updateBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_CU, this.printCU);
		println("### Print connection updates set to: " + this.printCU);
	}

	/**
	 * Toggles display of log messages.
	 */
	private void toggleL() {
		this.printL = !this.printL;
		this.configHelper.updateBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_L, this.printL);
		println("### Print log messages set to: " + this.printL);
	}

	/**
	 * Toggles display of module activities.
	 */
	private void toggleMA() {
		this.printMA = !this.printMA;
		this.configHelper.updateBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_MA, this.printMA);
		println("### Print module activities set to: " + this.printMA);
	}

	/**
	 * Toggles display of connection updates.
	 */
	private void toggleMU() {
		this.printMU = !this.printMU;
		this.configHelper.updateBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_MU, this.printMU);
		println("### Print module updates set to: " + this.printMU);
	}

	/**
	 * Toggles display of port updates.
	 */
	private void togglePU() {
		this.printPU = !this.printPU;
		this.configHelper.updateBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_PU, this.printPU);
		println("### Print port updates set to: " + this.printPU);
	}

	/**
	 * Toggles display of system states.
	 */
	private void toggleSS() {
		this.printSS = !this.printSS;
		this.configHelper.updateBoolean(ConsoleControlInterface.CONFIG_KEY___PRINT_EVENT_SS, this.printSS);
		println("### Print system states set to: " + this.printSS);
	}
}
