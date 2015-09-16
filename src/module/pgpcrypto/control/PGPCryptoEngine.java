package module.pgpcrypto.control;

import helper.DataContainerHeaderReader;
import helper.DataContainerHeaderWriter;
import helper.ObjectValidator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import javax.crypto.NoSuchPaddingException;

import module.pgpcrypto.exception.GpgCryptoException;
import module.pgpcrypto.model.CryptoActionInfo;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.util.encoders.Hex;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import db.iface.ComponentConfigurationController;
import framework.control.LocalizationConnector;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.exception.BrokerException;
import framework.exception.DataContainerException;
import framework.model.ProsumerPort;

/**
 * Cryptographic engine to manage encryption and decryption.
 * <p>
 * TODO: Add documentation on the container binary format (take picture from thesis).
 *
 * @author Stefan Werner
 */
public class PGPCryptoEngine {

	// memory footprint should be <10MB
	private static final long CACHE___MAX_ENTRIES = 100000;
	public static final String ENC_HEADER_KEY___FILENAME = "fn";
	public static final String ENC_HEADER_KEY___IV = "iv";
	public static final String ENC_HEADER_KEY___KEY = "k";
	public static final String HASH_ALGORITHM = "SHA-256";
	public static final byte[] HEADER___CRYPTO_SCHEME = { 1 };
	public static final byte[] HEADER___FORMAT_VERSION = { 1 };
	public static final byte[] HEADER___MAGIC_NUMBER = { 13, 18 };
	// not used currently, will make it possible to save used encryption scheme
	public static final String HEADER_KEY___CRYPTO_SCHEME = "cs";
	public static final String HEADER_KEY___ENC_DATA = "ed";
	public static final String HEADER_KEY___ENC_HEADER = "eh";
	// not used currently, will make it possible to switch to different decoders later on
	public static final String HEADER_KEY___FORMAT_VERSION = "fv";
	public static final String HEADER_KEY___MAGIC_NUMBER = "mn";

	private final ComponentConfigurationController componentConfigurationController;
	private final ProsumerPort externalKeyStoragePort;
	private LoadingCache<String, String> hashCache;
	private final ReentrantReadWriteLock keyLock = new ReentrantReadWriteLock(true);
	private final ReadLock keyReadLock = this.keyLock.readLock();
	private final LocalizationConnector localizationConnector;
	private final LogConnector logConnector;
	private PGPKeyManager manager;
	private final PGPCryptoModule module;
	private final ProsumerConnector prosumerConnector;

	/**
	 * Instantiates a new PGP crypto engine.
	 *
	 * @param module the module
	 * @param localizationConnector the localization connector
	 * @param logConnector the log connector
	 * @param prosumerConnector the prosumer connector
	 * @param externalKeyStoragePort the external key storage port
	 * @param componentConfigurationController the component configuration controller
	 */
	PGPCryptoEngine(final PGPCryptoModule module, final LocalizationConnector localizationConnector, final LogConnector logConnector, final ProsumerConnector prosumerConnector, final ProsumerPort externalKeyStoragePort, final ComponentConfigurationController componentConfigurationController) {
		this.localizationConnector = localizationConnector;
		this.module = module;
		this.logConnector = logConnector;
		this.prosumerConnector = prosumerConnector;
		this.externalKeyStoragePort = externalKeyStoragePort;
		this.componentConfigurationController = componentConfigurationController;
		if (externalKeyStoragePort == null) {
			this.manager = new PGPKeyManager(this, localizationConnector, logConnector, prosumerConnector, null, componentConfigurationController, this.keyLock);
		}
	}

