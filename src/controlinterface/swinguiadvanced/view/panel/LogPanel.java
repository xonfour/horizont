package controlinterface.swinguiadvanced.view.panel;

import framework.control.LocalizationConnector;
import framework.model.event.ConnectionUpdateEvent;
import framework.model.event.LogEvent;
import framework.model.event.ModuleActivityEvent;
import framework.model.event.ModuleUpdateEvent;
import framework.model.event.PortUpdateEvent;
import framework.model.event.type.LogEventLevelType;
import helper.PersistentConfigurationHelper;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import net.miginfocom.swing.MigLayout;

import com.google.common.base.Throwables;

import controlinterface.swinguiadvanced.constants.SwingAdvancedConstants;
import controlinterface.swinguiadvanced.view.SwingAdvancedWindow;
import controlinterface.swinguiadvanced.view.other.LimitLinesDocumentListener;
import controlinterface.swinguiadvanced.view.other.SmartScroller;

/**
 * Panel to visualize all of the system's events and to let the user filter them.
 *
 * @author Stefan Werner
 */
public class LogPanel extends JScrollPane {

	private static final long serialVersionUID = -782788881568308945L;

	private JButton clearLogButton;
	private final Map<String, String> componentIdsAndNames;
	private final PersistentConfigurationHelper config;
	private JCheckBox debugMessagesCheckBox;
	private Style debugStyle;
	private Style defaultStyle;
	private StyledDocument document;
	private LimitLinesDocumentListener documentListener;
	private final ReentrantLock documentLock = new ReentrantLock(true);
	private JCheckBox errorMessagesCheckBox;
	private Style errorStyle;
	private Pattern filterRegExpPattern = null;
	private JTextField filterTextField;
	private JLabel filterTextLabel;
	private int fontSize;
	private JLabel fontSizeLabel;
	private JSpinner fontSizeSpinner;
	private Style headingStyle;
	private JCheckBox infoMessagesCheckBox;
	private Style infoStyle;
	private int lineLimit;
	private JLabel lineLimitLabel;
	private JSpinner lineLimitSpinner;
	private final LocalizationConnector localizationConnector;
	private JTextPane logTextPane;
	private JCheckBox moduleActionMessagesCheckBox;
	private final String originCU;
	private final String originMA;
	private final String originMU;
	private final String originPU;
	private Style ownMsgStyle;
	private JButton settingsButton;
	private JPanel settingsMenuPanel;
	private JPopupMenu settingsPopupMenu;
	private boolean showDebug;
	private boolean showError;
	private boolean showInfo;
	private boolean showModuleAction;
	private boolean showWarning;
	private JPanel toolbarPanel;
	private JCheckBox unlimitedCheckBox;
	private boolean unlimitedLineLimit;
	private JCheckBox warningMessagesCheckBox;
	private Style warningStyle;

	/**
	 * Instantiates a new log panel.
	 *
	 * @param componentIdsAndNames the component IDs and corresponding
	 * @param persistentConfigHelper the persistent config helper
	 * @param localizationConnector the localization connector
	 */
	public LogPanel(final Map<String, String> componentIdsAndNames, final PersistentConfigurationHelper persistentConfigHelper, final LocalizationConnector localizationConnector) {
		this.componentIdsAndNames = componentIdsAndNames;
		this.config = persistentConfigHelper;
		this.localizationConnector = localizationConnector;
		this.originCU = localizationConnector.getLocalizedString("CONNECTION UPDATE");
		this.originMA = localizationConnector.getLocalizedString("MODULE ACTION");
		this.originMU = localizationConnector.getLocalizedString("MODULE UPDATE");
		this.originPU = localizationConnector.getLocalizedString("PORT UPDATE");
		loadConfig();
		initialize();
	}

