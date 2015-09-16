package controlinterface.swinguiadvanced.view.panel;

import helper.ConfigValue;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;

import com.google.common.base.Joiner;
import com.google.common.primitives.Ints;

import controlinterface.swinguiadvanced.view.other.TextLineNumber;

/**
 * Panel to visualize a {@link helper.ConfigValue}.
 * <p>
 * TODO: Cleanup, turn to JSON.
 *
 * @author Stefan Werner
 */
public class ConfigValuePanel extends JPanel {

	private static final String[] DEFAULT_ARRAY_SEPARATOR_NAMES = { "[newline]", "[space]", "/", ";", ",", "_" };
	private static final String[] DEFAULT_ARRAY_SEPARATORS = { "\n", " ", "/", ";", ",", "_" };
	private static final Joiner JOINER = Joiner.on("\n").skipNulls();
	private static final long serialVersionUID = 6271933743624330979L;

	private JPanel arrayHeaderPanel;
	private JScrollPane arrayScrollPane;
	private JComboBox<String> arraySeparatorComboBox;
	private JTextArea arrayTextArea;
	private JCheckBox booleanCheckBox;
	private ConfigValue configValue;
	private String curArrayTextSeparator = ConfigValuePanel.DEFAULT_ARRAY_SEPARATORS[0];
	private JLabel descriptionLabel;
	private JCheckBox emptyCheckBox;
	private JLabel keyLabel;
	private JCheckBox nullValueCheckBox;
	private JList<String> selectionList;
	private JScrollPane selectionScrollPane;
	private JSpinner spinner;
	private JTextField textField;
	private int type = 0;
	private JLabel typeLabel;

