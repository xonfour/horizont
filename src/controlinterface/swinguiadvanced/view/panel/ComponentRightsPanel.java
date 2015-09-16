package controlinterface.swinguiadvanced.view.panel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import framework.control.LocalizationConnector;

/**
 * Panel to visualize sets of component (control interface or module) rights and to let the user alter them.
 *
 * @author Stefan Werner
 */
public class ComponentRightsPanel extends JPanel {

	private static final long serialVersionUID = -1858671116333927672L;

	private final Map<Integer, JCheckBox> rightsCheckBoxes = new HashMap<Integer, JCheckBox>();
	private final JButton selectAllButton;
	private final JButton selectNoneButton;

	/**
	 * Create the panel.
	 *
	 * @param availableRights the available rights
	 * @param initialRights the initial rights
	 * @param localizationConnector the localization connector
	 */
	public ComponentRightsPanel(final Map<Integer, String> availableRights, final int initialRights, final LocalizationConnector localizationConnector) {
		setLayout(new MigLayout("flowy", "[grow]", "[][]"));
		this.selectAllButton = new JButton(localizationConnector.getLocalizedString("Select All"));
		this.selectAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				for (final JCheckBox checkBox : ComponentRightsPanel.this.rightsCheckBoxes.values()) {
					checkBox.setSelected(true);
				}
			}
		});
		add(this.selectAllButton, "flowx,cell 0 0,growx");
		this.selectNoneButton = new JButton(localizationConnector.getLocalizedString("Select None"));
		this.selectNoneButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				for (final JCheckBox checkBox : ComponentRightsPanel.this.rightsCheckBoxes.values()) {
					checkBox.setSelected(false);
				}
			}
		});
		add(this.selectNoneButton, "cell 0 0,growx");
		for (final int right : availableRights.keySet()) {
			final String s = availableRights.get(right);
			final JCheckBox checkBox = new JCheckBox(s);
			if ((initialRights & right) == right) {
				checkBox.setSelected(true);
			}
			this.rightsCheckBoxes.put(right, checkBox);
			add(checkBox, "cell 0 1,growx");
		}
	}

	/**
	 * Gets the selected rights.
	 *
	 * @return the selected rights
	 */
	public int getSelectedRights() {
		int rights = 0;
		for (final int right : this.rightsCheckBoxes.keySet()) {
			final JCheckBox checkBox = this.rightsCheckBoxes.get(right);
			if (checkBox.isSelected()) {
				rights += right;
			}
		}
		return rights;
	}
}
