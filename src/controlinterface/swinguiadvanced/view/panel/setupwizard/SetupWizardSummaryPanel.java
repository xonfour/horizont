package controlinterface.swinguiadvanced.view.panel.setupwizard;

import helper.ResourceHelper;

import java.awt.Component;
import java.awt.Font;
import java.util.List;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingSimpleControlWrapper;
import framework.control.LocalizationConnector;

/**
 * Last Setup Wizard slide to manage system after setup is complete.
 *
 * @author Stefan Werner
 */
public class SetupWizardSummaryPanel extends JPanel {

	private static final String RESOURCE___ICON_ERROR = "icons/error_l.png";
	private static final String RESOURCE___ICON_OK = "icons/ok_l.png";
	private static final long serialVersionUID = -6827310189075123481L;

	private ImageIcon iconError;
	private JLabel iconLabel;
	private ImageIcon iconOK;
	private final LocalizationConnector localizationConnector;
	private JTextPane summaryPane;
	private Component verticalStrut;

	/**
	 * Instantiates a new setup wizard summary panel slide.
	 *
	 * @param controller the controller
	 * @param localizationConnector the localization connector
	 */
	public SetupWizardSummaryPanel(final SwingSimpleControlWrapper controller, final LocalizationConnector localizationConnector) {
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Initializes the slide.
	 */
	private void initialize() {
		setLayout(new MigLayout("flowy", "[grow][][grow]", "[grow]"));
		this.iconLabel = new JLabel();
		this.iconError = ResourceHelper.getImageIconByName(SetupWizardSummaryPanel.RESOURCE___ICON_ERROR);
		this.iconOK = ResourceHelper.getImageIconByName(SetupWizardSummaryPanel.RESOURCE___ICON_OK);
		add(this.iconLabel, "cell 1 0,alignx center,aligny center");
		this.verticalStrut = Box.createVerticalStrut(20);
		add(this.verticalStrut, "cell 1 0");
		this.summaryPane = new JTextPane();
		this.summaryPane.setEditable(false);
		this.summaryPane.setFont(new Font("Dialog", Font.PLAIN, 18));
		add(this.summaryPane, "cell 1 0,alignx center,aligny center");
	}

	/**
	 * Sets a list of error messages.
	 *
	 * @param errorLines the message list
	 */
	public void setMessages(final List<String> errorLines) {
		if ((errorLines == null) || errorLines.isEmpty()) {
			this.iconLabel.setIcon(this.iconOK);
			this.summaryPane.setText(this.localizationConnector.getLocalizedString("Everything set up. System is ready!"));
		} else {
			final StringBuilder sb = new StringBuilder();
			this.iconLabel.setIcon(this.iconError);
			//sb.append(this.localizationConnector.getLocalizedString("System not ready, please solve the following issues:\n\n"));
			for (final String s : errorLines) {
				sb.append(this.localizationConnector.getLocalizedString(s));
				sb.append("\n\n");
			}
			sb.deleteCharAt(sb.length() - 1);
			this.summaryPane.setText(sb.toString());
		}
	}
}
