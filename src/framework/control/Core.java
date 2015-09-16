package framework.control;

import i18n.control.SimpleLocalizationController;
import i18n.iface.LocalizationController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import db.iface.BaseConfigurationController;
import db.orientdb.control.OrientDBController;
import framework.constants.Constants;
import framework.constants.ControlInterfaceRight;
import framework.exception.DatabaseException;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.LogEventSourceType;
import framework.model.event.type.SystemStateType;
import framework.model.summary.BaseConfigurationSummary;

/**
 * The core class of the system containing the main method. Initializes and stops all other parts. The main method accepts some command line options, use
 * <code>--help</code> to get an overview.
 * <p>
 * <code>
 * Usage Options:
 *   -aa, --add-advanced-ui
 *      Add another instance of the advanced UI
 *      Default: false
 *   -as, --add-setup-wizard-ui
 *      Add another instance of the (simple) setup wizard UI
 *      Default: false
 *   -h, /h, --help
 *      Display this help/usage information
 *      Default: false
 *   -lc, --load-config
 *      JSON config file to load, overwriting any existing data
 *   -s, --storage-location
 *      Folder to use as storage location for internal database
 *      Default: /home/dust/FluentCloud
 * </code>
 * 
 * @author Stefan Werner
 */
public class Core {

	/**
	 * Clean up thread to run when the JVM shuts down.
	 */
	private class CleanUpThread extends Thread {

		/* (non-Javadoc)
		 *
		 * @see java.lang.Thread#run() */
		@Override
		public void run() {
			if ((Core.this.broker != null) && Core.this.broker.isRunning()) {
				Core.this.broker.shutdown();
			}
		}
	}

	private static final DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat(Constants.DEFAULT_DATE_FORMAT_PATTERN);

	private static Core core;

	/**
	 * Gets the default date format.
	 *
	 * @return the default date format
	 */
	public static DateFormat getDefaultDateFormat() {
		return Core.DEFAULT_DATE_FORMAT;
	}

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(final String[] args) {
		try {
			Core.core = new Core(args);
		} catch (final DatabaseException e) {
			e.printStackTrace();
			System.exit(1);
		}
		Core.core.startSystem();
	}

	@Parameter(names = { "-aa", "--add-advanced-ui" }, description = "Add another instance of the advanced UI")
	public boolean addAdvUi = false;
	@Parameter(names = { "-as", "--add-setup-wizard-ui" }, description = "Add another instance of the (simple) setup wizard UI")
	public boolean addSimpleUi = false;
	private final BaseConfigurationController baseConfigController;
	private final Broker broker;
	private final ComponentAuthorizationManager componentAuthorizationManager;
	private final ComponentInstanceManager componentInstanceManager;
	@Parameter(names = { "-lc", "--load-config" }, description = "JSON config file to load, overwriting any existing data")
	public String configLocation = null;
	private final ControlInterfaceActionHandler controlInterfaceActionHandler;
	private SystemStateType currentSystemState = SystemStateType.SYSTEM_INITIALIZING;
	private final OrientDBController dbController;
	@Parameter(names = { "-h", "/h", "--help" }, description = "Display this help/usage information", help = true)
	private boolean help;
	private final JCommander jCommander;
	private final LocalizationController localizationController;
	private final LogConnector logConnector;
	@Parameter(names = { "-s", "--storage-location" }, description = "Folder to use as storage location for internal database")
	public String storageLocation = System.getProperty("user.home") + File.separator + Constants.APP_NAME;

