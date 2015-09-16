package helper;

import java.util.HashMap;
import java.util.Map;

import framework.constants.GenericControlInterfaceCommandProperties;

/**
 * Helper to form and check answers to module or control interface commands.
 *
 * @author Stefan Werner
 */
public class CommandResultHelper {

	/**
	 * Gets the default fail result and add additional properties (key1, value1, key2, value2 and so on).
	 *
	 * @param additionalKeysAndValues the additional keys and values
	 * @return the default fail result
	 */
	public static Map<String, String> getDefaultResultFail(final String... additionalKeysAndValues) {
		final Map<String, String> result = new HashMap<String, String>();
		result.put(GenericControlInterfaceCommandProperties.KEY___RESULT, GenericControlInterfaceCommandProperties.VALUE___FAIL);
		final int keyCount = additionalKeysAndValues.length / 2;
		for (int i = 0; i < keyCount; i++) {
			result.put(additionalKeysAndValues[i * 2], additionalKeysAndValues[(i * 2) + 1]);
		}
		return result;
	}

	/**
	 * Gets the default OK result and add additional properties (key1, value1, key2, value2 and so on).
	 *
	 * @param additionalKeysAndValues the additional keys and values
	 * @return the default ok result
	 */
	public static Map<String, String> getDefaultResultOk(final String... additionalKeysAndValues) {
		final Map<String, String> result = new HashMap<String, String>();
		result.put(GenericControlInterfaceCommandProperties.KEY___RESULT, GenericControlInterfaceCommandProperties.VALUE___OK);
		final int keyCount = additionalKeysAndValues.length / 2;
		for (int i = 0; i < keyCount; i++) {
			result.put(additionalKeysAndValues[i * 2], additionalKeysAndValues[(i * 2) + 1]);
		}
		return result;
	}

	/**
	 * Checks if is ok.
	 *
	 * @param result the result
	 * @return true, if is ok
	 */
	public static boolean isOK(final Map<String, String> result) {
		if (result == null) {
			return false;
		}
		final String value = result.get(GenericControlInterfaceCommandProperties.KEY___RESULT);
		if ((value != null) && value.equals(GenericControlInterfaceCommandProperties.VALUE___OK)) {
			return true;
		} else {
			return false;
		}
	}
}
