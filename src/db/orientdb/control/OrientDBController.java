package db.orientdb.control;

import java.io.File;

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.ODatabaseThreadLocalFactory;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.object.db.OObjectDatabaseTx;

import db.iface.BaseConfigurationController;
import db.orientdb.model.OrientDBBaseConfiguration;
import db.orientdb.model.OrientDBComponentConfiguration;
import db.orientdb.model.OrientDBElement;
import db.orientdb.model.OrientDBPortTuple;
import db.orientdb.model.OrientDBProperty;
import framework.control.LogConnector;
import framework.exception.DatabaseException;
import framework.model.event.type.LogEventLevelType;

/**
 * Initializes and provides the database instance.
 * <p>
 * TODO: Consider moving to Graph API. This Javassist stuff (POJO to record matching) seems to be broken. 
 *
 * @author Stefan Werner
 */
public class OrientDBController {

	private static final String DB_LOCATION_PREFIX = "plocal:";
	private static final String DB_NAME = "orientdb";

	private OrientDBBaseConfiguration config = null;
	private OrientDBBaseConfigurationController configController = null;
	private final String databaseTypeAndLocation;
	private final LogConnector logConnector;
	private OPartitionedDatabasePool pool;

	/**
	 * Instantiates a new orient db controller.
	 *
	 * @param logConnector the log connector
	 * @param localDatabaseBaseLocation the local database base location
	 * @throws DatabaseException the database exception
	 */
	public OrientDBController(final LogConnector logConnector, String localDatabaseBaseLocation) throws DatabaseException {
		this.logConnector = logConnector;
		if (!localDatabaseBaseLocation.endsWith(File.separator)) {
			localDatabaseBaseLocation += File.separator;
		}
		this.databaseTypeAndLocation = OrientDBController.DB_LOCATION_PREFIX + localDatabaseBaseLocation + OrientDBController.DB_NAME;
		logConnector.log(LogEventLevelType.DEBUG, "initializing database...");
		final boolean initialized = initialize();
		if (initialized) {
			logConnector.log(LogEventLevelType.DEBUG, "ready");
		} else {
			logConnector.log(LogEventLevelType.ERROR, "unable to open/create");
			throw new DatabaseException();
		}
	}

	/**
	 * Gets the base configuration.
	 *
	 * @return the configuration
	 * @throws DatabaseException if there is an error within the database
	 */
	public BaseConfigurationController getConfig() throws DatabaseException {
		if (this.configController == null) {
			try {
				this.configController = new OrientDBBaseConfigurationController(this.config, this, this.logConnector);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
				throw new DatabaseException(e);
			}
		}
		return this.configController;
	}

	/**
	 * Gets a database instance.
	 *
	 * @return the DB instance
	 */
	public OObjectDatabaseTx getDBInstance() {
		final OObjectDatabaseTx db = new OObjectDatabaseTx(pool.acquire());
		db.getEntityManager().registerEntityClass(OrientDBBaseConfiguration.class);
		db.getEntityManager().registerEntityClass(OrientDBElement.class);
		db.getEntityManager().registerEntityClass(OrientDBComponentConfiguration.class);
		db.getEntityManager().registerEntityClass(OrientDBPortTuple.class);
		db.getEntityManager().registerEntityClass(OrientDBProperty.class);
		return db;
	}

	/**
	 * Initializes the database.
	 *
	 * @return true, if successful
	 */
	private boolean initialize() {
		boolean result = false;
		try {
			final OObjectDatabaseTx rawDB = new OObjectDatabaseTx(this.databaseTypeAndLocation);
			if (!rawDB.exists()) {
				rawDB.create();
				rawDB.getEntityManager().registerEntityClass(OrientDBBaseConfiguration.class);
			}
			rawDB.close();
			ODatabaseRecordThreadLocal.INSTANCE.remove();
			pool = new OPartitionedDatabasePool(OrientDBController.this.databaseTypeAndLocation, "admin", "admin");			
			final ODatabaseThreadLocalFactory customFactory = new ODatabaseThreadLocalFactory() {

				@Override
				public ODatabaseDocumentInternal getThreadDatabase() {
					// TODO: Seems to be broken or is not called anymore by instance.
					final OObjectDatabaseTx db = new OObjectDatabaseTx(pool.acquire());
					db.getEntityManager().registerEntityClass(OrientDBBaseConfiguration.class);
					db.getEntityManager().registerEntityClass(OrientDBElement.class);
					db.getEntityManager().registerEntityClass(OrientDBComponentConfiguration.class);
					db.getEntityManager().registerEntityClass(OrientDBPortTuple.class);
					db.getEntityManager().registerEntityClass(OrientDBProperty.class);
					return db.getUnderlying();
				}
			};
			Orient.instance().registerThreadDatabaseFactory(customFactory);
			// TODO: Only save dirty objects (-> db.setDirty(pojo)), use?
			// OGlobalConfiguration.OBJECT_SAVE_ONLY_DIRTY.setValue(true);
			final OObjectDatabaseTx db = getDBInstance();
			try {
				final long v1ConfigCount = db.countClass("OrientDBBaseConfiguration");
				if (v1ConfigCount > 1) {
					this.logConnector.log(LogEventLevelType.ERROR, "database corrupt, more than one base configurations");
				} else if (v1ConfigCount == 1) {
					this.logConnector.log(LogEventLevelType.DEBUG, "database ready, loading base config...");
					this.config = db.browseClass(OrientDBBaseConfiguration.class).next();
					result = true;
				} else if (v1ConfigCount == 0) {
					this.logConnector.log(LogEventLevelType.DEBUG, "new database, initializing base config...");
					this.config = db.newInstance(OrientDBBaseConfiguration.class);
					db.save(this.config);
					result = true;
				}
			} finally {
				db.close();
			}
			return result;
		} catch (final Exception e) {
			this.logConnector.log(e);
		}
		return result;
	}
}