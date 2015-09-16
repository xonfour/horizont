package framework.constants;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Contains available rights for control interfaces as bit masks.
 *
 * @author Stefan Werner
 */
public final class ControlInterfaceRight {

	public static Map<Integer, String> availableControlInterfaceRights = ImmutableMap.<Integer, String> builder().put(ControlInterfaceRight.CAN_MISS_EVENTS, "Can miss events (recommended for all but logging CIs)").put(ControlInterfaceRight.CONTROL_STATE, "Control state of the system (start/stop/exit)").put(ControlInterfaceRight.MANAGE_CIS, "Manage other control interfaces").put(ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS, "Manage modules and connections between them").put(ControlInterfaceRight.RCV_CONN_UPDATE, "Receive connection updates").put(ControlInterfaceRight.RCV_LOG_EVENT, "Receive logging messages").put(ControlInterfaceRight.RCV_MOD_ACT, "Receive info about all module activities").put(ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE, "Receive module updates").put(ControlInterfaceRight.READ_MODULES_AND_CONNECTIONS, "Read module and connection configuration").put(ControlInterfaceRight.MANAGE_DATABASE, "Manage database directly, import/export data").put(ControlInterfaceRight.DIRECT_STORAGE_ACCESS, "Access (read/write) directly to system storage location").build();
	public static final int RIGHT___ALL = Integer.MAX_VALUE;
	public static final int RIGHT___NON = 0;
	public static final int CAN_MISS_EVENTS = 1;
	public static final int CONTROL_STATE = 2;
	public static final int MANAGE_CIS = 4;
	public static final int MANAGE_MODULES_AND_CONNECTIONS = 8;
	public static final int RCV_CONN_UPDATE = 16;
	public static final int RCV_LOG_EVENT = 32;
	public static final int RCV_MOD_ACT = 64;
	public static final int RCV_MOD_AND_PORT_UPDATE = 128;
	public static final int READ_MODULES_AND_CONNECTIONS = 256;
	public static final int MANAGE_DATABASE = 512;
	public static final int DIRECT_STORAGE_ACCESS = 1024;
}
