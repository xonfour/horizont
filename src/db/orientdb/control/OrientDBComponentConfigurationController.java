package db.orientdb.control;

import helper.ObjectValidator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.orientechnologies.orient.core.tx.OTransaction.TXTYPE;
import com.orientechnologies.orient.object.db.OObjectDatabaseTx;

import db.iface.ComponentConfigurationController;
import db.orientdb.model.OrientDBComponentConfiguration;
import db.orientdb.model.OrientDBElement;
import db.orientdb.model.OrientDBProperty;
import framework.control.LogConnector;
import framework.exception.DatabaseException;
import framework.model.DataElement;
import framework.model.type.DataElementType;

/**
 * Implements {@link db.iface.ComponentConfigurationController} using OrientDB as the database backend.
 * <p>
 * TODO:<br>
 * - ALL exceptions need to be caught, wrapped with a databaseException and be rethrown.<br>
 * - Check OrientDB's threading/version management. Currently elements are researched recursively on every access. Existing elements already extracted from the
 * DB cannot be used as a starting point for further actions because the version management used within OrientDB doesn't play nicely with that scenario (it
 * sometimes leads to exceptions because of writing to outdated elements). This is OK for now but should definitively change in the future to give a little
 * speed boost here.<br>
 * - It would be nice to have data elements linked in both ways (-> parent element), but same problem as above.<br>
 *
 * @author Stefan Werner
 */
public final class OrientDBComponentConfigurationController implements ComponentConfigurationController {

	private final OrientDBController dbController;
	private final OrientDBComponentConfiguration dbModConfig;
	private final LogConnector log;
	private final ReentrantReadWriteLock modConfLock = new ReentrantReadWriteLock(true);
	private final ReadLock modConfReadLock = this.modConfLock.readLock();
	private final WriteLock modConfWriteLock = this.modConfLock.writeLock();
	private boolean valid = true;

	/**
	 * Instantiates a new orient db component configuration controller.
	 *
	 * @param dbController the db controller
	 * @param dbModConfig the db module configuration
	 * @param log the log
	 */
	public OrientDBComponentConfigurationController(final OrientDBController dbController, final OrientDBComponentConfiguration dbModConfig, final LogConnector log) {
		this.dbController = dbController;
		this.dbModConfig = dbModConfig;
		this.log = log;
	}

