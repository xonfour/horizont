package controlinterface.swinguiadvanced.view.panel.setupwizard;

import helper.ResourceHelper;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingSimpleControlWrapper;
import framework.control.LocalizationConnector;

/**
 * Setup Wizard slide to manage own/private cryptographic settings.
 *
 * @author Stefan Werner
 */
public class SetupWizardPrivateCryptoPanel extends JPanel {

	private static final long serialVersionUID = 8236577393116969080L;
	private static final String RESOURCE___ICON = "icons/controlinterface/swingadvanced/setupwizzard/config2.png";

	private final SwingSimpleControlWrapper controller;
	private final LocalizationConnector localizationConnector;
	private JButton generateKeyButton;
	private JButton restoreKeyButton;
	private JLabel youLabel;
	private JSeparator separator;
	private JButton backupPublicKeyButton;
	private JLabel iconLabel;
	private JButton reloadButton;

	/**
	 * Instantiates a new setup wizard private crypto panel slide.
	 *
	 * @param controller the controller
	 * @param localizationConnector the localization connector
	 */
	public SetupWizardPrivateCryptoPanel(final SwingSimpleControlWrapper controller, final LocalizationConnector localizationConnector) {
		this.controller = controller;
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Disables buttons.
	 */
	private void disableButtons() {
		this.generateKeyButton.setEnabled(false);
		this.restoreKeyButton.setEnabled(false);
		this.backupPublicKeyButton.setEnabled(false);
		this.reloadButton.setEnabled(true);
	}

	/**
	 * Initializes the slide.
	 */
	private void initialize() {
		setLayout(new MigLayout("", "[grow][300px,grow][grow]", "[grow][][][][][][][grow]"));
		this.iconLabel = new JLabel();
		this.iconLabel.setIcon(ResourceHelper.getImageIconByName(SetupWizardPrivateCryptoPanel.RESOURCE___ICON));
		add(this.iconLabel, "cell 0 1 1 6,alignx right,gapx 0 40,aligny center");
		this.youLabel = new JLabel(this.localizationConnector.getLocalizedString("Your Key Pair"));
		add(this.youLabel, "cell 1 1,alignx center");
		this.separator = new JSeparator();
		add(this.separator, "cell 1 2,growx");
		this.generateKeyButton = new JButton(this.localizationConnector.getLocalizedString("Generate New Key"));
		this.generateKeyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				disableButtons();
				SetupWizardPrivateCryptoPanel.this.controller.generateNewPrivateKey();
			}
		});
		add(this.generateKeyButton, "cell 1 3,growx");
		this.restoreKeyButton = new JButton(this.localizationConnector.getLocalizedString("Restore Existing Key"));
		this.restoreKeyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				disableButtons();
				SetupWizardPrivateCryptoPanel.this.controller.restorePrivateKey();
			}
		});
		add(this.restoreKeyButton, "cell 1 4,growx");
		this.backupPublicKeyButton = new JButton(this.localizationConnector.getLocalizedString("Backup Key"));
		this.backupPublicKeyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SetupWizardPrivateCryptoPanel.this.controller.backupOwnPrivateKey();
			}
		});
		add(this.backupPublicKeyButton, "cell 1 5,growx,aligny top");
		this.reloadButton = new JButton("Reload");
		this.reloadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SetupWizardPrivateCryptoPanel.this.controller.readConfig();
			}
		});
		add(this.reloadButton, "cell 1 6,growx");
	}

	/**
	 * Sets the key state.
	 *
	 * @param hasKey the new key state
	 */
	public void setKeyState(final boolean hasKey) {
		this.generateKeyButton.setEnabled(!hasKey);
		this.restoreKeyButton.setEnabled(!hasKey);
		this.backupPublicKeyButton.setEnabled(hasKey);
		this.reloadButton.setEnabled(false);
	}
}
