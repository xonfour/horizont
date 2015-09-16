package helper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import module.iface.Module;
import module.iface.Prosumer;
import module.iface.Provider;

/**
 * Sorts given modules to provide best start/stop order.
 * <p>
 * TODO: Current implementation is naive, use some graph search algorithm to find best orders.
 *
 * @author Stefan Werner
 */
public class ModulePrioritySorter {

	// very simple sorting, but will work for now
	/**
	 * Gets the start order.
	 *
	 * @param modules the modules
	 * @return the start order
	 */
	// in the future some type of topological sorting could be useful
	public static List<Module> getStartOrder(final Collection<Module> modules) {
		final List<Module> result = new ArrayList<Module>();
		final Set<Module> prosumerProviderModules = new HashSet<Module>();
		final Set<Module> prosumerModules = new HashSet<Module>();
		for (final Module m : modules) {
			if (m instanceof Provider) {
				result.add(m);
			} else if (m instanceof Prosumer) {
				prosumerModules.add(m);
			} else {
				prosumerProviderModules.add(m);
			}
		}
		result.addAll(prosumerProviderModules);
		result.addAll(prosumerModules);
		return result;
	}

	/**
	 * Gets the stop order.
	 *
	 * @param modules the modules
	 * @return the stop order
	 */
	public static List<Module> getStopOrder(final Collection<Module> modules) {
		final List<Module> result = ModulePrioritySorter.getStartOrder(modules);
		Collections.reverse(result);
		return result;
	}
}