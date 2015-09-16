package helper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;

/**
 * Represents a single configuration value used to exchange data beween modules, between a module and a control interface and to store configuration data in the
 * database.
 * <p>
 * TODO: This is a monster. Rework and change to JSON.
 *
 * @author Stefan Werner
 */
public final class ConfigValue implements Comparable<ConfigValue> {

	/**
	 * The enum VALUE_TYPE.
	 */
	public static enum VALUE_TYPE {
		INTEGER, LONG, STRING
	};

	public static final String ARRAY_END = "]";
	public static final String ARRAY_START = "[";
	public static final String ARRAY_VALUES_SEPARATOR = ";";
	public static final Joiner ARRAY_JOINER = Joiner.on(ConfigValue.ARRAY_VALUES_SEPARATOR).skipNulls();
	public static final String CLASS_ARRAY = "[]";
	public static final String CLASS_BOOLEAN = "Boolean";
	public static final String CLASS_DOUBLE = "Double";
	public static final String CLASS_FLOAT = "Float";
	public static final String CLASS_INTEGER = "Integer";
	public static final String CLASS_LONG = "Long";
	public static final String CLASS_STRING = "String";
	public static final String OPTIONS_SEPARATOR = "/";
	public static final Joiner optionsJoiner = Joiner.on(ConfigValue.OPTIONS_SEPARATOR).skipNulls();
	public static final Pattern PATTERN = Pattern.compile("(?<!\\\\)\\{(.*?)(?<!\\\\)\\}");
	public static final Pattern PATTERN_ARRAY = Pattern.compile("(?<!\\\\)\\[(.*?)(?<!\\\\)\\]");
	public static final Pattern PATTERN_ARRAY_VALUES = Pattern.compile("(?<!\\\\)(?<=" + ConfigValue.ARRAY_VALUES_SEPARATOR + "|^)(.*?)(?<!\\\\)(?=" + ConfigValue.ARRAY_VALUES_SEPARATOR + "|$)");
	public static final Pattern PATTERN_OPTION_VALUES = Pattern.compile("(?<!\\\\)(?<=" + ConfigValue.OPTIONS_SEPARATOR + "|^)(.*?)(?<!\\\\)(?=" + ConfigValue.OPTIONS_SEPARATOR + "|$)");
	public static final Pattern PATTERN_RANGE = Pattern.compile("^([0-9[\\.,\\-]].*?)" + ConfigValue.RANGE_SEPARATOR + "([0-9[\\.,\\-]].*?)$");
	public static final String PREFIX___CUR_VAL = "cur:";
	public static final String PREFIX___DESC = "desc:";
	public static final String PREFIX___OPTS = "opts:";
	public static final String PREFIX___RANGE = "range:";
	public static final String PREFIX___TYPE = "type:";
	public static final String RANGE_SEPARATOR = "~";

	/**
	 * Array array to escaped string array.
	 *
	 * @param array the array
	 * @return the string[]
	 */
	private static String[] arrayArrayToEscapedStringArray(final Object[][] array) {
		if (array == null) {
			return null;
		}
		final String[] sArray = new String[array.length];
		for (int i = 0; i < array.length; i++) {
			if (array != null) {
				sArray[i] = ConfigValue.arrayToEscapedString(array[i]);
			}
		}
		return sArray;
	}

	/**
	 * Array to escaped string.
	 *
	 * @param array the array
	 * @return the string
	 */
	private static String arrayToEscapedString(final Object[] array) {
		if (array == null) {
			return null;
		}
		final List<String> result = new ArrayList<String>();
		for (final Object o : array) {
			if (o != null) {
				result.add(ConfigValue.escape(o.toString()));
			}
		}
		return "[" + ConfigValue.ARRAY_JOINER.join(result) + "]";
	}

	/**
	 * Array to escaped string array.
	 *
	 * @param array the array
	 * @return the string[]
	 */
	private static String[] arrayToEscapedStringArray(final Object[] array) {
		if (array == null) {
			return null;
		}
		final List<String> result = new ArrayList<String>();
		for (final Object o : array) {
			if (o != null) {
				result.add(ConfigValue.escape(o.toString()));
			}
		}
		return result.toArray(new String[0]);
	}

	/**
	 * Escape.
	 *
	 * @param unescapedString the unescaped string
	 * @return the string
	 */
	private static String escape(final String unescapedString) {
		if (unescapedString == null) {
			return null;
		}
		return unescapedString.replace("{", "\\{").replace("}", "\\}").replace("[", "\\[").replace("]", "\\]").replace(";", "\\;");
	}

	/**
	 * Gets the boolean.
	 *
	 * @param s the s
	 * @return the boolean
	 */
	public static Boolean getBoolean(final String s) {
		if (s == null) {
			return null;
		}
		return Boolean.valueOf(s);
	}