	/**
	 * Instantiates a new config value panel.
	 *
	 * @param configValue the config value
	 */
	public ConfigValuePanel(final ConfigValue configValue) {
		this.configValue = configValue;
		setLayout(new MigLayout("", "[grow]", "[][][grow]"));
		this.keyLabel = new JLabel(configValue.getKey());
		this.keyLabel.setFont(new Font("Dialog", Font.BOLD, 12));
		add(this.keyLabel, "cell 0 0,growx");
		this.typeLabel = new JLabel("(" + configValue.getFullTypeString() + ")");
		add(this.typeLabel, "cell 0 0");
		this.descriptionLabel = new JLabel(configValue.getDescription());
		add(this.descriptionLabel, "cell 0 1");
		final boolean isArray = configValue.isArray();
		final String[] options = configValue.getRawOptions();
		final int optionsType = configValue.getOptionsType();
		if (isArray && (optionsType == 1)) {
			this.type = 1; // TYPE: MULTIPLE SELECTIONS
			this.selectionScrollPane = new JScrollPane();
			add(this.selectionScrollPane, "cell 0 2,grow");
			this.selectionList = new JList<String>(options);
			this.selectionList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			final String[] curSelection = configValue.getCurrentValueStringArray();
			if (curSelection != null) {
				final Set<Integer> selectionIndices = new HashSet<Integer>();
				for (int i = 0; i < options.length; i++) {
					for (final String s : curSelection) {
						if (s.equals(options[i])) {
							selectionIndices.add(i);
							break;
						}
					}
				}
				this.selectionList.setSelectedIndices(Ints.toArray(selectionIndices));
			}
			this.selectionScrollPane.setViewportView(this.selectionList);
			this.nullValueCheckBox = new JCheckBox("unset");
			this.nullValueCheckBox.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(final ChangeEvent arg0) {
					if (!ConfigValuePanel.this.nullValueCheckBox.isSelected()) {
						ConfigValuePanel.this.selectionList.setEnabled(true);
					} else {
						ConfigValuePanel.this.selectionList.setEnabled(false);
					}
				}
			});
			if (curSelection == null) {
				this.nullValueCheckBox.setSelected(true);
			}
			add(this.nullValueCheckBox, "cell 0 2");
		} else if ((isArray && (optionsType == 2)) || (optionsType == 1)) {
			this.type = 2; // TYPE: SINGLE SELECTION
			this.selectionScrollPane = new JScrollPane();
			add(this.selectionScrollPane, "cell 0 2,grow");
			this.selectionList = new JList<String>(options);
			final String currentValue = configValue.getCurrentValueString();
			if (currentValue != null) {
				for (int i = 0; i < options.length; i++) {
					if (currentValue.equals(options[i])) {
						this.selectionList.setSelectedIndex(i);
						break;
					}
				}
			}
			this.selectionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			this.selectionScrollPane.setViewportView(this.selectionList);
			this.nullValueCheckBox = new JCheckBox("unset");
			this.nullValueCheckBox.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(final ChangeEvent arg0) {
					if (!ConfigValuePanel.this.nullValueCheckBox.isSelected()) {
						ConfigValuePanel.this.selectionList.setEnabled(true);
					} else {
						ConfigValuePanel.this.selectionList.setEnabled(false);
					}
				}
			});
			if (currentValue == null) {
				this.nullValueCheckBox.setSelected(true);
			}
			add(this.nullValueCheckBox, "cell 0 2");
		} else if (configValue.isArray() && (optionsType == 0)) {
			this.type = 3; // TYPE: ARRAY
			this.arrayScrollPane = new JScrollPane();
			this.arrayScrollPane.setPreferredSize(new Dimension(400, 150));
			add(this.arrayScrollPane, "cell 0 2,grow");
			this.arrayTextArea = new JTextArea();
			final String[] curVals = configValue.getCurrentValueStringArray();
			this.arrayTextArea.setWrapStyleWord(true);
			this.arrayTextArea.setLineWrap(true);
			this.arrayScrollPane.setViewportView(this.arrayTextArea);
			final TextLineNumber textLineNumber = new TextLineNumber(this.arrayTextArea);
			this.arrayScrollPane.setRowHeaderView(textLineNumber);
			this.arrayHeaderPanel = new JPanel();
			this.arrayScrollPane.setColumnHeaderView(this.arrayHeaderPanel);
			this.arrayHeaderPanel.setLayout(new BoxLayout(this.arrayHeaderPanel, BoxLayout.X_AXIS));
			this.arrayHeaderPanel.add(new JLabel("Separator:"));
			this.arrayHeaderPanel.add(Box.createHorizontalStrut(2));
			this.arraySeparatorComboBox = new JComboBox<String>(ConfigValuePanel.DEFAULT_ARRAY_SEPARATOR_NAMES);
			this.arraySeparatorComboBox.setSelectedIndex(0);
			this.arraySeparatorComboBox.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent arg0) {
					final int separatorIndex = ConfigValuePanel.this.arraySeparatorComboBox.getSelectedIndex();
					final String newSeparator = separatorIndex < 0 ? (String) ConfigValuePanel.this.arraySeparatorComboBox.getSelectedItem() : ConfigValuePanel.DEFAULT_ARRAY_SEPARATORS[separatorIndex];
					reformatTextArea(ConfigValuePanel.this.curArrayTextSeparator, newSeparator);
					ConfigValuePanel.this.curArrayTextSeparator = newSeparator;
				}
			});
			this.arrayHeaderPanel.add(this.arraySeparatorComboBox);
			this.arrayHeaderPanel.add(Box.createHorizontalStrut(2));
			this.emptyCheckBox = new JCheckBox("empty");
			this.emptyCheckBox.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(final ChangeEvent arg0) {
					if (ConfigValuePanel.this.emptyCheckBox.isSelected() || ConfigValuePanel.this.nullValueCheckBox.isSelected()) {
						ConfigValuePanel.this.arrayTextArea.setEnabled(false);
					} else {
						ConfigValuePanel.this.arrayTextArea.setEnabled(true);
					}
				}
			});
			this.arrayHeaderPanel.add(this.emptyCheckBox);
			this.arrayHeaderPanel.add(Box.createHorizontalGlue());
			this.nullValueCheckBox = new JCheckBox("unset");
			this.nullValueCheckBox.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(final ChangeEvent arg0) {
					if (ConfigValuePanel.this.nullValueCheckBox.isSelected()) {
						ConfigValuePanel.this.arrayTextArea.setEnabled(false);
						ConfigValuePanel.this.emptyCheckBox.setEnabled(false);
					} else {
						if (!ConfigValuePanel.this.emptyCheckBox.isSelected()) {
							ConfigValuePanel.this.arrayTextArea.setEnabled(true);
						}
						ConfigValuePanel.this.emptyCheckBox.setEnabled(true);
					}
				}
			});
			this.arrayHeaderPanel.add(this.nullValueCheckBox);
			if (curVals != null) {
				if (curVals.length > 0) {
					this.arrayTextArea.setText(ConfigValuePanel.JOINER.join(curVals));
				} else {
					this.emptyCheckBox.setSelected(true);
				}
			} else {
				this.arrayTextArea.setEnabled(false);
				this.nullValueCheckBox.setSelected(true);
			}
		} else if (configValue.isBoolean()) {
			this.type = 4; // TYPE: BOOLEAN
			this.booleanCheckBox = new JCheckBox("Enable");
			final Boolean currentValue = configValue.getCurrentValueBoolean();
			if (currentValue != null) {
				this.booleanCheckBox.setSelected(currentValue);
			}
			add(this.booleanCheckBox, "cell 0 2,growx");
			this.nullValueCheckBox = new JCheckBox("unset");
			this.nullValueCheckBox.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(final ChangeEvent arg0) {
					if (!ConfigValuePanel.this.nullValueCheckBox.isSelected()) {
						ConfigValuePanel.this.booleanCheckBox.setEnabled(true);
					} else {
						ConfigValuePanel.this.booleanCheckBox.setEnabled(false);
					}
				}
			});
			if (currentValue == null) {
				this.nullValueCheckBox.setSelected(true);
			}
			add(this.nullValueCheckBox, "cell 0 2");
		} else if (configValue.isString()) {
			this.type = 5; // TYPE: STRING
			final String currentValue = configValue.getCurrentValueString();
			this.textField = new JTextField(currentValue);
			this.textField.selectAll();
			add(this.textField, "cell 0 2,growx");
			this.nullValueCheckBox = new JCheckBox("unset");
			this.nullValueCheckBox.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(final ChangeEvent arg0) {
					if (!ConfigValuePanel.this.nullValueCheckBox.isSelected()) {
						ConfigValuePanel.this.textField.setEnabled(true);
					} else {
						ConfigValuePanel.this.textField.setEnabled(false);
					}
				}
			});
			if (currentValue == null) {
				this.nullValueCheckBox.setSelected(true);
			}
			add(this.nullValueCheckBox, "cell 0 2");
		} else {
			this.type = 6; // TYPE: NUM SELECTION
			SpinnerNumberModel model;
			Number cur;
			Number min;
			Number max;
			Number step;
			boolean isNull = false;
			if (configValue.isDouble()) {
				step = new Double(1);
				min = configValue.getMinValDouble() != null ? configValue.getMinValDouble() : null;
				max = configValue.getMaxValDouble() != null ? configValue.getMaxValDouble() : null;
				if (configValue.getCurrentValueDouble() != null) {
					cur = configValue.getCurrentValueDouble();
				} else if (min != null) {
					cur = min;
				} else {
					cur = new Double(0);
					isNull = true;
				}
			} else if (configValue.isFloat()) {
				step = new Float(1);
				min = configValue.getMinValFloat() != null ? configValue.getMinValFloat() : null;
				max = configValue.getMaxValFloat() != null ? configValue.getMaxValFloat() : null;
				if (configValue.getCurrentValueFloat() != null) {
					cur = configValue.getCurrentValueFloat();
				} else if (min != null) {
					cur = min;
				} else {
					cur = new Float(0);
					isNull = true;
				}
			} else if (configValue.isInteger()) {
				step = new Integer(1);
				min = configValue.getMinValInteger() != null ? configValue.getMinValInteger() : null;
				max = configValue.getMaxValInteger() != null ? configValue.getMaxValInteger() : null;
				if (configValue.getCurrentValueInteger() != null) {
					cur = configValue.getCurrentValueInteger();
				} else if (min != null) {
					cur = min;
				} else {
					cur = new Integer(0);
					isNull = true;
				}
			} else if (configValue.isLong()) {
				step = new Long(1);
				min = configValue.getMinValLong() != null ? configValue.getMinValLong() : null;
				max = configValue.getMaxValLong() != null ? configValue.getMaxValLong() : null;
				if (configValue.getCurrentValueLong() != null) {
					cur = configValue.getCurrentValueLong();
				} else if (min != null) {
					cur = min;
				} else {
					cur = new Long(0);
					isNull = true;
				}
			} else {
				this.type = 0;
				return;
			}
			try {
				model = new SpinnerNumberModel(cur, (Comparable<?>) min, (Comparable<?>) max, step);
				this.spinner = new JSpinner(model);
				add(this.spinner, "cell 0 2,growx");
				this.nullValueCheckBox = new JCheckBox("unset");
				this.nullValueCheckBox.addChangeListener(new ChangeListener() {
					@Override
					public void stateChanged(final ChangeEvent arg0) {
						if (!ConfigValuePanel.this.nullValueCheckBox.isSelected()) {
							ConfigValuePanel.this.spinner.setEnabled(true);
						} else {
							ConfigValuePanel.this.spinner.setEnabled(false);
						}
					}
				});
				if (isNull) {
					this.nullValueCheckBox.setSelected(true);
				}
				add(this.nullValueCheckBox, "cell 0 2");
			} catch (final IllegalArgumentException e) {
				this.type = 0;
			}
		}
	}

	/**
	 * Gets the updated config value.
	 *
	 * @return the updated config value
	 */
	public ConfigValue getUpdatedConfigValue() {
		switch (this.type) {
		case 1: // TYPE: MULTIPLE SELECTIONS
			if (this.nullValueCheckBox.isSelected()) {
				this.configValue.setRawCurrentValue(null);
			} else {
				if (this.selectionList.isSelectionEmpty()) {
					this.configValue.setRawCurrentValue("");
				} else {
					this.configValue.setRawCurrenValueArray(this.selectionList.getSelectedValuesList().toArray());
				}
			}
			break;
		case 2: // TYPE: SINGLE SELECTION
			if (this.nullValueCheckBox.isSelected()) {
				this.configValue.setRawCurrentValue(null);
			} else {
				if (this.selectionList.isSelectionEmpty()) {
					this.configValue.setRawCurrentValue("");
				} else {
					this.configValue.setRawCurrentValue(this.selectionList.getSelectedValue());
				}
			}
			break;
		case 3: // TYPE: ARRAY
			if (this.nullValueCheckBox.isSelected()) {
				this.configValue.setRawCurrentValue(null);
			} else if (this.emptyCheckBox.isSelected()) {
				this.configValue.setRawCurrenValueArray(new String[0]);
			} else {
				final int separatorIndex = this.arraySeparatorComboBox.getSelectedIndex();
				final String separator = separatorIndex < 0 ? (String) this.arraySeparatorComboBox.getSelectedItem() : ConfigValuePanel.DEFAULT_ARRAY_SEPARATORS[separatorIndex];
				final String array[] = this.arrayTextArea.getText().split(separator);
				if (this.configValue.isBoolean()) {
					final List<Boolean> list = new ArrayList<Boolean>();
					for (final String s : array) {
						final Boolean value = ConfigValue.getBoolean(s);
						if (value != null) {
							list.add(value);
						}
					}
					Collections.sort(list);
					this.configValue.setCurrentValueBooleanArray(list.toArray(new Boolean[0]));
				} else if (this.configValue.isDouble()) {
					final List<Double> list = new ArrayList<Double>();
					for (final String s : array) {
						final Double value = ConfigValue.getDouble(s);
						if (value != null) {
							list.add(value);
						}
					}
					Collections.sort(list);
					this.configValue.setCurrentValueDoubleArray(list.toArray(new Double[0]));
				} else if (this.configValue.isFloat()) {
					final List<Float> list = new ArrayList<Float>();
					for (final String s : array) {
						final Float value = ConfigValue.getFloat(s);
						if (value != null) {
							list.add(value);
						}
					}
					Collections.sort(list);
					this.configValue.setCurrentValueFloatArray(list.toArray(new Float[0]));
				} else if (this.configValue.isInteger()) {
					final List<Integer> list = new ArrayList<Integer>();
					for (final String s : array) {
						final Integer value = ConfigValue.getInteger(s);
						if (value != null) {
							list.add(value);
						}
					}
					Collections.sort(list);
					this.configValue.setCurrentValueIntegerArray(list.toArray(new Integer[0]));
				} else if (this.configValue.isLong()) {
					final List<Long> list = new ArrayList<Long>();
					for (final String s : array) {
						final Long value = ConfigValue.getLong(s);
						if (value != null) {
							list.add(value);
						}
					}
					Collections.sort(list);
					this.configValue.setCurrentValueLongArray(list.toArray(new Long[0]));
				} else if (this.configValue.isString()) {
					Arrays.sort(array, String.CASE_INSENSITIVE_ORDER);
					this.configValue.setCurrentValueStringArray(array);
				}
			}
			break;
		case 4: // TYPE: BOOLEAN
			if (this.nullValueCheckBox.isSelected()) {
				this.configValue.setRawCurrentValue(null);
			} else {
				this.configValue.setCurrentValueBoolean(this.booleanCheckBox.isSelected());
			}
			break;
		case 5: // TYPE: STRING
			if (this.nullValueCheckBox.isSelected()) {
				this.configValue.setRawCurrentValue(null);
			} else {
				this.configValue.setCurrentValueString(this.textField.getText());
			}
			break;
		case 6: // TYPE: NUM SELECTION
			if (this.nullValueCheckBox.isSelected()) {
				this.configValue.setRawCurrentValue(null);
			} else {
				this.configValue.setRawCurrentValue(this.spinner.getValue());
			}
			break;
		}
		return this.configValue;
	}

	/**
	 * Reformat text area when a new separator is selected.
	 *
	 * @param oldSep the old separator
	 * @param newSep the new separator
	 */
	private void reformatTextArea(final String oldSep, final String newSep) {
		if (this.arrayTextArea != null) {
			final String newText = this.arrayTextArea.getText().replace(oldSep, newSep);
			this.arrayTextArea.setText(newText);
		}
	}
}
