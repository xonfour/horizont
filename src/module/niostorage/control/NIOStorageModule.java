package module.niostorage.control;

import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.PersistentConfigurationHelper;
import helper.TextFormatHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import module.iface.AbstractProvider;
import module.iface.ErrorCode;
import module.iface.Provider;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import db.iface.ComponentConfigurationController;
import framework.constants.Constants;
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
 * Module to access user data by using the JAVA NIO API added in Java 7.
 * <p>
 * TODO: Implement proper locking mechanisms where required.
 *
 * @author Stefan Werner
 */
public class NIOStorageModule extends AbstractProvider {

	/**
	 * Element change monitor utilizing the NIO watcher service.
	 * <p>
	 * Inspired by http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java (2.5.2014).
	 */
	private class ExternalMonitorThread extends Thread {

		@Override
		public void run() {
			try {
				NIOStorageModule.this.watcher = FileSystems.getDefault().newWatchService();
				registerAll(NIOStorageModule.this.basePath);
			} catch (final IOException e) {
				NIOStorageModule.this.logConnector.log(e);
				return;
			}
			while ((NIOStorageModule.this.watcher != null) && !Thread.currentThread().isInterrupted()) {
				WatchKey key;
				try {
					key = NIOStorageModule.this.watcher.take();
				} catch (final InterruptedException e) {
					return;
				}

				final Path dir = NIOStorageModule.this.keys.get(key);
				if (dir == null) {
					continue;
				}

				final Map<Path, Kind<?>> events = new HashMap<Path, Kind<?>>();

				for (final WatchEvent<?> event : key.pollEvents()) {
					final Kind<?> kind = event.kind();

					if (kind == StandardWatchEventKinds.OVERFLOW) {
						continue;
						// ignore missed events for now
					}

					// Context for directory entry event is the file name of entry
					final Object context = event.context();
					if (context instanceof Path) {
						final Path name = (Path) context;
						final Path child = dir.resolve(name);

						if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
							try {
								if (Files.isDirectory(child)) {
									registerAll(child);
								}
							} catch (final IOException e) {
								NIOStorageModule.this.logConnector.log(e);
								NIOStorageModule.this.logConnector.log(LogEventLevelType.WARNING, "cannot monitor directory " + child.toString());
							}
						}
						events.put(child, kind);
					}
				}

				// reset key and remove from set if directory no longer accessible
				final boolean valid = key.reset();
				if (!valid) {
					NIOStorageModule.this.keys.remove(key);
				}

				if (!events.isEmpty()) {
					try {
						NIOStorageModule.this.eventWorkerThread.execute(new Runnable() {

							@Override
							public void run() {
								for (final Path path : events.keySet()) {
									final Kind<?> kind = events.get(path);
									final String[] pathArray = getPathArray(NIOStorageModule.this.basePath.relativize(path));
									if (pathArray != null) {
										final DataElement fsElement = getElementInternal(pathArray, path);
										if (fsElement != null) {
											try {
												if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
													NIOStorageModule.this.providerConnector.sendElementEvent(NIOStorageModule.this.port, fsElement, DataElementEventType.ADD);
												} else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
													NIOStorageModule.this.providerConnector.sendElementEvent(NIOStorageModule.this.port, fsElement, DataElementEventType.MODIFY);
												}
											} catch (final BrokerException e) {
												NIOStorageModule.this.logConnector.log(e);
											}
										} else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
											try {
												NIOStorageModule.this.providerConnector.sendElementEvent(NIOStorageModule.this.port, new DataElement(pathArray), DataElementEventType.DELETE);
											} catch (final BrokerException e) {
												NIOStorageModule.this.logConnector.log(e);
											}
										}
									}
								}
							}
						});
					} catch (final RejectedExecutionException e) {
						// ignored
					}
				}
			}
			try {
				NIOStorageModule.this.watcher.close();
			} catch (final IOException e) {
				// ignored
			}
		}
	}

	/**
	 * Internal element change monitor with regular element checks.
	 */
	private class InternalMonitorThread extends Thread {

		private Map<String, DataElement> elementMap;

		@Override
		public void run() {
			while (!isInterrupted()) {
				try {
					final Map<String, DataElement> newElementMap = new ConcurrentHashMap<String, DataElement>();
					final Set<DataElement> newElements = getChildElements(NIOStorageModule.this.port, new String[0], true);
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
											NIOStorageModule.this.providerConnector.sendElementEvent(NIOStorageModule.this.port, newElement, DataElementEventType.ADD);
										} catch (final BrokerException e) {
											NIOStorageModule.this.logConnector.log(e);
										}
									} else if (!element.equals(newElement)) {
										try {
											NIOStorageModule.this.providerConnector.sendElementEvent(NIOStorageModule.this.port, newElement, DataElementEventType.MODIFY);
										} catch (final BrokerException e) {
											NIOStorageModule.this.logConnector.log(e);
										}
									}
								} else {
									error = true;
								}
							}
							if (!error) {
								for (final DataElement element : this.elementMap.values()) {
									try {
										NIOStorageModule.this.providerConnector.sendElementEvent(NIOStorageModule.this.port, element, DataElementEventType.DELETE);
									} catch (final BrokerException e) {
										NIOStorageModule.this.logConnector.log(e);
									}
								}
							}
						}
						this.elementMap = newElementMap;
					}
					if (this.elementMap != null) {
						NIOStorageModule.this.currentRefreshInterval = Math.min(((this.elementMap.size() / 1000) * (NIOStorageModule.this.mediumRefreshIntervalSeconds - NIOStorageModule.this.minRefreshIntervalSeconds)) + NIOStorageModule.this.minRefreshIntervalSeconds, NIOStorageModule.this.maxRefreshIntervalSeconds);
					}
				} catch (final ModuleException e) {
					NIOStorageModule.this.logConnector.log(e);
				}
				try {
					TimeUnit.SECONDS.sleep(NIOStorageModule.this.currentRefreshInterval);
				} catch (final InterruptedException e) {
					break;
				}
			}
		}
	}

	private static final String CONFIG_PROP_KEY___FORCE_RO = "force_ro";
	private static final String CONFIG_PROP_KEY___MAX_REF_IVAL_SECS = "max_refresh_interval_seconds";
	private static final String CONFIG_PROP_KEY___MEDIUM_REF_IVAL_SECS = "medium_refresh_interval_seconds";
	private static final String CONFIG_PROP_KEY___MIN_REF_IVAL_SECS = "min_refresh_interval_seconds";
	private static final String CONFIG_PROP_KEY___MONITOR_FS = "monitor_fs";
	private static final String CONFIG_PROP_KEY___OPTIONAL_FS_PROPS = "opt_fs_props";
	private static final String CONFIG_PROP_KEY___PATH = "path";
	private static final String CONFIG_PROP_KEY___PROTOCOL = "protocol";
	private static final String CONFIG_PROP_KEY___USE_INTERNAL_MONITORING = "use_internal_monitoring";
	private static final String[] DB___CONFIG_DATA_PATH = { "config_data" };
	private static final String DB___DOMAIN___CONFIG = "config";
	private static final int DEFAULT_CONFIG_VALUE___MAX_REFRESH_INTERVAL_SECS = 3600;
	private static final int DEFAULT_CONFIG_VALUE___MEDIUM_REFRESH_INTERVAL_SECS = 900;
	private static final int DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS = 300;
	private static final boolean DEFAULT_CONFIG_VALUE___USE_INTERNAL_MONITORING = false;
	private static final String PORT_ID = "port";

	private Path basePath = null;
	private PersistentConfigurationHelper configHelper;
	private boolean connected = false;
	private int currentRefreshInterval = NIOStorageModule.DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS;
	private ExecutorService eventWorkerThread;
	private FileSystem fileSystem = null;
	private boolean forceReadOnly = false;
	private final Map<WatchKey, Path> keys = new HashMap<WatchKey, Path>();
	private final int maxRefreshIntervalSeconds = NIOStorageModule.DEFAULT_CONFIG_VALUE___MAX_REFRESH_INTERVAL_SECS;
	private final int mediumRefreshIntervalSeconds = NIOStorageModule.DEFAULT_CONFIG_VALUE___MEDIUM_REFRESH_INTERVAL_SECS;
	private final int minRefreshIntervalSeconds = NIOStorageModule.DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS;
	private boolean monitorFilesystem = true;
	private Thread monitorThread;
	private final Map<String, String> optFSProps = new HashMap<String, String>();
	private String pathName = null;
	private ProviderPort port;
	private String protocol = "file";
	private boolean readOnly = false;
	private boolean ready = false;
	private boolean running = false;
	private WatchService watcher;

	/**
	 * Instantiates a new NIO storage module.
	 *
	 * @param providerBrokerConnector the provider broker connector
	 * @param componentConfiguration the component configuration
	 * @param logConnector the log connector
	 */
	public NIOStorageModule(final ProviderConnector providerBrokerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(providerBrokerConnector, componentConfiguration, logConnector);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#checkAndLock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public synchronized int checkAndLock(final ProviderPort port, final String[] path) throws ModuleException {
		// no locking supported for now
		return ErrorCode.ENOSYS;
	}

	/**
	 * Checks own rights.
	 *
	 * @return true, if OK
	 */
	private boolean checkRights() {
		// currently this module does not require any special rights. In the future this can be used to check for certain SecurityManager rights, for example
		// real file
		// system access etc.
		return true;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#createDirectory(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int createFolder(final ProviderPort port, final String[] path) throws ModuleException {
		if (!mayReadWrite()) {
			return -1;
		}
		final Path absPath = getAbsolutePath(path);
		try {
			if (Files.exists(absPath)) {
				return 1;
			} else {
				Files.createDirectories(absPath);
				return 0;
			}
		} catch (final IOException e) {
			this.logConnector.log(e);
			return Provider.RESULT_CODE___ERROR_GENERAL;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#delete(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int delete(final ProviderPort port, final String[] path) throws ModuleException {
		if (!mayReadWrite()) {
			return -1;
		}
		final Path absPath = getAbsolutePath(path);
		try {
			if (Files.notExists(absPath)) {
				return Provider.RESULT_CODE___ERROR_NO_SUCH_FILE;
			} else {
				if (Files.isDirectory(absPath)) {
					recursiveDelete(absPath);
				} else {
					Files.delete(absPath);
				}
				return Provider.RESULT_CODE___OK;
			}
		} catch (final IOException e) {
			this.logConnector.log(e);
			return Provider.RESULT_CODE___ERROR_GENERAL;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#shutdown() */
	@Override
	public void enterShutdown() {
		if ((this.monitorThread != null) && !this.monitorThread.isInterrupted()) {
			this.monitorThread.interrupt();
		}
		if (this.eventWorkerThread != null) {
			this.eventWorkerThread.shutdown();
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#startup() */
	@Override
	public void enterStartup() {
		if ((this.pathName != null) && !this.pathName.isEmpty() && (this.protocol != null) && !this.protocol.isEmpty()) {
			try {
				final URI uri = new URI(this.protocol, "/", null);
				try {
					this.fileSystem = FileSystems.getFileSystem(uri);
				} catch (final FileSystemNotFoundException e1) {
					this.fileSystem = FileSystems.newFileSystem(uri, this.optFSProps);
				}
				this.basePath = this.fileSystem.getPath(this.pathName);
			} catch (URISyntaxException | IllegalArgumentException | IOException e) {
				this.logConnector.log(e, "unable to connect to filesystem");
				return;
			}
			if ((this.basePath != null) && Files.exists(this.basePath)) {
				String threadNamePrefix = "?";
				try {
					threadNamePrefix = this.componentConfiguration.getComponentName() + "-" + this.getClass().getSimpleName() + "-%d";
				} catch (final DatabaseException e) {
					this.logConnector.log(e);
				}
				this.eventWorkerThread = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(threadNamePrefix).build());
				if (Files.isReadable(this.basePath)) {
					if (!Files.isWritable(this.basePath)) {
						this.logConnector.log(LogEventLevelType.WARNING, "readonly filesystem");
						this.readOnly = true;
					}
					this.logConnector.log(LogEventLevelType.INFO, "ready");
					this.ready = true;
				} else {
					this.logConnector.log(LogEventLevelType.ERROR, "unable to read filesystem");
				}
			}
			// filesystem öffnen
			if (!this.ready) {
				this.running = false;
				this.logConnector.log(LogEventLevelType.WARNING, "module not running");
			} else {
				this.running = true;
				initializeFSMonitor();
			}
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		// TODO: Close file system?
		this.running = false;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		sendStateSignal();
	}

	/**
	 * Gets the absolute path of a given (relative) path.
	 *
	 * @param path the path
	 * @return the absolute path
	 */
	private Path getAbsolutePath(final String[] path) {
		// TODO: Sanitize path (e.g. check for ".."), currently done in ObjectValidator.
		Path relPath = null;
		if (path.length == 0) {
			return this.basePath;
		} else if (path.length == 1) {
			relPath = this.fileSystem.getPath(path[0]).normalize();
		} else {
			relPath = this.fileSystem.getPath(path[0], Arrays.copyOfRange(path, 1, path.length)).normalize();
		}
		return this.basePath.resolve(relPath.normalize());
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getChildElements(framework.model.ProviderPort, java.lang.String[], boolean) */
	@Override
	public Set<DataElement> getChildElements(final ProviderPort port, final String[] path, final boolean recursive) throws ModuleException {
		mayRead();
		final Set<DataElement> result = new TreeSet<DataElement>();
		final Set<FileVisitOption> options = EnumSet.of(FileVisitOption.FOLLOW_LINKS); // TODO: should be configurable in the future
		final FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(final Path child, final BasicFileAttributes attributes) {
				final DataElement element = getElementInternal(getPathArray(NIOStorageModule.this.basePath.relativize(child)), child);
				result.add(element);
				return FileVisitResult.CONTINUE;
			}
		};
		try {
			if (recursive) {
				Files.walkFileTree(getAbsolutePath(path), options, Constants.MAX_PATH_DEPTH, visitor);
			} else {
				Files.walkFileTree(getAbsolutePath(path), options, 1, visitor);
			}
		} catch (final IOException e) {
			this.logConnector.log(e);
			return null;
		}
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getFSElement(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElement getElement(final ProviderPort port, final String[] path) throws ModuleException {
		mayRead();
		return getElementInternal(path, getAbsolutePath(path));
	}

	/**
	 * Gets an element (internal method)
	 *
	 * @param pathArray the path array representation of the path
	 * @param path the path
	 * @return the element internal
	 */
	private DataElement getElementInternal(final String[] pathArray, final Path path) {
		try {
			final BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
			long modTime = 0;
			if (attributes.lastModifiedTime() != null) {
				modTime = attributes.lastModifiedTime().toMillis();
			}
			if (attributes.isDirectory()) {
				return new DataElement(pathArray, DataElementType.FOLDER, attributes.size(), modTime);
			} else if (attributes.isRegularFile()) {
				return new DataElement(pathArray, DataElementType.FILE, attributes.size(), modTime);
			} else {
				return new DataElement(pathArray, DataElementType.OTHER, attributes.size(), modTime);
			}
		} catch (final IOException e) {
			if (e instanceof NoSuchFileException) {
				this.logConnector.log(LogEventLevelType.DEBUG, "file for path " + path.toString() + " does not exist");
				return null;
			} else {
				this.logConnector.log(e);
				return null;
			}
		}
	}

	/**
	 * Gets a path array from a path.
	 *
	 * @param path the path
	 * @return the path array
	 */
	private String[] getPathArray(final Path path) {
		if (path.isAbsolute()) {
			// only relative paths are allowed, just a little security measure
			return null;
		}
		final List<String> pathElemList = new ArrayList<String>();
		for (final Path elem : path) {
			pathElemList.add(elem.toString());
		}
		return pathElemList.toArray(new String[0]);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return ImmutableSet.copyOf(GenericControlInterfaceCommands.DEFAULT_SUPPORT___CONFIG);
	}

	@Override
	public Set<String> getSupportedModuleCommands(final Port port, final String[] path) {
		return new HashSet<String>(); // currently no commands supported
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getType(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElementType getType(final ProviderPort port, final String[] path) throws ModuleException {
		mayRead();
		final Path absPath = getAbsolutePath(path);
		try {
			if (Files.notExists(absPath)) {
				return null;
			}
			final BasicFileAttributes attributes = Files.readAttributes(absPath, BasicFileAttributes.class);
			if (attributes.isDirectory()) {
				return DataElementType.FOLDER;
			} else if (attributes.isRegularFile()) {
				return DataElementType.FILE;
			} else {
				return DataElementType.OTHER;
			}
		} catch (final IOException e) {
			this.logConnector.log(e);
			return DataElementType.NONEXISTENT_OR_UNKNOWN;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		initializeConfig();
		try {
			this.port = this.providerConnector.registerProviderPort(this, NIOStorageModule.PORT_ID, -1);
		} catch (final BrokerException e) {
			this.logConnector.log(e);
			this.ready = false;
		}
	}

	/**
	 * Initializes the configuration.
	 */
	private void initializeConfig() {
		try {
			this.configHelper = new PersistentConfigurationHelper(this.componentConfiguration, NIOStorageModule.DB___DOMAIN___CONFIG, NIOStorageModule.DB___CONFIG_DATA_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.WARNING, "no persistent config");
			this.configHelper = new PersistentConfigurationHelper();
		}

		parseOptionalFSProps();

		ConfigValue cv;
		String key;

		key = NIOStorageModule.CONFIG_PROP_KEY___MIN_REF_IVAL_SECS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueInteger(NIOStorageModule.DEFAULT_CONFIG_VALUE___MIN_REFRESH_INTERVAL_SECS);
			cv.setDescriptionString("Internal monitoring: Lowest refresh interval (for 1 element).");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = NIOStorageModule.CONFIG_PROP_KEY___MEDIUM_REF_IVAL_SECS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueInteger(NIOStorageModule.DEFAULT_CONFIG_VALUE___MEDIUM_REFRESH_INTERVAL_SECS);
			cv.setDescriptionString("Internal monitoring: Medium refresh interval (for 1000 elements).");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = NIOStorageModule.CONFIG_PROP_KEY___MAX_REF_IVAL_SECS;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueInteger(NIOStorageModule.DEFAULT_CONFIG_VALUE___MAX_REFRESH_INTERVAL_SECS);
			cv.setDescriptionString("Internal monitoring: Maximum refresh interval.");
			this.configHelper.updateConfigValue(key, cv, true);
		}

		key = NIOStorageModule.CONFIG_PROP_KEY___USE_INTERNAL_MONITORING;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(NIOStorageModule.DEFAULT_CONFIG_VALUE___USE_INTERNAL_MONITORING);
			cv.setDescriptionString("Use internal polling mechanism to monitor filesystem (instead of external file system provider).");
			this.configHelper.updateConfigValue(key, cv, true);
		}// TODO: If changed stop old monitor and start new one.

		this.pathName = this.configHelper.getString(NIOStorageModule.CONFIG_PROP_KEY___PATH, null);
		this.forceReadOnly = this.configHelper.getBoolean(NIOStorageModule.CONFIG_PROP_KEY___FORCE_RO, this.forceReadOnly);
		this.monitorFilesystem = this.configHelper.getBoolean(NIOStorageModule.CONFIG_PROP_KEY___MONITOR_FS, this.monitorFilesystem);
		this.protocol = this.configHelper.getString(NIOStorageModule.CONFIG_PROP_KEY___PROTOCOL, this.protocol);
	}

	/**
	 * Initializes the file system monitor (internal or external).
	 */
	private void initializeFSMonitor() {
		if ((this.monitorThread != null) && !this.monitorThread.isInterrupted()) {
			this.monitorThread.interrupt();
		}
		if (this.monitorFilesystem) {
			if (this.configHelper.getBoolean(NIOStorageModule.CONFIG_PROP_KEY___USE_INTERNAL_MONITORING, NIOStorageModule.DEFAULT_CONFIG_VALUE___USE_INTERNAL_MONITORING)) {
				this.monitorThread = new InternalMonitorThread();
			} else {
				this.monitorThread = new ExternalMonitorThread();
			}
			this.monitorThread.start();
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return this.ready && checkRights();
	}

	/**
	 * Checks if the module may read data.
	 *
	 * @throws ModuleException if the module is not ready
	 */
	private void mayRead() throws ModuleException {
		if (!this.ready) {
			throw new ModuleException("module not ready");
		}
	}

	/**
	 * Checks if the module may read AND write data.
	 *
	 * @return true, if OK
	 * @throws ModuleException if the module is not ready
	 */
	private boolean mayReadWrite() throws ModuleException {
		mayRead();
		if (this.readOnly || this.forceReadOnly) {
			return false;
		} else {
			return true;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#move(framework.model.ProviderPort, java.lang.String[], java.lang.String[]) */
	@Override
	public int move(final ProviderPort port, final String[] srcPath, final String[] destPath) throws ModuleException {
		if (!mayReadWrite()) {
			return -1;
		}
		final Path srcAbsPath = getAbsolutePath(srcPath);
		final Path destAbsPath = getAbsolutePath(destPath);
		try {
			if (Files.notExists(srcAbsPath)) {
				return Provider.RESULT_CODE___ERROR_NO_SUCH_FILE;
			} else if (Files.exists(destAbsPath)) {
				return Provider.RESULT_CODE___ERROR_ALREADY_EXISTENT;
			}
			if (Files.notExists(destAbsPath.getParent())) {
				final int i = createFolder(port, getPathArray(destAbsPath.getParent()));
				if (i != 0) {
					return Provider.RESULT_CODE___ERROR_GENERAL;
				}
			}
			Files.move(srcAbsPath, destAbsPath);
			return 0;
		} catch (final IOException e) {
			this.logConnector.log(e);
			return Provider.RESULT_CODE___ERROR_GENERAL;
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return null;
		}
		// TODO: Currently we have a mix of the old and new configuration system, so move to initializeConfig() and only use new system.
		if (command.equals(GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES)) {
			final ConfigValue configValueForceRO = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___FORCE_RO);
			configValueForceRO.setCurrentValueBoolean(this.forceReadOnly);
			configValueForceRO.setDescriptionString("Force read only even if file system is writable.");
			final ConfigValue configValueMonitorFS = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___MONITOR_FS);
			configValueMonitorFS.setCurrentValueBoolean(this.monitorFilesystem);
			configValueMonitorFS.setDescriptionString("Monitor file system for changes.");
			final ConfigValue configValueProtocol = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___PROTOCOL);
			configValueProtocol.setCurrentValueString(this.protocol);
			final Set<String> protocols = new HashSet<String>();
			for (final FileSystemProvider p : FileSystemProvider.installedProviders()) {
				try {
					protocols.add(p.getScheme());
				} catch (final Exception e) {
					// ignore
				}
			}
			configValueProtocol.setOptionsString(protocols.toArray(new String[0]));
			configValueProtocol.setDescriptionString("The Java NIO file system protocol.");
			final ConfigValue configValuePath = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___PATH);
			configValuePath.setCurrentValueString(this.pathName);
			configValuePath.setDescriptionString("The path/URL of the file system.");
			ConfigValue configValueOptionalProps = this.configHelper.getConfigValue(NIOStorageModule.CONFIG_PROP_KEY___OPTIONAL_FS_PROPS);
			if (configValueOptionalProps == null) {
				configValueOptionalProps = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___OPTIONAL_FS_PROPS);
				configValueOptionalProps.setCurrentValueStringArray((String) null);
				configValueOptionalProps.setDescriptionString("Optional provider specific properties for file system.");
			}
			// TODO: Better use configHelper.getAllValues(CommandResultHelper.getDefaultResultOk()) to get values.
			return CommandResultHelper.getDefaultResultOk(NIOStorageModule.CONFIG_PROP_KEY___FORCE_RO, configValueForceRO.toString(), NIOStorageModule.CONFIG_PROP_KEY___MONITOR_FS, configValueMonitorFS.toString(), NIOStorageModule.CONFIG_PROP_KEY___PATH, configValuePath.toString(), NIOStorageModule.CONFIG_PROP_KEY___PROTOCOL, configValueProtocol.toString(), NIOStorageModule.CONFIG_PROP_KEY___OPTIONAL_FS_PROPS, configValueOptionalProps.toString(), NIOStorageModule.CONFIG_PROP_KEY___MAX_REF_IVAL_SECS, this.configHelper.getConfigValue(NIOStorageModule.CONFIG_PROP_KEY___MAX_REF_IVAL_SECS).toString(), NIOStorageModule.CONFIG_PROP_KEY___MIN_REF_IVAL_SECS, this.configHelper.getConfigValue(NIOStorageModule.CONFIG_PROP_KEY___MIN_REF_IVAL_SECS).toString(), NIOStorageModule.CONFIG_PROP_KEY___MEDIUM_REF_IVAL_SECS, this.configHelper.getConfigValue(NIOStorageModule.CONFIG_PROP_KEY___MEDIUM_REF_IVAL_SECS).toString(), NIOStorageModule.CONFIG_PROP_KEY___USE_INTERNAL_MONITORING, this.configHelper.getConfigValue(NIOStorageModule.CONFIG_PROP_KEY___USE_INTERNAL_MONITORING).toString());
		} else if (command.equals(GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES) && (properties != null)) {
			boolean result = false;
			if (this.configHelper.updateAllValues(properties, false)) {
				initializeConfig();
				result = true;
			}

			final ConfigValue configValueOptionalProps = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___OPTIONAL_FS_PROPS, properties.get(NIOStorageModule.CONFIG_PROP_KEY___OPTIONAL_FS_PROPS));
			final ConfigValue configValueForceRO = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___FORCE_RO, properties.get(NIOStorageModule.CONFIG_PROP_KEY___FORCE_RO));
			final ConfigValue configValueMonitorFS = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___MONITOR_FS, properties.get(NIOStorageModule.CONFIG_PROP_KEY___MONITOR_FS));
			final ConfigValue configValueProtocol = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___PROTOCOL, properties.get(NIOStorageModule.CONFIG_PROP_KEY___PROTOCOL));
			final ConfigValue configValuePath = new ConfigValue(NIOStorageModule.CONFIG_PROP_KEY___PATH, properties.get(NIOStorageModule.CONFIG_PROP_KEY___PATH));
			if (configValueOptionalProps.isValid()) {
				this.configHelper.updateConfigValue(NIOStorageModule.CONFIG_PROP_KEY___OPTIONAL_FS_PROPS, configValueOptionalProps, true);
				result = true;
			}
			if (configValueForceRO.isValid()) {
				this.forceReadOnly = configValueForceRO.getCurrentValueBoolean();
				this.configHelper.updateBoolean(NIOStorageModule.CONFIG_PROP_KEY___FORCE_RO, this.forceReadOnly);
				result = true;
			}
			if (configValueMonitorFS.isValid()) {
				if (this.running && (this.monitorFilesystem != configValueMonitorFS.getCurrentValueBoolean())) {
					return CommandResultHelper.getDefaultResultFail("reason", "is_running");
				}
				this.monitorFilesystem = configValueMonitorFS.getCurrentValueBoolean();
				this.configHelper.updateBoolean(NIOStorageModule.CONFIG_PROP_KEY___MONITOR_FS, this.monitorFilesystem);
				result = true;
			}
			if (configValueProtocol.isValid()) {
				if (this.running && !this.protocol.equals(configValueProtocol.getCurrentValueString())) {
					return CommandResultHelper.getDefaultResultFail("reason", "is_running");
				}
				this.protocol = configValueProtocol.getCurrentValueString();
				this.configHelper.updateString(NIOStorageModule.CONFIG_PROP_KEY___PROTOCOL, this.protocol);
				result = true;
			}
			if (configValuePath.isValid()) {
				if (this.running && !this.pathName.equals(configValuePath.getCurrentValueString())) {
					return CommandResultHelper.getDefaultResultFail("reason", "is_running");
				}
				this.pathName = configValuePath.getCurrentValueString();
				this.configHelper.updateString(NIOStorageModule.CONFIG_PROP_KEY___PATH, this.pathName);
				result = true;
			}
			if (result) {
				return CommandResultHelper.getDefaultResultOk();
			}
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#notifyConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		if (port == this.port) {
			this.connected = true;
			sendStateSignal();
		}
		// TODO: Ignored for now, should be used to connect and to start monitor thread.
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#notifyDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		if (port == this.port) {
			this.connected = false;
		}
		// TODO: Ignored for now, should be used to disconnect/cleanup and to stop monitor thread.
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#getState(framework.model.ProviderPort) */
	@Override
	public void onStateRequest(final ProviderPort port) {
		sendStateSignal();
	}

	/**
	 * Parses optional file system properties (currently used for the Dropbox authorization token).
	 */
	private void parseOptionalFSProps() {
		final ConfigValue cv = this.configHelper.getConfigValue(NIOStorageModule.CONFIG_PROP_KEY___OPTIONAL_FS_PROPS);
		if ((cv != null) && cv.isString() && cv.isArray() && cv.isValid()) {
			final String[] optFSPropsStrings = cv.getCurrentValueStringArray();
			if (optFSPropsStrings != null) {
				for (final String s : optFSPropsStrings) {
					final String[] kv = s.split("=");
					if (kv.length == 2) {
						this.optFSProps.put(kv[0], kv[1]);
					}
				}
			}
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#readData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public InputStream readData(final ProviderPort port, final String[] path) throws ModuleException {
		mayRead();
		final Path absPath = getAbsolutePath(path);
		try {
			return Files.newInputStream(absPath);
		} catch (final IOException e) {
			this.logConnector.log(e);
			return null;
		}
	}

	/**
	 * Deletes an element and all of it's children.
	 *
	 * @param absPath the absolute path to delete
	 * @throws IOException if an I/O exception has occurred
	 */
	private void recursiveDelete(final Path absPath) throws IOException {
		Files.walkFileTree(absPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

		});
	}

	/**
	 * Registers for folder for monitoring with NIO API.
	 *
	 * @param path the path
	 * @throws IOException if an I/O exception has occurred
	 */
	private void register(final Path path) throws IOException {
		final WatchKey key = path.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
		this.keys.put(key, path);
	}

	/**
	 * Registers folders for monitoring with NIO API recursively.
	 *
	 * @param start the start path
	 * @throws IOException if an I/O exception has occurred
	 */
	private void registerAll(final Path start) throws IOException {
		Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(final Path path, final BasicFileAttributes attrs) throws IOException {
				try {
					register(path);
				} catch (final IOException e) {
					NIOStorageModule.this.logConnector.log(e);
					NIOStorageModule.this.logConnector.log(LogEventLevelType.WARNING, "cannot monitor directory " + path.toString());
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Sends state signal.
	 */
	// TODO: Check connection state here.
	private void sendStateSignal() {
		try {
			if (this.running) {
				if (this.ready) {
					int state = ModuleStateType.READY;
					if (this.readOnly || this.forceReadOnly) {
						state |= ModuleStateType.READONLY;
					}
					if (this.connected) {
						this.providerConnector.sendState(this.port, state);
					} else {
						this.providerConnector.sendState(this.port, 0);
					}
				} else {
					if (this.connected) {
						this.providerConnector.sendState(this.port, 0);
					}
				}
			}
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#unlock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public synchronized int unlock(final ProviderPort port, final String[] path) throws ModuleException {
		// no locking supported for now
		return ErrorCode.ENOSYS;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Provider#writeData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public OutputStream writeData(final ProviderPort port, final String[] path) throws ModuleException {
		if (path.length == 0) {
			return null;
		}
		if (!mayReadWrite()) {
			return null;
		}
		final Path absPath = getAbsolutePath(path);
		if (Files.notExists(absPath.getParent())) {
			final int i = createFolder(port, Arrays.copyOfRange(path, 0, path.length - 1));
			if (i != 0) {
				return null;
			}
		}
		try {
			return Files.newOutputStream(absPath);
		} catch (final IOException e) {
			this.logConnector.log(e);
			return null;
		}
	}
}
