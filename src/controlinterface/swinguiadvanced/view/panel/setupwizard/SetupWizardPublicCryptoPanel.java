package controlinterface.swinguiadvanced.view.panel.setupwizard;

import helper.ResourceHelper;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextPane;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingSimpleControlWrapper;
import framework.control.LocalizationConnector;

/**
 * Setup Wizard slide to manage public cryptographic settings for sharing.
 *
 * @author Stefan Werner
 */
public class SetupWizardPublicCryptoPanel extends JPanel {

	private static final String RESOURCE___ICON = "icons/controlinterface/swingadvanced/setupwizzard/config4.png";
	private static final long serialVersionUID = -459910328974969504L;

	private final SwingSimpleControlWrapper controller;
	private JTextPane fingerprintPane;
	private JLabel fingerprintTextLabel;
	private JLabel iconLabel;
	private final LocalizationConnector localizationConnector;
	private JButton manageOtherPublicKeysButton;
	private JLabel othersLabel;
	private JButton ownPublicKeyExportButton;
	private JSeparator separator;

	/**
	 * Instantiates a new setup wizard public crypto panel slide.
	 *
	 * @param wrapper the simple UI wrapper
	 * @param localizationConnector the localization connector
	 */
	public SetupWizardPublicCryptoPanel(final SwingSimpleControlWrapper wrapper, final LocalizationConnector localizationConnector) {
		this.controller = wrapper;
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Initializes the slide.
	 */
	private void initialize() {
		setLayout(new MigLayout("flowy", "[grow][300px,grow][grow]", "[grow][][][50px][][grow]"));
		this.fingerprintTextLabel = new JLabel(this.localizationConnector.getLocalizedString("Your Fingerprint (compare whenever you exchange public keys):"));
		add(this.fingerprintTextLabel, "cell 0 1 3 1,alignx center");
		this.fingerprintPane = new JTextPane();
		this.fingerprintPane.setForeground(new Color(0, 102, 153));
		this.fingerprintPane.setFont(new Font("Dialog", Font.PLAIN, 40));
		add(this.fingerprintPane, "cell 0 2 3 1,alignx center,growy");
		this.iconLabel = new JLabel();
		this.iconLabel.setIcon(ResourceHelper.getImageIconByName(SetupWizardPublicCryptoPanel.RESOURCE___ICON));
		add(this.iconLabel, "cell 0 4,alignx right,gapx 0 40,aligny center");
		this.othersLabel = new JLabel(this.localizationConnector.getLocalizedString("Others"));
		add(this.othersLabel, "cell 1 4,alignx center");
		this.separator = new JSeparator();
		add(this.separator, "cell 1 4,growx");
		this.ownPublicKeyExportButton = new JButton(this.localizationConnector.getLocalizedString("Export Your Own Public Key"));
		this.ownPublicKeyExportButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SetupWizardPublicCryptoPanel.this.controller.exportOwnPublicKey();
			}
		});
		add(this.ownPublicKeyExportButton, "cell 1 4,growx");
		this.manageOtherPublicKeysButton = new JButton(this.localizationConnector.getLocalizedString("Manage Other Public Keys"));
		this.manageOtherPublicKeysButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SetupWizardPublicCryptoPanel.this.controller.manageOtherPublicKeys();
			}
		});
		add(this.manageOtherPublicKeysButton, "cell 1 4,growx");
	}

	/**
	 * Sets the fingerprint of the current private key.
	 *
	 * @param fingerprint the fingerprint
	 */
	public void setKeyFingerprint(final String fingerprint) {
		if (fingerprint == null) {
			this.fingerprintPane.setText("(" + this.localizationConnector.getLocalizedString("own key is missing") + ")");
			this.ownPublicKeyExportButton.setEnabled(false);
		} else {
			this.fingerprintPane.setText(fingerprint);
			this.ownPublicKeyExportButton.setEnabled(true);
		}
	}
}
