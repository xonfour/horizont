package framework.model.type;

/**
 * The module states as a bit mask.
 *
 * @author Stefan Werner
 */
public final class ModuleStateType {

	// main state types
	public static final int NOT_READY = 0;
	public static final int READY = 1;

	// additional state types
	public static final int WAITING = 2;
	public static final int READONLY = 4;
	public static final int ERROR = 8;
}
