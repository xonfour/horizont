package db.orientdb.control;

import helper.ObjectValidator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.orientechnologies.orient.core.tx.OTransaction.TXTYPE;
import com.orientechnologies.orient.object.db.OObjectDatabaseTx;

import db.iface.BaseConfigurationController;
import db.iface.ComponentConfigurationController;
import db.orientdb.model.GsonOrientDBExclusionStrategy;
import db.orientdb.model.OrientDBBaseConfiguration;
import db.orientdb.model.OrientDBComponentConfiguration;
import db.orientdb.model.OrientDBPortTuple;
import framework.constants.ControlInterfaceRight;
import framework.control.LogConnector;
import framework.exception.DatabaseException;
import framework.model.event.type.LogEventLevelType;
import framework.model.summary.BaseConfigurationSummary;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.ControlInterfaceSummary;
import framework.model.summary.ModuleSummary;
import framework.model.summary.PortSummary;
import framework.model.type.PortType;

/**
 * Implements {@link db.iface.BaseConfigurationController} using OrientDB as the database backend.
 * <p>
 * TODO: Currently the complete database structure has to be in memory for im-/export to work. There should be a better solution.
 *
 * @author Stefan Werner
 */
public final class OrientDBBaseConfigurationController implements BaseConfigurationController {

	private final ReentrantReadWriteLock baseConfLock = new ReentrantReadWriteLock(true);
	private final ReadLock baseConfReadLock = this.baseConfLock.readLock();
	private final WriteLock baseConfWriteLock = this.baseConfLock.writeLock();
	private final Map<String, OrientDBComponentConfigurationController> ciConfigurations = new ConcurrentHashMap<String, OrientDBComponentConfigurationController>();
	private final OrientDBBaseConfiguration dbBaseConfig;
	private final OrientDBController dbController;
	private final LogConnector logConnector;
	private final Map<String, OrientDBComponentConfigurationController> moduleConfigurations = new ConcurrentHashMap<String, OrientDBComponentConfigurationController>();

	/**
	 * Instantiates a new orient db base configuration controller.
	 *
	 * @param dbBaseConfig the db base configuration to read from and write to
	 * @param dbController the db controller
	 * @param logConnector the log connector
	 * @throws DatabaseException if there is an error within the database
	 * @throws IllegalArgumentException if illegal arguments are given
	 */
	public OrientDBBaseConfigurationController(final OrientDBBaseConfiguration dbBaseConfig, final OrientDBController dbController, final LogConnector logConnector) throws DatabaseException, IllegalArgumentException {
		if ((dbBaseConfig == null) || (dbController == null) || (logConnector == null)) {
			throw new IllegalArgumentException();
		}
		this.dbBaseConfig = dbBaseConfig;
		this.dbController = dbController;
		this.logConnector = logConnector;
	}

