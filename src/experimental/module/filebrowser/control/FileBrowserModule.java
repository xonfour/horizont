package experimental.module.filebrowser.control;

import helper.CommandResultHelper;
import helper.PersistentConfigurationHelper;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import module.iface.AbstractProsumer;
import db.iface.ComponentConfigurationController;
import experimental.module.filebrowser.view.FileBrowser;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.exception.BrokerException;
import framework.exception.DatabaseException;
import framework.exception.ModuleException;
import framework.model.Port;
import framework.model.ProsumerPort;
import framework.model.event.ProviderStateEvent;
import framework.model.type.ModuleStateType;

/**
 *
 * @author Stefan Werner
 */
public class FileBrowserModule extends AbstractProsumer {

	private static final String[] DB___CONFIG_DATA_PATH = { "config_data" };
	private static final String DB___DOMAIN___CONFIG = "config";
	private static final String CI_COMMAND___SHOW_FS_WINDOW = "show_filebrowser_window";
	private static final String CI_COMMAND___DISPOSE_FS_WINDOW = "show_filebrowser_window";

	private ProsumerPort port;
	private boolean started = false;
	private boolean connected = false;
	private boolean providerReady = false;
	private FileBrowser fileBrowser = null;
	private PersistentConfigurationHelper configHelper;

	/**
	 * @param prosumerConnector
	 * @param componentConfiguration
	 * @param logConnector
	 */
	public FileBrowserModule(final ProsumerConnector prosumerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(prosumerConnector, componentConfiguration, logConnector);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#enterShutdown() */
	@Override
	public void enterShutdown() {
		this.started = false;
		manageState();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#enterStartup() */
	@Override
	public void enterStartup() {
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		this.started = true;
		if (this.connected && !this.providerReady) {
			try {
				this.prosumerConnector.requestConnectedProviderStatus(this.port);
			} catch (BrokerException | ModuleException e) {
				this.logConnector.log(e);
			}
		} else {
			manageState();
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return Collections.singleton(FileBrowserModule.CI_COMMAND___SHOW_FS_WINDOW);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedModuleSignals(framework.model.Port, java.lang.String[]) */
	@Override
	public Set<String> getSupportedModuleCommands(final Port port, final String[] path) {
		return Collections.emptySet();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		try {
			this.port = this.prosumerConnector.registerProsumerPort(this, "storage", 1);
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
		try {
			this.configHelper = new PersistentConfigurationHelper(this.componentConfiguration, FileBrowserModule.DB___DOMAIN___CONFIG, FileBrowserModule.DB___CONFIG_DATA_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			this.configHelper = new PersistentConfigurationHelper();
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return true;
	}

	private synchronized void manageState() {
		if (this.started && this.connected && this.providerReady && (this.fileBrowser == null)) {
			this.fileBrowser = new FileBrowser(this, this.port, this.prosumerConnector, this.logConnector, this.prosumerConnector.getNewLocalizationConnector());
			this.fileBrowser.setVisible(true);
		} else if (!(this.started && this.connected && this.providerReady) && (this.fileBrowser != null)) {
			this.fileBrowser.close();
			setFileBrowserClosed();
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		if (command.equals(FileBrowserModule.CI_COMMAND___SHOW_FS_WINDOW)) {
			manageState();
			return CommandResultHelper.getDefaultResultOk();
		} else if (command.equals(FileBrowserModule.CI_COMMAND___DISPOSE_FS_WINDOW)) {
			this.fileBrowser.close();
			setFileBrowserClosed();
			return CommandResultHelper.getDefaultResultOk();
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onModuleSignal(framework.model.Port, java.lang.String, java.lang.String[], java.util.Map) */
	@Override
	public Map<String, String> onModuleCommand(final Port port, final String signal, final String[] path, final Map<String, String> properties) {
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onPortConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		this.connected = true;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onPortDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		this.connected = false;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Prosumer#onProviderStateEvent(framework.model.Port, framework.model.event.ProviderStateEvent) */
	@Override
	public void onProviderStateEvent(final Port port, final ProviderStateEvent event) {
		this.providerReady = (event.state & ModuleStateType.READY) > 0;
		manageState();
	}

	public void setFileBrowserClosed() {
		if (this.fileBrowser != null) {
			this.fileBrowser = null;
		}
	}
}
