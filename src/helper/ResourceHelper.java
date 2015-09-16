package helper;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

/**
 * Helper to load resource files. Holds a static cache for images to only have them loaded once.
 * <p>
 *
 * @author Stefan Werner
 */
public class ResourceHelper {

	// Map is used to cache images globally
	// TODO: Should be done in a more sophisticated way.
	private static Map<String, ImageIcon> iconMap = new HashMap<String, ImageIcon>();

	/**
	 * Gets an image icon by name.
	 *
	 * @param name the name
	 * @return the image icon by name
	 */
	public static ImageIcon getImageIconByName(final String name) {
		if (name == null) {
			return null;
		}
		ImageIcon icon = ResourceHelper.iconMap.get(name);
		if (icon == null) {
			final URL url = ResourceHelper.getResource(name);
			if (url == null) {
				return new ImageIcon();
			}
			icon = new ImageIcon(url);
			ResourceHelper.iconMap.put(name, icon);
		}
		return icon;
	}

	/**
	 * Gets a resource.
	 *
	 * @param fileName the file name
	 * @return the resource
	 */
	public static URL getResource(final String fileName) {
		return ClassLoader.getSystemClassLoader().getResource(fileName);
	}

	/**
	 * Gets a resource stream.
	 *
	 * @param fileName the resource file name
	 * @return the resource stream
	 */
	public static InputStream getStream(final String fileName) {
		return ClassLoader.getSystemClassLoader().getResourceAsStream(fileName);
	}
}
