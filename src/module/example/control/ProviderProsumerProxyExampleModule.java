package module.example.control;

import framework.constants.GenericControlInterfaceCommands;
import framework.constants.ModuleRight;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.control.ProviderConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.DatabaseException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.Port;
import framework.model.ProsumerPort;
import framework.model.ProviderPort;
import framework.model.event.DataElementEvent;
import framework.model.event.ProviderStateEvent;
import framework.model.event.type.LogEventLevelType;
import framework.model.type.DataElementType;
import framework.model.type.ModuleStateType;
import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.PersistentConfigurationHelper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import module.iface.AbstractProsumerProvider;
import module.iface.DataElementEventListener;
import module.iface.ErrorCode;

import com.google.common.collect.ImmutableSet;

import db.iface.ComponentConfigurationController;

/**
 * This is a simple proxy module that logs read and write access dates. It either logs the first or the last access depending on updateExistingElements. This
 * configuration value is stored in the database and can be read/modified by corresponding control interface commands. The dates can be queried by other modules
 * and/or component interfaces. It also does some basic state checks and comes with a simple live cycle and own rights management.
 * <p>
 * IMPORTANT: This module is not really meant to be used in a production environment. Instead it can be seen as a base for module developers. Because of this it
 * tries to be VERY detailed and accurate to provide best practice usage examples for all common tasks. You just want to write a Prosumer? -> Extend
 * AbstractProsumer, change (uncomment corresponding) constructor and remove PROVIDER METHODS section and ProviderPort stuff. You just want to write a Provider?
 * -> Extend AbstractProvider, change (uncomment corresponding) constructor and remove DataElementEventListener, PROSUMER METHODS section and ProsumerPort
 * stuff.
 */
public class ProviderProsumerProxyExampleModule extends AbstractProsumerProvider implements DataElementEventListener {

	// DATABASE STUFF

	// database elements tree domain for configuration storage (we don't outbid the database with some basic configuration values but it keeps things simple)
	private static final String DB___DOMAIN___CONFIG = "config";

	// where to store configuration data in the database element tree
	private static final String[] DB___CONFIG_DATA_PATH = { "config_data" };

	// where to store log data in the database element tree
	private static final String DB___DOMAIN___ACCESS_LOG = "access_log";

	// CONFIG STUFF

	// a handy little tool to ease saving and loading configuration values to/from database
	private PersistentConfigurationHelper configHelper;

	// configuration value: should we update elements already logged (true = last access date, false = first access date)
	private boolean updateExistingElements = true;

	// property key for updateExistingElements value in configuration (and commands)
	private static final String CONFIG_PROPERTY_KEY___UPDATE_EXISTING_ELEMENT_DATA = "update_existing_element_data";

	// COMMAND STUFF

	// module / control interface command
	// when used as a control interface command, properties have to contain valid PROPERTY_KEY___PATH property
	private static final String COMMAND___GET_ACCESS_DATE = "get_access_date";

	// when a control interface wants to get access date this property has to contain a String array ConfigValue
	public static final String PROPERTY_KEY___PATH = "path";

	// this property holds the access date of the requested path element
	public static final String PROPERTY_KEY___ACCESS_DATE = "access_date";

	// MODULE COMMUNICATION STUFF

	// id (name) for ProsumerPort
	private static final String PORT_ID___PROSUMER = "to_provider";

	// id (name) for ProviderPort
	private static final String PORT_ID___PROVIDER = "to_prosumer";

	// the ProsumerPort we need for communication/authentication within the ProsumerConnector
	private ProsumerPort prosumerPort;

	// the ProviderPort we need for communication/authentication within the ProviderConnector
	private ProviderPort providerPort;

	// stores the connection state of the providerPort
	private boolean prosumerPortConnected = false;

	// stores the connection state of the prosumerPort
	private boolean providerPortConnected = false;

	// stores the readiness state of the connected Provider (if any)
	private boolean connectedProviderReady = false;

	// stores the full state of the connected Provider (if any)
	private int connectedProviderState = 0;

	// stores the bitmask of rights this module has (currently only just for checking event listener rights)
	private int ownRights = ModuleRight.RIGHT___NON;

