package experimental.module.fuse.control;

import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.PersistentConfigurationHelper;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import module.iface.AbstractProsumer;

import com.google.common.collect.ImmutableSet;

import db.iface.ComponentConfigurationController;
import framework.constants.GenericControlInterfaceCommands;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.exception.BrokerException;
import framework.exception.DatabaseException;
import framework.exception.ModuleException;
import framework.model.Port;
import framework.model.ProsumerPort;
import framework.model.event.ProviderStateEvent;
import framework.model.event.type.LogEventLevelType;
import framework.model.type.ModuleStateType;

/**
 *
 * @author Stefan Werner
 */
public class FuseModule extends AbstractProsumer {

	public static final String CONFIG_DOMAIN = "config";
	public static final String CONFIG_KEY___MOUNT_POINT = "mount_point";
	public static final String[] CONFIG_PATH = { "config" };
	public static final String PORT_ID = "port";

	private ProsumerPort port;

	private boolean connected = false;
	private boolean started = false;
	private boolean mounted = false;
	private boolean providerReady = false;
	private PersistentConfigurationHelper config;
	private String mountPoint = null;
	private FuseConnector fuseConnector;
	private String fsName = null;
	private boolean readOnly = false;

	/**
	 * @param prosumerConnector
	 * @param componentConfiguration
	 * @param logConnector
	 */
	public FuseModule(final ProsumerConnector prosumerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(prosumerConnector, componentConfiguration, logConnector);
	}

	private synchronized void checkState(final boolean isEvent) {
		if (this.connected && this.started && this.providerReady && (this.mountPoint != null) && !this.mountPoint.isEmpty() && !this.mounted) {
			this.fuseConnector = new FuseConnector(this.port, this.prosumerConnector, this.logConnector, this.fsName, this.readOnly);
			try {
				this.logConnector.log(LogEventLevelType.DEBUG, "mounting fuse at " + this.mountPoint);
				this.fuseConnector.mount(new File(this.mountPoint), false);
				this.mounted = true;
				this.fuseConnector.setConnected(true);
				this.logConnector.log(LogEventLevelType.INFO, "FUSE successfully mounted");
			} catch (final Exception e) {
				this.logConnector.log(e);
			}
		} else if ((!this.connected || !this.started || !this.providerReady || (this.mountPoint == null) || this.mountPoint.isEmpty()) && this.mounted) {
			try {
				this.logConnector.log(LogEventLevelType.DEBUG, "unmounting fuse");
				this.fuseConnector.unmount();
				this.fuseConnector.destroy();
				this.mounted = false;
				this.logConnector.log(LogEventLevelType.INFO, "FUSE successfully unmounted");
			} catch (final Exception e) {
				this.logConnector.log(e);
			}
		} else if (this.started && this.connected && !this.providerReady && !isEvent) {
			try {
				this.prosumerConnector.requestConnectedProviderStatus(this.port);
			} catch (BrokerException | ModuleException e) {
				this.logConnector.log(e);
			}
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#enterShutdown() */
	@Override
	public void enterShutdown() {
		this.started = false;
		checkState(false);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#enterStartup() */
	@Override
	public void enterStartup() {
		// no op
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		// no op
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		this.started = true;
		checkState(false);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return ImmutableSet.copyOf(GenericControlInterfaceCommands.DEFAULT_SUPPORT___CONFIG);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedModuleCommands(framework.model.Port, java.lang.String[]) */
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
			this.config = new PersistentConfigurationHelper(this.componentConfiguration, FuseModule.CONFIG_DOMAIN, FuseModule.CONFIG_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			this.config = new PersistentConfigurationHelper();
		}
		this.mountPoint = this.config.getString(FuseModule.CONFIG_KEY___MOUNT_POINT, null);
		try {
			this.fsName = this.componentConfiguration.getComponentName();
		} catch (final DatabaseException e1) {
			this.logConnector.log(e1);
		}
		if ((this.fsName == null) || this.fsName.isEmpty()) {
			this.fsName = "unnamed";
		}
		try {
			this.port = this.prosumerConnector.registerProsumerPort(this, FuseModule.PORT_ID, 1);
		} catch (final BrokerException e) {
			this.logConnector.log(e);
			this.port = null;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return true; // TODO wird das eigentlich benutzt???
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return null;
		}
		if (command.equals(GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES)) {
			final ConfigValue configValue = new ConfigValue(FuseModule.CONFIG_KEY___MOUNT_POINT);
			configValue.setCurrentValueString(this.mountPoint != null ? this.mountPoint : "");
			configValue.setDescriptionString("The mount point where to mount the FUSE filesystem.");
			final Map<String, String> result = CommandResultHelper.getDefaultResultOk(FuseModule.CONFIG_KEY___MOUNT_POINT, configValue.toString());
			return result;
		} else if (command.equals(GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES) && (properties != null)) {
			final String newConfigValue = properties.get(FuseModule.CONFIG_KEY___MOUNT_POINT);
			if ((newConfigValue != null) && !newConfigValue.isEmpty()) {
				final ConfigValue configValue = new ConfigValue(FuseModule.CONFIG_KEY___MOUNT_POINT, newConfigValue);
				if (!configValue.isValid() || !configValue.isString()) {
					return CommandResultHelper.getDefaultResultFail();
				} else {
					final String newMountPoint = configValue.getCurrentValueString();
					if (newMountPoint.equals(this.mountPoint)) {
						return CommandResultHelper.getDefaultResultOk();
					} else {
						if (this.config.updateString(FuseModule.CONFIG_KEY___MOUNT_POINT, newMountPoint)) {
							this.mountPoint = null;
							checkState(false);
							this.mountPoint = newMountPoint;
							checkState(false);
							return CommandResultHelper.getDefaultResultOk(FuseModule.CONFIG_KEY___MOUNT_POINT, this.mountPoint);
						}
					}
				}
			}
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onModuleCommand(framework.model.Port, java.lang.String, java.lang.String[], java.util.Map) */
	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onPortConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		this.connected = true;
		checkState(false);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onPortDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		this.connected = false;
		checkState(false);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Prosumer#onProviderStateEvent(framework.model.Port, framework.model.event.ProviderStateEvent) */
	@Override
	public synchronized void onProviderStateEvent(final Port port, final ProviderStateEvent event) {
		this.readOnly = (event.state & ModuleStateType.READONLY) > 0;
		if (this.fuseConnector != null) {
			this.fuseConnector.setReadOnly(this.readOnly);
		}
		if ((event.state & ModuleStateType.READY) > 0) {
			this.providerReady = true;
		} else {
			this.providerReady = false;
		}
		checkState(true);
	}
}
