package module.webdavclient.control;

import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.PersistentConfigurationHelper;
import helper.TextFormatHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import module.iface.AbstractProvider;
import module.iface.ErrorCode;
import module.webdavclient.model.WebDavOutputStream;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineException;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

import db.iface.ComponentConfigurationController;
import framework.constants.GenericControlInterfaceCommands;
import framework.control.LogConnector;
import framework.control.ProviderConnector;
import framework.exception.BrokerException;
import framework.exception.DatabaseException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.Port;
import framework.model.ProviderPort;
import framework.model.event.type.DataElementEventType;
import framework.model.event.type.LogEventLevelType;
import framework.model.type.DataElementType;
import framework.model.type.ModuleStateType;

/**
 * Module implementing a provider for the WebDAV(s) protocol by using the Sardine client library.
 *
 * @author Stefan Werner
 */
public class WebDavClientModule extends AbstractProvider {

	/**
	 * Internal element change monitor with regular element checks.
	 */
	private class MonitorThread extends Thread {

		private Map<String, DataElement> elementMap;

		@Override
		public void run() {
			while (!isInterrupted()) {
				try {
					final Map<String, DataElement> newElementMap = new ConcurrentHashMap<String, DataElement>();
					final Set<DataElement> newElements = getChildElements(WebDavClientModule.this.providerPort, new String[0], true);
					if (newElements != null) {
						if (this.elementMap != null) {
							boolean error = false;
							for (final DataElement newElement : newElements) {
								if (newElement == null) {
									error = true;
									continue;
								}
								final String internalPath = TextFormatHelper.getPathString(newElement.getPath());
								if (internalPath != null) {
									newElementMap.put(internalPath, newElement);
									final DataElement element = this.elementMap.remove(internalPath);
									if (element == null) {
										try {
											WebDavClientModule.this.providerConnector.sendElementEvent(WebDavClientModule.this.providerPort, newElement, DataElementEventType.ADD);
										} catch (final BrokerException e) {
											WebDavClientModule.this.logConnector.log(e);
										}
									} else if (!element.equals(newElement)) {
										try {
											WebDavClientModule.this.providerConnector.sendElementEvent(WebDavClientModule.this.providerPort, newElement, DataElementEventType.MODIFY);
										} catch (final BrokerException e) {
											WebDavClientModule.this.logConnector.log(e);
										}
									}
								} else {
									error = true;
								}
							}
							if (!error) {
								for (final DataElement element : this.elementMap.values()) {
									try {
										WebDavClientModule.this.providerConnector.sendElementEvent(WebDavClientModule.this.providerPort, element, DataElementEventType.DELETE);
									} catch (final BrokerException e) {
										WebDavClientModule.this.logConnector.log(e);
									}
								}
							}
						}
						this.elementMap = newElementMap;
					}
					if (this.elementMap != null) {
						WebDavClientModule.this.currentRefreshIntervalSecs = Math.min(((this.elementMap.size() / 1000) * (WebDavClientModule.this.mediumRefreshIntervalSeconds - WebDavClientModule.this.minRefreshIntervalSeconds)) + WebDavClientModule.this.minRefreshIntervalSeconds, WebDavClientModule.this.maxRefreshIntervalSeconds);
					}
				} catch (final ModuleException e) {
					WebDavClientModule.this.logConnector.log(e);
				}
				try {
					TimeUnit.SECONDS.sleep(WebDavClientModule.this.currentRefreshIntervalSecs);
				} catch (final InterruptedException e) {
					break;
				}
			}
		}
	}

