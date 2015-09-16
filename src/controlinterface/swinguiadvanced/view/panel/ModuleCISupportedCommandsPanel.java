package controlinterface.swinguiadvanced.view.panel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface;
import framework.control.LocalizationConnector;
import framework.model.summary.ModuleSummary;

/**
 * Panel to display supported commands of a module and to let the user access them.
 *
 * @author Stefan Werner
 */
public class ModuleCISupportedCommandsPanel extends JPanel {

	private static final long serialVersionUID = 6121015965081945735L;

	private JList<String> commandList;
	private Map<String, String> commandMap;
	private final SwingAdvancedControlInterface controller;
	private final LocalizationConnector localizationConnector;
	private JButton refreshButton;
	private JScrollPane scrollPane;
	private JButton sendButton;
	private JButton sendWithPropsButton;
	private ModuleSummary summary;
	private JLabel supportedModuleCommandsLabel;

	/**
	 * Creates the panel.
	 *
	 * @param summary the module summary
	 * @param controller the advanced CI controller
	 * @param localizationConnector the localization connector
	 */
	public ModuleCISupportedCommandsPanel(final ModuleSummary summary, final SwingAdvancedControlInterface controller, final LocalizationConnector localizationConnector) {
		this.controller = controller;
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Initializes the panel.
	 */
	private void initialize() {
		setLayout(new MigLayout("ins 0", "[grow]", "[][grow][]"));
		this.supportedModuleCommandsLabel = new JLabel(this.localizationConnector.getLocalizedString("Supported Module Commands:"));
		add(this.supportedModuleCommandsLabel, "cell 0 0");
		this.scrollPane = new JScrollPane();
		add(this.scrollPane, "cell 0 1,grow");
		this.commandList = new JList<String>();
		this.commandList.addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(final ListSelectionEvent arg0) {
				if (ModuleCISupportedCommandsPanel.this.commandList.getSelectedIndex() < 0) {
					ModuleCISupportedCommandsPanel.this.sendButton.setEnabled(false);
					ModuleCISupportedCommandsPanel.this.sendWithPropsButton.setEnabled(false);
				} else {
					ModuleCISupportedCommandsPanel.this.sendButton.setEnabled(true);
					ModuleCISupportedCommandsPanel.this.sendWithPropsButton.setEnabled(true);
				}
			}
		});
		this.commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		this.scrollPane.setViewportView(this.commandList);
		this.sendButton = new JButton(this.localizationConnector.getLocalizedString("Send"));
		this.sendButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				final String command = ModuleCISupportedCommandsPanel.this.commandList.getSelectedValue();
				if (command != null) {
					ModuleCISupportedCommandsPanel.this.controller.sendCiCommand(ModuleCISupportedCommandsPanel.this.summary, ModuleCISupportedCommandsPanel.this.commandMap.get(command), false);
				}
			}
		});
		this.sendButton.setEnabled(false);
		add(this.sendButton, "flowx,cell 0 2,growx");
		this.sendWithPropsButton = new JButton(this.localizationConnector.getLocalizedString("Send with Properties"));
		this.sendWithPropsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				final String command = ModuleCISupportedCommandsPanel.this.commandList.getSelectedValue();
				if (command != null) {
					ModuleCISupportedCommandsPanel.this.controller.sendCiCommand(ModuleCISupportedCommandsPanel.this.summary, ModuleCISupportedCommandsPanel.this.commandMap.get(command), true);
				}
			}
		});
		this.sendWithPropsButton.setEnabled(false);
		add(this.sendWithPropsButton, "flowx,cell 0 2,growx");
		this.refreshButton = new JButton(this.localizationConnector.getLocalizedString("Refresh"));
		this.refreshButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				updateCommands(true);
			}
		});
		add(this.refreshButton, "cell 0 2,growx");
	}

	/**
	 * Localizes module commands.
	 *
	 * @param commands the commands to localize
	 * @return the map of original and localized commands
	 */
	private Map<String, String> localizeCommands(final Set<String> commands) {
		if (commands == null) {
			return null;
		}
		final Map<String, String> result = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		for (final String command : commands) {
			result.put(this.localizationConnector.getLocalizedString(command), command);
		}
		return result;
	}

	/**
	 * Updates commands.
	 *
	 * @param forceRefresh set to true to force refresh
	 */
	private void updateCommands(final boolean forceRefresh) {
		this.sendButton.setEnabled(false);
		this.sendWithPropsButton.setEnabled(false);
		this.commandMap = localizeCommands(this.controller.getSupportedCiCommands(this.summary, forceRefresh));
		if (this.commandMap != null) {
			this.commandList.setListData(this.commandMap.keySet().toArray(new String[0]));
			this.commandList.setSelectedIndex(-1);
			this.commandList.setEnabled(true);
		} else {
			this.commandList.setEnabled(false);
		}
	}

	/**
	 * Updates data.
	 *
	 * @param summary the module summary of the selected module
	 */
	public void updateData(final ModuleSummary summary) {
		this.summary = summary;
		updateCommands(false);
	}

}
