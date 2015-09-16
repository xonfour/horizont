package controlinterface.swinguiadvanced.view.panel;

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import net.miginfocom.swing.MigLayout;

/**
 * Simple Panel to visualize a message, an optional icon/symbol and an optional checkbox.
 *
 * @author Stefan Werner
 */
public class MessagePanel extends JPanel {

	private static final long serialVersionUID = 4029860761585046944L;

	private JCheckBox checkBox;
	private JLabel iconLabel;
	private final JTextPane textPane;

	/**
	 * Instantiates a new message panel.
	 *
	 * @param messageText the message text
	 * @param messageIcon the message icon
	 * @param checkBoxText the check box text
	 */
	public MessagePanel(final String messageText, final Icon messageIcon, final String checkBoxText) {
		setLayout(new MigLayout("", "[grow]", "[][]"));
		if (messageIcon != null) {
			this.iconLabel = new JLabel(messageIcon);
			add(this.iconLabel, "cell 0 0,aligny center");
		}
		this.textPane = new JTextPane();
		this.textPane.setText(messageText);
		this.textPane.setEditable(false);
		add(this.textPane, "cell 0 0,growx,aligny center");
		if (checkBoxText != null) {
			this.checkBox = new JCheckBox(checkBoxText);
			add(this.checkBox, "cell 0 1");
		}
	}

	/**
	 * Checks if is check box if selected.
	 *
	 * @return true, if checkbox is selected
	 */
	public boolean isCheckBoxSelected() {
		if (this.checkBox != null) {
			return this.checkBox.isSelected();
		} else {
			return false;
		}
	}
}
