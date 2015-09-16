package helper.view;

import java.io.File;

import javax.swing.JFileChooser;

/**
 * Provides Swing file open and save dialogs.
 *
 * @author Stefan Werner
 */
public class SwingFileDialogHelper {

	/**
	 * Shows file open dialog.
	 *
	 * @param defaultLocation the default location
	 * @return the file
	 */
	public static File showFileOpenDialog(final String defaultLocation) {
		final JFileChooser chooser = new JFileChooser(defaultLocation);
		final int option = chooser.showOpenDialog(null);
		if ((option == JFileChooser.APPROVE_OPTION) && (chooser.getSelectedFile() != null) && chooser.getSelectedFile().isFile()) {
			return chooser.getSelectedFile();
		} else {
			return null;
		}
	}

	/**
	 * Shows file save dialog.
	 *
	 * @param defaultLocation the default location
	 * @return the file
	 */
	public static File showFileSaveDialog(final String defaultLocation) {
		final JFileChooser chooser = new JFileChooser(defaultLocation);
		final int option = chooser.showSaveDialog(null);
		if ((option == JFileChooser.APPROVE_OPTION) && (chooser.getSelectedFile() != null)) {
			return chooser.getSelectedFile();
		} else {
			return null;
		}
	}

}
