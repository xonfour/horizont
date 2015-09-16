package module.iface;

import framework.model.Port;
import framework.model.event.ProviderStateEvent;

/**
 * Basic interface for all modules implementing a prosumer.
 * <p>
 * USAGE: You must not implement this interface. Instead extend one of the following abstract implementations and provide a constructor accepting the specified
 * objects that does the corresponding super() call:<br>
 * AbstractProsumer -> ProsumerConnector, ComponentConfigurationController, LogConnector<br>
 * AbstractProsumerProvider -> ProsumerConnector, ProviderConnector, ComponentConfigurationController, LogConnector
 * <p>
 * IMPORTANT: Methods in this class must be thread-safe.
 *
 * @author Stefan Werner
 */
public interface Prosumer extends Module {

	/**
	 * Informs about state events send by connected providers.
	 *
	 * @param port the port
	 * @param event the event
	 */
	public void onProviderStateEvent(Port port, ProviderStateEvent event);
}