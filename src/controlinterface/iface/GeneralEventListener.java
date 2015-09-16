package controlinterface.iface;

import framework.model.event.GeneralEvent;

/**
 * Listener for receiving any events from the system. See
 * {@link framework.control.ControlInterfaceConnector#addGeneralEventListener(GeneralEventListener, framework.model.event.type.GeneralEventType...)} for more
 * details.
 *
 * @author Stefan Werner
 */
public interface GeneralEventListener {

	/**
	 * Called on general event.
	 *
	 * @param event the event
	 */
	public void onGeneralEvent(GeneralEvent event);
}
