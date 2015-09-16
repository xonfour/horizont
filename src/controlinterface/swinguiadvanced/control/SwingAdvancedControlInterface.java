package controlinterface.swinguiadvanced.control;

import helper.PersistentConfigurationHelper;
import helper.view.SwingFileDialogHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.UIManager;

import com.seaglasslookandfeel.SeaGlassLookAndFeel;

import controlinterface.iface.AbstractControlInterface;
import controlinterface.iface.GeneralEventListener;
import controlinterface.swinguiadvanced.constants.SwingAdvancedConstants;
import controlinterface.swinguiadvanced.view.BeanShellWindow;
import controlinterface.swinguiadvanced.view.SwingAdvancedWindow;
import controlinterface.swinguiadvanced.view.dialog.GenericDialog;
import controlinterface.swinguiadvanced.view.panel.BaseConfigurationPanel;
import controlinterface.swinguiadvanced.view.panel.ConnectionGraphPanel;
import controlinterface.swinguiadvanced.view.panel.ConnectionInfoPanel;
import controlinterface.swinguiadvanced.view.panel.ConnectionPrioritySelectionPanel;
import controlinterface.swinguiadvanced.view.panel.GeneralInfoPanel;
import controlinterface.swinguiadvanced.view.panel.LogPanel;
import controlinterface.swinguiadvanced.view.panel.ModuleInfoPanel;
import controlinterface.swinguiadvanced.view.panel.PortInfoPanel;
import controlinterface.swinguiadvanced.view.panel.StringMapViewAndEditPanel;
import db.iface.ComponentConfigurationController;
import framework.constants.ControlInterfaceRight;
import framework.constants.GenericControlInterfaceCommandProperties;
import framework.control.ControlInterfaceConnector;
import framework.control.LocalizationConnector;
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
import framework.model.event.type.ConnectionEventType;
import framework.model.event.type.GeneralEventType;
import framework.model.event.type.ModuleUpdateEventType;
import framework.model.event.type.PortUpdateEventType;
import framework.model.event.type.SystemStateType;
import framework.model.summary.BaseConfigurationSummary;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.ModuleSummary;
import framework.model.summary.PortSummary;
import framework.model.summary.Summary;
import framework.model.type.PortType;

/**
 * Contains methods for an advaned user interface to almost every functionality of the system.
 * <p>
 * TODO: Check localization within dialog methods.
 *
 * @author Stefan Werner
 */
public class SwingAdvancedControlInterface extends AbstractControlInterface implements GeneralEventListener {

	private String cancelString;
	private String closeString;
	private final Map<String, String> componentIdsAndNames = new ConcurrentHashMap<String, String>();
	private ComponentManagementController componentManagementController;
	private ConnectionGraphPanel connectionGraphPanel;
	private ConnectionInfoPanel connectionInfoPanel;
	private ConnectionManagementController connectionManagementController;
	private Summary currentSummary = null;
	private SystemStateType currentSystemState;
	private String errorString;
	private GeneralInfoPanel generalInfoPanel;
	private boolean initialDataLoaded = false;
	private LocalizationConnector localizationConnector;
	private LogPanel logPanel;
	private ModuleInfoPanel moduleInfoPanel;
	private String okString;
	private int ownRights;
	private PortInfoPanel portInfoPanel;
	private Map<String, String> propertiesClipboard;
	private SwingSimpleControlWrapper simpleWrapper = null;
	private boolean started = false;
	private final ReentrantLock stateLock = new ReentrantLock();
	private boolean stopped = false;
	private final Map<String, Set<String>> supportedModuleCiCommandCache = new ConcurrentHashMap<String, Set<String>>();
	private SwingAdvancedWindow window = null;

	/**
	 * Instantiates a new swing advanced control interface.
	 *
	 * @param connector the connector
	 * @param ciConfiguration the ci configuration
	 * @param logConnector the log connector
	 */
	public SwingAdvancedControlInterface(final ControlInterfaceConnector connector, final ComponentConfigurationController ciConfiguration, final LogConnector logConnector) {
		super(connector, ciConfiguration, logConnector);
	}