	/**
	 * Adds a connection update event.
	 *
	 * @param event the event
	 * @return true, if successful
	 */
	public boolean addConnectionUpdateEvent(final ConnectionUpdateEvent event) {
		if (event == null) {
			return false;
		}
		if (!this.showModuleAction) {
			return true;
		}
		this.documentLock.lock();
		boolean result;
		final String prosumerComponentName = getComponentNameOrId(event.connectionSummary.getProsumerPortSummary().getModuleId());
		final String providerComponentName = getComponentNameOrId(event.connectionSummary.getProviderPortSummary().getModuleId());
		final String origin = this.originCU + " - " + event.connectionSummary.getProsumerPortSummary().getPortId() + " (" + prosumerComponentName + ") <-> " + event.connectionSummary.getProviderPortSummary().getPortId() + " (" + providerComponentName + ")";
		final String msg = this.localizationConnector.getLocalizedString(event.type.name());
		result = addEntry(event.creationDate, origin, msg, this.defaultStyle);
		this.documentLock.unlock();
		return result;
	}

	/**
	 * Adds an log entry.
	 *
	 * @param date the date
	 * @param origin the origin
	 * @param msgList the list of messages
	 * @param style the style
	 * @return true, if successful
	 */
	private boolean addEntry(final long date, final String origin, final List<String> msgList, final Style style) {
		if ((origin == null) || (msgList == null)) {
			return false;
		}
		if ((this.filterRegExpPattern != null) && !this.filterRegExpPattern.matcher(origin).find()) {
			boolean show = false;
			for (final String line : msgList) {
				if (this.filterRegExpPattern.matcher(line).find()) {
					show = true;
					break;
				}
			}
			if (!show) {
				return false;
			}
		}
		try {
			final String dateString = this.localizationConnector.getFormatedDateDefault(date);
			final String prefix = dateString + " | ";
			final String whiteSpacePrefix = "    "; // Strings.repeat(" ", dateString.length()) + " | ";
			for (int i = msgList.size() - 1; i >= 0; i--) {
				this.document.insertString(0, msgList.get(i) + "\n", style);
				this.document.insertString(0, whiteSpacePrefix, this.document.getStyle(SwingAdvancedConstants.LOG_STYLE___DEFAULT));
			}
			this.document.insertString(0, origin + "\n", this.document.getStyle(SwingAdvancedConstants.LOG_STYLE___HEADING));
			this.document.insertString(0, prefix, this.document.getStyle(SwingAdvancedConstants.LOG_STYLE___DEFAULT));
			return true;
		} catch (final BadLocationException e) {
			return false;
		}
	}

	/**
	 * Adds an entry.
	 *
	 * @param date the date
	 * @param origin the origin
	 * @param msg the message
	 * @param style the style
	 * @return true, if successful
	 */
	private boolean addEntry(final long date, final String origin, final String msg, final Style style) {
		if ((origin == null) || (msg == null)) {
			return false;
		}
		final String msgLines[] = msg.split("\r?\n|\r");
		return addEntry(date, origin, Arrays.asList(msgLines), style);
	}

	/**
	 * Adds a log event.
	 *
	 * @param event the event
	 * @return true, if successful
	 */
	public boolean addLogEvent(final LogEvent event) {
		if (event == null) {
			return false;
		}
		if (((event.getLogLevel() == LogEventLevelType.DEBUG) && !this.showDebug) || ((event.getLogLevel() == LogEventLevelType.ERROR) && !this.showError) || ((event.getLogLevel() == LogEventLevelType.INFO) && !this.showInfo) || ((event.getLogLevel() == LogEventLevelType.WARNING) && !this.showWarning)) {
			return true;
		}
		this.documentLock.lock();
		boolean result = false;
		String origin;
		final String componentName = getComponentNameOrId(event.getComponentId());
		origin = event.getLogLevel().name() + " - " + event.getLogSource().name() + "/" + componentName;
		String msg;
		if (event.getException() != null) {
			msg = this.localizationConnector.getLocalizedString(event.getMessage()) + "\n" + Throwables.getStackTraceAsString(event.getException());
		} else {
			msg = this.localizationConnector.getLocalizedString(event.getMessage());
		}
		if ((event.getLogLevel() == LogEventLevelType.DEBUG) && this.showDebug) {
			result = addEntry(event.getCreationDate(), origin, msg, this.debugStyle);
		} else if ((event.getLogLevel() == LogEventLevelType.INFO) && this.showInfo) {
			result = addEntry(event.getCreationDate(), origin, msg, this.infoStyle);
		} else if ((event.getLogLevel() == LogEventLevelType.WARNING) && this.showWarning) {
			result = addEntry(event.getCreationDate(), origin, msg, this.warningStyle);
		} else if ((event.getLogLevel() == LogEventLevelType.ERROR) && this.showError) {
			result = addEntry(event.getCreationDate(), origin, msg, this.errorStyle);
		}
		this.documentLock.unlock();
		return result;
	}

