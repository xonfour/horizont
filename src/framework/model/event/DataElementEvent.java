package framework.model.event;

import java.util.Date;

import framework.control.Core;
import framework.model.DataElement;
import framework.model.event.type.DataElementEventType;

/**
 * Event send when data elements change.
 *
 * @author Stefan Werner
 */
public final class DataElementEvent implements GeneralEvent {

	public final long creationDate;
	public final DataElementEventType eventType;
	public final DataElement dataElement;

	/**
	 * Instantiates a new data element event.
	 *
	 * @param dataElement the data element
	 * @param eventType the event type
	 */
	public DataElementEvent(final DataElement dataElement, final DataElementEventType eventType) {
		this.dataElement = dataElement;
		this.eventType = eventType;
		this.creationDate = System.currentTimeMillis();
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "DataElementEvent [" + Core.getDefaultDateFormat().format(new Date(this.creationDate)) + ", eventType=" + this.eventType + ", dataElement=" + this.dataElement + "]";
	}
}