	// MODULE STATES STUFF

	// stores the current module state: 0 = new, 1 = initialized/stopped, 2 = started/running, 3 = connected, 4 = operational, -1 = error
	private int currentState = 0;

	// lock to make this instance thread safe
	private final ReentrantLock stateLock = new ReentrantLock();

	// CONSTRUCTOR /////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor call inherited from AbstractProsumerProvider. The constructor must exist exactly in this form. If it does not the module will NOT be usable.
	 *
	 * @param prosumerConnector Connector to be used for communication with connected PROVIDERS over the local PROSUMER PORT(S)
	 * @param providerConnector Connector to be used for communication with connected PROSUMERS over the local PROVIDER PORT(S)
	 * @param componentConfiguration The database storage and module configuration
	 * @param logConnector Connector to be used for logging purposes
	 */
	public ProviderProsumerProxyExampleModule(final ProsumerConnector prosumerConnector, final ProviderConnector providerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(prosumerConnector, providerConnector, componentConfiguration, logConnector);
		// you may access componentConfiguration or log here, but calling any (well, most) connector methods will result in an Exception being thrown
		// best practice is to wait for initialize() call
	}

	// for AbstractProsumer:
	// public ProviderProsumerProxyExampleModule(ProsumerConnector prosumerConnector, ComponentConfigurationController componentConfiguration, LogConnector
	// logConnector) {
	// super(prosumerConnector, componentConfiguration, logConnector);
	// }

	// for AbstractProvider:
	// public ProviderProsumerProxyExampleModule(ProviderConnector providerConnector, ComponentConfigurationController componentConfiguration, LogConnector
	// logConnector) {
	// super(providerConnector, componentConfiguration, logConnector);
	// }

