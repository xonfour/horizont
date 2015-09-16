package framework.model.event;

import java.util.Date;

import framework.control.Core;
import framework.model.event.type.LogEventLevelType;
import framework.model.event.type.LogEventSourceType;

/**
 * Event send on log entries.
 *
 * @author Stefan Werner
 */
public class LogEvent implements GeneralEvent {

	private final String componentId;
	private final long creationDate;
	private final Exception exception;
	private final LogEventLevelType logLevel;
	private final LogEventSourceType logSource;
	private final String message;

	/**
	 * Instantiates a new log event.
	 *
	 * @param logLevel the log level
	 * @param logSource the log source
	 * @param componentId the component ID
	 * @param message the message
	 */
	public LogEvent(final LogEventLevelType logLevel, final LogEventSourceType logSource, final String componentId, final String message) {
		this.creationDate = System.currentTimeMillis();
		this.logLevel = logLevel;
		this.logSource = logSource;
		this.componentId = componentId;
		this.message = message;
		this.exception = null;
	}

	/**
	 * Instantiates a new log event with an exception.
	 *
	 * @param logLevel the log level
	 * @param logSource the log source
	 * @param componentId the component ID
	 * @param message the message
	 * @param e the Exception
	 */
	public LogEvent(final LogEventLevelType logLevel, final LogEventSourceType logSource, final String componentId, final String message, final Exception e) {
		this.creationDate = System.currentTimeMillis();
		this.logLevel = logLevel;
		this.logSource = logSource;
		this.componentId = componentId;
		this.message = message;
		this.exception = e;
	}

	/**
	 * Gets the component ID.
	 *
	 * @return the component ID
	 */
	public String getComponentId() {
		return this.componentId;
	}

	/**
	 * Gets the creation date.
	 *
	 * @return the creation date
	 */
	public Long getCreationDate() {
		return this.creationDate;
	}

	/**
	 * Gets the exception.
	 *
	 * @return the exception
	 */
	public Exception getException() {
		return this.exception;
	}

	/**
	 * Gets the log level.
	 *
	 * @return the log level
	 */
	public LogEventLevelType getLogLevel() {
		return this.logLevel;
	}

	/**
	 * Gets the log source.
	 *
	 * @return the log source
	 */
	public LogEventSourceType getLogSource() {
		return this.logSource;
	}

	/**
	 * Gets the message.
	 *
	 * @return the message
	 */
	public String getMessage() {
		return this.message;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "LogEvent [" + Core.getDefaultDateFormat().format(new Date(this.creationDate)) + ", logLevel=" + this.logLevel + ", logSource=" + this.logSource + ", componentId=" + this.componentId + ", message=" + this.message + ", exception=" + this.exception + "]";
	}
}