	private static final Joiner ADDRESS_JOINER = Joiner.on("/").skipNulls();
	private static final String CONFIG_PROPERTY_KEY___BASE_ADDRESS = "base_address";
	private static final String CONFIG_PROPERTY_KEY___COMPRESSION = "compression";
	private static final String CONFIG_PROPERTY_KEY___FOREC_RO = "force_readonly";
	private static final String CONFIG_PROPERTY_KEY___MAX_REF_IVAL_SECS = "max_refresh_interval_seconds";
	private static final String CONFIG_PROPERTY_KEY___MEDIUM_REF_IVAL_SECS = "medium_refresh_interval_seconds";
	private static final String CONFIG_PROPERTY_KEY___MIN_REF_IVAL_SECS = "min_refresh_interval_seconds";
	private static final String CONFIG_PROPERTY_KEY___MONITOR_FS = "monitor_fs";
	private static final String CONFIG_PROPERTY_KEY___PASSWORD = "password";
	private static final String CONFIG_PROPERTY_KEY___PREEMP_AUTH = "preemp_auth";
	private static final String CONFIG_PROPERTY_KEY___SUPPORT_LOCKING = "support_locking";
	private static final String CONFIG_PROPERTY_KEY___USERNAME = "username";
	private static final String[] DB___CONFIG_DATA_PATH = { "config_data" };
	private static final String DB___DOMAIN___CONFIG = "config";
	private static final boolean DEFAULT_CONFIG_VALUE___ENABLE_COMPRESSION = false;
	private static final boolean DEFAULT_CONFIG_VALUE___ENABLE_PREEMPTIVE_AUTH = false;
	private static final boolean DEFAULT_CONFIG_VALUE___FORCE_READONLY_FS = false;
	private static final int DEFAULT_CONFIG_VALUE___MAX_REFRESH_INTERVAL_SECS = 3600;
	private static final int DEFAULT_CONFIG_VALUE___MEDIUM_REFRESH_INTERVAL_SECS = 600;
	private static final int DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS = 300;
	private static final boolean DEFAULT_CONFIG_VALUE___MONITOR_FS = true;
	private static final boolean DEFAULT_CONFIG_VALUE___SUPPORT_LOCKING = false;
	private static final int MAX_PATH_RECURSION_DEPTH = 16;
	private static final Escaper PATH_ESCAPER = UrlEscapers.urlPathSegmentEscaper();
	private static final String PORT_ID___PROVIDER = "port";

	private String baseAddress = null;
	private PersistentConfigurationHelper configHelper;
	private final Map<String, String> currentlyHeldWebDavLocks = new ConcurrentHashMap<String, String>();
	private int currentModuleState = 0; // 0 = new, 1 = stopped, 2 = started, 3 = connected, -1 = error
	private int currentRefreshIntervalSecs = WebDavClientModule.DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS;
	private boolean forceReadonlyFS = WebDavClientModule.DEFAULT_CONFIG_VALUE___FORCE_READONLY_FS;
	private MonitorThread fsMonitorThread = null;
	private int maxRefreshIntervalSeconds = WebDavClientModule.DEFAULT_CONFIG_VALUE___MAX_REFRESH_INTERVAL_SECS;
	private int mediumRefreshIntervalSeconds = WebDavClientModule.DEFAULT_CONFIG_VALUE___MEDIUM_REFRESH_INTERVAL_SECS;
	private int minRefreshIntervalSeconds = WebDavClientModule.DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS;
	private ProviderPort providerPort;
	private boolean providerPortConnected = false;
	private Sardine sardine = null;
	private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
	private final ReadLock stateReadLock = this.stateLock.readLock();
	private final WriteLock stateWriteLock = this.stateLock.writeLock();
	private boolean supportLocking = WebDavClientModule.DEFAULT_CONFIG_VALUE___SUPPORT_LOCKING;
	private boolean webDavReady = false;

