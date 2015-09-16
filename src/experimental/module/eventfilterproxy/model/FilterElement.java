package experimental.module.eventfilterproxy.model;

import java.util.Arrays;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import framework.model.DataElement;
import framework.model.event.type.DataElementEventType;

/**
 *
 * @author Stefan Werner
 */
public final class FilterElement implements Delayed {

	private final long elapseDate;
	private final DataElement element;
	private final DataElementEventType eventType;

	public FilterElement(final DataElement element, final DataElementEventType eventType, final int delay) {
		this.element = element;
		this.eventType = eventType;
		this.elapseDate = System.currentTimeMillis() + delay;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Comparable#compareTo(java.lang.Object) */
	@Override
	public int compareTo(final Delayed arg0) {
		if (arg0 instanceof FilterElement) {
			final long delta = this.elapseDate - ((FilterElement) arg0).elapseDate;
			if (delta < 0) {
				return -1;
			} else if (delta > 0) {
				return 1;
			} else {
				return 0;
			}
		}
		return -1;
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
		if (!(obj instanceof FilterElement)) {
			return false;
		}
		final FilterElement other = (FilterElement) obj;
		if ((this.element != null) && (other.getElement() != null)) {
			return Arrays.equals(this.element.getPath(), other.getElement().getPath());
		} else {
			return this.element == other.getElement();
		}
	}

	/**
	 * @return the currentDelay
	 */
	public long getCurrentDelay() {
		return this.elapseDate;
	}

	/* (non-Javadoc)
	 *
	 * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit) */
	@Override
	public long getDelay(final TimeUnit arg0) {
		final long delta = this.elapseDate - System.currentTimeMillis();
		return arg0.convert(delta, TimeUnit.MILLISECONDS);
	}

	/**
	 * @return the element
	 */
	public DataElement getElement() {
		return this.element;
	}

	/**
	 * @return the eventType
	 */
	public DataElementEventType getEventType() {
		return this.eventType;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + (((this.element != null) && (this.element.getPath() != null)) ? Arrays.hashCode(this.element.getPath()) : 0);
		return result;
	}
}
