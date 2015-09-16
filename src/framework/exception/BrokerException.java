package framework.exception;

/**
 * Exception thrown when broker errors occur.
 *
 * @author Stefan Werner
 */
public class BrokerException extends AbstractException {

	private static final long serialVersionUID = 8582259532018762578L;

	/**
	 * Instantiates a new broker exception.
	 */
	public BrokerException() {
		super();
	}

	/**
	 * Instantiates a new broker exception.
	 *
	 * @param arg0 the arg0
	 */
	public BrokerException(final String arg0) {
		super(arg0);
	}

	/**
	 * Instantiates a new broker exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 */
	public BrokerException(final String arg0, final Throwable arg1) {
		super(arg0, arg1);
	}

	/**
	 * Instantiates a new broker exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 * @param arg2 the arg2
	 * @param arg3 the arg3
	 */
	public BrokerException(final String arg0, final Throwable arg1, final boolean arg2, final boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Instantiates a new broker exception.
	 *
	 * @param arg0 the arg0
	 */
	public BrokerException(final Throwable arg0) {
		super(arg0);
	}
}
