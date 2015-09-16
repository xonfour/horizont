package module.pgpcrypto.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import net.miginfocom.swing.MigLayout;

/**
 * A simple (passive) status dialog.
 * <p>
 * TODO: Unused, use it!
 *
 * @author Stefan Werner
 */
public class StatusDialog extends JDialog {

	private static final long serialVersionUID = 1895749421823407035L;

	private static StatusDialog statusDialog;

	/**
	 * Ends progress dialog.
	 *
	 * @param progressTitle the progress title
	 */
	public static void endProgressDialog(final String progressTitle) {
		if (StatusDialog.statusDialog == null) {
			return;
		}
		StatusDialog.statusDialog.progressBar.setIndeterminate(false);
		StatusDialog.statusDialog.progressBar.setValue(100);
		StatusDialog.statusDialog.progressBar.setString(progressTitle);
		StatusDialog.statusDialog.okButton.setEnabled(true);
	}

	/**
	 * Shows progress dialog.
	 *
	 * @param title the title
	 */
	public static void showProgressDialog(final String title) {
		// only one status dialog allowed
		if (StatusDialog.statusDialog != null) {
			return;
		}
		StatusDialog.statusDialog = new StatusDialog(title);
		try {
			StatusDialog.statusDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			StatusDialog.statusDialog.pack();
			StatusDialog.statusDialog.setModalityType(ModalityType.APPLICATION_MODAL);
			StatusDialog.statusDialog.progressBar.setIndeterminate(true);
			StatusDialog.statusDialog.setVisible(true);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private final JPanel contentPanel = new JPanel();
	private JButton okButton;
	private final JProgressBar progressBar;
	private final JTextPane titleTextPane;

	/**
	 * Creates the dialog.
	 *
	 * @param title the title
	 */
	public StatusDialog(final String title) {
		setBounds(100, 100, 447, 322);
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		this.contentPanel.setLayout(new MigLayout("", "[grow]", "[][]"));

		this.titleTextPane = new JTextPane();
		this.titleTextPane.setText(title);
		this.contentPanel.add(this.titleTextPane, "cell 0 0");

		this.progressBar = new JProgressBar(100);
		this.contentPanel.add(this.progressBar, "cell 0 1,growx");
		{
			final JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				this.okButton = new JButton("OK");
				this.okButton.setEnabled(false);
				this.okButton.addActionListener(new ActionListener() {
					/* (non-Javadoc)
					 *
					 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
					@Override
					public void actionPerformed(final ActionEvent e) {
						dispose();
					}
				});
				this.okButton.setActionCommand("OK");
				buttonPane.add(this.okButton);
				getRootPane().setDefaultButton(this.okButton);
			}
			{
				final JButton cancelButton = new JButton("Cancel");
				cancelButton.addActionListener(new ActionListener() {
					/* (non-Javadoc)
					 *
					 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
					@Override
					public void actionPerformed(final ActionEvent e) {
						dispose();
					}
				});
				cancelButton.setActionCommand("Cancel");
				buttonPane.add(cancelButton);
			}
		}
	}
}
