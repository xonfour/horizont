package framework.control;

import framework.model.event.LogEvent;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.LogEventSourceType;

/**
 * Connector to the framework for all other system parts to access the logging subsystem.
 *
 * @author Stefan Werner
 */
public class LogConnector {

	private ControlInterfaceActionHandler actionHandler = null;
	private final String componentId;
	private final LogEventSourceType source;

	/**
	 * Instantiates a new log connector.
	 *
	 * @param controlActionHandler the control action handler
	 * @param source the source
	 * @param componentId the component ID
	 */
	public LogConnector(final ControlInterfaceActionHandler controlActionHandler, final LogEventSourceType source, final String componentId) {
		this.actionHandler = controlActionHandler;
		this.source = source;
		this.componentId = componentId;
	}

	/**
	 * Instantiates a new log connector without a connected action handler. Used early at system startup when there is no control action handler yet available.
	 * Will log to System.out unless handler is set. Also useful for testing.
	 *
	 * @param source the source
	 * @param componentId the component ID
	 */
	public LogConnector(final LogEventSourceType source, final String componentId) {
		this.source = source;
		this.componentId = componentId;
	}

	/**
	 * Logs an exception.
	 *
	 * @param e the exception
	 */
	public void log(final Exception e) {
		if (this.actionHandler != null) {
			this.actionHandler.announceLogElement(new LogEvent(LogEventLevelType.ERROR, this.source, this.componentId, e.getMessage(), e));
		} else {
			e.printStackTrace();
		}
	}

	/**
	 * Logs an exception and a custom message.
	 *
	 * @param e the exception
	 * @param message the message
	 */
	public void log(final Exception e, final String message) {
		if (this.actionHandler != null) {
			this.actionHandler.announceLogElement(new LogEvent(LogEventLevelType.ERROR, this.source, this.componentId, message, e));
		} else {
			e.printStackTrace();
		}
	}

	/**
	 * Logs a custom message with the selected log level.
	 *
	 * @param logLevel the log level
	 * @param message the message
	 */
	public void log(final LogEventLevelType logLevel, final String message) {
		if (this.actionHandler != null) {
			this.actionHandler.announceLogElement(new LogEvent(logLevel, this.source, this.componentId, message));
		} else {
			if ((logLevel == LogEventLevelType.DEBUG) || (logLevel == LogEventLevelType.INFO)) {
				System.out.println(System.currentTimeMillis() + " " + this.source + "/" + this.componentId + ": " + message);
			} else {
				System.err.println(System.currentTimeMillis() + " " + this.source + "/" + this.componentId + ": " + message);
			}
		}
	}

	/**
	 * Sets the control action handler.
	 *
	 * @param handler the new control action handler
	 */
	void setControlActionHandler(final ControlInterfaceActionHandler handler) {
		this.actionHandler = handler;
	}
}
