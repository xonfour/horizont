package framework.exception;

/**
 * Exception thrown when actions are attempted while the broker being in the wrong state.
 *
 * @author Stefan Werner
 */
public class WrongBrokerStateException extends BrokerException {

	private static final long serialVersionUID = -6288575313744982006L;

	/**
	 * Instantiates a new wrong broker state exception.
	 */
	public WrongBrokerStateException() {
		super();
	}

	/**
	 * Instantiates a new wrong broker state exception.
	 *
	 * @param arg0 the arg0
	 */
	public WrongBrokerStateException(final String arg0) {
		super(arg0);
	}
}
