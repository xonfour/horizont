package framework.exception;

/**
 * Exception thrown when actions are attempted while the system being in the wrong state.
 *
 * @author Stefan Werner
 */
public class WrongSystemStateException extends ControlInterfaceException {

	private static final long serialVersionUID = -7893398809814367009L;

	/**
	 * Instantiates a new wrong system state exception.
	 */
	public WrongSystemStateException() {
		super();
	}

	/**
	 * Instantiates a new wrong system state exception.
	 *
	 * @param arg0 the arg0
	 */
	public WrongSystemStateException(final String arg0) {
		super(arg0);
	}
}
