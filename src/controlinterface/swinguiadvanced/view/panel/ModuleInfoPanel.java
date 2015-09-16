package controlinterface.swinguiadvanced.view.panel;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface;
import framework.control.LocalizationConnector;
import framework.model.summary.ModuleSummary;

/**
 * Provides context information on a selected module in the advanced CI.
 *
 * @author Stefan Werner
 */
public class ModuleInfoPanel extends JPanel {

	private static final long serialVersionUID = -8617065043542879808L;

	private final SwingAdvancedControlInterface controller;
	private JLabel idLabel;
	private JLabel idTextLabel;
	private final LocalizationConnector localizationConnector;
	private ModuleCISupportedCommandsPanel moduleCISupportedCommandsPanel;
	private JLabel moduleLabel;
	private ModuleSummary moduleSummary;
	private JLabel nameLabel;
	private JLabel NameTextLabel;
	private JButton removeButton;
	private JButton renameButton;
	private JLabel typeLabel;
	private JLabel typeTextLabel;

	/**
	 * Create the panel.
	 *
	 * @param moduleSummary the module summary
	 * @param controller the advanced CI controller
	 * @param localizationConnector the localization connector
	 */
	public ModuleInfoPanel(final ModuleSummary moduleSummary, final SwingAdvancedControlInterface controller, final LocalizationConnector localizationConnector) {
		this.controller = controller;
		this.localizationConnector = localizationConnector;
		initialize();
		updateData(moduleSummary);
	}

	/**
	 * Initializes the panel.
	 */
	private void initialize() {
		setLayout(new MigLayout("", "[grow]", "[][][][][grow][][]"));
		this.moduleLabel = new JLabel(this.localizationConnector.getLocalizedString("Module"));
		this.moduleLabel.setFont(new Font("Dialog", Font.BOLD, 12));
		add(this.moduleLabel, "cell 0 0");
		this.nameLabel = new JLabel(this.localizationConnector.getLocalizedString("Name: "));
		add(this.nameLabel, "flowx,cell 0 1");
		this.NameTextLabel = new JLabel();
		add(this.NameTextLabel, "cell 0 1");
		this.idLabel = new JLabel(this.localizationConnector.getLocalizedString("ID:"));
		add(this.idLabel, "flowx,cell 0 2");
		this.idTextLabel = new JLabel();
		add(this.idTextLabel, "cell 0 2");
		this.typeLabel = new JLabel(this.localizationConnector.getLocalizedString("Type: "));
		add(this.typeLabel, "flowx,cell 0 3");
		this.typeTextLabel = new JLabel();
		add(this.typeTextLabel, "cell 0 3");
		this.moduleCISupportedCommandsPanel = new ModuleCISupportedCommandsPanel(this.moduleSummary, this.controller, this.localizationConnector);
		add(this.moduleCISupportedCommandsPanel, "cell 0 4,grow");
		this.renameButton = new JButton(this.localizationConnector.getLocalizedString("Rename"));
		this.renameButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				ModuleInfoPanel.this.controller.renameModule(ModuleInfoPanel.this.moduleSummary);
			}
		});
		add(this.renameButton, "cell 0 5,growx");
		this.removeButton = new JButton(this.localizationConnector.getLocalizedString("Remove"));
		this.removeButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				ModuleInfoPanel.this.controller.removeModule(ModuleInfoPanel.this.moduleSummary);
			}
		});
		add(this.removeButton, "cell 0 6,growx");
	}

	/**
	 * Updates data.
	 *
	 * @param moduleSummary the module summary
	 */
	public void updateData(final ModuleSummary moduleSummary) {
		this.moduleSummary = moduleSummary;
		this.moduleCISupportedCommandsPanel.updateData(moduleSummary);
		this.NameTextLabel.setText(moduleSummary.getModuleName());
		this.idTextLabel.setText(moduleSummary.getModuleId());
		this.typeTextLabel.setText(moduleSummary.getModuleType());
	}
}
