package module.pgpcrypto.control;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Iterator;

import javax.crypto.NoSuchPaddingException;

import module.pgpcrypto.exception.GpgCryptoException;
import module.pgpcrypto.model.CryptoActionInfo;

import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyRing;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.util.io.Streams;

/**
 * Provides static methods for encryption and decryption of byte arrays and streams.
 */
class PGPCryptoUtils {

	private static final SecureRandom RANDOM = new SecureRandom();

	/**
	 * Creates an array containing random data of given size.
	 *
	 * @param size the size
	 * @return the byte[]
	 */
	public static byte[] createRandomArray(final int size) {
		final byte[] randomByteArray = new byte[size];
		PGPCryptoUtils.RANDOM.nextBytes(randomByteArray);
		return randomByteArray;
	}

	/**
	 * Decrypts and verifies a byte array.
	 *
	 * @param in the input stream to read from
	 * @param pubKeyCollection the pub key collection
	 * @param signPubKey the signature public key
	 * @param encPrivatKey the encryption privat key
	 * @param info the object to store info (like key ID)
	 * @return the decrypted byte array
	 * @throws IOException if an I/O exception has occurred
	 * @throws GpgCryptoException if an internal problem occured or if the encryption is invalid
	 * @throws PGPException if a problem within the PGP subsystem occurred
	 */
	public static byte[] decryptAndVerifyByteArray(final byte[] in, final PGPPublicKeyRingCollection pubKeyCollection, final PGPPublicKey signPubKey, final PGPPrivateKey encPrivatKey, final CryptoActionInfo info) throws IOException, GpgCryptoException, PGPException {
		final InputStream inStream = PGPUtil.getDecoderStream(new ByteArrayInputStream(in));

		final PGPObjectFactory pgpF = new BcPGPObjectFactory(inStream);
		PGPEncryptedDataList enc;

		final Object o = pgpF.nextObject();
		if (o instanceof PGPEncryptedDataList) {
			enc = (PGPEncryptedDataList) o;
		} else {
			enc = (PGPEncryptedDataList) pgpF.nextObject();
		}

		final Iterator<?> it = enc.getEncryptedDataObjects();
		PGPPublicKeyEncryptedData pbe = null;

		boolean ownKeyIdFound = false;
		while (it.hasNext()) {
			pbe = (PGPPublicKeyEncryptedData) it.next();
			if (pbe.getKeyID() == encPrivatKey.getKeyID()) {
				ownKeyIdFound = true;
				break;
			}
		}

		if (!ownKeyIdFound) {
			throw new GpgCryptoException("own key not found in header");
		}

		final BcPublicKeyDataDecryptorFactory decryptorFactory = new BcPublicKeyDataDecryptorFactory(encPrivatKey);

		if (pbe.getSymmetricAlgorithm(decryptorFactory) != PGPCryptoModule.SYM_ENCRYPTION_ALGO) {
			throw new GpgCryptoException("symmetric algorithm is unsupported");
		}

		final InputStream clear = pbe.getDataStream(decryptorFactory);

		final PGPObjectFactory plainFact = new BcPGPObjectFactory(clear);

		Object message = plainFact.nextObject();

		PGPOnePassSignatureList onePassSignatureList = null;
		PGPSignatureList signatureList = null;

		final ByteArrayOutputStream literalOutputStream = new ByteArrayOutputStream();

		while (message != null) {
			if (message instanceof PGPLiteralData) {
				Streams.pipeAll(((PGPLiteralData) message).getInputStream(), literalOutputStream);
			} else if (message instanceof PGPOnePassSignatureList) {
				onePassSignatureList = (PGPOnePassSignatureList) message;
			} else if (message instanceof PGPSignatureList) {
				signatureList = (PGPSignatureList) message;
			} else {
				throw new GpgCryptoException("decrypt header: message type unknown");
			}
			message = plainFact.nextObject();
		}

		literalOutputStream.close();

		final byte[] literalOutput = literalOutputStream.toByteArray();

		if ((onePassSignatureList == null) || (signatureList == null)) {
			throw new GpgCryptoException("verify header: no signatures found/data corrupt");
		} else {
			for (int i = 0; i < onePassSignatureList.size(); i++) {
				final PGPOnePassSignature opSignature = onePassSignatureList.get(i);
				PGPPublicKey publicKey = pubKeyCollection.getPublicKey(opSignature.getKeyID());
				if ((publicKey == null) && (opSignature.getKeyID() == signPubKey.getKeyID())) {
					publicKey = signPubKey;
				}
				if (publicKey != null) {
					opSignature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);
					opSignature.update(literalOutput);
					final PGPSignature signature = signatureList.get(i);
					if (opSignature.verify(signature)) {
						info.setAuthorId(opSignature.getKeyID());
						final Iterator<?> userIds = publicKey.getUserIDs();
						while (userIds.hasNext()) {
							info.setAuthorName((String) userIds.next());
						}
					} else {
						throw new GpgCryptoException("Signature verification failed/data corrupt");
					}
				} else {
					throw new GpgCryptoException("Cannot verify signature, puplic key missing");
				}
			}
		}

