package controlinterface.swinguiadvanced.control;

import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.ResourceHelper;
import helper.TextFormatHelper;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;

import module.pgpcrypto.control.PGPCryptoModule;
import controlinterface.iface.GeneralEventListener;
import controlinterface.swinguiadvanced.view.SetupWizardWindow;
import controlinterface.swinguiadvanced.view.panel.setupwizard.SetupWizardDropboxAuthToken;
import controlinterface.swinguiadvanced.view.panel.setupwizard.SetupWizardPrivateCryptoPanel;
import controlinterface.swinguiadvanced.view.panel.setupwizard.SetupWizardPublicCryptoPanel;
import controlinterface.swinguiadvanced.view.panel.setupwizard.SetupWizardSelectFolderPanel;
import controlinterface.swinguiadvanced.view.panel.setupwizard.SetupWizardStatusPanel;
import controlinterface.swinguiadvanced.view.panel.setupwizard.SetupWizardStepPanel;
import controlinterface.swinguiadvanced.view.panel.setupwizard.SetupWizardSummaryPanel;
import framework.constants.Constants;
import framework.constants.GenericControlInterfaceCommandProperties;
import framework.constants.GenericControlInterfaceCommands;
import framework.constants.GenericModuleCommandProperties;
import framework.constants.GenericModuleCommands;
import framework.control.ControlInterfaceConnector;
import framework.control.LocalizationConnector;
import framework.control.LogConnector;
import framework.exception.AuthorizationException;
import framework.exception.ControlInterfaceException;
import framework.model.event.ConnectionUpdateEvent;
import framework.model.event.GeneralEvent;
import framework.model.event.LogEvent;
import framework.model.event.ModuleActivityEvent;
import framework.model.event.ModuleUpdateEvent;
import framework.model.event.SystemStateEvent;
import framework.model.event.type.GeneralEventType;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.ModuleActivityEventType;
import framework.model.event.type.ModuleUpdateEventType;
import framework.model.event.type.SystemStateType;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.ModuleSummary;
import framework.model.summary.PortSummary;

/**
 * Provides methods and wrappers around system methods for presenting a simple user interface (setup wizard).
 * <p>
 * TODO:<br>
 * - This Class only uses a little bit of the advanced {@link SwingAdvancedControlInterface} Class. We should separate both.<br>
 * - Implement CI shutdown, for example remove tray icon (not a real problem as we don't expect users to remove this CI on purpose).
 *
 * @author Stefan Werner
 */
public class SwingSimpleControlWrapper implements GeneralEventListener {

	private static final String COMMAND_RESULT_KEY___OPTIONAL_FS_PROPS = "opt_fs_props";
	private static final String COMMAND_RESULT_KEY___PATH = "path";
	private static final String DROPBOX_FOLDER_DEFAULT = Constants.APP_NAME;
	private static final String DROPBOX_PROP_ACCESS_TOKEN_PREFIX = "accessToken=";
	private static final String ERROR_PREFIX = "Setup failed. I'm sorry for this. Please contact the developer. ";
	private static final String MOD_NAME___CRYPTO = "crypto";
	private static final String MOD_NAME___LOCAL = "local";
	private static final String MOD_NAME___REMOTE = "remote";
	private static final String MOD_NAME___SYNC = "sync";
	private static final String MOD_TYPE___CRYPTO = "module.pgpcrypto.control.PGPCryptoModule";
	private static final String MOD_TYPE___LOCAL = "module.niostorage.control.NIOStorageModule";
	private static final String MOD_TYPE___REMOTE = "module.niostorage.control.NIOStorageModule";
	private static final String MOD_TYPE___SYNC = "module.simplesync.control.SimpleSyncModule";
	private static final String PORT_ID___CRYPTO_DEC = "decrypted";
	private static final String PORT_ID___CRYPTO_ENC = "encrypted";
	private static final String PORT_ID___DROPBOX = SwingSimpleControlWrapper.PORT_ID___LOCAL;
	private static final String PORT_ID___LOCAL = "port";
	private static final String PORT_ID___SYNC1 = "storage1";
	private static final String PORT_ID___SYNC2 = "storage2";
	public static final String RESOURCE___ICON = "icons/controlinterface/swingadvanced/icon.png";

