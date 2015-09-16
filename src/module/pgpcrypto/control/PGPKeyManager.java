package module.pgpcrypto.control;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;

import module.pgpcrypto.helper.HumanReadableIdGenerationHelper;
import module.pgpcrypto.model.KeyRingInfo;
import module.pgpcrypto.ui.CryptoDialogFactory;
import module.pgpcrypto.ui.KeyRingInfoRenderer;
import module.pgpcrypto.ui.KeyRingSelectionDialog;
import module.pgpcrypto.ui.PasswordDialog;
import module.pgpcrypto.ui.PublicKeyManagementDialog;
import module.pgpcrypto.ui.SecretKeyManagementDialog;
import module.pgpcrypto.ui.TextInputDialog;
import module.pgpcrypto.ui.UserInterfaceElementFactory;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection;

import com.google.common.base.Joiner;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import db.iface.ComponentConfigurationController;
import framework.control.LocalizationConnector;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.DatabaseException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.ProsumerPort;
import framework.model.event.type.LogEventLevelType;

/**
 * Manager for all key data. Also stores sharing informations (which path to share which whom).
 * <p>
 * IMPORTANT: Many methods don't accept parameters because they will fire up dialog for interaction with the user. Keys are first read from connected module at
 * key port (if configured that way), database is (always) used as a backup/fallback.
 * <p>
 * TODO:<br>
 * - Check locking (for example engine status update).<br>
 * - Forward errors to engine (to have them displayed to the user).<br>
 * - Better key port handling, use getStatus().<br>
 * - Better separation between dialogs/views and control methods. Avoid having public methods here.<br>
 * - More/better logging, more details and not only exceptions.<br>
 * - Make dialogs asynchronous.<br>
 * - Create dialog with summary of all set up shares (and offer a way to remove them).<br>
 * - If a public key is removed we need to also remove if from every share in the database.
 *
 * @author Stefan Werner
 */
public class PGPKeyManager {

	private static final String[] DB___KEY_DATA = { "key_data" };
	private static final String[] DB___SELECTED_SEC_KEY = { "selected_keys", "sec_key" };
	private static final String DB_PROP___PUB_KEY_DATA = "pub_key_data";
	private static final String DB_PROP___SEC_KEY_DATA = "sec_key_data";
	private static final String DB_PROP___SEC_KEY_ID = "sec_key_id";
	private static final String DB_PROP___SHARING_KEY_IDS = "sharing_keys";
	private static final String DB_PROP___SHARING_KEY_IDS___ALL = "all";
	private static final String ID_SEPARATOR = ",";
	private static final String KEY_CONFIG_DOMAIN = "key_config";
	private static final String[] PUBKEYS_OLD_PATH = { "pgp_crypto_keys", "pubkeyrings.gpg.old_" };
	private static final String[] PUBKEYS_PATH = { "pgp_crypto_keys", "pubkeyrings.gpg" };
	private static final String[] SECKEYS_OLD_PATH = { "pgp_crypto_keys", "seckeyrings.gpg.old_" };
	private static final String[] SECKEYS_PATH = { "pgp_crypto_keys", "seckeyrings.gpg" };
	private static final String SHARE_CONFIG_DOMAIN = "share_config";

	private final ComponentConfigurationController componentConfigurationController;
	private final PGPCryptoEngine engine;
	private final ProsumerPort externalKeyStoragePort;
	private final ReadLock keyReadLock;
	private final WriteLock keyWriteLock;
	private final LocalizationConnector localizationConnector;
	private final LogConnector logConnector;
	private PGPPrivateKey ownEncPrivateKey;
	private PGPPublicKey ownEncPublicKey;
	private PGPPrivateKey ownSignPrivateKey;
	private PGPPublicKey ownSignPublicKey;
	private final ProsumerConnector prosumerConnector;
	private PGPPublicKeyRingCollection pubKeyCollection;
	private PGPSecretKeyRingCollection secKeyCollection;
	private PGPSecretKeyRing selectedSecKey;
	private ExecutorService service;

	/**
	 * Instantiates a new PGP key manager.
	 *
	 * @param engine the engine
	 * @param localizationConnector the localization connector
	 * @param logConnector the log connector
	 * @param prosumerConnector the prosumer connector
	 * @param externalKeyStoragePort the external key storage port
	 * @param componentConfigurationController the component configuration controller
	 * @param keyLock the key lock
	 */
	PGPKeyManager(final PGPCryptoEngine engine, final LocalizationConnector localizationConnector, final LogConnector logConnector, final ProsumerConnector prosumerConnector, final ProsumerPort externalKeyStoragePort, final ComponentConfigurationController componentConfigurationController, final ReentrantReadWriteLock keyLock) {
		this.engine = engine;
		this.localizationConnector = localizationConnector;
		this.logConnector = logConnector;
		this.prosumerConnector = prosumerConnector;
		this.externalKeyStoragePort = externalKeyStoragePort;
		this.componentConfigurationController = componentConfigurationController;
		this.keyReadLock = keyLock.readLock();
		this.keyWriteLock = keyLock.writeLock();
		initialize();
	}

	/**
	 * Shares the given path with all available keys.
	 *
	 * @param path the path
	 */
	void addAllSharingKeys(final String[] path) {
		if (path != null) {
			setSharingKeysInternal(path, true);
		}
	}

