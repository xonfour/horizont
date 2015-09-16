package db.iface;

import java.util.Map;
import java.util.Set;

import framework.exception.DatabaseException;
import framework.model.DataElement;
import framework.model.type.DataElementType;

/**
 * Used to hold all the configuration and persistent data of a single component (control interface or module).
 *
 * @author Stefan Werner
 */
public interface ComponentConfigurationController {

	/**
	 * Deletes all of Element's extended properties.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @return true, if successful
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean deleteAllElementProperties(String domain, String[] path) throws IllegalArgumentException, DatabaseException;

	/**
	 * Deletes Element.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @return true, if successful
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean deleteElement(String domain, String[] path) throws IllegalArgumentException, DatabaseException;

	/**
	 * Deletes element domain and the complete Element structure contained.
	 *
	 * @param domain the domain
	 * @return true, if successful
	 * @throws DatabaseException the database exception
	 */
	public boolean deleteElementDomain(String domain) throws DatabaseException;

	/**
	 * Deletes Element's extended property.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @param propertyKey the property key
	 * @return true, if successful
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean deleteElementProperty(String domain, String[] path, String propertyKey) throws IllegalArgumentException, DatabaseException;

	/**
	 * Gets all child Elements of a parent element.
	 *
	 * IMPORTANT: Regarding the returned value, NULL is not the same as an empty Set! Example: An element with type FILE could have no children Set and would
	 * therefore return NULL, but a FOLDER type element could have an empty children Set that represents an empty folder. It is NOT up to the database to
	 * enforce that!
	 *
	 * @param domain the domain
	 * @param path the path
	 * @return A Set of child elements or NULL if no such parent element or if parent element does not contain children Set.
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public Set<DataElement> getChildElements(String domain, String[] path) throws IllegalArgumentException, DatabaseException;

	/**
	 * Gets the individual name of the Component.
	 *
	 * @return the component name
	 * @throws DatabaseException the database exception
	 */
	public String getComponentName() throws DatabaseException;

	/**
	 * Gets the access rights of the Component.
	 *
	 * @return the component rights
	 * @throws DatabaseException the database exception
	 */
	public int getComponentRights() throws DatabaseException;

	/**
	 * Gets the type of the Component.
	 *
	 * @return the component type
	 * @throws DatabaseException the database exception
	 */
	public String getComponentType() throws DatabaseException;

	/**
	 * Gets Element.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @return the element requested or NULL if no such element
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public DataElement getElement(String domain, String[] path) throws IllegalArgumentException, DatabaseException;

	/**
	 * Gets the Element domains currently set up.
	 *
	 * @return the element domains
	 * @throws DatabaseException the database exception
	 */
	public Set<String> getElementDomains() throws DatabaseException;

	/**
	 * Initializes Element domains, creates them if necessary.
	 *
	 * @param domains the domains
	 * @return true, if successful
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean initializeElementDomains(String... domains) throws IllegalArgumentException, DatabaseException;

	/**
	 * Invalidates this Component's configuration so it cannot be accessed/modified anymore.
	 */
	public void invalidateConfiguration();

	/**
	 * Checks if this Component's configuration is valid and can be accessed/modified.
	 *
	 * @return true, if is configuration valid
	 */
	public boolean isConfigurationValid();

	/**
	 * Checks if Element is currently marked.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @return True if marked, false if not marked or nonexistent.
	 * @throws DatabaseException the database exception
	 */
	public boolean isElementMarked(String domain, String[] path) throws DatabaseException;

	/**
	 * Moves Element from source domain/path to destination. Can also be used to rename Element. MUST NOT overwrite existing Elements.
	 *
	 * @param srcDomain the src domain
	 * @param srcPath the src path
	 * @param destDomain the dest domain
	 * @param destPath the dest path
	 * @return True if successfully moved/renamed, false if nonexistent of destination Element exists.
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean moveElement(String srcDomain, String[] srcPath, String destDomain, String[] destPath) throws IllegalArgumentException, DatabaseException;

	/**
	 * Stores Element in database, updates existing Element if necessary.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @param element the element
	 * @return true, if successful
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean storeElement(String domain, String[] path, DataElement element) throws IllegalArgumentException, DatabaseException;

	/**
	 * Unmark all elements.
	 *
	 * @param domain the domain
	 * @return true if updated successfully, false if Element does not exist.
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean unmarkAllElements(String domain) throws IllegalArgumentException, DatabaseException;

	/**
	 * Updates Element's marked state in database.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @param markState the mark state
	 * @return true if updated successfully, false if Element does not exist.
	 * @throws DatabaseException the database exception
	 */
	public boolean updateElementMarkState(String domain, String[] path, boolean markState) throws DatabaseException;

	/**
	 * Updates Element's modification date in database.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @param modificationDate the modification date
	 * @return true if updated successfully, false if Element does not exist.
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean updateElementModificationDate(String domain, String[] path, long modificationDate) throws IllegalArgumentException, DatabaseException;

	/**
	 * Updates Element's properties in database, merges/overwrites existing properties.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @param properties NULL keys or values are NOT allowed, but values may be empty (-> "")
	 * @return true if updated successfully, false if Element does not exist.
	 * @throws IllegalArgumentException or if properties contains NULL keys or values.
	 * @throws DatabaseException the database exception
	 */
	public boolean updateElementProperties(String domain, String[] path, Map<String, String> properties) throws IllegalArgumentException, DatabaseException;

	/**
	 * Updates Element's property in database, overwrites existing property. NULL for key or value is NOT allowed.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @param propertyKey the property key
	 * @param propertyValue may be empty (-> "")
	 * @return true if updated successfully
	 * @throws IllegalArgumentException or if key or value contains NULL.
	 * @throws DatabaseException the database exception
	 */
	public boolean updateElementProperty(String domain, String[] path, String propertyKey, String propertyValue) throws IllegalArgumentException, DatabaseException;

	/**
	 * Updates Element's size in database.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @param size the size
	 * @return true if updated successfully
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean updateElementSize(String domain, String[] path, long size) throws IllegalArgumentException, DatabaseException;

	/**
	 * Updates Element's type in database.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @param type the type
	 * @return true if updated successfully
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws DatabaseException the database exception
	 */
	public boolean updateElementType(String domain, String[] path, DataElementType type) throws IllegalArgumentException, DatabaseException;
}