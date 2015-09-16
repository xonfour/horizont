package framework.exception;

/**
 * Data container exception.
 *
 * @author Stefan Werner
 */
public class DataContainerException extends AbstractException {

	private static final long serialVersionUID = 6522569290344786291L;

	/**
	 * Instantiates a new data container exception.
	 */
	public DataContainerException() {
		super();
	}

	/**
	 * Instantiates a new data container exception.
	 *
	 * @param arg0 the arg0
	 */
	public DataContainerException(final String arg0) {
		super(arg0);
	}

	/**
	 * Instantiates a new data container exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 */
	public DataContainerException(final String arg0, final Throwable arg1) {
		super(arg0, arg1);
	}

	/**
	 * Instantiates a new data container exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 * @param arg2 the arg2
	 * @param arg3 the arg3
	 */
	public DataContainerException(final String arg0, final Throwable arg1, final boolean arg2, final boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Instantiates a new data container exception.
	 *
	 * @param arg0 the arg0
	 */
	public DataContainerException(final Throwable arg0) {
		super(arg0);
	}
}
