package framework.exception;

/**
 * Exception thrown when there is a database error.
 *
 * @author Stefan Werner
 */
public class DatabaseException extends AbstractException {

	private static final long serialVersionUID = 5747510957161638535L;

	/**
	 * Instantiates a new database exception.
	 */
	public DatabaseException() {
		super();
	}

	/**
	 * Instantiates a new database exception.
	 *
	 * @param arg0 the arg0
	 */
	public DatabaseException(final String arg0) {
		super(arg0);
	}

	/**
	 * Instantiates a new database exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 */
	public DatabaseException(final String arg0, final Throwable arg1) {
		super(arg0, arg1);
	}

	/**
	 * Instantiates a new database exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 * @param arg2 the arg2
	 * @param arg3 the arg3
	 */
	public DatabaseException(final String arg0, final Throwable arg1, final boolean arg2, final boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Instantiates a new database exception.
	 *
	 * @param arg0 the arg0
	 */
	public DatabaseException(final Throwable arg0) {
		super(arg0);
	}
}
