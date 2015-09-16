package controlinterface.swinguiadvanced.view.panel.setupwizard;

import helper.ResourceHelper;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;
import framework.control.LocalizationConnector;

/**
 * Setup Wizard slide to select local folder for synchronization.
 *
 * @author Stefan Werner
 */
public class SetupWizardSelectFolderPanel extends JPanel {

	private static final long serialVersionUID = -2197084792016340789L;
	private static final String RESOURCE___ICON = "icons/controlinterface/swingadvanced/setupwizzard/config1.png";

	private final LocalizationConnector localizationConnector;
	private JLabel selectedLocalFolderLabel;
	private JTextField selectedFolderTextField;
	private JButton selectFolderButton;
	private JLabel iconLabel;

	/**
	 * Instantiates a new setup wizard select folder panel slide.
	 *
	 * @param localizationConnector the localization connector
	 */
	public SetupWizardSelectFolderPanel(final LocalizationConnector localizationConnector) {
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Gets the selected folder.
	 *
	 * @return the selected folder
	 */
	public String getSelectedFolder() {
		return this.selectedFolderTextField.getText();
	}

	/**
	 * Initializes the slide.
	 */
	private void initialize() {
		setLayout(new MigLayout("flowy", "[grow][300px,grow][grow]", "[grow][][grow]"));
		this.iconLabel = new JLabel();
		this.iconLabel.setIcon(ResourceHelper.getImageIconByName(SetupWizardSelectFolderPanel.RESOURCE___ICON));
		add(this.iconLabel, "cell 0 1,gapx 0 40,alignx right,aligny center");
		this.selectFolderButton = new JButton(this.localizationConnector.getLocalizedString("Select Folder"));
		this.selectFolderButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				selectFolder();
			}
		});
		this.selectedLocalFolderLabel = new JLabel(this.localizationConnector.getLocalizedString("Selected Local Folder:"));
		add(this.selectedLocalFolderLabel, "cell 1 1");
		this.selectedFolderTextField = new JTextField();
		this.selectedFolderTextField.setEditable(false);
		add(this.selectedFolderTextField, "cell 1 1,growx");
		this.selectedFolderTextField.setColumns(10);
		add(this.selectFolderButton, "cell 1 1,growx");
	}

	/**
	 * Selects a folder.
	 */
	private void selectFolder() {
		JFileChooser fc;
		if (this.selectedFolderTextField.getText() != null) {
			fc = new JFileChooser(this.selectedFolderTextField.getText());
		} else {
			fc = new JFileChooser();
		}
		fc.setDialogTitle(this.localizationConnector.getLocalizedString("Select Folder"));
		fc.setAcceptAllFileFilterUsed(false);
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			this.selectedFolderTextField.setText(fc.getSelectedFile().toString());
		}
	}

	/**
	 * Sets the selected folder.
	 *
	 * @param pathName the selected folder
	 */
	public void setSelectedFolder(final String pathName) {
		if (pathName != null) {
			this.selectedFolderTextField.setText(pathName);
		}
	}
}
