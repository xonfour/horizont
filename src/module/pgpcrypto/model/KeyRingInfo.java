package module.pgpcrypto.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper object to exchange information on key rings.
 *
 * @author Stefan Werner
 */
public class KeyRingInfo {

	/**
	 * The type of a key.
	 */
	public static enum KEY_TYPE {
		ENC, SIGN, UNKOWN
	};

	private boolean isUsable = false;
	private KEY_TYPE keyType;
	private long masterKeyId = 0;
	private String masterUserId = "unset";
	private String[] relatingPath = null;
	private List<KeyRingInfo> subKeyInfoList = new ArrayList<KeyRingInfo>();

	/**
	 * Instantiates a new key ring info.
	 */
	public KeyRingInfo() {
	}

	/**
	 * Instantiates a new key ring info.
	 *
	 * @param masterKeyId the master key ID
	 * @param masterUserId the master user ID
	 */
	public KeyRingInfo(final long masterKeyId, final String masterUserId) {
		this(masterKeyId, masterUserId, KEY_TYPE.UNKOWN);
	}

	/**
	 * Instantiates a new key ring info.
	 *
	 * @param masterKeyId the master key ID
	 * @param masterUserId the master user ID
	 * @param keyType the key type
	 */
	public KeyRingInfo(final long masterKeyId, final String masterUserId, final KEY_TYPE keyType) {
		this.masterKeyId = masterKeyId;
		this.masterUserId = masterUserId;
		this.keyType = keyType;
	}

	/**
	 * Adds the sub key info.
	 *
	 * @param subKeyInfo the sub key info
	 */
	public void addSubKeyInfo(final KeyRingInfo subKeyInfo) {
		this.subKeyInfoList.add(subKeyInfo);
	}

	/**
	 * Gets the key ID.
	 *
	 * @return the key ID
	 */
	public long getKeyId() {
		return getMasterKeyId();
	}

	/**
	 * Gets the key type.
	 *
	 * @return the key type
	 */
	public KEY_TYPE getKeyType() {
		return this.keyType;
	}

	/**
	 * Gets the master key ID.
	 *
	 * @return the master key ID
	 */
	public long getMasterKeyId() {
		return this.masterKeyId;
	}

	/**
	 * Gets the master user ID.
	 *
	 * @return the master user ID
	 */
	public String getMasterUserId() {
		return this.masterUserId;
	}

	/**
	 * Gets the relating path (used for sharing).
	 *
	 * @return the relating path
	 */
	public String[] getRelatingPath() {
		return this.relatingPath;
	}

	/**
	 * Gets the sub key info list.
	 *
	 * @return the sub key info list
	 */
	public List<KeyRingInfo> getSubKeyInfoList() {
		return this.subKeyInfoList;
	}

	/**
	 * Gets the user ID.
	 *
	 * @return the user ID
	 */
	public String getUserId() {
		return getMasterUserId();
	}

	/**
	 * Checks if is usable.
	 *
	 * @return true, if is usable
	 */
	public boolean isUsable() {
		return this.isUsable;
	}

	/**
	 * Sets the key ID.
	 *
	 * @param keyId the new key ID
	 */
	public void setKeyId(final long keyId) {
		setMasterKeyId(keyId);
	}

	/**
	 * Sets the key type.
	 *
	 * @param keyType the new key type
	 */
	public void setKeyType(final KEY_TYPE keyType) {
		this.keyType = keyType;
	}

	/**
	 * Sets the master key ID.
	 *
	 * @param masterKeyId the new master key ID
	 */
	public void setMasterKeyId(final long masterKeyId) {
		this.masterKeyId = masterKeyId;
	}

	/**
	 * Sets the master user ID.
	 *
	 * @param masterUserId the new master user ID
	 */
	public void setMasterUserId(final String masterUserId) {
		this.masterUserId = masterUserId;
	}

	/**
	 * Sets the relating path (used for sharing).
	 *
	 * @param relatingPath the new relating path
	 */
	public void setRelatingPath(final String[] relatingPath) {
		this.relatingPath = relatingPath;
	}

	/**
	 * Sets the sub key info list.
	 *
	 * @param subKeyInfoList the new sub key info list
	 */
	public void setSubKeyInfoList(final List<KeyRingInfo> subKeyInfoList) {
		this.subKeyInfoList = subKeyInfoList;
	}

	/**
	 * Sets the usable.
	 *
	 * @param isUsable the new usable
	 */
	public void setUsable(final boolean isUsable) {
		this.isUsable = isUsable;
	}

	/**
	 * Sets the user ID.
	 *
	 * @param userId the new user ID
	 */
	public void setUserId(final String userId) {
		setMasterUserId(userId);
	}

	@Override
	public String toString() {
		if (this.subKeyInfoList != null) {
			return Long.toHexString(this.masterKeyId).toUpperCase() + " / " + this.masterUserId + " + " + this.subKeyInfoList.size() + " subkey(s)";
		} else {
			return Long.toHexString(this.masterKeyId).toUpperCase() + " / " + this.masterUserId;
		}
	}
}