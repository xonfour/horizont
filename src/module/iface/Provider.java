package module.iface;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.ProviderPort;
import framework.model.type.DataElementType;

/**
 * Basic interface for all modules implementing a provider.
 * <p>
 * USAGE: You must not implement this interface. Instead extend one of the following abstract implementations and provide a constructor accepting the specified
 * objects that does the corresponding super() call:<br>
 * AbstractProsumerProvider -> ProsumerConnector, ProviderConnector, ComponentConfigurationController, LogConnector<br>
 * AbstractProvider -> ProviderConnector, ComponentConfigurationController, LogConnector
 * <p>
 * IMPORTANT: Methods in this class must be thread-safe.
 * <p>
 * TODO:<br>
 * - Currently some methods may return null values on error. This should be avoided. Exceptions should be used instead.<br>
 * - Only use int values from ErrorCodes as return values, not this positive/negative number mix.<br>
 * - Additional methods: isReadwrite(), isReadOnly(), getMimeType(), canMonitor().
 *
 * @author Stefan Werner
 */
public interface Provider extends Module {

	public static final int RESULT_CODE___ERROR_ALREADY_EXISTENT = 3;
	public static final int RESULT_CODE___ERROR_ALREADY_LOCKED_BY_OTHERS = 4;
	public static final int RESULT_CODE___ERROR_GENERAL = 1;
	public static final int RESULT_CODE___ERROR_NO_SUCH_FILE = 2;
	public static final int RESULT_CODE___INVALID_NOT_SUPPORTED = -1;
	public static final int RESULT_CODE___INVALID_READONLY = -1;
	public static final int RESULT_CODE___OK = 0;
	public static final int STATUS_CODE___AVAILABLE = 0;
	public static final int STATUS_CODE___UNAVAILABLE = 1;

	/**
	 * Checks if an element is already locked and locks it if possible. How this is done is up to the implementation. Should be reentrant (return OK if already
	 * locked by this instance).
	 *
	 * @param port the port
	 * @param path the path
	 * @return 0 if successfully locked, ErrorCodes.EROFS if read-only file system, ErrorCodes.EINVAL if invalid path, ErrorCodes.ENOENT if element not
	 *         existing, ErrorCodes.ENOSYS if not supported, ErrorCodes.EIO if IO error occurred
	 * @throws ModuleException if an exception occurs
	 */
	public int checkAndLock(ProviderPort port, String[] path) throws ModuleException;

	/**
	 * Creates a folder. If necessary parent folders are created automatically.
	 *
	 * @param port the port
	 * @param path the path
	 * @return the result code, 0 = OK, -1 = readonly, >1 = error, 1 = exists
	 * @throws ModuleException if an exception occurs
	 */
	public int createFolder(ProviderPort port, String[] path) throws ModuleException;

	/**
	 * Deletes an element at a given path.
	 *
	 * @param port the port
	 * @param path the path
	 * @return the result code, 0 = OK, -1 = read-only, 1 non-existing, >1 = error
	 * @throws ModuleException if an exception occurs
	 */
	public int delete(ProviderPort port, String[] path) throws ModuleException;

	/**
	 * Gets the child elements under a given parent path.
	 *
	 * @param port the port
	 * @param path the path
	 * @param recursive set to true to get all children recursively (relative depth >= 1)
	 * @return the child elements (null if non existing/error or no children supported, for example within file elements)
	 * @throws ModuleException if an exception occurs
	 */
	// null if non existing/error or no children supported (for example within file elements)
	public Set<DataElement> getChildElements(ProviderPort port, String[] path, boolean recursive) throws ModuleException;

	/**
	 * Gets the element at a given path.
	 *
	 * @param port the port
	 * @param path the path
	 * @return the element (null if non existing or error)
	 * @throws ModuleException if an exception occurs
	 */
	// null if non existing or error
	public DataElement getElement(ProviderPort port, String[] path) throws ModuleException;

	/**
	 * Gets the type of the element at a given path.
	 *
	 * @param port the port
	 * @param path the path
	 * @return the type (null if no such element)
	 * @throws ModuleException if an exception occurs
	 */
	// null if no such element
	public DataElementType getType(ProviderPort port, String[] path) throws ModuleException;

	/**
	 * Moves an element from one path to another within the same port. If necessary parent folders for destination are created automatically. Will NOT overwrite
	 * an existing destination element.
	 *
	 * @param port the port
	 * @param srcPath the source path
	 * @param destPath the destination path
	 * @return the result code, 0 = OK, 1 = source non existent, 2 = destination exists, >2 = error
	 * @throws ModuleException if an exception occurs
	 */
	public int move(ProviderPort port, String[] srcPath, String[] destPath) throws ModuleException;

	/**
	 * Requests the module state.
	 * <p>
	 * USAGE: Return the current state by calling {@link framework.control.ProviderConnector#sendState(framework.model.Port, int)}.
	 *
	 * @param port the port
	 */
	public void onStateRequest(ProviderPort port);

	/**
	 * Reads data from an element at given path.
	 *
	 * @param port the port
	 * @param path the path
	 * @return the input stream to read from (null if no such element or no data)
	 * @throws ModuleException if an exception occurs
	 */
	// null if no data or error
	public InputStream readData(ProviderPort port, String[] path) throws ModuleException;

	/**
	 * Unlocks an element at given path.
	 *
	 * @param port the port
	 * @param path the path
	 * @return the result code, 0 = OK, 4 = already locked, -1 = not supported
	 * @throws ModuleException if an exception occurs
	 */
	public int unlock(ProviderPort port, String[] path) throws ModuleException;

	/**
	 * Writes data to an element at given path, creates it if necessary (including parent folders).
	 *
	 * @param port the port
	 * @param path the path
	 * @return the output stream to write to (null if read only)
	 * @throws ModuleException if an exception occurs
	 */
	public OutputStream writeData(ProviderPort port, String[] path) throws ModuleException;
}
