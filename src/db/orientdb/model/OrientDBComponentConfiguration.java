package db.orientdb.model;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Version;

/**
 * Model for persisting a component configuration.
 *
 * @author Stefan Werner
 */
public class OrientDBComponentConfiguration {

	@ManyToMany(cascade = { CascadeType.REMOVE })
	private Map<String, OrientDBElement> rootElements = new HashMap<String, OrientDBElement>();
	@Id
	private Object id;
	private String componentId;
	private String componentName;
	private String componentType;
	private int componentRights = 0;
	@Version
	private Object version;

	/**
	 * Gets the component ID.
	 *
	 * @return the component ID
	 */
	public String getComponentId() {
		return this.componentId;
	}

	/**
	 * Gets the component name.
	 *
	 * @return the component name
	 */
	public String getComponentName() {
		return this.componentName;
	}

	/**
	 * Gets the component rights.
	 *
	 * @return the component rights
	 */
	public int getComponentRights() {
		return this.componentRights;
	}

	/**
	 * Gets the component type.
	 *
	 * @return the component type
	 */
	public String getComponentType() {
		return this.componentType;
	}

	/**
	 * Gets the root elements.
	 *
	 * @return the root elements
	 */
	public Map<String, OrientDBElement> getRootElements() {
		return this.rootElements;
	}

	/**
	 * Sets the component id.
	 *
	 * @param componentId the new component id
	 */
	public void setComponentId(final String componentId) {
		this.componentId = componentId;
	}

	/**
	 * Sets the component name.
	 *
	 * @param componentName the componentName to set
	 */
	public void setComponentName(final String componentName) {
		this.componentName = componentName;
	}

	/**
	 * Sets the component rights.
	 *
	 * @param componentRights the component rights to set
	 */
	public void setComponentRights(final int componentRights) {
		this.componentRights = componentRights;
	}

	/**
	 * Sets the component type.
	 *
	 * @param componentType the new component type
	 */
	public void setComponentType(final String componentType) {
		this.componentType = componentType;
	}

	/**
	 * Sets all root elements.
	 *
	 * @param rootElements the root elements
	 */
	public void setRootElements(final Map<String, OrientDBElement> rootElements) {
		this.rootElements = rootElements;
	}
}