	/**
	 * Adds a module action event.
	 *
	 * @param event the event
	 * @return true, if successful
	 */
	public boolean addModuleActionEvent(final ModuleActivityEvent event) {
		if (event == null) {
			return false;
		}
		if (!this.showModuleAction) {
			return true;
		}
		this.documentLock.lock();
		boolean result;
		String origin;
		final String componentName = getComponentNameOrId(event.getSendingModuleId());
		origin = this.originMA + " - " + componentName;
		final List<String> msgList = new ArrayList<String>();
		msgList.add(this.localizationConnector.getLocalizedString(event.getActivity()));
		final Map<String, Object> props = event.getProperties();
		if (props != null) {
			for (final String k : props.keySet()) {
				String v;
				try {
					final Object o = props.get(k);
					if ((o != null) && o.getClass().isArray()) {
						v = Arrays.toString((Object[]) o);
					} else {
						v = getComponentNameOrId(String.valueOf(o));
					}
				} catch (final Exception e) {
					v = e.getLocalizedMessage();
				}
				msgList.add(this.localizationConnector.getLocalizedString(k) + " -> " + v);
			}
		}
		result = addEntry(event.getCreationDate(), origin, msgList, this.defaultStyle);
		this.documentLock.unlock();
		return result;
	}

	/**
	 * Adds a module update event.
	 *
	 * @param event the event
	 * @return true, if successful
	 */
	public boolean addModuleUpdateEvent(final ModuleUpdateEvent event) {
		if (event == null) {
			return false;
		}
		if (!this.showModuleAction) {
			return true;
		}
		this.documentLock.lock();
		boolean result;
		final String origin = this.originMU + " - " + getComponentNameOrId(event.moduleSummary.getModuleId());
		final String msg = this.localizationConnector.getLocalizedString(event.type.name());
		result = addEntry(event.creationDate, origin, msg, this.defaultStyle);
		this.documentLock.unlock();
		return result;
	}

	/**
	 * Adds an own message (of the advanced CI itself).
	 *
	 * @param msg the message
	 * @return true, if successful
	 */
	public boolean addOwnMessage(final String msg) {
		if (msg == null) {
			return false;
		}
		this.documentLock.lock();
		final boolean result = addEntry(System.currentTimeMillis(), SwingAdvancedConstants.ORIGIN___OWN_MESSAGE, this.localizationConnector.getLocalizedString(msg), this.defaultStyle);
		this.documentLock.unlock();
		return result;
	}

	/**
	 * Adds a port update event.
	 *
	 * @param event the event
	 * @return true, if successful
	 */
	public boolean addPortUpdateEvent(final PortUpdateEvent event) {
		if (event == null) {
			return false;
		}
		if (!this.showModuleAction) {
			return true;
		}
		this.documentLock.lock();
		boolean result;
		final String componentName = getComponentNameOrId(event.portSummary.getModuleId());
		final String origin = this.originPU + " - " + event.portSummary.getPortId() + " (" + componentName + ")";
		final String msg = this.localizationConnector.getLocalizedString(event.type.name());
		result = addEntry(event.creationDate, origin, msg, this.defaultStyle);
		this.documentLock.unlock();
		return result;
	}