	@Override
	public ComponentConfigurationController addCIConfiguration(final String ciType, final String ciId, final String ciName, final int rights) {
		this.logConnector.log(LogEventLevelType.DEBUG, "adding ui configuration for " + ciId + "/" + ciType);
		OrientDBComponentConfigurationController result = null;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (!this.dbBaseConfig.getCiConfigurations().containsKey(ciId)) {
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				final OrientDBComponentConfiguration dbCompConf = db.newInstance(OrientDBComponentConfiguration.class);
				dbCompConf.setComponentType(ciType);
				dbCompConf.setComponentId(ciId);
				dbCompConf.setComponentName(ciName);
				dbCompConf.setComponentRights(rights);
				db.save(dbCompConf);
				this.dbBaseConfig.getCiConfigurations().put(ciId, dbCompConf);
				db.save(this.dbBaseConfig);
				result = new OrientDBComponentConfigurationController(this.dbController, dbCompConf, this.logConnector);
				db.commit();
				this.ciConfigurations.put(ciId, result);
			} catch (final Exception e) {
				this.logConnector.log(e);
				this.dbBaseConfig.getCiConfigurations().remove(ciId);
				db.rollback();
			}
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#addModuleConfiguration(java.lang.String) */
	@Override
	public ComponentConfigurationController addModuleConfiguration(final String moduleType, final String moduleId, final String moduleName, final int rights) {
		this.logConnector.log(LogEventLevelType.DEBUG, "adding module configuration for " + moduleId + "/" + moduleType);
		this.logConnector.log(LogEventLevelType.DEBUG, "adding module configuration for " + moduleId + "/" + moduleType);
		OrientDBComponentConfigurationController result = null;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (!this.dbBaseConfig.getModuleConfigurations().containsKey(moduleId)) {
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				final OrientDBComponentConfiguration dbModConf = db.newInstance(OrientDBComponentConfiguration.class);
				dbModConf.setComponentType(moduleType);
				dbModConf.setComponentId(moduleId);
				dbModConf.setComponentName(moduleName);
				dbModConf.setComponentRights(rights);
				db.save(dbModConf);
				this.dbBaseConfig.getModuleConfigurations().put(moduleId, dbModConf);
				db.save(this.dbBaseConfig);
				result = new OrientDBComponentConfigurationController(this.dbController, dbModConf, this.logConnector);
				db.commit();
				this.moduleConfigurations.put(moduleId, result);
			} catch (final Exception e) {
				this.logConnector.log(e);
				this.dbBaseConfig.getModuleConfigurations().remove(moduleId);
				db.rollback();
			}
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#addPortConnection(framework.model.config.PortTuple) */
	@Override
	public boolean addOrUpdatePortConnection(final ConnectionSummary connectionSummary) {
		if (connectionSummary == null) {
			return false;
		}
		boolean result = false;
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		this.baseConfWriteLock.lock();
		try {
			db.begin(TXTYPE.OPTIMISTIC);
			Set<OrientDBPortTuple> connections = this.dbBaseConfig.getPortConnections();
			if (connections == null) {
				this.dbBaseConfig.setPortConnections(new HashSet<OrientDBPortTuple>());
				connections = this.dbBaseConfig.getPortConnections();
			}
			OrientDBPortTuple newTuple = convertToTuple(connectionSummary);

			for (final OrientDBPortTuple dbTuple : this.dbBaseConfig.getPortConnections()) {
				if (newTuple.equals(dbTuple)) {
					this.dbBaseConfig.getPortConnections().remove(dbTuple);
					break;
				}
			}

			if (newTuple != null) {
				if (!connections.contains(newTuple)) {
					newTuple = db.save(newTuple);
					connections.add(newTuple);
					db.save(this.dbBaseConfig);
					db.commit();
					result = true;
				}
			}
		} catch (final Exception e) {
			this.logConnector.log(e);
			db.rollback();
			db.reload(this.dbBaseConfig);
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/**
	 * Converts a port tuple to a connection summary.
	 *
	 * @param dbPortTuple the db port tuple
	 * @return the connection summary
	 * @throws IllegalArgumentException if illegal arguments are given
	 */
	private ConnectionSummary convertToSummary(final OrientDBPortTuple dbPortTuple) throws IllegalArgumentException {
		if (dbPortTuple == null) {
			throw new IllegalArgumentException("invalid dbPortTuple");
		}
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		final PortSummary prosumerPortSummary = new PortSummary(dbPortTuple.getProsumerModuleId(), PortType.PROSUMER, dbPortTuple.getProsumerPortId(), dbPortTuple.getProsumerMaxConnections(), -1);
		final PortSummary providerPortSummary = new PortSummary(dbPortTuple.getProviderModuleId(), PortType.PROVIDER, dbPortTuple.getProviderPortId(), dbPortTuple.getProviderMaxConnections(), -1);
		db.close();
		return new ConnectionSummary(prosumerPortSummary, providerPortSummary, false, dbPortTuple.getPriority(), 0, System.currentTimeMillis());
	}

	/**
	 * Converts a connection summary to a port tuple.
	 *
	 * @param connectionSummary the connection summary
	 * @return the orient db port tuple
	 * @throws IllegalArgumentException if illegal arguments are given
	 */
	private OrientDBPortTuple convertToTuple(final ConnectionSummary connectionSummary) throws IllegalArgumentException {
		if ((connectionSummary == null) || !ObjectValidator.checkConnectionSummary(connectionSummary)) {
			throw new IllegalArgumentException("invalid portTuple");
		}
		return new OrientDBPortTuple(connectionSummary.getProsumerPortSummary().getModuleId(), connectionSummary.getProsumerPortSummary().getPortId(), connectionSummary.getProsumerPortSummary().getMaxConnections(), connectionSummary.getProviderPortSummary().getModuleId(), connectionSummary.getProviderPortSummary().getPortId(), connectionSummary.getProviderPortSummary().getMaxConnections(), connectionSummary.getPriority());
	}

	/**
	 * Deserializes a new base configuration.
	 *
	 * @param in the input stream to read base configuration from
	 * @return the orient db base configuration
	 */
	private OrientDBBaseConfiguration deserializeBaseConfiguration(final InputStream in) {
		try {
			final Gson gson = new GsonBuilder().create();
			final Reader r = new InputStreamReader(in);
			final OrientDBBaseConfiguration baseConfig = gson.fromJson(r, OrientDBBaseConfiguration.class);
			return baseConfig;
		} catch (final Exception e) {
			this.logConnector.log(e);
			return null;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.BaseConfigurationController#exportConfiguration(java.io.OutputStream) */
	@Override
	public boolean exportCompleteConfiguration(final OutputStream out) {
		return exportConfiguration(out, true, true, null, null);
	}

	/**
	 * Exports configuration partly.
	 *
	 * @param out the output stream to write to
	 * @param exportAll set to true to export all data
	 * @param exportPortConnections set to true to export port connections
	 * @param moduleIdsToExport the module IDs to export
	 * @param ciIdsToExport the CI IDs to export
	 * @return true, if successful
	 */
	private boolean exportConfiguration(final OutputStream out, final boolean exportAll, final boolean exportPortConnections, final Set<String> moduleIdsToExport, final Set<String> ciIdsToExport) {
		if (!exportAll && ((moduleIdsToExport == null) || (ciIdsToExport == null))) {
			return false;
		}
		this.baseConfReadLock.lock();
		boolean result = false;
		if (this.dbBaseConfig != null) {
			final OObjectDatabaseTx db = this.dbController.getDBInstance();
			Writer w = null;
			try {
				final OrientDBBaseConfiguration detachedBaseConfig = db.detachAll(this.dbBaseConfig, true);
				if (!exportAll) {
					if (!exportPortConnections) {
						detachedBaseConfig.getPortConnections().clear();
					}
					Iterator<String> idIter = detachedBaseConfig.getModuleConfigurations().keySet().iterator();
					while (idIter.hasNext()) {
						final String id = idIter.next();
						if (!moduleIdsToExport.contains(id)) {
							idIter.remove();
						}
					}
					idIter = detachedBaseConfig.getCiConfigurations().keySet().iterator();
					while (idIter.hasNext()) {
						final String id = idIter.next();
						if (!ciIdsToExport.contains(id)) {
							idIter.remove();
						}
					}
					final Iterator<OrientDBPortTuple> portTupleIter = detachedBaseConfig.getPortConnections().iterator();
					while (portTupleIter.hasNext()) {
						final OrientDBPortTuple portTuple = portTupleIter.next();
						if (!moduleIdsToExport.contains(portTuple.getProsumerModuleId()) || !moduleIdsToExport.contains(portTuple.getProviderModuleId())) {
							portTupleIter.remove();
						}
					}
				}
				final Gson gson = new GsonBuilder().setExclusionStrategies(new GsonOrientDBExclusionStrategy()).create();
				w = new OutputStreamWriter(out);
				w.write(gson.toJson(detachedBaseConfig));
				w.flush();
				result = true;
			} catch (final Exception e) {
				this.logConnector.log(e);
			} finally {
				db.close();
				if (w != null) {
					try {
						w.close();
					} catch (final IOException e1) {
						// ignored
					}
				}
			}
		}
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.BaseConfigurationController#exportConfiguration(java.io.OutputStream, boolean, java.util.Set, java.util.Set) */
	@Override
	public boolean exportConfiguration(final OutputStream out, final boolean exportPortConnections, final Set<String> moduleIdsToExport, final Set<String> ciIdsToExport) {
		return exportConfiguration(out, false, exportPortConnections, moduleIdsToExport, ciIdsToExport);
	}

	@Override
	public BaseConfigurationSummary getBaseConfigurationSummary(final InputStream in) {
		final OrientDBBaseConfiguration newBaseConfig = deserializeBaseConfiguration(in);
		if (newBaseConfig == null) {
			return null;
		}
		final boolean hasPortConnections = (newBaseConfig.getPortConnections() != null) && !newBaseConfig.getPortConnections().isEmpty();
		return new BaseConfigurationSummary(hasPortConnections, getModuleSummaries(newBaseConfig), getCISummaries(newBaseConfig));
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#getUIConfiguration(java.lang.String) */
	@Override
	public ComponentConfigurationController getCIConfiguration(final String ciId) {
		OrientDBComponentConfigurationController result = this.ciConfigurations.get(ciId);
		if (result != null) {
			return result;
		}
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getCiConfigurations() != null) {
			final OrientDBComponentConfiguration dbCompConf = this.dbBaseConfig.getCiConfigurations().get(ciId);
			if (dbCompConf != null) {
				result = new OrientDBComponentConfigurationController(this.dbController, dbCompConf, this.logConnector);
				this.ciConfigurations.put(ciId, result);
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#getUIConfigurations() */
	@Override
	public Map<String, ComponentConfigurationController> getCIConfigurations() {
		final Map<String, ComponentConfigurationController> result = new HashMap<String, ComponentConfigurationController>();
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getCiConfigurations() != null) {
			for (final String key : this.dbBaseConfig.getCiConfigurations().keySet()) {
				OrientDBComponentConfigurationController modConf = this.ciConfigurations.get(key);
				if (modConf != null) {
					result.put(key, modConf);
				} else {
					final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getCiConfigurations().get(key);
					if (dbModConf != null) {
						if (modConf == null) {
							modConf = new OrientDBComponentConfigurationController(this.dbController, dbModConf, this.logConnector);
							this.ciConfigurations.put(key, modConf);
						}
						result.put(key, modConf);
					}
				}
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#getUIName(java.lang.String) */
	@Override
	public String getCIName(final String uiId) {
		String result = "";
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getCiConfigurations() != null) {
			final OrientDBComponentConfiguration dbCIConf = this.dbBaseConfig.getCiConfigurations().get(uiId);
			if (dbCIConf != null) {
				result = dbCIConf.getComponentName();
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#getUIRights(java.lang.String) */
	@Override
	public int getCIRights(final String uiId) {
		int result = ControlInterfaceRight.RIGHT___NON;
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getCiConfigurations() != null) {
			final OrientDBComponentConfiguration dbCIConf = this.dbBaseConfig.getCiConfigurations().get(uiId);
			if (dbCIConf != null) {
				result = dbCIConf.getComponentRights();
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/**
	 * Gets all control interface summaries from a given base configuration.
	 *
	 * @param baseConfig the base configuration
	 * @return the CI summaries
	 */
	private Set<ControlInterfaceSummary> getCISummaries(final OrientDBBaseConfiguration baseConfig) {
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (baseConfig.getCiConfigurations() != null) {
			final Set<ControlInterfaceSummary> ciSummaries = new HashSet<ControlInterfaceSummary>();
			for (final OrientDBComponentConfiguration conf : baseConfig.getCiConfigurations().values()) {
				ciSummaries.add(new ControlInterfaceSummary(conf.getComponentId(), conf.getComponentName(), conf.getComponentType(), conf.getComponentRights()));
			}
			db.close();
			return ImmutableSet.copyOf(ciSummaries);
		} else {
			db.close();
			return null;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.BaseConfigurationController#getUIType(java.lang.String) */
	@Override
	public String getCIType(final String uiId) {
		String result = null;
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getCiConfigurations().get(uiId);
			if (dbModConf != null) {
				result = dbModConf.getComponentType();
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see framework.iface.BaseConfigurationController#getCurrentBaseConfigurationSummary() */
	@Override
	public BaseConfigurationSummary getCurrentBaseConfigurationSummary() {
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		final boolean hasPortConnections = (this.dbBaseConfig.getPortConnections() != null) && !this.dbBaseConfig.getPortConnections().isEmpty();
		final Set<ModuleSummary> moduleIds = getModuleSummaries(this.dbBaseConfig);
		final Set<ControlInterfaceSummary> ciIds = getCISummaries(this.dbBaseConfig);
		db.close();
		this.baseConfReadLock.unlock();
		return new BaseConfigurationSummary(hasPortConnections, moduleIds, ciIds);
	}

	/* (non-Javadoc)
	 * 
	 * @see framework.iface.BaseConfigurationController#getModuleConfiguration(java.lang.String) */
	@Override
	public ComponentConfigurationController getModuleConfiguration(final String moduleId) {
		OrientDBComponentConfigurationController result = this.moduleConfigurations.get(moduleId);
		if (result != null) {
			return result;
		}
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getModuleConfigurations().get(moduleId);
			if (dbModConf != null) {
				result = new OrientDBComponentConfigurationController(this.dbController, dbModConf, this.logConnector);
				this.moduleConfigurations.put(moduleId, result);
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#getModuleConfigurations() */
	@Override
	public Map<String, ComponentConfigurationController> getModuleConfigurations() {
		final Map<String, ComponentConfigurationController> result = new HashMap<String, ComponentConfigurationController>();
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations() != null) {
			for (final String key : this.dbBaseConfig.getModuleConfigurations().keySet()) {
				OrientDBComponentConfigurationController modConf = this.moduleConfigurations.get(key);
				if (modConf != null) {
					result.put(key, modConf);
				} else {
					final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getModuleConfigurations().get(key);
					if (dbModConf != null) {
						if (modConf == null) {
							modConf = new OrientDBComponentConfigurationController(this.dbController, dbModConf, this.logConnector);
							this.moduleConfigurations.put(key, modConf);
						}
						result.put(key, modConf);
					}
				}
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#getModuleName(java.lang.String) */
	@Override
	public String getModuleName(final String moduleId) {
		String result = "";
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getModuleConfigurations().get(moduleId);
			if (dbModConf != null) {
				result = dbModConf.getComponentName();
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#getModuleRights(java.lang.String) */
	@Override
	public int getModuleRights(final String moduleId) {
		int result = ControlInterfaceRight.RIGHT___NON;
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getModuleConfigurations().get(moduleId);
			if (dbModConf != null) {
				result = dbModConf.getComponentRights();
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/**
	 * Gets all module summaries from a given base configuration.
	 *
	 * @param baseConfig the base configuration
	 * @return the module summaries
	 */
	private Set<ModuleSummary> getModuleSummaries(final OrientDBBaseConfiguration baseConfig) {
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (baseConfig.getModuleConfigurations() != null) {
			final Set<ModuleSummary> moduleSummaries = new HashSet<ModuleSummary>();
			for (final OrientDBComponentConfiguration conf : baseConfig.getModuleConfigurations().values()) {
				moduleSummaries.add(new ModuleSummary(conf.getComponentId(), conf.getComponentName(), conf.getComponentType(), conf.getComponentRights(), new HashSet<PortSummary>()));
			}
			db.close();
			return ImmutableSet.copyOf(moduleSummaries);
		} else {
			db.close();
			return null;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see framework.iface.BaseConfigurationController#getModuleType(java.lang.String) */
	@Override
	public String getModuleType(final String moduleId) {
		String result = null;
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getModuleConfigurations().get(moduleId);
			if (dbModConf != null) {
				result = dbModConf.getComponentType();
			}
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#getPortConnections() */
	@Override
	public Set<ConnectionSummary> getPortConnections() {
		final Set<ConnectionSummary> result = new HashSet<ConnectionSummary>();
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			for (final OrientDBPortTuple dbPortTuple : this.dbBaseConfig.getPortConnections()) {
				final ConnectionSummary summary = convertToSummary(dbPortTuple);
				if (summary != null) {
					result.add(summary);
				} else {

				}
			}
		} catch (final Exception e) {
			this.logConnector.log(e);
		}
		db.close();
		this.baseConfReadLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#getVersion() */
	@Override
	public int getVersion() {
		this.baseConfReadLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		final int version = this.dbBaseConfig.getConfigVersion();
		db.close();
		this.baseConfReadLock.unlock();
		return version;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.BaseConfigurationController#importConfiguration(java.io.InputStream, boolean) */
	@Override
	public boolean importCompleteConfiguration(final InputStream in, final boolean overwrite) {
		return importConfiguration(in, false, overwrite, true, true, null, null);
	}

	/**
	 * Imports configuration partly.
	 *
	 * @param in the input stream to read from
	 * @param clearBeforeImport set to true to clear database before import
	 * @param overwrite set to true to overwrite existing data (if any)
	 * @param importAll true import all data
	 * @param importPortConnections set to true to import port connections
	 * @param moduleIdsToImport the module IDs to import
	 * @param ciIdsToImport the CI IDs to import
	 * @return true, if successful
	 */
	private boolean importConfiguration(final InputStream in, final boolean clearBeforeImport, final boolean overwrite, final boolean importAll, final boolean importPortConnections, Set<String> moduleIdsToImport, Set<String> ciIdsToImport) {
		final OrientDBBaseConfiguration newBaseConfig = deserializeBaseConfiguration(in);

		if (newBaseConfig == null) {
			return false;
		}

		if (!importAll && ((moduleIdsToImport == null) || (ciIdsToImport == null))) {
			return false;
		}

		if ((moduleIdsToImport != null) && !moduleIdsToImport.isEmpty()) {
			if (newBaseConfig.getModuleConfigurations() == null) {
				return false;
			}
			for (final String id : moduleIdsToImport) {
				if (!newBaseConfig.getModuleConfigurations().containsKey(id)) {
					return false;
				}
			}
		}

		if ((ciIdsToImport != null) && !ciIdsToImport.isEmpty()) {
			if (newBaseConfig.getCiConfigurations() == null) {
				return false;
			}
			for (final String id : ciIdsToImport) {
				if (!newBaseConfig.getCiConfigurations().containsKey(id)) {
					return false;
				}
			}
		}

		boolean result = false;
		this.baseConfWriteLock.lock();

		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		try {
			if (clearBeforeImport) {
				final Map<String, OrientDBComponentConfiguration> modConfs = this.dbBaseConfig.getModuleConfigurations();
				if (modConfs != null) {
					for (final OrientDBComponentConfiguration modConf : modConfs.values()) {
						db.delete(modConf);
					}
					modConfs.clear();
				}
				final Map<String, OrientDBComponentConfiguration> ciConfs = this.dbBaseConfig.getCiConfigurations();
				if (ciConfs != null) {
					for (final OrientDBComponentConfiguration ciConf : ciConfs.values()) {
						db.delete(ciConf);
					}
					ciConfs.clear();
				}
				if (this.dbBaseConfig.getPortConnections() != null) {
					final Set<OrientDBPortTuple> tuples = this.dbBaseConfig.getPortConnections();
					for (final OrientDBPortTuple tuple : tuples) {
						db.delete(tuple);
					}
					tuples.clear();
				}
			}

			if ((importAll || importPortConnections) && (newBaseConfig.getPortConnections() != null) && !newBaseConfig.getPortConnections().isEmpty()) {
				Set<OrientDBPortTuple> tuples = this.dbBaseConfig.getPortConnections();
				if (tuples == null) {
					tuples = new HashSet<OrientDBPortTuple>();
					this.dbBaseConfig.setPortConnections(tuples);
				}
				for (OrientDBPortTuple tuple : newBaseConfig.getPortConnections()) {
					if (this.dbBaseConfig.getPortConnections().contains(tuple)) {
						if (overwrite) {
							this.dbBaseConfig.getPortConnections().remove(tuple);
						} else {
							continue;
						}
					}
					tuple = db.save(tuple);
					tuples.add(tuple);
				}
			}

			if (importAll) {
				moduleIdsToImport = newBaseConfig.getModuleConfigurations().keySet();
				ciIdsToImport = newBaseConfig.getCiConfigurations().keySet();
			}

			if ((moduleIdsToImport != null) && !moduleIdsToImport.isEmpty()) {
				Map<String, OrientDBComponentConfiguration> modConfs = this.dbBaseConfig.getModuleConfigurations();
				if (modConfs == null) {
					modConfs = new HashMap<String, OrientDBComponentConfiguration>();
					this.dbBaseConfig.setModuleConfigurations(modConfs);
				}
				for (final String id : moduleIdsToImport) {
					if (modConfs.containsKey(id)) {
						if (overwrite) {
							final OrientDBComponentConfiguration oldModConf = modConfs.get(id);
							if (oldModConf != null) {
								modConfs.remove(id);
							}
						} else {
							continue;
						}
					}
					OrientDBComponentConfiguration newModConf = newBaseConfig.getModuleConfigurations().get(id);
					if (newModConf != null) {
						newModConf = db.save(newModConf);
						modConfs.put(id, newModConf);
					}
				}
			}

			if ((ciIdsToImport != null) && !ciIdsToImport.isEmpty()) {
				Map<String, OrientDBComponentConfiguration> ciConfs = this.dbBaseConfig.getCiConfigurations();
				if (ciConfs == null) {
					ciConfs = new HashMap<String, OrientDBComponentConfiguration>();
					this.dbBaseConfig.setCiConfigurations(ciConfs);
				}
				for (final String id : ciIdsToImport) {
					if (ciConfs.containsKey(id)) {
						if (overwrite) {
							final OrientDBComponentConfiguration oldCiConf = ciConfs.get(id);
							if (oldCiConf != null) {
								ciConfs.remove(id);
							}
						} else {
							continue;
						}
					}
					OrientDBComponentConfiguration newCiConf = newBaseConfig.getCiConfigurations().get(id);
					if (newCiConf != null) {
						newCiConf = db.save(newCiConf);
						ciConfs.put(id, newCiConf);
					}
				}
			}

			db.save(this.dbBaseConfig);
			result = true;
		} catch (final Exception e) {
			this.logConnector.log(e);
		} finally {
			db.close();
		}

		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.BaseConfigurationController#importConfiguration(java.io.InputStream, boolean, java.util.Set, java.util.Set) */
	@Override
	public boolean importConfiguration(final InputStream in, final boolean importPortConnections, final Set<String> moduleIdsToImport, final Set<String> ciIdsToImport) {
		return importConfiguration(in, false, true, false, importPortConnections, moduleIdsToImport, ciIdsToImport);
	}

	@Override
	public boolean removeCIConfiguration(final String ciId) {
		boolean result = false;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		final OrientDBComponentConfiguration dbCIConf = this.dbBaseConfig.getCiConfigurations().get(ciId);
		final ComponentConfigurationController ciConf = this.ciConfigurations.remove(ciId);
		if (dbCIConf != null) {
			try {
				db.begin(TXTYPE.OPTIMISTIC);
				this.dbBaseConfig.getCiConfigurations().remove(ciId);
				db.save(this.dbBaseConfig);
				db.commit();
				if (ciConf != null) {
					ciConf.invalidateConfiguration();
				}
				result = true;
			} catch (final Exception e) {
				this.logConnector.log(e);
				db.rollback();
				db.reload(this.dbBaseConfig);
			}
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#removeModuleConfiguration(java.lang.String) */
	@Override
	public boolean removeModuleConfiguration(final String moduleId) {
		boolean result = false;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations().containsKey(moduleId)) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getModuleConfigurations().get(moduleId);
			final ComponentConfigurationController modConf = this.moduleConfigurations.remove(moduleId);
			if (dbModConf != null) {
				try {
					db.begin(TXTYPE.OPTIMISTIC);
					this.dbBaseConfig.getModuleConfigurations().remove(moduleId);
					db.save(this.dbBaseConfig);
					db.commit();
					if (modConf != null) {
						modConf.invalidateConfiguration();
					}
					result = true;
				} catch (final Exception e) {
					this.logConnector.log(e);
					db.rollback();
					db.reload(this.dbBaseConfig);
				}
			}
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#removePortConnection(framework.model.config.PortTuple) */
	@Override
	public boolean removePortConnection(final ConnectionSummary connectionSummary) {
		boolean result = false;
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		this.baseConfWriteLock.lock();
		try {
			db.begin(TXTYPE.OPTIMISTIC);
			final Set<OrientDBPortTuple> connections = this.dbBaseConfig.getPortConnections();
			if (connections != null) {
				final OrientDBPortTuple dbPortTuple = convertToTuple(connectionSummary);
				for (OrientDBPortTuple dbPortTupleFromDB : connections) {
					if (dbPortTupleFromDB != null) {
						dbPortTupleFromDB = db.detach(dbPortTupleFromDB);
						if (dbPortTupleFromDB.equals(dbPortTuple)) {
							connections.remove(dbPortTupleFromDB);
							db.save(this.dbBaseConfig);
							result = true;
							break;
						}
					}
				}
			}
			db.commit();
		} catch (final Exception e) {
			this.logConnector.log(e);
			db.rollback();
			result = false;
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.BaseConfigurationController#restoreConfiguration(java.io.InputStream) */
	@Override
	public boolean restoreConfiguration(final InputStream in) {
		return importConfiguration(in, true, true, true, true, null, null);
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#setUIName(java.lang.String, java.lang.String) */
	@Override
	public boolean setCIName(final String uiId, final String uiName) {
		boolean result = false;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getCiConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getCiConfigurations().get(uiId);
			if (dbModConf != null) {
				try {
					db.begin(TXTYPE.OPTIMISTIC);
					dbModConf.setComponentName(uiName);
					db.save(dbModConf);
					db.commit();
					result = true;
				} catch (final Exception e) {
					this.logConnector.log(e);
					db.rollback();
					db.reload(dbModConf);
				}
			}
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#setUIRights(java.lang.String, int) */
	@Override
	public boolean setCIRights(final String uiId, final int rights) {
		boolean result = false;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getCiConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getCiConfigurations().get(uiId);
			if (dbModConf != null) {
				try {
					db.begin(TXTYPE.OPTIMISTIC);
					dbModConf.setComponentRights(rights);
					db.save(dbModConf);
					db.commit();
					result = true;
				} catch (final Exception e) {
					this.logConnector.log(e);
					db.rollback();
					db.reload(dbModConf);
				}
			}
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#setModuleName(java.lang.String, java.lang.String) */
	@Override
	public boolean setModuleName(final String moduleId, final String moduleName) {
		boolean result = false;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getModuleConfigurations().get(moduleId);
			if (dbModConf != null) {
				try {
					db.begin(TXTYPE.OPTIMISTIC);
					dbModConf.setComponentName(moduleName);
					db.save(dbModConf);
					db.commit();
					result = true;
				} catch (final Exception e) {
					this.logConnector.log(e);
					db.rollback();
					db.reload(dbModConf);
				}
			}
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.iface.BaseConfigurationController#setModuleRights(java.lang.String, int) */
	@Override
	public boolean setModuleRights(final String moduleId, final int rights) {
		boolean result = false;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		if (this.dbBaseConfig.getModuleConfigurations() != null) {
			final OrientDBComponentConfiguration dbModConf = this.dbBaseConfig.getModuleConfigurations().get(moduleId);
			if (dbModConf != null) {
				try {
					db.begin(TXTYPE.OPTIMISTIC);
					dbModConf.setComponentRights(rights);
					db.save(dbModConf);
					db.commit();
					result = true;
				} catch (final Exception e) {
					this.logConnector.log(e);
					db.rollback();
					db.reload(dbModConf);
				}
			}
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#setPortConnections(java.util.Set) */
	@Override
	public boolean setPortConnections(final Set<ConnectionSummary> portConnections) {
		boolean result = false;
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		this.baseConfWriteLock.lock();
		try {
			db.begin(TXTYPE.OPTIMISTIC);
			Set<OrientDBPortTuple> connections = this.dbBaseConfig.getPortConnections();
			if (connections == null) {
				this.dbBaseConfig.setPortConnections(new HashSet<OrientDBPortTuple>());
				connections = this.dbBaseConfig.getPortConnections();
			}
			connections.clear();
			for (final ConnectionSummary summary : portConnections) {
				OrientDBPortTuple dbPortTuple = convertToTuple(summary);
				if (dbPortTuple != null) {
					dbPortTuple = db.save(dbPortTuple);
					connections.add(dbPortTuple);
				}
			}
			db.save(this.dbBaseConfig);
			db.commit();
			result = true;
		} catch (final Exception e) {
			this.logConnector.log(e);
			db.rollback();
			db.reload(this.dbBaseConfig);
		} 
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see db.orientdb.control.BaseConfigurationController#setVersion(int) */
	@Override
	public boolean setVersion(final int version) {
		boolean result = false;
		this.baseConfWriteLock.lock();
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		this.dbBaseConfig.setConfigVersion(version);
		try {
			db.save(this.dbBaseConfig);
			result = true;
		} catch (final Exception e) {
			this.logConnector.log(e);
			db.reload(this.dbBaseConfig);
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see db.iface.BaseConfigurationController#updatePortConnection(framework.model.PortTuple) */
	@Override
	public boolean updatePortConnection(final ConnectionSummary connectionSummary) {
		if (connectionSummary == null) {
			return false;
		}
		boolean result = false;
		final OObjectDatabaseTx db = this.dbController.getDBInstance();
		this.baseConfWriteLock.lock();
		try {
			db.begin(TXTYPE.OPTIMISTIC);
			Set<OrientDBPortTuple> connections = this.dbBaseConfig.getPortConnections();
			if (connections == null) {
				this.dbBaseConfig.setPortConnections(new HashSet<OrientDBPortTuple>());
				connections = this.dbBaseConfig.getPortConnections();
			}
			final OrientDBPortTuple newTuple = convertToTuple(connectionSummary);
			if (newTuple != null) {
				for (final OrientDBPortTuple dbTuple : this.dbBaseConfig.getPortConnections()) {
					if (newTuple.equals(dbTuple)) {
						this.dbBaseConfig.getPortConnections().remove(dbTuple);
						db.commit();
						result = true;
						break;
					}
				}
			}
		} catch (final Exception e) {
			this.logConnector.log(e);
			db.rollback();
			db.reload(this.dbBaseConfig);
		}
		db.close();
		this.baseConfWriteLock.unlock();
		return result;
	}
}
