package controlinterface.swinguiadvanced.view.panel;

import helper.ConfigValue;

import java.awt.BorderLayout;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

/**
 * Container for a set of {@link ConfigValuePanel ConfigValuePanels}. Used inside of the advanced CI.
 *
 * @author Stefan Werner
 */
public class ConfigPanel extends JPanel {

	private static final long serialVersionUID = -8233835279394151091L;

	private final Set<ConfigValuePanel> configValuePanels = new HashSet<ConfigValuePanel>();
	private final JPanel parentPanel;
	private final Map<String, String> properties;
	private final JScrollPane scrollPane;

	/**
	 * Instantiates a new configuration panel.
	 *
	 * @param properties the properties
	 */
	public ConfigPanel(final Map<String, String> properties) {
		this.properties = properties;
		setLayout(new BorderLayout(0, 0));
		this.scrollPane = new JScrollPane();
		this.scrollPane.setBorder(null);
		add(this.scrollPane, BorderLayout.CENTER);
		this.parentPanel = new JPanel();
		this.scrollPane.setViewportView(this.parentPanel);
		this.parentPanel.setLayout(new MigLayout("flowy", "[grow]", "[grow]"));
		final Iterator<String> iter = properties.keySet().iterator();
		while (iter.hasNext()) {
			final String key = iter.next();
			final String value = properties.get(key);
			if (value != null) {
				final ConfigValue configValue = new ConfigValue(key, value);
				if (configValue.isValid()) {
					final ConfigValuePanel panel = new ConfigValuePanel(configValue);
					// panel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
					this.parentPanel.add(panel, "cell 0 0,grow");
					this.configValuePanels.add(panel);
					if (iter.hasNext()) {
						this.parentPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "cell 0 0,growx");
					}
				}
			}
		}
		if (this.configValuePanels.isEmpty()) {
			final JLabel infoLabel = new JLabel("No config values found.");
			this.parentPanel.add(infoLabel, "cell 0 0,grow,alignx center");
		}
	}

	/**
	 * Gets the updated properties based on alterations by the user.
	 *
	 * @return the updated properties
	 */
	public Map<String, String> getUpdatedProperties() {
		for (final ConfigValuePanel panel : this.configValuePanels) {
			final ConfigValue configValue = panel.getUpdatedConfigValue();
			this.properties.put(configValue.getKey(), configValue.toString());
		}
		return this.properties;
	}
}
