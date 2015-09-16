package module.pgpcrypto.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.miginfocom.swing.MigLayout;

import com.devewm.pwdstrength.PasswordStrengthMeter;

/**
 * Shows a password input dialog with a simple password strength/quality meter.
 *
 * @author Stefan Werner
 */
public class PasswordDialog extends JDialog implements DocumentListener {

	public static final int BEST_PASSWORD_STRENGTH = 50;
	private static final long serialVersionUID = 1895749421823407035L;
	public static final int STRENGTH_STEP_SIZE = 10;

	/**
	 * shows secret key password dialog
	 *
	 * @param title the title
	 * @return the password
	 */
	public static char[] showSecretKeyPasswordDialog(final String title) {
		final PasswordDialog dialog = new PasswordDialog(title);
		try {
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			dialog.pack();
			dialog.setModal(true);
			dialog.setVisible(true);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return dialog.getPassword();
	}

	private final JPanel contentPanel = new JPanel();
	private final JLabel lblPleaseProvidePassword;
	private char[] password = new char[0];
	private final JPasswordField passwordField;
	private final JProgressBar passwordQualityProgressBar;
	private final PasswordStrengthMeter passwordStrengthMeter = PasswordStrengthMeter.getInstance();

	/**
	 * Creates the dialog.
	 *
	 * @param title the title
	 */
	public PasswordDialog(final String title) {
		setBounds(100, 100, 450, 300);
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		this.contentPanel.setLayout(new MigLayout("", "[250px:n,grow]", "[][][]"));

		this.lblPleaseProvidePassword = new JLabel(title);
		this.contentPanel.add(this.lblPleaseProvidePassword, "cell 0 0");

		this.passwordField = new JPasswordField();
		this.passwordField.getDocument().addDocumentListener(this);
		this.contentPanel.add(this.passwordField, "cell 0 1,growx");

		this.passwordQualityProgressBar = new JProgressBar();
		this.passwordQualityProgressBar.setStringPainted(true);
		this.passwordQualityProgressBar.setString("Password Quality (BETA)");
		this.passwordQualityProgressBar.setToolTipText("WARNING: BETA, MAY BE INACCURATE!");
		this.passwordQualityProgressBar.setMaximum(PasswordDialog.BEST_PASSWORD_STRENGTH / PasswordDialog.STRENGTH_STEP_SIZE);
		this.contentPanel.add(this.passwordQualityProgressBar, "cell 0 2,growx");
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
						PasswordDialog.this.password = PasswordDialog.this.passwordField.getPassword();
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

	/* (non-Javadoc)
	 *
	 * @see javax.swing.event.DocumentListener#changedUpdate(javax.swing.event.DocumentEvent) */
	@Override
	public void changedUpdate(final DocumentEvent arg0) {
		// ignored
	}

	/**
	 * Gets the password.
	 *
	 * @return the password
	 */
	public char[] getPassword() {
		return this.password;
	}

	/* (non-Javadoc)
	 *
	 * @see javax.swing.event.DocumentListener#insertUpdate(javax.swing.event.DocumentEvent) */
	@Override
	public void insertUpdate(final DocumentEvent arg0) {
		refreshPasswordStrength();
	}

	/**
	 * Refreshes password strength meter.
	 */
	private void refreshPasswordStrength() {
		// This approach is VERY basic and naive as attacks on passwords may be (way) more sophisticated then simple brute forcing but it is better than nothing
		// to motivate user to use "good" passwords.
		// TODO: Find better way to measure password strength.
		this.passwordQualityProgressBar.setValue(this.passwordStrengthMeter.iterationCount(new String(this.passwordField.getPassword())).bitCount() / PasswordDialog.STRENGTH_STEP_SIZE);
	}

	/* (non-Javadoc)
	 *
	 * @see javax.swing.event.DocumentListener#removeUpdate(javax.swing.event.DocumentEvent) */
	@Override
	public void removeUpdate(final DocumentEvent arg0) {
		refreshPasswordStrength();
	}
}
