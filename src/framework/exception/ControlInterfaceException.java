package framework.exception;

/**
 * Exception thrown when a control interface produces an error or uses invalid values.
 *
 * @author Stefan Werner
 */
public class ControlInterfaceException extends AbstractException {

	private static final long serialVersionUID = 7003570288794213596L;

	/**
	 * Instantiates a new control interface exception.
	 */
	public ControlInterfaceException() {
		super();
	}

	/**
	 * Instantiates a new control interface exception.
	 *
	 * @param arg0 the arg0
	 */
	public ControlInterfaceException(final String arg0) {
		super(arg0);
	}

	/**
	 * Instantiates a new control interface exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 */
	public ControlInterfaceException(final String arg0, final Throwable arg1) {
		super(arg0, arg1);
	}

	/**
	 * Instantiates a new control interface exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 * @param arg2 the arg2
	 * @param arg3 the arg3
	 */
	public ControlInterfaceException(final String arg0, final Throwable arg1, final boolean arg2, final boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Instantiates a new control interface exception.
	 *
	 * @param arg0 the arg0
	 */
	public ControlInterfaceException(final Throwable arg0) {
		super(arg0);
	}
}
