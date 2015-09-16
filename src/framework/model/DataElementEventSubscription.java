package framework.model;

import java.util.Arrays;

import module.iface.DataElementEventListener;

/**
 * Represents a prosumer subscription for data element events.
 *
 * @author Stefan Werner
 */
public final class DataElementEventSubscription {

	private final DataElementEventListener dataElementEventListener;
	private final String[] path;
	private final boolean recursive;

	/**
	 * Instantiates a new data element event subscription.
	 *
	 * @param path the path
	 * @param recursive set to true to match recursively
	 * @param dataElementEventListener the data element event listener
	 */
	public DataElementEventSubscription(final String[] path, final boolean recursive, final DataElementEventListener dataElementEventListener) {
		this.path = path;
		this.recursive = recursive;
		this.dataElementEventListener = dataElementEventListener;
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
		if (!(obj instanceof DataElementEventSubscription)) {
			return false;
		}
		final DataElementEventSubscription other = (DataElementEventSubscription) obj;
		if (this.dataElementEventListener == null) {
			if (other.dataElementEventListener != null) {
				return false;
			}
		} else if (!this.dataElementEventListener.equals(other.dataElementEventListener)) {
			return false;
		}
		if (!Arrays.equals(this.path, other.path)) {
			return false;
		}
		if (this.recursive != other.recursive) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the data element event listener.
	 *
	 * @return the data element event listener
	 */
	public DataElementEventListener getDataElementEventListener() {
		return this.dataElementEventListener;
	}

	/**
	 * Gets the path.
	 *
	 * @return the path
	 */
	public String[] getPath() {
		return this.path;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.dataElementEventListener == null) ? 0 : this.dataElementEventListener.hashCode());
		result = (prime * result) + Arrays.hashCode(this.path);
		result = (prime * result) + (this.recursive ? 1231 : 1237);
		return result;
	}

	/**
	 * Checks if a given path is included in this subscription.
	 *
	 * @param otherPath the other path
	 * @return true, if successful
	 */
	public boolean isIncluded(final String[] otherPath) {
		if (otherPath.length < this.path.length) {
			return false;
		} else if ((otherPath.length == this.path.length) || ((otherPath.length > this.path.length) && this.recursive)) {
			for (int i = 0; i < this.path.length; i++) {
				if (!otherPath[i].equals(this.path[i])) {
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Checks if subscription is recursive.
	 *
	 * @return true, if recursive
	 */
	public boolean isRecursive() {
		return this.recursive;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "DataElementEventSubscription [path=" + Arrays.toString(this.path) + ", recursive=" + this.recursive + ", dataElementEventListener=" + this.dataElementEventListener + "]";
	}
}
