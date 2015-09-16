package module.iface;

import framework.model.ProsumerPort;
import framework.model.event.DataElementEvent;

/**
 * Prosumer listener to be called on element change events (add, delete, modify for now).
 *
 * @author Stefan Werner
 */
public interface DataElementEventListener {

	/**
	 * Receives element events.
	 *
	 * @param port the port
	 * @param event the event
	 */
	public void onElementEvent(ProsumerPort port, DataElementEvent event);
}
