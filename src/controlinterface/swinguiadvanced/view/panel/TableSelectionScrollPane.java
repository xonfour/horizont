package controlinterface.swinguiadvanced.view.panel;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

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

import controlinterface.swinguiadvanced.view.other.TableSelectionListener;

/**
 * Visualizes a sortable and modifiable table inside a dynamic JScrollPane.
 * 
 * @author Stefan Werner
 */
public class TableSelectionScrollPane extends JScrollPane {

	public static final int DEFAULT_VISIBLE_ROWS = 15;
	private static final long serialVersionUID = -4829037427314909650L;

	private String[] columnTitles;
	private DefaultTableModel model;
	private JTable table;

	/**
	 * Instantiates a new table selection scroll pane. Use this one if there is just one column.
	 *
	 * @param data the data array
	 * @param singleColumnTitle the column title
	 */
	public TableSelectionScrollPane(final String[] data, final String singleColumnTitle) {
		final String[][] realData = new String[data.length][1];
		for (int i = 0; i < data.length; i++) {
			realData[i][0] = data[i];
		}
		initialize(realData, singleColumnTitle);
	}

	/**
	 * Instantiates a new table selection scroll pane.
	 *
	 * @param data the 2D data array
	 * @param columnTitles the column titles
	 * @wbp.parser.constructor
	 */
	public TableSelectionScrollPane(final String[][] data, final String... columnTitles) {
		initialize(data, columnTitles);
	}

	/**
	 * Adds a table selection listener. It is called when an item is (de)selected.
	 *
	 * @param tableSelectionListener the table selection listener
	 */
	public void addTableSelectionListener(final TableSelectionListener tableSelectionListener) {
		this.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					tableSelectionListener.onComponentSelected(TableSelectionScrollPane.this.table.getSelectedRow());
				}
			}
		});
	}

	/**
	 * Gets the selected index.
	 *
	 * @return the selected index
	 */
	public int getSelectedIndex() {
		return this.table.convertRowIndexToModel(this.table.getSelectedRow());
	}

	/**
	 * Initializes the pane.
	 *
	 * @param data the 2D data array
	 * @param columnTitles the column titles
	 */
	private void initialize(final String[][] data, final String... columnTitles) {
		this.columnTitles = columnTitles;
		this.table = new JTable();
		setViewportView(this.table);
		this.table.setShowVerticalLines(false);
		this.table.setShowHorizontalLines(false);
		this.table.setFillsViewportHeight(true);
		this.table.setPreferredScrollableViewportSize(new Dimension(this.table.getPreferredScrollableViewportSize().width, TableSelectionScrollPane.DEFAULT_VISIBLE_ROWS * this.table.getRowHeight()));
		this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		this.model = new DefaultTableModel(data, columnTitles) {

			private static final long serialVersionUID = -4930839050847578012L;

			@Override
			public Class<?> getColumnClass(final int columnIndex) {
				return String.class;
			}

			@Override
			public boolean isCellEditable(final int row, final int column) {
				return false;
			}
		};
		this.table.setModel(this.model);
		final TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(this.table.getModel());
		for (int i = 0; i < columnTitles.length; i++) {
			sorter.setComparator(i, String.CASE_INSENSITIVE_ORDER);
		}
		final List<RowSorter.SortKey> sortKeys = new ArrayList<RowSorter.SortKey>();
		for (int i = 0; i < columnTitles.length; i++) {
			sortKeys.add(new RowSorter.SortKey(i, SortOrder.ASCENDING));
		}
		sorter.setSortKeys(sortKeys);
		this.table.setRowSorter(sorter);
	}

	/**
	 * Updates data.
	 *
	 * @param data the 2D data array
	 */
	public void updateData(final String[][] data) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				TableSelectionScrollPane.this.model.setDataVector(data, TableSelectionScrollPane.this.columnTitles);
				TableSelectionScrollPane.this.table.clearSelection();
			}
		});
	}
}
