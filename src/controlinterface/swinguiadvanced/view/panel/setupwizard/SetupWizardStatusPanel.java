package controlinterface.swinguiadvanced.view.panel.setupwizard;

import helper.ResourceHelper;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingSimpleControlWrapper;
import controlinterface.swinguiadvanced.view.other.LimitLinesDocumentListener;
import controlinterface.swinguiadvanced.view.other.SmartScroller;
import framework.control.LocalizationConnector;
import framework.model.event.type.ConnectionEventType;
import framework.model.event.type.SystemStateType;

/**
 * Last Setup Wizard slide to manage system after setup is complete.
 *
 * @author Stefan Werner
 */
public class SetupWizardStatusPanel extends JPanel {

	private static final int MAX_INFO_LINES = 50;
	private static final String RESOURCE___ICON = "icons/controlinterface/swingadvanced/setupwizzard/icon.png";
	private static final long serialVersionUID = 2790247863121287620L;

	private JLabel activityLabel;
	private final SwingSimpleControlWrapper controller;
	private Document document;
	private LimitLinesDocumentListener documentListener;
	private final String errorText;
	private JLabel iconLabel;
	private final String idleText;
	private JLabel label;
	private JTextArea latestModificationsTextArea;
	private JLabel latestModificationsTextLabel;
	private final LocalizationConnector localizationConnector;
	private final String runningText;
	private JScrollPane scrollPane;
	private JButton startButton;
	private final String startingText;
	private JLabel stateLabel;
	private JLabel stateTextlabel;
	private JButton stopButton;
	private final String stoppedText;
	private final String stoppingText;
	private final String workingText;

	/**
	 * Instantiates a new setup wizard status panel slide.
	 *
	 * @param wrapper the simple ci wrapper
	 * @param localizationConnector the localization connector
	 */
	public SetupWizardStatusPanel(final SwingSimpleControlWrapper wrapper, final LocalizationConnector localizationConnector) {
		this.controller = wrapper;
		this.localizationConnector = localizationConnector;
		this.runningText = localizationConnector.getLocalizedString("Running");
		this.stoppedText = localizationConnector.getLocalizedString("Stopped");
		this.startingText = localizationConnector.getLocalizedString("Starting");
		this.stoppingText = localizationConnector.getLocalizedString("Stopping");
		this.errorText = localizationConnector.getLocalizedString("Error");
		this.workingText = localizationConnector.getLocalizedString("Working");
		this.idleText = localizationConnector.getLocalizedString("Idle");
		initialize();
	}

	/**
	 * Adds an info entry.
	 *
	 * @param entry the entry to add
	 */
	public synchronized void addInfoEntry(final String entry) {
		try {
			this.document.insertString(0, entry + "\n", null);
		} catch (final BadLocationException e) {
			// ignored
		}
	}

	/**
	 * Initializes the slide.
	 */
	private void initialize() {
		setLayout(new MigLayout("", "[grow][grow 300][grow]", "[grow][][][][][][200][grow]"));
		this.stopButton = new JButton(this.localizationConnector.getLocalizedString("Stop"));
		this.stopButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SetupWizardStatusPanel.this.controller.stopSystem();
			}
		});
		this.startButton = new JButton(this.localizationConnector.getLocalizedString("Start"));
		this.startButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SetupWizardStatusPanel.this.controller.startSystem();
			}
		});
		this.iconLabel = new JLabel();
		this.iconLabel.setIcon(ResourceHelper.getImageIconByName(SetupWizardStatusPanel.RESOURCE___ICON));
		add(this.iconLabel, "cell 1 0,alignx center,aligny center");
		this.stateTextlabel = new JLabel(this.localizationConnector.getLocalizedString("Current State:"));
		this.stateTextlabel.setFont(new Font("Dialog", Font.BOLD, 13));
		add(this.stateTextlabel, "flowx,cell 1 1");
		this.stateLabel = new JLabel(this.stoppedText);
		this.stateLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		add(this.stateLabel, "cell 1 1");
		this.label = new JLabel(" - ");
		this.label.setFont(new Font("Dialog", Font.BOLD, 13));
		add(this.label, "cell 1 1");
		this.activityLabel = new JLabel(this.idleText);
		this.activityLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		add(this.activityLabel, "cell 1 1");
		JSeparator separator = new JSeparator();
		add(separator, "cell 1 2,growx");
		add(this.startButton, "flowx,cell 1 3,growx");
		add(this.stopButton, "cell 1 3,growx");
		separator = new JSeparator();
		add(separator, "cell 1 4,growx");
		this.latestModificationsTextLabel = new JLabel(this.localizationConnector.getLocalizedString("Latest Modifications:"));
		add(this.latestModificationsTextLabel, "cell 1 5");
		this.scrollPane = new JScrollPane();
		add(this.scrollPane, "cell 1 6,grow");
		this.latestModificationsTextArea = new JTextArea();
		this.document = this.latestModificationsTextArea.getDocument();
		this.latestModificationsTextArea.setBackground(Color.WHITE);
		this.latestModificationsTextArea.setEditable(false);
		this.documentListener = new LimitLinesDocumentListener(SetupWizardStatusPanel.MAX_INFO_LINES, false);
		this.latestModificationsTextArea.getDocument().addDocumentListener(this.documentListener);
		new SmartScroller(this.scrollPane, SmartScroller.VERTICAL, SmartScroller.START);
		this.scrollPane.setViewportView(this.latestModificationsTextArea);
	}

	/**
	 * Sets the current activity.
	 *
	 * @param type the new activity
	 */
	public void setActivity(final ConnectionEventType type) {
		switch (type) {
		case BUSY:
			this.activityLabel.setText(this.workingText);
			break;
		default:
			this.activityLabel.setText(this.idleText);
			break;
		}
	}

	/**
	 * Sets the current state.
	 *
	 * @param stateType the new state
	 */
	public void setState(final SystemStateType stateType) {
		switch (stateType) {
		case BROKER_RUNNING:
			this.stopButton.setEnabled(true);
			this.startButton.setEnabled(false);
			this.stateLabel.setText(this.runningText);
			break;
		case BROKER_STOPPED_AND_READY:
			this.stopButton.setEnabled(false);
			this.startButton.setEnabled(true);
			this.stateLabel.setText(this.stoppedText);
			break;
		case BROKER_STARTING_UP:
			this.stopButton.setEnabled(false);
			this.startButton.setEnabled(false);
			this.stateLabel.setText(this.startingText);
			break;
		case BROKER_SHUTTING_DOWN:
			this.stopButton.setEnabled(false);
			this.startButton.setEnabled(false);
			this.stateLabel.setText(this.stoppingText);
			break;
		case SYSTEM_OR_BROKER_ERROR:
			this.stopButton.setEnabled(false);
			this.startButton.setEnabled(true);
			this.stateLabel.setText(this.errorText);
			break;
		default:
			this.stopButton.setEnabled(false);
			this.startButton.setEnabled(false);
			this.stateLabel.setText(this.stoppedText);
			break;
		}
	}
}
