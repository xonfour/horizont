package framework.exception;

/**
 * Exception thrown when actions are attempted while module being in the wrong state.
 *
 * @author Stefan Werner
 */
public class WrongModuleStateException extends BrokerException {

	private static final long serialVersionUID = -4684071636610405881L;

	/**
	 * Instantiates a new wrong module state exception.
	 */
	public WrongModuleStateException() {
		super();
	}

	/**
	 * Instantiates a new wrong module state exception.
	 *
	 * @param arg0 the arg0
	 */
	public WrongModuleStateException(final String arg0) {
		super(arg0);
	}
}
