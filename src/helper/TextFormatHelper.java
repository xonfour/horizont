package helper;

import java.text.DecimalFormat;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

/**
 * Formats texts to be used internally or to visualize them to the user.
 *
 * @author Stefan Werner
 */
public class TextFormatHelper {

	public static final Joiner pathJoiner = Joiner.on("/");
	public static final Splitter pathSplitter = Splitter.on("/").omitEmptyStrings();

	/**
	 * Convert size value to human readable format.
	 *
	 * @param size the size
	 * @return the human readable string
	 */
	public static String convertSizeValueToHumanReadableFormat(final long size) {
		if (size <= 0) {
			return "-";
		}
		final String[] units = new String[] { "B", "KiB", "MiB", "GiB", "TiB" };
		final int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
		return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}

	/**
	 * Gets the path array.
	 *
	 * @param pathString the path string
	 * @return the path array
	 */
	public static String[] getPathArray(final String pathString) {
		return TextFormatHelper.pathSplitter.splitToList(pathString).toArray(new String[0]);
	}

	/**
	 * Gets a path string.
	 *
	 * @param path the path
	 * @return the path string
	 */
	public static String getPathString(final String[] path) {
		return TextFormatHelper.pathJoiner.join(path);
	}
}
