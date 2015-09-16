package module.pgpcrypto.control;

import helper.CommandResultHelper;
import helper.ConfigValue;
import helper.PersistentConfigurationHelper;
import helper.TextFormatHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import module.iface.AbstractProsumerProvider;
import module.iface.DataElementEventListener;
import module.iface.Provider;
import module.pgpcrypto.model.CryptoActionInfo;

import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import db.iface.ComponentConfigurationController;
import framework.constants.GenericControlInterfaceCommandProperties;
import framework.constants.GenericControlInterfaceCommands;
import framework.constants.GenericModuleCommandProperties;
import framework.constants.GenericModuleCommands;
import framework.constants.ModuleRight;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.control.ProviderConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.DatabaseException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.Port;
import framework.model.ProsumerPort;
import framework.model.ProviderPort;
import framework.model.event.DataElementEvent;
import framework.model.event.ProviderStateEvent;
import framework.model.event.type.DataElementEventType;
import framework.model.event.type.LogEventLevelType;
import framework.model.type.DataElementType;
import framework.model.type.ModuleStateType;

/**
 * Module to create a transparent encryption proxy using OpenPGP. Is wraps files in containers and also encrypts their names. Folder names are also encrypted.
 * Data may be shared with users by importing and verifying their public keys.
 * <p>
 * IMPORTANT: This module will try to hide files/folders which cannot be accessed by this instance. Folders may loose their plain text name if their info file
 * is not found. Inaccessible conflicting files/folders are moved on write (TODO: needs more testing). The size value in DataElements retrieved from this module
 * reflect the size of the encrypted container and will NOT equal the size of the unencrypted data.
 * <p>
 * TODO:<br>
 * - Sharing configuration could also be shared with a special file by trusted sharers. - Add module command to treat an element as if it was new (to force
 * resync after adding/removing keys/sharers).
 *
 * @author Stefan Werner
 */
public class PGPCryptoModule extends AbstractProsumerProvider implements DataElementEventListener {

	private static final long CACHE___EXPIRE_MINUTES = 120;
	public static final Integer CAPABILITY_ASYMMETRIC_CIPHER_SIZES = 3;
	public static final Integer CAPABILITY_ASYMMETRIC_CIPHERS = 2;
	public static final Integer CAPABILITY_BLOCK_CIPHER_SIZES = 1;
	public static final Integer CAPABILITY_BLOCK_CIPHERS = 0;
	public static final String COMMAND___ADD_ALL_SHARERS_TO_PATH = "add_all_sharers_to_path";
	public static final String COMMAND___BACKUP_ALL_KEYS = "backup_all_keys";
	public static final String COMMAND___BACKUP_PRIVATE_KEYS = "backup_secret_keys";
	public static final String COMMAND___CHANGE_PRIVATE_KEY = "change_private_key";
	public static final String COMMAND___CHECK_STATE = "check_state";
	public static final String COMMAND___EXPORT_OWN_PUBLIC_KEY = "export_own_pub_key";
	public static final String COMMAND___GENERATE_KEY = "generate_key";
	public static final String COMMAND___GET_PRIVATE_KEY_FINGERPRINT = "get_private_key_fingerprint";
	public static final String COMMAND___IMPORT_KEYS = "import_keys";
	public static final String COMMAND___INVALIDATE_CACHE = "inval_cache";
	public static final String COMMAND___MANAGE_PRIVATE_KEYS = "manage_sec_keys";
	public static final String COMMAND___MANAGE_PUBLIC_KEYS = "manage_pub_keys";
	public static final String COMMAND___RESTART_ENGINE = "restart_engine";
	public static final String COMMAND___SELECT_SHARES = "select_shares";
	public static final String COMMAND_PROPERTY_KEY___COMMAND = "command";
	public static final String COMMAND_PROPERTY_KEY___PATH = "path";
	private static final String CONFIG_DOMAIN = "config";
	private static final String[] CONFIG_PATH = { "config" };
	private static final String CONFIG_PROPERTY_KEY___USE_EXTERNAL_KEY_STORAGE = "use_external_key_storage";
	private static final String CONFLICTING_ELEMENT_SUFFIX = "___ACCESS_CONFLICT_";
	public static final String CRYPTO_DIRINFO_APPENDIX_UNENCRYPTED = "_cleartext_directory_name";
	public static final String CRYPTO_DIRINFO_FILENAME = ".dirinfo_do_not_delete";
	public static final int CRYPTO_HASH_ITERATIONS = 192;
	public static final int DEFAULT___ASYM_KEY_SIZE = 2048;
	public static final int DEFAULT___MAC_SIZE = 128;
	private static final boolean DEFAULT_CONFIG_VALUE___USE_EXTERNAL_KEY_STORAGE = false;
	private static final String FOLDERINFO_FILENAME = ".folderinfo___do_not_delete";
	static final String[] GLOBAL_SHARE_CONFIG_FILE = { ".global_share_config" };
	public static final int IV_SIZE = 16;
	public static final int KEY_SIZE = 16;
	public static final int MIN_ASYM_KEY_SIZE = PGPCryptoModule.DEFAULT___ASYM_KEY_SIZE;
	private static final long NAME_CACHE___MAX_ENTRIES = 100000; // memory footprint should be <10MB
	private static final long NO_ACCESS_CACHE___MAX_ENTRIES = 5000;
	private static final String PORTID_DEC = "decrypted";
	private static final String PORTID_ENC = "encrypted";
	private static final String PORTID_KEYS = "key_storage";
	public static final String[] SUPPORTED_CI_COMMANDS = { GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES, GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES, PGPCryptoModule.COMMAND___MANAGE_PRIVATE_KEYS, PGPCryptoModule.COMMAND___CHECK_STATE, PGPCryptoModule.COMMAND___MANAGE_PUBLIC_KEYS, PGPCryptoModule.COMMAND___BACKUP_PRIVATE_KEYS, PGPCryptoModule.COMMAND___IMPORT_KEYS, PGPCryptoModule.COMMAND___GET_PRIVATE_KEY_FINGERPRINT, PGPCryptoModule.COMMAND___GENERATE_KEY, PGPCryptoModule.COMMAND___EXPORT_OWN_PUBLIC_KEY, PGPCryptoModule.COMMAND___BACKUP_ALL_KEYS, PGPCryptoModule.COMMAND___CHANGE_PRIVATE_KEY, PGPCryptoModule.COMMAND___RESTART_ENGINE };
	public static final String[] SUPPORTED_MODULE_COMMANDS = { PGPCryptoModule.COMMAND___INVALIDATE_CACHE, PGPCryptoModule.COMMAND___SELECT_SHARES, GenericModuleCommands.GET_ACCESS_MODE, GenericModuleCommands.SET_PRIVATE, GenericModuleCommands.SET_SHARED };
	static final int SYM_ENCRYPTION_ALGO = SymmetricKeyAlgorithmTags.AES_256;
	static final String UNENCRYPTED_FILE_CONTENT_SUFFIX = "___UNENCRYPTED_FILE_CONTENT";
	static final String UNENCRYPTED_FOLDER_NAME_SUFFIX = "___UNENCRYPTED_FOLDER_NAME";
	// TODO: Make cryptographic settings configurable. Settings should also be stored in container file.
	// private static final String CONFIG_PROPERTY_KEY___SYM_CRYPTO_ALGO = "sym_crypto_algo";
	// private static final String DEFAULT_CONFIG_VALUE___SYM_CRYPTO_ALGO = "aes";
	// private static final String CONFIG_PROPERTY_KEY___SYM_KEY_LENGTH = "sym_key_length";
	// private static final int DEFAULT_CONFIG_VALUE___SYM_KEY_LENGTH = 256;

