package module.pgpcrypto.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;

import net.miginfocom.swing.MigLayout;

/**
 * A simple dialog for text input.
 * <p>
 * TODO: Do we really need this. Consider moving to a generic dialog (see {@link controlinterface.swinguiadvanced.view.dialog.GenericDialog}).
 *
 * @author Stefan Werner
 */
public class TextInputDialog extends JDialog {

	private static final long serialVersionUID = 1895749421823407035L;

	/**
	 * Shows text input dialog.
	 *
	 * @param title the title
	 * @return the string
	 */
	public static String showTextInputDialog(final String title) {
		final TextInputDialog dialog = new TextInputDialog(title);
		try {
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			dialog.pack();
			dialog.setModal(true);
			dialog.setVisible(true);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return dialog.getText();
	}

	private final JPanel contentPanel = new JPanel();
	private final JLabel titleLabel;
	private final JScrollPane scrollPane;
	private String input = null;

	private final JTextArea textArea;

	/**
	 * Creates the dialog.
	 *
	 * @param title the title
	 */
	public TextInputDialog(final String title) {
		setBounds(100, 100, 600, 400);
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		this.contentPanel.setLayout(new MigLayout("", "[grow]", "[][grow]"));

		this.titleLabel = new JLabel(title);
		this.contentPanel.add(this.titleLabel, "cell 0 0");

		this.scrollPane = new JScrollPane();
		this.scrollPane.setViewportBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		this.contentPanel.add(this.scrollPane, "cell 0 1,grow");

		this.textArea = new JTextArea();
		this.scrollPane.setViewportView(this.textArea);

		final JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
		getContentPane().add(buttonPane, BorderLayout.SOUTH);
		{
			final JButton okButton = new JButton("OK");
			okButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					TextInputDialog.this.input = TextInputDialog.this.textArea.getText();
					dispose();
				}
			});
			okButton.setActionCommand("OK");
			buttonPane.add(okButton);
			getRootPane().setDefaultButton(okButton);
		}
		{
			final JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					dispose();
				}
			});
			cancelButton.setActionCommand("Cancel");
			buttonPane.add(cancelButton);
		}
	}

	/**
	 * Gets the text input.
	 *
	 * @return the text
	 */
	public String getText() {
		return this.input;
	}
}
