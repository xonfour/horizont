package experimental.module.filebrowser.content.type;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;

/**
 *
 * @author Stefan Werner
 */
public class ImageContent {

	public static BufferedImage getImage(final InputStream in) throws ImageReadException, IOException {
		final BufferedImage image = Imaging.getBufferedImage(in);
		return image;
	}

	public static void ghjgh() {
		try {
			// <b>Code won't work unless these variables are properly
			// initialized.
			// Imaging works equally well with File, byte array or InputStream
			// inputs.</b>
			final BufferedImage someImage = null;
			final byte someBytes[] = null;
			final File someFile = null;
			final InputStream someInputStream = null;
			final OutputStream someOutputStream = null;

			// <b>The Imaging class provides a simple interface to the library.
			// </b>

			// <b>how to read an image: </b>
			final byte imageBytes[] = someBytes;
			Imaging.getBufferedImage(imageBytes);

			Imaging.getBufferedImage(imageBytes);
			final File file = someFile;
			Imaging.getBufferedImage(file);
			final InputStream is = someInputStream;
			Imaging.getBufferedImage(is);

			// <b>Write an image. </b>
			final BufferedImage image = someImage;
			final File dst = someFile;
			final ImageFormat format = ImageFormats.PNG;
			final Map<String, Object> optionalParams = new HashMap<String, Object>();
			Imaging.writeImage(image, dst, format, optionalParams);

			final OutputStream os = someOutputStream;
			Imaging.writeImage(image, os, format, optionalParams);

			Imaging.getICCProfileBytes(imageBytes);

			Imaging.getICCProfile(imageBytes);

			Imaging.getImageSize(imageBytes);

			Imaging.getImageInfo(imageBytes);

			// <b>try to guess the image's format. </b>
			final ImageFormat imageFormat = Imaging.guessFormat(imageBytes);
			imageFormat.equals(ImageFormats.PNG);

			Imaging.getMetadata(imageBytes);

			// <b>print a dump of information about an image to stdout. </b>
			Imaging.dumpImageFile(imageBytes);

			Imaging.getFormatCompliance(imageBytes);

		} catch (final Exception e) {

		}
	}
}