		if (!pbe.verify()) {
			throw new GpgCryptoException("message failed integrity check");
		}
		return literalOutput;
	}

	/**
	 * Encrypts and signs a byte array.
	 *
	 * @param in the decrypted byte array
	 * @param pubKeyCollection the public key collection
	 * @param ownPubKey the own public key
	 * @param signPrivatKey the signature private key
	 * @return the encrypted byte array
	 * @throws IOException if an I/O exception has occurred
	 * @throws PGPException if a problem within the PGP subsystem occurred
	 */
	public static byte[] encryptAndSignByteArray(final byte[] in, final PGPPublicKeyRingCollection pubKeyCollection, final PGPPublicKey ownPubKey, final PGPPrivateKey signPrivatKey) throws IOException, PGPException {

		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(new BcPGPDataEncryptorBuilder(PGPCryptoModule.SYM_ENCRYPTION_ALGO).setWithIntegrityPacket(true).setSecureRandom(PGPCryptoUtils.RANDOM));

		encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(ownPubKey));
		final Iterator<?> itRings = pubKeyCollection.getKeyRings();
		while (itRings.hasNext()) {
			final PGPPublicKeyRing keyRing = (PGPPublicKeyRing) itRings.next();
			final Iterator<?> itKeys = keyRing.getPublicKeys();
			// ignore master signing key
			itKeys.next();
			while (itKeys.hasNext()) {
				final PGPPublicKey pubKey = (PGPPublicKey) itKeys.next();
				if (!pubKey.isMasterKey() && pubKey.isEncryptionKey()) {
					encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(pubKey));
				}
			}
		}

		final OutputStream cOut = encGen.open(out, new byte[in.length]);
		final PGPSignatureGenerator sGen = new PGPSignatureGenerator(new BcPGPContentSignerBuilder(signPrivatKey.getPublicKeyPacket().getAlgorithm(), HashAlgorithmTags.SHA256));
		sGen.init(PGPSignature.BINARY_DOCUMENT, signPrivatKey);
		final PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
		sGen.setHashedSubpackets(spGen.generate());
		sGen.generateOnePassVersion(false).encode(cOut);

		final PGPLiteralDataGenerator lGen = new PGPLiteralDataGenerator();
		final OutputStream lOut = lGen.open(cOut, PGPLiteralData.BINARY, "header", new Date(), new byte[in.length]);

		lOut.write(in);
		sGen.update(in);
		lOut.close();
		sGen.generate().encode(cOut);
		cOut.close();
		return out.toByteArray();
	}

	/**
	 * Gets an decrypted input stream.
	 *
	 * @param encryptedIn the encrypted input stream to read from
	 * @param keyBytes the key bytes
	 * @param ivBytes the iv bytes
	 * @param macSize the MAC size
	 * @return the decrypted input stream
	 * @throws IOException if an I/O exception has occurred
	 */
	public static InputStream getDecryptedInputStream(final InputStream encryptedIn, final byte[] keyBytes, final byte[] ivBytes, final int macSize) throws IOException {
		final AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
		cipher.init(false, new AEADParameters(new KeyParameter(keyBytes), macSize, ivBytes));
		return new CipherInputStream(encryptedIn, cipher);
	}

	/**
	 * Gets a key ring from an input stream.
	 *
	 * @param keyBlockStream the input stream to read key ring from
	 * @return the keyring
	 * @throws IOException if an I/O exception has occurred
	 */
	public static PGPKeyRing getKeyring(final InputStream keyBlockStream) throws IOException {
		final PGPObjectFactory factory = new BcPGPObjectFactory(PGPUtil.getDecoderStream(keyBlockStream));
		final Object o = factory.nextObject();
		if (o instanceof PGPPublicKeyRing) {
			final PGPPublicKeyRing p = (PGPPublicKeyRing) o;
			return p;
		} else if (o instanceof PGPSecretKeyRing) {
			final PGPSecretKeyRing p = (PGPSecretKeyRing) o;
			return p;
		} else {
			factory.nextObject();
		}
		throw new IllegalArgumentException("Input text does not contain a PGP Public Key");
	}

	/**
	 * Gets an output stream to write decrpted data to. This data is encrypted.
	 *
	 * @param encryptedOut the output stream encrypted data is written to.
	 * @param keyBytes the key bytes
	 * @param ivBytes the iv bytes
	 * @param macSize the MAC size
	 * @return the output stream to write to
	 * @throws IOException if an I/O exception has occurred
	 * @throws InvalidKeyException if the key is invalid
	 * @throws InvalidAlgorithmParameterException the a parameter is invalid
	 * @throws NoSuchAlgorithmException if the selected algorithm is not available
	 * @throws NoSuchProviderException if the selected provider is not available
	 * @throws NoSuchPaddingException if the selected padding is not available
	 */
	public static OutputStream getOutputStreamToEncryptTo(final OutputStream encryptedOut, final byte[] keyBytes, final byte[] ivBytes, final int macSize) throws IOException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException {
		final AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
		cipher.init(true, new AEADParameters(new KeyParameter(keyBytes), macSize, ivBytes));
		return new CipherOutputStream(encryptedOut, cipher);
	}
}
