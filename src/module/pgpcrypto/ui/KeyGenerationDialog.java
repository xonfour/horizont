package module.pgpcrypto.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;
import javax.swing.border.EtchedBorder;

import net.miginfocom.swing.MigLayout;

import org.bouncycastle.util.Arrays;

/**
 * Dialog to request user input for key generation.
 * <p>
 * TODO: Nice dialog, but not currently used. Use it! And also add localization and password quality meter.
 *
 * @author Stefan Werner
 */
public class KeyGenerationDialog extends JDialog {

	private static final long serialVersionUID = 1895749421823407035L;

	/**
	 * Shows the dialogs.
	 *
	 * @return the result
	 */
	public static char[][] showDialog() {
		final KeyGenerationDialog dialog = new KeyGenerationDialog();
		try {
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			// dialog.pack();
			dialog.setModal(true);
			dialog.setLocationRelativeTo(dialog.getParent());
			dialog.setVisible(true);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return dialog.getResult();
	}

	private final JPanel contentPanel = new JPanel();
	private JTextField emailField;
	private JLabel emailLabel;
	private JTextField nameField;
	private JLabel nameLabel;
	private JPasswordField passwordField1;
	private JPasswordField passwordField2;
	private JTextPane passwordInfoPane;
	private JLabel passwordLabel;
	private JLabel passwordRepeatLabel;
	private char[][] result = null;
	private JSeparator separator;
	private JTextPane userIdInfoPane;

	/**
	 * Creates the dialog.
	 */
	public KeyGenerationDialog() {
		setBounds(100, 100, 350, 400);
		setTitle("Key generation");
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		this.contentPanel.setLayout(new MigLayout("", "[][grow]", "[][][][][][][]"));

		this.userIdInfoPane = new JTextPane();
		this.userIdInfoPane.setEditable(false);
		this.userIdInfoPane.setText("Please provide a user name and an email address to indentify this key. Both must not be empty.");
		this.contentPanel.add(this.userIdInfoPane, "cell 0 0 2 1");

		this.nameLabel = new JLabel("User Name");
		this.contentPanel.add(this.nameLabel, "cell 0 1,alignx trailing");

		this.nameField = new JTextField();
		this.contentPanel.add(this.nameField, "cell 1 1,growx");
		this.nameField.setColumns(10);

		this.emailLabel = new JLabel("Email");
		this.contentPanel.add(this.emailLabel, "cell 0 2,alignx trailing");

		this.emailField = new JTextField();
		this.contentPanel.add(this.emailField, "cell 1 2,growx");
		this.emailField.setColumns(10);

		this.separator = new JSeparator();
		this.contentPanel.add(this.separator, "cell 0 3 2 1,growx");

		this.passwordInfoPane = new JTextPane();
		this.passwordInfoPane.setEditable(false);
		this.passwordInfoPane.setText("Please select a secure password and enter it below two times to avoid typing mistakes. Minimum password length is 8 characters.");
		this.contentPanel.add(this.passwordInfoPane, "cell 0 4 2 1");

		this.passwordLabel = new JLabel("Password");
		this.contentPanel.add(this.passwordLabel, "cell 0 5,alignx trailing");

		this.passwordField1 = new JPasswordField();
		this.contentPanel.add(this.passwordField1, "cell 1 5,growx");

		this.passwordRepeatLabel = new JLabel("(repeat)");
		this.contentPanel.add(this.passwordRepeatLabel, "cell 0 6,alignx trailing");

		this.passwordField2 = new JPasswordField();
		this.contentPanel.add(this.passwordField2, "flowx,cell 1 6,growx");
		{
			final JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				final JButton okButton = new JButton("OK");
				okButton.addActionListener(new ActionListener() {
					/* (non-Javadoc)
					 *
					 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
					@Override
					public void actionPerformed(final ActionEvent e) {
						if (KeyGenerationDialog.this.nameField.getText().isEmpty() || KeyGenerationDialog.this.emailField.getText().isEmpty()) {
							JOptionPane.showMessageDialog(null, "Name and/or Email address must not be empty!", "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}
						if ((KeyGenerationDialog.this.passwordField1.getPassword().length < 8) || !Arrays.areEqual(KeyGenerationDialog.this.passwordField1.getPassword(), KeyGenerationDialog.this.passwordField2.getPassword())) {
							JOptionPane.showMessageDialog(null, "Passwort to short or does not match!", "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}
						KeyGenerationDialog.this.result = new char[3][];
						KeyGenerationDialog.this.result[0] = KeyGenerationDialog.this.nameField.getText().toCharArray();
						KeyGenerationDialog.this.result[1] = KeyGenerationDialog.this.emailField.getText().toCharArray();
						KeyGenerationDialog.this.result[2] = KeyGenerationDialog.this.passwordField1.getPassword();
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

	/**
	 * Gets the result.
	 *
	 * @return the result
	 */
	public char[][] getResult() {
		return this.result;
	}
}
