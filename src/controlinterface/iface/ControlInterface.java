package controlinterface.iface;

/**
 * The interface ControlInterface.
 * <p>
 * IMPORTANT: You must not implement this interface. Instead see {@link AbstractControlInterface}.
 *
 * @author Stefan Werner
 */
public interface ControlInterface {

	/**
	 * Shutdown control interface.
	 */
	public void shutdown();

	/**
	 * Start up control interface.
	 */
	public void startup();
}