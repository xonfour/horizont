package framework.control;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import framework.exception.AuthorizationException;

/**
 * Stores and checks authorizations of components (control interfaces and modules) based on their (int) rights.
 * <p>
 * TODO:<br>
 * - Merge with/add to Java SecurityManager subsystem.<br>
 * - Add a dynamic session ID?
 *
 * @author Stefan Werner
 */
public class ComponentAuthorizationManager {

	private final Map<String, Integer> componentRights = new ConcurrentHashMap<String, Integer>();

	/**
	 * Gets the rights of a component.
	 *
	 * @param componentId the component ID
	 * @return the rights
	 */
	int getRights(final String componentId) {
		if ((componentId == null) || componentId.isEmpty()) {
			return -1;
		}
		final Integer rights = this.componentRights.get(componentId);
		if (rights == null) {
			return -1;
		} else {
			return rights;
		}
	}

	/**
	 * Checks if the component holds ALL given rights.
	 *
	 * @param componentId the component ID
	 * @param rights the rights to check for
	 * @return true, if components holds ALL rights
	 */
	boolean hasRights(final String componentId, final int... rights) {
		if ((componentId == null) || componentId.isEmpty()) {
			return false;
		}
		final Integer savedRights = this.componentRights.get(componentId);
		if (savedRights == null) {
			return false;
		}
		for (final int right : rights) {
			if ((savedRights & right) != right) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Removes a component.
	 *
	 * @param componentId the component ID
	 * @return true, if successful
	 */
	boolean removeComponent(final String componentId) {
		return this.componentRights.remove(componentId) != null;
	}

	/**
	 * Updates a component.
	 *
	 * @param componentId the component ID
	 * @param rights the rights
	 */
	void updateComponent(final String componentId, final int rights) {
		this.componentRights.put(componentId, rights);
	}

	/**
	 * Verifies a component hold ALL given rights.
	 *
	 * @param componentId the component id
	 * @param rights the rights
	 * @throws AuthorizationException if component does NOT hold ALL given rights
	 */
	void verifyAllComponentRights(final String componentId, final int... rights) throws AuthorizationException {
		if ((componentId == null) || componentId.isEmpty()) {
			throw new AuthorizationException("invalid component id");
		}
		final Integer savedRights = this.componentRights.get(componentId);
		if (savedRights == null) {
			throw new AuthorizationException("component id unknown");
		}
		for (final int right : rights) {
			if ((savedRights & right) != right) {
				throw new AuthorizationException("missing right: " + (right - (savedRights & right)));
			}
		}
	}

	/**
	 * Verifies a component hold ANY (at least one) given rights.
	 *
	 * @param componentId the component ID
	 * @param rights the rights
	 * @throws AuthorizationException if component does NOT hold ANY given rights
	 */
	void verifyAnyComponentRights(final String componentId, final int... rights) throws AuthorizationException {
		if ((componentId == null) || componentId.isEmpty()) {
			throw new AuthorizationException("invalid component id");
		}
		final Integer savedRights = this.componentRights.get(componentId);
		if (savedRights == null) {
			throw new AuthorizationException("component id unknown");
		}
		boolean ok = false;
		for (final int right : rights) {
			if ((savedRights & right) == right) {
				ok = true;
				break;
			}
		}
		if (!ok) {
			throw new AuthorizationException("missing right");
		}
	}
}
