package controlinterface.swinguiadvanced.view.panel;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import net.miginfocom.swing.MigLayout;
import controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface;
import controlinterface.swinguiadvanced.view.dialog.GenericDialog;
import framework.control.LocalizationConnector;

/**
 * Panel used to view and modify String maps. Currently used to send commands to and receive results from modules.
 *
 * @author Stefan Werner
 */
public class StringMapViewAndEditPanel extends JPanel {

	private static final long serialVersionUID = -5327827173715667398L;

	private JButton addButton;
	private JButton clipboardClearButton;
	private JButton clipboardCopyButton;
	private JButton clipboardPasteButton;
	private String[] columnTitles;
	private final SwingAdvancedControlInterface controller;
	private final boolean editable;
	private Map<String, String> initialData;
	private final LocalizationConnector localizationConnector;
	private DefaultTableModel model;
	private JButton parseConfigButton;
	private JButton removeButton;
	private JScrollPane scrollPane;
	private JPanel scrollPaneContainerPanel;
	private JTable table;

	/**
	 * Instantiates a new string map view and edit panel.
	 *
	 * @param controller the advanced CI controller
	 * @param initialData the initial data map
	 * @param editable true, if editable
	 * @param localizationConnector the localization connector
	 */
	public StringMapViewAndEditPanel(final SwingAdvancedControlInterface controller, final Map<String, String> initialData, final boolean editable, final LocalizationConnector localizationConnector) {
		this.controller = controller;
		this.initialData = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		if (initialData != null) {
			this.initialData.putAll(initialData);
		}
		this.editable = editable;
		this.localizationConnector = localizationConnector;
		initialize();
		updateData();
	}

	/**
	 * Gets the String data map.
	 *
	 * @return the data map
	 */
	public Map<String, String> getDataMap() {
		final Map<String, String> result = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		for (int i = 0; i < this.model.getRowCount(); i++) {
			final String key = (String) this.model.getValueAt(i, 0);
			final String value = (String) this.model.getValueAt(i, 1);
			if ((key != null) && !key.isEmpty() && (value != null) && !value.isEmpty()) {
				result.put(key, value);
			}
		}
		return result;
	}