	/**
	 * Instantiates a new core.
	 *
	 * @param args the args of the main method call
	 * @throws DatabaseException if there is a problem with the database
	 */
	private Core(final String[] args) throws DatabaseException {
		this.jCommander = new JCommander(this, args);
		if (this.help) {
			this.jCommander.usage();
			System.exit(0);
		}
		final LogConnector dbLogConnector = new LogConnector(LogEventSourceType.DATABASE, Constants.COMPONENT_ID___DATABASE);
		final String storageLocation = getSystemDataStorageLocation();
		if (!checkStorageLocation(storageLocation)) {
			System.err.println("unable to access/write base system data location " + storageLocation + " -> exiting");
			System.exit(1);
		}
		this.dbController = new OrientDBController(dbLogConnector, storageLocation);
		this.baseConfigController = this.dbController.getConfig();
		this.componentAuthorizationManager = new ComponentAuthorizationManager();
		final LogConnector brokerLogConnector = new LogConnector(LogEventSourceType.FRAMEWORK, Constants.COMPONENT_ID___BROKER);
		this.broker = new Broker(this.baseConfigController, this.componentAuthorizationManager, brokerLogConnector);
		this.controlInterfaceActionHandler = new ControlInterfaceActionHandler(this, this.broker, this.componentAuthorizationManager);
		this.broker.setControlActionHandler(this.controlInterfaceActionHandler);
		dbLogConnector.setControlActionHandler(this.controlInterfaceActionHandler);
		brokerLogConnector.setControlActionHandler(this.controlInterfaceActionHandler);
		this.localizationController = new SimpleLocalizationController(Constants.I18N_INTERNAL_RESOURCE_LOCATION);
		this.logConnector = new LogConnector(this.controlInterfaceActionHandler, LogEventSourceType.FRAMEWORK, Constants.COMPONENT_ID___CORE);
		this.componentInstanceManager = new ComponentInstanceManager(new LogConnector(this.controlInterfaceActionHandler, LogEventSourceType.FRAMEWORK, Constants.COMPONENT_ID___CORE), this.broker, this.controlInterfaceActionHandler, this.componentAuthorizationManager, this.baseConfigController);
		this.broker.setComponentInstanceManager(this.componentInstanceManager);
		this.controlInterfaceActionHandler.setComponentInstanceManager(this.componentInstanceManager);
	}

	/**
	 * Adds an advanced user interface instance.
	 */
	private void addAdvancedUi() {
		this.componentInstanceManager.addNewControlInterface("controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface", ControlInterfaceRight.RIGHT___ALL);
	}

	/**
	 * Adds a simple user interface instance (setup wizard).
	 */
	private void addSimpleUi() {
		this.componentInstanceManager.addNewControlInterface("controlinterface.swinguiadvanced.control.SwingSimpleControlInterface", ControlInterfaceRight.RIGHT___ALL);
	}

	/**
	 * Announces the current system state to event listeners.
	 */
	private void announceCurrentSystemState() {
		this.controlInterfaceActionHandler.announceSystemState(this.currentSystemState);
	}