	/**
	 * Instantiates a new web dav client module.
	 *
	 * @param providerConnector the provider connector
	 * @param componentConfiguration the component configuration
	 * @param logConnector the log connector
	 */
	public WebDavClientModule(final ProviderConnector providerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(providerConnector, componentConfiguration, logConnector);
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#checkAndLock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int checkAndLock(final ProviderPort port, String[] path) throws ModuleException {
		int result = ErrorCode.ENOSYS;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			if (this.supportLocking) {
				path = checkAndSanitizePath(path);
				if (!mayWrite()) {
					result = ErrorCode.EROFS;
				} else {
					final String address = getAddress(path);
					if (address == null) {
						result = ErrorCode.EINVAL;
					} else if (!this.currentlyHeldWebDavLocks.containsKey(address)) {
						try {
							if (!this.sardine.exists(address)) {
								result = ErrorCode.ENOENT;
							} else {
								final String lockToken = this.sardine.lock(address);
								if (lockToken != null) {
									this.currentlyHeldWebDavLocks.put(address, lockToken);
								}
							}
						} catch (final IOException e) {
							result = ErrorCode.EIO;
						}
					}
				}
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/**
	 * Checks and sanitizes a given path.
	 *
	 * @param path the path
	 * @return the the sanitized path
	 * @throws ModuleException if something went wrong
	 */
	private String[] checkAndSanitizePath(final String[] path) throws ModuleException {
		final String exMsg = "invalid/illegal path argument";
		if (path == null) {
			throw new ModuleException(exMsg);
		}
		final String[] result = new String[path.length];
		for (int i = 0; i < path.length; i++) {
			if ((path[i] == null) || path[i].isEmpty() || CharMatcher.JAVA_ISO_CONTROL.matchesAnyOf(path[i])) {
				throw new ModuleException(exMsg + (path[i] == null) + path[i].isEmpty() + CharMatcher.JAVA_ISO_CONTROL.or(CharMatcher.INVISIBLE).matchesAnyOf(path[i]));
			}
			result[i] = WebDavClientModule.PATH_ESCAPER.escape(path[i]);
		}
		return result;
	}

	/**
	 * Checks a folder for existence and type.
	 *
	 * @param address the address
	 * @return true, if exists and is a directory
	 */
	private boolean checkFolder(final String address) {
		try {
			final List<DavResource> resources = this.sardine.list(address, 0);
			return (resources != null) && (resources.size() == 1) && resources.get(0).isDirectory();
		} catch (final IOException e) {
			return false;
		}
	}

	/**
	 * Checks for operational state and port type.
	 *
	 * @param port the port
	 * @throws ModuleException if not in operational state or port not the provider port
	 */
	private void checkForOperationalStateAndPort(final ProviderPort port) throws ModuleException {
		this.stateReadLock.lock();
		try {
			if ((this.currentModuleState < 3) || (port == null) || (port != this.providerPort) || (this.sardine == null)) {
				throw new ModuleException("not in operational state or invalid port");
			}
		} finally {
			this.stateReadLock.unlock();
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#createFolder(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int createFolder(final ProviderPort port, String[] path) throws ModuleException {
		int result = 0;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			path = checkAndSanitizePath(path);
			if (!mayWrite()) {
				this.stateReadLock.unlock();
				return ErrorCode.EROFS;
			}
			final String address = getAddress(path);
			if (address == null) {
				result = ErrorCode.EINVAL;
			} else {
				try {
					if (this.sardine.exists(address)) {
						result = ErrorCode.EEXIST;
					} else {
						this.sardine.createDirectory(address);
					}
				} catch (final IOException e) {
					result = ErrorCode.EIO;
				}
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#delete(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int delete(final ProviderPort port, String[] path) throws ModuleException {
		int result = 0;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			path = checkAndSanitizePath(path);
			if (!mayWrite()) {
				this.stateReadLock.unlock();
				return ErrorCode.EROFS;
			}
			final String address = getAddress(path);
			if (address == null) {
				result = ErrorCode.EINVAL;
			} else {
				try {
					if (!this.sardine.exists(address)) {
						result = ErrorCode.ENOENT;
					} else {
						this.sardine.delete(address);
					}
				} catch (final IOException e) {
					result = ErrorCode.EIO;
				}
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#enterShutdown() */
	@Override
	public void enterShutdown() {
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#enterStartup() */
	@Override
	public void enterStartup() {
		if (this.currentModuleState == 1) {
			this.currentModuleState++;
		}
		manageState();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		if (this.currentModuleState > 1) {
			this.currentModuleState = 1;
		}
		manageState();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
	}

	/**
	 * Gets the address of a given path.
	 *
	 * @param path the path
	 * @return the address
	 */
	public String getAddress(final String[] path) {
		if (path == null) {
			return null;
		} else if (path.length == 0) {
			return this.baseAddress;
		} else {
			return this.baseAddress + "/" + WebDavClientModule.ADDRESS_JOINER.join(path);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getChildElements(framework.model.ProviderPort, java.lang.String[], boolean) */
	@Override
	public Set<DataElement> getChildElements(final ProviderPort port, String[] path, final boolean recursive) throws ModuleException {
		Set<DataElement> result = null;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			path = checkAndSanitizePath(path);
			result = getChildElementsRecursion(port, path, recursive, 1);
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/**
	 * Gets the child elements (internal recursive method).
	 *
	 * @param port the port
	 * @param sanitizedPath the sanitized path
	 * @param recursive set to true to get children recursively
	 * @param curDepth the current depth
	 * @return the child elements
	 * @throws ModuleException if something went wrong
	 */
	private Set<DataElement> getChildElementsRecursion(final ProviderPort port, final String[] sanitizedPath, final boolean recursive, final int curDepth) throws ModuleException {
		Set<DataElement> result = null;
		this.stateReadLock.lock();
		try {
			final String address = getAddress(sanitizedPath);
			if (address != null) {
				result = getDataElements(sanitizedPath, address, false, true);
				if (recursive && (curDepth <= WebDavClientModule.MAX_PATH_RECURSION_DEPTH) && (result != null)) {
					final Set<DataElement> moreResults = new HashSet<DataElement>();
					for (final DataElement element : result) {
						if (element.getType() == DataElementType.FOLDER) {
							final String[] sanitizedChildPath = checkAndSanitizePath(element.getPath());
							moreResults.addAll(getChildElementsRecursion(port, sanitizedChildPath, true, curDepth + 1));
						}
					}
					result.addAll(moreResults);
				}
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/**
	 * Gets a data element.
	 *
	 * @param path the path
	 * @param address the address
	 * @return the data element
	 */
	private DataElement getDataElement(final String[] path, final String address) {
		final Set<DataElement> elements = getDataElements(path, address, true, false);
		if ((elements == null) || elements.isEmpty()) {
			return null;
		} else {
			return elements.iterator().next();
		}
	}

	/**
	 * Gets data elements.
	 *
	 * @param sanitizedPath the sanitized path
	 * @param sanitizedAddress the sanitized address
	 * @param includeCurrentPathDataElement set to true to include element of the given path
	 * @param includeChildren set to true to include children under the given path
	 * @return the data elements
	 */
	private Set<DataElement> getDataElements(final String[] sanitizedPath, final String sanitizedAddress, final boolean includeCurrentPathDataElement, final boolean includeChildren) {
		Set<DataElement> result = null;
		if (sanitizedAddress != null) {
			List<DavResource> resources;
			DavResource currentPathDavResource;
			try {
				resources = this.sardine.list(sanitizedAddress, 0);
				if ((resources != null) && (resources.size() == 1)) {
					currentPathDavResource = resources.get(0);
					if (includeChildren) {
						resources = this.sardine.list(sanitizedAddress, 1); // recursive list currently (04/2015) seems to be broken
						if ((resources != null) && !includeCurrentPathDataElement) {
							// currently there is no way to exclude the current (base) path from results and since I'm not completely convinced that it is
							// always returned as head item in the returned list we better play safe
							final Iterator<DavResource> iter = resources.iterator();
							while (iter.hasNext()) {
								final DavResource resource = iter.next();
								if (resource.getPath().equals(currentPathDavResource.getPath())) {
									iter.remove();
									break;
								}
							}
						}
					}
					if (resources != null) {
						result = new HashSet<DataElement>();
						for (final DavResource resource : resources) {
							String[] newPath;
							if (resource.getPath().equals(currentPathDavResource.getPath())) {
								newPath = sanitizedPath;
							} else {
								newPath = ObjectArrays.concat(sanitizedPath, resource.getName());
							}
							if (resource.isDirectory()) {
								result.add(new DataElement(newPath, DataElementType.FOLDER, 0l, resource.getModified().getTime()));
							} else {
								result.add(new DataElement(newPath, DataElementType.FILE, resource.getContentLength(), resource.getModified().getTime()));
							}
						}
					}
				}
			} catch (final IOException e) {
				if (e instanceof SardineException) {
					final SardineException se = (SardineException) e;
					if (se.getMessage().contains("404")) {
						return null;
					}
				} else {
					this.logConnector.log(e, "unable to stat remote element " + sanitizedAddress);
				}
			}
		}
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getElement(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElement getElement(final ProviderPort port, String[] path) throws ModuleException {
		DataElement result = null;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			path = checkAndSanitizePath(path);
			if (path.length == 0) {
				result = new DataElement(path, DataElementType.FOLDER, 0l, 0l);
			} else {
				final String address = getAddress(path);
				if (address != null) {
					result = getDataElement(path, address);
				}
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/**
	 * Gets the parent address of a given path.
	 *
	 * @param path the path
	 * @return the parent address
	 */
	private String getParentAddress(final String[] path) {
		if ((path == null) || (path.length == 0)) {
			return null;
		} else if (path.length == 1) {
			return this.baseAddress;
		} else {
			return this.baseAddress + "/" + WebDavClientModule.ADDRESS_JOINER.join(Arrays.copyOfRange(path, 0, path.length - 1));
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return ImmutableSet.copyOf(GenericControlInterfaceCommands.DEFAULT_SUPPORT___CONFIG);
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#getSupportedModuleCommands(framework.model.Port, java.lang.String[]) */
	@Override
	public Set<String> getSupportedModuleCommands(final Port port, final String[] path) {
		return Collections.emptySet();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getType(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElementType getType(final ProviderPort port, String[] path) throws ModuleException {
		DataElementType result = null;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			path = checkAndSanitizePath(path);
			final String address = getAddress(path);
			if (address != null) {
				final DataElement element = getDataElement(path, address);
				if (element != null) {
					result = element.getType();
				}
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		if (this.currentModuleState == 0) {
			initializeConfig();
			try {
				this.providerPort = this.providerConnector.registerProviderPort(this, WebDavClientModule.PORT_ID___PROVIDER, -1); // -1 = unlimited connections
			} catch (final BrokerException e) {
				this.logConnector.log(e, "entering error state");
				this.currentModuleState = -1;
				return;
			}
			this.currentModuleState++;
		}
	}

	/**
	 * Initializes configuration.
	 */
	private void initializeConfig() {
		try {
			this.configHelper = new PersistentConfigurationHelper(this.componentConfiguration, WebDavClientModule.DB___DOMAIN___CONFIG, WebDavClientModule.DB___CONFIG_DATA_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.WARNING, "no persistent config");
			this.configHelper = new PersistentConfigurationHelper();
		}

		ConfigValue cv;
		String key;

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___MIN_REF_IVAL_SECS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueInteger(WebDavClientModule.DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS);
			cv.setDescriptionString("Lowest refresh interval (for monitoring 1 element).");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___MEDIUM_REF_IVAL_SECS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueInteger(WebDavClientModule.DEFAULT_CONFIG_VALUE___MEDIUM_REFRESH_INTERVAL_SECS);
			cv.setDescriptionString("Medium refresh interval (for monitoring 1000 elements).");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___MAX_REF_IVAL_SECS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueInteger(WebDavClientModule.DEFAULT_CONFIG_VALUE___MAX_REFRESH_INTERVAL_SECS);
			cv.setDescriptionString("Highest refresh interval.");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___MONITOR_FS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(WebDavClientModule.DEFAULT_CONFIG_VALUE___MONITOR_FS);
			cv.setDescriptionString("Poll remote filesystem regularly to check for changes.");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___BASE_ADDRESS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueString(null);
			cv.setDescriptionString("Base URL to WebDAV resource (for example https://host.net/somefolder).");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___COMPRESSION;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(WebDavClientModule.DEFAULT_CONFIG_VALUE___ENABLE_COMPRESSION);
			cv.setDescriptionString("Try to use HTTP GZIP compression.");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___FOREC_RO;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(WebDavClientModule.DEFAULT_CONFIG_VALUE___FORCE_READONLY_FS);
			cv.setDescriptionString("Force readonly operation even if remote resource can be written.");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___SUPPORT_LOCKING;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(WebDavClientModule.DEFAULT_CONFIG_VALUE___SUPPORT_LOCKING);
			cv.setDescriptionString("Support exclusive locking on resources (some server implementations may be broken).");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___PASSWORD;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueString(null);
			cv.setDescriptionString("Password used for authentification.");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___PREEMP_AUTH;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(WebDavClientModule.DEFAULT_CONFIG_VALUE___ENABLE_PREEMPTIVE_AUTH);
			cv.setDescriptionString("Host needs preemptive authentification (send with every request).");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = WebDavClientModule.CONFIG_PROPERTY_KEY___USERNAME;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueString(null);
			cv.setDescriptionString("Username used for authentification.");
			this.configHelper.updateConfigValue(key, cv, true);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return this.currentModuleState == 3;
	}

	/**
	 * Manages state.
	 */
	private void manageState() {
		this.stateWriteLock.lock();
		if ((this.currentModuleState == 2) && this.providerPortConnected && !this.webDavReady) {
			this.webDavReady = startWebDav();
			this.currentModuleState++;
		} else if ((this.currentModuleState == 3) && !this.providerPortConnected) {
			stopWebDav();
			this.webDavReady = false;
			this.currentModuleState--;
		} else if ((this.currentModuleState < 3) && this.webDavReady) {
			stopWebDav();
			this.webDavReady = false;
		}
		this.stateWriteLock.unlock();
	}

	/**
	 * Checks if module may do write operations.
	 *
	 * @return true, if OK
	 */
	private boolean mayWrite() {
		if (this.forceReadonlyFS) {
			return false;
		} else {
			return true;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#move(framework.model.ProviderPort, java.lang.String[], java.lang.String[]) */
	@Override
	public int move(final ProviderPort port, String[] srcPath, String[] destPath) throws ModuleException {
		int result = 0;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			srcPath = checkAndSanitizePath(srcPath);
			destPath = checkAndSanitizePath(destPath);
			if (!mayWrite()) {
				this.stateReadLock.unlock();
				return ErrorCode.EROFS;
			}
			final String srcAddress = getAddress(srcPath);
			final String destAddress = getAddress(destPath);
			if ((srcAddress == null) || (destAddress == null)) {
				result = ErrorCode.EINVAL;
			} else {
				try {
					if (!this.sardine.exists(srcAddress)) {
						result = ErrorCode.ENOENT;
					} else if (this.sardine.exists(destAddress)) {
						result = ErrorCode.EEXIST;
					} else {
						this.sardine.move(srcAddress, destAddress, false);
					}
				} catch (final IOException e) {
					this.logConnector.log(e, "unable to move from" + srcAddress + " to " + destAddress);
					result = ErrorCode.EIO;
				}
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return CommandResultHelper.getDefaultResultFail();
		}
		if (command.equals(GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES)) {
			return this.configHelper.getAllValues(CommandResultHelper.getDefaultResultOk());
		} else if (command.equals(GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES) && (properties != null)) {
			if (updateConfig(properties)) {
				return CommandResultHelper.getDefaultResultOk();
			}
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onModuleCommand(framework.model.Port, java.lang.String, java.lang.String[], java.util.Map) */
	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		if (port == this.providerPort) {
			this.providerPortConnected = true;
		}
		manageState();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		if (port == this.providerPort) {
			this.providerPortConnected = false;
		}
		manageState();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#onStateRequest(framework.model.ProviderPort) */
	@Override
	public void onStateRequest(final ProviderPort port) {
		try {
			int state = 0;
			if (this.webDavReady) {
				state |= ModuleStateType.READY;
			}
			if (this.forceReadonlyFS) {
				state |= ModuleStateType.READONLY;
			}
			this.providerConnector.sendState(port, state);
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#readData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public InputStream readData(final ProviderPort port, String[] path) throws ModuleException {
		InputStream result = null;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			path = checkAndSanitizePath(path);
			final String address = getAddress(path);
			if (address != null) {
				try {
					result = this.sardine.get(address);
				} catch (final IOException e) {
					this.logConnector.log(e, "unable to read data from " + address);
				}
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/**
	 * Sets up and starts the webDAV client and connection.
	 *
	 * @return true, if successful
	 */
	private boolean startWebDav() {
		boolean result = false;
		this.stateWriteLock.lock();
		this.baseAddress = this.configHelper.getString(WebDavClientModule.CONFIG_PROPERTY_KEY___BASE_ADDRESS, null);
		this.forceReadonlyFS = this.configHelper.getBoolean(WebDavClientModule.CONFIG_PROPERTY_KEY___FOREC_RO, WebDavClientModule.DEFAULT_CONFIG_VALUE___FORCE_READONLY_FS);
		this.supportLocking = this.configHelper.getBoolean(WebDavClientModule.CONFIG_PROPERTY_KEY___SUPPORT_LOCKING, WebDavClientModule.DEFAULT_CONFIG_VALUE___SUPPORT_LOCKING);
		this.minRefreshIntervalSeconds = this.configHelper.getInteger(WebDavClientModule.CONFIG_PROPERTY_KEY___MIN_REF_IVAL_SECS, WebDavClientModule.DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS);
		this.mediumRefreshIntervalSeconds = this.configHelper.getInteger(WebDavClientModule.CONFIG_PROPERTY_KEY___MEDIUM_REF_IVAL_SECS, WebDavClientModule.DEFAULT_CONFIG_VALUE___MEDIUM_REFRESH_INTERVAL_SECS);
		this.maxRefreshIntervalSeconds = this.configHelper.getInteger(WebDavClientModule.CONFIG_PROPERTY_KEY___MAX_REF_IVAL_SECS, WebDavClientModule.DEFAULT_CONFIG_VALUE___MAX_REFRESH_INTERVAL_SECS);
		final String username = this.configHelper.getString(WebDavClientModule.CONFIG_PROPERTY_KEY___USERNAME, null);
		final String password = this.configHelper.getString(WebDavClientModule.CONFIG_PROPERTY_KEY___PASSWORD, null);
		if ((username != null) && (password != null)) {
			this.sardine = SardineFactory.begin(username, password);
		} else {
			this.sardine = SardineFactory.begin();
		}
		if (!checkFolder(this.baseAddress)) {
			this.logConnector.log(LogEventLevelType.ERROR, "unable to stat remote base folder");
			this.sardine = null;
		} else {
			if (this.configHelper.getBoolean(WebDavClientModule.CONFIG_PROPERTY_KEY___COMPRESSION, WebDavClientModule.DEFAULT_CONFIG_VALUE___ENABLE_COMPRESSION)) {
				this.sardine.enableCompression();
			}
			result = true;
		}
		this.stateWriteLock.unlock();
		if (this.configHelper.getBoolean(WebDavClientModule.CONFIG_PROPERTY_KEY___MONITOR_FS, WebDavClientModule.DEFAULT_CONFIG_VALUE___MONITOR_FS)) {
			this.fsMonitorThread = new MonitorThread();
			this.fsMonitorThread.start();
		}
		return result;
	}

	/**
	 * Stops the webDAV client.
	 *
	 * @return true, if successful
	 */
	private boolean stopWebDav() {
		if (this.fsMonitorThread != null) {
			this.fsMonitorThread.interrupt();
		}
		this.stateWriteLock.lock();
		try {
			this.sardine.shutdown();
		} catch (final IOException e) {
			this.logConnector.log(e, "unable to stop Sardine WebDAV client");
		}
		this.sardine = null;
		this.currentlyHeldWebDavLocks.clear();
		this.stateWriteLock.unlock();
		return true;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#unlock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int unlock(final ProviderPort port, String[] path) throws ModuleException {
		int result = ErrorCode.ENOSYS;
		this.stateReadLock.lock();
		String address = null;
		try {
			checkForOperationalStateAndPort(port);
			if (this.supportLocking) {
				path = checkAndSanitizePath(path);
				address = getAddress(path);
				if (address != null) {
					final String lockToken = this.currentlyHeldWebDavLocks.remove(address);
					if (lockToken != null) {
						this.sardine.unlock(address, lockToken);
						result = 0;
					} else {
						result = ErrorCode.EINVAL;
					}
				}
			}
		} catch (final IOException e) {
			this.logConnector.log(e, "unable to unlock " + address);
			result = ErrorCode.EIO;
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}

	/**
	 * Updates configuration.
	 *
	 * @param props the props
	 * @return true, if successful
	 */
	private boolean updateConfig(final Map<String, String> props) {
		this.stateWriteLock.lock();
		final boolean result = this.configHelper.updateAllValues(props, false);
		this.stateWriteLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#writeData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public OutputStream writeData(final ProviderPort port, final String[] path) throws ModuleException {
		OutputStream result = null;
		this.stateReadLock.lock();
		try {
			checkForOperationalStateAndPort(port);
			final String[] sanitizedPath = checkAndSanitizePath(path);
			if (!mayWrite()) {
				this.stateReadLock.unlock();
				return null;
			}
			final String address = getAddress(sanitizedPath);
			final String parentAddress = getParentAddress(sanitizedPath);
			if ((address != null) && (parentAddress != null) && checkFolder(parentAddress)) {
				result = new WebDavOutputStream(this.sardine, address);
			}
		} finally {
			this.stateReadLock.unlock();
		}
		return result;
	}
}
