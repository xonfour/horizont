package module.pgpcrypto.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;

import module.pgpcrypto.model.KeyRingInfo;
import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang.ArrayUtils;

/**
 * Dialog to let the user select one or more key rings from a list.
 *
 * @author Stefan Werner
 */
public class KeyRingSelectionDialog extends JDialog {

	private static final long serialVersionUID = 1895749421823407035L;

	/**
	 * Shows selection list dialog.
	 *
	 * @param title the title
	 * @param elements the elements
	 * @param preselectedIndices the preselected indices
	 * @param allowMultiSelect set to true to allow multiple selections
	 * @param parentElements the parent elements
	 * @return the list of selected keys
	 */
	public static List<KeyRingInfo> showSelectionListDialog(final String title, final Collection<KeyRingInfo> elements, final Collection<Integer> preselectedIndices, final boolean allowMultiSelect, final Collection<KeyRingInfo> parentElements) {
		final KeyRingSelectionDialog dialog = new KeyRingSelectionDialog(title, elements, preselectedIndices, allowMultiSelect, parentElements);
		try {
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			dialog.pack();
			dialog.setModal(true);
			dialog.setVisible(true);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return dialog.selectedValueList;
	}

	private final JPanel contentPanel = new JPanel();
	private final JList<KeyRingInfo> elemKeyList;
	private final JScrollPane elemScrollPane;
	private JLabel othersharesLabel;
	private JList<KeyRingInfo> parentKeyList;
	private JScrollPane parentScrollPane;
	private List<KeyRingInfo> selectedValueList = new ArrayList<KeyRingInfo>();
	private final JLabel titleLabel;

	/**
	 * Creates the dialog.
	 *
	 * @param title the title
	 * @param elements the elements
	 * @param preselectedIndices the preselected indices
	 * @param allowMultiSelect set to true to allow multiple selections
	 * @param parentElements the parent elements (used for keys that are shown but cannot be (de)selected)
	 */
	public KeyRingSelectionDialog(final String title, final Collection<KeyRingInfo> elements, final Collection<Integer> preselectedIndices, final boolean allowMultiSelect, final Collection<KeyRingInfo> parentElements) {
		// setSize(new Dimension(600, 450));
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		this.contentPanel.setLayout(new MigLayout("", "[600px,grow]", "[][450px,grow][][grow]"));
		this.titleLabel = new JLabel(title);
		this.contentPanel.add(this.titleLabel, "cell 0 0");
		this.elemScrollPane = new JScrollPane();
		this.elemScrollPane.setViewportBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		this.contentPanel.add(this.elemScrollPane, "cell 0 1,grow");
		final DefaultListModel<KeyRingInfo> elemListModel = new DefaultListModel<KeyRingInfo>();
		this.elemKeyList = new JList<KeyRingInfo>(elemListModel);
		this.elemKeyList.setCellRenderer(new KeyRingInfoRenderer());
		if (allowMultiSelect) {
			this.elemKeyList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		} else {
			this.elemKeyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		}
		for (final KeyRingInfo info : elements) {
			elemListModel.addElement(info);
		}
		this.elemScrollPane.setViewportView(this.elemKeyList);
		if ((parentElements != null) && !parentElements.isEmpty()) {
			this.othersharesLabel = new JLabel("Overriding shares from higher level elements");
			this.contentPanel.add(this.othersharesLabel, "cell 0 2");
			this.parentScrollPane = new JScrollPane();
			this.parentScrollPane.setViewportBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
			this.contentPanel.add(this.parentScrollPane, "cell 0 3,grow");
			final DefaultListModel<KeyRingInfo> parentListModel = new DefaultListModel<KeyRingInfo>();
			this.parentKeyList = new JList<KeyRingInfo>(parentListModel);
			this.parentKeyList.setCellRenderer(new KeyRingInfoRenderer());
			for (final KeyRingInfo info : parentElements) {
				parentListModel.addElement(info);
			}
			this.parentScrollPane.setViewportView(this.parentKeyList);
		}
		if (preselectedIndices != null) {
			// TODO: This is somewhat ugly.
			this.elemKeyList.setSelectedIndices(ArrayUtils.toPrimitive(preselectedIndices.toArray(new Integer[0])));
		}
		final JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
		getContentPane().add(buttonPane, BorderLayout.SOUTH);
		final JButton okButton = new JButton("OK");
		okButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				KeyRingSelectionDialog.this.selectedValueList = KeyRingSelectionDialog.this.elemKeyList.getSelectedValuesList();
				dispose();
			}
		});
		okButton.setActionCommand("OK");
		buttonPane.add(okButton);
		getRootPane().setDefaultButton(okButton);
		final JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});
		cancelButton.setActionCommand("Cancel");
		buttonPane.add(cancelButton);
	}
}
