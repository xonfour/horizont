package controlinterface.swinguiadvanced.view.panel.setupwizard;

import helper.ResourceHelper;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;
import framework.control.LocalizationConnector;

/**
 * Setup Wizard slide to ask for the Dropbox auth token required to access the user's Dropbox account.
 *
 * @author Stefan Werner
 */
public class SetupWizardDropboxAuthToken extends JPanel {

	private static final String RESOURCE___ICON = "icons/controlinterface/swingadvanced/setupwizzard/config3.png";
	private static final long serialVersionUID = -3239815359082770683L;

	private JLabel iconLabel;
	private final LocalizationConnector localizationConnector;
	private JLabel selectedLocalFolderLabel;
	private JTextField tokenTextField;

	/**
	 * Instantiates a new setup wizard dropbox auth token slide.
	 *
	 * @param localizationConnector the localization connector
	 */
	public SetupWizardDropboxAuthToken(final LocalizationConnector localizationConnector) {
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Gets the token.
	 *
	 * @return the token
	 */
	public String getToken() {
		return this.tokenTextField.getText();
	}

	/**
	 * Initializes the slide.
	 */
	private void initialize() {
		setLayout(new MigLayout("flowy", "[grow][500px,grow][grow]", "[grow][][grow]"));
		this.iconLabel = new JLabel();
		this.iconLabel.setIcon(ResourceHelper.getImageIconByName(SetupWizardDropboxAuthToken.RESOURCE___ICON));
		add(this.iconLabel, "cell 0 1,gapx 0 40,alignx right,aligny center");
		this.selectedLocalFolderLabel = new JLabel(this.localizationConnector.getLocalizedString("Dropbox Authorization Token:"));
		add(this.selectedLocalFolderLabel, "cell 1 1");
		this.tokenTextField = new JTextField();
		add(this.tokenTextField, "cell 1 1,growx");
		this.tokenTextField.setColumns(10);
	}

	/**
	 * Sets the token.
	 *
	 * @param token the new token
	 */
	public void setToken(final String token) {
		this.tokenTextField.setText(token);
	}
}