	/**
	 * Adds all the styles.
	 *
	 * @param doc the document to add styles to
	 */
	// TODO: Use better styles/formating for log output.
	private void addStyles(final StyledDocument doc) {
		this.defaultStyle = doc.addStyle(SwingAdvancedConstants.LOG_STYLE___DEFAULT, null);
		StyleConstants.setForeground(this.defaultStyle, Color.LIGHT_GRAY);
		this.debugStyle = doc.addStyle(SwingAdvancedConstants.LOG_STYLE___DEBUG, null);
		StyleConstants.setForeground(this.debugStyle, Color.DARK_GRAY);
		this.infoStyle = doc.addStyle(SwingAdvancedConstants.LOG_STYLE___INFO, null);
		StyleConstants.setForeground(this.infoStyle, Color.LIGHT_GRAY);
		this.warningStyle = doc.addStyle(SwingAdvancedConstants.LOG_STYLE___WARNING, null);
		StyleConstants.setForeground(this.warningStyle, Color.YELLOW);
		this.errorStyle = doc.addStyle(SwingAdvancedConstants.LOG_STYLE___ERROR, null);
		StyleConstants.setForeground(this.errorStyle, Color.RED);
		this.ownMsgStyle = doc.addStyle(SwingAdvancedConstants.LOG_STYLE___OWN_MSG, null);
		StyleConstants.setForeground(this.ownMsgStyle, Color.WHITE);
		StyleConstants.setBold(this.ownMsgStyle, true);
		this.headingStyle = doc.addStyle(SwingAdvancedConstants.LOG_STYLE___HEADING, null);
		StyleConstants.setForeground(this.headingStyle, Color.WHITE);
		StyleConstants.setUnderline(this.headingStyle, true);
	}

	/**
	 * Gets the component name (or ID if name unknown).
	 *
	 * @param componentId the component id
	 * @return the component name or id
	 */
	public String getComponentNameOrId(final String componentId) {
		final String result = this.componentIdsAndNames.get(componentId);
		if (result == null) {
			return componentId;
		} else {
			return result + " - " + componentId;
		}
	}