	/**
	 * Adds a new module.
	 */
	public void addModule() {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return;
		}
		if ((this.componentManagementController != null) && ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0)) {
			this.componentManagementController.addNewModule(false);
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage control interfaces."), this.closeString);
		}
	}

	/**
	 * Connects two ports.
	 *
	 * @param srcPort the source port
	 * @param destPort the destination port
	 * @return true, if successful
	 */
	public boolean connect(final PortSummary srcPort, final PortSummary destPort) {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return false;
		}
		if (!mayConnect(srcPort, destPort)) {
			return false;
		}
		final ConnectionPrioritySelectionPanel panel = new ConnectionPrioritySelectionPanel(0, this.localizationConnector);
		final GenericDialog dialog = new GenericDialog(this.window, this.localizationConnector.getLocalizedString("Connection Priority"), this.okString, this.cancelString, this.localizationConnector.getLocalizedString("Select the priority for new connection:"), panel);
		if (dialog.showDialog() != 0) {
			return false;
		}
		final int priority = panel.getSelectedPriority();
		ConnectionSummary summary;
		if ((srcPort.getType() == PortType.PROSUMER) && (destPort.getType() == PortType.PROVIDER)) {
			summary = new ConnectionSummary(srcPort, destPort, true, priority, 0, System.currentTimeMillis());
		} else {
			summary = new ConnectionSummary(destPort, srcPort, true, priority, 0, System.currentTimeMillis());
		}
		try {
			if (this.connector.addConnection(summary)) {
				return true;
			} else {
				showErrorDialog("Unable to establish connection.");
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			showErrorDialog("Error while establishing connection", e);
		}
		return false;
	}

	/**
	 * Exits system.
	 *
	 * @param force the force
	 */
	public void exitSystem(final boolean force) {
		if (this.currentSystemState == SystemStateType.SYSTEM_INITIALIZING) {
			return;
		}
		if (this.window != null) {
			this.window.storeViewSettings();
		}
		try {
			this.connector.exit(force);
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			showErrorDialog("Error while exiting system", e);
		}
	}

	/**
	 * Exports the database. Opens dialog.
	 */
	public void exportDatabase() {
		FileOutputStream out = null;
		try {
			final BaseConfigurationSummary bcSummary = this.connector.getCurrentBaseConfiguration();
			final BaseConfigurationPanel panel = new BaseConfigurationPanel(bcSummary);
			final GenericDialog dialog = new GenericDialog(this.window, this.localizationConnector.getLocalizedString("Database Export"), this.okString, this.cancelString, this.localizationConnector.getLocalizedString("Select elements to export:"), panel);
			if (dialog.showDialog() != 0) {
				return;
			}
			final File file = SwingFileDialogHelper.showFileSaveDialog(System.getProperty("user.home") + File.separator + SwingAdvancedConstants.DEFAULT_EXPORT_DB_FILENAME);
			if (file != null) {
				out = new FileOutputStream(file);
				this.connector.exportConfiguration(out, panel.isConnectionsSelected(), panel.getSelectedModuleIds(), panel.getSelectedCIIds());
			}
		} catch (AuthorizationException | ControlInterfaceException | FileNotFoundException e) {
			this.logConnector.log(e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (final IOException e) {
					this.logConnector.log(e);
				}
			}
		}
	}

	/**
	 * Gets module properties from clipboard.
	 *
	 * @return the properties
	 */
	public Map<String, String> getPropertiesClipboard() {
		return this.propertiesClipboard;
	}

	/**
	 * Gets the supported control interface commands from module.
	 *
	 * @param summary the summary
	 * @param forceRefresh force refresh
	 * @return the supported control interface commands
	 */
	public Set<String> getSupportedCiCommands(final ModuleSummary summary, final boolean forceRefresh) {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return null;
		}
		if ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0) {
			try {
				final String moduleId = summary.getModuleId();
				Set<String> result = this.supportedModuleCiCommandCache.get(moduleId);
				if ((result == null) || forceRefresh) {
					result = this.connector.getSupportedControlInterfaceCommands(summary.getModuleId());
					this.supportedModuleCiCommandCache.put(moduleId, result);
				}
				return result;
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				showErrorDialog("Error while retrieving supported commands from module", e);
			}
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage control interfaces."), this.closeString);
		}
		return null;
	}

	/**
	 * Imports a database. Opens dialog.
	 */
	public void importDatabase() {
		FileInputStream in = null;
		try {
			final File file = SwingFileDialogHelper.showFileOpenDialog(System.getProperty("user.home") + File.separator + SwingAdvancedConstants.DEFAULT_EXPORT_DB_FILENAME);
			if (file == null) {
				return;
			}
			in = new FileInputStream(file);
			final BaseConfigurationSummary bcSummary = this.connector.getBaseConfiguration(in);
			if (bcSummary == null) {
				return;
				// TODO: Display error.
			}
			final BaseConfigurationPanel panel = new BaseConfigurationPanel(bcSummary);
			final GenericDialog dialog = new GenericDialog(this.window, this.localizationConnector.getLocalizedString("Database Export"), this.okString, this.cancelString, this.localizationConnector.getLocalizedString("Select elements to export:"), panel);
			if (dialog.showDialog() != 0) {
				return;
			}
			in.reset();
			this.connector.importConfiguration(in, panel.isConnectionsSelected(), panel.getSelectedModuleIds(), panel.getSelectedCIIds());
		} catch (AuthorizationException | ControlInterfaceException | IOException e) {
			this.logConnector.log(e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (final IOException e) {
					this.logConnector.log(e);
				}
			}
		}
	}

	/**
	 * Manages all connections. Opens dialog.
	 */
	public void manageAllConnections() {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return;
		}
		if ((this.connectionManagementController != null) && ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0)) {
			this.connectionManagementController.showConnectionManagementDialog();
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage module connections."), this.closeString);
		}
	}

	/**
	 * Manages control interfaces. Opens dialog.
	 */
	public void manageCIs() {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return;
		}
		if ((this.componentManagementController != null) && ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0)) {
			this.componentManagementController.showCIManagementDialog();
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage modules."), this.closeString);
		}
	}

	/**
	 * Manages modules. Opens dialog.
	 */
	public void manageModules() {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return;
		}
		if ((this.componentManagementController != null) && ((this.ownRights & ControlInterfaceRight.MANAGE_CIS) > 0)) {
			this.componentManagementController.showModuleManagementDialog();
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage control interfaces."), this.closeString);
		}
	}

	/**
	 * Checks if two ports may connect.
	 *
	 * @param srcPort the source port
	 * @param destPort the destination port
	 * @return true, if connectable
	 */
	public boolean mayConnect(final PortSummary srcPort, final PortSummary destPort) {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return false;
		}
		if ((srcPort == null) || (destPort == null) || (srcPort.getType() == destPort.getType())) {
			return false;
		}
		// As we allow multiple (in)active connections we don't need to do further checks
		return true;
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.GeneralEventListener#onGeneralEvent(framework.model.event.GeneralEvent) */
	@Override
	public void onGeneralEvent(final GeneralEvent event) {
		if (event instanceof ConnectionUpdateEvent) {
			final ConnectionUpdateEvent cuEvent = (ConnectionUpdateEvent) event;
			final ConnectionSummary summary = cuEvent.connectionSummary;
			if (summary.equals(this.currentSummary)) {
				if (cuEvent.type == ConnectionEventType.REMOVED) {
					selectedNothingInGraph();
				} else {
					this.connectionInfoPanel.updateData(summary, cuEvent.type);
				}
			}
			if (cuEvent.type == ConnectionEventType.REMOVED) {
				this.connectionGraphPanel.removeConnection(summary);
			} else {
				this.connectionGraphPanel.addOrUpdateConnection(summary, cuEvent.type);
			}
			this.logPanel.addConnectionUpdateEvent(cuEvent);
		} else if (event instanceof LogEvent) {
			final LogEvent lEvent = (LogEvent) event;
			this.logPanel.addLogEvent(lEvent);
		} else if (event instanceof ModuleActivityEvent) {
			final ModuleActivityEvent maEvent = (ModuleActivityEvent) event;
			this.logPanel.addModuleActionEvent(maEvent);
		} else if (event instanceof ModuleUpdateEvent) {
			final ModuleUpdateEvent muEvent = (ModuleUpdateEvent) event;
			final ModuleSummary summary = muEvent.moduleSummary;
			this.componentIdsAndNames.put(summary.getModuleId(), summary.getModuleName());
			if (summary.equals(this.currentSummary)) {
				if (muEvent.type == ModuleUpdateEventType.REMOVE) {
					selectedNothingInGraph();
				} else {
					this.moduleInfoPanel.updateData(summary);
				}
			}
			if (muEvent.type == ModuleUpdateEventType.REMOVE) {
				this.connectionGraphPanel.removeModule(summary);
			} else {
				this.connectionGraphPanel.addOrUpdateModule(summary);
			}
			this.logPanel.addModuleUpdateEvent(muEvent);
		} else if (event instanceof PortUpdateEvent) {
			final PortUpdateEvent puEvent = (PortUpdateEvent) event;
			final PortSummary summary = puEvent.portSummary;
			if (summary.equals(this.currentSummary)) {
				if (puEvent.type == PortUpdateEventType.REMOVE) {
					selectedNothingInGraph();
				} else {
					this.portInfoPanel.updateData(summary);
				}
			}
			if (puEvent.type == PortUpdateEventType.REMOVE) {
				this.connectionGraphPanel.removePort(summary);
			} else {
				this.connectionGraphPanel.addOrUpdatePort(summary);
			}
			this.logPanel.addPortUpdateEvent(puEvent);
		} else if (event instanceof SystemStateEvent) {
			final SystemStateEvent ssEvent = (SystemStateEvent) event;
			this.currentSystemState = ssEvent.systemStateType;
			processSystemState();
		}
	}

	/**
	 * Processes system state event.
	 */
	private void processSystemState() {
		this.stateLock.lock();
		if (!this.initialDataLoaded && (this.currentSystemState == SystemStateType.BROKER_STOPPED_AND_READY)) {
			refreshData();
			this.initialDataLoaded = true;
		}
		this.window.setCurrentStateText(this.localizationConnector.getLocalizedString(this.currentSystemState.name()));
		switch (this.currentSystemState) {
		case BROKER_RUNNING:
			this.window.setStartButtonEnabled(false);
			this.window.setStopButtonEnabled(true);
			this.window.setExitButtonEnabled(true);
			this.window.setMoreButtonAndMenuEnabled(true);
			break;
		case BROKER_SHUTTING_DOWN:
			this.window.setStartButtonEnabled(false);
			this.window.setStopButtonEnabled(false);
			this.window.setExitButtonEnabled(true);
			this.window.setMoreButtonAndMenuEnabled(false);
			break;
		case BROKER_STARTING_UP:
			this.window.setStartButtonEnabled(false);
			this.window.setStopButtonEnabled(false);
			this.window.setExitButtonEnabled(true);
			this.window.setMoreButtonAndMenuEnabled(false);
			break;
		case BROKER_STOPPED_AND_READY:
			this.window.setStartButtonEnabled(true);
			this.window.setStopButtonEnabled(false);
			this.window.setExitButtonEnabled(true);
			this.window.setMoreButtonAndMenuEnabled(true);
			break;
		case SYSTEM_EXITING:
			this.window.setStartButtonEnabled(false);
			this.window.setStopButtonEnabled(false);
			this.window.setExitButtonEnabled(true);
			this.window.setMoreButtonAndMenuEnabled(false);
			break;
		case SYSTEM_INITIALIZING:
			this.window.setStartButtonEnabled(false);
			this.window.setStopButtonEnabled(false);
			this.window.setExitButtonEnabled(false);
			this.window.setMoreButtonAndMenuEnabled(false);
			break;
		case SYSTEM_OR_BROKER_ERROR: // unknown system state -> allow all actions to get back to defines system state
			this.window.setStartButtonEnabled(true);
			this.window.setStopButtonEnabled(true);
			this.window.setExitButtonEnabled(true);
			this.window.setMoreButtonAndMenuEnabled(true);
			break;
		}
		this.stateLock.unlock();
	}

	/**
	 * Does the real startup of the UI.
	 *
	 * @param useSimpleUi set to true to use simple ui (setup wizard)
	 */
	@SuppressWarnings("unused")
	protected void realStartup(final boolean useSimpleUi) {
		this.stateLock.lock();
		if (this.started) {
			this.stateLock.unlock();
			return;
		}
		this.started = true;
		if (!System.getProperty("os.name").contains("Mac OS X") && SwingAdvancedConstants.USE_SEAGLASS_LNF) {
			try {
				UIManager.setLookAndFeel(new SeaGlassLookAndFeel());
			} catch (final Exception e) {
				this.logConnector.log(e);
			}
		}
		try {
			this.ownRights = this.connector.getOwnRights();
		} catch (final ControlInterfaceException e1) {
			this.ownRights = ControlInterfaceRight.RIGHT___NON;
		}
		try {
			this.localizationConnector = this.connector.getNewLocalizationConnector();
		} catch (final ControlInterfaceException e2) {
			this.localizationConnector = new LocalizationConnector();
		}
		if ((this.ownRights & (ControlInterfaceRight.MANAGE_CIS | ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS)) > 0) {
			this.componentManagementController = new ComponentManagementController(this.connector, this.logConnector, this.localizationConnector);
			this.connectionManagementController = new ConnectionManagementController(this.connector, this.logConnector, this.localizationConnector);
		}
		try {
			this.currentSystemState = this.connector.getCurrentSystemState();
		} catch (final ControlInterfaceException e1) {
			this.currentSystemState = SystemStateType.SYSTEM_OR_BROKER_ERROR;
		}
		try {
			this.localizationConnector = this.connector.getNewLocalizationConnector();
		} catch (final ControlInterfaceException e) {
			this.localizationConnector = new LocalizationConnector();
		}
		this.okString = this.localizationConnector.getLocalizedString("OK");
		this.closeString = this.localizationConnector.getLocalizedString("Close");
		this.errorString = this.localizationConnector.getLocalizedString("Error");
		this.cancelString = this.localizationConnector.getLocalizedString("Cancel");
		if (useSimpleUi) {
			this.simpleWrapper = new SwingSimpleControlWrapper(this, this.connector, this.logConnector, this.localizationConnector);
			this.simpleWrapper.start();
		} else {
			PersistentConfigurationHelper windowConfig;
			try {
				windowConfig = new PersistentConfigurationHelper(this.ciConfiguration, SwingAdvancedConstants.CONFIG___DOMAIN, SwingAdvancedConstants.CONFIG_PATH___MAIN_WINDOW);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
				windowConfig = new PersistentConfigurationHelper();
			}
			this.window = new SwingAdvancedWindow(this, windowConfig, this.localizationConnector);

			PersistentConfigurationHelper graphConfig;
			try {
				graphConfig = new PersistentConfigurationHelper(this.ciConfiguration, SwingAdvancedConstants.CONFIG___DOMAIN, SwingAdvancedConstants.CONFIG_PATH___CONNECTION_GRAPH_PANEL);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
				graphConfig = new PersistentConfigurationHelper();
			}
			this.connectionGraphPanel = new ConnectionGraphPanel(this, graphConfig);
			this.window.setTopLeftComponent(this.connectionGraphPanel);

			PersistentConfigurationHelper logConfig;
			try {
				logConfig = new PersistentConfigurationHelper(this.ciConfiguration, SwingAdvancedConstants.CONFIG___DOMAIN, SwingAdvancedConstants.CONFIG_PATH___LOG_PANEL);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
				logConfig = new PersistentConfigurationHelper();
			}
			this.logPanel = new LogPanel(this.componentIdsAndNames, logConfig, this.localizationConnector);
			this.window.setBottomComponent(this.logPanel);

			try {
				if (!this.connector.addGeneralEventListener(this, GeneralEventType.GENERAL_EVENT)) {
					showErrorDialog("Unable to add listener for system events.");
				}
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				showErrorDialog("Error while adding listener for system events", e);
			}
			refreshInfoAndSystemState();
			selectedNothingInGraph();
			this.window.setVisible(true);
		}
		this.stateLock.unlock();
	}

	/**
	 * Refreshes connection summary.
	 *
	 * @param summary the summary to refresh
	 * @return the refreshed summary
	 */
	public ConnectionSummary refreshConnection(ConnectionSummary summary) {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return null;
		}
		if ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0) {
			try {
				summary = this.connector.refreshConnection(summary);
				this.connectionGraphPanel.addOrUpdateConnection(summary);
				return summary;
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				showErrorDialog("Error while refreshing connection", e);
			}
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage control interfaces."), this.closeString);
		}
		return null;
	}

	/**
	 * Refreshes all data.
	 */
	private void refreshData() {
		try {
			final Set<ModuleSummary> summaries = this.connector.getActiveModules();
			for (final ModuleSummary summary : summaries) {
				this.componentIdsAndNames.put(summary.getModuleId(), summary.getModuleName());
				this.connectionGraphPanel.addOrUpdateModule(summary);
				for (final PortSummary portSummary : summary.getPorts()) {
					this.connectionGraphPanel.addOrUpdatePort(portSummary);
				}
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			showErrorDialog("Error while getting module set", e);
		}
		try {
			final Set<ConnectionSummary> summaries = this.connector.getConnections();
			for (final ConnectionSummary summary : summaries) {
				this.connectionGraphPanel.addOrUpdateConnection(summary);
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			showErrorDialog("Error while getting connection set", e);
		}
	}

	/**
	 * Refreshes this ci's info and system state.
	 */
	public void refreshInfoAndSystemState() {
		try {
			this.currentSystemState = this.connector.getCurrentSystemState();
			this.ownRights = this.connector.getOwnRights();
			processSystemState();
		} catch (final ControlInterfaceException e) {
			this.logConnector.log(e);
			showErrorDialog("Error while refreshing current system state", e);
		}
		try {
			final String infoText = this.ciConfiguration.getComponentName();
			if (infoText != null) {
				this.window.setInfoText(infoText + " (" + this.localizationConnector.getLocalizedString("Rights: ") + this.ownRights + ")");
			}
		} catch (final DatabaseException e) {
			this.logConnector.log(e);
		}
	}

	/**
	 * Removes a connection.
	 *
	 * @param summary the summary of the connection to remove
	 */
	public void removeConnection(final ConnectionSummary summary) {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return;
		}
		if ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0) {
			try {
				// TODO: Ask for confirmation.
				if (!this.connector.removeConnection(summary)) {
					showErrorDialog("Unable to remove connection.");
				}
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				showErrorDialog("Error while removing connection", e);
			}
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage control interfaces."), this.closeString);
		}
	}

	/**
	 * Removes a module.
	 *
	 * @param summary the summary of the module to remove
	 */
	public void removeModule(final ModuleSummary summary) {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return;
		}
		if ((this.componentManagementController != null) && ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0)) {
			this.componentManagementController.removeModule(this.window, summary);
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage control interfaces."), this.closeString);
		}
	}

	/**
	 * Renames a module.
	 *
	 * @param summary the summary of the module to rename
	 */
	public void renameModule(final ModuleSummary summary) {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return;
		}
		if ((this.componentManagementController != null) && ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0)) {
			this.componentManagementController.renameModule(this.window, summary);
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage control interfaces."), this.closeString);
		}
	}

	/**
	 * Changes contextual information depending on connection selected in graph view.
	 *
	 * @param summary the summary to display context information for.
	 */
	public void selectedConnectionInGraph(final ConnectionSummary summary) {
		if (summary == null) {
			return;
		}
		ConnectionSummary newSummary = refreshConnection(summary);
		if (newSummary == null) {
			newSummary = summary;
		}
		if (this.connectionInfoPanel == null) {
			this.connectionInfoPanel = new ConnectionInfoPanel(newSummary, this, this.localizationConnector);
		} else {
			this.connectionInfoPanel.updateData(newSummary);
		}
		this.currentSummary = newSummary;
		this.window.setTopRightComponent(this.connectionInfoPanel);
	}

	/**
	 * Changes contextual information depending on module selected in graph view.
	 *
	 * @param summary the summary to display context information for.
	 */
	public void selectedModuleInGraph(final ModuleSummary summary) {
		if (summary == null) {
			return;
		}
		if (this.moduleInfoPanel == null) {
			this.moduleInfoPanel = new ModuleInfoPanel(summary, this, this.localizationConnector);
		} else {
			this.moduleInfoPanel.updateData(summary);
		}
		this.currentSummary = summary;
		this.window.setTopRightComponent(this.moduleInfoPanel);
	}

	/**
	 * Changes contextual information to nothing if there is no selection.
	 */
	public void selectedNothingInGraph() {
		this.currentSummary = null;
		if (this.generalInfoPanel == null) {
			this.generalInfoPanel = new GeneralInfoPanel(this, this.localizationConnector);
		}
		this.window.setTopRightComponent(this.generalInfoPanel);
	}

	/**
	 * Changes contextual information depending on port selected in graph view.
	 *
	 * @param summary the summary to display context information for.
	 */
	public void selectedPortInGraph(final PortSummary summary) {
		if (summary == null) {
			return;
		}
		if (this.portInfoPanel == null) {
			this.portInfoPanel = new PortInfoPanel(summary, this, this.localizationConnector);
		} else {
			this.portInfoPanel.updateData(summary);
		}
		this.currentSummary = summary;
		this.window.setTopRightComponent(this.portInfoPanel);
	}

	/**
	 * Send control interface command to module.
	 *
	 * @param summary the summary of the module
	 * @param command the command to send
	 * @param addProperties the optional properties
	 */
	public void sendCiCommand(final ModuleSummary summary, final String command, final boolean addProperties) {
		if ((this.currentSystemState != SystemStateType.BROKER_RUNNING) && (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY)) {
			return;
		}
		if ((this.ownRights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) > 0) {
			try {
				final String moduleId = summary.getModuleId();
				Map<String, String> result = null;
				if (addProperties) {
					final StringMapViewAndEditPanel panel = new StringMapViewAndEditPanel(this, null, true, this.localizationConnector);
					final GenericDialog dialog = new GenericDialog(this.window, this.localizationConnector.getLocalizedString("Send Command"), this.okString, this.cancelString, this.localizationConnector.getLocalizedString("You may optionally add additional properties to command " + command + ":"), panel);
					if (dialog.showDialog() == 0) {
						final Map<String, String> properties = panel.getDataMap();
						result = this.connector.sendControlInterfaceCommand(moduleId, command, properties);
					} else {
						return;
					}
				} else {
					result = this.connector.sendControlInterfaceCommand(moduleId, command, null);
				}
				if (result != null) {
					final String okAnswer = result.get(GenericControlInterfaceCommandProperties.KEY___RESULT);
					if ((result.size() == 1) && (okAnswer != null) && okAnswer.equals(GenericControlInterfaceCommandProperties.VALUE___OK)) {
						// do not show dialog if we just receive an "ok" result
						return;
					}
					final StringMapViewAndEditPanel resultPanel = new StringMapViewAndEditPanel(this, result, false, this.localizationConnector);
					(new GenericDialog(null, this.localizationConnector.getLocalizedString("Result"), this.closeString, null, this.localizationConnector.getLocalizedString("Result(s) from command " + command + ":"), resultPanel)).showDialog();
				} else {
					showMessageDialog(this.localizationConnector.getLocalizedString("Result"), this.localizationConnector.getLocalizedString("Received NULL as answer."));
				}
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				showErrorDialog("Error while retrieving supported commands from module", e);
			}
		} else {
			GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString("Insufficient Rights"), this.localizationConnector.getLocalizedString("You don't have the right to manage control interfaces."), this.closeString);
		}
	}

	/**
	 * Copies module command properties to clipboard.
	 *
	 * @param propertiesClipboard the properties to copy
	 */
	public void setPropertiesClipboard(final Map<String, String> propertiesClipboard) {
		this.propertiesClipboard = new HashMap<String, String>(propertiesClipboard);
	}

	/**
	 * Showa an error dialog.
	 *
	 * @param msg the message to display
	 */
	public void showErrorDialog(final String msg) {
		GenericDialog.showGenericMessageDialog(this.errorString, this.localizationConnector.getLocalizedString(msg), this.closeString);
	}

	/**
	 * Shows an error dialog.
	 *
	 * @param msg the message to display
	 * @param e the Exception to display
	 */
	public void showErrorDialog(final String msg, final Exception e) {
		GenericDialog.showGenericMessageDialog(this.errorString, this.localizationConnector.getLocalizedString(msg) + ":\n" + e.getLocalizedMessage(), this.closeString);
	}

	/**
	 * Show a message dialog.
	 *
	 * @param title the title to display
	 * @param message the message to display
	 */
	public void showMessageDialog(final String title, final String message) {
		GenericDialog.showGenericMessageDialog(this.localizationConnector.getLocalizedString(title), this.localizationConnector.getLocalizedString(message), this.localizationConnector.getLocalizedString("Close"));
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.ControlInterface#shutdown() */
	@Override
	public void shutdown() {
		this.stateLock.lock();
		if (this.stopped) {
			this.stateLock.unlock();
			return;
		}
		this.stopped = true;
		if (this.simpleWrapper != null) {
			this.simpleWrapper.shutdown();
			this.simpleWrapper = null;
		} else if (this.window != null) {
			this.window.storeViewSettings();
			this.window.setVisible(false); // TODO: Move to dispose().
		}
		this.stateLock.unlock();
	}

	/**
	 * Starts a BeanShell instance.
	 */
	public void startBeanShell() {
		if ((this.currentSystemState == SystemStateType.SYSTEM_EXITING) || (this.currentSystemState == SystemStateType.SYSTEM_INITIALIZING)) {
			return;
		}
		new BeanShellWindow(this.connector, this.ciConfiguration, this.logConnector, this.localizationConnector);
	}

	/**
	 * Starts the broker.
	 */
	public void startBroker() {
		if (this.currentSystemState != SystemStateType.BROKER_STOPPED_AND_READY) {
			return;
		}
		try {
			this.connector.startBroker();
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			showErrorDialog("Error while starting broker", e);
		}
	}

	@Override
	public void startup() {
		realStartup(false);
	}

	/**
	 * Stops the broker.
	 */
	public void stopBroker() {
		if (this.currentSystemState != SystemStateType.BROKER_RUNNING) {
			return;
		}
		try {
			this.connector.stopBroker();
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			showErrorDialog("Error while stopping broker", e);
		}
	}
}