	// MODULE METHODS /////////////////////////////////////////////////////////////////////////////////////

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#checkAndLock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int checkAndLock(final ProviderPort port, final String[] path) throws ModuleException {
		checkForOperationalStateAndPort(port);
		if (!mayWrite()) {
			return ErrorCode.EROFS;
		}
		try {
			return this.prosumerConnector.checkAndLock(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/**
	 * Check for operational state and port.
	 *
	 * @param port the port
	 * @throws ModuleException the module exception
	 */
	private void checkForOperationalStateAndPort(final ProviderPort port) throws ModuleException {
		if ((this.currentState < 4) || (port == null) || (port != this.providerPort)) {
			throw new ModuleException("not in operational state or invalid port");
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#createFolder(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int createFolder(final ProviderPort port, final String[] path) throws ModuleException {
		checkForOperationalStateAndPort(port);
		if (!mayWrite()) {
			return ErrorCode.EROFS;
		}
		try {
			return this.prosumerConnector.createFolder(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#delete(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int delete(final ProviderPort port, final String[] path) throws ModuleException {
		checkForOperationalStateAndPort(port);
		if (!mayWrite()) {
			return ErrorCode.EROFS;
		}
		try {
			return this.prosumerConnector.delete(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#enterShutdown() */
	@Override
	public void enterShutdown() {
		// no operation here
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#enterStartup() */
	@Override
	public void enterStartup() {
		if (this.currentState == 1) {
			this.currentState++;
		}
		manageState();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		if (this.currentState > 1) {
			this.currentState = 1;
		}
		// we will have to recheck readiness on consecutive startup so reset it to default value
		this.connectedProviderReady = false;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		// no operation here
		// it is good practice to have Provider parts ready after enterStartup() call to care for connected modules not respecting module state events
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getChildElements(framework.model.ProviderPort, java.lang.String[], boolean) */
	@Override
	public Set<DataElement> getChildElements(final ProviderPort port, final String[] path, final boolean recursive) throws ModuleException {
		checkForOperationalStateAndPort(port);
		try {
			return this.prosumerConnector.getChildElements(this.prosumerPort, path, recursive);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getElement(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElement getElement(final ProviderPort port, final String[] path) throws ModuleException {
		checkForOperationalStateAndPort(port);
		try {
			return this.prosumerConnector.getElement(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
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
		if (port == this.prosumerPort) {
			Set<String> bridgedResults;
			try {
				bridgedResults = this.providerConnector.getSupportedModuleCommands(this.providerPort, path);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return Collections.emptySet();
			}
			if ((bridgedResults == null) || bridgedResults.isEmpty()) {
				return ImmutableSet.of(ProviderProsumerProxyExampleModule.COMMAND___GET_ACCESS_DATE);
			} else {
				return ImmutableSet.<String> builder().addAll(bridgedResults).add(ProviderProsumerProxyExampleModule.COMMAND___GET_ACCESS_DATE).build();
			}
		} else {
			if (this.prosumerPortConnected) {
				try {
					return this.prosumerConnector.getSupportedModuleCommands(this.prosumerPort, path);
				} catch (BrokerException | ModuleException | AuthorizationException e) {
					this.logConnector.log(e);
					// ignored as we are transparent to the connected prosumer
				}
			}
		}
		return Collections.emptySet();
	}

	// PROSUMER METHODS /////////////////////////////////////////////////////////////////////////////////////

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getType(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElementType getType(final ProviderPort port, final String[] path) throws ModuleException {
		checkForOperationalStateAndPort(port);
		try {
			return this.prosumerConnector.getType(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		if (this.currentState == 0) {
			// load own rights bit mask - should never fail if not called in constructor
			// this is only used here to
			try {
				this.ownRights = this.prosumerConnector.getOwnRights();
			} catch (final BrokerException e) {
				this.logConnector.log(e, "entering error state");
				this.currentState = -1;
				return;
			}
			try {
				this.componentConfiguration.initializeElementDomains(ProviderProsumerProxyExampleModule.DB___DOMAIN___ACCESS_LOG); // configuration domain is
				// initialized by
				// PersistentConfigHelper
				// below
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e, "entering error state");
				this.currentState = -1;
				return;
			}
			try {
				this.configHelper = new PersistentConfigurationHelper(this.componentConfiguration, ProviderProsumerProxyExampleModule.DB___DOMAIN___CONFIG, ProviderProsumerProxyExampleModule.DB___CONFIG_DATA_PATH);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
				this.logConnector.log(LogEventLevelType.WARNING, "no persistent config");
				this.configHelper = new PersistentConfigurationHelper(); // creates non-persistent configuration in memory
			}
			// load configuration if existent, else use default value (here: true)
			this.updateExistingElements = this.configHelper.getBoolean(ProviderProsumerProxyExampleModule.CONFIG_PROPERTY_KEY___UPDATE_EXISTING_ELEMENT_DATA, this.updateExistingElements);

			// try to register Prosumer- and ProviderPort
			// we only register one of its kind but you can register more of course, for example to distinguish between different connections or provide
			// privileged or limited ports
			try {
				this.prosumerPort = this.prosumerConnector.registerProsumerPort(this, ProviderProsumerProxyExampleModule.PORT_ID___PROSUMER, 1); // currently
				// only 0
				// (disabled)
				// or 1 are
				// allowed
				// for
				// maximum
				// connection
				// count
				this.providerPort = this.providerConnector.registerProviderPort(this, ProviderProsumerProxyExampleModule.PORT_ID___PROVIDER, -1); // -1 =
				// unlimited
				// connections
				// if this module has the right to receive events register a listener to forward them to connected Prosumer(s)
				if ((this.ownRights & ModuleRight.RECEIVE_EVENTS) > 0) {
					final String[] rootPath = {};
					this.prosumerConnector.subscribe(this.prosumerPort, rootPath, true, this);
				}
			} catch (BrokerException | AuthorizationException e) {
				this.logConnector.log(e, "entering error state");
				this.currentState = -1;
				return;
			}
			// initialize anything else here!
			this.currentState++;
		}

	}

	// PROVIDER METHODS /////////////////////////////////////////////////////////////////////////////////////

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return this.currentState == 3;
	}

	/**
	 * Manage state.
	 */
	private void manageState() {
		this.stateLock.lock();
		if ((this.currentState == 2) && this.prosumerPortConnected && this.providerPortConnected) {
			this.currentState++;
		} else if ((this.currentState == 3) && (!this.prosumerPortConnected || !this.providerPortConnected)) {
			this.currentState--;
		} else if ((this.currentState == 3) && this.connectedProviderReady) {
			this.currentState++;
		} else if ((this.currentState == 4) && !this.connectedProviderReady) {
			this.currentState--;
		}
		this.stateLock.unlock();
	}

	/**
	 * May write.
	 *
	 * @return true, if successful
	 */
	private boolean mayWrite() {
		// very simple check if connected provider may be written
		if (!this.providerPortConnected || ((this.connectedProviderState & ModuleStateType.READONLY) > 0)) {
			return false;
		} else {
			return true;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#move(framework.model.ProviderPort, java.lang.String[], java.lang.String[]) */
	@Override
	public int move(final ProviderPort port, final String[] srcPath, final String[] destPath) throws ModuleException {
		checkForOperationalStateAndPort(port);
		if (!mayWrite()) {
			return ErrorCode.EROFS;
		}
		try {
			return this.prosumerConnector.move(this.prosumerPort, srcPath, destPath);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return CommandResultHelper.getDefaultResultFail();
		}
		if (command.equals(GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES)) {
			final ConfigValue updateExistingElementConfigValue = new ConfigValue(ProviderProsumerProxyExampleModule.CONFIG_PROPERTY_KEY___UPDATE_EXISTING_ELEMENT_DATA);
			updateExistingElementConfigValue.setCurrentValueBoolean(this.updateExistingElements);
			updateExistingElementConfigValue.setDescriptionString("Update with subsequent access (saves last access instead of first).");
			return CommandResultHelper.getDefaultResultOk(ProviderProsumerProxyExampleModule.CONFIG_PROPERTY_KEY___UPDATE_EXISTING_ELEMENT_DATA, updateExistingElementConfigValue.toString());
		} else if (command.equals(GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES) && (properties != null)) {
			final ConfigValue updateExistingElementConfigValue = new ConfigValue(ProviderProsumerProxyExampleModule.CONFIG_PROPERTY_KEY___UPDATE_EXISTING_ELEMENT_DATA, properties.get(ProviderProsumerProxyExampleModule.CONFIG_PROPERTY_KEY___UPDATE_EXISTING_ELEMENT_DATA));
			if (updateExistingElementConfigValue.isValid()) {
				this.updateExistingElements = updateExistingElementConfigValue.getCurrentValueBoolean();
				this.configHelper.updateBoolean(ProviderProsumerProxyExampleModule.CONFIG_PROPERTY_KEY___UPDATE_EXISTING_ELEMENT_DATA, this.updateExistingElements);
				return CommandResultHelper.getDefaultResultOk();
			}
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.DataElementEventListener#onElementEvent(framework.model.ProsumerPort, framework.model.event.DataElementEvent) */
	@Override
	public void onElementEvent(final ProsumerPort port, final DataElementEvent event) {
		// just forward events to connected Prosumer(s)
		if (this.providerPortConnected) {
			try {
				this.providerConnector.sendElementEvent(this.providerPort, event.dataElement, event.eventType);
			} catch (final BrokerException e) {
				this.logConnector.log(e);
			}
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onModuleCommand(framework.model.Port, java.lang.String, java.lang.String[], java.util.Map) */
	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		if (this.currentState < 2) {
			return CommandResultHelper.getDefaultResultFail();
		}
		if ((command != null) && command.equals(ProviderProsumerProxyExampleModule.COMMAND___GET_ACCESS_DATE) && (port == this.prosumerPort)) {
			DataElement element;
			try {
				element = this.componentConfiguration.getElement(ProviderProsumerProxyExampleModule.DB___DOMAIN___ACCESS_LOG, path);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
				return CommandResultHelper.getDefaultResultFail();
			}
			if (element != null) {
				return CommandResultHelper.getDefaultResultOk(ProviderProsumerProxyExampleModule.PROPERTY_KEY___ACCESS_DATE, String.valueOf(element.getModificationDate()));
			}
		} else if ((port == this.prosumerPort) && (this.currentState >= 3)) {
			try {
				return this.providerConnector.sendModuleCommand(this.providerPort, command, path, properties);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return CommandResultHelper.getDefaultResultFail();
			}
		} else if ((port == this.providerPort) && (this.currentState >= 3)) {
			try {
				return this.prosumerConnector.sendModuleCommand(this.prosumerPort, command, path, properties);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return CommandResultHelper.getDefaultResultFail();
			}
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		if (port == this.prosumerPort) { // instanceof would also be sufficient in this context (only one ProsumerPort)
			this.prosumerPortConnected = true;
		} else if (port == this.providerPort) {
			this.providerPortConnected = true;
		}
		manageState();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		if (port == this.prosumerPort) { // instanceof would also be sufficient in this context (only one ProsumerPort)
			this.prosumerPortConnected = false;
		} else if (port == this.providerPort) {
			this.providerPortConnected = false;
		}
		manageState();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Prosumer#onProviderStateEvent(framework.model.Port, framework.model.event.ProviderStateEvent) */
	@Override
	public void onProviderStateEvent(final Port port, final ProviderStateEvent event) {
		// if the connected provider signals readiness this proxy module also becomes ready
		if ((event.state & ModuleStateType.READY) > 0) {
			this.connectedProviderReady = true;
		} else {
			this.connectedProviderReady = false;
		}
		this.connectedProviderState = event.state;
		// propagate state event to connected Prosumer
		if (this.providerPortConnected) {
			try {
				this.providerConnector.sendState(this.providerPort, event.state);
			} catch (final BrokerException e) {
				this.logConnector.log(e);
			}
		}
		manageState();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#onStateRequest(framework.model.ProviderPort) */
	@Override
	public void onStateRequest(final ProviderPort port) {
		if (this.prosumerPortConnected) {
			try {
				this.prosumerConnector.requestConnectedProviderStatus(this.prosumerPort);
			} catch (BrokerException | ModuleException e) {
				this.logConnector.log(e);
			}
		}
	}

	// LOCAL CUSTOM METHODS /////////////////////////////////////////////////////////////////////////////////////

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#readData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public InputStream readData(final ProviderPort port, final String[] path) throws ModuleException {
		checkForOperationalStateAndPort(port);
		DataElement element = null;
		try {
			element = this.componentConfiguration.getElement(ProviderProsumerProxyExampleModule.DB___DOMAIN___ACCESS_LOG, path);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}
		if (element == null) {
			try {
				this.componentConfiguration.storeElement(ProviderProsumerProxyExampleModule.DB___DOMAIN___ACCESS_LOG, path, new DataElement(path));
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		} else if (this.updateExistingElements) {
			try {
				this.componentConfiguration.updateElementModificationDate(ProviderProsumerProxyExampleModule.DB___DOMAIN___ACCESS_LOG, path, System.currentTimeMillis());
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		}
		try {
			return this.prosumerConnector.readData(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#unlock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int unlock(final ProviderPort port, final String[] path) throws ModuleException {
		checkForOperationalStateAndPort(port);
		if (!mayWrite()) {
			return ErrorCode.EROFS;
		}
		try {
			return this.prosumerConnector.unlock(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#writeData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public OutputStream writeData(final ProviderPort port, final String[] path) throws ModuleException {
		checkForOperationalStateAndPort(port);
		DataElement element = null;
		try {
			element = this.componentConfiguration.getElement(ProviderProsumerProxyExampleModule.DB___DOMAIN___ACCESS_LOG, path);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}
		if (element == null) {
			try {
				this.componentConfiguration.storeElement(ProviderProsumerProxyExampleModule.DB___DOMAIN___ACCESS_LOG, path, new DataElement(path));
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		} else if (this.updateExistingElements) {
			try {
				this.componentConfiguration.updateElementModificationDate(ProviderProsumerProxyExampleModule.DB___DOMAIN___ACCESS_LOG, path, System.currentTimeMillis());
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		}
		try {
			return this.prosumerConnector.writeData(this.prosumerPort, path);
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
			return null;
		}
	}
}
