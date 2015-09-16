package framework.constants;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Contains available rights for modules as bit masks.
 *
 * @author Stefan Werner
 */
public class ModuleRight {

	public static Map<Integer, String> availableModuleRights = ImmutableMap.<Integer, String> builder().put(ModuleRight.OBSERVE_STREAMS, "Observe input and output streams").put(ModuleRight.READ_DATA, "Read data from connected provider modules").put(ModuleRight.RECEIVE_EVENTS, "Receive events from connected provider modules").put(ModuleRight.SEND_COMMAND, "Send commands to connected modules").put(ModuleRight.WRITE_DATA, "Write data to connected provider modules").put(ModuleRight.READ_DB, "Read from the internal database").put(ModuleRight.WRITE_DB, "Write to the internal database").build();
	public static final int RIGHT___ALL = Integer.MAX_VALUE;
	public static final int RIGHT___NON = 0;
	public static final int OBSERVE_STREAMS = 1;
	public static final int READ_DATA = 2;
	public static final int RECEIVE_EVENTS = 4;
	public static final int SEND_COMMAND = 8;
	public static final int WRITE_DATA = 16;
	public static final int READ_DB = 32; // TODO: Currently unused, use it in the database.
	public static final int WRITE_DB = 64; // TODO: Currently unused, use it in the database.
}
