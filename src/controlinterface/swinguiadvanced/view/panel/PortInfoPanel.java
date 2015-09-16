package controlinterface.swinguiadvanced.view.panel;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface;
import framework.control.LocalizationConnector;
import framework.model.summary.PortSummary;

/**
 * Provides context information on a selected port in the advanced CI.
 *
 * @author Stefan Werner
 */
public class PortInfoPanel extends JPanel {

	private static final long serialVersionUID = 7680135114069805663L;

	private JLabel curConnectioncountLabel;
	private JLabel currentConnectionsLabel;
	private final LocalizationConnector localizationConnector;
	private JLabel maxConnectioncountLabel;
	private JLabel maximumConnectionsLabel;
	private JLabel nameLabel;
	private JLabel portLabel;
	private JLabel porttypeLabel;
	private JLabel typeLabel;

	/**
	 * Create the panel.
	 *
	 * @param portSummary the port summary
	 * @param controller the advanced CI controller (currently unused)
	 * @param localizationConnector the localization connector
	 */
	public PortInfoPanel(final PortSummary portSummary, final SwingAdvancedControlInterface controller, final LocalizationConnector localizationConnector) {
		// this.controller = controller;
		this.localizationConnector = localizationConnector;
		initialize();
		updateData(portSummary);
	}

	/**
	 * Initializes the panel.
	 */
	private void initialize() {
		setLayout(new MigLayout("", "[grow]", "[][][][][]"));
		this.portLabel = new JLabel(this.localizationConnector.getLocalizedString("Port Info"));
		this.portLabel.setFont(new Font("Dialog", Font.BOLD, 12));
		add(this.portLabel, "cell 0 0");
		this.nameLabel = new JLabel();
		add(this.nameLabel, "cell 0 1");
		this.typeLabel = new JLabel(this.localizationConnector.getLocalizedString("Type: "));
		add(this.typeLabel, "flowx,cell 0 2");
		this.currentConnectionsLabel = new JLabel(this.localizationConnector.getLocalizedString("Current connections: "));
		add(this.currentConnectionsLabel, "flowx,cell 0 3");
		this.curConnectioncountLabel = new JLabel();
		add(this.curConnectioncountLabel, "cell 0 3,growx");
		this.maximumConnectionsLabel = new JLabel(this.localizationConnector.getLocalizedString("Maximum connections: "));
		add(this.maximumConnectionsLabel, "flowx,cell 0 4");
		this.maxConnectioncountLabel = new JLabel();
		add(this.maxConnectioncountLabel, "cell 0 4");
		this.porttypeLabel = new JLabel();
		add(this.porttypeLabel, "cell 0 2");
	}

	/**
	 * Updates data.
	 *
	 * @param portSummary the port summary
	 */
	public void updateData(final PortSummary portSummary) {
		this.nameLabel.setText(portSummary.getPortId());
		this.porttypeLabel.setText(this.localizationConnector.getLocalizedString(portSummary.getType().name()));
		this.curConnectioncountLabel.setText(String.valueOf(portSummary.getCurrentConnections()));
		this.maxConnectioncountLabel.setText(portSummary.getMaxConnections() < 0 ? "inf." : String.valueOf(portSummary.getMaxConnections()));
	}
}
