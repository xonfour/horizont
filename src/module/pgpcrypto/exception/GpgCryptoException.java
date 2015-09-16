package module.pgpcrypto.exception;

/**
 * Exception thrown when there is a problem with the cryptographic engine or the key manager. Mostly indicates invalid data.
 *
 * @author Stefan Werner
 */
public class GpgCryptoException extends Exception {

	private static final long serialVersionUID = -8990487458527712226L;

	/**
	 * Instantiates a new gpg crypto exception.
	 */
	public GpgCryptoException() {
		super();
	}

	/**
	 * Instantiates a new gpg crypto exception.
	 *
	 * @param arg0 the arg0
	 */
	public GpgCryptoException(final String arg0) {
		super(arg0);
	}

	/**
	 * Instantiates a new gpg crypto exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 */
	public GpgCryptoException(final String arg0, final Throwable arg1) {
		super(arg0, arg1);
	}

	/**
	 * Instantiates a new gpg crypto exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 * @param arg2 the arg2
	 * @param arg3 the arg3
	 */
	public GpgCryptoException(final String arg0, final Throwable arg1, final boolean arg2, final boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Instantiates a new gpg crypto exception.
	 *
	 * @param arg0 the arg0
	 */
	public GpgCryptoException(final Throwable arg0) {
		super(arg0);
	}

}
