package experimental.module.filebrowser.content.control;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.imaging.ImageReadException;

import experimental.module.filebrowser.content.type.ImageContent;
import framework.model.DataElement;

/**
 *
 * @author Stefan Werner
 */
public class ContentResolver {

	public static Image getPreviewImageOfContent(final DataElement element, final InputStream fileInputStream, final int maxImageWidth, final int maxImageHeight) {
		final String filename = element.getName();
		if ((filename == null) || filename.isEmpty()) {
			return null;
		}
		BufferedImage image = null;
		if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".png")) { // TODO
			try {
				image = ImageContent.getImage(fileInputStream);
			} catch (final ImageReadException e) {
				// TODO Auto-generated catch block
			} catch (final IOException e) {
				// TODO Auto-generated catch block
			}
			if (image != null) {
				return ContentResolver.scaleImageIfRequired(image, maxImageWidth, maxImageHeight);
			}
		}
		return null;
	}

	public static Image scaleImageIfRequired(final BufferedImage input, final int width, final int height) {
		if ((input.getWidth() > width) || (input.getHeight() > width)) {
			if (input.getWidth() > input.getHeight()) {
				return input.getScaledInstance(width, -1, Image.SCALE_FAST);
			} else {
				return input.getScaledInstance(-1, width, Image.SCALE_FAST);
			}
		} else if ((input.getWidth() < width) && (input.getHeight() < width)) {
			if (input.getWidth() > input.getHeight()) {
				return input.getScaledInstance(width, -1, Image.SCALE_FAST);
			} else {
				return input.getScaledInstance(-1, width, Image.SCALE_FAST);
			}
		}
		return input;
	}

}