	private PersistentConfigurationHelper configHelper;
	private boolean cryptoConfigOk = false;
	private ProviderPort decPort;
	private boolean decPortConnected = false;
	private Cache<String, String> elementNameCache;
	private ProsumerPort encPort;
	private boolean encPortConnected = false;
	private boolean encPortReady = false;
	private PGPCryptoEngine engine;
	private ExecutorService executor;
	private boolean initialized = false;
	private ProsumerPort keyPort;
	private boolean keyPortConnected = false;
	private boolean keyPortReady = false;
	private final Set<String> lockedPaths = new ConcurrentSkipListSet<String>();
	private final boolean moveConflictingElements = false;
	private Cache<String, String[]> noAccessCache;
	private boolean running = false;
	private boolean started = false;
	private final ReentrantLock stateLock = new ReentrantLock(true);
	private boolean useExternalKeyStorage = PGPCryptoModule.DEFAULT_CONFIG_VALUE___USE_EXTERNAL_KEY_STORAGE;

	/**
	 * Instantiates a new PGP crypto module.
	 *
	 * @param prosumerConnector the prosumer connector
	 * @param providerConnector the provider connector
	 * @param componentConfiguration the component configuration
	 * @param logConnector the log connector
	 */
	public PGPCryptoModule(final ProsumerConnector prosumerConnector, final ProviderConnector providerConnector, final ComponentConfigurationController componentConfiguration, final LogConnector logConnector) {
		super(prosumerConnector, providerConnector, componentConfiguration, logConnector);
	}

