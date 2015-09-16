package helper;

import java.util.Map;
import java.util.Set;

import com.google.common.base.CharMatcher;

import framework.constants.Constants;
import framework.model.DataElement;
import framework.model.Port;
import framework.model.PortTuple;
import framework.model.ProsumerPort;
import framework.model.ProviderPort;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.PortSummary;
import framework.model.type.PortType;

/**
 * Contains static methods to check objects handled by the system. Mainly used by the framework and the database. Methods return with <code>true</code> if
 * object(s) is/are valid.
 * <p>
 * IMPORTANT: We really need to do all this checking because we cannot trust external third party components.
 * <p>
 * TODO: But we could find a better way to do so. And even with this class there are plenty of dezentralized checks spread over the code.
 *
 * @author Stefan Werner
 */
public final class ObjectValidator {

	/**
	 * Check args not to be null.
	 *
	 * @param args the args
	 * @return true, if OK
	 */
	public static boolean checkArgsNotNull(final Object... args) {
		for (final Object obj : args) {
			if (obj == null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check array for null elements.
	 *
	 * @param array the array
	 * @return true, if OK
	 */
	public static boolean checkArrayForNullElements(final Object[] array) {
		for (final Object obj : array) {
			if (obj == null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check connection summary.
	 *
	 * @param connectionSummary the connection summary
	 * @return true, if OK
	 */
	public static boolean checkConnectionSummary(final ConnectionSummary connectionSummary) {
		if (connectionSummary == null) {
			return false;
		}
		// TODO: also check other values
		final PortSummary prosumerPort = connectionSummary.getProsumerPortSummary();
		final PortSummary providerPort = connectionSummary.getProviderPortSummary();
		if ((connectionSummary == null) || (prosumerPort == null) || (providerPort == null) || (prosumerPort.getType() != PortType.PROSUMER) || (providerPort.getType() != PortType.PROVIDER)) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Check data element.
	 *
	 * @param element the element
	 * @return true, if OK
	 */
	public static boolean checkDataElement(final DataElement element) {
		if ((element == null) || (element.getPath() == null)) {
			return false;
		}
		return ObjectValidator.checkDataElementValues(element.getModificationDate(), element.getSize()) && ObjectValidator.checkPath(element.getPath()) && (!element.hasAdditionalProperties() || ObjectValidator.checkMapForNullKeysOrValues(element.getAdditionalProperties()));
	}

	/**
	 * Check element values.
	 *
	 * @param modificationDate the modification date
	 * @param size the size
	 * @return true, if OK
	 */
	public static boolean checkDataElementValues(final long modificationDate, final long size) {
		if ((modificationDate < 0) || (size < 0)) {
			return false;
		}
		return true;
	}

	/**
	 * Check map for null keys or values.
	 *
	 * @param map the map
	 * @return true, if OK
	 */
	public static boolean checkMapForNullKeysOrValues(final Map<?, ?> map) {
		if ((map == null) || map.containsKey(null) || map.containsValue(null)) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Check path.
	 *
	 * @param path the path
	 * @return true, if OK
	 */
	public static boolean checkPath(final String[] path) {
		if ((path == null) || (path.length > Constants.MAX_PATH_DEPTH)) {
			return false;
		}
		for (final String s : path) {
			// filter for empty elements and the ones with special meaning
			// SHOULD be done in Provider modules providing file system access but is done here as an additional security measure
			if (s.isEmpty() || s.equals("..") || s.equals(".")) {
				return false;
			}
			if (CharMatcher.JAVA_ISO_CONTROL.and(CharMatcher.INVISIBLE).matchesAnyOf(s)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check port.
	 *
	 * @param port the port
	 * @return true, if OK
	 */
	public static boolean checkPort(final Port port) {
		if ((port == null) || (port.getModuleId() == null) || port.getModuleId().isEmpty() || (port.getPortId() == null) || port.getPortId().isEmpty()) {
			return false;
		}
		if ((port instanceof ProsumerPort) && ((port.getMaxConnections() != 0) && (port.getMaxConnections() != 1))) {
			return false;
		} else if ((port instanceof ProviderPort) && (port.getMaxConnections() < -1)) {
			return false;
		}
		return true;
	}

	/**
	 * Check port tuple.
	 *
	 * @param tuple the tuple
	 * @return true, if OK
	 */
	public static boolean checkPortTuple(final PortTuple tuple) {
		if (tuple == null) {
			return false;
		}
		if ((tuple.getProsumerPort() != null) && (tuple.getProviderPort() != null) && ObjectValidator.checkPort(tuple.getProsumerPort()) && ObjectValidator.checkPort(tuple.getProviderPort())) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Check set for null elements.
	 *
	 * @param set the set
	 * @return true, if OK
	 */
	public static boolean checkSetForNullElements(final Set<?> set) {
		if ((set == null) || set.contains(null)) {
			return false;
		} else {
			return true;
		}
	}
}
