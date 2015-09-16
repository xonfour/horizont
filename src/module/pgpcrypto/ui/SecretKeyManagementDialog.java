package module.pgpcrypto.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;

import module.pgpcrypto.control.PGPKeyManager;
import module.pgpcrypto.model.KeyRingInfo;
import net.miginfocom.swing.MigLayout;

/**
 * Dialog to manage secret keys.
 *
 * @author Stefan Werner
 */
public class SecretKeyManagementDialog extends JDialog {

	private static final long serialVersionUID = 9136368465252737504L;

	private final JButton btnGenerate;
	private final JButton btnImportFromFile;
	private final JButton btnRemove;
	private final JPanel contentPanel = new JPanel();
	private final PGPKeyManager cryptoManager;
	private final JList<KeyRingInfo> list;
	private final JScrollPane scrollPane;

	/**
	 * Creates the dialog.
	 *
	 * @param cryptoManager the crypto manager
	 */
	public SecretKeyManagementDialog(final PGPKeyManager cryptoManager) {
		this.cryptoManager = cryptoManager;
		setSize(new Dimension(600, 450));
		getContentPane().setLayout(new BorderLayout());
		this.contentPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		getContentPane().add(this.contentPanel, BorderLayout.CENTER);
		this.contentPanel.setLayout(new MigLayout("", "[grow]", "[grow][]"));

		this.scrollPane = new JScrollPane();
		this.scrollPane.setViewportBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		this.contentPanel.add(this.scrollPane, "cell 0 0,grow");

		this.list = new JList<KeyRingInfo>();
		this.list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		this.list.setCellRenderer(cryptoManager.getKeyListCellRenderer());
		this.scrollPane.setViewportView(this.list);

		this.btnImportFromFile = new JButton("Import from File");
		this.btnImportFromFile.addActionListener(new ActionListener() {
			/* (non-Javadoc)
			 *
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
			@Override
			public void actionPerformed(final ActionEvent e) {

				if (cryptoManager.importKeys()) {
					updateView();
				}
			}
		});
		this.contentPanel.add(this.btnImportFromFile, "flowx,cell 0 1,growx");

		this.btnRemove = new JButton("Remove");
		this.btnRemove.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				final int result = JOptionPane.showConfirmDialog(null, "Are you sure you want to REMOVE the selected keys? This CANNOT be undone!", "Remove", JOptionPane.YES_NO_OPTION);
				if (result == JOptionPane.YES_OPTION) {
					cryptoManager.removeSecretKeys(SecretKeyManagementDialog.this.list.getSelectedValuesList());
					updateView();
				}
			}
		});

		this.btnGenerate = new JButton("Generate");
		this.btnGenerate.addActionListener(new ActionListener() {
			/* (non-Javadoc)
			 *
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
			@Override
			public void actionPerformed(final ActionEvent e) {
				cryptoManager.generateKeyPair();
				updateView();
			}
		});
		this.contentPanel.add(this.btnGenerate, "cell 0 1,growx");
		this.contentPanel.add(this.btnRemove, "cell 0 1");
		{
			final JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				final JButton okButton = new JButton("OK");
				okButton.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(final ActionEvent arg0) {
						dispose();
					}
				});
				okButton.setActionCommand("OK");
				buttonPane.add(okButton);
				getRootPane().setDefaultButton(okButton);
			}
		}
		setModal(false);
		setVisible(true);
		updateView();
	}

	/**
	 * Updates view.
	 */
	private void updateView() {
		this.list.setListData(this.cryptoManager.getAllKnownSecretKeyInfo().values().toArray(new KeyRingInfo[0]));
	}
}
