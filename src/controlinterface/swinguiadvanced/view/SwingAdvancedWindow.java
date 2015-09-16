package controlinterface.swinguiadvanced.view;

import framework.constants.Constants;
import framework.control.LocalizationConnector;
import helper.PersistentConfigurationHelper;
import helper.ResourceHelper;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalExclusionType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.constants.SwingAdvancedConstants;
import controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface;
import controlinterface.swinguiadvanced.view.dialog.GenericDialog;
import controlinterface.swinguiadvanced.view.panel.MessagePanel;

/**
 * Provides a advanced control interface window providing low level access to system's functionalities.
 *
 * @author Stefan Werner
 */
public class SwingAdvancedWindow extends JFrame {

	private static final long serialVersionUID = 1472529513536012381L;

	private JMenuItem aboutMenuItem;
	private ImageIcon appIcon = null;
	private final PersistentConfigurationHelper configHelper;
	private JPanel contentPane;
	private final SwingAdvancedControlInterface controller;
	private JLabel currentStateLabel;
	private JLabel currentStateTextLabel;
	private JButton exitButton;
	private ImageIcon exitIcon = null;
	private JMenuItem exportDatabaseMenuItem;
	private JSplitPane horizontalInnerSplitPane;
	private Component horizontalStrut;
	private JLabel iconLabel;
	private JMenuItem importDatabaseMenuItem;
	private JLabel infoTextLabel;
	private final LocalizationConnector localizationConnector;
	private JMenuItem manageDisconnectedConnectionsMenuItem;
	private JMenuItem manageModulesMenuItem;
	private JMenuItem manageOtherControlMenuItem;
	private JButton moreButton;
	private ImageIcon moreIcon = null;
	private JPopupMenu morePopupMenu;
	private ImageIcon refreshIcon = null;
	private JButton refreshSystemStateButton;
	private JMenuItem startBeanshellInstanceMenuItem;
	private JButton startButton;
	private ImageIcon startIcon = null;
	private JPanel statePanel;
	private JButton stopButton;
	private ImageIcon stopIcon = null;
	private ImageIcon toolbarIcon = null;
	private JPanel toolBarPanel;
	private JSplitPane verticalOuterSplitPane;

