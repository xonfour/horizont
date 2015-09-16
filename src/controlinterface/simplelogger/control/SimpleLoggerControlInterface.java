package controlinterface.simplelogger.control;

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
import helper.PersistentConfigurationHelper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Charsets;

import controlinterface.iface.AbstractControlInterface;
import controlinterface.iface.GeneralEventListener;
import db.iface.ComponentConfigurationController;

/**
 * Simple implementation of a logger that logs events to a custom file. It defaults to a file in the system's data storage folder. The location and the types of
 * events to log are configurable.
 * <p>
 * TODO: Currently the logger only reads configuration from database, but does not write it (so it always uses default unless database (for example as JSON
 * file) is modified manually.
 *
 * @author Stefan Werner
 */
public class SimpleLoggerControlInterface extends AbstractControlInterface implements GeneralEventListener {

	private static final boolean CONFIG_DEFAULT___LOG_EVENT = true;
	private static final String CONFIG_DEFAULT___LOG_FILENAME = "system.log";
	private static final String CONFIG_DOMAIN = "config";
	private static final String CONFIG_FALLBACK___LOG_FILE_LOCATION = System.getProperty("user.home");
	private static final String CONFIG_KEY___LOG_EVENT_CU = "log_event_cu";
	private static final String CONFIG_KEY___LOG_EVENT_L = "log_event_le";
	private static final String CONFIG_KEY___LOG_EVENT_MA = "log_event_ma";
	private static final String CONFIG_KEY___LOG_EVENT_MU = "log_event_mu";
	private static final String CONFIG_KEY___LOG_EVENT_PU = "log_event_pu";
	private static final String CONFIG_KEY___LOG_EVENT_SS = "log_event_ss";
	private static final String CONFIG_KEY___LOG_FILE_LOCATION = "log_file_loc";
	private static final String CONFIG_KEY___LOG_FILENAME = "loc_filename";
	private static final String[] CONFIG_PATH = { "config" };

	private PersistentConfigurationHelper configHelper;
	private final ReentrantLock lock = new ReentrantLock(true);
	private boolean logCU = SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT;
	private String logFileLocation = null;
	private String logFileName = SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_FILENAME;
	private boolean logL = SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT;
	private boolean logMA = SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT;
	private boolean logMU = SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT;
	private boolean logPU = SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT;
	private boolean logSS = SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT;
	private BufferedWriter writer = null;

	/**
	 * Instantiates a new simple logger control interface.
	 *
	 * @param connector the CI connector
	 * @param ciConfiguration the CI configuration
	 * @param logConnector the log connector
	 */
	public SimpleLoggerControlInterface(final ControlInterfaceConnector connector, final ComponentConfigurationController ciConfiguration, final LogConnector logConnector) {
		super(connector, ciConfiguration, logConnector);
	}

	/**
	 * Close file writer.
	 */
	private void closeWriter() {
		this.lock.lock();
		if (this.writer != null) {
			try {
				this.writer.close();
			} catch (final IOException e) {
				this.logConnector.log(e);
			}
			this.writer = null;
		}
		this.lock.unlock();
	}

	/**
	 * Log a message to file.
	 *
	 * @param msg the message
	 */
	private void log(final String msg) {
		this.lock.lock();
		if (this.writer != null) {
			try {
				this.writer.write(msg + "\n");
			} catch (final IOException e) {
				this.logConnector.log(e);
				closeWriter();
			}
		}
		this.lock.unlock();
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.GeneralEventListener#onGeneralEvent(framework.model.event.GeneralEvent) */
	@Override
	public void onGeneralEvent(final GeneralEvent event) {
		if (event instanceof ConnectionUpdateEvent) {
			final ConnectionUpdateEvent cuEvent = (ConnectionUpdateEvent) event;
			if (this.logCU) {
				log(cuEvent.toString());
			}
		} else if (event instanceof LogEvent) {
			final LogEvent lEvent = (LogEvent) event;
			if (this.logL) {
				log(lEvent.toString());
			}
		} else if (event instanceof ModuleActivityEvent) {
			final ModuleActivityEvent maEvent = (ModuleActivityEvent) event;
			if (this.logMA) {
				log(maEvent.toString());
			}
		} else if (event instanceof ModuleUpdateEvent) {
			final ModuleUpdateEvent muEvent = (ModuleUpdateEvent) event;
			if (this.logMU) {
				log(muEvent.toString());
			}
		} else if (event instanceof PortUpdateEvent) {
			final PortUpdateEvent puEvent = (PortUpdateEvent) event;
			if (this.logPU) {
				log(puEvent.toString());
			}
		} else if (event instanceof SystemStateEvent) {
			final SystemStateEvent ssEvent = (SystemStateEvent) event;
			if (this.logSS) {
				log(ssEvent.toString());
			}
		}
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.ControlInterface#shutdown() */
	@Override
	public void shutdown() {
		closeWriter();
	}

	/* (non-Javadoc)
	 *
	 * @see controlinterface.iface.ControlInterface#startup() */
	@Override
	public void startup() {
		try {
			this.configHelper = new PersistentConfigurationHelper(this.ciConfiguration, SimpleLoggerControlInterface.CONFIG_DOMAIN, SimpleLoggerControlInterface.CONFIG_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.configHelper = new PersistentConfigurationHelper();
		}
		this.logCU = this.configHelper.getBoolean(SimpleLoggerControlInterface.CONFIG_KEY___LOG_EVENT_CU, SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT);
		this.logL = this.configHelper.getBoolean(SimpleLoggerControlInterface.CONFIG_KEY___LOG_EVENT_L, SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT);
		this.logMA = this.configHelper.getBoolean(SimpleLoggerControlInterface.CONFIG_KEY___LOG_EVENT_MA, SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT);
		this.logMU = this.configHelper.getBoolean(SimpleLoggerControlInterface.CONFIG_KEY___LOG_EVENT_MU, SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT);
		this.logPU = this.configHelper.getBoolean(SimpleLoggerControlInterface.CONFIG_KEY___LOG_EVENT_PU, SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT);
		this.logSS = this.configHelper.getBoolean(SimpleLoggerControlInterface.CONFIG_KEY___LOG_EVENT_SS, SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_EVENT);
		this.logFileLocation = this.configHelper.getString(SimpleLoggerControlInterface.CONFIG_KEY___LOG_FILE_LOCATION, null);
		this.logFileName = this.configHelper.getString(SimpleLoggerControlInterface.CONFIG_KEY___LOG_FILENAME, SimpleLoggerControlInterface.CONFIG_DEFAULT___LOG_FILENAME);
		if (this.logFileLocation == null) {
			try {
				this.logFileLocation = this.connector.getSystemDataStorageLocation();
			} catch (final AuthorizationException e) {
				this.logFileLocation = SimpleLoggerControlInterface.CONFIG_FALLBACK___LOG_FILE_LOCATION;
			}
		}
		final Path logFilePath = Paths.get(this.logFileLocation, this.logFileName);
		if (!Files.isWritable(logFilePath)) {
			this.logConnector.log(LogEventLevelType.ERROR, "Log file not writable.");
			return;
		}
		try {
			this.writer = Files.newBufferedWriter(logFilePath, Charsets.UTF_8, StandardOpenOption.APPEND);
		} catch (final IOException e) {
			this.logConnector.log(e, "Log file not writable.");
			return;
		}
		try {
			if (!this.connector.addGeneralEventListener(this, GeneralEventType.GENERAL_EVENT)) {
				this.logConnector.log(LogEventLevelType.ERROR, "Unable to add listener for system events.");
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e, "Error while adding listener for system events");
		}
	}
}