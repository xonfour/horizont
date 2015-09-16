package controlinterface.swinguiadvanced.view.dialog;

import helper.ResourceHelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.MatteBorder;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.constants.SwingAdvancedConstants;
import controlinterface.swinguiadvanced.view.panel.MessagePanel;

/**
 * Generic Dialog to wrap other visual components.
 * <p>
 * IMPORTANT: In this Class no localization is done.
 *
 * @author Stefan Werner
 */
public class GenericDialog extends JDialog {

	private static final long serialVersionUID = -8121820705074051686L;
	private static String RESOURCE___ICON = "icons/controlinterface/swingadvanced/toolbar_icon.png";

	/**
	 * Shows a generic message dialog.
	 *
	 * @param title the title
	 * @param message the message
	 * @param closeButtonName the close button name
	 */
	public static void showGenericMessageDialog(final String title, final String message, final String closeButtonName) {
		final MessagePanel panel = new MessagePanel(message, null, null);
		(new GenericDialog(null, title, closeButtonName, null, panel)).showDialog();
	}

	private int buttonPressed = -1;
	private final JPanel contentPanel = new JPanel();
	private JButton firstButton;
	private JButton secondButton;

	/**
	 * Instantiates a new generic dialog.
	 *
	 * @param parent the parent window
	 * @param title the title of the dialog
	 * @param preferredSize the preferred size
	 * @param firstButtonTitle the first button title
	 * @param secondButtonTitle the second button title
	 * @param elements the elements of the dialog (visual components or other Objects)
	 */
	public GenericDialog(final Window parent, final String title, final Dimension preferredSize, final String firstButtonTitle, final String secondButtonTitle, final Object... elements) {
		super(parent);
		setIconImage(ResourceHelper.getImageIconByName(GenericDialog.RESOURCE___ICON).getImage());
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.DOCUMENT_MODAL);
		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		setMaximumSize(screenSize);
		setMinimumSize(SwingAdvancedConstants.DIALOG___MIN_SIZE);
		if (preferredSize != null) {
			setPreferredSize(preferredSize);
		}
		setTitle(title);
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		this.contentPanel.setLayout(new MigLayout("flowy", "[grow]", "[grow]"));
		for (final Object elem : elements) {
			if (elem instanceof JComponent) {
				if (elem instanceof JScrollPane) {
					this.contentPanel.add((JComponent) elem, "cell 0 0,grow,growprioy 200");
				} else {
					this.contentPanel.add((JComponent) elem, "cell 0 0,grow");
				}
			} else {
				final JTextPane textPane = new JTextPane();
				textPane.setEditable(false);
				try {
					textPane.setText(elem.toString());
				} catch (final Exception e) {
					textPane.setText("ERR/NULL");
				}
				this.contentPanel.add(textPane, "cell 0 0,growx");
			}
		}

		// dialog buttons
		final JPanel buttonPane = new JPanel();
		buttonPane.setBackground(Color.WHITE);
		buttonPane.setBorder(new MatteBorder(1, 0, 0, 0, SystemColor.windowBorder));
		buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		if (firstButtonTitle != null) {
			this.firstButton = new JButton(firstButtonTitle);
			this.firstButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent arg0) {
					GenericDialog.this.buttonPressed = 0;
					dispose();
				}
			});
			this.firstButton.setActionCommand(firstButtonTitle);
			buttonPane.add(this.firstButton);
			getRootPane().setDefaultButton(this.firstButton);
		}

		if (secondButtonTitle != null) {
			this.secondButton = new JButton(secondButtonTitle);
			this.secondButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent arg0) {
					GenericDialog.this.buttonPressed = 1;
					dispose();
				}
			});
			this.secondButton.setActionCommand(secondButtonTitle);
			buttonPane.add(this.secondButton);
		}

		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				pack();
			}
		});
	}

	/**
	 * Instantiates a new generic dialog.
	 *
	 * @param parent the parent window
	 * @param title the title
	 * @param firstButtonTitle the first button title
	 * @param secondButtonTitle the second button title
	 * @param elements the elements
	 */
	public GenericDialog(final Window parent, final String title, final String firstButtonTitle, final String secondButtonTitle, final Object... elements) {
		this(parent, title, null, firstButtonTitle, secondButtonTitle, elements);
	}

	/**
	 * Disables first button.
	 */
	public void disableFirstButton() {
		this.firstButton.setEnabled(false);
	}

	/**
	 * Enables first button.
	 */
	public void enableFirstButton() {
		this.firstButton.setEnabled(true);
	}

	/**
	 * Shows dialog.
	 *
	 * @return the number of the button pressed (from left to right, starting with 0)
	 */
	public int showDialog() {
		setVisible(true);
		return this.buttonPressed;
	}
}