	/**
	 * Initializes the panel.
	 */
	private void initialize() {
		setLayout(new MigLayout("", "[300px,grow]", "[150px,grow][][]"));
		this.scrollPaneContainerPanel = new JPanel(new BorderLayout());
		add(this.scrollPaneContainerPanel, "cell 0 0,grow");
		this.scrollPane = new JScrollPane();
		this.scrollPaneContainerPanel.add(this.scrollPane, BorderLayout.CENTER);
		this.columnTitles = new String[2];
		this.columnTitles[0] = this.localizationConnector.getLocalizedString("Key");
		this.columnTitles[1] = this.localizationConnector.getLocalizedString("Value");
		this.table = new JTable();
		this.scrollPane.setViewportView(this.table);
		this.table.setShowVerticalLines(false);
		this.table.setShowHorizontalLines(false);
		this.table.setFillsViewportHeight(true);
		this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting() && (StringMapViewAndEditPanel.this.removeButton != null)) {
					if (StringMapViewAndEditPanel.this.table.getSelectedRow() < 0) {
						StringMapViewAndEditPanel.this.removeButton.setEnabled(false);
					} else {
						StringMapViewAndEditPanel.this.removeButton.setEnabled(true);
					}
				}
			}
		});
		if (this.editable) {
			this.addButton = new JButton(this.localizationConnector.getLocalizedString("Add"));
			this.addButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent arg0) {
					StringMapViewAndEditPanel.this.model.addRow((Object[]) null);
				}
			});
			add(this.addButton, "flowx,cell 0 1,growx");
			this.removeButton = new JButton(this.localizationConnector.getLocalizedString("Remove"));
			this.removeButton.setEnabled(false);
			this.removeButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent arg0) {
					if (StringMapViewAndEditPanel.this.table.getSelectedRow() >= 0) {
						StringMapViewAndEditPanel.this.model.removeRow(StringMapViewAndEditPanel.this.table.convertRowIndexToModel(StringMapViewAndEditPanel.this.table.getSelectedRow()));
					}
				}
			});
			add(this.removeButton, "cell 0 1,growx");
		}
		this.parseConfigButton = new JButton(this.localizationConnector.getLocalizedString("Parse Config Values"));
		this.parseConfigButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				parseConfig();
			}
		});
		add(this.parseConfigButton, "cell 0 1,growx");
		this.clipboardCopyButton = new JButton(this.localizationConnector.getLocalizedString("Copy Data"));
		this.clipboardCopyButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				StringMapViewAndEditPanel.this.controller.setPropertiesClipboard(getDataMap());
				if (StringMapViewAndEditPanel.this.clipboardPasteButton != null) {
					StringMapViewAndEditPanel.this.clipboardPasteButton.setEnabled(true);
				}
				StringMapViewAndEditPanel.this.clipboardClearButton.setEnabled(true);
			}
		});
		add(this.clipboardCopyButton, "cell 0 2,growx");
		if (this.editable) {
			this.clipboardPasteButton = new JButton(this.localizationConnector.getLocalizedString("Paste Data"));
			this.clipboardPasteButton.setEnabled(this.controller.getPropertiesClipboard() != null);
			this.clipboardPasteButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent arg0) {
					final Map<String, String> newData = StringMapViewAndEditPanel.this.controller.getPropertiesClipboard();
					if ((newData != null) && !newData.isEmpty()) {
						StringMapViewAndEditPanel.this.initialData = StringMapViewAndEditPanel.this.controller.getPropertiesClipboard();
						updateData();
					}
				}
			});
			add(this.clipboardPasteButton, "cell 0 2,growx");
		}
		this.clipboardClearButton = new JButton(this.localizationConnector.getLocalizedString("Clear Clipboard"));
		this.clipboardClearButton.setEnabled(this.controller.getPropertiesClipboard() != null);
		this.clipboardClearButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				StringMapViewAndEditPanel.this.controller.setPropertiesClipboard(null);
				if (StringMapViewAndEditPanel.this.clipboardPasteButton != null) {
					StringMapViewAndEditPanel.this.clipboardPasteButton.setEnabled(false);
				}
				StringMapViewAndEditPanel.this.clipboardClearButton.setEnabled(false);
			}
		});
		add(this.clipboardClearButton, "cell 0 2,growx");
	}

	/**
	 * Parses the map for config values.
	 */
	private void parseConfig() {
		final Map<String, String> curData = getDataMap();
		final ConfigPanel panel = new ConfigPanel(curData);
		GenericDialog dialog;
		if (this.editable) {
			dialog = new GenericDialog(SwingUtilities.getWindowAncestor(this), this.localizationConnector.getLocalizedString("Config Values"), this.localizationConnector.getLocalizedString("OK"), this.localizationConnector.getLocalizedString("Cancel"), panel);
		} else {
			dialog = new GenericDialog(SwingUtilities.getWindowAncestor(this), this.localizationConnector.getLocalizedString("Config Values"), null, this.localizationConnector.getLocalizedString("Close"), panel);
		}
		if ((dialog.showDialog() == 0) && this.editable) {
			this.initialData.clear();
			this.initialData.putAll(panel.getUpdatedProperties());
			updateData();
		}
	}

	/**
	 * Updates data.
	 */
	private void updateData() {
		if (this.initialData != null) {
			this.model = new DefaultTableModel(null, this.columnTitles) {

				private static final long serialVersionUID = 6875316337150818665L;

				@Override
				public Class<?> getColumnClass(final int columnIndex) {
					return String.class;
				}

				@Override
				public boolean isCellEditable(final int row, final int column) {
					return StringMapViewAndEditPanel.this.editable;
				}
			};
			for (final String key : this.initialData.keySet()) {
				final String value = this.initialData.get(key);
				if ((key != null) && !key.isEmpty() && (value != null) && !value.isEmpty()) {
					final String[] row = { key, value };
					this.model.addRow(row);
				}
			}
			this.table.setModel(this.model);
		}
		final TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(this.model);
		sorter.setComparator(0, String.CASE_INSENSITIVE_ORDER);
		sorter.setComparator(1, String.CASE_INSENSITIVE_ORDER);
		final List<RowSorter.SortKey> sortKeys = new ArrayList<RowSorter.SortKey>();
		sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
		sortKeys.add(new RowSorter.SortKey(1, SortOrder.ASCENDING));
		sorter.setSortKeys(sortKeys);
		this.table.setRowSorter(sorter);
	}
}
