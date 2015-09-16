package controlinterface.swinguiadvanced.view.panel;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import net.miginfocom.swing.MigLayout;

/**
 * A simple text input panel for dialogs.
 *
 * @author Stefan Werner
 */
public class TextInputPanel extends JPanel {

	private static final long serialVersionUID = 7202055528136520555L;

	private final JTextField textField;

	/**
	 * Instantiates a new text input panel.
	 *
	 * @param initialText the initial text
	 * @param preselect set to true to preselect all text
	 */
	public TextInputPanel(final String initialText, final boolean preselect) {
		setLayout(new MigLayout("", "[300px,grow]", "[]"));
		this.textField = new JTextField(initialText);
		add(this.textField, "cell 0 0,growx");
		this.textField.setColumns(10);
		if (preselect) {
			addAncestorListener(new AncestorListener() {

				@Override
				public void ancestorAdded(final AncestorEvent event) {
					TextInputPanel.this.textField.requestFocusInWindow();
					TextInputPanel.this.textField.selectAll();
				}

				@Override
				public void ancestorMoved(final AncestorEvent arg0) {
				}

				@Override
				public void ancestorRemoved(final AncestorEvent arg0) {
				}
			});
		}
	}

	/**
	 * Gets the text.
	 *
	 * @return the text
	 */
	public String getText() {
		return this.textField.getText();
	}
}
