package db.orientdb.model;

import javax.persistence.Id;
import javax.persistence.Version;

/**
 * Model for persisting a String in a Map.
 * <p>
 * TODO: There must be a better way than having an custom class for this!
 *
 * @author Stefan Werner
 */
public class OrientDBProperty {

	@Id
	private Object id;
	private String property;
	@Version
	private Object version;

	/**
	 * Gets the property.
	 *
	 * @return the property
	 */
	public String getProperty() {
		return this.property;
	}

	/**
	 * Sets the property.
	 *
	 * @param property the property to set
	 */
	public void setProperty(final String property) {
		this.property = property;
	}
}
