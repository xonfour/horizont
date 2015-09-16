package framework.exception;

/**
 * Exception thrown when actions are attempted while control interface being in the wrong state.
 *
 * @author Stefan Werner
 */
public class WrongControlInterfaceStateException extends ControlInterfaceException {

	private static final long serialVersionUID = -6689149536515071418L;

	/**
	 * Instantiates a new wrong control interface state exception.
	 */
	public WrongControlInterfaceStateException() {
		super();
	}

	/**
	 * Instantiates a new wrong control interface state exception.
	 *
	 * @param arg0 the arg0
	 */
	public WrongControlInterfaceStateException(final String arg0) {
		super(arg0);
	}
}
