package framework.exception;

/**
 * Can be thrown be modules to indicate errors but is also used to wrap other uncatched exceptions from modules.
 *
 * @author Stefan Werner
 */
public class ModuleException extends AbstractException {

	private static final long serialVersionUID = 6113494309120092922L;

	/**
	 * Instantiates a new module exception.
	 */
	public ModuleException() {
		super();
	}

	/**
	 * Instantiates a new module exception.
	 *
	 * @param arg0 the arg0
	 */
	public ModuleException(final String arg0) {
		super(arg0);
	}

	/**
	 * Instantiates a new module exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 */
	public ModuleException(final String arg0, final Throwable arg1) {
		super(arg0, arg1);
	}

	/**
	 * Instantiates a new module exception.
	 *
	 * @param arg0 the arg0
	 * @param arg1 the arg1
	 * @param arg2 the arg2
	 * @param arg3 the arg3
	 */
	public ModuleException(final String arg0, final Throwable arg1, final boolean arg2, final boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	/**
	 * Instantiates a new module exception.
	 *
	 * @param arg0 the arg0
	 */
	public ModuleException(final Throwable arg0) {
		super(arg0);
	}
}
