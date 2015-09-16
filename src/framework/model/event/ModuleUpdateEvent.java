package framework.model.event;

import java.util.Date;

import framework.control.Core;
import framework.model.event.type.ModuleUpdateEventType;
import framework.model.summary.ModuleSummary;

/**
 * Event send on module updates.
 *
 * @author Stefan Werner
 */
public final class ModuleUpdateEvent implements GeneralEvent {

	public final long creationDate;
	public final ModuleSummary moduleSummary;
	public final ModuleUpdateEventType type;
	public final boolean uniqueEvent;

	/**
	 * Instantiates a new module update event.
	 *
	 * @param moduleSummary the module summary
	 * @param type the type
	 * @param uniqueEvent true for unique event (so it may not be overwritten by newer events of the same type)
	 */
	public ModuleUpdateEvent(final ModuleSummary moduleSummary, final ModuleUpdateEventType type, final boolean uniqueEvent) {
		this.moduleSummary = moduleSummary;
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
		if (!(obj instanceof ModuleUpdateEvent)) {
			return false;
		}
		final ModuleUpdateEvent other = (ModuleUpdateEvent) obj;
		if (this.moduleSummary == null) {
			if (other.moduleSummary != null) {
				return false;
			}
		} else if (!this.moduleSummary.equals(other.moduleSummary)) {
			return false;
		}
		return true;
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
		result = (prime * result) + ((this.moduleSummary == null) ? 0 : this.moduleSummary.getModuleId().hashCode());
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "ModuleUpdateEvent [" + Core.getDefaultDateFormat().format(new Date(this.creationDate)) + ", type=" + this.type + ", moduleSummary=" + this.moduleSummary + ", uniqueEvent=" + this.uniqueEvent + "]";
	}
}
