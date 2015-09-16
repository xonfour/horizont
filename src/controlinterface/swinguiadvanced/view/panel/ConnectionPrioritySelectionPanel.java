package controlinterface.swinguiadvanced.view.panel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;
import framework.control.LocalizationConnector;

/**
 * Simple dialog to select priority of a new port connection.
 *
 * @author Stefan Werner
 */
public class ConnectionPrioritySelectionPanel extends JPanel {

	private static final long serialVersionUID = -3475225577696906783L;

	private final JLabel connectionPriorityLabel;
	private final JSpinner prioritySpinner;

	/**
	 * Instantiates a new connection priority selection panel.
	 *
	 * @param initialPriority the initial priority
	 * @param localizationConnector the localization connector
	 */
	public ConnectionPrioritySelectionPanel(final int initialPriority, final LocalizationConnector localizationConnector) {
		setLayout(new MigLayout("", "[grow]", "[]"));
		this.connectionPriorityLabel = new JLabel(localizationConnector.getLocalizedString("Connection priority:"));
		add(this.connectionPriorityLabel, "flowx,cell 0 0");
		this.prioritySpinner = new JSpinner();
		this.prioritySpinner.setModel(new SpinnerNumberModel(initialPriority, null, null, new Integer(1)));
		add(this.prioritySpinner, "cell 0 0,width 100px,grow");
	}

	/**
	 * Gets the selected priority.
	 *
	 * @return the selected priority
	 */
	public int getSelectedPriority() {
		return (int) this.prioritySpinner.getValue();
	}
}
