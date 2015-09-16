package controlinterface.swinguiadvanced.view.panel;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface;
import framework.control.LocalizationConnector;

/**
 * Provides general information when nothing is selected in the advanced CI.
 * <p>
 * TODO: Well, fill it!
 *
 * @author Stefan Werner
 */
public class GeneralInfoPanel extends JPanel {

	private static final long serialVersionUID = -6476144016889301986L;

	private final LocalizationConnector localizationConnector;
	private JLabel systemStateLabel;

	/**
	 * Instantiates a new general info panel.
	 *
	 * @param controller the advanced CI controller
	 * @param localizationConnector the localization connector
	 */
	public GeneralInfoPanel(final SwingAdvancedControlInterface controller, final LocalizationConnector localizationConnector) {
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Initialize.
	 */
	private void initialize() {
		setLayout(new MigLayout("", "[grow]", "[]"));
		this.systemStateLabel = new JLabel(this.localizationConnector.getLocalizedString("Nothing selected"));
		add(this.systemStateLabel, "flowx,cell 0 0");
	}
}
