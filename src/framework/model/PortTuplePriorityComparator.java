package framework.model;

import java.util.Comparator;

/**
 * Comparator for port tuples. Currently tuples are sorted by their priority (higher number = higher priority).
 *
 * @author Stefan Werner
 */
public class PortTuplePriorityComparator implements Comparator<PortTuple> {

	/* (non-Javadoc)
	 *
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object) */
	@Override
	public int compare(final PortTuple arg0, final PortTuple arg1) {
		return Integer.compare(arg1.getPriority(), arg0.getPriority());
	}
}