	private ConnectionSummary connectionSummaryCryptoSync = null;
	private ConnectionSummary connectionSummaryDropboxCrypto = null;
	private ConnectionSummary connectionSummaryLocalSync = null;
	private final ControlInterfaceConnector ciConnector;
	private final SwingAdvancedControlInterface advancedCIcontroller;
	private ModuleSummary cryptoModuleSummary = null;
	private String currentDropboxAuthToken = null;
	private ConfigValue currentDropboxAuthTokenConfigValue = null;
	private ConfigValue currentDropboxFolderConfigValue = null;
	private ConfigValue currentLocalFolderConfigValue = null;
	private ModuleSummary dropboxModuleSummary = null;
	private final List<String> errorMessageList = new ArrayList<String>();
	private final LocalizationConnector localizationConnector;
	private ModuleSummary localModuleSummary = null;
	private final LogConnector logConnector;
	private PortSummary portCryptoDec = null;
	private PortSummary portCryptoEnc = null;
	private PortSummary portDropbox = null;
	private PortSummary portLocal = null;
	private PortSummary portSync1 = null;
	private PortSummary portSync2 = null;
	private String privateKeyFingerprint = null;
	private SetupWizardDropboxAuthToken setupWizardDropboxAuthToken;
	private SetupWizardPrivateCryptoPanel setupWizardPrivateCryptoPanel;
	private SetupWizardPublicCryptoPanel setupWizardPublicCryptoPanel;
	private SetupWizardSelectFolderPanel setupWizardSelectFolderPanel;
	private SetupWizardStatusPanel setupWizardStatusPanel;
	private SetupWizardSummaryPanel setupWizardSummaryPanel;
	private boolean severeError = false;
	private int StatusSlideNum = 0;
	private ModuleSummary syncModuleSummary = null;
	private SetupWizardWindow window = null;

	/**
	 * Instantiates a new swing simple control wrapper.
	 *
	 * @param advancedCIcontroller the {@link SwingAdvancedControlInterface} controller
	 * @param ciConnector the CI connector
	 * @param logConnector the log connector
	 * @param localizationConnector the localization connector
	 */
	public SwingSimpleControlWrapper(final SwingAdvancedControlInterface advancedCIcontroller, final ControlInterfaceConnector ciConnector, final LogConnector logConnector, final LocalizationConnector localizationConnector) {
		this.advancedCIcontroller = advancedCIcontroller;
		this.ciConnector = ciConnector;
		this.logConnector = logConnector;
		this.localizationConnector = localizationConnector;
	}

	/**
	 * Backups own OpenPGP private key.
	 */
	public void backupOwnPrivateKey() {
		sendCryptoCommand(PGPCryptoModule.COMMAND___BACKUP_PRIVATE_KEYS);
	}

	/**
	 * Check current config (modules, connections etc.).
	 *
	 * @return true, if OK
	 */
	private boolean checkConfig() {
		this.errorMessageList.clear();
		boolean result = true;
		if ((this.currentLocalFolderConfigValue == null) || (this.currentLocalFolderConfigValue.getCurrentValueString() == null) || this.currentLocalFolderConfigValue.getCurrentValueString().isEmpty()) {
			this.errorMessageList.add("Local folder is unset, please select one.");
			result = false;
		} else {
			final Path localPath = Paths.get(this.currentLocalFolderConfigValue.getCurrentValueString()).normalize();
			if (!Files.isDirectory(localPath) || !Files.isWritable(localPath)) {
				this.errorMessageList.add("Local folder is unusable, please select a different one.");
				result = false;
			}
		}
		if ((this.privateKeyFingerprint == null) || this.privateKeyFingerprint.isEmpty()) {
			this.errorMessageList.add("Unable to find a usable private key, please generate a new or restore an existing one.");
			result = false;
		}
		if ((this.currentDropboxAuthToken == null) || this.currentDropboxAuthToken.isEmpty()) {
			this.errorMessageList.add("The Dropbox Authorization Token is unset, please generate it following the steps in this wizard.");
			result = false;
		}
		displayConfigErrors();
		return result;
	}

	/**
	 * Displays configuration errors.
	 */
	private void displayConfigErrors() {
		if (!this.errorMessageList.isEmpty()) {
			logErrors(this.errorMessageList);
			if (this.setupWizardSummaryPanel != null) {
				this.setupWizardSummaryPanel.setMessages(this.errorMessageList);
			}
			this.errorMessageList.clear();
		} else {
			if (this.setupWizardSummaryPanel != null) {
				this.setupWizardSummaryPanel.setMessages(null);
			}
		}
	}

	/**
	 * Exits system.
	 */
	public void exitSystem() {
		try {
			this.ciConnector.exit(false);
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			this.advancedCIcontroller.showErrorDialog("Error while exiting system", e);
		}
	}

	/**
	 * Exports own OpenPGP public key.
	 */
	public void exportOwnPublicKey() {
		sendCryptoCommand(PGPCryptoModule.COMMAND___EXPORT_OWN_PUBLIC_KEY);
	}

