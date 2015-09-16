package framework.model.event;

import java.util.Date;

import framework.control.Core;
import framework.model.event.type.PortUpdateEventType;
import framework.model.summary.PortSummary;

/**
 * Event send on port updates.
 *
 * @author Stefan Werner
 */
public final class PortUpdateEvent implements GeneralEvent {

	public final long creationDate;
	public final PortSummary portSummary;
	public final PortUpdateEventType type;
	public final boolean uniqueEvent;

	/**
	 * Instantiates a new port update event.
	 *
	 * @param portSummary the port summary
	 * @param type the type
	 * @param uniqueEvent true for unique event (so it may not be overwritten by newer events of the same type)
	 */
	public PortUpdateEvent(final PortSummary portSummary, final PortUpdateEventType type, final boolean uniqueEvent) {
		this.portSummary = portSummary;
		this.type = type;
		this.uniqueEvent = uniqueEvent;
		this.creationDate = System.currentTimeMillis();
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof PortUpdateEvent)) {
			return false;
		}
		final PortUpdateEvent other = (PortUpdateEvent) obj;
		if (this.portSummary == null) {
			if (other.portSummary != null) {
				return false;
			}
		} else if (!(this.portSummary.getModuleId().equals(other.portSummary.getModuleId()) && this.portSummary.getPortId().equals(other.portSummary.getModuleId()))) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the hash string.
	 * <p>
	 * TODO: Do we really need that? Why not just use toString()?
	 *
	 * @return the hash string
	 */
	private String getHashString() {
		return this.portSummary.getModuleId() + " " + this.portSummary.getPortId();
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
		result = (prime * result) + ((this.portSummary == null) ? 0 : getHashString().hashCode());
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "PortUpdateEvent [" + Core.getDefaultDateFormat().format(new Date(this.creationDate)) + ", type=" + this.type + ", portSummary=" + this.portSummary + ", uniqueEvent=" + this.uniqueEvent + "]";
	}
}
