package controlinterface.swinguiadvanced.view.panel;

import helper.TextFormatHelper;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface;
import framework.control.LocalizationConnector;
import framework.model.event.type.ConnectionEventType;
import framework.model.summary.ConnectionSummary;

/**
 * Provides context information on a selected connection in the advanced CI.
 *
 * @author Stefan Werner
 */
public class ConnectionInfoPanel extends JPanel {

	private static final long serialVersionUID = -6899238824896768314L;

	private ConnectionSummary connection;
	private JLabel connectionLabel;
	private final SwingAdvancedControlInterface controller;
	private JButton disconnectButton;
	private final LocalizationConnector localizationConnector;
	private JLabel prosumerLabel;
	private JLabel prosumerTextLabel;
	private JLabel providerLabel;
	private JLabel providerTextLabel;
	private JButton refreshButton;
	private JLabel refreshDateLabel;
	private JLabel refreshDateTextLabel;
	private JLabel stateLabel;
	private JLabel stateTextLabel;
	private JLabel transferedDataLabel;
	private JLabel transferedDataTextLabel;

	/**
	 * Create the panel.
	 *
	 * @param connection the connection summary
	 * @param controller the advanced CI controller
	 * @param localizationConnector the localization connector
	 */
	public ConnectionInfoPanel(final ConnectionSummary connection, final SwingAdvancedControlInterface controller, final LocalizationConnector localizationConnector) {
		this.controller = controller;
		this.localizationConnector = localizationConnector;
		initialize();
		updateData(connection);
	}

	/**
	 * Initializes the panel.
	 */
	private void initialize() {
		setLayout(new MigLayout("", "[grow]", "[][][][][][][grow][][]"));
		this.connectionLabel = new JLabel(this.localizationConnector.getLocalizedString("Connection Info"));
		this.connectionLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		add(this.connectionLabel, "cell 0 0");
		this.prosumerLabel = new JLabel(this.localizationConnector.getLocalizedString("Prosumer Port: "));
		add(this.prosumerLabel, "cell 0 1");
		this.prosumerTextLabel = new JLabel();
		add(this.prosumerTextLabel, "cell 0 1");
		this.providerLabel = new JLabel(this.localizationConnector.getLocalizedString("Provider Port: "));
		add(this.providerLabel, "cell 0 2");
		this.providerTextLabel = new JLabel();
		add(this.providerTextLabel, "cell 0 2");
		this.refreshDateLabel = new JLabel(this.localizationConnector.getLocalizedString("Last refresh:"));
		add(this.refreshDateLabel, "cell 0 3");
		this.refreshDateTextLabel = new JLabel();
		add(this.refreshDateTextLabel, "cell 0 3");
		this.transferedDataLabel = new JLabel(this.localizationConnector.getLocalizedString("Transfered Data:"));
		add(this.transferedDataLabel, "cell 0 4");
		this.transferedDataTextLabel = new JLabel();
		add(this.transferedDataTextLabel, "cell 0 4");
		this.refreshButton = new JButton(this.localizationConnector.getLocalizedString("Refresh"));
		this.refreshButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				final ConnectionSummary refreshedSummary = ConnectionInfoPanel.this.controller.refreshConnection(ConnectionInfoPanel.this.connection);
				if (refreshedSummary != null) {
					ConnectionInfoPanel.this.connection = refreshedSummary;
					updateData(refreshedSummary);
				}
			}
		});
		add(this.refreshButton, "cell 0 7,growx");
		this.disconnectButton = new JButton(this.localizationConnector.getLocalizedString("Disconnect"));
		this.disconnectButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				ConnectionInfoPanel.this.controller.removeConnection(ConnectionInfoPanel.this.connection);
			}
		});
		add(this.disconnectButton, "cell 0 8,growx");
	}

	/**
	 * Updates the data.
	 *
	 * @param connection the connection summary
	 */
	public void updateData(final ConnectionSummary connection) {
		if (connection != null) {
			this.connection = connection;
			final String prosumerText = connection.getProsumerPortSummary().getPortId() + " (" + connection.getProsumerPortSummary().getModuleId() + ")";
			this.prosumerTextLabel.setText(prosumerText);
			final String providerText = connection.getProviderPortSummary().getPortId() + " (" + connection.getProviderPortSummary().getModuleId() + ")";
			this.providerTextLabel.setText(providerText);
			this.refreshDateTextLabel.setText(connection.getLatestRefreshDate() == 0 ? "-" : this.localizationConnector.getFormatedDateLocalized(connection.getLatestRefreshDate()));
			this.transferedDataTextLabel.setText(TextFormatHelper.convertSizeValueToHumanReadableFormat(connection.getDataTransfered()));
		}
	}

	/**
	 * Updates the data.
	 *
	 * @param connection the connection
	 * @param state the connection state
	 */
	public void updateData(final ConnectionSummary connection, final ConnectionEventType state) {
		updateData(connection);
		if (this.stateLabel == null) {
			this.stateLabel = new JLabel(this.localizationConnector.getLocalizedString("State:"));
			add(this.stateLabel, "cell 0 5");
			this.stateTextLabel = new JLabel(this.localizationConnector.getLocalizedString(state.name()));
			add(this.stateTextLabel, "cell 0 5");
		} else {
			this.stateTextLabel.setText(this.localizationConnector.getLocalizedString(state.name()));
		}
	}
}