	/**
	 * Generates a new OpenPGP private key.
	 */
	public void generateNewPrivateKey() {
		sendCryptoCommand(PGPCryptoModule.COMMAND___GENERATE_KEY);
	}

	/**
	 * Initializes the configuration.
	 *
	 * @return true, if successful
	 */
	private boolean initConfig() {
		try {
			while (this.ciConnector.getCurrentSystemState() == SystemStateType.SYSTEM_INITIALIZING) {
				Thread.sleep(1000);
			}
		} catch (ControlInterfaceException | InterruptedException e1) {
			return false;
		}
		try {
			final Set<ModuleSummary> summaries = this.ciConnector.getActiveModules();
			boolean hasLocal = false;
			boolean hasCrypto = false;
			boolean hasDropbox = false;
			boolean hasSync = false;
			for (final ModuleSummary summary : summaries) {
				if (!hasCrypto && summary.getModuleName().equals(SwingSimpleControlWrapper.MOD_NAME___CRYPTO) && summary.getModuleType().equals(SwingSimpleControlWrapper.MOD_TYPE___CRYPTO)) {
					hasCrypto = true;
					this.cryptoModuleSummary = summary;
					for (final PortSummary portSummary : summary.getPorts()) {
						if (portSummary.getPortId().equals(SwingSimpleControlWrapper.PORT_ID___CRYPTO_DEC)) {
							this.portCryptoDec = portSummary;
						} else if (portSummary.getPortId().equals(SwingSimpleControlWrapper.PORT_ID___CRYPTO_ENC)) {
							this.portCryptoEnc = portSummary;
						}
					}
				} else if (!hasDropbox && summary.getModuleName().equals(SwingSimpleControlWrapper.MOD_NAME___REMOTE) && summary.getModuleType().equals(SwingSimpleControlWrapper.MOD_TYPE___REMOTE)) {
					hasDropbox = true;
					this.dropboxModuleSummary = summary;
					for (final PortSummary portSummary : summary.getPorts()) {
						if (portSummary.getPortId().equals(SwingSimpleControlWrapper.PORT_ID___DROPBOX)) {
							this.portDropbox = portSummary;
						}
					}
				} else if (!hasLocal && summary.getModuleName().equals(SwingSimpleControlWrapper.MOD_NAME___LOCAL) && summary.getModuleType().equals(SwingSimpleControlWrapper.MOD_TYPE___LOCAL)) {
					hasLocal = true;
					this.localModuleSummary = summary;
					for (final PortSummary portSummary : summary.getPorts()) {
						if (portSummary.getPortId().equals(SwingSimpleControlWrapper.PORT_ID___LOCAL)) {
							this.portLocal = portSummary;
						}
					}
				} else if (!hasSync && summary.getModuleName().equals(SwingSimpleControlWrapper.MOD_NAME___SYNC) && summary.getModuleType().equals(SwingSimpleControlWrapper.MOD_TYPE___SYNC)) {
					hasSync = true;
					this.syncModuleSummary = summary;
					for (final PortSummary portSummary : summary.getPorts()) {
						if (portSummary.getPortId().equals(SwingSimpleControlWrapper.PORT_ID___SYNC1)) {
							this.portSync1 = portSummary;
						} else if (portSummary.getPortId().equals(SwingSimpleControlWrapper.PORT_ID___SYNC2)) {
							this.portSync2 = portSummary;
						}
					}
				}
			}
			if (!(hasCrypto && hasDropbox && hasLocal && hasSync && (this.portCryptoDec != null) && (this.portCryptoEnc != null) && (this.portDropbox != null) && (this.portLocal != null) && (this.portSync1 != null) && (this.portSync2 != null))) {
				this.severeError = true;
				logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((modules))");
				return false;
			}
			this.connectionSummaryCryptoSync = new ConnectionSummary(this.portSync2, this.portCryptoDec);
			this.connectionSummaryDropboxCrypto = new ConnectionSummary(this.portCryptoEnc, this.portDropbox);
			this.connectionSummaryLocalSync = new ConnectionSummary(this.portSync1, this.portLocal);
			final Set<ConnectionSummary> connectionSummaries = this.ciConnector.getConnections();
			if (!connectionSummaries.contains(this.connectionSummaryCryptoSync)) {
				if (!this.ciConnector.addConnection(this.connectionSummaryCryptoSync)) {
					this.severeError = true;
					logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((connection c->s))");
					return false;
				}
			}
			if (!connectionSummaries.contains(this.connectionSummaryDropboxCrypto)) {
				if (!this.ciConnector.addConnection(this.connectionSummaryDropboxCrypto)) {
					this.severeError = true;
					logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((connection d->c))");
					return false;
				}
			}
			if (!connectionSummaries.contains(this.connectionSummaryLocalSync)) {
				if (!this.ciConnector.addConnection(this.connectionSummaryLocalSync)) {
					this.severeError = true;
					logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((connection l->s))");
					return false;
				}
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			this.severeError = true;
			logError(e.getLocalizedMessage());
			return false;
		}
		return true;
	}

	/**
	 * Initializes CI.
	 */
	public void initialize() {
		// no op
	}

	/**
	 * Initializes slides for setup wizard.
	 * <p>
	 * TODO: This should not be done in a controller class.
	 */
	private void initializeSlides() {
		String heading;
		String text;
		JComponent component;
		SetupWizardStepPanel stepsPanel;

		heading = "Welcome to FluentCloud!";

		text = "This is the initial setup wizard. Press 'Next' at the bottom to continue.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/welcome.png"));
		stepsPanel = new SetupWizardStepPanel(null, heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		// ///////////////////

		heading = "Introduction - Default Configuration";

		text = "This wizard will create the simple configuration you see below. Don't feel scared, just hit 'Next' and I will explain.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/simple_config_overview.png"));
		stepsPanel = new SetupWizardStepPanel(null, heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "1. We take a folder on your lokal hard drive.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/simple_config_overview1.png"));
		stepsPanel = new SetupWizardStepPanel(null, heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "2. We encrypt all of its content (files and folders) using PGP asymmetric encryption.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/simple_config_overview2.png"));
		stepsPanel = new SetupWizardStepPanel(null, heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "3. We sync this data with a folder called \"" + Constants.APP_NAME + "\" inside your Dropbox account.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/simple_config_overview3.png"));
		stepsPanel = new SetupWizardStepPanel(null, heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "4. You may share your data with other people you trust in a secure way. We come back to that in a minute.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/simple_config_overview4.png"));
		stepsPanel = new SetupWizardStepPanel(null, heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		// ///////////////////

		heading = "Local Sync Folder";

		text = "First you need to select a folder on your local system. The content of this folder will be kept synchronized with your Dropbox.";
		this.setupWizardSelectFolderPanel = new SetupWizardSelectFolderPanel(this.localizationConnector);
		stepsPanel = new SetupWizardStepPanel("2", heading, text, this.setupWizardSelectFolderPanel, true, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		// ///////////////////

		heading = "Data Security";

		text = "Remote data will be encrypted using PGP asymmetic encryption. Starting to sound complicated? OK, we keep it simple (I promise) but there are a few important things you need to know. The key we will create is actually made up of two parts:\n\nThe private part (which we just call 'private key') allows you to decrypt your data and is protected by a password only you should know. If you loose it or if you forget your password your encrypted data is lost. But don't panic, keys are managed internally and you will be given the opportunity to take a backup.\n\nThe public part (which we just call 'public key') can be given to anyone and is used to encrypt data and to check validity.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/private_public_key.png"));
		stepsPanel = new SetupWizardStepPanel("2", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "Setup your own key pair.";
		this.setupWizardPrivateCryptoPanel = new SetupWizardPrivateCryptoPanel(this, this.localizationConnector);
		stepsPanel = new SetupWizardStepPanel("2", heading, text, this.setupWizardPrivateCryptoPanel, true, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		// ///////////////////

		heading = "Dropbox";

		text = "In order to give this software access to your Dropbox account you have to generate an access token. Unfortunately this is still BETA software so you have to do this manually. But it is not that complicated and I show you how! First of all go to\n\nhttps://www.dropbox.com/developers/apps\n\n and login if neccessary.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/dropbox01.png"));
		stepsPanel = new SetupWizardStepPanel("3", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "Now we will register this software.\n\nClick on \"Create app\".";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/dropbox02.png"));
		stepsPanel = new SetupWizardStepPanel("3", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "We need to create a \"Dropbox API app\".\n\nIt should not be limited to its own folder (select \"No\") and it will access \"All file types\".\n\nLast but not least provide some unique name and click on \"Create app\".";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/dropbox03.png"));
		stepsPanel = new SetupWizardStepPanel("3", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "On the next page you'll find plenty of obscure information about your newly created \"app\".\n\nFind the section named \"OAuth2\" and click on \"Generate\" to generate an access token.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/dropbox04.png"));
		stepsPanel = new SetupWizardStepPanel("3", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "The button will change into a long sequence of numbers and letters.\n\nCopy and paste it completely into the field on the next page.\n\nAnd as the text under it already indicated: Don't share it to anyone - except this software!";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/dropbox05.png"));
		stepsPanel = new SetupWizardStepPanel("3", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "Please enter your newly generated access token.";
		this.setupWizardDropboxAuthToken = new SetupWizardDropboxAuthToken(this.localizationConnector);
		stepsPanel = new SetupWizardStepPanel("3", heading, text, this.setupWizardDropboxAuthToken, true, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "One last step and we are done here:\n\nPlease use the Dropbox web interface or the App to create a folder called \"" + Constants.APP_NAME + "\".";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/dropbox06.png"));
		stepsPanel = new SetupWizardStepPanel("3", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		// ///////////////////

		heading = "Sharing";

		text = "You have to exchange public keys with everybody you want to share data with. For that purpose you can export your own public key or import other's later on.\n\nWait a second! Mustn't I protect all my key stuff? No, this is how asymmetric encryption works: The PUBLIC key can be - well, you guess it - public.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/public_key_exchange.png"));
		stepsPanel = new SetupWizardStepPanel("4", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "By the way: Every key has an individual 'fingerprint' uniquely identifying that key. Whenever you exchange public keys with others it is important to compare these values to make sure the real owner of the key is the one you thing she or he is. As these fingerprints consist of long and unhandy numbers we make it easy for you and give you 6 words (see the EXAMPLE below). Just make sure they exactly are the same on both sides.";
		component = new JLabel(ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/setupwizzard/fingerprint.png"));
		stepsPanel = new SetupWizardStepPanel("4", heading, text, component, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		text = "Setup other's public keys to share data with them.";
		this.setupWizardPublicCryptoPanel = new SetupWizardPublicCryptoPanel(this, this.localizationConnector);
		stepsPanel = new SetupWizardStepPanel("4", heading, text, this.setupWizardPublicCryptoPanel, true, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		// ///////////////////

		heading = "Summary";

		text = "Let's do a final check.";
		this.setupWizardSummaryPanel = new SetupWizardSummaryPanel(this, this.localizationConnector);
		stepsPanel = new SetupWizardStepPanel(null, heading, text, this.setupWizardSummaryPanel, false, this.localizationConnector);
		this.window.addSlide(stepsPanel);

		// ///////////////////

		heading = "Overview";
		this.setupWizardStatusPanel = new SetupWizardStatusPanel(this, this.localizationConnector);
		this.StatusSlideNum = this.window.addSlide(this.setupWizardStatusPanel);
	}

	/**
	 * Initializes tray icon.
	 */
	private void initializeTrayIcon() {
		if (SystemTray.isSupported()) {
			final SystemTray sysTray = SystemTray.getSystemTray();
			TrayIcon trayIcon;
			final ImageIcon icon = ResourceHelper.getImageIconByName(SwingSimpleControlWrapper.RESOURCE___ICON);
			if (icon != null) {
				trayIcon = new TrayIcon(icon.getImage(), Constants.APP_NAME + " " + Constants.APP_VERSION);
				trayIcon.setImageAutoSize(true);
			} else {
				trayIcon = new TrayIcon((new ImageIcon()).getImage(), Constants.APP_NAME + " " + Constants.APP_VERSION);
			}
			trayIcon.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseClicked(final MouseEvent e) {
					if (SwingSimpleControlWrapper.this.window != null) {
						SwingSimpleControlWrapper.this.window.toggleVisibility();
					}
				}
			});
			try {
				sysTray.add(trayIcon);
			} catch (final Exception e) {
				this.logConnector.log(e);
			}
		}
	}

	/**
	 * Logs error.
	 *
	 * @param message the error message to log
	 */
	private void logError(final String message) {
		this.logConnector.log(LogEventLevelType.ERROR, message);
	}

	/**
	 * Logs errors.
	 *
	 * @param messageList the list of error messages to log
	 */
	private void logErrors(final List<String> messageList) {
		// TODO: Format list.
		this.logConnector.log(LogEventLevelType.ERROR, messageList.toString());
	}

	/**
	 * Manages other's OpenPGP public keys.
	 */
	public void manageOtherPublicKeys() {
		sendCryptoCommand(PGPCryptoModule.COMMAND___MANAGE_PUBLIC_KEYS);
	}

	/**
	 * Checks if the user may go to a next slide.
	 *
	 * @param nextSlideNum the next slide number
	 * @return true, if allowed
	 */
	public boolean mayGoTo(final int nextSlideNum) {
		if (nextSlideNum == this.StatusSlideNum) {
			return writeConfig() && readConfig() && checkConfig();
		} else {
			return true;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.GeneralEventListener#onGeneralEvent(framework.model.event.GeneralEvent) */
	@Override
	public void onGeneralEvent(final GeneralEvent event) {
		if (event instanceof ConnectionUpdateEvent) {
			final ConnectionUpdateEvent cuEvent = (ConnectionUpdateEvent) event;
			if ((this.connectionSummaryCryptoSync != null) && cuEvent.connectionSummary.equals(this.connectionSummaryCryptoSync)) {
				this.setupWizardStatusPanel.setActivity(cuEvent.type);
			}
		} else if (event instanceof ModuleActivityEvent) {
			final ModuleActivityEvent maEvent = (ModuleActivityEvent) event;
			if ((this.syncModuleSummary != null) && (maEvent.getSendingModuleId().equals(this.syncModuleSummary.getModuleId()))) {
				final Object destModId = maEvent.getProperties().get(ModuleActivityEventType.MOD_ACT_PROPKEY___DEST_MODULEID);
				final String[] path = (String[]) maEvent.getProperties().get(ModuleActivityEventType.MOD_ACT_PROPKEY___PATH);
				String activity = null;
				if ((destModId != null) && (destModId instanceof String) && ((String) destModId).equals(this.localModuleSummary.getModuleId())) {
					if (maEvent.getActivity().equals(ModuleActivityEventType.MOD_ACT___DELETE)) {
						activity = this.localizationConnector.getLocalizedString("deleting locally");
					}
				} else if ((destModId != null) && (destModId instanceof String) && ((String) destModId).equals(this.cryptoModuleSummary.getModuleId())) {
					if (path != null) {
						if (maEvent.getActivity().equals(ModuleActivityEventType.MOD_ACT___READ_DATA)) {
							activity = this.localizationConnector.getLocalizedString("reading from remote");
						} else if (maEvent.getActivity().equals(ModuleActivityEventType.MOD_ACT___WRITE_DATA)) {
							activity = this.localizationConnector.getLocalizedString("writing to remote");
						} else if (maEvent.getActivity().equals(ModuleActivityEventType.MOD_ACT___DELETE)) {
							activity = this.localizationConnector.getLocalizedString("deleting remotely");
						}
					}
				}
				if (activity == null) {
					return;
				}
				try {
					this.setupWizardStatusPanel.addInfoEntry(TextFormatHelper.getPathString(path) + "   (" + activity + ")");
				} catch (final ClassCastException e) {
					this.logConnector.log(e);
				}
			}
		} else if (event instanceof ModuleUpdateEvent) {
			final ModuleUpdateEvent muEvent = (ModuleUpdateEvent) event;
			if ((muEvent.type == ModuleUpdateEventType.FAIL_RESPOND) || (muEvent.type == ModuleUpdateEventType.FAIL_START) || (muEvent.type == ModuleUpdateEventType.FAIL_STOP)) {
				if (muEvent.moduleSummary.getModuleName().equals(SwingSimpleControlWrapper.MOD_NAME___LOCAL)) {
					this.advancedCIcontroller.showErrorDialog("Unable to read local folder. Please make sure it exists and is accessable. System will be stopped.");
					stopSystem();
				} else if (muEvent.moduleSummary.getModuleName().equals(SwingSimpleControlWrapper.MOD_NAME___REMOTE)) {
					this.advancedCIcontroller.showErrorDialog("Unable to read remote folder. Please check internet connection and make sure the folder exists and is accessable. System will be stopped.");
					stopSystem();
				}
			}
		} else if (event instanceof LogEvent) {
			final LogEvent lEvent = (LogEvent) event;
			if (lEvent.getLogLevel() == LogEventLevelType.WARNING) {
				this.advancedCIcontroller.showErrorDialog("Warning:\n" + lEvent.getMessage());
			} else if (lEvent.getLogLevel() == LogEventLevelType.ERROR) {
				this.advancedCIcontroller.showErrorDialog("Error:\n" + lEvent.getMessage());
			}
		} else if (event instanceof SystemStateEvent) {
			final SystemStateEvent ssEvent = (SystemStateEvent) event;
			switch (ssEvent.systemStateType) {
			case BROKER_STOPPED_AND_READY:
				this.window.setBackButtonState(true);
				this.window.setExitButtonState(true);
				break;
			case SYSTEM_OR_BROKER_ERROR:
				this.window.setBackButtonState(true);
				this.window.setExitButtonState(true);
				break;
			default:
				this.window.setBackButtonState(false);
				this.window.setExitButtonState(false);
				break;
			}
			this.setupWizardStatusPanel.setState(ssEvent.systemStateType);
		}
	}

	/**
	 * Reads the system's configuration.
	 *
	 * @return true, if successful
	 */
	public boolean readConfig() {
		if ((this.setupWizardDropboxAuthToken == null) || (this.setupWizardPrivateCryptoPanel == null) || (this.setupWizardPublicCryptoPanel == null) || (this.setupWizardSelectFolderPanel == null) || (this.setupWizardStatusPanel == null) || (this.setupWizardSummaryPanel == null)) {
			return false;
		}
		try {
			Map<String, String> result = this.ciConnector.sendControlInterfaceCommand(this.cryptoModuleSummary.getModuleId(), GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES, null);
			if (result == null) {
				this.severeError = true;
				logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((command c))");
				return false;
			}
			result = this.ciConnector.sendControlInterfaceCommand(this.cryptoModuleSummary.getModuleId(), PGPCryptoModule.COMMAND___GET_PRIVATE_KEY_FINGERPRINT, null);
			if (result != null) {
				final String fingerprint = result.get(GenericControlInterfaceCommandProperties.KEY___MESSAGE);
				if ((fingerprint != null) && !fingerprint.isEmpty()) {
					this.privateKeyFingerprint = fingerprint;
					this.setupWizardPrivateCryptoPanel.setKeyState(true);
					this.setupWizardPublicCryptoPanel.setKeyFingerprint(fingerprint);

				} else {
					this.privateKeyFingerprint = null;
					this.setupWizardPrivateCryptoPanel.setKeyState(false);
					this.setupWizardPublicCryptoPanel.setKeyFingerprint(null);
				}
			}

			result = this.ciConnector.sendControlInterfaceCommand(this.dropboxModuleSummary.getModuleId(), GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES, null);
			if (result == null) {
				this.severeError = true;
				logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((command d))");
				return false;
			}
			// TODO: The remote/Dropbox folder is currently ignored, we only use a static one.
			// final ConfigValue cvDropboxFolder = new ConfigValue(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___PATH,
			// result.get(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___PATH));
			this.currentDropboxFolderConfigValue = new ConfigValue(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___PATH);
			this.currentDropboxFolderConfigValue.setCurrentValueString(SwingSimpleControlWrapper.DROPBOX_FOLDER_DEFAULT);
			this.currentDropboxAuthTokenConfigValue = new ConfigValue(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___OPTIONAL_FS_PROPS, result.get(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___OPTIONAL_FS_PROPS));
			final String[] optFSPropsStrings = this.currentDropboxAuthTokenConfigValue.getCurrentValueStringArray();
			if ((optFSPropsStrings != null) && (optFSPropsStrings.length > 0)) {
				this.currentDropboxAuthToken = optFSPropsStrings[0].replace(SwingSimpleControlWrapper.DROPBOX_PROP_ACCESS_TOKEN_PREFIX, "");
				this.setupWizardDropboxAuthToken.setToken(this.currentDropboxAuthToken);
			} else {
				this.currentDropboxAuthToken = null;
				this.setupWizardDropboxAuthToken.setToken("");
			}

			result = this.ciConnector.sendControlInterfaceCommand(this.localModuleSummary.getModuleId(), GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES, null);
			if (result == null) {
				this.severeError = true;
				logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((command l))");
				return false;
			}
			this.currentLocalFolderConfigValue = new ConfigValue(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___PATH, result.get(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___PATH));
			if (this.currentLocalFolderConfigValue.getCurrentValueString() != null) {
				this.setupWizardSelectFolderPanel.setSelectedFolder(this.currentLocalFolderConfigValue.getCurrentValueString());
			}

			// we only check if sync modules answers the request, nothing more for now
			result = this.ciConnector.sendControlInterfaceCommand(this.syncModuleSummary.getModuleId(), GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES, null);
			if (result == null) {
				this.severeError = true;
				logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((command s))");
				return false;
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			this.severeError = true;
			logError(e.getLocalizedMessage());
			return false;
		}
		return true;
	}

	/**
	 * Restores private key.
	 */
	public void restorePrivateKey() {
		sendCryptoCommand(PGPCryptoModule.COMMAND___IMPORT_KEYS);
	}

	/**
	 * Sends command to crypto module.
	 *
	 * @param command the command to send
	 */
	private void sendCryptoCommand(final String command) {
		Map<String, String> results;
		try {
			results = this.ciConnector.sendControlInterfaceCommand(this.cryptoModuleSummary.getModuleId(), command, null);
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			return;
		}
		if (results != null) {
			final String result = results.get(GenericModuleCommandProperties.KEY___RESULT);
			if ((result != null) && result.equals(GenericModuleCommandProperties.VALUE___FAIL)) {
				// TODO: Display error?
			}
		}
	}

	/**
	 * Sends command to crypto module in order to mark a certain path as shared.
	 *
	 * @param pathName the path to share
	 */
	private void sendCryptoCommandSetShared(final String pathName) {
		if (pathName == null) {
			return;
		}
		final Map<String, String> props = new HashMap<String, String>();
		Map<String, String> results;
		try {
			props.put(PGPCryptoModule.COMMAND_PROPERTY_KEY___PATH, pathName);
			results = this.ciConnector.sendControlInterfaceCommand(this.cryptoModuleSummary.getModuleId(), GenericModuleCommands.SET_SHARED, props);
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			return;
		}
		if (results != null) {
			final String result = results.get(GenericModuleCommandProperties.KEY___RESULT);
			if ((result != null) && result.equals(GenericModuleCommandProperties.VALUE___FAIL)) {
				// TODO: Display error?
			}
		}
	}

	/**
	 * Shuts setup wizard down.
	 */
	public void shutdown() {
		if (this.window != null) {
			this.window.setVisible(false);
		}
	}

	/**
	 * Starts setup wizard.
	 */
	public void start() {
		this.window = new SetupWizardWindow(this, this.localizationConnector);
		initializeSlides();
		try {
			final SystemStateType state = this.ciConnector.getCurrentSystemState();
			this.setupWizardStatusPanel.setState(state);
			this.ciConnector.addGeneralEventListener(this, GeneralEventType.CONNECTION_UPDATE, GeneralEventType.MODULE_ACTIVITY, GeneralEventType.SYSTEM_STATE);
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
		}
		final Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				if (initConfig() && readConfig() && !SwingSimpleControlWrapper.this.severeError) {
					initializeTrayIcon();
					if (checkConfig()) {
						SwingSimpleControlWrapper.this.window.setVisible(false);
						SwingSimpleControlWrapper.this.window.setVisibleSlide(SwingSimpleControlWrapper.this.window.getSlideCount() - 1);
						// controller.startBroker();
					} else {
						SwingSimpleControlWrapper.this.window.setVisible(true);
						SwingSimpleControlWrapper.this.window.setVisibleSlide(0);
					}
				} else {
					// TODO: Here we should tell the user how to reset configuration or fall back to advanced UI.
					exitSystem();
				}
			}
		});
		t.start();
	}

	/**
	 * Starts system.
	 */
	public void startSystem() {
		try {
			if (checkConfig() && (this.ciConnector.getCurrentSystemState() == SystemStateType.BROKER_STOPPED_AND_READY)) {
				this.ciConnector.startBroker();
			}
		} catch (ControlInterfaceException | AuthorizationException e) {
			this.errorMessageList.add(e.getMessage());
		}
		displayConfigErrors();
	}

	/**
	 * Stops system.
	 */
	public void stopSystem() {
		try {
			if (this.ciConnector.getCurrentSystemState() == SystemStateType.BROKER_RUNNING) {
				this.ciConnector.stopBroker();
			}
		} catch (ControlInterfaceException | AuthorizationException e) {
			this.errorMessageList.add(e.getMessage());
		}
		displayConfigErrors();
	}

	/**
	 * Writes configuration (external: module, connections etc.).
	 *
	 * @return true, if successful
	 */
	private boolean writeConfig() {
		if ((this.setupWizardSelectFolderPanel == null) || (this.currentLocalFolderConfigValue == null) || (this.setupWizardDropboxAuthToken == null)) {
			return false;
		}
		// crypto
		sendCryptoCommandSetShared("/");
		// local folder
		final Map<String, String> properties = new HashMap<String, String>();
		this.currentLocalFolderConfigValue.setCurrentValueString(this.setupWizardSelectFolderPanel.getSelectedFolder());
		properties.put(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___PATH, this.currentLocalFolderConfigValue.toString());
		try {
			if (!CommandResultHelper.isOK(this.ciConnector.sendControlInterfaceCommand(this.localModuleSummary.getModuleId(), GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES, properties))) {
				this.severeError = true;
				logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((set_cfg l))");
				return false;
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			return false;
		}
		// dropbox
		properties.clear();
		this.currentDropboxAuthTokenConfigValue.setCurrentValueStringArray(SwingSimpleControlWrapper.DROPBOX_PROP_ACCESS_TOKEN_PREFIX + this.setupWizardDropboxAuthToken.getToken());
		properties.put(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___PATH, this.currentDropboxFolderConfigValue.toString());
		properties.put(SwingSimpleControlWrapper.COMMAND_RESULT_KEY___OPTIONAL_FS_PROPS, this.currentDropboxAuthTokenConfigValue.toString());
		try {
			if (!CommandResultHelper.isOK(this.ciConnector.sendControlInterfaceCommand(this.dropboxModuleSummary.getModuleId(), GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES, properties))) {
				this.severeError = true;
				logError(SwingSimpleControlWrapper.ERROR_PREFIX + "((set_cfg d))");
				return false;
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			return false;
		}
		return true;
	}
}