	/**
	 * Shares the given path with all available keys.
	 *
	 * @param path the path
	 * @return true, if successful
	 */
	boolean addAllSharingKeys(final String[] path) {
		if (this.manager != null) {
			this.manager.addAllSharingKeys(path);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Enables auto add sharing keys for a given path. New sharing keys will be automatically added to this path in the future.
	 *
	 * @param path the path
	 * @return true, if successful
	 */
	boolean autoAddAllSharingKeys(final String[] path) {
		if (this.manager != null) {
			this.manager.autoAddAllSharingKeys(path);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Backups all keys.
	 *
	 * @return true, if successful
	 */
	boolean backupAllKeys() {
		if (this.manager != null) {
			return this.manager.backupAllKeys();
		} else {
			return false;
		}
	}

	/**
	 * Backups secret keys.
	 *
	 * @return true, if successful
	 */
	boolean backupSecretKeys() {
		if (this.manager != null) {
			return this.manager.backupSecretKeys();
		} else {
			return false;
		}
	}

	/**
	 * Changes selected/used (own) secret key ring.
	 */
	void changeSecretKeyRing() {
		if (this.manager != null) {
			this.manager.changeSecretKeyRing();
		}
	}

	/**
	 * Decrypts an input stream
	 *
	 * @param encryptedSourceIn the encrypted source input stream
	 * @param info the object to store info (like key ID)
	 * @return the decrypted input stream
	 */
	InputStream decrypt(final InputStream encryptedSourceIn, final CryptoActionInfo info) {
		InputStream result = null;
		this.keyReadLock.lock();
		try {
			result = decryptHeader(encryptedSourceIn, info, true);
		} catch (final Exception e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Decrypts an element name.
	 *
	 * @param encryptedSourceIn the encrypted source input stream
	 * @return the decrypted name
	 */
	String decryptElementName(final InputStream encryptedSourceIn) {
		this.keyReadLock.lock();
		final CryptoActionInfo info = new CryptoActionInfo();
		try {
			decryptHeader(encryptedSourceIn, info, false);
		} catch (final Exception e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return info.getObjectName();
	}

	/**
	 * Decrypts an element header.
	 *
	 * @param encryptedDataStream the encrypted data stream
	 * @param info the object to store info (like key ID)
	 * @param getContentStream true to also get the (decrypted input stream), not necessary if only the name is decrypted
	 * @return the input stream (may be null)
	 * @throws DataContainerException if a problem with the encryption/decryption occurred
	 * @throws IOException if an I/O exception has occurred
	 * @throws GpgCryptoException if a problem with the encryption/decryption occurred
	 * @throws PGPException if a problem within the PGP subsystem occurred
	 */
	private InputStream decryptHeader(final InputStream encryptedDataStream, final CryptoActionInfo info, final boolean getContentStream) throws DataContainerException, IOException, GpgCryptoException, PGPException {
		// TODO: Add logging!
		final DataContainerHeaderReader outerContainer = new DataContainerHeaderReader(encryptedDataStream);
		outerContainer.readHeader();
		final Map<String, byte[]> outerFields = outerContainer.getFieldMap();
		if ((outerFields == null) || !outerContainer.hasUnlimitedField() || !outerContainer.getUnlimitedFieldName().equals(PGPCryptoEngine.HEADER_KEY___ENC_DATA)) {
			return null;
		}
		final byte[] magicNum = outerFields.get(PGPCryptoEngine.HEADER_KEY___MAGIC_NUMBER);
		final byte[] formatVer = outerFields.get(PGPCryptoEngine.HEADER_KEY___FORMAT_VERSION);
		final byte[] cryptoScheme = outerFields.get(PGPCryptoEngine.HEADER_KEY___CRYPTO_SCHEME);

		if ((magicNum == null) || !Arrays.equals(magicNum, PGPCryptoEngine.HEADER___MAGIC_NUMBER) || (formatVer == null) || !Arrays.equals(formatVer, PGPCryptoEngine.HEADER___FORMAT_VERSION) || (cryptoScheme == null) || !Arrays.equals(cryptoScheme, PGPCryptoEngine.HEADER___CRYPTO_SCHEME)) {
			return null;
		}

		final byte[] encInnerHeader = outerFields.get(PGPCryptoEngine.HEADER_KEY___ENC_HEADER);

		final PGPPrivateKey ownEncPrivatKey = this.manager.getOwnEncPrivateKey();
		final PGPPublicKey ownSignPublicKey = this.manager.getOwnSignPublicKey();

		if ((ownEncPrivatKey == null) || (ownSignPublicKey == null)) {
			return null;
		}

		final byte[] decInnerHeader = PGPCryptoUtils.decryptAndVerifyByteArray(encInnerHeader, this.manager.getAllKnownPublicKeys(), ownSignPublicKey, ownEncPrivatKey, info);

		final ByteArrayInputStream innerDecIn = new ByteArrayInputStream(decInnerHeader);

		final DataContainerHeaderReader innerContainer = new DataContainerHeaderReader(innerDecIn);
		innerContainer.readHeader();
		final Map<String, byte[]> innerFields = innerContainer.getFieldMap();
		if (innerFields == null) {
			return null;
		}

		final byte[] randomIv = innerFields.get(PGPCryptoEngine.ENC_HEADER_KEY___IV);
		final byte[] randomKey = innerFields.get(PGPCryptoEngine.ENC_HEADER_KEY___KEY);
		final byte[] filenameByte = innerFields.get(PGPCryptoEngine.ENC_HEADER_KEY___FILENAME);

		if ((randomIv == null) || (randomIv.length == 0) || (randomKey == null) || (randomKey.length == 0) || (filenameByte == null) || (filenameByte.length == 0)) {
			return null;
		}

		final String filename = new String(filenameByte, "UTF-8");

		// TODO: Set more infos?
		info.setObjectName(filename);

		if (getContentStream) {
			return PGPCryptoUtils.getDecryptedInputStream(encryptedDataStream, randomKey, randomIv, PGPCryptoModule.DEFAULT___MAC_SIZE);
		} else {
			return null;
		}
	}

	/**
	 * Destroys the key manager (cleans up, removes keys).
	 */
	void destroy() {
		if (this.manager != null) {
			this.manager.destroy();
			this.manager = null;
		}
	}

	/**
	 * Encrypts an output stream.
	 *
	 * @param encryptedDestinationOut the output stream to write encrypted data to
	 * @param decryptedPath the decrypted path
	 * @return the output stream
	 */
	OutputStream encrypt(final OutputStream encryptedDestinationOut, final String[] decryptedPath) {
		final byte[] randomKey = PGPCryptoUtils.createRandomArray(PGPCryptoModule.KEY_SIZE);
		final byte[] randomIv = PGPCryptoUtils.createRandomArray(PGPCryptoModule.IV_SIZE);
		if (!ObjectValidator.checkArgsNotNull(decryptedPath, encryptedDestinationOut, randomKey, randomIv) || (decryptedPath.length == 0)) {
			return null;
		}
		OutputStream result = null;
		this.keyReadLock.lock();
		try {
			if (encryptHeader(decryptedPath, encryptedDestinationOut, randomKey, randomIv)) {
				result = PGPCryptoUtils.getOutputStreamToEncryptTo(encryptedDestinationOut, randomKey, randomIv, PGPCryptoModule.DEFAULT___MAC_SIZE);
			}
			encryptedDestinationOut.flush();
		} catch (InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | DataContainerException | IOException | PGPException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return result;
	}

	/**
	 * Encrypts an element name. Used for folders where we need to write a independent container file.
	 *
	 * @param encryptedDestinationOut the output stream to write encrypted data (element name) to
	 * @param decryptedPath the decrypted path
	 * @return true, if successful
	 */
	boolean encryptElementName(final OutputStream encryptedDestinationOut, final String[] decryptedPath) {
		final byte[] randomKey = PGPCryptoUtils.createRandomArray(PGPCryptoModule.KEY_SIZE);
		final byte[] randomIv = PGPCryptoUtils.createRandomArray(PGPCryptoModule.IV_SIZE);
		if (!ObjectValidator.checkArgsNotNull(decryptedPath, encryptedDestinationOut, randomKey, randomIv) || (decryptedPath.length < 1)) {
			return false;
		}
		boolean result = false;
		this.keyReadLock.lock();
		try {
			if (encryptHeader(decryptedPath, encryptedDestinationOut, randomKey, randomIv)) {
				result = true;
			}
		} catch (DataContainerException | IOException | PGPException e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		try {
			encryptedDestinationOut.close();
		} catch (final IOException e) {
			this.logConnector.log(e);
		}
		return result;
	}

	/**
	 * Encrypts an element header.
	 *
	 * @param decryptedPath the decrypted path
	 * @param dataStream the output stream to write encrypted data to
	 * @param randomKey the random key
	 * @param randomIv the random iv
	 * @return true, if successful
	 * @throws DataContainerException if a problem with the encryption/decryption occurred
	 * @throws IOException if an I/O exception has occurred
	 * @throws PGPException if a problem within the PGP subsystem occurred
	 */
	private boolean encryptHeader(final String[] decryptedPath, final OutputStream dataStream, final byte[] randomKey, final byte[] randomIv) throws DataContainerException, IOException, PGPException {
		// TODO: Add logging!
		final DataContainerHeaderWriter outerContainer = new DataContainerHeaderWriter(dataStream);
		outerContainer.addField(PGPCryptoEngine.HEADER_KEY___MAGIC_NUMBER, PGPCryptoEngine.HEADER___MAGIC_NUMBER);
		outerContainer.addField(PGPCryptoEngine.HEADER_KEY___FORMAT_VERSION, PGPCryptoEngine.HEADER___FORMAT_VERSION);
		outerContainer.addField(PGPCryptoEngine.HEADER_KEY___CRYPTO_SCHEME, PGPCryptoEngine.HEADER___CRYPTO_SCHEME);
		final ByteArrayOutputStream innerDecOut = new ByteArrayOutputStream();
		final DataContainerHeaderWriter innerContainer = new DataContainerHeaderWriter(innerDecOut);
		innerContainer.addField(PGPCryptoEngine.ENC_HEADER_KEY___KEY, randomKey);
		innerContainer.addField(PGPCryptoEngine.ENC_HEADER_KEY___IV, randomIv);
		final byte[] filenameByte = decryptedPath[decryptedPath.length - 1].getBytes("UTF-8");
		innerContainer.addField(PGPCryptoEngine.ENC_HEADER_KEY___FILENAME, filenameByte);
		innerContainer.writeHeader();
		innerDecOut.close();
		final byte[] decInnerHeader = innerDecOut.toByteArray();
		final PGPPublicKeyRingCollection sharingKeys = this.manager.getSharingKeys(decryptedPath);
		final PGPPublicKey ownEncPublicKey = this.manager.getOwnEncPublicKey();
		final PGPPrivateKey ownSignPrivatKey = this.manager.getOwnSignPrivateKey();
		if ((ownEncPublicKey == null) || (ownSignPrivatKey == null)) {
			return false;
		}
		final byte[] encInnerHeader = PGPCryptoUtils.encryptAndSignByteArray(decInnerHeader, sharingKeys, ownEncPublicKey, ownSignPrivatKey);
		outerContainer.addField(PGPCryptoEngine.HEADER_KEY___ENC_HEADER, encInnerHeader);
		outerContainer.addUnlimitedSizeField(PGPCryptoEngine.HEADER_KEY___ENC_DATA);
		outerContainer.writeHeader();
		return true;
	}

	/**
	 * Exports selected (own) public keys.
	 *
	 * @return true, if successful
	 */
	boolean exportSelectedOwnPublicKey() {
		if (this.manager != null) {
			return this.manager.exportSelectedOwnPublicKey();
		} else {
			return false;
		}
	}

	/**
	 * Generates a new key pair.
	 *
	 * @return true, if successful
	 */
	boolean generateKeyPair() {
		if (this.manager != null) {
			return this.manager.generateKeyPair();
		} else {
			return false;
		}
	}

	/**
	 * Gets the private key finger prints.
	 *
	 * @return the private key finger prints
	 */
	String getPrivateKeyFingerprints() {
		return this.manager.getPrivateKeyFingerprints();
	}

	/**
	 * Gets the secure hash of a element name.
	 *
	 * @param name the name
	 * @return the secure hash
	 */
	private String getSecureHash(final String name) {
		if ((name == null) || (name.length() < 1)) {
			return "";
		}
		MessageDigest md;
		try {
			md = MessageDigest.getInstance(PGPCryptoEngine.HASH_ALGORITHM);
		} catch (final NoSuchAlgorithmException e) {
			return null;
		}
		md.update(name.getBytes());
		final byte[] shaDig = md.digest();
		return new String(Hex.encode(shaDig));
	}

	/**
	 * Hashes a complete path. Will exclude special file names.
	 *
	 * @param path the path to hash
	 * @return the path with every element hashed
	 */
	String[] hashPath(final String[] path) {
		if (path == null) {
			return null;
		} else {
			final String[] result = new String[path.length];
			for (int i = 0; i < path.length; i++) {
				if (path[i].endsWith(PGPCryptoModule.UNENCRYPTED_FILE_CONTENT_SUFFIX)) {
					result[i] = path[i];
				} else if (path[i].endsWith(PGPCryptoModule.UNENCRYPTED_FOLDER_NAME_SUFFIX)) {
					result[i] = path[i].replaceAll(PGPCryptoModule.UNENCRYPTED_FOLDER_NAME_SUFFIX, "");
				} else {
					result[i] = this.hashCache.getUnchecked(path[i]);
				}
			}
			return result;
		}
	}

	/**
	 * Imports keys.
	 *
	 * @return true, if successful
	 */
	boolean importKeys() {
		if (this.manager != null) {
			return this.manager.importKeys();
		} else {
			return false;
		}
	}

	/**
	 * Checks if key manager available.
	 *
	 * @return true, if key manager available
	 */
	boolean isKeyManagerAvailable() {
		return this.manager != null;
	}

	/**
	 * Checks if a given path is shared with anybody.
	 *
	 * @param path the path
	 * @return true, if shared
	 */
	boolean isShared(final String[] path) {
		return (this.manager != null) && this.manager.isShared(path);
	}

	/**
	 * Manages public keys.
	 *
	 * @return true, if successful
	 */
	boolean managePublicKeys() {
		if (this.manager != null) {
			this.manager.managePubKeys();
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Manages secret keys.
	 *
	 * @return true, if successful
	 */
	boolean manageSecretKeys() {
		if (this.manager != null) {
			this.manager.manageSecKeys();
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Removes all sharing keys for a given path.
	 *
	 * @param path the path
	 * @return true, if successful
	 */
	boolean removeAllSharingKeys(final String[] path) {
		if (this.manager != null) {
			this.manager.removeAllSharingKeys(path);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Sets the key manager state. Used to tell the module that key manager is ready.
	 *
	 * @param configOk the new key manager state
	 */
	void setKeyManagerState(final boolean configOk) {
		if (!configOk) {
			this.module.setEngineState(false);
		}
	}

	/**
	 * Sets the sharing keys.
	 *
	 * @param path the path
	 * @return true, if successful
	 */
	boolean setSharingKeys(final String[] path) {
		if (this.manager != null) {
			this.manager.setSharingKeys(path);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Starts the engine.
	 *
	 * @return true, if successful
	 */
	boolean start() {
		this.hashCache = CacheBuilder.newBuilder().maximumSize(PGPCryptoEngine.CACHE___MAX_ENTRIES).build(new CacheLoader<String, String>() {
			@Override
			public String load(final String name) {
				return getSecureHash(name);
			}
		});
		try {
			if ((this.externalKeyStoragePort != null) && !this.prosumerConnector.isConnected(this.externalKeyStoragePort)) {
				return false;
			}
		} catch (final BrokerException e) {
			this.logConnector.log(e);
			return false;
		}
		if ((this.externalKeyStoragePort != null) && (this.manager == null)) {
			this.manager = new PGPKeyManager(this, this.localizationConnector, this.logConnector, this.prosumerConnector, this.externalKeyStoragePort, this.componentConfigurationController, this.keyLock);
		}
		if (this.manager.start()) {
			this.module.setEngineState(true);
			return true;
		} else {
			this.module.setEngineState(false);
			return false;
		}
	}

	/**
	 * Stops the engine.
	 */
	void stop() {
		if (this.manager != null) {
			this.manager.stop();
			if (this.externalKeyStoragePort != null) {
				this.manager.destroy();
				this.manager = null;
			}
		}
	}

	/**
	 * Writes encrypted directory info.
	 *
	 * @param decryptedPath the decrypted path
	 * @param out the output stream to write header to
	 * @return the hashed path
	 */
	String[] writeEncryptedDirectoryInfo(final String[] decryptedPath, final OutputStream out) {
		if ((decryptedPath == null) || (decryptedPath.length < 1)) {
			return null;
		}
		try {
			final byte[] dummy = new byte[0];
			this.keyReadLock.lock();
			encryptHeader(decryptedPath, out, dummy, dummy);
		} catch (final Exception e) {
			this.logConnector.log(e);
		}
		this.keyReadLock.unlock();
		return hashPath(decryptedPath);
	}
}
