package framework.model.event.type;

/**
 * Types of system states.
 *
 * @author Stefan Werner
 */
public enum SystemStateType {
	BROKER_RUNNING, BROKER_SHUTTING_DOWN, BROKER_STARTING_UP, BROKER_STOPPED_AND_READY, SYSTEM_EXITING, SYSTEM_INITIALIZING, SYSTEM_OR_BROKER_ERROR
}
