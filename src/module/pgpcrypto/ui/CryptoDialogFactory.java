package module.pgpcrypto.ui;

import javax.swing.JOptionPane;

/**
 * Factory for dialog boxes.
 * <p>
 * TODO: Currently dialog are modal, we should change that. It would also be nice to have only one dialog box that grows with new messages arriving. Also check
 * localization (currently done in controller class).
 *
 * @author Stefan Werner
 */
public final class CryptoDialogFactory {

	/**
	 * Display error dialog.
	 *
	 * @param text the text
	 */
	public static void displayErrorDialog(final String text) {
		JOptionPane.showMessageDialog(null, text, "Error", JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Display error dialog.
	 *
	 * @param text the text
	 * @param e the exception
	 */
	public static void displayErrorDialog(final String text, final Exception e) {
		JOptionPane.showMessageDialog(null, text + "\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Display info dialog.
	 *
	 * @param text the text
	 */
	public static void displayInfoDialog(final String text) {
		JOptionPane.showMessageDialog(null, text, "Information", JOptionPane.INFORMATION_MESSAGE);
	}
}
