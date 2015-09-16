package module.pgpcrypto.ui;

import java.awt.Component;

import javax.swing.JFileChooser;

/**
 * A factory for creating user interfaces, currently only file open and save dialog.
 *
 * @author Stefan Werner
 */
public class UserInterfaceElementFactory {

	/**
	 * Gets a file open path.
	 *
	 * @param parent the parent component
	 * @return the file open path
	 */
	public static String getFileOpenPath(final Component parent) {
		final JFileChooser chooser = new JFileChooser();
		// FileNameExtensionFilter filter = new FileNameExtensionFilter("SecureCloud Profiles", "json", "text");
		// chooser.setFileFilter(filter);
		final int option = chooser.showOpenDialog(parent);
		if ((option == JFileChooser.APPROVE_OPTION) && (chooser.getSelectedFile() != null)) {
			return chooser.getSelectedFile().getAbsolutePath();
		}
		return null;
	}

	/**
	 * Gets a file save path.
	 *
	 * @param parent the parent component
	 * @return the file save path
	 */
	public static String getFileSavePath(final Component parent) {
		final JFileChooser chooser = new JFileChooser();
		// FileNameExtensionFilter filter = new FileNameExtensionFilter("SecureCloud Profiles", "json", "text");
		// chooser.setFileFilter(filter);
		final int option = chooser.showSaveDialog(parent);
		if ((option == JFileChooser.APPROVE_OPTION) && (chooser.getSelectedFile() != null)) {
			return chooser.getSelectedFile().getAbsolutePath();
		}
		return null;
	}
}