	/**
	 * Checks and creates a folder info file.
	 *
	 * @param encryptedPath the encrypted path
	 * @param decryptedPath the decrypted path
	 * @param overwrite the overwrite
	 * @return true, if successful
	 */
	private boolean checkAndCreateFolderInfoFile(final String[] encryptedPath, final String[] decryptedPath, final boolean overwrite) {
		if ((encryptedPath == null) || (decryptedPath == null) || (encryptedPath.length != decryptedPath.length)) {
			return false;
		} else if (decryptedPath[decryptedPath.length - 1].endsWith(PGPCryptoModule.UNENCRYPTED_FOLDER_NAME_SUFFIX)) {
			return true;
		}
		boolean result = false;
		OutputStream out = null;
		String[] lockedPath = null;
		try {
			final DataElement folderElem = this.prosumerConnector.getElement(this.encPort, encryptedPath);
			if ((folderElem != null) && (folderElem.getType() == DataElementType.FOLDER)) {
				final String[] folderInfoPath = new String[encryptedPath.length + 1];
				System.arraycopy(encryptedPath, 0, folderInfoPath, 0, encryptedPath.length);
				folderInfoPath[folderInfoPath.length - 1] = PGPCryptoModule.FOLDERINFO_FILENAME;
				final DataElement folderInfoElem = this.prosumerConnector.getElement(this.encPort, folderInfoPath);
				if ((folderInfoElem == null) || ((folderInfoElem.getType() == DataElementType.FILE) && overwrite)) {
					if (lockPath(folderInfoPath)) {
						lockedPath = folderInfoPath;
						out = this.prosumerConnector.writeData(this.encPort, folderInfoPath);
						if (out != null) {
							result = this.engine.encryptElementName(out, decryptedPath);
						}
					}
				}
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
		}
		unlockPath(lockedPath);
		if (out != null) {
			try {
				out.close();
			} catch (final IOException e) {
				this.logConnector.log(e);
			}
		}
		return result;
	}

	/**
	 * Checks and creates folder info files recursively.
	 *
	 * @param encryptedPath the encrypted path
	 * @param decryptedPath the decrypted path
	 * @return true, if successful
	 */
	private boolean checkAndCreateFolderInfoFilesRecursively(final String[] encryptedPath, final String[] decryptedPath) {
		if ((encryptedPath == null) || (decryptedPath == null) || (encryptedPath.length != decryptedPath.length)) {
			return false;
		}
		boolean result = false;
		for (int i = 1; i <= encryptedPath.length; i++) {
			result &= checkAndCreateFolderInfoFile(Arrays.copyOfRange(encryptedPath, 0, i), Arrays.copyOfRange(decryptedPath, 0, i), false);
		}
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#checkAndLock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int checkAndLock(final ProviderPort port, final String[] path) throws ModuleException {
		checkRunning();
		try {
			final String[] encPath = encryptPath(path);
			if (encPath != null) {
				return this.prosumerConnector.checkAndLock(this.encPort, encPath);
			} else {
				return Provider.RESULT_CODE___ERROR_NO_SUCH_FILE;
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/**
	 * Checks for and handles conflicts.
	 *
	 * @param encryptedPath the encrypted path
	 * @return true, if successful
	 */
	@SuppressWarnings("unused")
	private boolean checkForAndHandleConflict(final String[] encryptedPath) {
		if (encryptedPath == null) {
			return false;
			// TODO: Currently conflicting elements are not moved so this is dead code for now. Check functionality and use it.
		} else if (!this.moveConflictingElements || (encryptedPath.length == 0)) {
			return true;
		}
		for (int i = 1; i <= encryptedPath.length; i++) {
			final String[] subPath = Arrays.copyOfRange(encryptedPath, 0, i);
			DataElement subElem;
			try {
				subElem = this.prosumerConnector.getElement(this.encPort, subPath);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return false;
			}
			if (subElem == null) {
				return true;
			} else {
				final String name = decryptElementName(subPath);
				if (name == null) {
					final String[] subConflictPath = Arrays.copyOf(subPath, subPath.length);
					subConflictPath[subConflictPath.length - 1] += PGPCryptoModule.CONFLICTING_ELEMENT_SUFFIX + System.currentTimeMillis();
					int lr = -1;
					try {
						lr = this.prosumerConnector.checkAndLock(this.encPort, subConflictPath);
					} catch (BrokerException | ModuleException | AuthorizationException e) {
						this.logConnector.log(e);
					}
					boolean result = false;
					try {
						this.prosumerConnector.move(this.encPort, subPath, subConflictPath);
						this.noAccessCache.put(getInternalPathString(subConflictPath), subConflictPath);
						result = true;
					} catch (BrokerException | ModuleException | AuthorizationException e) {
						this.logConnector.log(e);
					}
					if (lr == 0) {
						try {
							this.prosumerConnector.unlock(this.encPort, subConflictPath);
						} catch (BrokerException | ModuleException | AuthorizationException e) {
							this.logConnector.log(e);
						}
					}
					return result;
				}
			}
		}
		return true;
	}

	/**
	 * Checks module rights.
	 *
	 * @return true, if OK
	 */
	private boolean checkRights() {
		int rights;
		try {
			rights = this.prosumerConnector.getOwnRights();
		} catch (final BrokerException e) {
			this.logConnector.log(e);
			rights = 0;
		}
		final int requiredRights = ModuleRight.READ_DATA | ModuleRight.RECEIVE_EVENTS | ModuleRight.WRITE_DATA | ModuleRight.WRITE_DB;
		if ((rights & requiredRights) != requiredRights) {
			this.logConnector.log(LogEventLevelType.ERROR, "insufficient module rights");
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Check if module is running.
	 *
	 * @throws ModuleException if module is not running
	 */
	private void checkRunning() throws ModuleException {
		if (!this.running || !this.cryptoConfigOk) {
			throw new ModuleException("Module not running");
		}
	}

	/**
	 * Checks state.
	 */
	private void checkState() {
		this.stateLock.lock();
		if (this.decPortConnected && this.encPortConnected && (!this.useExternalKeyStorage || (this.keyPortConnected && this.keyPortReady)) && this.encPortReady && this.started && this.initialized && checkRights() && !this.running) {
			if (this.engine.start()) {
				this.running = true;
				sendStateUpdate();
			}
		} else if (!(this.decPortConnected && this.encPortConnected && (!this.useExternalKeyStorage || (this.keyPortConnected && this.keyPortReady)) && this.encPortReady && this.started && this.initialized && checkRights()) && this.running) {
			this.running = false;
			sendStateUpdate();
			this.engine.stop();
		}
		this.stateLock.unlock();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#createDirectory(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int createFolder(final ProviderPort port, final String[] path) throws ModuleException {
		checkRunning();
		try {
			final String[] encPath = encryptPath(path);
			if (encPath != null) {
				if (!lockPath(encPath) || !checkForAndHandleConflict(encPath)) {
					return Provider.RESULT_CODE___ERROR_GENERAL;
				}
				final int result = this.prosumerConnector.createFolder(this.encPort, encPath);
				if (result == Provider.RESULT_CODE___OK) {
					checkAndCreateFolderInfoFilesRecursively(encPath, path);
				}
				return result;
			} else {
				return Provider.RESULT_CODE___ERROR_NO_SUCH_FILE;
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/**
	 * Decrypts an element.
	 *
	 * @param encryptedElement the encrypted element
	 * @param forceReload true to force reload of element header
	 * @return the decrypted data element
	 */
	private DataElement decryptElement(final DataElement encryptedElement, final boolean forceReload) {
		if (encryptedElement == null) {
			return null;
		}
		final String[] decryptedPath = decryptPath(encryptedElement.getPath(), forceReload);
		if (decryptedPath != null) {
			return new DataElement(decryptedPath, encryptedElement);
		} else {
			return null;
		}
	}

	/**
	 * Decrypts an element name.
	 *
	 * @param encryptedPath the encrypted path
	 * @return the decrypted name
	 */
	private String decryptElementName(final String[] encryptedPath) {
		if (encryptedPath[encryptedPath.length - 1].endsWith(PGPCryptoModule.UNENCRYPTED_FOLDER_NAME_SUFFIX) || encryptedPath[encryptedPath.length - 1].endsWith(PGPCryptoModule.UNENCRYPTED_FILE_CONTENT_SUFFIX)) {
			return encryptedPath[encryptedPath.length - 1];
		}
		String result = null;
		InputStream in = null;
		try {
			final DataElementType type = this.prosumerConnector.getType(this.encPort, encryptedPath);
			if (type == DataElementType.FILE) {
				in = this.prosumerConnector.readData(this.encPort, encryptedPath);
			} else if (type == DataElementType.FOLDER) {
				final String[] folderInfoPath = new String[encryptedPath.length + 1];
				System.arraycopy(encryptedPath, 0, folderInfoPath, 0, encryptedPath.length);
				folderInfoPath[folderInfoPath.length - 1] = PGPCryptoModule.FOLDERINFO_FILENAME;
				final DataElement folderInfoElem = this.prosumerConnector.getElement(this.encPort, folderInfoPath);
				if ((folderInfoElem != null) && (folderInfoElem.getType() == DataElementType.FILE)) {
					in = this.prosumerConnector.readData(this.encPort, folderInfoPath);
				} else {
					result = encryptedPath[encryptedPath.length - 1] + PGPCryptoModule.UNENCRYPTED_FOLDER_NAME_SUFFIX;
				}
			}
			if (in != null) {
				result = this.engine.decryptElementName(in);
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
		}
		if (in != null) {
			try {
				in.close();
			} catch (final IOException e) {
				this.logConnector.log(e);
			}
		}
		return result;
	}

	/**
	 * Decrypts a full path.
	 *
	 * @param encryptedPath the encrypted path
	 * @param forceReload true to force reload of element header(s)
	 * @return the decrypted path
	 */
	private String[] decryptPath(final String[] encryptedPath, final boolean forceReload) {
		if (encryptedPath == null) {
			return null;
		} else if (encryptedPath.length == 0) {
			return encryptedPath;
		} else if (forceReload && (encryptedPath.length > 1) && encryptedPath[encryptedPath.length - 1].endsWith(PGPCryptoModule.FOLDERINFO_FILENAME)) {
			// folder info file was updated -> invalidate cache data (if any)
			this.elementNameCache.invalidate(encryptedPath[encryptedPath.length - 2]);
			this.noAccessCache.invalidate(getInternalPathString(Arrays.copyOfRange(encryptedPath, 0, encryptedPath.length - 1)));
			return null;
		}
		final String[] result = new String[encryptedPath.length];
		for (int i = 0; i < (encryptedPath.length - 1); i++) {
			String part = this.elementNameCache.getIfPresent(encryptedPath[i]);
			if (part == null) {
				final String[] subPath = Arrays.copyOfRange(encryptedPath, 0, i + 1);
				final String subIntPath = getInternalPathString(subPath);
				if (this.noAccessCache.getIfPresent(subIntPath) != null) {
					return null;
				}
				part = decryptElementName(subPath);
				if (part != null) {
					this.elementNameCache.put(encryptedPath[i], part);
				} else {
					this.noAccessCache.put(subIntPath, subPath);
				}
			}
			if (part == null) {
				return null;
			} else {
				result[i] = part;
			}
		}
		final int i = encryptedPath.length - 1;
		String part = null;
		final String intEncPath = getInternalPathString(encryptedPath);
		if (!forceReload) {
			part = this.elementNameCache.getIfPresent(encryptedPath[i]);
			if (part == null) {
				if (this.noAccessCache.getIfPresent(intEncPath) != null) {
					return null;
				}
			}
		}
		if (part == null) {
			part = decryptElementName(encryptedPath);
			if (part != null) {
				this.elementNameCache.put(encryptedPath[i], part);
				this.noAccessCache.invalidate(intEncPath);
			} else {
				this.noAccessCache.put(intEncPath, encryptedPath);
			}
		}
		if (part == null) {
			return null;
		} else {
			result[i] = part;
		}
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#delete(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int delete(final ProviderPort port, final String[] path) throws ModuleException {
		checkRunning();
		try {
			final String[] encPath = encryptPath(path);
			if (encPath != null) {
				if (!checkForAndHandleConflict(encPath)) {
					return Provider.RESULT_CODE___ERROR_GENERAL;
				}
				return this.prosumerConnector.delete(this.encPort, encPath);
			} else {
				return Provider.RESULT_CODE___ERROR_NO_SUCH_FILE;
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/**
	 * Encrypts a full path.
	 *
	 * @param decryptedPath the decrypted path
	 * @return the encrypted path
	 */
	private String[] encryptPath(final String[] decryptedPath) {
		if (decryptedPath == null) {
			return null;
		} else if (decryptedPath.length == 0) {
			return decryptedPath;
		} else if (decryptedPath[decryptedPath.length - 1].equals(PGPCryptoModule.FOLDERINFO_FILENAME)) {
			// ignore requests for internal FOLDERINFO files
			return null;
		} else {
			// see engine for details on how hashing is done and what parts are removed or left untouched
			return this.engine.hashPath(decryptedPath);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#enterShutdown() */
	@Override
	public void enterShutdown() {
		this.stateLock.lock();
		this.started = false;
		checkState();
		this.stateLock.unlock();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#enterStartup() */
	@Override
	public void enterStartup() {
		// executor.execute(new Runnable() {
		//
		// @Override
		// public void run() {
		// dataWriteLock.lock();
		// started = false;
		// checkState();
		// dataWriteLock.unlock();
		// }
		// });
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#exitShutdown() */
	@Override
	public void exitShutdown() {
		this.encPortReady = false;
		this.keyPortReady = false;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#exitStartup() */
	@Override
	public void exitStartup() {
		this.stateLock.lock();
		this.started = true;
		checkState();
		if (this.encPortConnected) {
			try {
				this.prosumerConnector.requestConnectedProviderStatus(this.encPort);
			} catch (BrokerException | ModuleException e) {
				this.logConnector.log(e);
			}
		}
		if (this.keyPortConnected) {
			try {
				this.prosumerConnector.requestConnectedProviderStatus(this.keyPort);
			} catch (BrokerException | ModuleException e) {
				this.logConnector.log(e);
			}
		}
		this.stateLock.unlock();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getChildElements(framework.model.ProviderPort, java.lang.String[], boolean) */
	@Override
	public Set<DataElement> getChildElements(final ProviderPort port, final String[] path, final boolean recursive) throws ModuleException {
		checkRunning();
		final Set<DataElement> decElements = new HashSet<DataElement>();
		try {
			if (this.prosumerConnector.isConnected(this.encPort)) {
				final String[] encPath = encryptPath(path);
				if (encPath != null) {
					final Set<DataElement> encElements = this.prosumerConnector.getChildElements(this.encPort, encPath, recursive);
					for (final DataElement encElement : encElements) {
						if (encElement.getName().equals(PGPCryptoModule.FOLDERINFO_FILENAME)) {
							// ignore FOLDERINFO files
							continue;
						} else {
							final DataElement decElement = decryptElement(encElement, false);
							// ignore elements that cannot be decrypted
							if (decElement != null) {
								decElements.add(decElement);
							}
						}
					}
				} else {
					return null;
				}
			}
		} catch (BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
		}
		return decElements;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getElement(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElement getElement(final ProviderPort port, final String[] path) throws ModuleException {
		checkRunning();
		if ((path == null) || ((path.length > 0) && path[path.length - 1].equals(PGPCryptoModule.FOLDERINFO_FILENAME))) {
			return null;
		} else if (path.length == 0) {
			try {
				final DataElement element = this.prosumerConnector.getElement(this.encPort, new String[0]);
				if (element != null) {
					return new DataElement(path, element.getType(), element.getSize(), element.getModificationDate());
				} else {
					return null;
				}
			} catch (BrokerException | AuthorizationException e) {
				this.logConnector.log(e);
				return null;
			}
		} else {
			try {
				final String[] encPath = encryptPath(path);
				if (encPath != null) {
					return decryptElement(this.prosumerConnector.getElement(this.encPort, encPath), false);
				} else {
					return null;
				}
			} catch (BrokerException | AuthorizationException e) {
				throw new ModuleException(e);
			}
		}
	}

	/**
	 * Gets an internal path string representation.
	 * <p>
	 * TODO: Do we really need this? At least move to helper?
	 *
	 * @param path the path
	 * @return the internal path string
	 */
	private String getInternalPathString(final String[] path) {
		String s = "";
		for (final String p : path) {
			s += p + "/\\";
		}
		return s;
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#getSupportedControlInterfaceCommands() */
	@Override
	public Set<String> getSupportedControlInterfaceCommands() {
		return Sets.union(Sets.newHashSet(PGPCryptoModule.SUPPORTED_CI_COMMANDS), Sets.newHashSet(PGPCryptoModule.SUPPORTED_MODULE_COMMANDS));
	}

	@Override
	public Set<String> getSupportedModuleCommands(final Port port, final String[] path) {
		return Sets.newHashSet(PGPCryptoModule.SUPPORTED_MODULE_COMMANDS);
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#getType(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public DataElementType getType(final ProviderPort port, final String[] path) throws ModuleException {
		checkRunning();
		try {
			final String[] encPath = encryptPath(path);
			if (encPath != null) {
				return this.prosumerConnector.getType(this.encPort, encPath);
			} else {
				return null;
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#initialize() */
	@Override
	public void initialize() {
		this.stateLock.lock();
		try {
			final String threadNamePrefix = this.componentConfiguration.getComponentName() + "-" + this.getClass().getSimpleName() + "-%d";
			this.executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(threadNamePrefix).build());
			initializeConfig();
			this.componentConfiguration.initializeElementDomains(PGPCryptoModule.PORTID_DEC, PGPCryptoModule.PORTID_ENC);
			this.encPort = this.prosumerConnector.registerProsumerPort(this, PGPCryptoModule.PORTID_ENC, 1);
			final String[] rootPath = {};
			this.prosumerConnector.subscribe(this.encPort, rootPath, true, this);
			this.keyPort = this.prosumerConnector.registerProsumerPort(this, PGPCryptoModule.PORTID_KEYS, 1);
			this.decPort = this.providerConnector.registerProviderPort(this, PGPCryptoModule.PORTID_DEC, -1);
			initializeEngine();
			this.elementNameCache = CacheBuilder.newBuilder().maximumSize(PGPCryptoModule.NAME_CACHE___MAX_ENTRIES).expireAfterWrite(PGPCryptoModule.CACHE___EXPIRE_MINUTES, TimeUnit.MINUTES).build();
			this.noAccessCache = CacheBuilder.newBuilder().maximumSize(PGPCryptoModule.NO_ACCESS_CACHE___MAX_ENTRIES).expireAfterWrite(PGPCryptoModule.CACHE___EXPIRE_MINUTES, TimeUnit.MINUTES).build();
			this.initialized = true;
		} catch (IllegalArgumentException | DatabaseException | BrokerException | AuthorizationException e) {
			this.logConnector.log(e);
			this.initialized = false;
		}
		this.stateLock.unlock();
	}

	/**
	 * Initializes the configuration.
	 */
	private void initializeConfig() {
		try {
			this.configHelper = new PersistentConfigurationHelper(this.componentConfiguration, PGPCryptoModule.CONFIG_DOMAIN, PGPCryptoModule.CONFIG_PATH);
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
			this.logConnector.log(LogEventLevelType.WARNING, "no persistent config");
			this.configHelper = new PersistentConfigurationHelper();
		}

		ConfigValue cv;
		String key;

		key = PGPCryptoModule.CONFIG_PROPERTY_KEY___USE_EXTERNAL_KEY_STORAGE;
		cv = this.configHelper.getConfigValue(key);
		if ((cv == null) || !cv.isValid()) {
			cv = new ConfigValue(key);
			cv.setCurrentValueBoolean(PGPCryptoModule.DEFAULT_CONFIG_VALUE___USE_EXTERNAL_KEY_STORAGE);
			cv.setDescriptionString("Use external provider connected to keystorage port for data storage.");
			this.configHelper.updateConfigValue(key, cv, true);
		}
		this.useExternalKeyStorage = cv.getCurrentValueBoolean();
		//
		// key = CONFIG_PROPERTY_KEY___SYM_CRYPTO_ALGO;
		// cv = configHelper.getConfigValue(key);
		// if (cv == null || !cv.isValid()) {
		// cv = new ConfigValue(key);
		// cv.setCurrentValueString(DEFAULT_CONFIG_VALUE___SYM_CRYPTO_ALGO);
		// cv.setDescriptionString("Algorithm used for symmetric encryption (default is AES).");
		// configHelper.updateConfigValue(key, cv, true);
		// }
		// symCryptoAlgo = configHelper.getString(CONFIG_PROPERTY_KEY___SYM_CRYPTO_ALGO, DEFAULT_CONFIG_VALUE___SYM_CRYPTO_ALGO);
		//
		// key = CONFIG_PROPERTY_KEY___SYM_KEY_LENGTH;
		// cv = configHelper.getConfigValue(key);
		// if (cv == null || !cv.isValid()) {
		// cv = new ConfigValue(key);
		// cv.setCurrentValueInteger(DEFAULT_CONFIG_VALUE___SYM_KEY_LENGTH);
		// cv.setDescriptionString("Length of the symmetric encryption key (default is 256).");
		// configHelper.updateConfigValue(key, cv, true);
		// }
		// symKeyLength = configHelper.getInteger(CONFIG_PROPERTY_KEY___SYM_KEY_LENGTH, DEFAULT_CONFIG_VALUE___SYM_KEY_LENGTH);
	}

	/**
	 * Initializes the engine.
	 */
	private void initializeEngine() {
		this.stateLock.lock();
		if (this.engine != null) {
			this.engine.destroy();
		}
		if (this.useExternalKeyStorage) {
			this.engine = new PGPCryptoEngine(this, this.prosumerConnector.getNewLocalizationConnector(), this.logConnector, this.prosumerConnector, this.keyPort, this.componentConfiguration);
		} else {
			this.engine = new PGPCryptoEngine(this, this.prosumerConnector.getNewLocalizationConnector(), this.logConnector, this.prosumerConnector, null, this.componentConfiguration);
		}
		this.stateLock.unlock();
	}

	/**
	 * Invalidates the decrypted element names and no access caches.
	 */
	private void invalidateCache() {
		this.stateLock.lock();
		this.elementNameCache.invalidateAll();
		this.noAccessCache.invalidateAll();
		this.stateLock.unlock();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#isReady() */
	@Override
	public boolean isReady() {
		return this.initialized && checkRights();
	}

	/**
	 * Locks a path.
	 *
	 * @param path the path
	 * @return true, if successful
	 */
	private boolean lockPath(final String[] path) {
		if (path == null) {
			return false;
		}
		boolean result = false;
		this.stateLock.lock();
		final String intPath = getInternalPathString(path);
		if (!this.lockedPaths.contains(intPath)) {
			this.lockedPaths.add(intPath);
			result = true;
		}
		this.stateLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#move(framework.model.ProviderPort, java.lang.String[], java.COMMAND___SELECT_SHARESlang.String[]) */
	@Override
	public int move(final ProviderPort port, final String[] srcPath, final String[] destPath) throws ModuleException {
		checkRunning();
		try {
			final String[] encSrcPath = encryptPath(srcPath);
			final String[] encDestPath = encryptPath(destPath);
			if ((encSrcPath != null) && (encDestPath != null)) {
				if (encSrcPath[encSrcPath.length - 1].equals(encDestPath[encDestPath.length - 1])) {
					final int result = this.prosumerConnector.move(this.encPort, encSrcPath, encDestPath);
					if (result == Provider.RESULT_CODE___OK) {
						checkAndCreateFolderInfoFilesRecursively(encDestPath, destPath);
					}
					return result;
				} else {
					final DataElement elem = this.prosumerConnector.getElement(this.encPort, encSrcPath);
					if (elem == null) {
						return Provider.RESULT_CODE___ERROR_NO_SUCH_FILE;
					} else if (elem.getType() == DataElementType.FOLDER) {
						final int result = this.prosumerConnector.move(this.encPort, encSrcPath, encDestPath);
						if (result == Provider.RESULT_CODE___OK) {
							checkAndCreateFolderInfoFile(encDestPath, destPath, true);
							checkAndCreateFolderInfoFilesRecursively(encDestPath, destPath);
						}
						return result;
					} else if (elem.getType() == DataElementType.FILE) {
						// unfortunately we need to reencrypt the file
						final InputStream in = readData(this.decPort, srcPath);
						final OutputStream out = writeData(this.decPort, destPath);
						int result = Provider.RESULT_CODE___ERROR_GENERAL;
						try {
							ByteStreams.copy(in, out);
							result = Provider.RESULT_CODE___OK;
						} catch (final IOException e) {
							this.logConnector.log(e);
						}
						try {
							in.close();
						} catch (final IOException e) {
							this.logConnector.log(e);
						}
						try {
							out.close();
						} catch (final IOException e) {
							this.logConnector.log(e);
						}
						return result;
					} else {
						return Provider.RESULT_CODE___INVALID_NOT_SUPPORTED;
					}
				}
			} else {
				return Provider.RESULT_CODE___ERROR_NO_SUCH_FILE;
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.Module#onControlInterfaceCommand(java.lang.String, java.util.Map) */
	@Override
	public Map<String, String> onControlInterfaceCommand(final String command, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return CommandResultHelper.getDefaultResultFail();
		}
		switch (command) {
		case GenericControlInterfaceCommands.GET_CONFIG_PROPERTIES:
			return this.configHelper.getAllValues(CommandResultHelper.getDefaultResultOk());
		case GenericControlInterfaceCommands.SET_CONFIG_PROPERTIES:
			if ((properties != null) && this.configHelper.updateAllValues(properties, false)) {
				initializeConfig();
				return CommandResultHelper.getDefaultResultOk(GenericControlInterfaceCommandProperties.KEY___MESSAGE, GenericControlInterfaceCommandProperties.VALUE_MESSAGE___RESTART_REQUIRED);
			}
			break;
		case COMMAND___MANAGE_PRIVATE_KEYS:
			if (this.engine.isKeyManagerAvailable()) {
				this.engine.manageSecretKeys();
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___MANAGE_PUBLIC_KEYS:
			if (this.engine.isKeyManagerAvailable()) {
				this.engine.managePublicKeys();
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___GET_PRIVATE_KEY_FINGERPRINT:
			if (this.engine.isKeyManagerAvailable()) {
				final String fingerprintString = this.engine.getPrivateKeyFingerprints();
				if (fingerprintString != null) {
					return CommandResultHelper.getDefaultResultOk(GenericControlInterfaceCommandProperties.KEY___MESSAGE, fingerprintString);
				} else {
					return CommandResultHelper.getDefaultResultFail();
				}
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___GENERATE_KEY:
			if (this.engine.isKeyManagerAvailable()) {
				this.executor.execute(new Runnable() {

					@Override
					public void run() {
						PGPCryptoModule.this.engine.generateKeyPair();
					}
				});
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___BACKUP_PRIVATE_KEYS:
			if (this.engine.isKeyManagerAvailable()) {
				this.executor.execute(new Runnable() {

					@Override
					public void run() {
						PGPCryptoModule.this.engine.backupSecretKeys();
					}
				});
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___BACKUP_ALL_KEYS:
			if (this.engine.isKeyManagerAvailable()) {
				this.executor.execute(new Runnable() {

					@Override
					public void run() {
						PGPCryptoModule.this.engine.backupAllKeys();
					}
				});
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___IMPORT_KEYS:
			if (this.engine.isKeyManagerAvailable()) {
				this.executor.execute(new Runnable() {

					@Override
					public void run() {
						PGPCryptoModule.this.engine.importKeys();
					}
				});
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___EXPORT_OWN_PUBLIC_KEY:
			if (this.engine.isKeyManagerAvailable()) {
				this.executor.execute(new Runnable() {

					@Override
					public void run() {
						PGPCryptoModule.this.engine.exportSelectedOwnPublicKey();
					}
				});
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___CHECK_STATE:
			if (this.engine.isKeyManagerAvailable()) {
				this.executor.execute(new Runnable() {

					@Override
					public void run() {
						checkState();
					}
				});
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___CHANGE_PRIVATE_KEY:
			if (this.engine.isKeyManagerAvailable()) {
				this.executor.execute(new Runnable() {

					@Override
					public void run() {
						PGPCryptoModule.this.engine.changeSecretKeyRing();
						invalidateCache();
					}
				});
				return CommandResultHelper.getDefaultResultOk();
			} else {
				return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "key manager not available");
			}
		case COMMAND___RESTART_ENGINE:
			this.executor.execute(new Runnable() {

				@Override
				public void run() {
					initializeEngine();
				}
			});
			return CommandResultHelper.getDefaultResultOk();
		}
		if (properties != null) {
			final String pathName = properties.get(PGPCryptoModule.COMMAND_PROPERTY_KEY___PATH);
			if (pathName != null) {
				final String[] path = TextFormatHelper.getPathArray(pathName);
				return onModuleCommand(this.decPort, command, path, properties);
			}
		}
		return CommandResultHelper.getDefaultResultFail(GenericControlInterfaceCommandProperties.KEY___MESSAGE, "invalid control interface command or module command without 'path' property.");
	}

	@Override
	public void onElementEvent(final ProsumerPort port, final DataElementEvent event) {
		final DataElement element = event.dataElement;
		final DataElementEventType type = event.eventType;
		if ((port == this.encPort) && !this.lockedPaths.contains(getInternalPathString(element.getPath()))) {
			final DataElement decElement = decryptElement(element, event.eventType != DataElementEventType.DELETE);
			// ignore elements that cannot be accessed/decrypted
			if (decElement != null) {
				try {
					this.providerConnector.sendElementEvent(this.decPort, decElement, type);
				} catch (final BrokerException e) {
					this.logConnector.log(e);
				}
			}
		}
	}

	@Override
	public Map<String, String> onModuleCommand(final Port port, final String command, final String[] path, final Map<String, String> properties) {
		if ((command == null) || command.isEmpty()) {
			return CommandResultHelper.getDefaultResultFail();
		}
		if (port == this.decPort) {
			switch (command) {
			case COMMAND___INVALIDATE_CACHE:
				invalidateCache();
				return CommandResultHelper.getDefaultResultOk();
			case COMMAND___SELECT_SHARES:
				if (path != null) {
					this.engine.setSharingKeys(path);
					return CommandResultHelper.getDefaultResultOk();
				}
				break;
			case GenericModuleCommands.GET_ACCESS_MODE:
				if (path != null) {
					if (this.engine.isShared(path)) {
						return CommandResultHelper.getDefaultResultOk(GenericModuleCommandProperties.KEY___ACCESS_MODE, GenericModuleCommandProperties.VALUE_ACCESS_MODE___SHARED);
					} else {
						return CommandResultHelper.getDefaultResultOk(GenericModuleCommandProperties.KEY___ACCESS_MODE, GenericModuleCommandProperties.VALUE_ACCESS_MODE___PRIVATE);
					}
				}
				break;
			case GenericModuleCommands.SET_PRIVATE:
				if (path != null) {
					this.engine.removeAllSharingKeys(path);
					return CommandResultHelper.getDefaultResultOk();
				}
				break;
			case GenericModuleCommands.SET_SHARED:
				if (path != null) {
					this.engine.autoAddAllSharingKeys(path);
					return CommandResultHelper.getDefaultResultOk();
				}
				break;
			}

			if (command.equals(PGPCryptoModule.COMMAND___INVALIDATE_CACHE)) {
				invalidateCache();
				return CommandResultHelper.getDefaultResultOk();
			} else if (command.equals(PGPCryptoModule.COMMAND___SELECT_SHARES) && (path != null)) {
				this.executor.execute(new Runnable() {

					@Override
					public void run() {
						PGPCryptoModule.this.engine.setSharingKeys(path);
					}
				});
				return CommandResultHelper.getDefaultResultOk();
			}
		}
		return CommandResultHelper.getDefaultResultFail();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortConnection(framework.model.Port) */
	@Override
	public void onPortConnection(final Port port) {
		this.stateLock.lock();
		if (port == this.decPort) {
			this.decPortConnected = true;
		} else if (port == this.encPort) {
			this.encPortConnected = true;
		} else if (port == this.keyPort) {
			this.keyPortConnected = true;
		}
		checkState();
		if ((port instanceof ProsumerPort) && this.started) {
			try {
				this.prosumerConnector.requestConnectedProviderStatus((ProsumerPort) port);
			} catch (BrokerException | ModuleException e) {
				this.logConnector.log(e);
			}
		}
		this.stateLock.unlock();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Module#onPortDisconnection(framework.model.Port) */
	@Override
	public void onPortDisconnection(final Port port) {
		this.stateLock.lock();
		if (port == this.decPort) {
			this.decPortConnected = false;
		} else if (port == this.encPort) {
			this.encPortConnected = false;
			this.encPortReady = false;
		} else if (port == this.keyPort) {
			this.keyPortConnected = false;
			this.keyPortReady = false;
		}
		checkState();
		this.stateLock.unlock();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Prosumer#onConnectedModuleStateEvent(framework.model.Port, int) */
	@Override
	public void onProviderStateEvent(final Port port, final ProviderStateEvent event) {
		final int moduleState = event.state;
		this.stateLock.lock();
		final boolean ready = (moduleState & ModuleStateType.READY) == ModuleStateType.READY;
		if (port == this.encPort) {
			if (ready) {
				this.encPortReady = true;
			} else {
				this.encPortReady = false;
			}
		} else if (port == this.keyPort) {
			if (ready) {
				this.keyPortReady = true;
			} else {
				this.keyPortReady = false;
			}
		}
		checkState();
		this.stateLock.unlock();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#onStateRequest(framework.model.ProviderPort) */
	@Override
	public void onStateRequest(final ProviderPort port) {
		sendStateUpdate();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#readData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public InputStream readData(final ProviderPort port, final String[] path) throws ModuleException {
		checkRunning();
		final String[] encPath = encryptPath(path);
		try {
			if (encPath != null) {
				if (!lockPath(encPath)) {
					return null;
				}
				final InputStream encIn = this.prosumerConnector.readData(this.encPort, encPath);
				if (encIn != null) {
					final CryptoActionInfo info = new CryptoActionInfo();
					return this.engine.decrypt(encIn, info);
				} else {
					return null;
				}
			} else {
				return null;
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		} finally {
			unlockPath(encPath);
		}
	}

	/**
	 * Sends state update to connected modules.
	 */
	private void sendStateUpdate() {
		if (!this.started) {
			return;
		}
		try {
			if (this.running && this.cryptoConfigOk) {
				this.providerConnector.sendState(this.decPort, ModuleStateType.READY);
			} else if (!this.initialized) {
				this.providerConnector.sendState(this.decPort, ModuleStateType.ERROR);
			} else {
				this.providerConnector.sendState(this.decPort, 0);
			}
		} catch (final BrokerException e) {
			this.logConnector.log(e);
		}
	}

	/**
	 * Sets the engine's state.
	 *
	 * @param configOk the new engine state
	 */
	void setEngineState(final boolean configOk) {
		this.stateLock.lock();
		if (configOk != this.cryptoConfigOk) {
			this.cryptoConfigOk = configOk;
			sendStateUpdate();
		}
		this.stateLock.unlock();
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#unlock(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public int unlock(final ProviderPort port, final String[] path) throws ModuleException {
		checkRunning();
		try {
			final String[] encPath = encryptPath(path);
			if (encPath != null) {
				return this.prosumerConnector.unlock(this.encPort, encPath);
			} else {
				return Provider.RESULT_CODE___ERROR_NO_SUCH_FILE;
			}

		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		}
	}

	/**
	 * Unlocks a path.
	 *
	 * @param path the path
	 * @return true, if successful
	 */
	private boolean unlockPath(final String[] path) {
		if (path == null) {
			return false;
		}
		boolean result = false;
		this.stateLock.lock();
		final String intPath = getInternalPathString(path);
		if (this.lockedPaths.contains(intPath)) {
			this.lockedPaths.remove(intPath);
			result = true;
		}
		this.stateLock.unlock();
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see module.iface.Provider#writeData(framework.model.ProviderPort, java.lang.String[]) */
	@Override
	public OutputStream writeData(final ProviderPort port, final String[] path) throws ModuleException {
		checkRunning();
		// improvement: check for existence -> if file cannot be accessed -> fail (return null) or move it
		final String[] encPath = encryptPath(path);
		try {
			if (encPath != null) {
				if (!checkForAndHandleConflict(encPath)) {
					return null;
				}
				if (!lockPath(encPath)) {
					return null;
				}
				final OutputStream encOut = this.prosumerConnector.writeData(this.encPort, encPath);
				if (encOut != null) {
					final OutputStream decOut = this.engine.encrypt(encOut, path);
					if (decOut != null) {
						checkAndCreateFolderInfoFilesRecursively(encPath, path);
						return decOut;
					} else {
						return null;
					}
				} else {
					return null;
				}
			} else {
				return null;
			}
		} catch (BrokerException | AuthorizationException e) {
			throw new ModuleException(e);
		} finally {
			unlockPath(encPath);
		}
	}
}
