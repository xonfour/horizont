package framework.exception;

/**
 * Basis for all other Exceptions of the System.
 *
 * @author Stefan Werner
 */
public abstract class AbstractException extends Exception {

	private static final long serialVersionUID = -18601884235844976L;

	/**
	 * Instantiates a new abstract secure cloud exception.
	 */
	public AbstractException() {
		super();
	}

	/**
	 * Instantiates a new abstract secure cloud exception.
	 *
	 * @param arg0 the message
	 */
	public AbstractException(final String arg0) {
		super(arg0);
	}

	/**
	 * Instantiates a new abstract secure cloud exception.
	 *
	 * @param arg0 the message
	 * @param arg1 the throwable
	 */
	public AbstractException(final String arg0, final Throwable arg1) {
		super(arg0, arg1);
	}

	/**
	 * Instantiates a new abstract secure cloud exception.
	 *
	 * @param arg0 the message
	 * @param arg1 the throwable
	 * @param arg2 the set to true to enable suppression
	 * @param arg3 the true for writable stack trace
	 */
	public AbstractException(final String arg0, final Throwable arg1, final boolean arg2, final boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Instantiates a new abstract secure cloud exception.
	 *
	 * @param arg0 the throwable
	 */
	public AbstractException(final Throwable arg0) {
		super(arg0);
	}
}