	/**
	 * Checks storage location. Makes sure it is writable and creates it if necessary.
	 *
	 * @param storageLocation the storage location
	 * @return true, if successful
	 */
	private boolean checkStorageLocation(final String storageLocation) {
		if ((storageLocation == null) || storageLocation.isEmpty()) {
			return false;
		}
		final Path localPath = Paths.get(storageLocation);
		if (Files.notExists(localPath)) {
			try {
				Files.createDirectories(localPath);
			} catch (final IOException e) {
				this.logConnector.log(e);
				return false;
			}
		}
		if (!Files.isDirectory(localPath) || !Files.isWritable(localPath)) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Exits the system.
	 *
	 * @param lastCIId the last ci id
	 * @param force the force
	 * @return true, if successful
	 */
	boolean exit(final String lastCIId, final boolean force) {
		if (((this.broker == null) || !this.broker.isRunning()) && ((this.componentInstanceManager.getActiveCIsCount() == 0) || ((this.componentInstanceManager.getActiveCIsCount() == 1) && this.componentInstanceManager.isActiveControlInterface(lastCIId)))) {
			System.exit(0);
		} else if (force) {
			System.exit(1);
		}
		return false;
	}

	/**
	 * Exports configuration party.
	 *
	 * @param out the output stream to write to
	 * @param exportPortConnections set to true to export port connections
	 * @param moduleIdsToExport the module IDs to export
	 * @param ciIdsToExport the CI IDs to export
	 * @return true, if successful
	 */
	boolean exportConfiguration(final OutputStream out, final boolean exportPortConnections, final Set<String> moduleIdsToExport, final Set<String> ciIdsToExport) {
		return this.baseConfigController.exportConfiguration(out, exportPortConnections, moduleIdsToExport, ciIdsToExport);
	}

	/**
	 * Gets the base configuration summary from a given input stream.
	 *
	 * @param in the input stream
	 * @return the base configuration
	 */
	BaseConfigurationSummary getBaseConfiguration(final InputStream in) {
		return this.baseConfigController.getBaseConfigurationSummary(in);
	}

	/**
	 * Gets the current base configuration summary from database.
	 *
	 * @return the current base configuration
	 */
	BaseConfigurationSummary getCurrentBaseConfiguration() {
		return this.baseConfigController.getCurrentBaseConfigurationSummary();
	}

	/**
	 * Gets the current system state.
	 *
	 * @return the current system state
	 */
	SystemStateType getCurrentSystemState() {
		return this.currentSystemState;
	}

	/**
	 * Gets a new localization connector.
	 *
	 * @param componentId the component id
	 * @return the new localization connector
	 */
	LocalizationConnector getNewLocalizationConnector(final String componentId) {
		return new LocalizationConnector(componentId, this.localizationController);
	}

	/**
	 * Gets the system data storage location.
	 *
	 * @return the system data storage location
	 */
	String getSystemDataStorageLocation() {
		// TODO: We should use an URI instead.
		// TODO: This should be done in a more sophisticated way:
		// WINDOWS -> c:\\users\\user\\appdata (?)
		// LINUX -> $XDG_CONFIG_HOME -> $HOME/.config/
		// MAC -> ~/Documents/ (?)
		return this.storageLocation;
	}

	/**
	 * Import configuration from input stream partly.
	 *
	 * @param in the input stream to read
	 * @param importPortConnections set to true to import port connections
	 * @param moduleIdsToImport the module IDs to import
	 * @param ciIdsToImport the CI IDs to import
	 * @return true, if successful
	 */
	boolean importConfiguration(final InputStream in, final boolean importPortConnections, final Set<String> moduleIdsToImport, final Set<String> ciIdsToImport) {
		return this.baseConfigController.importConfiguration(in, importPortConnections, moduleIdsToImport, ciIdsToImport);
	}

	/**
	 * Sets the current system state.
	 *
	 * @param type the new current system state
	 */
	void setCurrentSystemState(final SystemStateType type) {
		this.currentSystemState = type;
		announceCurrentSystemState();
	}

	/**
	 * Starts the broker.
	 *
	 * @return true, if successful
	 */
	boolean startBroker() {
		if (this.broker != null) {
			if (!this.broker.isRunning()) {
				this.currentSystemState = SystemStateType.BROKER_STARTING_UP;
				announceCurrentSystemState();
				final boolean result = this.broker.startup();
				if (result) {
					this.currentSystemState = SystemStateType.BROKER_RUNNING;
					announceCurrentSystemState();
				} else {
					this.currentSystemState = SystemStateType.SYSTEM_OR_BROKER_ERROR;
					announceCurrentSystemState();
				}
				return result;
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	/**
	 * Starts the complete system.
	 */
	private void startSystem() {
		Runtime.getRuntime().addShutdownHook(new CleanUpThread());
		if (this.configLocation != null) {
			FileInputStream in;
			try {
				in = new FileInputStream(this.configLocation);
				if (!this.baseConfigController.importCompleteConfiguration(in, true)) {
					System.err.println("unable to load config " + this.configLocation);
					System.exit(1);
				}
			} catch (final FileNotFoundException e) {
				System.err.println("unable to load config " + this.configLocation);
				System.exit(1);
			}
		}
		if (this.baseConfigController.getCIConfigurations().isEmpty()) {
			this.logConnector.log(LogEventLevelType.INFO, "initializing config \"" + Constants.DEFAULT_CONFIG_LOCATION + "\"...");
			final InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(Constants.DEFAULT_CONFIG_LOCATION);
			if (in != null) {
				this.baseConfigController.restoreConfiguration(in);
			}
		}
		final boolean ciResult = this.componentInstanceManager.startCIsFromDB();
		final boolean brokerResult = this.broker.initialize();
		if (ciResult && brokerResult) {
			this.currentSystemState = SystemStateType.BROKER_STOPPED_AND_READY;
		} else {
			this.currentSystemState = SystemStateType.SYSTEM_OR_BROKER_ERROR;
		}
		announceCurrentSystemState();
		if (this.addAdvUi) {
			addAdvancedUi();
		}
		if (this.addSimpleUi) {
			addSimpleUi();
		}
		if (this.baseConfigController.getCIConfigurations().isEmpty()) {
			addAdvancedUi();
		}
	}

	/**
	 * Stops the broker.
	 *
	 * @return true, if successful
	 */
	boolean stopBroker() {
		if (this.broker != null) {
			if (this.broker.isRunning()) {
				this.currentSystemState = SystemStateType.BROKER_SHUTTING_DOWN;
				announceCurrentSystemState();
				final boolean result = this.broker.shutdown();
				if (result) {
					this.currentSystemState = SystemStateType.BROKER_STOPPED_AND_READY;
					announceCurrentSystemState();
				} else {
					this.currentSystemState = SystemStateType.SYSTEM_OR_BROKER_ERROR;
					announceCurrentSystemState();
				}
				return result;
			} else {
				return true;
			}
		} else {
			return false;
		}
	}
}