	/**
	 * Instantiates a new swing advanced window.
	 *
	 * @param controller the advanced CI controller
	 * @param configHelper the config helper
	 * @param localizationConnector the localization connector
	 */
	public SwingAdvancedWindow(final SwingAdvancedControlInterface controller, final PersistentConfigurationHelper configHelper, final LocalizationConnector localizationConnector) {
		this.controller = controller;
		this.configHelper = configHelper;
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Display the about dialog.
	 */
	private void displayAboutDialog() {
		final String msg = this.localizationConnector.getLocalizedString("This is ") + SwingAdvancedConstants.CI_ADVANCED_NAME + " " + SwingAdvancedConstants.CI_VERSION + "\nRunning on " + Constants.APP_NAME + " " + Constants.APP_VERSION;
		final MessagePanel panel = new MessagePanel(msg, this.toolbarIcon, null);
		(new GenericDialog(null, this.localizationConnector.getLocalizedString("About"), this.localizationConnector.getLocalizedString("Close"), null, panel)).showDialog();
	}

	/**
	 * Exits the system.
	 */
	private void exit() {
		final MessagePanel panel = new MessagePanel(this.localizationConnector.getLocalizedString("System shut down and exit. Are you sure?"), null, this.localizationConnector.getLocalizedString("Force exit if some control interfaces / modules fail to shut down."));
		final GenericDialog dialog = new GenericDialog(this, this.localizationConnector.getLocalizedString("Exit"), this.localizationConnector.getLocalizedString("Yes"), this.localizationConnector.getLocalizedString("No"), panel);
		if (dialog.showDialog() == 0) {
			this.controller.exitSystem(panel.isCheckBoxSelected());
		}
	}

	/**
	 * Initializes the window.
	 */
	private void initialize() {
		setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);
		loadImages();
		if (this.toolbarIcon != null) {
			setIconImage(this.appIcon.getImage());
		}
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // TODO: remove?
		setState(this.configHelper.getInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___STATE, SwingAdvancedConstants.MAIN_WINDOW___DEFAULT_STATE));
		final int sizeX = this.configHelper.getInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___SIZE_X, SwingAdvancedConstants.MAIN_WINDOW___DEFAULT_SIZE_X);
		final int sizeY = this.configHelper.getInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___SIZE_Y, SwingAdvancedConstants.MAIN_WINDOW___DEFAULT_SIZE_Y);
		setSize(new Dimension(sizeX, sizeY));
		setTitle(Constants.APP_NAME);
		this.contentPane = new JPanel();
		this.contentPane.setLayout(new MigLayout("ins 0", "[grow,fill]", "[][grow,fill][]"));
		setContentPane(this.contentPane);
		// TOOLBAR
		this.toolBarPanel = new JPanel();
		this.toolBarPanel.setBackground(Color.WHITE);
		this.toolBarPanel.setBorder(new MatteBorder(0, 0, 1, 0, SystemColor.windowBorder));
		this.contentPane.add(this.toolBarPanel, "cell 0 0");
		this.toolBarPanel.setLayout(new MigLayout("ins 2", "[][grow][]", "[]"));
		if (this.toolbarIcon != null) {
			this.iconLabel = new JLabel(this.toolbarIcon);
			this.toolBarPanel.add(this.iconLabel, "cell 0 0");
		}
		this.startButton = new JButton(this.localizationConnector.getLocalizedString("Start"));
		this.startButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.startBroker();
			}
		});
		if (this.startIcon != null) {
			this.startButton.setIcon(this.startIcon);
		}
		this.toolBarPanel.add(this.startButton, "cell 0 0,growy");
		this.stopButton = new JButton(this.localizationConnector.getLocalizedString("Stop"));
		this.stopButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.stopBroker();
			}
		});
		if (this.stopIcon != null) {
			this.stopButton.setIcon(this.stopIcon);
		}
		this.toolBarPanel.add(this.stopButton, "cell 0 0,growy");
		this.horizontalStrut = Box.createHorizontalStrut(20);
		this.toolBarPanel.add(this.horizontalStrut, "cell 0 0");
		this.exitButton = new JButton(this.localizationConnector.getLocalizedString("Exit"));
		this.exitButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				exit();
			}
		});
		if (this.exitIcon != null) {
			this.exitButton.setIcon(this.exitIcon);
		}
		this.toolBarPanel.add(this.exitButton, "cell 0 0,growy");
		initializeMorePopupMenu();
		this.moreButton = new JButton(this.localizationConnector.getLocalizedString("More"));
		this.moreButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.morePopupMenu.show(SwingAdvancedWindow.this.moreButton, 0, SwingAdvancedWindow.this.moreButton.getHeight());
			}
		});
		if (this.moreIcon != null) {
			this.moreButton.setIcon(this.moreIcon);
		}
		this.toolBarPanel.add(this.moreButton, "flowx,cell 2 0,growy");
		// MAIN AREA/SPLIT PANES
		this.verticalOuterSplitPane = new JSplitPane();
		this.verticalOuterSplitPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		this.verticalOuterSplitPane.setResizeWeight(0.6);
		final int vertDivLoc = this.configHelper.getInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___SPLITVIEW_VERT_DIV_POS, -1);
		if (vertDivLoc >= 0) {
			this.verticalOuterSplitPane.setDividerLocation(vertDivLoc);
		}
		this.verticalOuterSplitPane.setOneTouchExpandable(true);
		this.verticalOuterSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		this.contentPane.add(this.verticalOuterSplitPane, "cell 0 1");
		this.horizontalInnerSplitPane = new JSplitPane();
		this.horizontalInnerSplitPane.setResizeWeight(1.0);
		this.horizontalInnerSplitPane.setOneTouchExpandable(true);
		this.verticalOuterSplitPane.setTopComponent(this.horizontalInnerSplitPane);
		// BOTTOM INFO/STATE PANEL
		this.statePanel = new JPanel();
		this.statePanel.setBorder(new MatteBorder(1, 0, 0, 0, SystemColor.windowBorder));
		this.statePanel.setBackground(Color.WHITE);
		this.contentPane.add(this.statePanel, "cell 0 2");
		this.statePanel.setLayout(new MigLayout("", "[][grow][]", "[]"));
		this.infoTextLabel = new JLabel();
		this.statePanel.add(this.infoTextLabel, "cell 0 0");
		this.currentStateTextLabel = new JLabel(this.localizationConnector.getLocalizedString("Current State:"));
		this.statePanel.add(this.currentStateTextLabel, "cell 2 0");
		this.currentStateLabel = new JLabel("-");
		this.currentStateLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		this.statePanel.add(this.currentStateLabel, "cell 2 0");
		if (this.refreshIcon != null) {
			this.refreshSystemStateButton = new JButton(this.refreshIcon);
		} else {
			this.refreshSystemStateButton = new JButton(this.localizationConnector.getLocalizedString("Refresh"));
		}
		this.refreshSystemStateButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.refreshInfoAndSystemState();
			}
		});
		this.statePanel.add(this.refreshSystemStateButton, "cell 2 0");
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(final WindowEvent e) {
				storeViewSettings();
			}
		});
	}

	/**
	 * Initializes the "More" popup menu.
	 */
	private void initializeMorePopupMenu() {
		this.morePopupMenu = new JPopupMenu();

		this.manageModulesMenuItem = new JMenuItem(this.localizationConnector.getLocalizedString("Modules Management"));
		this.manageModulesMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.manageModules();
			}
		});
		this.morePopupMenu.add(this.manageModulesMenuItem);
		this.manageDisconnectedConnectionsMenuItem = new JMenuItem(this.localizationConnector.getLocalizedString("Connections Management"));
		this.manageDisconnectedConnectionsMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.manageAllConnections();
			}
		});
		this.morePopupMenu.add(this.manageDisconnectedConnectionsMenuItem);
		this.manageOtherControlMenuItem = new JMenuItem(this.localizationConnector.getLocalizedString("Control Interfaces Management"));
		this.manageOtherControlMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.manageCIs();
			}
		});
		this.morePopupMenu.add(this.manageOtherControlMenuItem);
		this.startBeanshellInstanceMenuItem = new JMenuItem(this.localizationConnector.getLocalizedString("Start BeanShell Instance"));
		this.startBeanshellInstanceMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.startBeanShell();
			}
		});
		this.morePopupMenu.add(this.startBeanshellInstanceMenuItem);
		JSeparator separator = new JSeparator();
		this.morePopupMenu.add(separator);
		this.exportDatabaseMenuItem = new JMenuItem(this.localizationConnector.getLocalizedString("Export Database"));
		this.exportDatabaseMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.exportDatabase();
			}
		});
		this.morePopupMenu.add(this.exportDatabaseMenuItem);
		this.importDatabaseMenuItem = new JMenuItem(this.localizationConnector.getLocalizedString("Import Database"));
		this.importDatabaseMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				SwingAdvancedWindow.this.controller.importDatabase();
			}
		});
		this.morePopupMenu.add(this.importDatabaseMenuItem);
		separator = new JSeparator();
		this.morePopupMenu.add(separator);
		this.aboutMenuItem = new JMenuItem(this.localizationConnector.getLocalizedString("About"));
		this.aboutMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				displayAboutDialog();
			}
		});
		this.morePopupMenu.add(this.aboutMenuItem);
	}

	/**
	 * Load all icons/symbols.
	 */
	private void loadImages() {
		// TODO: move to constants
		this.appIcon = ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/icon.png");
		this.exitIcon = ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/button_exit.png");
		this.moreIcon = ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/button_more.png");
		this.refreshIcon = ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/button_refresh.png");
		this.startIcon = ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/button_start.png");
		this.stopIcon = ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/button_stop.png");
		this.toolbarIcon = ResourceHelper.getImageIconByName("icons/controlinterface/swingadvanced/toolbar_icon.png");
	}

	/**
	 * Sets the bottom component.
	 *
	 * @param component the new bottom component
	 */
	public void setBottomComponent(final JComponent component) {
		this.verticalOuterSplitPane.setRightComponent(component);
	}

	/**
	 * Sets the current state text.
	 *
	 * @param state the new current state text
	 */
	public void setCurrentStateText(final String state) {
		this.currentStateLabel.setText(state);
	}

	/**
	 * Sets exit button enabled.
	 *
	 * @param state the new state
	 */
	public void setExitButtonEnabled(final boolean state) {
		this.exitButton.setEnabled(state);
	}

	/**
	 * Sets the info text.
	 *
	 * @param infoText the new info text
	 */
	public void setInfoText(final String infoText) {
		this.infoTextLabel.setText(infoText);
	}

	/**
	 * Sets "More" button and menu enabled.
	 *
	 * @param state the new state
	 */
	public void setMoreButtonAndMenuEnabled(final boolean state) {
		this.moreButton.setEnabled(state);
		this.morePopupMenu.setEnabled(state);
	}

	/**
	 * Sets the start button enabled.
	 *
	 * @param state the new state
	 */
	public void setStartButtonEnabled(final boolean state) {
		this.startButton.setEnabled(state);
	}

	/**
	 * Sets the stop button enabled.
	 *
	 * @param state the new state
	 */
	public void setStopButtonEnabled(final boolean state) {
		this.stopButton.setEnabled(state);
	}

	/**
	 * Sets the top left component.
	 *
	 * @param component the new top left component
	 */
	public void setTopLeftComponent(final JComponent component) {
		this.horizontalInnerSplitPane.setLeftComponent(component);
	}

	/**
	 * Sets the top right component.
	 *
	 * @param component the new top right component
	 */
	public void setTopRightComponent(final JComponent component) {
		this.horizontalInnerSplitPane.setRightComponent(component);
	}

	/**
	 * Stores view settings.
	 */
	public void storeViewSettings() {
		final Dimension d = getSize();
		this.configHelper.updateInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___SIZE_X, d.width);
		this.configHelper.updateInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___SIZE_Y, d.height);
		this.configHelper.updateInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___STATE, getState());
		this.configHelper.updateInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___SPLITVIEW_VERT_DIV_POS, this.verticalOuterSplitPane.getDividerLocation());
		this.configHelper.updateInteger(SwingAdvancedConstants.CONFIG___MAIN_WINDOW___SPLITVIEW_HORIZ_DIV_POS, this.horizontalInnerSplitPane.getDividerLocation());
	}
}
