package db.orientdb.model;

import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Version;

import framework.model.type.DataElementType;

/**
 * Model for persisting a data element.
 *
 * @author Stefan Werner
 */
public class OrientDBElement {

	@ManyToMany(cascade = { CascadeType.REMOVE })
	private Map<String, OrientDBProperty> additionalProperties; // individual properties of the element
	@ManyToMany(cascade = { CascadeType.REMOVE })
	private Map<String, OrientDBElement> children; // child elements
	@Id
	private Object id;
	private boolean marked = false; // TODO: Currently unused, could be used to enable semi-automatic database cleanup.
	private long modificationDate = 0;
	private String name; // element name without path, null for root element
	private long size = 0;
	private DataElementType type = DataElementType.NONEXISTENT_OR_UNKNOWN;
	@Version
	private Object version;

	/**
	 * Gets additional properties.
	 *
	 * @return the additional properties
	 */
	public Map<String, OrientDBProperty> getAdditionalProperties() {
		return this.additionalProperties;
	}

	/**
	 * Gets the children.
	 *
	 * @return the children
	 */
	public Map<String, OrientDBElement> getChildren() {
		return this.children;
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
	 * Checks if element is marked.
	 *
	 * @return true, if marked
	 */
	public boolean isMarked() {
		return this.marked;
	}

	/**
	 * Sets the additional properties.
	 *
	 * @param additionalProperties the additional properties to set
	 */
	public void setAdditionalProperties(final Map<String, OrientDBProperty> additionalProperties) {
		this.additionalProperties = additionalProperties;
	}

	/**
	 * Sets the children.
	 *
	 * @param children the children
	 */
	public void setChildren(final Map<String, OrientDBElement> children) {
		this.children = children;
	}

	/**
	 * Sets marked.
	 *
	 * @param mark set to true to mark
	 */
	public void setMarked(final boolean mark) {
		this.marked = mark;
	}

	/**
	 * Sets the modification date.
	 *
	 * @param modificationDate the modification date to set
	 */
	public void setModificationDate(final long modificationDate) {
		this.modificationDate = modificationDate;
	}

	/**
	 * Sets the name.
	 *
	 * @param name the new name
	 */
	public void setName(final String name) {
		this.name = name;
	}

	/**
	 * Sets the size.
	 *
	 * @param size the size to set
	 */
	public void setSize(final long size) {
		this.size = size;
	}

	/**
	 * Sets the type.
	 *
	 * @param type the type to set
	 */
	public void setType(final DataElementType type) {
		this.type = type;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "OrientDBFSElement [getChildren()=" + getChildren() + ", getModificationDate()=" + getModificationDate() + ", getName()=" + getName() + ", getAdditionalProperties()=" + getAdditionalProperties() + ", getSize()=" + getSize() + ", getType()=" + getType() + ", isMarked()=" + isMarked() + "]";
	}
}
