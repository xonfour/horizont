package controlinterface.swinguiadvanced.view.panel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.ConnectionManagementController;
import controlinterface.swinguiadvanced.view.other.TableSelectionListener;
import framework.control.LocalizationConnector;

/**
 * Visualizes (in)active connection and provides ways to manage them.
 *
 * @author Stefan Werner
 */
public class ConnectionManagementPanel extends JPanel {

	private static final long serialVersionUID = -5788748908103055456L;

	private final TableSelectionScrollPane conPane;
	private final JButton reloadButton;
	private final JButton removeButton;

	/**
	 * Create the panel.
	 *
	 * @param controller the connection management controller
	 * @param localizationConnector the localization connector
	 */
	public ConnectionManagementPanel(final ConnectionManagementController controller, final LocalizationConnector localizationConnector) {
		setLayout(new MigLayout("fill", "[1100px,grow]", "[grow][]"));
		final String[] columnTitles = new String[9];
		columnTitles[0] = localizationConnector.getLocalizedString("Prov. Port Name (ID)");
		columnTitles[1] = localizationConnector.getLocalizedString("Prov. Port Name (ID)");
		columnTitles[2] = localizationConnector.getLocalizedString("Pros. Port Name (ID)");
		columnTitles[3] = localizationConnector.getLocalizedString("Pros. Module Name (ID)");
		columnTitles[4] = localizationConnector.getLocalizedString("Is Active");
		columnTitles[5] = localizationConnector.getLocalizedString("Priority");
		columnTitles[6] = localizationConnector.getLocalizedString("Data Transfered");
		columnTitles[7] = localizationConnector.getLocalizedString("Last Update");
		columnTitles[8] = localizationConnector.getLocalizedString("State");
		this.conPane = new TableSelectionScrollPane(null, columnTitles);
		this.conPane.addTableSelectionListener(new TableSelectionListener() {

			@Override
			public void onComponentSelected(final int index) {
				if (index < 0) {
					ConnectionManagementPanel.this.removeButton.setEnabled(false);
				} else {
					ConnectionManagementPanel.this.removeButton.setEnabled(true);
				}
			}
		});
		add(this.conPane, "cell 0 0,grow");
		this.reloadButton = new JButton(localizationConnector.getLocalizedString("Reload"));
		this.reloadButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				controller.updateData(false);
			}
		});
		add(this.reloadButton, "flowx,cell 0 1,growx");
		this.removeButton = new JButton(localizationConnector.getLocalizedString("Remove"));
		this.removeButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				controller.removeConnection(ConnectionManagementPanel.this.conPane.getSelectedIndex());
			}
		});
		this.removeButton.setEnabled(false);
		add(this.removeButton, "cell 0 1,growx");
	}

	/**
	 * Gets the selected index.
	 *
	 * @return the selected index
	 */
	public int getSelectedIndex() {
		return this.conPane.getSelectedIndex();
	}

	/**
	 * Updates the data.
	 *
	 * @param data the data 2D array
	 */
	public void updateData(final String[][] data) {
		this.conPane.updateData(data);
	}
}
