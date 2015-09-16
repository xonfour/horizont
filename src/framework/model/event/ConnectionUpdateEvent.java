package framework.model.event;

import java.util.Date;

import framework.control.Core;
import framework.model.event.type.ConnectionEventType;
import framework.model.summary.ConnectionSummary;

/**
 * Event send on connection updates.
 *
 * @author Stefan Werner
 */
public final class ConnectionUpdateEvent implements GeneralEvent {

	public final ConnectionSummary connectionSummary;
	public final long creationDate;
	public final ConnectionEventType type;
	public final boolean uniqueEvent;

	/**
	 * Instantiates a new connection update event.
	 *
	 * @param connectionSummary the connection summary
	 * @param type the type
	 */
	public ConnectionUpdateEvent(final ConnectionSummary connectionSummary, final ConnectionEventType type) {
		this.connectionSummary = connectionSummary;
		this.type = type;
		this.uniqueEvent = true;
		this.creationDate = System.currentTimeMillis();
	}

	/**
	 * Instantiates a new connection update event.
	 *
	 * @param connectionSummary the connection summary
	 * @param type the type
	 * @param uniqueEvent true for unique event (so it may not be overwritten by newer events of the same type)
	 */
	public ConnectionUpdateEvent(final ConnectionSummary connectionSummary, final ConnectionEventType type, final boolean uniqueEvent) {
		this.connectionSummary = connectionSummary;
		this.type = type;
		this.uniqueEvent = uniqueEvent;
		this.creationDate = System.currentTimeMillis();
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals(final Object obj) {
		if (this.uniqueEvent) {
			return super.equals(obj);
		}
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ConnectionUpdateEvent)) {
			return false;
		}
		final ConnectionUpdateEvent other = (ConnectionUpdateEvent) obj;
		if (this.connectionSummary == null) {
			if (other.connectionSummary != null) {
				return false;
			}
		} else if (!(this.connectionSummary.getProsumerPortSummary().getModuleId().equals(other.connectionSummary.getProsumerPortSummary().getModuleId()) && this.connectionSummary.getProsumerPortSummary().getPortId().equals(other.connectionSummary.getProsumerPortSummary().getPortId()) && this.connectionSummary.getProviderPortSummary().getModuleId().equals(other.connectionSummary.getProviderPortSummary().getModuleId()) && this.connectionSummary.getProviderPortSummary().getPortId().equals(other.connectionSummary.getProviderPortSummary().getPortId()))) {
			return false;
		}
		return true;
	}

	/**
	 * Gets a simple hash string of the event.
	 * <p>
	 * TODO: Do we really need that? Why not just use toString()?
	 *
	 * @return the hash string
	 */
	private String getHashString() {
		return this.connectionSummary.getProsumerPortSummary().getModuleId() + " " + this.connectionSummary.getProsumerPortSummary().getPortId() + " " + this.connectionSummary.getProviderPortSummary().getModuleId() + " " + this.connectionSummary.getProviderPortSummary().getPortId();
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		if (this.uniqueEvent) {
			return super.hashCode();
		}
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.connectionSummary == null) ? 0 : getHashString().hashCode());
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "ConnectionUpdateEvent [" + Core.getDefaultDateFormat().format(new Date(this.creationDate)) + ", type=" + this.type + ", connectionSummary=" + this.connectionSummary + ", uniqueEvent=" + this.uniqueEvent + "]";
	}
}