	/**
	 * Gets the boolean array.
	 *
	 * @param s the s
	 * @return the boolean array
	 */
	public static Boolean[] getBooleanArray(String s) {
		if ((s == null) || !s.startsWith("[") || !s.endsWith("]")) {
			return null;
		}
		s = s.substring(1, s.length() - 1);
		final List<Boolean> values = new ArrayList<Boolean>();
		final Matcher matcher = ConfigValue.PATTERN_ARRAY_VALUES.matcher(s);
		while (matcher.find()) {
			values.add(Boolean.valueOf(matcher.group(1)));
		}
		return values.toArray(new Boolean[0]);
	}

	/**
	 * Gets the double.
	 *
	 * @param s the s
	 * @return the double
	 */
	public static Double getDouble(final String s) {
		if (s == null) {
			return null;
		}
		try {
			return Double.valueOf(s);
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Gets the double array.
	 *
	 * @param s the s
	 * @return the double array
	 */
	public static Double[] getDoubleArray(String s) {
		if ((s == null) || !s.startsWith("[") || !s.endsWith("]")) {
			return null;
		}
		s = s.substring(1, s.length() - 1);
		final List<Double> values = new ArrayList<Double>();
		final Matcher matcher = ConfigValue.PATTERN_ARRAY_VALUES.matcher(s);
		while (matcher.find()) {
			try {
				values.add(Double.valueOf(matcher.group(1)));
			} catch (final NumberFormatException e) {
				// ignored
			}
		}
		return values.toArray(new Double[0]);
	}

	/**
	 * Gets the float.
	 *
	 * @param s the s
	 * @return the float
	 */
	public static Float getFloat(final String s) {
		if (s == null) {
			return null;
		}
		try {
			return Float.valueOf(s);
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Gets the float array.
	 *
	 * @param s the s
	 * @return the float array
	 */
	public static Float[] getFloatArray(String s) {
		if ((s == null) || !s.startsWith("[") || !s.endsWith("]")) {
			return null;
		}
		s = s.substring(1, s.length() - 1);
		final List<Float> values = new ArrayList<Float>();
		final Matcher matcher = ConfigValue.PATTERN_ARRAY_VALUES.matcher(s);
		while (matcher.find()) {
			try {
				values.add(Float.valueOf(matcher.group(1)));
			} catch (final NumberFormatException e) {
				// ignored
			}
		}
		return values.toArray(new Float[0]);
	}

	/**
	 * Gets the integer.
	 *
	 * @param s the s
	 * @return the integer
	 */
	public static Integer getInteger(final String s) {
		if (s == null) {
			return null;
		}
		try {
			return Integer.valueOf(s);
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Gets the integer array.
	 *
	 * @param s the s
	 * @return the integer array
	 */
	public static Integer[] getIntegerArray(String s) {
		if ((s == null) || !s.startsWith("[") || !s.endsWith("]")) {
			return null;
		}
		s = s.substring(1, s.length() - 1);
		final List<Integer> values = new ArrayList<Integer>();
		final Matcher matcher = ConfigValue.PATTERN_ARRAY_VALUES.matcher(s);
		while (matcher.find()) {
			try {
				values.add(Integer.valueOf(matcher.group(1)));
			} catch (final NumberFormatException e) {
				// ignored
			}
		}
		return values.toArray(new Integer[0]);
	}

	/**
	 * Gets the long.
	 *
	 * @param s the s
	 * @return the long
	 */
	public static Long getLong(final String s) {
		if (s == null) {
			return null;
		}
		try {
			return Long.valueOf(s);
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Gets the long array.
	 *
	 * @param s the s
	 * @return the long array
	 */
	public static Long[] getLongArray(String s) {
		if ((s == null) || !s.startsWith("[") || !s.endsWith("]")) {
			return null;
		}
		s = s.substring(1, s.length() - 1);
		final List<Long> values = new ArrayList<Long>();
		final Matcher matcher = ConfigValue.PATTERN_ARRAY_VALUES.matcher(s);
		while (matcher.find()) {
			try {
				values.add(Long.valueOf(matcher.group(1)));
			} catch (final NumberFormatException e) {
				// ignored
			}
		}
		return values.toArray(new Long[0]);
	}

	/**
	 * Gets the string array.
	 *
	 * @param array the array
	 * @return the string array
	 */
	private static String[] getStringArray(final Object[] array) {
		final String[] stringArray = new String[array.length];
		for (int i = 0; i < array.length; i++) {
			if (array[i] != null) {
				stringArray[i] = array[i].toString();
			}
		}
		return stringArray;
	}

	/**
	 * Gets the string array.
	 *
	 * @param s the s
	 * @return the string array
	 */
	private static String[] getStringArray(String s) {
		if ((s == null) || !s.startsWith("[") || !s.endsWith("]")) {
			return null;
		}
		s = s.substring(1, s.length() - 1);
		final List<String> values = new ArrayList<String>();
		final Matcher matcher = ConfigValue.PATTERN_ARRAY_VALUES.matcher(s);
		while (matcher.find()) {
			values.add(ConfigValue.unescape(matcher.group(1)));
		}
		return values.toArray(new String[0]);
	}

	/**
	 * Unescape.
	 *
	 * @param escapedString the escaped string
	 * @return the string
	 */
	private static String unescape(final String escapedString) {
		if (escapedString == null) {
			return null;
		}
		return escapedString.replace("\\{", "{").replace("\\}", "}").replace("\\[", "[").replace("\\]", "]").replace("\\;", ";");
	}

	private String descriptionString = null;

	private String escapedCurrentValue = null;

	private String escapedMaxVal = null;

	private String escapedMinVal = null;

	private String[] escapedOptions = null;

	private final String key;

	private String type = "";

	/**
	 * Instantiates a new config value.
	 *
	 * @param key the key
	 */
	public ConfigValue(final String key) {
		if (key != null) {
			this.key = CharMatcher.JAVA_ISO_CONTROL.removeFrom(key);
		} else {
			this.key = null;
		}
	}

	/**
	 * Instantiates a new config value.
	 *
	 * @param key the key
	 * @param stringToParse the string to parse
	 */
	public ConfigValue(final String key, final String stringToParse) {
		this(key);
		if (stringToParse != null) {
			final Matcher matcher = ConfigValue.PATTERN.matcher(stringToParse);
			byte b = 0;
			while (matcher.find()) {
				final String found = matcher.group(1);
				if (found.startsWith(ConfigValue.PREFIX___TYPE) && ((b & 1) == 0)) {
					this.type = ConfigValue.unescape(found.substring(ConfigValue.PREFIX___TYPE.length(), found.length()));
					b |= 1;
				} else if (found.startsWith(ConfigValue.PREFIX___CUR_VAL) && ((b & 2) == 0)) {
					this.escapedCurrentValue = found.substring(ConfigValue.PREFIX___CUR_VAL.length(), found.length());
					b |= 2;
				} else if (found.startsWith(ConfigValue.PREFIX___OPTS) && ((b & 4) == 0)) {
					final Matcher optionsMatcher = ConfigValue.PATTERN_OPTION_VALUES.matcher(found.substring(ConfigValue.PREFIX___OPTS.length(), found.length()));
					final List<String> tmpOptions = new ArrayList<String>();
					while (optionsMatcher.find()) {
						tmpOptions.add(optionsMatcher.group(1));
					}
					this.escapedOptions = tmpOptions.toArray(new String[0]);
					b |= 4;
				} else if (found.startsWith(ConfigValue.PREFIX___RANGE) && ((b & 8) == 0)) {
					final Matcher rangeMatcher = ConfigValue.PATTERN_RANGE.matcher(found.substring(ConfigValue.PREFIX___RANGE.length(), found.length()));
					if (rangeMatcher.find()) {
						this.escapedMinVal = rangeMatcher.group(1);
						this.escapedMaxVal = rangeMatcher.group(2);
					}
					b |= 8;
				} else if (found.startsWith(ConfigValue.PREFIX___DESC) && ((b & 16) == 0)) {
					this.descriptionString = ConfigValue.unescape(found.substring(ConfigValue.PREFIX___DESC.length(), found.length()));
					b |= 16;
				}
			}
		}
	}

	@Override
	public int compareTo(final ConfigValue arg0) {
		return String.CASE_INSENSITIVE_ORDER.compare(this.key, arg0.key);
	}

	/**
	 * Gets the current value boolean.
	 *
	 * @return the current value boolean
	 */
	public Boolean getCurrentValueBoolean() {
		return ConfigValue.getBoolean(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value boolean array.
	 *
	 * @return the current value boolean array
	 */
	public Boolean[] getCurrentValueBooleanArray() {
		return ConfigValue.getBooleanArray(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value double.
	 *
	 * @return the current value double
	 */
	public Double getCurrentValueDouble() {
		return ConfigValue.getDouble(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value double array.
	 *
	 * @return the current value double array
	 */
	public Double[] getCurrentValueDoubleArray() {
		return ConfigValue.getDoubleArray(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value float.
	 *
	 * @return the current value float
	 */
	public Float getCurrentValueFloat() {
		return ConfigValue.getFloat(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value float array.
	 *
	 * @return the current value float array
	 */
	public Float[] getCurrentValueFloatArray() {
		return ConfigValue.getFloatArray(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value integer.
	 *
	 * @return the current value integer
	 */
	public Integer getCurrentValueInteger() {
		return ConfigValue.getInteger(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value integer array.
	 *
	 * @return the current value integer array
	 */
	public Integer[] getCurrentValueIntegerArray() {
		return ConfigValue.getIntegerArray(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value long.
	 *
	 * @return the current value long
	 */
	public Long getCurrentValueLong() {
		return ConfigValue.getLong(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value long array.
	 *
	 * @return the current value long array
	 */
	public Long[] getCurrentValueLongArray() {
		return ConfigValue.getLongArray(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value string.
	 *
	 * @return the current value string
	 */
	public String getCurrentValueString() {
		return ConfigValue.unescape(this.escapedCurrentValue);
	}

	/**
	 * Gets the current value string array.
	 *
	 * @return the current value string array
	 */
	public String[] getCurrentValueStringArray() {
		return ConfigValue.getStringArray(this.escapedCurrentValue);
	}

	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	public String getDescription() {
		return this.descriptionString;
	}

	/**
	 * Gets the full type string.
	 *
	 * @return the full type string
	 */
	public String getFullTypeString() {
		return this.type;
	}

	/**
	 * Gets the key.
	 *
	 * @return the key
	 */
	public String getKey() {
		return this.key;
	}

	/**
	 * Gets the max val boolean array.
	 *
	 * @return the max val boolean array
	 */
	public Boolean[] getMaxValBooleanArray() {
		return ConfigValue.getBooleanArray(this.escapedMaxVal);
	}

	/**
	 * Gets the max val double.
	 *
	 * @return the max val double
	 */
	public Double getMaxValDouble() {
		return ConfigValue.getDouble(this.escapedMaxVal);
	}

	/**
	 * Gets the max val double array.
	 *
	 * @return the max val double array
	 */
	public Double[] getMaxValDoubleArray() {
		return ConfigValue.getDoubleArray(this.escapedMaxVal);
	}

	/**
	 * Gets the max val float.
	 *
	 * @return the max val float
	 */
	public Float getMaxValFloat() {
		return ConfigValue.getFloat(this.escapedMaxVal);
	}

	/**
	 * Gets the max val float array.
	 *
	 * @return the max val float array
	 */
	public Float[] getMaxValFloatArray() {
		return ConfigValue.getFloatArray(this.escapedMaxVal);
	}

	/**
	 * Gets the max val integer.
	 *
	 * @return the max val integer
	 */
	public Integer getMaxValInteger() {
		return ConfigValue.getInteger(this.escapedMaxVal);
	}

	/**
	 * Gets the max val integer array.
	 *
	 * @return the max val integer array
	 */
	public Integer[] getMaxValIntegerArray() {
		return ConfigValue.getIntegerArray(this.escapedMaxVal);
	}

	/**
	 * Gets the max val long.
	 *
	 * @return the max val long
	 */
	public Long getMaxValLong() {
		return ConfigValue.getLong(this.escapedMaxVal);
	}

	/**
	 * Gets the max val long array.
	 *
	 * @return the max val long array
	 */
	public Long[] getMaxValLongArray() {
		return ConfigValue.getLongArray(this.escapedMaxVal);
	}

	/**
	 * Gets the max val string.
	 *
	 * @return the max val string
	 */
	public String getMaxValString() {
		return ConfigValue.unescape(this.escapedMaxVal);
	}

	/**
	 * Gets the max val string array.
	 *
	 * @return the max val string array
	 */
	public String[] getMaxValStringArray() {
		return ConfigValue.getStringArray(this.escapedMaxVal);
	}

	/**
	 * Gets the min val boolean array.
	 *
	 * @return the min val boolean array
	 */
	public Boolean[] getMinValBooleanArray() {
		return ConfigValue.getBooleanArray(this.escapedMinVal);
	}

	/**
	 * Gets the min val double.
	 *
	 * @return the min val double
	 */
	public Double getMinValDouble() {
		return ConfigValue.getDouble(this.escapedMinVal);
	}

	/**
	 * Gets the min val double array.
	 *
	 * @return the min val double array
	 */
	public Double[] getMinValDoubleArray() {
		return ConfigValue.getDoubleArray(this.escapedMinVal);
	}

	/**
	 * Gets the min val float.
	 *
	 * @return the min val float
	 */
	public Float getMinValFloat() {
		return ConfigValue.getFloat(this.escapedMinVal);
	}

	/**
	 * Gets the min val float array.
	 *
	 * @return the min val float array
	 */
	public Float[] getMinValFloatArray() {
		return ConfigValue.getFloatArray(this.escapedMinVal);
	}

	/**
	 * Gets the min val integer.
	 *
	 * @return the min val integer
	 */
	public Integer getMinValInteger() {
		return ConfigValue.getInteger(this.escapedMinVal);
	}

	/**
	 * Gets the min val integer array.
	 *
	 * @return the min val integer array
	 */
	public Integer[] getMinValIntegerArray() {
		return ConfigValue.getIntegerArray(this.escapedMinVal);
	}

	/**
	 * Gets the min val long.
	 *
	 * @return the min val long
	 */
	public Long getMinValLong() {
		return ConfigValue.getLong(this.escapedMinVal);
	}

	/**
	 * Gets the min val long array.
	 *
	 * @return the min val long array
	 */
	public Long[] getMinValLongArray() {
		return ConfigValue.getLongArray(this.escapedMinVal);
	}

	/**
	 * Gets the min val string.
	 *
	 * @return the min val string
	 */
	public String getMinValString() {
		return ConfigValue.unescape(this.escapedMinVal);
	}

	/**
	 * Gets the min val string array.
	 *
	 * @return the min val string array
	 */
	public String[] getMinValStringArray() {
		return ConfigValue.getStringArray(this.escapedMinVal);
	}

	/**
	 * Gets the options boolean array.
	 *
	 * @return the options boolean array
	 */
	public Boolean[][] getOptionsBooleanArray() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Boolean[][] result = new Boolean[this.escapedOptions.length][];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getBooleanArray(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options double.
	 *
	 * @return the options double
	 */
	public Double[] getOptionsDouble() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Double[] result = new Double[this.escapedOptions.length];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getDouble(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options double array.
	 *
	 * @return the options double array
	 */
	public Double[][] getOptionsDoubleArray() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Double[][] result = new Double[this.escapedOptions.length][];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getDoubleArray(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options float.
	 *
	 * @return the options float
	 */
	public Float[] getOptionsFloat() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Float[] result = new Float[this.escapedOptions.length];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getFloat(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options float array.
	 *
	 * @return the options float array
	 */
	public Float[][] getOptionsFloatArray() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Float[][] result = new Float[this.escapedOptions.length][];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getFloatArray(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options integer.
	 *
	 * @return the options integer
	 */
	public Integer[] getOptionsInteger() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Integer[] result = new Integer[this.escapedOptions.length];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getInteger(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options integer array.
	 *
	 * @return the options integer array
	 */
	public Integer[][] getOptionsIntegerArray() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Integer[][] result = new Integer[this.escapedOptions.length][];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getIntegerArray(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options long.
	 *
	 * @return the options long
	 */
	public Long[] getOptionsLong() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Long[] result = new Long[this.escapedOptions.length];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getLong(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options long array.
	 *
	 * @return the options long array
	 */
	public Long[][] getOptionsLongArray() {
		if (this.escapedOptions == null) {
			return null;
		}
		final Long[][] result = new Long[this.escapedOptions.length][];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			result[i] = ConfigValue.getLongArray(this.escapedOptions[i]);
		}
		return result;
	}

	/**
	 * Gets the options string.
	 *
	 * @return the options string
	 */
	public String[] getOptionsString() {
		if (this.escapedOptions == null) {
			return null;
		}
		final List<String> options = new ArrayList<String>();
		for (final String s : this.escapedOptions) {
			options.add(ConfigValue.unescape(s));
		}
		return options.toArray(new String[0]);
	}

	/**
	 * Gets the options string array.
	 *
	 * @return the options string array
	 */
	public String[][] getOptionsStringArray() {
		final String[][] result = new String[this.escapedOptions.length][];
		for (int i = 0; i < this.escapedOptions.length; i++) {
			final Matcher matcher = ConfigValue.PATTERN_ARRAY.matcher(this.escapedOptions[i]);
			if (matcher.find()) {
				result[i] = ConfigValue.getStringArray(matcher.group(1));
			}
		}
		return result;
	}

	/**
	 * Gets the options type.
	 *
	 * @return the options type
	 */
	// 0 = empty/no options, 1 = multiselect plain values, 2 = single select an array
	public int getOptionsType() {
		if ((this.escapedOptions == null) || (this.escapedOptions.length == 0) || ((this.escapedOptions.length == 1) && this.escapedOptions[0].isEmpty())) {
			return 0;
		}
		final Matcher matcher = ConfigValue.PATTERN_ARRAY.matcher(this.escapedOptions[0]);
		if (matcher.find()) {
			return 2;
		} else {
			return 1;
		}
	}

	/**
	 * Gets the raw options.
	 *
	 * @return the raw options
	 */
	public String[] getRawOptions() {
		return getOptionsString();
	}

	/**
	 * Checks if is array.
	 *
	 * @return true, if is array
	 */
	public boolean isArray() {
		return this.type.endsWith(ConfigValue.CLASS_ARRAY);
	}

	/**
	 * Checks if is boolean.
	 *
	 * @return true, if is boolean
	 */
	public boolean isBoolean() {
		return this.type.startsWith(ConfigValue.CLASS_BOOLEAN);
	}

	/**
	 * Checks if is double.
	 *
	 * @return true, if is double
	 */
	public boolean isDouble() {
		return this.type.startsWith(ConfigValue.CLASS_DOUBLE);
	}

	/**
	 * Checks if is float.
	 *
	 * @return true, if is float
	 */
	public boolean isFloat() {
		return this.type.startsWith(ConfigValue.CLASS_FLOAT);
	}

	/**
	 * Checks if is integer.
	 *
	 * @return true, if is integer
	 */
	public boolean isInteger() {
		return this.type.startsWith(ConfigValue.CLASS_INTEGER);
	}

	/**
	 * Checks if is long.
	 *
	 * @return true, if is long
	 */
	public boolean isLong() {
		return this.type.startsWith(ConfigValue.CLASS_LONG);
	}

	/**
	 * Checks if is numeric type.
	 *
	 * @return true, if is numeric type
	 */
	public boolean isNumericType() {
		if (this.type.startsWith(ConfigValue.CLASS_DOUBLE) || this.type.startsWith(ConfigValue.CLASS_FLOAT) || this.type.startsWith(ConfigValue.CLASS_INTEGER) || this.type.startsWith(ConfigValue.CLASS_LONG)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Checks if is string.
	 *
	 * @return true, if is string
	 */
	public boolean isString() {
		return this.type.startsWith(ConfigValue.CLASS_STRING);
	}

	/**
	 * Checks if is valid.
	 *
	 * @return true, if is valid
	 */
	public boolean isValid() {
		if ((this.type == null) || this.type.isEmpty() || (!isBoolean() && !isDouble() && !isFloat() && !isInteger() && !isLong() && !isString())) {
			return false;
		}
		try {
			if (isNumericType() && !isArray() && (this.escapedMinVal != null) && !this.escapedMinVal.isEmpty() && (this.escapedMaxVal != null) && !this.escapedMaxVal.isEmpty()) {
				if ((isDouble() && (ConfigValue.getDouble(this.escapedMinVal) > ConfigValue.getDouble(this.escapedMaxVal))) || (isFloat() && (ConfigValue.getFloat(this.escapedMinVal) > ConfigValue.getFloat(this.escapedMaxVal))) || (isInteger() && (ConfigValue.getInteger(this.escapedMinVal) > ConfigValue.getInteger(this.escapedMaxVal))) || (isLong() && (ConfigValue.getLong(this.escapedMinVal) > ConfigValue.getLong(this.escapedMaxVal)))) {
					return false;
				}
			}
			if (isDouble()) {
				if (isArray()) {
					if ((this.escapedCurrentValue != null) && (ConfigValue.getDoubleArray(this.escapedCurrentValue) == null)) {
						return false;
					}
				} else {
					if ((this.escapedCurrentValue != null) && (ConfigValue.getDouble(this.escapedCurrentValue) == null)) {
						return false;
					}
					if (this.escapedOptions != null) {
						for (int i = 0; i < this.escapedOptions.length; i++) {
							if ((this.escapedOptions[i] != null) && (ConfigValue.getDouble(this.escapedOptions[i]) == null)) {
								return false;
							}
						}
					}
				}
			} else if (isFloat()) {
				if (isArray()) {
					if ((this.escapedCurrentValue != null) && (ConfigValue.getFloatArray(this.escapedCurrentValue) == null)) {
						return false;
					}
				} else {
					if ((this.escapedCurrentValue != null) && (ConfigValue.getFloat(this.escapedCurrentValue) == null)) {
						return false;
					}
					if (this.escapedOptions != null) {
						for (int i = 0; i < this.escapedOptions.length; i++) {
							if ((this.escapedOptions[i] != null) && (ConfigValue.getFloat(this.escapedOptions[i]) == null)) {
								return false;
							}
						}
					}
				}
			} else if (isInteger()) {
				if (isArray()) {
					if ((this.escapedCurrentValue != null) && (ConfigValue.getIntegerArray(this.escapedCurrentValue) == null)) {
						return false;
					}
				} else {
					if ((this.escapedCurrentValue != null) && (ConfigValue.getInteger(this.escapedCurrentValue) == null)) {
						return false;
					}
					if (this.escapedOptions != null) {
						for (int i = 0; i < this.escapedOptions.length; i++) {
							if ((this.escapedOptions[i] != null) && (ConfigValue.getInteger(this.escapedOptions[i]) == null)) {
								return false;
							}
						}
					}
				}
			} else if (isLong()) {
				if (isArray()) {
					if ((this.escapedCurrentValue != null) && (ConfigValue.getLongArray(this.escapedCurrentValue) == null)) {
						return false;
					}
				} else {
					if ((this.escapedCurrentValue != null) && (ConfigValue.getLong(this.escapedCurrentValue) == null)) {
						return false;
					}
					if (this.escapedOptions != null) {
						for (int i = 0; i < this.escapedOptions.length; i++) {
							if ((this.escapedOptions[i] != null) && (ConfigValue.getLong(this.escapedOptions[i]) == null)) {
								return false;
							}
						}
					}
				}
			}
			return true;
		} catch (final NullPointerException npe) { // better/more checks COULD be done
			return false;
		}
	}

	/**
	 * Sets the current value boolean.
	 *
	 * @param currentValue the new current value boolean
	 */
	public void setCurrentValueBoolean(final Boolean currentValue) {
		if (currentValue == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = currentValue.toString();
		}
		this.type = ConfigValue.CLASS_BOOLEAN;
	}

	/**
	 * Sets the current value boolean array.
	 *
	 * @param array the new current value boolean array
	 */
	public void setCurrentValueBooleanArray(final Boolean... array) {
		if (array == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = ConfigValue.arrayToEscapedString(array);
		}
		this.type = ConfigValue.CLASS_BOOLEAN + ConfigValue.CLASS_ARRAY;
	}

	/**
	 * Sets the current value double.
	 *
	 * @param currentValue the new current value double
	 */
	public void setCurrentValueDouble(final Double currentValue) {
		if (currentValue == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = currentValue.toString();
		}
		this.type = ConfigValue.CLASS_DOUBLE;
	}

	/**
	 * Sets the current value double array.
	 *
	 * @param array the new current value double array
	 */
	public void setCurrentValueDoubleArray(final Double... array) {
		if (array == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = ConfigValue.arrayToEscapedString(array);
		}
		this.type = ConfigValue.CLASS_DOUBLE + ConfigValue.CLASS_ARRAY;
	}

	/**
	 * Sets the current value float.
	 *
	 * @param currentValue the new current value float
	 */
	public void setCurrentValueFloat(final Float currentValue) {
		if (currentValue == null) {
			this.escapedCurrentValue = null;
		} else {
		}
		this.escapedCurrentValue = currentValue.toString();
		this.type = ConfigValue.CLASS_FLOAT;
	}

	/**
	 * Sets the current value float array.
	 *
	 * @param array the new current value float array
	 */
	public void setCurrentValueFloatArray(final Float... array) {
		if (array == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = ConfigValue.arrayToEscapedString(array);
		}
		this.type = ConfigValue.CLASS_FLOAT + ConfigValue.CLASS_ARRAY;
	}

	/**
	 * Sets the current value integer.
	 *
	 * @param currentValue the new current value integer
	 */
	public void setCurrentValueInteger(final Integer currentValue) {
		if (currentValue == null) {
			this.escapedCurrentValue = null;
		} else {
		}
		this.escapedCurrentValue = currentValue.toString();
		this.type = ConfigValue.CLASS_INTEGER;
	}

	/**
	 * Sets the current value integer array.
	 *
	 * @param array the new current value integer array
	 */
	public void setCurrentValueIntegerArray(final Integer... array) {
		if (array == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = ConfigValue.arrayToEscapedString(array);
		}
		this.type = ConfigValue.CLASS_INTEGER + ConfigValue.CLASS_ARRAY;
	}

	/**
	 * Sets the current value long.
	 *
	 * @param currentValue the new current value long
	 */
	public void setCurrentValueLong(final Long currentValue) {
		if (currentValue == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = currentValue.toString();
		}
		this.type = ConfigValue.CLASS_LONG;
	}

	/**
	 * Sets the current value long array.
	 *
	 * @param array the new current value long array
	 */
	public void setCurrentValueLongArray(final Long... array) {
		if (array == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = ConfigValue.arrayToEscapedString(array);
		}
		this.type = ConfigValue.CLASS_LONG + ConfigValue.CLASS_ARRAY;
	}

	/**
	 * Sets the current value string.
	 *
	 * @param currentValue the new current value string
	 */
	public void setCurrentValueString(final String currentValue) {
		if (currentValue == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = currentValue;
		}
		this.type = ConfigValue.CLASS_STRING;
	}

	/**
	 * Sets the current value string array.
	 *
	 * @param array the new current value string array
	 */
	public void setCurrentValueStringArray(final String... array) {
		if (array == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = ConfigValue.arrayToEscapedString(array);
		}
		this.type = ConfigValue.CLASS_STRING + ConfigValue.CLASS_ARRAY;
	}

	/**
	 * Sets the description string.
	 *
	 * @param descriptionString the new description string
	 */
	public void setDescriptionString(final String descriptionString) {
		this.descriptionString = descriptionString;
	}

	/**
	 * Sets the options boolean array.
	 *
	 * @param array the new options boolean array
	 */
	public void setOptionsBooleanArray(final Boolean[]... array) {
		setOptionsObjectArray(array);
	}

	/**
	 * Sets the options double.
	 *
	 * @param array the new options double
	 */
	public void setOptionsDouble(final Double... array) {
		setOptionsObject(array);
	}

	/**
	 * Sets the options double array.
	 *
	 * @param array the new options double array
	 */
	public void setOptionsDoubleArray(final Double[]... array) {
		setOptionsObjectArray(array);
	}

	/**
	 * Sets the options float.
	 *
	 * @param array the new options float
	 */
	public void setOptionsFloat(final Float... array) {
		setOptionsObject(array);
	}

	/**
	 * Sets the options float array.
	 *
	 * @param array the new options float array
	 */
	public void setOptionsFloatArray(final Float[]... array) {
		setOptionsObjectArray(array);
	}

	/**
	 * Sets the options integer.
	 *
	 * @param array the new options integer
	 */
	public void setOptionsInteger(final Integer... array) {
		setOptionsObject(array);
	}

	/**
	 * Sets the options integer array.
	 *
	 * @param array the new options integer array
	 */
	public void setOptionsIntegerArray(final Integer[]... array) {
		setOptionsObjectArray(array);
	}

	/**
	 * Sets the options long.
	 *
	 * @param array the new options long
	 */
	public void setOptionsLong(final Long... array) {
		setOptionsObject(array);
	}

	/**
	 * Sets the options long array.
	 *
	 * @param array the new options long array
	 */
	public void setOptionsLongArray(final Long[]... array) {
		setOptionsObjectArray(array);
	}

	/**
	 * Sets the options object.
	 *
	 * @param array the new options object
	 */
	private void setOptionsObject(final Object[] array) {
		if (array == null) {
			this.escapedOptions = null;
		} else {
			this.escapedOptions = ConfigValue.arrayToEscapedStringArray(array);
		}
	}

	/**
	 * Sets the options object array.
	 *
	 * @param array the new options object array
	 */
	private void setOptionsObjectArray(final Object[][] array) {
		if (array == null) {
			this.escapedOptions = null;
		} else {
			this.escapedOptions = ConfigValue.arrayArrayToEscapedStringArray(array);
		}
	}

	/**
	 * Sets the options string.
	 *
	 * @param array the new options string
	 */
	public void setOptionsString(final String... array) {
		this.escapedOptions = ConfigValue.getStringArray(array);
	}

	/**
	 * Sets the options string array.
	 *
	 * @param array the new options string array
	 */
	public void setOptionsStringArray(final String[]... array) {
		setOptionsObjectArray(array);
	}

	/**
	 * Sets the range boolean array.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeBooleanArray(final Long[] minVal, final Long[] maxVal) {
		setRangeObjectArray(minVal, maxVal);
	}

	/**
	 * Sets the range double.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeDouble(final Double minVal, final Double maxVal) {
		setRangeObject(minVal, maxVal);
	}

	/**
	 * Sets the range double array.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeDoubleArray(final Double[] minVal, final Double[] maxVal) {
		setRangeObjectArray(minVal, maxVal);
	}

	/**
	 * Sets the range float.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeFloat(final Float minVal, final Float maxVal) {
		setRangeObject(minVal, maxVal);
	}

	/**
	 * Sets the range float array.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeFloatArray(final Float[] minVal, final Float[] maxVal) {
		setRangeObjectArray(minVal, maxVal);
	}

	/**
	 * Sets the range integer.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeInteger(final Integer minVal, final Integer maxVal) {
		setRangeObject(minVal, maxVal);
	}

	/**
	 * Sets the range integer array.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeIntegerArray(final Integer[] minVal, final Integer[] maxVal) {
		setRangeObjectArray(minVal, maxVal);
	}

	/**
	 * Sets the range long.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeLong(final Long minVal, final Long maxVal) {
		setRangeObject(minVal, maxVal);
	}

	/**
	 * Sets the range long array.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeLongArray(final Long[] minVal, final Long[] maxVal) {
		setRangeObjectArray(minVal, maxVal);
	}

	/**
	 * Sets the range object.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeObject(final Object minVal, final Object maxVal) {
		if (minVal == null) {
			this.escapedMinVal = null;
		} else {
			this.escapedMinVal = minVal.toString();
		}
		if (maxVal == null) {
			this.escapedMaxVal = null;
		} else {
			this.escapedMaxVal = maxVal.toString();
		}
	}

	/**
	 * Sets the range object array.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeObjectArray(final Object[] minVal, final Object[] maxVal) {
		if (minVal == null) {
			this.escapedMinVal = null;
		} else {
			this.escapedMinVal = ConfigValue.arrayToEscapedString(minVal);
		}
		if (maxVal == null) {
			this.escapedMaxVal = null;
		} else {
			this.escapedMaxVal = ConfigValue.arrayToEscapedString(maxVal);
		}
	}

	/**
	 * Sets the range string.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeString(final String minVal, final String maxVal) {
		this.escapedMinVal = ConfigValue.escape(minVal);
		this.escapedMaxVal = ConfigValue.escape(maxVal);
	}

	/**
	 * Sets the range string array.
	 *
	 * @param minVal the min val
	 * @param maxVal the max val
	 */
	public void setRangeStringArray(final String[] minVal, final String[] maxVal) {
		setRangeObjectArray(minVal, maxVal);
	}

	/**
	 * Sets the raw current value.
	 *
	 * @param currentValue the new raw current value
	 */
	public void setRawCurrentValue(final Object currentValue) {
		if (currentValue == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = currentValue.toString();
		}
	}

	/**
	 * Sets the raw curren value array.
	 *
	 * @param array the new raw curren value array
	 */
	public void setRawCurrenValueArray(final Object[] array) {
		if (array == null) {
			this.escapedCurrentValue = null;
		} else {
			this.escapedCurrentValue = ConfigValue.arrayToEscapedString(array);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		final String curValString = this.escapedCurrentValue != null ? "{" + ConfigValue.PREFIX___CUR_VAL + this.escapedCurrentValue + "}" : "";
		String optionsString = "";
		if (this.escapedOptions != null) {
			optionsString = "{" + ConfigValue.PREFIX___OPTS + ConfigValue.optionsJoiner.join(this.escapedOptions) + "}";
		}
		String rangeString = "{" + ConfigValue.PREFIX___RANGE;
		if ((this.escapedMinVal != null) && (this.escapedMaxVal != null)) {
			rangeString += this.escapedMinVal + ConfigValue.RANGE_SEPARATOR + this.escapedMaxVal + "}";
		} else if (this.escapedMinVal != null) {
			rangeString += this.escapedMinVal + ConfigValue.RANGE_SEPARATOR + "}";
		} else if (this.escapedMaxVal != null) {
			rangeString += ConfigValue.RANGE_SEPARATOR + this.escapedMaxVal;
		} else {
			rangeString = "";
		}
		final String descString = this.descriptionString != null ? "{" + ConfigValue.PREFIX___DESC + ConfigValue.escape(this.descriptionString) + "}" : "";
		return "{" + ConfigValue.PREFIX___TYPE + this.type + "}" + curValString + optionsString + rangeString + descString;
	}
}
