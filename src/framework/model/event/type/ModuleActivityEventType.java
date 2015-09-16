package framework.model.event.type;

/**
 * Types of module activity events.
 * <p>
 * TODO: Change to Enum?
 *
 * @author Stefan Werner
 */
public class ModuleActivityEventType {

	public static final String MOD_ACT___CHECK_AND_LOCK = "check_and_lock";
	public static final String MOD_ACT___CREATE_FOLDER = "create_folder";
	public static final String MOD_ACT___DELETE = "delete";
	public static final String MOD_ACT___ELEMENT_EVENT = "element_event";
	public static final String MOD_ACT___GET_CHILD_FSELEMENTS = "get_child_elements";
	public static final String MOD_ACT___GET_CONN_PROVIDER_STATUS = "get_connected_provider_status";
	public static final String MOD_ACT___GET_ELEMENT = "get_element";
	public static final String MOD_ACT___GET_SUBSCRIPTION = "get_eubscription";
	public static final String MOD_ACT___GET_SUBSCRIPTIONS = "get_subscriptions";
	public static final String MOD_ACT___GET_SUPPORTED_COMMANDS = "get_supported_commands";
	public static final String MOD_ACT___GET_TYPE = "get_type";
	public static final String MOD_ACT___IS_CONNECTED = "is_connected";
	public static final String MOD_ACT___IS_RUNNING = "is_running";
	public static final String MOD_ACT___IS_SUBSCRIBED = "is_subscribed";
	public static final String MOD_ACT___MOVE = "move";
	public static final String MOD_ACT___READ_DATA = "read_data";
	public static final String MOD_ACT___REGISTER_PROSUMER_PORT = "register_prosumer_port";
	public static final String MOD_ACT___REGISTER_PROVIDER_PORT = "register_provider_port";
	public static final String MOD_ACT___REMOVE_ALL_SUBSCRIPTIONS = "remove_all_subscriptions";
	public static final String MOD_ACT___STATE_CHANGE = "state_change";
	public static final String MOD_ACT___SEND_COMMAND = "send_command";
	public static final String MOD_ACT___ADD_STREAM_LISTENER = "add_stream_listener";
	public static final String MOD_ACT___REMOVE_STREAM_LISTENER = "remove_stream_listener";
	public static final String MOD_ACT___REMOVE_ALL_STREAM_LISTENER = "remove_all_stream_listeners";
	public static final String MOD_ACT___SUBSCRIBE = "subscribe";
	public static final String MOD_ACT___UNLOCK = "unlock";
	public static final String MOD_ACT___UNREGISTER_PROSUMER_PORT = "unregister_prosumer_port";
	public static final String MOD_ACT___UNREGISTER_PROVIDER_PORT = "unregister_provider_port";
	public static final String MOD_ACT___UNSUBSCRIBE = "unsubscribe";
	public static final String MOD_ACT___WRITE_DATA = "writeData";
	public static final String MOD_ACT___INPUTSTREAM_CLOSED = "input_stream_closed";
	public static final String MOD_ACT___OUTPUTSTREAM_CLOSED = "output_stream_closed";

	// type: String
	public static final String MOD_ACT_PROPKEY___DEST_MODULEID = "destModuleId";

	// type: PortType
	public static final String MOD_ACT_PROPKEY___DEST_PORT_TYPE = "destPortType";

	// type: String
	public static final String MOD_ACT_PROPKEY___DEST_PORTID = "destPortId";

	// type: DateElementEventType
	public static final String MOD_ACT_PROPKEY___ELEMENT_EVENT_TYPE = "elemEventType";

	// type: PortType
	public static final String MOD_ACT_PROPKEY___PORT_TYPE = "portType";

	// type: String
	public static final String MOD_ACT_PROPKEY___PORTID = "portId";

	// type: String[]
	public static final String MOD_ACT_PROPKEY___PATH = "path";

	// type: Map<String, String>
	public static final String MOD_ACT_PROPKEY___PROPERTIES = "props";

	// type: String[]
	public static final String MOD_ACT_PROPKEY___DEST_PATH = "destPath";

	// type: Boolean
	public static final String MOD_ACT_PROPKEY___RECURSIVE = "recursive";

	// type: String
	public static final String MOD_ACT_PROPKEY___COMMAND = "command";

	// type: Integer
	public static final String MOD_ACT_PROPKEY___STATE = "state";
}
