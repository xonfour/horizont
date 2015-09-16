package framework.model;

import helper.ObjectValidator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import framework.model.type.DataElementType;

/**
 * Represents a data element for exchanging meta data between modules and for storage in the database.
 *
 * @author Stefan Werner
 */
public final class DataElement implements Comparable<DataElement> {

	private Map<String, String> additionalProperties;
	private final long modificationDate;
	private final String name;
	private final String[] path;
	private final long size;
	private final DataElementType type;

	/**
	 * Instantiates a new data element with the given path and meta data set to default values:<br>
	 * <code>type = DataElementType.NONEXISTENT_OR_UNKNOWN; size = 0; modificationDate = System.currentTimeMillis(); additionalProperties = null;</code>
	 *
	 * @param path the path
	 */
	public DataElement(final String[] path) {
		if (path.length > 0) {
			this.name = path[path.length - 1];
		} else {
			this.name = null;
		}
		this.path = path;
		this.type = DataElementType.NONEXISTENT_OR_UNKNOWN;
		this.size = 0;
		this.modificationDate = System.currentTimeMillis();
		this.additionalProperties = null;
		if (!ObjectValidator.checkPath(path)) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Instantiates a new data element with the given path and all other meta data copied from an old element.
	 *
	 * @param newPath the new path
	 * @param oldElement the old element
	 */
	public DataElement(final String[] newPath, final DataElement oldElement) {
		if (newPath.length > 0) {
			this.name = newPath[newPath.length - 1];
		} else {
			this.name = null;
		}
		this.path = newPath;
		this.type = oldElement.getType();
		this.size = oldElement.getSize();
		this.modificationDate = oldElement.getModificationDate();
		this.additionalProperties = oldElement.getAdditionalProperties();
		if (!ObjectValidator.checkPath(this.path)) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Instantiates a new data element with additional properties.
	 *
	 * @param path the path
	 * @param type the type
	 * @param size the size
	 * @param modificationDate the modification date
	 * @param additionalProperties the additional properties
	 */
	public DataElement(final String[] path, final DataElementType type, final long size, final long modificationDate, final Map<String, String> additionalProperties) {
		if (path.length > 0) {
			this.name = path[path.length - 1];
		} else {
			this.name = null;
		}
		this.path = Arrays.copyOf(path, path.length);
		this.type = type;
		this.size = size;
		this.modificationDate = modificationDate;
		this.additionalProperties = additionalProperties;
		if (!ObjectValidator.checkDataElement(this)) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Instantiates a new data element.
	 *
	 * @param path the path
	 * @param type the type
	 * @param size the size
	 * @param modificationDate the modification date
	 */
	public DataElement(final String[] path, final DataElementType type, final Long size, final Long modificationDate) {
		if (path.length > 0) {
			this.name = path[path.length - 1];
		} else {
			this.name = null;
		}
		this.path = path;
		this.type = type;
		this.size = size;
		this.modificationDate = modificationDate;
		this.additionalProperties = null;
		if (!ObjectValidator.checkDataElement(this)) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Instantiates a new data element with the given path, additional properties and all other meta data set to default values:<br>
	 * <code>type = DataElementType.NONEXISTENT_OR_UNKNOWN; size = 0; modificationDate = System.currentTimeMillis()</code>
	 *
	 * @param path the path
	 * @param additionalProperties the additional properties
	 */
	public DataElement(final String[] path, final Map<String, String> additionalProperties) {
		if (path.length > 0) {
			this.name = path[path.length - 1];
		} else {
			this.name = null;
		}
		this.path = path;
		this.type = DataElementType.NONEXISTENT_OR_UNKNOWN;
		this.size = 0;
		this.modificationDate = System.currentTimeMillis();
		this.additionalProperties = additionalProperties;
		if (!ObjectValidator.checkPath(path)) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Adds additional properties.
	 *
	 * @param additionalProperties the additional properties
	 */
	public void addAdditionalProperties(final Map<String, String> additionalProperties) {
		if (this.additionalProperties == null) {
			this.additionalProperties = new HashMap<String, String>();
		}
		this.additionalProperties.putAll(additionalProperties);
	}

	/**
	 * Adds an additional property.
	 *
	 * @param key the key
	 * @param value the value
	 */
	public void addAdditionalProperty(final String key, final String value) {
		if ((key != null) && !key.isEmpty() && (value != null) && !value.isEmpty()) {
			if (this.additionalProperties == null) {
				this.additionalProperties = new HashMap<String, String>();
			}
			this.additionalProperties.put(key, value);
		}
	}

	@Override
	public DataElement clone() {
		Map<String, String> clonedProperties = null;
		if (this.additionalProperties != null) {
			clonedProperties = new HashMap<String, String>(this.additionalProperties);
		}
		return new DataElement(this.path, this.type, this.size, this.modificationDate, clonedProperties);
	}

	/**
	 * Clone without additional properties.
	 *
	 * @return the cloned data element
	 */
	public DataElement cloneWithoutAdditionalProperties() {
		return new DataElement(this.path, this.type, this.size, this.modificationDate);
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Comparable#compareTo(java.lang.Object) */
	@Override
	public int compareTo(final DataElement arg0) {
		return Arrays.toString(this.path).compareTo(Arrays.toString(arg0.path));
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
		if (!(obj instanceof DataElement)) {
			return false;
		}
		final DataElement other = (DataElement) obj;
		if (this.modificationDate != other.modificationDate) {
			return false;
		}
		if (!Arrays.equals(this.path, other.path)) {
			return false;
		}
		if (this.size != other.size) {
			return false;
		}
		if (this.type != other.type) {
			return false;
		}
		return true;
	}

	/**
	 * Gets additional properties.
	 *
	 * @return the additional properties
	 */
	public Map<String, String> getAdditionalProperties() {
		return this.additionalProperties;
	}

	/**
	 * Gets an additional property.
	 *
	 * @param key the key
	 * @return the additional property (may be null if no additional properties or no such key)
	 */
	public String getAdditionalProperty(final String key) {
		if (this.additionalProperties == null) {
			return null;
		} else {
			return this.additionalProperties.get(key);
		}
	}

	/**
	 * Gets the modification date.
	 *
	 * @return the modification date
	 */
	public long getModificationDate() {
		return this.modificationDate;
	}

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Gets the path.
	 *
	 * @return the path
	 */
	public String[] getPath() {
		return this.path;
	}

	/**
	 * Gets the size.
	 *
	 * @return the size
	 */
	public long getSize() {
		return this.size;
	}

	/**
	 * Gets the type.
	 *
	 * @return the type
	 */
	public DataElementType getType() {
		return this.type;
	}

	/**
	 * Checks for additional properties.
	 *
	 * @return true, if present
	 */
	public boolean hasAdditionalProperties() {
		return (this.additionalProperties != null) && !this.additionalProperties.isEmpty();
	}

	/**
	 * Checks for an additional property.
	 *
	 * @param key the key
	 * @return true, if present
	 */
	public boolean hasAdditionalProperty(final String key) {
		if (this.additionalProperties == null) {
			return false;
		} else {
			return this.additionalProperties.containsKey(key);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + (int) (this.modificationDate ^ (this.modificationDate >>> 32));
		result = (prime * result) + Arrays.hashCode(this.path);
		result = (prime * result) + (int) (this.size ^ (this.size >>> 32));
		result = (prime * result) + ((this.type == null) ? 0 : this.type.hashCode());
		return result;
	}

	/**
	 * Sets the additional properties.
	 *
	 * @param additionalProperties the additional properties
	 */
	public void setAdditionalProperties(final Map<String, String> additionalProperties) {
		this.additionalProperties = additionalProperties;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "DataElement [name=" + this.name + ", path=" + Arrays.toString(this.path) + ", size=" + this.size + ", modificationDate=" + this.modificationDate + ", additionalProperties=" + this.additionalProperties + ", type=" + this.type + "]";
	}
}
