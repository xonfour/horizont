package framework.exception;

/**
 * Exception thrown when actions are attempted by components (control interfaces or modules) without proper rights.
 *
 * @author Stefan Werner
 */
public class AuthorizationException extends AbstractException {

	private static final long serialVersionUID = -1532927182946518671L;

	/**
	 * Instantiates a new authorization exception.
	 */
	public AuthorizationException() {
		super();
	}

	/**
	 * Instantiates a new authorization exception.
	 *
	 * @param arg0 the arg0
	 */
	public AuthorizationException(final String arg0) {
		super(arg0);
	}

	/**
	 * Instantiates a new authorization exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 */
	public AuthorizationException(final String arg0, final Throwable arg1) {
		super(arg0, arg1);
	}

	/**
	 * Instantiates a new authorization exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 * @param arg2 the arg2
	 * @param arg3 the arg3
	 */
	public AuthorizationException(final String arg0, final Throwable arg1, final boolean arg2, final boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Instantiates a new authorization exception.
	 *
	 * @param arg0 the arg0
	 */
	public AuthorizationException(final Throwable arg0) {
		super(arg0);
	}
}
