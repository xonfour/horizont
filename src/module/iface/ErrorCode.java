package module.iface;

/**
 * This class holds numeric return error values for Provider method calls. It follows the well-established Linux system errors (/usr/include/asm/errno.h)
 * <p>
 * TODO: Use it everywhere.
 *
 * @author Stefan Werner
 */
public final class ErrorCode {

	/**
	 * Operation not permitted
	 */
	public static final int EPERM = -1;

	/**
	 * No such file or directory
	 */
	public static final int ENOENT = -2;

	/**
	 * Interrupted system call
	 */
	public static final int EINTR = -4;

	/**
	 * I/O error
	 */
	public static final int EIO = -5;

	/**
	 * Try again
	 */
	public static final int EAGAIN = -11;

	/**
	 * Permission denied
	 */
	public static final int EACCES = -13;

	/**
	 * Device or resource busy
	 */
	public static final int EBUSY = -16;

	/**
	 * File exists
	 */
	public static final int EEXIST = -17;

	/**
	 * Not a directory
	 */
	public static final int ENOTDIR = -20;

	/**
	 * Is a directory
	 */
	public static final int EISDIR = -21;

	/**
	 * Invalid argument
	 */
	public static final int EINVAL = -22;

	/**
	 * No space left on device
	 */
	public static final int ENOSPC = -28;

	/**
	 * Read-only file system
	 */
	public static final int EROFS = -30;

	/**
	 * File name too long
	 */
	public static final int ENAMETOOLONG = -36;

	/**
	 * Function not implemented
	 */
	public static final int ENOSYS = -38;

	/**
	 * Directory not empty
	 */
	public static final int ENOTEMPTY = -39;

	/**
	 * Directory not empty
	 */
	public static final int ETIMEDOUT = -110;

	/**
	 * Connection refused
	 */
	public static final int ECONNREFUSED = -111;

	/**
	 * Host is down
	 */
	public static final int EHOSTDOWN = -112;

	/**
	 * No route to host
	 */
	public static final int EHOSTUNREACH = -113;
}
