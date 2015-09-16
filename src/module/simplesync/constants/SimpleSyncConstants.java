package module.simplesync.constants;

import framework.constants.GenericControlInterfaceCommands;

/**
 * Constants for the simple sync module.
 *
 * @author Stefan Werner
 */
public class SimpleSyncConstants {

	// treat an element as if it was modified
	// TODO: Currently unused. Use it.
	public static final String COMMAND___FORCE_TRANSFER = "force_transfer";
	public static final String RESULT___FAIL_REASON = "fail_reason";
	public static final String RESULT___FAIL_REASON___ELEMENT_NOT_FOUND = "not_found";
	public static final String RESULT___FAIL_REASON___NOT_A_FILE = "not_a_file";
	public static final String RESULT___FAIL_REASON___READ_ERROR = "read_error";
	public static final String[] SUPPORTED_CI_COMMANDS = { GenericControlInterfaceCommands.SHOW_UI };
	public static final String[] SUPPORTED_MODULE_COMMANDS_FILES = { SimpleSyncConstants.COMMAND___FORCE_TRANSFER };
}
