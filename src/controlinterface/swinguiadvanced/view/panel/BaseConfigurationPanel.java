package controlinterface.swinguiadvanced.view.panel;

import java.util.HashSet;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ListSelectionModel;

import net.miginfocom.swing.MigLayout;

import com.google.common.collect.ImmutableSet;

import framework.model.summary.BaseConfigurationSummary;
import framework.model.summary.ControlInterfaceSummary;
import framework.model.summary.ModuleSummary;

/**
 * Panel to visualize a base configuration summary and to let the user select parts of it.
 *
 * @author Stefan Werner
 */
public class BaseConfigurationPanel extends JPanel {

	private static final long serialVersionUID = 4007040202672535541L;

	private final BaseConfigurationSummary bcSummary;
	private JList<ControlInterfaceSummary> ciList;
	private JScrollPane cisScrollPane;
	private JCheckBox connectionsCheckBox;
	private JLabel controlInterfacesLabel;
	private JLabel modulesLabel;
	private JList<ModuleSummary> modulesList;
	private JScrollPane modulesScrollPane;
	private JSeparator separator;

	/**
	 * Instantiates a new base configuration panel.
	 *
	 * @param baseConfigruationSummary the summary to visualize
	 */
	public BaseConfigurationPanel(final BaseConfigurationSummary baseConfigruationSummary) {
		this.bcSummary = baseConfigruationSummary;
		initialize();
		parseSummary();
	}

	/**
	 * Gets the selected control interface IDs.
	 *
	 * @return the selected IDs
	 */
	public Set<String> getSelectedCIIds() {
		final Set<String> ciIds = new HashSet<String>();
		if (this.ciList.getSelectedValuesList() != null) {
			for (final ControlInterfaceSummary summary : this.ciList.getSelectedValuesList()) {
				ciIds.add(summary.getCiId());
			}
		}
		return ImmutableSet.copyOf(ciIds);
	}

	/**
	 * Gets the selected module IDs.
	 *
	 * @return the selected IDs
	 */
	public Set<String> getSelectedModuleIds() {
		final Set<String> moduleIds = new HashSet<String>();
		if (this.modulesList.getSelectedValuesList() != null) {
			for (final ModuleSummary summary : this.modulesList.getSelectedValuesList()) {
				moduleIds.add(summary.getModuleId());
			}
		}
		return ImmutableSet.copyOf(moduleIds);
	}

	/**
	 * Initializes the panel.
	 */
	private void initialize() {
		setLayout(new MigLayout("", "[grow]", "[grow][][][grow]"));
		this.modulesScrollPane = new JScrollPane();
		add(this.modulesScrollPane, "cell 0 0,grow");
		this.modulesLabel = new JLabel("Modules");
		this.modulesScrollPane.setColumnHeaderView(this.modulesLabel);
		this.modulesList = new JList<ModuleSummary>();
		this.modulesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		this.modulesScrollPane.setViewportView(this.modulesList);
		this.connectionsCheckBox = new JCheckBox("Connections between selected modules");
		add(this.connectionsCheckBox, "cell 0 1");
		this.separator = new JSeparator();
		add(this.separator, "cell 0 2,growx");
		this.cisScrollPane = new JScrollPane();
		add(this.cisScrollPane, "cell 0 3,grow");
		this.controlInterfacesLabel = new JLabel("Control Interfaces");
		this.cisScrollPane.setColumnHeaderView(this.controlInterfacesLabel);
		this.ciList = new JList<ControlInterfaceSummary>();
		this.ciList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		this.cisScrollPane.setViewportView(this.ciList);
	}

	/**
	 * Checks if module connections are selected
	 *
	 * @return true, if module connections are selected
	 */
	public boolean isConnectionsSelected() {
		return this.connectionsCheckBox.isSelected();
	}

	/**
	 * Parses the base configuration summary.
	 */
	private void parseSummary() {
		if (this.bcSummary == null) {
			return;
		}
		if (this.bcSummary.getModuleIds() != null) {
			this.modulesList.setListData(this.bcSummary.getModuleIds().toArray(new ModuleSummary[0]));
		}
		if (this.bcSummary.getCiIds() != null) {
			this.ciList.setListData(this.bcSummary.getCiIds().toArray(new ControlInterfaceSummary[0]));
		}
		if (!this.bcSummary.hasPortConnections()) {
			this.connectionsCheckBox.setEnabled(false);
		}
	}
}