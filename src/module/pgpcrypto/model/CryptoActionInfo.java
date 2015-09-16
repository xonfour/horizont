package module.pgpcrypto.model;

/**
 * Wrapper object to exchange information on encryption. Currently only used to return the name of an object and the (authenticated) author (key ID).
 *
 * @author Stefan Werner
 */
public class CryptoActionInfo {

	private long authorId = 0;
	private String authorName = null;
	private String objectName = null;

	/**
	 * Gets the author ID.
	 *
	 * @return the author id
	 */
	public long getAuthorId() {
		return this.authorId;
	}

	/**
	 * Gets the author name.
	 *
	 * @return the author name
	 */
	public String getAuthorName() {
		return this.authorName;
	}

	/**
	 * Gets the object name.
	 *
	 * @return the object name
	 */
	public String getObjectName() {
		return this.objectName;
	}

	/**
	 * Sets the author ID.
	 *
	 * @param authorId the new author ID
	 */
	public void setAuthorId(final long authorId) {
		this.authorId = authorId;
	}

	/**
	 * Sets the author name.
	 *
	 * @param authorName the new author name
	 */
	public void setAuthorName(final String authorName) {
		this.authorName = authorName;
	}

	/**
	 * Sets the object name.
	 *
	 * @param objectName the new object name
	 */
	public void setObjectName(final String objectName) {
		this.objectName = objectName;
	}
}