	/**
	 * Adds a database element by path.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @return the orient db element (may be null)
	 * @throws IllegalArgumentException if illegal arguments are given
	 * @throws DatabaseException if there is an error within the database
	 */
	private OrientDBElement addDBElementByPath(final String domain, final String[] path) throws IllegalArgumentException, DatabaseException {
		if (!ObjectValidator.checkPath(path)) {
			throw new IllegalArgumentException("invalid path");
		}
		OrientDBElement result = null;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement root = getDBRootElement(domain);
			if (root == null) {
				throw new IllegalArgumentException("invalid domain");
			}
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				OrientDBElement curElem = root;
				OrientDBElement parentElem = null;
				for (int i = 0; i < path.length; i++) {
					if (curElem.getChildren() == null) {
						curElem.setChildren(new HashMap<String, OrientDBElement>());
					}
					parentElem = curElem;
					curElem = parentElem.getChildren().get(path[i]);
					if (curElem == null) {
						curElem = db.newInstance(OrientDBElement.class);
						curElem.setName(path[i]);
						if (i < (path.length - 1)) {
							curElem.setChildren(new HashMap<String, OrientDBElement>());
						}
						db.save(curElem);
						parentElem.getChildren().put(path[i], curElem);
						db.save(parentElem);
					}
				}
				db.commit();
				result = curElem;
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				if (root != null) {
					db.reload(root);
				}
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/**
	 * Adds a property to an element.
	 *
	 * @param db the database instance
	 * @param element the element
	 * @param propertyKey the property key
	 * @param propertyValue the property value
	 */
	private void addDBElementProperty(final OObjectDatabaseTx db, final OrientDBElement element, final String propertyKey, final String propertyValue) {
		this.modConfWriteLock.lock();
		if (element.getAdditionalProperties() == null) {
			element.setAdditionalProperties(new HashMap<String, OrientDBProperty>());
		}
		final Map<String, OrientDBProperty> propMap = element.getAdditionalProperties();
		OrientDBProperty prop = propMap.get(propertyKey);
		if (prop != null) {
			prop.setProperty(propertyValue);
			db.save(prop);
		} else {
			prop = db.newInstance(OrientDBProperty.class);
			prop.setProperty(propertyValue);
			db.save(prop);
			propMap.put(propertyKey, prop);
		}
		this.modConfWriteLock.unlock();
	}

	/**
	 * Check validity of the current configuration. A configuration may become invalid for example because a module as been removed.
	 *
	 * @throws DatabaseException if there is an error within the database
	 */
	private void checkValidity() throws DatabaseException {
		if (!isConfigurationValid()) {
			throw new DatabaseException("configuration invalid");
		}
	}

	/**
	 * Convert an internal property map to a String-only variant.
	 *
	 * This is currently necessary because unfortunately OrientDB does not support pure String maps (TODO: needs more investigation)
	 *
	 * @param props the props
	 * @return the map
	 */
	private Map<String, String> convertPropertyMap(final Map<String, OrientDBProperty> props) {
		final Map<String, String> result = new HashMap<String, String>();
		for (final String key : props.keySet()) {
			final OrientDBProperty prop = props.get(key);
			if ((prop != null) && (prop.getProperty() != null) && !prop.getProperty().isEmpty()) {
				result.put(key, prop.getProperty());
			}
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#removeAllElementPropertiesByPath(java.lang.String, java.lang.String[]) */
	@Override
	public boolean deleteAllElementProperties(final String domain, final String[] path) throws IllegalArgumentException, DatabaseException {
		checkValidity();
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			OrientDBElement element = null;
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				element = getDBElementByPath(domain, path);
				if ((element != null) && (element.getAdditionalProperties() != null) && !element.getAdditionalProperties().isEmpty()) {
					for (final OrientDBProperty prop : element.getAdditionalProperties().values()) {
						db.delete(prop);
					}
					element.getAdditionalProperties().clear();
					db.save(element);
					result = true;
				}
				db.commit();
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				result = false;
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#deleteFSElementRecord(java.lang.String, java.lang.String[]) */
	@Override
	public boolean deleteElement(final String domain, final String[] path) throws IllegalArgumentException, DatabaseException {
		checkValidity();
		if ((domain == null) || domain.isEmpty() || !ObjectValidator.checkPath(path)) {
			throw new IllegalArgumentException("invalid domain or path");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			final OrientDBElement element = getDBElementByPath(domain, path);
			final OrientDBElement parent = getParent(domain, path);
			if ((element == null) || (parent == null)) {
				this.modConfWriteLock.unlock();
				return false;
			}
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				final Map<String, OrientDBElement> children = parent.getChildren();
				if (children != null) {
					children.remove(element.getName());
				}
				db.save(parent);
				db.delete(element);
				db.commit();
				result = true;
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				result = false;
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#removeFSElementDomain(java.lang.String) */
	@Override
	public boolean deleteElementDomain(final String domain) throws DatabaseException {
		if (domain == null) {
			throw new IllegalArgumentException("invalid domain");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement element = getDBRootElement(domain);
			if (element == null) {
				throw new IllegalArgumentException("invalid domain");
			}
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				if (element != null) {
					this.dbModConfig.getRootElements().remove(domain);
					db.save(this.dbModConfig);
					db.commit();
					result = true;
				}
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				result = false;
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#removeElementPropertyByPath(java.lang.String, java.lang.String[], java.lang.String) */
	@Override
	public boolean deleteElementProperty(final String domain, final String[] path, final String propertyKey) throws IllegalArgumentException, DatabaseException {
		if (propertyKey == null) {
			throw new IllegalArgumentException("invalid propertyKey");
		}
		boolean result = false;
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		this.modConfWriteLock.lock();
		try {
			checkValidity();
			OrientDBElement element = null;
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				element = getDBElementByPath(domain, path);
				if ((element != null) && (element.getAdditionalProperties() != null)) {
					final OrientDBProperty prop = element.getAdditionalProperties().get(propertyKey);
					if (prop != null) {
						element.getAdditionalProperties().remove(propertyKey);
						db.save(element);
						result = true;
					}
				}
				db.commit();
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				result = false;
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see framework.iface.ModuleConfigurationController#getChildFSElements(java.lang.String, java.lang.String[]) */
	@Override
	public Set<DataElement> getChildElements(final String domain, final String[] path) throws IllegalArgumentException, DatabaseException {
		Set<DataElement> result = null;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement element = getDBElementByPath(domain, path);
			Map<String, OrientDBElement> children = null;
			if ((element != null) && ((children = element.getChildren()) != null)) {
				result = new HashSet<DataElement>();
				for (final String name : children.keySet()) {
					final OrientDBElement child = children.get(name);
					if ((child != null) && child.getName().equals(name)) {
						if (ObjectValidator.checkDataElementValues(child.getModificationDate(), child.getSize())) {
							final String[] childPath = Arrays.copyOf(path, path.length + 1);
							childPath[path.length] = name;
							if (child.getAdditionalProperties() != null) {
								result.add(new DataElement(childPath, child.getType(), child.getSize(), child.getModificationDate(), convertPropertyMap(child.getAdditionalProperties())));
							} else {
								result.add(new DataElement(childPath, child.getType(), child.getSize(), child.getModificationDate()));
							}
						}
					} else {
						throw new DatabaseException("FSElement structure corrupt");
					}
				}
			}
		} finally {
			db.close();
			this.modConfReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.ModuleConfigurationController#getModuleName() */
	@Override
	public String getComponentName() throws DatabaseException {
		String result = null;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		result = this.dbModConfig.getComponentName();
		db.close();
		this.modConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.ModuleConfigurationController#getModuleRights() */
	@Override
	public int getComponentRights() throws DatabaseException {
		int result = 0;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		result = this.dbModConfig.getComponentRights();
		db.close();
		this.modConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see framework.iface.ModuleConfigurationController#getModuleType() */
	@Override
	public String getComponentType() throws DatabaseException {
		String result = null;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			result = this.dbModConfig.getComponentType();
		} finally {
			db.close();
			this.modConfReadLock.unlock();
		}
		return result;
	}

	/**
	 * Gets the DB element by path.
	 *
	 * @param domain the domain
	 * @param path the path
	 * @return the DB element by path
	 * @throws IllegalArgumentException if illegal arguments are given
	 * @throws DatabaseException if there is an error within the database
	 */
	private OrientDBElement getDBElementByPath(final String domain, final String[] path) throws IllegalArgumentException, DatabaseException {
		if (!ObjectValidator.checkPath(path)) {
			throw new IllegalArgumentException("invalid path");
		}
		OrientDBElement result = null;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement root = getDBRootElement(domain);
			if (root == null) {
				throw new IllegalArgumentException("invalid domain");
			}
			OrientDBElement curElem = root;
			for (int i = 0; i < path.length; i++) {
				if ((curElem == null) || (curElem.getChildren() == null)) {
					curElem = null;
					break;
				}
				curElem = curElem.getChildren().get(path[i]);
			}
			result = curElem;
		} finally {
			db.close();
			this.modConfReadLock.unlock();
		}
		return result;
	}

	/**
	 * Gets the DB root element.
	 *
	 * @param domain the domain
	 * @return the root element
	 * @throws IllegalArgumentException if illegal arguments are given
	 * @throws DatabaseException if there is an error within the database
	 */
	private OrientDBElement getDBRootElement(final String domain) throws IllegalArgumentException, DatabaseException {
		if (domain == null) {
			throw new IllegalArgumentException("invalid domain");
		}
		OrientDBElement result = null;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			result = this.dbModConfig.getRootElements().get(domain);
		} finally {
			db.close();
			this.modConfReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see framework.iface.ModuleConfigurationController#getFSElement(java.lang.String, java.lang.String[]) */
	@Override
	public DataElement getElement(final String domain, final String[] path) throws IllegalArgumentException, DatabaseException {
		DataElement result = null;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement element = getDBElementByPath(domain, path);
			if (element != null) {
				if (ObjectValidator.checkDataElementValues(element.getModificationDate(), element.getSize())) {
					if (element.getAdditionalProperties() != null) {
						result = new DataElement(path, element.getType(), element.getSize(), element.getModificationDate(), convertPropertyMap(element.getAdditionalProperties()));
					} else {
						result = new DataElement(path, element.getType(), element.getSize(), element.getModificationDate());
					}
				}
			}
		} finally {
			db.close();
			this.modConfReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#getFSElementDomains() */
	@Override
	public Set<String> getElementDomains() throws DatabaseException {
		Set<String> result;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			result = this.dbModConfig.getRootElements().keySet();
		} finally {
			db.close();
			this.modConfReadLock.unlock();
		}
		return result;
	}

	/**
	 * Gets the DB parent element of a given child path.
	 *
	 * @param domain the domain
	 * @param childPath the child path
	 * @return the parent
	 * @throws IllegalArgumentException if illegal arguments are given
	 * @throws DatabaseException if there is an error within the database
	 */
	private OrientDBElement getParent(final String domain, final String[] childPath) throws IllegalArgumentException, DatabaseException {
		if (childPath.length == 0) {
			return null;
		}
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		OrientDBElement result = null;
		try {
			if (childPath.length == 1) {
				result = getDBRootElement(domain);
			} else {
				result = getDBElementByPath(domain, Arrays.copyOfRange(childPath, 0, childPath.length - 1));
			}
		} finally {
			db.close();
			this.modConfReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.ComponentConfigurationController#initializeElementDomains(java.lang.String[]) */
	@Override
	public boolean initializeElementDomains(final String... domains) throws IllegalArgumentException, DatabaseException {
		this.modConfWriteLock.lock();
		for (final String domain : domains) {
			if ((domain == null) || domain.isEmpty()) {
				this.modConfWriteLock.unlock();
				return false;
			}
			if (updateElementDomain(domain) == null) {
				return false;
			}
		}
		this.modConfWriteLock.unlock();
		return true;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#invalidate() */
	@Override
	public void invalidateConfiguration() {
		this.modConfWriteLock.lock();
		this.valid = false;
		this.modConfWriteLock.unlock();
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#isValid() */
	@Override
	public boolean isConfigurationValid() {
		boolean result;
		this.modConfReadLock.lock();
		result = this.valid;
		this.modConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#isFSElementMarked(java.lang.String, java.lang.String[]) */
	@Override
	public boolean isElementMarked(final String domain, final String[] path) throws DatabaseException {
		if (!ObjectValidator.checkPath(path)) {
			throw new IllegalArgumentException("invalid path");
		}
		boolean result = false;
		this.modConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement element = getDBElementByPath(domain, path);
			if (element != null) {
				result = element.isMarked();
			}
		} finally {
			db.close();
			this.modConfReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.ComponentConfigurationController#moveElement(java.lang.String, java.lang.String[], java.lang.String, java.lang.String[]) */
	@Override
	public boolean moveElement(final String srcDomain, final String[] srcPath, final String destDomain, final String[] destPath) throws IllegalArgumentException, DatabaseException {
		if ((srcPath.length < 1) || (destPath.length < 1) || !ObjectValidator.checkPath(srcPath) || !ObjectValidator.checkPath(destPath) || (srcDomain == null) || srcDomain.isEmpty() || (destDomain == null) || destDomain.isEmpty()) {
			throw new IllegalArgumentException("illegal srcPath and/or destPath");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				final OrientDBElement dbSrcElement = getDBElementByPath(srcDomain, srcPath);
				final OrientDBElement dbDestElement = getDBElementByPath(destDomain, destPath);
				if ((dbSrcElement != null) && (dbDestElement == null)) {
					final OrientDBElement dbSrcParentElement = getParent(srcDomain, srcPath);
					final OrientDBElement dbDestParentElement = getDBElementByPath(destDomain, Arrays.copyOfRange(destPath, 0, destPath.length - 1));
					if ((dbSrcParentElement != null) && (dbDestParentElement != null)) {
						if (dbSrcParentElement.getChildren().remove(dbSrcElement.getName()) != null) {
							if (dbDestParentElement.getChildren() == null) {
								dbDestParentElement.setChildren(new HashMap<String, OrientDBElement>());
							}
							final String newName = destPath[destPath.length - 1];
							dbSrcElement.setName(newName);
							dbDestParentElement.getChildren().put(newName, dbSrcElement);
							db.save(dbDestParentElement);
							db.save(dbSrcElement);
							db.save(dbSrcParentElement);
							db.commit();
							result = true;
						}
					}
				}
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				if (e instanceof IllegalArgumentException) {
					throw e;
				}
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see framework.iface.ModuleConfigurationController#setElement(java.lang.String, java.lang.String[], framework.model.FSElementType, long, long) */
	@Override
	public boolean storeElement(final String domain, final String[] path, final DataElement element) throws IllegalArgumentException, DatabaseException {
		if (!ObjectValidator.checkDataElement(element)) {
			throw new IllegalArgumentException("illegal element");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			try {
				OrientDBElement dbElement = getDBElementByPath(domain, path);
				db.begin(TXTYPE.OPTIMISTIC);
				if (dbElement == null) {
					dbElement = addDBElementByPath(domain, path);
				}
				dbElement.setType(element.getType());
				dbElement.setModificationDate(element.getModificationDate());
				dbElement.setSize(element.getSize());
				if (element.hasAdditionalProperties()) {
					final Map<String, String> props = element.getAdditionalProperties();
					for (final String s : props.keySet()) {
						final String val = props.get(s);
						addDBElementProperty(db, dbElement, s, val);
					}
				}
				db.save(dbElement);
				db.commit();
				result = true;
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				if (e instanceof IllegalArgumentException) {
					throw e;
				}
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#unmarkAllFSElements(java.lang.String) */
	@Override
	public boolean unmarkAllElements(final String domain) throws IllegalArgumentException, DatabaseException {
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement root = getDBRootElement(domain);
			if (root == null) {
				throw new IllegalArgumentException("invalid domain");
			}
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				unmarkAllElementsRecursion(root, db);
				db.commit();
				result = true;
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				if (root != null) {
					db.reload(root);
				}
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/**
	 * Unmarks all DB elements (internal recursion).
	 *
	 * @param element the element
	 * @param db the database instance
	 */
	private void unmarkAllElementsRecursion(final OrientDBElement element, final OObjectDatabaseTx db) {
		if (element.getChildren() != null) {
			for (final OrientDBElement child : element.getChildren().values()) {
				unmarkAllElementsRecursion(child, db);
			}
		}
		if (element.isMarked()) {
			if (((element.getChildren() == null) || element.getChildren().isEmpty()) && ((element.getAdditionalProperties() == null) || element.getAdditionalProperties().isEmpty())) {
				db.delete(element);
			} else {
				element.setMarked(false);
				db.save(element);
			}
		}
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#setFSElementMarkState(java.lang.String, java.lang.String[], boolean) */
	@Override
	public boolean updateElementMarkState(final String domain, final String[] path, final boolean markState) throws DatabaseException {
		if (!ObjectValidator.checkPath(path)) {
			throw new IllegalArgumentException("invalid path");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement element = getDBElementByPath(domain, path);
			if (element != null) {
				if (!markState && (((element.getChildren() == null) || element.getChildren().isEmpty()) && ((element.getAdditionalProperties() == null) || element.getAdditionalProperties().isEmpty()))) {
					db.delete(element);
				} else {
					element.setMarked(markState);
					db.save(element);
				}
				result = true;
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see framework.iface.ModuleConfigurationController#setElementModificationDate(java.lang.String, java.lang.String[], long) */
	@Override
	public boolean updateElementModificationDate(final String domain, final String[] path, final long modificationDate) throws IllegalArgumentException, DatabaseException {
		if (!ObjectValidator.checkDataElementValues(modificationDate, 0)) {
			throw new IllegalArgumentException("illegal modificationDate");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				final OrientDBElement element = getDBElementByPath(domain, path);
				if (element != null) {
					element.setModificationDate(modificationDate);
					db.save(element);
					db.commit();
					result = true;
				}
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				if (e instanceof IllegalArgumentException) {
					throw e;
				}
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#setElementPropertiesByPath(java.lang.String, java.lang.String[], java.util.Map) */
	@Override
	public boolean updateElementProperties(final String domain, final String[] path, final Map<String, String> properties) throws IllegalArgumentException, DatabaseException {
		if (!ObjectValidator.checkMapForNullKeysOrValues(properties)) {
			throw new IllegalArgumentException("invalid properties");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			final OrientDBElement element = getDBElementByPath(domain, path);
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				if (element != null) {
					for (final String s : properties.keySet()) {
						final String val = properties.get(s);
						addDBElementProperty(db, element, s, val);
					}
					db.save(element);
					db.commit();
					result = true;
				}
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				result = false;
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.orientdb.control.ModuleConfigurationController#setElementPropertyByPath(java.lang.String, java.lang.String[], java.lang.String,
	 * java.lang.String) */
	@Override
	public boolean updateElementProperty(final String domain, final String[] path, final String propertyKey, final String propertyValue) throws IllegalArgumentException, DatabaseException {
		if ((propertyKey == null) || (propertyValue == null)) {
			throw new IllegalArgumentException("invalid propertyKey/propertyValue");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			OrientDBElement element = null;
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				element = getDBElementByPath(domain, path);
				if (element != null) {
					addDBElementProperty(db, element, propertyKey, propertyValue);
					db.save(element);
					db.commit();
					result = true;
				}
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				result = false;
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see framework.iface.ModuleConfigurationController#setElementSize(java.lang.String, java.lang.String[], long) */
	@Override
	public boolean updateElementSize(final String domain, final String[] path, final long size) throws IllegalArgumentException, DatabaseException {
		if (!ObjectValidator.checkDataElementValues(0, size)) {
			throw new IllegalArgumentException("illegal size");
		}
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				final OrientDBElement element = getDBElementByPath(domain, path);
				if (element != null) {
					element.setSize(size);
					db.save(element);
					db.commit();
					result = true;
				}
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				if (e instanceof IllegalArgumentException) {
					throw e;
				}
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see framework.iface.ModuleConfigurationController#setElementType(java.lang.String, java.lang.String[], framework.model.FSElementType) */
	@Override
	public boolean updateElementType(final String domain, final String[] path, final DataElementType type) throws IllegalArgumentException, DatabaseException {
		boolean result = false;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				final OrientDBElement element = getDBElementByPath(domain, path);
				if (element != null) {
					element.setType(type);
					db.save(element);
					db.commit();
					result = true;
				}
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				if (e instanceof IllegalArgumentException) {
					throw e;
				}
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}

	/**
	 * Updates an element domain, creates it if neccessary.
	 *
	 * @param domain the domain
	 * @return the orient db element
	 * @throws DatabaseException if there is an error within the database
	 */
	private OrientDBElement updateElementDomain(final String domain) throws DatabaseException {
		if (domain == null) {
			throw new IllegalArgumentException("invalid domain");
		}
		OrientDBElement result = null;
		this.modConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			checkValidity();
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				if (!this.dbModConfig.getRootElements().containsKey(domain)) {
					final OrientDBElement root = db.newInstance(OrientDBElement.class);
					root.setName(domain);
					root.setType(DataElementType.FOLDER);
					db.save(root);
					this.dbModConfig.getRootElements().put(domain, root);
					db.save(this.dbModConfig);
					db.commit();
					result = root;
				} else {
					result = this.dbModConfig.getRootElements().get(domain);
				}
			} catch (final Exception e) {
				this.log.log(e);
				db.rollback();
				result = null;
			}
		} finally {
			db.close();
			this.modConfWriteLock.unlock();
		}
		return result;
	}
}