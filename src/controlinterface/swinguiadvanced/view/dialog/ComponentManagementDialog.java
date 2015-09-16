package controlinterface.swinguiadvanced.view.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.MatteBorder;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.ComponentManagementController;
import controlinterface.swinguiadvanced.view.other.TableSelectionListener;
import controlinterface.swinguiadvanced.view.panel.TableSelectionScrollPane;
import framework.control.LocalizationConnector;

/**
 * Dialog for managing components (control interfaces and modules).
 * <p>
 * TODO: Change to JPanel and wrap by GenericDialog.
 *
 * @author Stefan Werner
 */
public class ComponentManagementDialog extends JDialog {

	/**
	 * The Enum TYPE. Type of components managed.
	 */
	public static enum TYPE {
		CI, MODULE
	}

	private static final long serialVersionUID = 8428568340772786825L;

	private final JButton addButton;
	private final TableSelectionScrollPane componentListSelectionScrollPane;
	private final JPanel contentPanel = new JPanel();
	private int ownCIIndex = -1;
	private final JButton removeButton;
	private final JButton renameButton;
	private final JButton setRightsButton;

	/**
	 * Instantiates a new component management dialog.
	 *
	 * @param type the type of components to manage
	 * @param title the title of the dialog
	 * @param controller the controller of this dialog
	 * @param localizationConnector the localization connector
	 */
	public ComponentManagementDialog(final TYPE type, final String title, final ComponentManagementController controller, final LocalizationConnector localizationConnector) {
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.DOCUMENT_MODAL);
		setPreferredSize(new Dimension(600, 300));
		setTitle(title);

		// content
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel.setLayout(new MigLayout("", "[grow]", "[grow][]"));
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);

		this.componentListSelectionScrollPane = new TableSelectionScrollPane(null, localizationConnector.getLocalizedString("Name"), localizationConnector.getLocalizedString("ID"), localizationConnector.getLocalizedString("Type"), localizationConnector.getLocalizedString("Rights"));
		this.componentListSelectionScrollPane.addTableSelectionListener(new TableSelectionListener() {

			@Override
			public void onComponentSelected(final int index) {
				if (index < 0) {
					ComponentManagementDialog.this.setRightsButton.setEnabled(false);
					ComponentManagementDialog.this.removeButton.setEnabled(false);
					ComponentManagementDialog.this.renameButton.setEnabled(false);
				} else if (index == ComponentManagementDialog.this.ownCIIndex) {
					ComponentManagementDialog.this.setRightsButton.setEnabled(false);
					ComponentManagementDialog.this.removeButton.setEnabled(true); // it is OK to remove yourself
					ComponentManagementDialog.this.renameButton.setEnabled(true);
				} else {
					ComponentManagementDialog.this.setRightsButton.setEnabled(true);
					ComponentManagementDialog.this.removeButton.setEnabled(true);
					ComponentManagementDialog.this.renameButton.setEnabled(true);
				}
			}
		});
		this.contentPanel.add(this.componentListSelectionScrollPane, "cell 0 0,grow");

		this.addButton = new JButton(localizationConnector.getLocalizedString("Add"));
		this.contentPanel.add(this.addButton, "flowx,cell 0 1,growx");
		this.addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				if (type == TYPE.CI) {
					controller.addNewCI();
				} else {
					controller.addNewModule(true);
				}
			}
		});

		this.setRightsButton = new JButton(localizationConnector.getLocalizedString("Set Rights"));
		this.contentPanel.add(this.setRightsButton, "cell 0 1");
		this.setRightsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				if (type == TYPE.CI) {
					controller.setCIRights(ComponentManagementDialog.this.componentListSelectionScrollPane.getSelectedIndex());
				} else {
					controller.setModuleRights(ComponentManagementDialog.this.componentListSelectionScrollPane.getSelectedIndex());
				}
			}
		});
		this.setRightsButton.setEnabled(false);

		this.renameButton = new JButton(localizationConnector.getLocalizedString("Rename"));
		this.renameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				if (type == TYPE.CI) {
					controller.renameCI(ComponentManagementDialog.this.componentListSelectionScrollPane.getSelectedIndex());
				} else {
					controller.renameModule(ComponentManagementDialog.this.componentListSelectionScrollPane.getSelectedIndex());
				}
			}
		});
		this.renameButton.setEnabled(false);
		this.contentPanel.add(this.renameButton, "cell 0 1");

		this.removeButton = new JButton(localizationConnector.getLocalizedString("Remove"));
		this.removeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				if (type == TYPE.CI) {
					controller.removeCI(ComponentManagementDialog.this.componentListSelectionScrollPane.getSelectedIndex());
				} else {
					controller.removeModule(ComponentManagementDialog.this.componentListSelectionScrollPane.getSelectedIndex());
				}
			}
		});
		this.contentPanel.add(this.removeButton, "cell 0 1");
		this.removeButton.setEnabled(false);

		// dialog buttons
		final JPanel buttonPane = new JPanel();
		buttonPane.setBackground(Color.WHITE);
		buttonPane.setBorder(new MatteBorder(1, 0, 0, 0, SystemColor.windowBorder));
		buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		final JButton okButton = new JButton(localizationConnector.getLocalizedString("Close"));
		okButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				dispose();
			}
		});
		okButton.setActionCommand(localizationConnector.getLocalizedString("Close"));
		buttonPane.add(okButton);
		getRootPane().setDefaultButton(okButton);

		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				pack();
			}
		});
	}

	/**
	 * Sets the index of own CI if managing control interfaces (to disable rights management).
	 *
	 * IMPORTANT: Of course this is only done for cosmetic reasons as managing own rights is forbidden by the framework.
	 *
	 * @param index the own index
	 */
	public void setOwnCIIndex(final int index) {
		this.ownCIIndex = index;
	}

	/**
	 * Shows the dialog.
	 */
	public void showDialog() {
		setVisible(true);
	}

	/**
	 * Updates the visible data.
	 *
	 * @param data the data
	 */
	public void updateData(final String[][] data) {
		this.componentListSelectionScrollPane.updateData(data);
	}
}
