package controlinterface.swinguiadvanced.view.panel.setupwizard;

import java.awt.Color;
import java.awt.Font;
import java.awt.SystemColor;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.border.MatteBorder;

import net.miginfocom.swing.MigLayout;
import framework.constants.Constants;
import framework.control.LocalizationConnector;

/**
 * Generic Setup Wizard slide wrapper for texts/images.
 *
 * @author Stefan Werner
 */
public class SetupWizardStepPanel extends JPanel {

	private static final Color LIGHT_BG_COLOR = SetupWizardStepPanel.LIGHT_FG_COLOR;
	private static final Color LIGHT_FG_COLOR = Color.WHITE;
	private static final long serialVersionUID = -605422200117309007L;

	private JLabel headingLabel;
	private JPanel indexPanel;
	private JLabel stepNumberLabel;
	private JTextPane textArea;
	private final JPanel textPanel;

	/**
	 * Instantiates a new setup wizard step panel slide.
	 *
	 * @param index the index number
	 * @param heading the heading
	 * @param text the text
	 * @param component the component to include in the slide
	 * @param isConfigSlide true, if user config (user input) slide (text background will be dark)
	 * @param localizationConnector the localization connector
	 */
	public SetupWizardStepPanel(String index, String heading, String text, final JComponent component, final boolean isConfigSlide, final LocalizationConnector localizationConnector) {
		index = localizationConnector.getLocalizedString(index);
		heading = localizationConnector.getLocalizedString(heading);
		text = localizationConnector.getLocalizedString(text);
		setLayout(new MigLayout("ins 0", "[grow]", "[][grow]"));
		this.textPanel = new JPanel();
		this.textPanel.setBackground(Color.WHITE);
		this.textPanel.setBorder(new MatteBorder(0, 0, 1, 0, SystemColor.windowBorder));
		this.textPanel.setLayout(new MigLayout("ins 0", "[][grow]", "[][grow,fill]"));
		if (isConfigSlide) {
			this.textPanel.setBackground(Constants.DARK_BG_COLOR);
		}
		add(this.textPanel, "cell 0 0,grow");
		if (index != null) {
			this.indexPanel = new JPanel();
			this.indexPanel.setBackground(Constants.DARK_BG_COLOR);
			this.textPanel.add(this.indexPanel, "flowx,cell 0 0 1 2,growy");
			this.stepNumberLabel = new JLabel(index);
			this.stepNumberLabel.setForeground(Color.WHITE);
			this.indexPanel.add(this.stepNumberLabel);
			this.stepNumberLabel.setFont(new Font("Dialog", Font.PLAIN, 50));
		}
		if (heading != null) {
			this.headingLabel = new JLabel(heading);
			this.headingLabel.setFont(new Font("Dialog", Font.BOLD, 16));
			if (isConfigSlide) {
				this.headingLabel.setForeground(SetupWizardStepPanel.LIGHT_FG_COLOR);
			}
			this.textPanel.add(this.headingLabel, "cell 1 0,gapx 5 10,gapy 5 0");
		}
		if (text != null) {
			this.textArea = new JTextPane();
			this.textArea.setEditable(false);
			if (isConfigSlide) {
				this.textArea.setBackground(Constants.DARK_BG_COLOR);
				this.textArea.setForeground(SetupWizardStepPanel.LIGHT_FG_COLOR);
			} else {
				this.textArea.setBackground(SetupWizardStepPanel.LIGHT_BG_COLOR);
			}
			this.textPanel.add(this.textArea, "cell 1 1,gapx 0 10,gapy 0 5");
			this.textArea.setText(text);
		}
		if (component != null) {
			add(component, "cell 0 1,alignx center,aligny center");
		}
		setVisible(true);
	}
}