	/**
	 * Adds a key to known public keys.
	 *
	 * @param keyRing the key ring
	 * @return true, if successful
	 */
	private boolean addKeyToKnownPublicKeys(final PGPPublicKeyRing keyRing) {
		if (PGPKeyUtils.isPublicKeyRingUsable(keyRing, PGPCryptoModule.MIN_ASYM_KEY_SIZE)) {
			this.keyWriteLock.lock();
			this.pubKeyCollection = PGPPublicKeyRingCollection.addPublicKeyRing(this.pubKeyCollection, keyRing);
			this.keyWriteLock.unlock();
			saveAllKnownPublicKeys();
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Adds a new public key.
	 *
	 * @param in the input stream to read from
	 * @return the list of keys found
	 */
	public List<KeyRingInfo> addNewPublicKey(final InputStream in) {
		final PGPPublicKeyRingCollection newCollection = PGPKeyUtils.readArmoredPubKeyRingCollectionFromStream(in);
		if (newCollection == null) {
			return null;
		}
		final Map<Long, KeyRingInfo> fullMap = PGPKeyUtils.getKeyInfoMapFromPubKeyCollection(newCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		// TODO: filter already known keys
		final List<KeyRingInfo> selectionList = KeyRingSelectionDialog.showSelectionListDialog("Select Public Keys", fullMap.values(), null, true, null);
		final PGPPublicKeyRingCollection selectionCollection = PGPKeyUtils.getSelectionFromPubKeyList(newCollection, selectionList, PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		this.keyWriteLock.lock();
		this.pubKeyCollection = PGPKeyUtils.mergePubKeyRingCollection(this.pubKeyCollection, selectionCollection);
		saveAllKnownPublicKeys();
		this.keyWriteLock.unlock();
		return selectionList;
	}

	/**
	 * Adds a new secret key.
	 *
	 * @param in the input stream to read from
	 * @return the list of keys found
	 */
	public List<KeyRingInfo> addNewSecretKey(final InputStream in) {
		final PGPSecretKeyRingCollection newCollection = PGPKeyUtils.readArmoredSecKeyRingCollectionFromStream(in);
		if (newCollection == null) {
			return null;
		}
		final Map<Long, KeyRingInfo> fullMap = PGPKeyUtils.getKeyInfoMapFromSecKeyCollection(newCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		// TODO: filter already known keys
		final List<KeyRingInfo> selectionList = KeyRingSelectionDialog.showSelectionListDialog("Select Secret Keys", fullMap.values(), null, true, null);
		final PGPSecretKeyRingCollection selectionCollection = PGPKeyUtils.getSelectionFromSecKeyList(newCollection, selectionList, PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		this.keyWriteLock.lock();
		this.secKeyCollection = PGPKeyUtils.mergeSecKeyRingCollection(this.secKeyCollection, selectionCollection);
		saveAllKnownSecretKeys();
		this.keyWriteLock.unlock();
		checkState();
		return selectionList;
	}

	/**
	 * Adds a selected public key.
	 *
	 * @param selectionList the selection list
	 * @param in the input stream to read from
	 * @return selectionList
	 */
	public List<KeyRingInfo> addSelectedPublicKey(final List<KeyRingInfo> selectionList, final InputStream in) {
		final PGPPublicKeyRingCollection newCollection = PGPKeyUtils.readArmoredPubKeyRingCollectionFromStream(in);
		// TODO: filter already known keys
		if (newCollection == null) {
			return null;
		}
		final PGPPublicKeyRingCollection selectionCollection = PGPKeyUtils.getSelectionFromPubKeyList(newCollection, selectionList, PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		this.keyWriteLock.lock();
		this.pubKeyCollection = PGPKeyUtils.mergePubKeyRingCollection(this.pubKeyCollection, selectionCollection);
		saveAllKnownPublicKeys();
		this.keyWriteLock.unlock();
		return selectionList;
	}

	/**
	 * Enables auto add sharing keys for a given path. New sharing keys will be automatically added to this path in the future.
	 *
	 * @param path the path
	 */
	void autoAddAllSharingKeys(final String[] path) {
		if (path != null) {
			setSharingKeysInternal(path, null, false);
		}
	}

	/**
	 * Backups all keys.
	 *
	 * @return true, if successful
	 */
	public boolean backupAllKeys() {
		final JFileChooser chooser = new JFileChooser();
		final int option = chooser.showSaveDialog(null);
		if ((option == JFileChooser.APPROVE_OPTION) && (chooser.getSelectedFile() != null)) {
			try {
				return writeAllKnownKeysToStream(new FileOutputStream(chooser.getSelectedFile()));
			} catch (final Exception e1) {
				this.logConnector.log(e1);
			}
		}
		return false;
	}

	/**
	 * Backups secret keys.
	 *
	 * @return true, if successful
	 */
	public boolean backupSecretKeys() {
		final JFileChooser chooser = new JFileChooser();
		final int option = chooser.showSaveDialog(null);
		if ((option == JFileChooser.APPROVE_OPTION) && (chooser.getSelectedFile() != null)) {
			try {
				return writeAllKnownOwnKeysToStream(new FileOutputStream(chooser.getSelectedFile()));
			} catch (final Exception e1) {
				this.logConnector.log(e1);
			}
		}
		return false;
	}

	/**
	 * Changes selected/used (own) secret key ring.
	 */
	void changeSecretKeyRing() {
		this.keyWriteLock.lock();
		try {
			this.componentConfigurationController.deleteElementProperty(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___SELECTED_SEC_KEY, PGPKeyManager.DB_PROP___SEC_KEY_ID);
			this.selectedSecKey = null;
			preparePrivateKeys();
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}
		this.keyWriteLock.unlock();
	}

	/**
	 * Checks state.
	 */
	private void checkState() {
		boolean secUsable = true;
		boolean pubUsable = true;
		this.keyWriteLock.lock();
		if (!PGPKeyUtils.isSecretKeyCollectionUsable(this.secKeyCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE)) {
			CryptoDialogFactory.displayErrorDialog("Secret key ring collection contains unusable key rings");
			secUsable = false;
		}
		if (!PGPKeyUtils.isPublicKeyCollectionUsable(this.pubKeyCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE)) {
			CryptoDialogFactory.displayErrorDialog("Public key ring collection contains unusable key rings");
			pubUsable = false;
		}
		if (secUsable && pubUsable) {
			this.engine.setKeyManagerState(true);
		} else {
			this.engine.setKeyManagerState(false);
		}
		this.keyWriteLock.unlock();
	}

	/**
	 * Destroys this instance (cleans up, removes keys).
	 */
	void destroy() {
		this.service.shutdownNow();
		this.pubKeyCollection = null;
		this.secKeyCollection = null;
	}

	/**
	 * Exports public keys.
	 *
	 * @return true, if successful
	 */
	public boolean exportPublicKeys() {
		boolean result = false;
		OutputStream out = null;
		try {
			final String file = UserInterfaceElementFactory.getFileSavePath(null);
			this.keyReadLock.lock();
			if ((file != null) && !file.isEmpty() && !Files.exists(Paths.get(file))) {
				out = new FileOutputStream(file);
				PGPKeyUtils.writeArmoredPubKeyRingCollectionToStream(this.pubKeyCollection, out);
				result = true;
			}
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		try {
			if (out != null) {
				out.close();
			}
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Exports selected (own) public keys.
	 *
	 * @return true, if successful
	 */
	public boolean exportSelectedOwnPublicKey() {
		final JFileChooser chooser = new JFileChooser();
		final int option = chooser.showSaveDialog(null);
		if ((option == JFileChooser.APPROVE_OPTION) && (chooser.getSelectedFile() != null)) {
			try {
				final PGPSecretKeyRing secRing = selectSecretKeyRing();
				if (secRing != null) {
					writePublicKeys(new FileOutputStream(chooser.getSelectedFile()), secRing.getPublicKey().getKeyID());
					return true;
				}
			} catch (final Exception e1) {
				this.logConnector.log(e1);
			}
		}
		return false;
	}

	/**
	 * Generates a new key pair.
	 *
	 * @return true, if successful
	 */
	public boolean generateKeyPair() {
		// TODO dialog!
		boolean result = false;
		PGPKeyRingGenerator keyRingGenerator;
		try {
			this.logConnector.log(LogEventLevelType.DEBUG, "Generating new key pair...");
			final char[] password = PasswordDialog.showSecretKeyPasswordDialog(this.localizationConnector.getLocalizedString("Enter Password:"));
			final char[] passwordRepeat = PasswordDialog.showSecretKeyPasswordDialog(this.localizationConnector.getLocalizedString("Repeat Password:"));
			if ((password == null) || (passwordRepeat == null) || !Arrays.equals(password, passwordRepeat)) {
				CryptoDialogFactory.displayErrorDialog(this.localizationConnector.getLocalizedString("Passwords are not equal."));
				return false;
			}
			final String name = TextInputDialog.showTextInputDialog(this.localizationConnector.getLocalizedString("Enter a name and/or email address to identify the new key. "));
			if ((name == null) || name.isEmpty()) {
				CryptoDialogFactory.displayErrorDialog(this.localizationConnector.getLocalizedString("Cannot create key with empty identification string."));
				return false;
			}
			keyRingGenerator = PGPKeyUtils.generateKeyPair(PGPCryptoModule.DEFAULT___ASYM_KEY_SIZE, name, password);
			this.keyWriteLock.lock();
			this.secKeyCollection = PGPSecretKeyRingCollection.addSecretKeyRing(this.secKeyCollection, keyRingGenerator.generateSecretKeyRing());
			this.pubKeyCollection = PGPPublicKeyRingCollection.addPublicKeyRing(this.pubKeyCollection, keyRingGenerator.generatePublicKeyRing());
			saveAllKnownPublicKeys();
			saveAllKnownSecretKeys();
			result = true;
		} catch (InvalidKeyException | NoSuchProviderException | SignatureException | NoSuchAlgorithmException | IOException | PGPException e) {
			this.logConnector.log(e);
			checkState();
		} finally {
			if (this.keyWriteLock.isHeldByCurrentThread()) {
				this.keyWriteLock.unlock();
			}
		}
		if (result) {
			checkState();
		}
		return result;
	}

	/**
	 * Gets all known public key info without selected.
	 *
	 * @return public key info
	 */
	public Map<Long, KeyRingInfo> getAllKnownPublicKeyInfosWithoutSelected() {
		this.keyReadLock.lock();
		final Map<Long, KeyRingInfo> result = PGPKeyUtils.getKeyInfoMapFromPubKeyCollection(this.pubKeyCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		if (this.selectedSecKey != null) {
			result.remove(this.selectedSecKey.getPublicKey().getKeyID());
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Gets all known public keys.
	 *
	 * @return known public keys
	 */
	PGPPublicKeyRingCollection getAllKnownPublicKeys() {
		return this.pubKeyCollection;
	}

	/**
	 * Gets all known public only key info.
	 *
	 * @return all known public only key info
	 */
	public Map<Long, KeyRingInfo> getAllKnownPublicOnlyKeyInfo() {
		this.keyReadLock.lock();
		final Map<Long, KeyRingInfo> result = PGPKeyUtils.getDifference(PGPKeyUtils.getKeyInfoMapFromPubKeyCollection(this.pubKeyCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE), PGPKeyUtils.getKeyInfoMapFromSecKeyCollection(this.secKeyCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE));
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Gets all known secret key info.
	 *
	 * @return all known secret key info
	 */
	public Map<Long, KeyRingInfo> getAllKnownSecretKeyInfo() {
		this.keyReadLock.lock();
		final Map<Long, KeyRingInfo> result = PGPKeyUtils.getKeyInfoMapFromSecKeyCollection(this.secKeyCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Gets the key list cell renderer for key management dialog.
	 *
	 * @return the key list cell renderer
	 */
	public DefaultListCellRenderer getKeyListCellRenderer() {
		return new KeyRingInfoRenderer();
	}

	/**
	 * Gets a set of key IDs from a single string. This is currently used to store sharing info in the database.
	 * <p>
	 * TODO: Find a better solution. JSON? Single element for every key?
	 *
	 * @param keysString the keys string
	 * @return the key set
	 */
	private Set<Long> getKeySet(final String keysString) {
		final Set<Long> result = new HashSet<Long>();
		if ((keysString != null) && !keysString.isEmpty()) {
			for (final String sId : keysString.split(PGPKeyManager.ID_SEPARATOR)) {
				try {
					final Long id = Long.valueOf(sId);
					result.add(id);
				} catch (final NumberFormatException e) {
					this.logConnector.log(e);
				}
			}
		}
		return result;
	}

	/**
	 * Gets a string representation of a set of key IDs. This is currently used to store sharing info in the database.
	 * <p>
	 * TODO: Find a better solution. JSON? Single element for every key?
	 *
	 * @param keySet the key set
	 * @return the keys string
	 */
	private String getKeysString(final Set<Long> keySet) {
		String s = "";
		for (final Long l : keySet) {
			if (!s.isEmpty()) {
				s += PGPKeyManager.ID_SEPARATOR;
			}
			s += l;
		}
		return s;
	}

	/**
	 * Gets the own encryption private key.
	 *
	 * @return the own encryption private key
	 */
	PGPPrivateKey getOwnEncPrivateKey() {
		return this.ownEncPrivateKey;
	}

	/**
	 * Gets the own encryption public key.
	 *
	 * @return the own encryption public key
	 */
	PGPPublicKey getOwnEncPublicKey() {
		return this.ownEncPublicKey;
	}

	/**
	 * Gets the own signature private key.
	 *
	 * @return the own signature private key
	 */
	PGPPrivateKey getOwnSignPrivateKey() {
		return this.ownSignPrivateKey;
	}

	/**
	 * Gets the own signature public key.
	 *
	 * @return the own signature public key
	 */
	PGPPublicKey getOwnSignPublicKey() {
		return this.ownSignPublicKey;
	}

	/**
	 * Gets a human readable representation of all private key ring fingerprints as a singe string.
	 *
	 * @return the private key fingerprints
	 */
	String getPrivateKeyFingerprints() {
		final Map<Long, KeyRingInfo> keyInfos = getAllKnownSecretKeyInfo();
		if (keyInfos.isEmpty()) {
			return null;
		} else {
			this.keyReadLock.lock();
			final List<String> fingerprints = new ArrayList<String>();
			for (final KeyRingInfo info : keyInfos.values()) {
				fingerprints.add(HumanReadableIdGenerationHelper.getHumanReadablePresentation(info.getKeyId()));
			}
			this.keyReadLock.unlock();
			return Joiner.on("\n").skipNulls().join(fingerprints);
		}
	}

	/**
	 * Gets the applicable sharing keys for a given path.
	 *
	 * @param path the path
	 * @return the sharing keys
	 */
	PGPPublicKeyRingCollection getSharingKeys(final String[] path) {
		PGPPublicKeyRingCollection result = null;
		this.keyReadLock.lock();
		final Map<Long, String[]> keyMap = getSharingKeys(path, true);
		if (keyMap != null) {
			result = PGPKeyUtils.getSelectionFromPubKeyList(this.pubKeyCollection, keyMap.keySet(), PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Gets the applicable sharing keys for a given (parent) path. Optionally include keys for the element itself.
	 *
	 * @param path the path
	 * @param includeElementItself set to true to include keys for the element itself
	 * @return the sharing keys
	 */
	private Map<Long, String[]> getSharingKeys(final String[] path, final boolean includeElementItself) {
		Map<Long, String[]> result = new HashMap<Long, String[]>();
		if ((path != null) && (!includeElementItself || (includeElementItself && (path.length > 0)))) {
			int length = path.length;
			if (!includeElementItself) {
				length--;
			}
			for (int i = 0; i <= length; i++) {
				final String[] curPath = Arrays.copyOfRange(path, 0, i);
				final String[] hashedCurPath = this.engine.hashPath(curPath);
				if (hashedCurPath == null) {
					break;
				}
				DataElement elem = null;
				try {
					elem = this.componentConfigurationController.getElement(PGPKeyManager.SHARE_CONFIG_DOMAIN, hashedCurPath);
				} catch (IllegalArgumentException | DatabaseException e) {
					this.logConnector.log(e);
					result = null;
				}
				if (elem == null) {
					break;
				} else {
					final String keyIds = elem.getAdditionalProperty(PGPKeyManager.DB_PROP___SHARING_KEY_IDS);
					if (keyIds != null) {
						Set<Long> curKeySet;
						if (keyIds.equalsIgnoreCase(PGPKeyManager.DB_PROP___SHARING_KEY_IDS___ALL)) {
							curKeySet = getAllKnownPublicOnlyKeyInfo().keySet();
							for (final Long l : curKeySet) {
								result.put(l, curPath);
							}
							break;
						} else {
							curKeySet = getKeySet(keyIds);
							for (final Long l : curKeySet) {
								if (!result.containsKey(l)) {
									result.put(l, curPath);
								}
							}
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Imports keys.
	 *
	 * @return true, if successful
	 */
	public boolean importKeys() {
		final JFileChooser chooser = new JFileChooser();
		final int option = chooser.showOpenDialog(null);
		boolean result = true;
		if ((option == JFileChooser.APPROVE_OPTION) && (chooser.getSelectedFile() != null)) {
			this.keyWriteLock.lock();
			ByteSource bs;
			try {
				bs = ByteSource.wrap(ByteStreams.toByteArray(new FileInputStream(chooser.getSelectedFile())));
				try {
					final List<KeyRingInfo> keyInfos = addNewSecretKey(bs.openStream());
					addSelectedPublicKey(keyInfos, bs.openStream());
				} catch (final Exception e1) {
					this.logConnector.log(e1);
				}
				try {
					addNewPublicKey(bs.openStream());
				} catch (final Exception e1) {
					this.logConnector.log(e1);
				}
			} catch (final IOException e) {
				this.logConnector.log(e);
				result = false;
			}
			this.keyWriteLock.unlock();
		}
		return result;
	}

	/**
	 * Imports public keys.
	 * <p>
	 * TODO: Move to view/UI classes?
	 *
	 * @return true, if successful
	 */
	public boolean importPublicKeys() {
		boolean result = false;
		InputStream in = null;
		try {
			final String file = UserInterfaceElementFactory.getFileOpenPath(null);
			if ((file == null) || file.isEmpty() || Files.notExists(Paths.get(file))) {
				// TODO: Display error.
				return false;
			}
			in = new FileInputStream(file);
			final PGPPublicKeyRingCollection importCollection = PGPKeyUtils.readArmoredPubKeyRingCollectionFromStream(in);
			final List<KeyRingInfo> selection = KeyRingSelectionDialog.showSelectionListDialog("Select pub keys", PGPKeyUtils.getKeyInfoMapFromPubKeyCollection(importCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE).values(), null, true, null);
			for (final KeyRingInfo keyInfo : selection) {
				final PGPPublicKeyRing keyRing = importCollection.getPublicKeyRing(keyInfo.getKeyId());
				if (keyRing != null) {
					result = addKeyToKnownPublicKeys(keyRing);
				}
			}
		} catch (final Exception e) {
			this.logConnector.log(e);
		}
		try {
			if (in != null) {
				in.close();
			}
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		return result;
	}

	/**
	 * Initializes the key manager.
	 *
	 * @return true, if successful
	 */
	boolean initialize() {
		this.keyWriteLock.lock();
		boolean result = false;
		try {
			this.componentConfigurationController.initializeElementDomains(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.SHARE_CONFIG_DOMAIN);
			final String threadNamePrefix = this.componentConfigurationController.getComponentName() + "-" + this.getClass().getSimpleName() + "-%d";
			this.service = Executors.newFixedThreadPool(5, new ThreadFactoryBuilder().setNameFormat(threadNamePrefix).build());
			this.pubKeyCollection = new BcPGPPublicKeyRingCollection(new ArrayList<PGPPublicKeyRing>());
			this.secKeyCollection = new BcPGPSecretKeyRingCollection(new ArrayList<PGPSecretKeyRing>());
			readAllKnownPublicKeysFromFileOrDB();
			readAllKnownSecretKeysFromFileOrDB();
			result = true;
		} catch (DatabaseException | IOException | PGPException | IllegalArgumentException e) {
			this.logConnector.log(e);
		}
		this.keyWriteLock.unlock();
		return result;
	}

	/**
	 * Checks if a given path is shared with anybody.
	 *
	 * @param path the path
	 * @return true, if shared
	 */
	boolean isShared(final String[] path) {
		if (path == null) {
			return false;
		}
		final Map<Long, String[]> keys = getSharingKeys(path, true);
		if ((keys == null) || keys.isEmpty()) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Manages public keys.
	 * <p>
	 * TODO: Move to view/UI classes?
	 */
	public void managePubKeys() {
		new PublicKeyManagementDialog(this);
	}

	/**
	 * Manages secret keys.
	 * <p>
	 * TODO: Move to view/UI classes?
	 */
	public void manageSecKeys() {
		new SecretKeyManagementDialog(this);
	}

	/**
	 * Prepares private keys for usage (asks for password).
	 *
	 * @return true, if successful
	 */
	private boolean preparePrivateKeys() {
		this.keyWriteLock.lock();
		boolean result = false;
		try {
			final DataElement dbSKElem = this.componentConfigurationController.getElement(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___SELECTED_SEC_KEY);
			if (dbSKElem != null) {
				final String secKeyIdString = dbSKElem.getAdditionalProperty(PGPKeyManager.DB_PROP___SEC_KEY_ID);
				if ((secKeyIdString != null) && !secKeyIdString.isEmpty()) {
					try {
						final long secKeyId = Long.valueOf(secKeyIdString);
						if (this.secKeyCollection.contains(secKeyId)) {
							this.selectedSecKey = this.secKeyCollection.getSecretKeyRing(secKeyId);
						}
					} catch (final NumberFormatException e) {
						this.logConnector.log(e);
					}
				}
			}
			if (this.selectedSecKey == null) {
				this.selectedSecKey = selectSecretKeyRing();
			}
			if (this.selectedSecKey != null) {
				final char[] password = PasswordDialog.showSecretKeyPasswordDialog(this.localizationConnector.getLocalizedString("Enter Password for key") + " " + ((String) this.selectedSecKey.getSecretKey().getUserIDs().next()) + ":");
				if ((password == null) || (password.length == 0)) {
					result = false;
				} else {
					try {
						this.ownEncPublicKey = PGPKeyUtils.getEncPublicKey(this.selectedSecKey);
						this.ownSignPublicKey = PGPKeyUtils.getSignPublicKey(this.selectedSecKey);
						this.ownEncPrivateKey = PGPKeyUtils.getPrivateKey(PGPKeyUtils.getEncSecretKey(this.selectedSecKey), password);
						this.ownSignPrivateKey = PGPKeyUtils.getPrivateKey(PGPKeyUtils.getSignSecretKey(this.selectedSecKey), password);
						result = true;
					} catch (final PGPException e) {
						// no logging here to protect sensitive data (password)
						result = false;
						throw new PGPException("error retrieving private keys");
					}
				}
			}
		} catch (DatabaseException | PGPException | IllegalArgumentException e) {
			this.logConnector.log(e);
		}
		if (result) {
			checkState();
		}
		this.keyWriteLock.unlock();
		return result;
	}

	/**
	 * Reads all known public keys from file or database.
	 */
	private void readAllKnownPublicKeysFromFileOrDB() {
		this.keyWriteLock.lock();
		if (this.externalKeyStoragePort != null) {
			try {
				if (this.prosumerConnector.getElement(this.externalKeyStoragePort, PGPKeyManager.PUBKEYS_PATH) != null) {
					readAllKnownPublicKeysFromStream(this.prosumerConnector.readData(this.externalKeyStoragePort, PGPKeyManager.PUBKEYS_PATH));
				}
			} catch (ModuleException | BrokerException | AuthorizationException e) {
				this.logConnector.log(e);
			}
		} else {
			try {
				final DataElement keyDataElement = this.componentConfigurationController.getElement(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___KEY_DATA);
				if (keyDataElement != null) {
					final String pubKeyData = keyDataElement.getAdditionalProperty(PGPKeyManager.DB_PROP___PUB_KEY_DATA);
					if ((pubKeyData != null) && !pubKeyData.isEmpty()) {
						readAllKnownPublicKeysFromStream(new ByteArrayInputStream(pubKeyData.getBytes()));
					}
				}
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		}
		this.keyWriteLock.unlock();
	}

	/**
	 * Reads all known public keys from input stream.
	 *
	 * @param in the input stream to read from
	 */
	private void readAllKnownPublicKeysFromStream(final InputStream in) {
		this.keyWriteLock.lock();
		this.pubKeyCollection = PGPKeyUtils.readArmoredPubKeyRingCollectionFromStream(in);
		try {
			in.close();
		} catch (final IOException e) {
		}
		// if (pubKeyCollection == null) {
		// try {
		// pubKeyCollection = new PGPPublicKeyRingCollection(new ArrayList<PGPPublicKeyRing>());
		// } catch (IOException | PGPException e) {
		// logConnector.log(e);
		// }
		// }
		this.keyWriteLock.unlock();
	}

	/**
	 * Reads all known secret keys from file or database.
	 */
	private void readAllKnownSecretKeysFromFileOrDB() {
		this.keyWriteLock.lock();
		if (this.externalKeyStoragePort != null) {
			try {
				if (this.prosumerConnector.getElement(this.externalKeyStoragePort, PGPKeyManager.SECKEYS_PATH) != null) {
					readAllKnownSecretKeysFromStream(this.prosumerConnector.readData(this.externalKeyStoragePort, PGPKeyManager.SECKEYS_PATH));
				}
				this.keyWriteLock.unlock();
				return;
			} catch (ModuleException | BrokerException | AuthorizationException e) {
				this.logConnector.log(e);
			}
		} else {
			try {
				final DataElement keyDataElement = this.componentConfigurationController.getElement(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___KEY_DATA);
				if (keyDataElement != null) {
					final String secKeyData = keyDataElement.getAdditionalProperty(PGPKeyManager.DB_PROP___SEC_KEY_DATA);
					if ((secKeyData != null) && !secKeyData.isEmpty()) {
						readAllKnownSecretKeysFromStream(new ByteArrayInputStream(secKeyData.getBytes()));
					}
				}
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		}
		this.keyWriteLock.unlock();
	}

	/**
	 * Read all known secret keys from input stream.
	 *
	 * @param in the input stream to read from
	 */
	private void readAllKnownSecretKeysFromStream(final InputStream in) {
		this.keyWriteLock.lock();
		this.secKeyCollection = PGPKeyUtils.readArmoredSecKeyRingCollectionFromStream(in);
		try {
			in.close();
		} catch (final Exception e) {
		}
		this.keyWriteLock.unlock();
	}

	/**
	 * Removes all sharing keys for a given path.
	 *
	 * @param path the path
	 */
	void removeAllSharingKeys(final String[] path) {
		if (path != null) {
			setSharingKeysInternal(path, Collections.<Long> emptySet(), false);
		}
	}

	/**
	 * Removes a public key.
	 *
	 * @param keyId the key ID to remove
	 * @return true, if successful
	 */
	private boolean removePublicKey(final Long keyId) {
		boolean result = false;
		this.keyWriteLock.lock();
		try {
			if (!this.secKeyCollection.contains(keyId)) {
				try {
					final PGPPublicKeyRing pRing = this.pubKeyCollection.getPublicKeyRing(keyId);
					if (pRing != null) {
						this.pubKeyCollection = PGPPublicKeyRingCollection.removePublicKeyRing(this.pubKeyCollection, this.pubKeyCollection.getPublicKeyRing(keyId));
						saveAllKnownPublicKeys();
						result = true;
					}
				} catch (final PGPException e) {
					this.logConnector.log(e);
				}
			}
		} catch (final PGPException e) {
			this.logConnector.log(e);
		}
		this.keyWriteLock.unlock();
		return result;
	}

	/**
	 * Removes public keys.
	 *
	 * @param list the list of public key info to remove
	 */
	public void removePublicKeys(final List<KeyRingInfo> list) {
		this.keyWriteLock.lock();
		for (final KeyRingInfo info : list) {
			removePublicKey(info.getKeyId());
		}
		this.keyWriteLock.unlock();
	}

	/**
	 * Removes a secret key.
	 *
	 * @param keyId the key ID to remove
	 * @return true, if successful
	 */
	private boolean removeSecretKey(final Long keyId) {
		boolean result = false;
		this.keyWriteLock.lock();
		try {
			final PGPSecretKeyRing sRing = this.secKeyCollection.getSecretKeyRing(keyId);
			if (sRing != null) {
				this.secKeyCollection = PGPSecretKeyRingCollection.removeSecretKeyRing(this.secKeyCollection, sRing);
				removePublicKey(keyId);
				saveAllKnownSecretKeys();
				saveAllKnownPublicKeys();
				result = true;
			}
		} catch (final PGPException e) {
			this.logConnector.log(e);
		}
		this.keyWriteLock.unlock();
		return result;
	}

	/**
	 * Removes secret keys.
	 *
	 * @param list the list of secret key info to remove
	 */
	public void removeSecretKeys(final List<KeyRingInfo> list) {
		this.keyWriteLock.lock();
		for (final KeyRingInfo info : list) {
			removeSecretKey(info.getKeyId());
		}
		this.keyWriteLock.unlock();
		checkState();
	}

	/**
	 * Saves all known public keys to database and optionally to file.
	 */
	private void saveAllKnownPublicKeys() {
		this.keyWriteLock.lock();
		if (this.externalKeyStoragePort != null) {
			final String[] oldPath = Arrays.copyOf(PGPKeyManager.PUBKEYS_OLD_PATH, PGPKeyManager.PUBKEYS_OLD_PATH.length);
			oldPath[oldPath.length - 1] += System.currentTimeMillis();
			boolean oldFileMoved = false;
			try {
				this.prosumerConnector.move(this.externalKeyStoragePort, PGPKeyManager.PUBKEYS_PATH, oldPath);
				oldFileMoved = true;
			} catch (BrokerException | ModuleException | AuthorizationException e1) {
				// ignored
			}
			try {
				writeAllKnownPublicKeysToStream(this.prosumerConnector.writeData(this.externalKeyStoragePort, PGPKeyManager.PUBKEYS_PATH));
				if (oldFileMoved) {
					this.prosumerConnector.delete(this.externalKeyStoragePort, oldPath);
				}
			} catch (ModuleException | BrokerException | AuthorizationException e) {
				this.logConnector.log(e);
			}
		}
		if (this.pubKeyCollection.size() > 0) {
			final ByteArrayOutputStream baOut = new ByteArrayOutputStream();
			writeAllKnownPublicKeysToStream(baOut);
			final DataElement elem = new DataElement(PGPKeyManager.DB___KEY_DATA);
			elem.addAdditionalProperty(PGPKeyManager.DB_PROP___PUB_KEY_DATA, new String(baOut.toByteArray()));
			try {
				this.componentConfigurationController.storeElement(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___KEY_DATA, elem);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		} else {
			try {
				this.componentConfigurationController.deleteElementProperty(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___KEY_DATA, PGPKeyManager.DB_PROP___PUB_KEY_DATA);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		}
		this.keyWriteLock.unlock();
	}

	/**
	 * Saves all known secret keys to database and optionally to file.
	 */
	private void saveAllKnownSecretKeys() {
		this.keyWriteLock.lock();
		if (this.externalKeyStoragePort != null) {
			final String[] oldPath = Arrays.copyOf(PGPKeyManager.SECKEYS_OLD_PATH, PGPKeyManager.SECKEYS_OLD_PATH.length);
			oldPath[oldPath.length - 1] += System.currentTimeMillis();
			boolean oldFileMoved = false;
			try {
				this.prosumerConnector.move(this.externalKeyStoragePort, PGPKeyManager.SECKEYS_PATH, oldPath);
				oldFileMoved = true;
			} catch (BrokerException | ModuleException | AuthorizationException e1) {
				// ignored
			}
			try {
				writeAllKnownSecretKeysToStream(this.prosumerConnector.writeData(this.externalKeyStoragePort, PGPKeyManager.SECKEYS_PATH));
				if (oldFileMoved) {
					this.prosumerConnector.delete(this.externalKeyStoragePort, oldPath);
				}
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
			}
		}
		if (this.secKeyCollection.size() > 0) {
			final ByteArrayOutputStream baOut = new ByteArrayOutputStream();
			writeAllKnownSecretKeysToStream(baOut);
			final DataElement elem = new DataElement(PGPKeyManager.DB___KEY_DATA);
			elem.addAdditionalProperty(PGPKeyManager.DB_PROP___SEC_KEY_DATA, new String(baOut.toByteArray()));
			try {
				this.componentConfigurationController.storeElement(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___KEY_DATA, elem);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		} else {
			try {
				this.componentConfigurationController.deleteElementProperty(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___KEY_DATA, PGPKeyManager.DB_PROP___SEC_KEY_DATA);
			} catch (IllegalArgumentException | DatabaseException e) {
				this.logConnector.log(e);
			}
		}
		this.keyWriteLock.unlock();
	}

	/**
	 * Selects the (own) secret key ring to use for operation.
	 *
	 * @return the PGP secret key ring
	 */
	private PGPSecretKeyRing selectSecretKeyRing() {
		this.keyWriteLock.lock();
		final Map<Long, KeyRingInfo> secKeyMap = PGPKeyUtils.getKeyInfoMapFromSecKeyCollection(this.secKeyCollection, PGPCryptoModule.MIN_ASYM_KEY_SIZE);
		PGPSecretKeyRing result = null;
		if (!secKeyMap.isEmpty() && (secKeyMap.size() == 1)) {
			try {
				result = this.secKeyCollection.getSecretKeyRing(secKeyMap.values().iterator().next().getKeyId());
			} catch (final PGPException e) {
				this.logConnector.log(e);
			}
		} else {
			final List<KeyRingInfo> keyList = KeyRingSelectionDialog.showSelectionListDialog("Select secret key to use", secKeyMap.values(), null, false, null);
			if ((keyList.size() == 1) && (keyList.get(0) instanceof KeyRingInfo)) {
				try {
					final Long id = keyList.get(0).getKeyId();
					final DataElement elem = new DataElement(PGPKeyManager.DB___SELECTED_SEC_KEY);
					elem.addAdditionalProperty(PGPKeyManager.DB_PROP___SEC_KEY_ID, id.toString());
					this.componentConfigurationController.storeElement(PGPKeyManager.KEY_CONFIG_DOMAIN, PGPKeyManager.DB___SELECTED_SEC_KEY, elem);
					result = this.secKeyCollection.getSecretKeyRing(id);
				} catch (PGPException | IllegalArgumentException | DatabaseException e) {
					this.logConnector.log(e);
				}
			}
		}
		this.keyWriteLock.unlock();
		return result;
	}

	/**
	 * Sets the sharing keys for a given path.
	 *
	 * @param path the path to set sharing keys for
	 */
	void setSharingKeys(final String[] path) {
		if (path != null) {
			setSharingKeysInternal(path, false);
		}
	}

	/**
	 * Sets the sharing keys for a given path (internal method).
	 *
	 * @param path the path to set sharing keys for
	 * @param addAll set to true to add all available keys
	 */
	void setSharingKeysInternal(final String[] path, final boolean addAll) {
		this.service.execute(new Runnable() {

			@Override
			public void run() {
				PGPKeyManager.this.keyReadLock.lock();
				final Map<Long, String[]> curKeys = getSharingKeys(path, true);
				final Map<Long, KeyRingInfo> availPubKeys = getAllKnownPublicKeyInfosWithoutSelected();
				PGPKeyManager.this.keyReadLock.unlock();
				if (curKeys != null) {
					final Set<Long> curKeysElem = new HashSet<Long>();
					final Map<Long, String[]> curKeysParent = new HashMap<Long, String[]>();
					for (final Entry<Long, String[]> e : curKeys.entrySet()) {
						if (Arrays.equals(e.getValue(), path)) {
							curKeysElem.add(e.getKey());
						} else {
							curKeysParent.put(e.getKey(), e.getValue());
						}
					}
					final List<KeyRingInfo> availPubKeyInfos = new ArrayList<KeyRingInfo>();
					final List<Integer> selection = new ArrayList<Integer>();
					int availPubKeyCount = 0;
					final List<KeyRingInfo> inheritedShares = new ArrayList<KeyRingInfo>();

					for (final Entry<Long, KeyRingInfo> e : availPubKeys.entrySet()) {
						if (curKeysParent.keySet().contains(e.getKey())) {
							final KeyRingInfo info = e.getValue();
							info.setRelatingPath(curKeysParent.get(e.getKey()));
							inheritedShares.add(info);
						} else {
							availPubKeyInfos.add(e.getValue());
							if (curKeysElem.contains(e.getKey())) {
								selection.add(availPubKeyCount);
							}
							availPubKeyCount++;
						}
					}
					List<KeyRingInfo> selectedKeys;
					if (addAll) {
						selectedKeys = availPubKeyInfos;
					} else {
						selectedKeys = KeyRingSelectionDialog.showSelectionListDialog("Select pub keys", availPubKeyInfos, selection, true, inheritedShares);
					}
					final Set<Long> selectedKeyIds = new HashSet<Long>();
					for (final KeyRingInfo info : selectedKeys) {
						selectedKeyIds.add(info.getKeyId());
					}
					setSharingKeysInternal(path, selectedKeyIds, false);
				}
			}
		});
	}

	/**
	 * Sets the sharing keys for a given path (internal method).
	 *
	 * @param path the path to set sharing keys for
	 * @param keySet the key set to set
	 * @param keepOld true to merge with already set keys
	 * @return true, if successful
	 */
	private boolean setSharingKeysInternal(final String[] path, final Set<Long> keySet, final boolean keepOld) {
		boolean result = false;
		DataElement elem = null;
		try {
			elem = this.componentConfigurationController.getElement(PGPKeyManager.SHARE_CONFIG_DOMAIN, path);
			if ((path.length > 0) || (elem != null)) {
				if (keySet != null) {
					if (keySet.isEmpty()) {
						final Set<DataElement> children = this.componentConfigurationController.getChildElements(PGPKeyManager.SHARE_CONFIG_DOMAIN, path);
						if ((children == null) || children.isEmpty()) {
							return this.componentConfigurationController.deleteElement(PGPKeyManager.SHARE_CONFIG_DOMAIN, path);
						}
					}
					if (elem == null) {
						elem = new DataElement(path);
					} else if (keepOld) {
						keySet.addAll(getKeySet(elem.getAdditionalProperty(PGPKeyManager.DB_PROP___SHARING_KEY_IDS)));
					}
					elem.addAdditionalProperty(PGPKeyManager.DB_PROP___SHARING_KEY_IDS, getKeysString(keySet));
				} else {
					elem.addAdditionalProperty(PGPKeyManager.DB_PROP___SHARING_KEY_IDS, PGPKeyManager.DB_PROP___SHARING_KEY_IDS___ALL);
				}
				final String[] hashedPath = this.engine.hashPath(path);
				if (hashedPath != null) {
					this.componentConfigurationController.storeElement(PGPKeyManager.SHARE_CONFIG_DOMAIN, hashedPath, elem);
					result = true;
				}
			}
		} catch (IllegalArgumentException | DatabaseException e) {
			this.logConnector.log(e);
		}
		return result;
	}

	/**
	 * Starts the key manager.
	 *
	 * @return true, if successful
	 */
	boolean start() {
		return preparePrivateKeys();
	}

	/**
	 * Stops the key manager.
	 */
	void stop() {
		this.keyWriteLock.lock();
		this.selectedSecKey = null;
		this.ownEncPublicKey = null;
		this.ownSignPublicKey = null;
		this.ownEncPrivateKey = null;
		this.ownSignPrivateKey = null;
		this.keyWriteLock.unlock();
	}

	/**
	 * Writes all known keys to output stream.
	 *
	 * @param out the output stream to write to
	 * @return true, if successful
	 */
	private boolean writeAllKnownKeysToStream(final OutputStream out) {
		boolean result = false;
		this.keyReadLock.lock();
		result = PGPKeyUtils.writeArmoredPubSecKeyRingCollectionToStream(this.pubKeyCollection, this.secKeyCollection, out);
		try {
			out.close();
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Write all known own keys to output stream. This means key rings where the manager possesses the public AND the corresponding secret (private) keys
	 *
	 * @param out the output stream to write to
	 * @return true, if successful
	 */
	private boolean writeAllKnownOwnKeysToStream(final OutputStream out) {
		boolean result = false;
		PGPPublicKeyRingCollection ownPubKeyCollection;
		try {
			ownPubKeyCollection = new PGPPublicKeyRingCollection(new ArrayList<PGPPublicKeyRing>());
		} catch (IOException | PGPException e1) {
			return result;
		}
		this.keyReadLock.lock();
		final Iterator<?> rIt = this.secKeyCollection.getKeyRings();
		while (rIt.hasNext()) {
			final Object obj = rIt.next();
			if (obj instanceof PGPSecretKeyRing) {
				final PGPSecretKeyRing sRing = (PGPSecretKeyRing) obj;
				final PGPPublicKey pKey = sRing.getPublicKey();
				if (pKey != null) {
					PGPPublicKeyRing pRing;
					try {
						pRing = this.pubKeyCollection.getPublicKeyRing(pKey.getKeyID());
						if (pRing != null) {
							ownPubKeyCollection = PGPPublicKeyRingCollection.addPublicKeyRing(ownPubKeyCollection, pRing);
						}
					} catch (final PGPException e) {
						this.logConnector.log(e);
					}
				}
			}
		}
		result = PGPKeyUtils.writeArmoredPubSecKeyRingCollectionToStream(ownPubKeyCollection, this.secKeyCollection, out);
		try {
			out.close();
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Write all known public keys to output stream.
	 *
	 * @param out the output stream to write to
	 * @return true, if successful
	 */
	private boolean writeAllKnownPublicKeysToStream(final OutputStream out) {
		boolean result = false;
		this.keyReadLock.lock();
		result = PGPKeyUtils.writeArmoredPubKeyRingCollectionToStream(this.pubKeyCollection, out);
		try {
			out.close();
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Write all known secret keys to output stream.
	 *
	 * @param out the output stream to write to
	 * @return true, if successful
	 */
	private boolean writeAllKnownSecretKeysToStream(final OutputStream out) {
		boolean result = false;
		this.keyReadLock.lock();
		result = PGPKeyUtils.writeArmoredSecKeyRingCollectionToStream(this.secKeyCollection, out);
		try {
			out.close();
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Write specific public keys to output stream. Will select key rings that include the given key ID.
	 *
	 * @param out the output stream to write to
	 * @param keyIds the key IDs
	 */
	public void writePublicKeys(final OutputStream out, final Long... keyIds) {
		this.keyReadLock.lock();
		PGPPublicKeyRing keyRing;
		try {
			PGPPublicKeyRingCollection collection = new PGPPublicKeyRingCollection(new ArrayList<PGPPublicKeyRing>());
			for (final Long l : keyIds) {
				keyRing = this.pubKeyCollection.getPublicKeyRing(l);
				if ((keyRing != null) && !collection.contains(l)) {
					collection = PGPPublicKeyRingCollection.addPublicKeyRing(collection, keyRing);
				}
			}
			PGPKeyUtils.writeArmoredPubKeyRingCollectionToStream(collection, out);
		} catch (PGPException | IOException e) {
			this.logConnector.log(e);
		}
		try {
			out.close();
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
	}

	/**
	 * Write specific secret keys to output stream. Will select key rings that include the given key ID.
	 *
	 * @param out the output stream to write to
	 * @param keyIds the key IDs
	 */
	public void writeSecretKeys(final OutputStream out, final Long... keyIds) {
		this.keyReadLock.lock();
		PGPSecretKeyRing keyRing;
		try {
			PGPSecretKeyRingCollection collection = new PGPSecretKeyRingCollection(new ArrayList<PGPPublicKeyRing>());
			for (final Long l : keyIds) {
				keyRing = this.secKeyCollection.getSecretKeyRing(l);
				if ((keyRing != null) && !this.secKeyCollection.contains(l)) {
					collection = PGPSecretKeyRingCollection.addSecretKeyRing(collection, keyRing);
				}
			}
			PGPKeyUtils.writeArmoredSecKeyRingCollectionToStream(collection, out);
		} catch (PGPException | IOException e) {
			this.logConnector.log(e);
		}
		try {
			out.close();
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
	}
}
