package controlinterface.swinguiadvanced.view;

import helper.ResourceHelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.SystemColor;
import java.awt.SystemTray;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.border.MatteBorder;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.constants.SwingAdvancedConstants;
import controlinterface.swinguiadvanced.control.SwingSimpleControlWrapper;
import controlinterface.swinguiadvanced.view.dialog.GenericDialog;
import controlinterface.swinguiadvanced.view.panel.MessagePanel;
import framework.constants.Constants;
import framework.control.LocalizationConnector;

/**
 * Provides a simple control interface in the style of a setup wizard window with multiple slides.
 * 
 * @author Stefan Werner
 */
public class SetupWizardWindow extends JFrame {

	private static final long serialVersionUID = 1900228147337213365L;

	private JPanel contentPanel;
	private final SwingSimpleControlWrapper controller;
	private int curPosition = 0;
	private JButton exitButton;
	private final LocalizationConnector localizationConnector;
	private JButton nextButton;
	private JLabel positionLabel;
	private JButton prevButton;
	private final List<JComponent> slideComponents = new ArrayList<JComponent>();

	/**
	 * Instantiates a new setup wizard window.
	 *
	 * @param swingSimpleControlWrapper the swing simple control wrapper
	 * @param localizationConnector the localization connector
	 */
	public SetupWizardWindow(final SwingSimpleControlWrapper swingSimpleControlWrapper, final LocalizationConnector localizationConnector) {
		this.controller = swingSimpleControlWrapper;
		this.localizationConnector = localizationConnector;
		initialize();
	}

	/**
	 * Adds a slide.
	 *
	 * @param component the component to add
	 * @return the index where slide was added
	 */
	public int addSlide(final JComponent component) {
		this.slideComponents.add(component);
		return this.slideComponents.size() - 1;
	}

	/**
	 * Exits the system.
	 */
	private void exit() {
		final MessagePanel panel = new MessagePanel(this.localizationConnector.getLocalizedString("System shut down and exit. Are you sure?"), null, null);
		final GenericDialog dialog = new GenericDialog(this, this.localizationConnector.getLocalizedString("Exit"), this.localizationConnector.getLocalizedString("Yes"), this.localizationConnector.getLocalizedString("No"), panel);
		if (dialog.showDialog() == 0) {
			this.controller.exitSystem();
		}
	}

	/**
	 * Gets the slide count.
	 *
	 * @return the slide count
	 */
	public int getSlideCount() {
		return this.slideComponents.size();
	}

	/**
	 * Initializes the window.
	 */
	private void initialize() {
		setTitle(Constants.APP_NAME + " - " + this.localizationConnector.getLocalizedString(SwingAdvancedConstants.CI_SETUP_WIZARD_NAME));
		final ImageIcon icon = ResourceHelper.getImageIconByName(SwingSimpleControlWrapper.RESOURCE___ICON);
		if (icon != null) {
			setIconImage(icon.getImage());
		}
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(final java.awt.event.WindowEvent windowEvent) {
				toggleVisibility();
			}
		});
		setPreferredSize(new Dimension(900, 700));
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel = new JPanel();
		this.contentPanel.setLayout(new BorderLayout());
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		// Buttons
		final JPanel buttonPane = new JPanel();
		buttonPane.setBackground(Color.WHITE);
		buttonPane.setBorder(new MatteBorder(1, 0, 0, 0, SystemColor.windowBorder));
		getContentPane().add(buttonPane, BorderLayout.SOUTH);
		buttonPane.setLayout(new MigLayout("", "[grow][]", "[]"));
		this.prevButton = new JButton(this.localizationConnector.getLocalizedString("Previous"));
		this.prevButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				setVisibleSlide(SetupWizardWindow.this.curPosition - 1);
			}
		});
		this.prevButton.setEnabled(false);
		buttonPane.add(this.prevButton, "flowx,cell 0 0");
		this.positionLabel = new JLabel();
		buttonPane.add(this.positionLabel, "cell 0 0");
		this.nextButton = new JButton(this.localizationConnector.getLocalizedString("Next"));
		this.nextButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				setVisibleSlide(SetupWizardWindow.this.curPosition + 1);
			}
		});
		buttonPane.add(this.nextButton, "cell 0 0");
		if (SystemTray.isSupported()) {
			final JButton hideButton = new JButton(this.localizationConnector.getLocalizedString("Hide"));
			hideButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent arg0) {
					toggleVisibility();
				}
			});
			buttonPane.add(hideButton, "flowx,cell 1 0");
		}
		this.exitButton = new JButton(this.localizationConnector.getLocalizedString("Exit"));
		this.exitButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				exit();
			}
		});
		buttonPane.add(this.exitButton, "flowx,cell 1 0");
		pack();
	}

	/**
	 * Sets the back button state.
	 *
	 * @param isEnabled the new back button state
	 */
	public void setBackButtonState(final boolean isEnabled) {
		this.prevButton.setEnabled(isEnabled);
	}

	/**
	 * Sets all button states.
	 */
	private void setButtonStates() {
		if (this.curPosition == 0) {
			this.prevButton.setEnabled(false);
		} else {
			this.prevButton.setEnabled(this.controller.mayGoTo(this.curPosition - 1));
		}
		if (this.curPosition >= (this.slideComponents.size() - 1)) {
			this.nextButton.setEnabled(false);
			this.prevButton.requestFocusInWindow();
		} else {
			this.nextButton.setEnabled(this.controller.mayGoTo(this.curPosition + 1));
			this.nextButton.requestFocusInWindow();
		}
	}

	/**
	 * Sets the exit button state.
	 *
	 * @param isEnabled the new exit button state
	 */
	public void setExitButtonState(final boolean isEnabled) {
		this.exitButton.setEnabled(isEnabled);
	}

	/**
	 * Sets the next button state.
	 *
	 * @param isEnabled the new next button state
	 */
	public void setNextButtonState(final boolean isEnabled) {
		this.nextButton.setEnabled(isEnabled);
	}

	/**
	 * Sets the visible slide.
	 *
	 * @param index the index of the new visible slide
	 */
	public synchronized void setVisibleSlide(final int index) {
		if ((index < 0) || (index >= this.slideComponents.size())) {
			return;
		}
		this.curPosition = index;
		this.contentPanel.removeAll();
		final JComponent comp = this.slideComponents.get(this.curPosition);
		this.contentPanel.add(comp);
		comp.repaint();
		this.positionLabel.setText((this.curPosition + 1) + "/" + this.slideComponents.size());
		setButtonStates();
	}

	/**
	 * Toggles visibility of window.
	 */
	public void toggleVisibility() {
		setVisible(!isVisible());
	}
}
