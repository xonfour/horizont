package framework.model.event;

import java.util.Date;

import framework.control.Core;
import framework.model.event.type.SystemStateType;

/**
 * Event send on system state changes.
 *
 * @author Stefan Werner
 */
public final class SystemStateEvent implements GeneralEvent {

	public final long creationDate;
	public final SystemStateType systemStateType;

	/**
	 * Instantiates a new system state event.
	 *
	 * @param systemStateType the system state type
	 */
	public SystemStateEvent(final SystemStateType systemStateType) {
		this.systemStateType = systemStateType;
		this.creationDate = System.currentTimeMillis();
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "SystemStateEvent [" + Core.getDefaultDateFormat().format(new Date(this.creationDate)) + ", systemStateType=" + this.systemStateType + "]";
	}

}
