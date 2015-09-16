package module.iface;

import framework.model.Port;

/**
 * Listener to be called after data streams have been closed.
 * <p>
 * IMPORTANT: Methods in this interface are called synchronously and will therefor block.
 *
 * @author Stefan Werner
 */
public interface StreamListener {

	/**
	 * On input stream close.
	 *
	 * @param port the port
	 * @param path the path
	 */
	public void onInputStreamClose(Port port, String[] path);

	/**
	 * On output stream close.
	 *
	 * @param port the port
	 * @param path the path
	 */
	public void onOutputStreamClose(Port port, String[] path);
}
