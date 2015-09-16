package module.pgpcrypto.control;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import module.pgpcrypto.model.KeyRingInfo;
import module.pgpcrypto.model.KeyRingInfo.KEY_TYPE;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair;

/**
 * Provides static methods for key management.
 *
 * @author Stefan Werner
 */
public class PGPKeyUtils {

	/**
	 * Generates a new key pair.
	 *
	 * @param keySize the key size
	 * @param identity the identity (name/email address)
	 * @param password the password
	 * @return the PGP key ring generator
	 * @throws IOException if an I/O exception has occurred
	 * @throws InvalidKeyException if the key is invalid (for example invalid size)
	 * @throws NoSuchProviderException if the selected provider is not available
	 * @throws SignatureException if the signature cannot be generated
	 * @throws PGPException if a problem within the PGP subsystem occurred
	 * @throws NoSuchAlgorithmException if the selected algorithm is not available
	 */
	public static PGPKeyRingGenerator generateKeyPair(final int keySize, final String identity, final char[] password) throws IOException, InvalidKeyException, NoSuchProviderException, SignatureException, PGPException, NoSuchAlgorithmException {
		final RSAKeyPairGenerator keyPairGenerator = new RSAKeyPairGenerator();
		keyPairGenerator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), new SecureRandom(), keySize, 12));
		final PGPKeyPair cryptoKeyPair = new BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_ENCRYPT, keyPairGenerator.generateKeyPair(), new Date());
		final PGPKeyPair signKeyPair = new BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_SIGN, keyPairGenerator.generateKeyPair(), new Date());
		final PGPSignatureSubpacketGenerator signGenerator = new PGPSignatureSubpacketGenerator();
		signGenerator.setKeyFlags(false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER);
		signGenerator.setPreferredSymmetricAlgorithms(false, new int[] { SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192, SymmetricKeyAlgorithmTags.AES_128 });
		signGenerator.setPreferredHashAlgorithms(false, new int[] { HashAlgorithmTags.SHA512, HashAlgorithmTags.SHA384, HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA224 });
		signGenerator.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);
		final PGPSignatureSubpacketGenerator cryptoGenerator = new PGPSignatureSubpacketGenerator();
		cryptoGenerator.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);

		final PGPDigestCalculator sha1Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1);
		final PGPDigestCalculator sha256Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256);
		final PBESecretKeyEncryptor secEncryptor = (new BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha256Calc, PGPCryptoModule.CRYPTO_HASH_ITERATIONS)).build(password);

		final PGPKeyRingGenerator keyRingGenerator = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, signKeyPair, identity, sha1Calc, signGenerator.generate(), null, new BcPGPContentSignerBuilder(signKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256), secEncryptor);

		keyRingGenerator.addSubKey(cryptoKeyPair, cryptoGenerator.generate(), null);

		return keyRingGenerator;
	}

	/**
	 * Gets the difference between two key ring info maps.
	 *
	 * @param fullSet the full set
	 * @param subtrahend the subtrahend
	 * @return the difference
	 */
	public static Map<Long, KeyRingInfo> getDifference(final Map<Long, KeyRingInfo> fullSet, final Map<Long, KeyRingInfo> subtrahend) {
		final Map<Long, KeyRingInfo> result = new HashMap<Long, KeyRingInfo>();
		for (final KeyRingInfo info : fullSet.values()) {
			if (!subtrahend.containsKey(info.getKeyId())) {
				result.put(info.getKeyId(), info);
			}
		}
		return result;
	}

	/**
	 * Gets the encoded secret key.
	 *
	 * @param secKey the secret key
	 * @return the encoded secret key
	 * @throws IOException if an I/O exception has occurred
	 */
	public static byte[] getEncodedSecretKey(final PGPSecretKey secKey) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		secKey.encode(out);
		out.close();
		return out.toByteArray();
	}

	/**
	 * Gets the public key for encryption from a key ring.
	 *
	 * @param keyRing the key ring
	 * @return the public key
	 */
	public static PGPPublicKey getEncPublicKey(final PGPSecretKeyRing keyRing) {
		final Iterator<?> it = keyRing.getPublicKeys();
		// ignore master signing key
		it.next();
		while (it.hasNext()) {
			final Object obj = it.next();
			if (obj instanceof PGPPublicKey) {
				final PGPPublicKey pubKey = (PGPPublicKey) obj;
				if (pubKey.isEncryptionKey()) {
					return pubKey;
				}
			}
		}
		return null;
	}

	/**
	 * Gets the secret key for encryption from a key ring.
	 *
	 * @param keyRing the key ring
	 * @return the secret key
	 */
	public static PGPSecretKey getEncSecretKey(final PGPSecretKeyRing keyRing) {
		final Iterator<?> it = keyRing.getSecretKeys();
		while (it.hasNext()) {
			final Object obj = it.next();
			if (obj instanceof PGPSecretKey) {
				final PGPSecretKey secKey = (PGPSecretKey) obj;
				if (!secKey.isMasterKey()) {
					// ignore master signing key
					return secKey;
				}
			}
		}
		return null;
	}

	/**
	 * Gets a key info map from a public key collection.
	 *
	 * @param collection the collection
	 * @param minKeySize the minimum key size (strength)
	 * @return the key info map
	 */
	public static Map<Long, KeyRingInfo> getKeyInfoMapFromPubKeyCollection(final PGPPublicKeyRingCollection collection, final int minKeySize) {
		final Map<Long, KeyRingInfo> keyRingInfoMap = new HashMap<Long, KeyRingInfo>();
		if (collection == null) {
			return keyRingInfoMap;
		}

		final Iterator<?> keyRingIter = collection.getKeyRings();
		while (keyRingIter.hasNext()) {
			final Object obj = keyRingIter.next();
			if (obj instanceof PGPPublicKeyRing) {
				final PGPPublicKeyRing keyRing = (PGPPublicKeyRing) obj;
				final KeyRingInfo keyRingInfo = new KeyRingInfo();

				final Iterator<?> keyIter = keyRing.getPublicKeys();
				while (keyIter.hasNext()) {
					final Object ringObj = keyIter.next();
					if (ringObj instanceof PGPPublicKey) {
						final PGPPublicKey key = (PGPPublicKey) ringObj;
						String userId = "?";
						if (key.getUserIDs().hasNext()) {
							userId = (String) key.getUserIDs().next();
						}
						if (key.isMasterKey()) {
							keyRingInfo.setMasterKeyId(key.getKeyID());
							keyRingInfo.setMasterUserId(userId);
							keyRingInfo.setKeyType(KEY_TYPE.SIGN);
						} else {
							if (key.isEncryptionKey()) {
								keyRingInfo.addSubKeyInfo(new KeyRingInfo(key.getKeyID(), userId, KEY_TYPE.ENC));
							} else {
								keyRingInfo.addSubKeyInfo(new KeyRingInfo(key.getKeyID(), userId, KEY_TYPE.UNKOWN));
							}
						}
					}
				}

				keyRingInfo.setUsable(PGPKeyUtils.isPublicKeyRingUsable(keyRing, minKeySize));
				keyRingInfoMap.put(keyRingInfo.getKeyId(), keyRingInfo);
			}
		}
		return keyRingInfoMap;
	}

	/**
	 * Gets a key info map from a secret key collection.
	 *
	 * @param collection the collection
	 * @param minKeySize the minimum key size (strength)
	 * @return the key info map
	 */
	public static Map<Long, KeyRingInfo> getKeyInfoMapFromSecKeyCollection(final PGPSecretKeyRingCollection collection, final int minKeySize) {
		final Map<Long, KeyRingInfo> keyRingInfoMap = new HashMap<Long, KeyRingInfo>();
		if (collection == null) {
			return keyRingInfoMap;
		}

		final Iterator<?> keyRingIter = collection.getKeyRings();
		while (keyRingIter.hasNext()) {
			final Object obj = keyRingIter.next();
			if (obj instanceof PGPSecretKeyRing) {
				final PGPSecretKeyRing keyRing = (PGPSecretKeyRing) obj;
				final KeyRingInfo keyRingInfo = new KeyRingInfo();

				final Iterator<?> keyIter = keyRing.getSecretKeys();
				while (keyIter.hasNext()) {
					final Object ringObj = keyIter.next();
					if (ringObj instanceof PGPSecretKey) {
						final PGPSecretKey key = (PGPSecretKey) ringObj;
						String userId = "?";
						if (key.getUserIDs().hasNext()) {
							userId = (String) key.getUserIDs().next();
						}
						if (key.isMasterKey()) {
							if (key.isSigningKey() && !key.isPrivateKeyEmpty()) {
								keyRingInfo.setMasterKeyId(key.getKeyID());
								keyRingInfo.setMasterUserId(userId);
								keyRingInfo.setKeyType(KEY_TYPE.SIGN);
							} else if (key.getPublicKey().isEncryptionKey()) {
								keyRingInfo.setMasterKeyId(key.getKeyID());
								keyRingInfo.setMasterUserId(userId);
								keyRingInfo.setKeyType(KEY_TYPE.ENC);
							} else {
								keyRingInfo.setMasterKeyId(key.getKeyID());
								keyRingInfo.setMasterUserId(userId);
								keyRingInfo.addSubKeyInfo(new KeyRingInfo(key.getKeyID(), userId, KEY_TYPE.UNKOWN));
							}
						} else {
							if (key.getPublicKey().isEncryptionKey()) {
								keyRingInfo.addSubKeyInfo(new KeyRingInfo(key.getKeyID(), userId, KEY_TYPE.ENC));
							} else if (key.isSigningKey() && !key.isPrivateKeyEmpty()) {
								keyRingInfo.addSubKeyInfo(new KeyRingInfo(key.getKeyID(), userId, KEY_TYPE.SIGN));
							} else {
								keyRingInfo.addSubKeyInfo(new KeyRingInfo(key.getKeyID(), userId, KEY_TYPE.UNKOWN));
							}
						}
					}

					keyRingInfo.setUsable(PGPKeyUtils.isSecretKeyRingUsable(keyRing, minKeySize));
					keyRingInfoMap.put(keyRingInfo.getKeyId(), keyRingInfo);
				}
			}
		}
		return keyRingInfoMap;
	}

	/**
	 * Gets the private key from secret key.
	 *
	 * @param secKey the secret key
	 * @param password the password
	 * @return the private key
	 * @throws PGPException if a problem within the PGP subsystem occurred
	 */
	public static PGPPrivateKey getPrivateKey(final PGPSecretKey secKey, final char[] password) throws PGPException {
		return secKey.extractPrivateKey(new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(password));
	}

	/**
	 * Gets a selection of public keys from a collection.
	 * <p>
	 * TODO: This is ugly, change it.
	 *
	 * @param collection the collection
	 * @param keyInfoOrIdList the list containing either key info or IDs (Strings)
	 * @param minKeySize the minimum key size (strength)
	 * @return the selection
	 */
	public static PGPPublicKeyRingCollection getSelectionFromPubKeyList(final PGPPublicKeyRingCollection collection, final Collection<?> keyInfoOrIdList, final int minKeySize) {
		PGPPublicKeyRingCollection selectedCollection;
		try {
			selectedCollection = new PGPPublicKeyRingCollection(new ArrayList<PGPPublicKeyRing>());
		} catch (IOException | PGPException e) {
			return null;
		}

		if ((keyInfoOrIdList == null) || keyInfoOrIdList.isEmpty()) {
			return selectedCollection;
		}

		for (final Object obj : keyInfoOrIdList) {
			long keyId;
			if (obj instanceof Long) {
				keyId = (Long) obj;
			} else if (obj instanceof KeyRingInfo) {
				keyId = ((KeyRingInfo) obj).getKeyId();
			} else {
				continue;
			}

			PGPPublicKeyRing keyRing;
			try {
				keyRing = collection.getPublicKeyRing(keyId);
			} catch (final PGPException e) {
				continue;
			}
			// check if key exists and is usable
			if ((keyRing != null) && PGPKeyUtils.isPublicKeyRingUsable(keyRing, minKeySize)) {
				selectedCollection = PGPPublicKeyRingCollection.addPublicKeyRing(selectedCollection, keyRing);
			}
		}
		return selectedCollection;
	}

	/**
	 * Gets a selection of secret keys from a collection.
	 *
	 * @param collection the collection
	 * @param keyInfoOrIdList the list containing either key info or IDs (Strings)
	 * @param minKeySize the minimum key size (strength)
	 * @return the selection
	 */
	public static PGPSecretKeyRingCollection getSelectionFromSecKeyList(final PGPSecretKeyRingCollection collection, final Collection<?> keyInfoOrIdList, final int minKeySize) {
		PGPSecretKeyRingCollection selectedCollection;
		try {
			selectedCollection = new PGPSecretKeyRingCollection(new ArrayList<PGPSecretKeyRing>());
		} catch (IOException | PGPException e) {
			e.printStackTrace();
			return null;
		}

		if ((keyInfoOrIdList == null) || keyInfoOrIdList.isEmpty()) {
			return selectedCollection;
		}

		for (final Object obj : keyInfoOrIdList) {
			long keyId;
			if (obj instanceof Long) {
				keyId = (Long) obj;
			} else if (obj instanceof KeyRingInfo) {
				keyId = ((KeyRingInfo) obj).getKeyId();
			} else {
				continue;
			}

			PGPSecretKeyRing keyRing;
			try {
				keyRing = collection.getSecretKeyRing(keyId);
			} catch (final PGPException e) {
				continue;
			}
			// check if key exists and is usable
			if ((keyRing != null) && PGPKeyUtils.isSecretKeyRingUsable(keyRing, minKeySize)) {
				selectedCollection = PGPSecretKeyRingCollection.addSecretKeyRing(selectedCollection, keyRing);
			}
		}
		return selectedCollection;
	}

	/**
	 * Gets the signature public key.
	 *
	 * @param keyRing the key ring
	 * @return the public key
	 */
	public static PGPPublicKey getSignPublicKey(final PGPSecretKeyRing keyRing) {
		final PGPSecretKey secKey = keyRing.getSecretKey();
		if (secKey.isSigningKey() && !secKey.isPrivateKeyEmpty()) {
			return secKey.getPublicKey();
		} else {
			return null;
		}
	}

	/**
	 * Gets the signature secret key.
	 *
	 * @param keyRing the key ring
	 * @return the secret key
	 */
	public static PGPSecretKey getSignSecretKey(final PGPSecretKeyRing keyRing) {
		final PGPSecretKey secKey = keyRing.getSecretKey();
		if (secKey.isSigningKey() && !secKey.isPrivateKeyEmpty()) {
			return secKey;
		} else {
			return null;
		}
	}

	/**
	 * Checks if a public key collection usable.
	 *
	 * @param collection the collection
	 * @param minKeySize the minimum key size (strength)
	 * @return true, if usable
	 */
	public static boolean isPublicKeyCollectionUsable(final PGPPublicKeyRingCollection collection, final int minKeySize) {
		if (collection == null) {
			return false;
		}

		final Iterator<?> keyRingIter = collection.getKeyRings();
		while (keyRingIter.hasNext()) {
			final Object obj = keyRingIter.next();
			if (obj instanceof PGPPublicKeyRing) {
				final PGPPublicKeyRing keyRing = (PGPPublicKeyRing) obj;
				if (!PGPKeyUtils.isPublicKeyRingUsable(keyRing, minKeySize)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Checks if is public key ring usable. Public key rings must provide a master key to check signatures and at least one signed subkey for encryption. Every
	 * subkey needs to be signed by the master key and/or some trusted external key. Keys must still be valid and must not be revoked. Also the key strength is
	 * checked.
	 * <p>
	 * TODO: Add optional certification by third party key.
	 *
	 * @param keyRing the key ring
	 * @param minKeySize the minimum key size (strength)
	 * @return true, if usable
	 */
	public static boolean isPublicKeyRingUsable(final PGPPublicKeyRing keyRing, final int minKeySize) {
		final PGPContentVerifierBuilderProvider verifier = new BcPGPContentVerifierBuilderProvider();
		int encKeyCount = 0;
		PGPPublicKey masterCertKey = null;

		// TODO: Set external signing key here, log/show errors to user.
		final PGPPublicKey externalCertKey = null;
		boolean masterKeySignedByExternalCertKey = false;

		final Iterator<?> keyIter = keyRing.getPublicKeys();
		while (keyIter.hasNext()) {
			final Object ringObj = keyIter.next();
			if (ringObj instanceof PGPPublicKey) {
				final PGPPublicKey key = (PGPPublicKey) ringObj;

				final int algo = key.getAlgorithm();
				// check algorithm -> currently only RSA keys are allowed
				if ((algo != PublicKeyAlgorithmTags.RSA_ENCRYPT) && (algo != PublicKeyAlgorithmTags.RSA_GENERAL) && (algo != PublicKeyAlgorithmTags.RSA_SIGN)) {
					return false;
				}
				// do not allow keys below a certain minimum key size
				if (key.getBitStrength() < minKeySize) {
					return false;
				}
				// do not allow revoked keys
				if (key.isRevoked()) {
					return false;
				}
				// check key for expiration
				final long validSeconds = key.getValidSeconds();
				if (validSeconds > 0) {
					// TODO: Better use Calendar? What about the timezone?
					final long validDate = key.getCreationTime().getTime() + (validSeconds * 1000);
					if (System.currentTimeMillis() > validDate) {
						return false;
					}
				}

				boolean canSign = false;
				boolean canEncrypt = false;
				boolean isCertified = false;
				final Iterator<?> sigIter = key.getSignatures();
				while (sigIter.hasNext()) {
					final Object sigObj = sigIter.next();
					if (sigObj instanceof PGPSignature) {
						final PGPSignature sig = (PGPSignature) sigObj;
						// check if key is correctly signed: master key needs to be signed by external key (if set) and subkeys need to be signed by master key
						// ignore other signatures
						try {
							if ((masterCertKey == null) && (externalCertKey != null) && (sig.getKeyID() == externalCertKey.getKeyID())) {
								sig.init(verifier, externalCertKey);
								if (sig.verifyCertification(externalCertKey, key)) {
									masterKeySignedByExternalCertKey = true;
								}
							} else if (masterCertKey != null) {
								if (sig.getKeyID() == masterCertKey.getKeyID()) {
									sig.init(verifier, masterCertKey);
									if (!sig.verifyCertification(masterCertKey, key)) {
										continue;
									}
								} else {
									continue;
								}
							}
						} catch (final PGPException e) {
							continue;
						}

						isCertified = true;

						if (sig.hasSubpackets()) {
							final PGPSignatureSubpacketVector sp = sig.getHashedSubPackets();
							if (sp != null) {
								final int flags = sp.getKeyFlags();
								if ((flags & KeyFlags.SIGN_DATA) > 0) {
									canSign = true;
								}

								if ((flags & (KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE)) > 0) {
									canEncrypt = true;
								}
							}
						}
					}
				}
				// key rings with uncertified keys are rejected
				if (!isCertified) {
					return false;
				}
				if (key.isMasterKey()) {
					if ((masterCertKey != null) || !canSign) {
						return false;
					} else {
						masterCertKey = key;
					}
				} else if (canEncrypt) {
					encKeyCount++;
				}
			}
		}
		return (encKeyCount > 0) && ((externalCertKey == null) || masterKeySignedByExternalCertKey);
	}

	/**
	 * Checks if a secret key collection usable.
	 *
	 * @param collection the collection
	 * @param minKeySize the minimum key size (strength)
	 * @return true, if usable
	 */
	public static boolean isSecretKeyCollectionUsable(final PGPSecretKeyRingCollection collection, final int minKeySize) {
		if (collection == null) {
			return false;
		}
		final Iterator<?> keyRingIter = collection.getKeyRings();
		while (keyRingIter.hasNext()) {
			final Object obj = keyRingIter.next();
			if (obj instanceof PGPSecretKeyRing) {
				final PGPSecretKeyRing keyRing = (PGPSecretKeyRing) obj;
				if (!PGPKeyUtils.isSecretKeyRingUsable(keyRing, minKeySize)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Checks if is secret key ring usable. Secret key rings must provide a master key to check signatures and at least one signed subkey for encryption. Every
	 * subkey needs to be signed by the master key and/or some trusted external key. Keys must still be valid and must not be revoked. Also the key strength is
	 * checked.
	 * <p>
	 * TODO: Add optional certification by third party key.
	 *
	 * @param keyRing the key ring
	 * @param minKeySize the minimum key size (strength)
	 * @return true, if usable
	 */
	// optionally they must be certified by third party key
	public static boolean isSecretKeyRingUsable(final PGPSecretKeyRing keyRing, final int minKeySize) {
		final PGPContentVerifierBuilderProvider verifier = new BcPGPContentVerifierBuilderProvider();
		int encKeyCount = 0;
		PGPPublicKey masterCertKey = null;

		// TODO: Set external signing key here, log/show errors to user.
		final PGPPublicKey externalCertKey = null;
		boolean masterKeySignedByExternalCertKey = false;

		final Iterator<?> keyIter = keyRing.getSecretKeys();
		while (keyIter.hasNext()) {
			final Object ringObj = keyIter.next();
			if (ringObj instanceof PGPSecretKey) {
				final PGPSecretKey key = (PGPSecretKey) ringObj;

				final PGPPublicKey pubKey = key.getPublicKey();

				final int algo = pubKey.getAlgorithm();
				// check algorithm -> currently only RSA keys are allowed
				if ((algo != PublicKeyAlgorithmTags.RSA_ENCRYPT) && (algo != PublicKeyAlgorithmTags.RSA_GENERAL) && (algo != PublicKeyAlgorithmTags.RSA_SIGN)) {
					return false;
				}
				// do not allow keys below a certain minimum key size
				if (pubKey.getBitStrength() < minKeySize) {
					return false;
				}
				// do not allow revoked keys
				if (pubKey.isRevoked()) {
					return false;
				}
				// check key for expiration
				final long validSeconds = pubKey.getValidSeconds();
				if (validSeconds > 0) {
					// TODO: Better use Calendar? what about the timezone?
					final long validDate = pubKey.getCreationTime().getTime() + (validSeconds * 1000);
					if (System.currentTimeMillis() > validDate) {
						return false;
					}
				}

				boolean canSign = false;
				boolean canEncrypt = false;
				boolean isCertified = false;
				final Iterator<?> sigIter = key.getPublicKey().getSignatures();
				while (sigIter.hasNext()) {
					final Object sigObj = sigIter.next();
					if (sigObj instanceof PGPSignature) {
						final PGPSignature sig = (PGPSignature) sigObj;
						// check if key is correctly signed: master key needs to be signed by external key (if set) and subkeys need to be signed by master key
						// ignore other signatures
						try {
							if ((masterCertKey == null) && (externalCertKey != null) && (sig.getKeyID() == externalCertKey.getKeyID())) {
								sig.init(verifier, externalCertKey);
								if (sig.verifyCertification(externalCertKey, key.getPublicKey())) {
									masterKeySignedByExternalCertKey = true;
								}
							} else if (masterCertKey != null) {
								if (sig.getKeyID() == masterCertKey.getKeyID()) {
									sig.init(verifier, masterCertKey);
									if (!sig.verifyCertification(masterCertKey, key.getPublicKey())) {
										continue;
									}
								} else {
									continue;
								}
							}
						} catch (final PGPException e) {
							continue;
						}

						isCertified = true;

						if (sig.hasSubpackets()) {
							final PGPSignatureSubpacketVector sp = sig.getHashedSubPackets();
							if (sp != null) {
								final int flags = sp.getKeyFlags();
								if ((flags & KeyFlags.SIGN_DATA) > 0) {
									canSign = true;
								}

								if ((flags & (KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE)) > 0) {
									canEncrypt = true;
								}
							}
						}
					}
				}
				// key rings with uncertified keys are rejected
				if (!isCertified) {
					return false;
				}
				if (key.isMasterKey()) {
					if ((masterCertKey != null) || !canSign) {
						return false;
					} else {
						masterCertKey = key.getPublicKey();
					}
				} else if (canEncrypt) {
					encKeyCount++;
				}
			}
		}

		return (encKeyCount > 0) && ((externalCertKey == null) || masterKeySignedByExternalCertKey);
	}

	/**
	 * Merges two public key ring collections.
	 *
	 * @param baseCollection the base collection
	 * @param mergeCollection the merge collection
	 * @return the merged PGP public key ring collection
	 */
	public static PGPPublicKeyRingCollection mergePubKeyRingCollection(final PGPPublicKeyRingCollection baseCollection, final PGPPublicKeyRingCollection mergeCollection) {
		PGPPublicKeyRingCollection collection;
		if ((mergeCollection == null) || (mergeCollection.size() == 0)) {
			return baseCollection;
		}
		if ((baseCollection == null) || (baseCollection.size() == 0)) {
			return mergeCollection;
		}

		try {
			collection = new BcPGPPublicKeyRingCollection(baseCollection.getEncoded()); // ugly
		} catch (IOException | PGPException e) {
			e.printStackTrace();
			return baseCollection;
		}

		final Iterator<?> keyRingIter = mergeCollection.getKeyRings();
		while (keyRingIter.hasNext()) {
			final Object obj = keyRingIter.next();
			if (obj instanceof PGPPublicKeyRing) {
				final PGPPublicKeyRing keyRing = (PGPPublicKeyRing) obj;
				try {
					if (!collection.contains(keyRing.getPublicKey().getKeyID())) {
						collection = PGPPublicKeyRingCollection.addPublicKeyRing(collection, keyRing);
					}
				} catch (final PGPException e) {
					e.printStackTrace();
					return baseCollection;
				}
			}
		}
		return collection;
	}

	/**
	 * Merges two secret key ring collection.
	 *
	 * @param baseCollection the base collection
	 * @param mergeCollection the merge collection
	 * @return the merged PGP secret key ring collection
	 */
	public static PGPSecretKeyRingCollection mergeSecKeyRingCollection(final PGPSecretKeyRingCollection baseCollection, final PGPSecretKeyRingCollection mergeCollection) {
		PGPSecretKeyRingCollection collection;
		if ((mergeCollection == null) || (mergeCollection.size() == 0)) {
			return baseCollection;
		}
		if ((baseCollection == null) || (baseCollection.size() == 0)) {
			return mergeCollection;
		}

		try {
			collection = new BcPGPSecretKeyRingCollection(baseCollection.getEncoded()); // ugly
		} catch (IOException | PGPException e) {
			e.printStackTrace();
			return baseCollection;
		}

		final Iterator<?> keyRingIter = mergeCollection.getKeyRings();
		while (keyRingIter.hasNext()) {
			final Object obj = keyRingIter.next();
			if (obj instanceof PGPSecretKeyRing) {
				final PGPSecretKeyRing keyRing = (PGPSecretKeyRing) obj;
				try {
					if (!collection.contains(keyRing.getSecretKey().getKeyID())) {
						collection = PGPSecretKeyRingCollection.addSecretKeyRing(collection, keyRing);
					}
				} catch (final PGPException e) {
					e.printStackTrace();
					return baseCollection;
				}
			}
		}
		return collection;
	}

	/**
	 * Reads armored public key ring collection from stream.
	 *
	 * @param in the input stream to read
	 * @return the PGP public key ring collection
	 */
	public static PGPPublicKeyRingCollection readArmoredPubKeyRingCollectionFromStream(final InputStream in) {
		try {
			while (in.available() > 0) {
				final ArmoredInputStream aIn = new ArmoredInputStream(in);
				try {
					return new BcPGPPublicKeyRingCollection(aIn);
				} catch (IOException | PGPException e) {
					e.printStackTrace(); // TODO: Fail better.
				}
			}
		} catch (final IOException e) {
			e.printStackTrace();
			return null;
		}
		return null;
	}

	/**
	 * Reads armored secret key ring collection from stream.
	 *
	 * @param in the input stream to read
	 * @return the PGP secret key ring collection
	 */
	public static PGPSecretKeyRingCollection readArmoredSecKeyRingCollectionFromStream(final InputStream in) {
		try {
			while (in.available() > 0) {
				final ArmoredInputStream aIn = new ArmoredInputStream(in);
				try {
					return new BcPGPSecretKeyRingCollection(aIn);
				} catch (IOException | PGPException e) {
					// ignored as Stream may contain other data
				}
			}
		} catch (final IOException e) {
			return null;
		}
		return null;
	}

	/**
	 * Writes armored public key ring collection to stream.
	 *
	 * @param collection the collection
	 * @param out the output stream to write to
	 * @return true, if successful
	 */
	public static boolean writeArmoredPubKeyRingCollectionToStream(final PGPPublicKeyRingCollection collection, OutputStream out) {
		try {
			out = new ArmoredOutputStream(out);
			collection.encode(out);
			out.close();
			return true;
		} catch (final IOException e) {
			return false;
		}
	}

	/**
	 * Write armored public secret key ring collection to stream.
	 *
	 * @param pCollection the public collection
	 * @param sCollection the secret collection
	 * @param out the output stream to write to
	 * @return true, if successful
	 */
	public static boolean writeArmoredPubSecKeyRingCollectionToStream(final PGPPublicKeyRingCollection pCollection, final PGPSecretKeyRingCollection sCollection, final OutputStream out) {
		try {
			ArmoredOutputStream aOut = new ArmoredOutputStream(out);
			pCollection.encode(aOut);
			aOut.close();
			aOut = new ArmoredOutputStream(out);
			sCollection.encode(aOut);
			aOut.close();
			return true;
		} catch (final IOException e) {
			return false;
		}
	}

	/**
	 * Write armored secret key ring collection to stream.
	 *
	 * @param collection the collection
	 * @param out the output stream to write to
	 * @return true, if successful
	 */
	public static boolean writeArmoredSecKeyRingCollectionToStream(final PGPSecretKeyRingCollection collection, OutputStream out) {
		try {
			out = new ArmoredOutputStream(out);
			collection.encode(out);
			out.close();
			return true;
		} catch (final IOException e) {
			return false;
		}
	}
}
