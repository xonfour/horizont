package experimental.module.filebrowser.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import net.miginfocom.swing.MigLayout;
import framework.model.DataElement;

/**
 *
 * @author Stefan Werner
 */
public class FileOrFolderElementPanel extends JPanel {

	/**
	 *
	 */
	private static final long serialVersionUID = 2612930705299225436L;

	private static void addPopup(final Component component, final JPopupMenu popup) {
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}

			private void showMenu(final MouseEvent e) {
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}

	private final JLabel label;
	private final JPopupMenu popupMenu;
	private final JMenuItem renameMenuItem;
	private final JMenuItem deleteMenuItem;

	private final JMenuItem synchronizeMenuItem;

	public FileOrFolderElementPanel(final DataElement element, final boolean isSelected, final Color listBgColor) {
		// setBorder(BorderFactory.createCompoundBorder(new MatteBorder(0, 0, 4, 4, listBgColor), new LineBorder(Color.LIGHT_GRAY, 0, true)));
		setLayout(new MigLayout("", "[100px::200px]", "[]"));

		this.label = new JLabel(element.getName());

		add(this.label, "cell 0 0,alignx center,aligny center");
		this.label.setMinimumSize(new Dimension(0, 0));

		this.popupMenu = new JPopupMenu();
		FileOrFolderElementPanel.addPopup(this.label, this.popupMenu);

		this.synchronizeMenuItem = new JMenuItem("Synchronize");
		this.synchronizeMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
			}
		});
		this.synchronizeMenuItem.setIcon(new ImageIcon(FileOrFolderElementPanel.class.getResource("/icons/syncing_xs.png")));
		this.popupMenu.add(this.synchronizeMenuItem);

		this.renameMenuItem = new JMenuItem("Rename");
		this.renameMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
			}
		});
		this.renameMenuItem.setIcon(new ImageIcon(FileOrFolderElementPanel.class.getResource("/icons/sign_xs.png")));
		this.popupMenu.add(this.renameMenuItem);

		this.deleteMenuItem = new JMenuItem("Delete");
		this.deleteMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
			}
		});
		this.deleteMenuItem.setIcon(new ImageIcon(FileOrFolderElementPanel.class.getResource("/icons/key_unknown_xs.png")));
		this.popupMenu.add(this.deleteMenuItem);

		if (isSelected) {
			setBackground(Color.BLACK); // TODO
			this.label.setForeground(Color.WHITE);
		}
	}

	@Override
	public String toString() {
		return this.label.getText();
	}
}
