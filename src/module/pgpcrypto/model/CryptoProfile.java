package module.pgpcrypto.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Object to store available/acceptable crypto profiles (including schemes, key sizes etc.).
 * <p>
 * TODO: Unused, use it!
 *
 * @author Stefan Werner
 */
public class CryptoProfile {

	/**
	 * String encoded encrypted own private key
	 */
	private Long secretKeyId;

	/**
	 * List of String encoded private keys of others to share files with
	 */
	private List<Long> sharingPublicKeyIdList = new ArrayList<Long>();

	/**
	 * Gets the available encryption schemes.
	 *
	 * @return the available encryption schemes
	 */
	/* (non-Javadoc)
	 *
	 * @see model.CryptoSubProfile#getAvailableEncryptionSchemes() */
	public List<String> getAvailableEncryptionSchemes() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Gets the current encryption scheme.
	 *
	 * @return the current encryption scheme
	 */
	/* (non-Javadoc)
	 *
	 * @see model.CryptoSubProfile#getCurrentEncryptionScheme() */
	public int getCurrentEncryptionScheme() {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * Gets the string encoded encrypted own private key.
	 *
	 * @return the string encoded encrypted own private key
	 */
	public Long getSecretKeyId() {
		return this.secretKeyId;
	}

	/**
	 * Gets the list of String encoded private keys of others to share files with.
	 *
	 * @return the list of String encoded private keys of others to share files with
	 */
	public List<Long> getSharingPublicKeyIdList() {
		return this.sharingPublicKeyIdList;
	}

	/**
	 * Sets the current encryption scheme.
	 */
	/* (non-Javadoc)
	 *
	 * @see model.CryptoSubProfile#setCurrentEncryptionScheme() */
	public void setCurrentEncryptionScheme() {
		// TODO Auto-generated method stub

	}

	/**
	 * Sets the string encoded encrypted own private key.
	 *
	 * @param secretKeyId the string encoded encrypted own private key
	 */
	public void setSecretKeyId(final long secretKeyId) {
		this.secretKeyId = secretKeyId;
	}

	/**
	 * Sets the list of String encoded private keys of others to share files with.
	 *
	 * @param sharingPublicKeyIdList the list of String encoded private keys of others to share files with
	 */
	public void setSharingPublicKeyIdList(final List<Long> sharingPublicKeyIdList) {
		this.sharingPublicKeyIdList = sharingPublicKeyIdList;
	}

}