	/**
	 * Initializes the panel.
	 */
	private void initialize() {
		this.logTextPane = new JTextPane();
		setViewportView(this.logTextPane);
		this.logTextPane.setEditable(false);
		this.logTextPane.setBackground(Color.BLACK);
		this.logTextPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, this.fontSize));
		this.document = this.logTextPane.getStyledDocument();
		addStyles(this.document);
		this.documentListener = new LimitLinesDocumentListener(this.lineLimit, false);
		this.logTextPane.getDocument().addDocumentListener(this.documentListener);
		new SmartScroller(this, SmartScroller.VERTICAL, SmartScroller.START);
		this.toolbarPanel = new JPanel();
		setColumnHeaderView(this.toolbarPanel);
		this.toolbarPanel.setBackground(Color.WHITE);
		this.toolbarPanel.setLayout(new MigLayout("ins 2", "[][grow,right]", "[]"));
		this.lineLimitLabel = new JLabel(this.localizationConnector.getLocalizedString("Line Limit:"));
		this.toolbarPanel.add(this.lineLimitLabel, "cell 0 0");
		this.lineLimitSpinner = new JSpinner();
		this.toolbarPanel.add(this.lineLimitSpinner, "cell 0 0,width 100px:100px:100px");
		this.lineLimitSpinner.setModel(new SpinnerNumberModel(this.lineLimit, 1, Integer.MAX_VALUE, 1));
		this.lineLimitSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent arg0) {
				updateLineLimit();
			}
		});
		this.unlimitedCheckBox = new JCheckBox(this.localizationConnector.getLocalizedString("unlimited"), this.unlimitedLineLimit);
		this.toolbarPanel.add(this.unlimitedCheckBox, "cell 0 0");
		this.unlimitedCheckBox.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent arg0) {
				updateLineLimit();
			}
		});
		updateLineLimit();
		this.clearLogButton = new JButton(this.localizationConnector.getLocalizedString("Clear"));
		this.toolbarPanel.add(this.clearLogButton, "cell 1 0,growy");
		this.clearLogButton.setIcon(new ImageIcon(SwingAdvancedWindow.class.getResource("/icons/controlinterface/swingadvanced/button_clear.png")));
		this.clearLogButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				try {
					LogPanel.this.document.remove(0, LogPanel.this.logTextPane.getDocument().getLength());
				} catch (final BadLocationException e) {
					// ignored
				}
			}
		});
		this.settingsButton = new JButton(this.localizationConnector.getLocalizedString("Settings"));
		this.toolbarPanel.add(this.settingsButton, "cell 1 0,growy");
		this.settingsButton.setIcon(new ImageIcon(SwingAdvancedWindow.class.getResource("/icons/controlinterface/swingadvanced/button_settings.png")));
		initializeSettingsPopupMenu();
		this.settingsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				LogPanel.this.settingsPopupMenu.show(LogPanel.this.settingsButton, 0, LogPanel.this.settingsButton.getHeight());
			}
		});
	}

	/**
	 * Initialize the settings popup menu.
	 */
	private void initializeSettingsPopupMenu() {
		this.settingsPopupMenu = new JPopupMenu();
		this.settingsMenuPanel = new JPanel();
		this.settingsMenuPanel.setLayout(new MigLayout("", "[]", "[][][][][][]"));
		this.debugMessagesCheckBox = new JCheckBox(this.localizationConnector.getLocalizedString("Debug Messages"), this.showDebug);
		this.debugMessagesCheckBox.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent arg0) {
				final boolean curState = LogPanel.this.debugMessagesCheckBox.isSelected();
				if (curState != LogPanel.this.showDebug) {
					LogPanel.this.showDebug = curState;
					LogPanel.this.config.updateBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_DEBUG, LogPanel.this.showDebug);
				}
			}
		});
		this.settingsMenuPanel.add(this.debugMessagesCheckBox, "cell 0 0");
		this.infoMessagesCheckBox = new JCheckBox(this.localizationConnector.getLocalizedString("Info Messages"), this.showInfo);
		this.infoMessagesCheckBox.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent arg0) {
				final boolean curState = LogPanel.this.infoMessagesCheckBox.isSelected();
				if (curState != LogPanel.this.showInfo) {
					LogPanel.this.showInfo = curState;
					LogPanel.this.config.updateBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_INFO, LogPanel.this.showInfo);
				}
			}
		});
		this.settingsMenuPanel.add(this.infoMessagesCheckBox, "cell 0 1");
		this.warningMessagesCheckBox = new JCheckBox(this.localizationConnector.getLocalizedString("Warning Messages"), this.showWarning);
		this.warningMessagesCheckBox.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent arg0) {
				final boolean curState = LogPanel.this.warningMessagesCheckBox.isSelected();
				if (curState != LogPanel.this.showWarning) {
					LogPanel.this.showWarning = curState;
					LogPanel.this.config.updateBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_WARNING, LogPanel.this.showWarning);
				}
			}
		});
		this.settingsMenuPanel.add(this.warningMessagesCheckBox, "cell 0 2");
		this.errorMessagesCheckBox = new JCheckBox(this.localizationConnector.getLocalizedString("Error Messages"), this.showError);
		this.errorMessagesCheckBox.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent arg0) {
				final boolean curState = LogPanel.this.errorMessagesCheckBox.isSelected();
				if (curState != LogPanel.this.showError) {
					LogPanel.this.showError = curState;
					LogPanel.this.config.updateBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_ERROR, LogPanel.this.showError);
				}
			}
		});
		this.settingsMenuPanel.add(this.errorMessagesCheckBox, "cell 0 3");
		this.moduleActionMessagesCheckBox = new JCheckBox(this.localizationConnector.getLocalizedString("Module/Communication Updates"), this.showModuleAction);
		this.moduleActionMessagesCheckBox.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent arg0) {
				final boolean curState = LogPanel.this.moduleActionMessagesCheckBox.isSelected();
				if (curState != LogPanel.this.showModuleAction) {
					LogPanel.this.showModuleAction = curState;
					LogPanel.this.config.updateBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_MODULE_ACTION, LogPanel.this.showModuleAction);
				}
			}
		});
		this.settingsMenuPanel.add(this.moduleActionMessagesCheckBox, "cell 0 4");
		this.fontSizeLabel = new JLabel(this.localizationConnector.getLocalizedString("Font Size:"));
		this.settingsMenuPanel.add(this.fontSizeLabel, "cell 0 5");
		this.fontSizeSpinner = new JSpinner();
		this.fontSizeSpinner.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent arg0) {
				try {
					LogPanel.this.fontSize = (Integer) LogPanel.this.fontSizeSpinner.getValue();
					LogPanel.this.config.updateInteger(SwingAdvancedConstants.CONFIG___LOG_PANEL___FONT_SIZE, LogPanel.this.fontSize);
					LogPanel.this.logTextPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, LogPanel.this.fontSize));
				} catch (final NumberFormatException e) {
					// ignored
				}
			}
		});
		this.fontSizeSpinner.setModel(new SpinnerNumberModel(this.fontSize, 1, 40, 1));
		this.settingsMenuPanel.add(this.fontSizeSpinner, "cell 0 5,growx");
		this.filterTextLabel = new JLabel(this.localizationConnector.getLocalizedString("Filter (Regex):"));
		this.settingsMenuPanel.add(this.filterTextLabel, "cell 0 6");
		this.filterTextField = new JTextField();
		this.filterTextField.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void changedUpdate(final DocumentEvent e) {
				updateFilter();
			}

			@Override
			public void insertUpdate(final DocumentEvent e) {
				updateFilter();
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				updateFilter();
			}
		});
		this.settingsMenuPanel.add(this.filterTextField, "cell 0 6,growx");

		this.settingsPopupMenu.add(this.settingsMenuPanel);
	}

	/**
	 * Loads the configuration (filter etc.).
	 */
	private void loadConfig() {
		this.showDebug = this.config.getBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_DEBUG, SwingAdvancedConstants.LOG___SHOW_DEBUG);
		this.showError = this.config.getBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_ERROR, SwingAdvancedConstants.LOG___SHOW_ERROR);
		this.showInfo = this.config.getBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_INFO, SwingAdvancedConstants.LOG___SHOW_INFO);
		this.showModuleAction = this.config.getBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_MODULE_ACTION, SwingAdvancedConstants.LOG___SHOW_MODULE_ACTION);
		this.showWarning = this.config.getBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___SHOW_WARNING, SwingAdvancedConstants.LOG___SHOW_WARNING);
		this.unlimitedLineLimit = this.config.getBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___LINE_LIMIT_UNLIMITED, SwingAdvancedConstants.LOG___LINE_LIMIT_UNLIMITED);
		this.lineLimit = this.config.getInteger(SwingAdvancedConstants.CONFIG___LOG_PANEL___LINE_LIMIT, SwingAdvancedConstants.LOG___LINE_LIMIT);
		this.fontSize = this.config.getInteger(SwingAdvancedConstants.CONFIG___LOG_PANEL___FONT_SIZE, SwingAdvancedConstants.LOG___FONT_SIZE);
	}

	/**
	 * Updates the regular expression filter.
	 */
	private void updateFilter() {
		if (this.filterTextField.getText().isEmpty()) {
			this.filterRegExpPattern = null;
			this.filterTextField.setBackground(Color.WHITE);
		} else {
			try {
				this.filterRegExpPattern = Pattern.compile(".*" + this.filterTextField.getText() + ".*");
				this.filterTextField.setBackground(Color.WHITE);
			} catch (final PatternSyntaxException exception) {
				this.filterRegExpPattern = null;
				this.filterTextField.setBackground(Color.RED);
			}
		}
	}

	/**
	 * Updates the line limiter.
	 */
	private void updateLineLimit() {
		if (this.unlimitedCheckBox.isSelected()) {
			this.unlimitedLineLimit = true;
			this.config.updateBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___LINE_LIMIT_UNLIMITED, true);
			this.lineLimitSpinner.setEnabled(false);
			this.documentListener.setLimitLines(Integer.MAX_VALUE);
		} else {
			this.unlimitedLineLimit = false;
			this.config.updateBoolean(SwingAdvancedConstants.CONFIG___LOG_PANEL___LINE_LIMIT_UNLIMITED, false);
			this.lineLimitSpinner.setEnabled(true);
			try {
				this.lineLimit = (Integer) this.lineLimitSpinner.getValue();
				this.config.updateInteger(SwingAdvancedConstants.CONFIG___LOG_PANEL___LINE_LIMIT, this.lineLimit);
				this.documentListener.setLimitLines(this.lineLimit);
			} catch (final NumberFormatException e) {
				this.documentListener.setLimitLines(this.lineLimit);
			}
		}
	}
}
