package module.simplesync.model;

import java.util.Comparator;

/**
 * Comparator used to find the most "important" job. The higher the depth of the path the higher the priority. Jobs with the same path depth are sorted
 * alphabetically.
 *
 * @author Stefan Werner
 */
public class JobPriorityComperator implements Comparator<SyncJob> {

	/* (non-Javadoc)
	 *
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object) */
	@Override
	public int compare(final SyncJob arg0, final SyncJob arg1) {
		final int delta = arg1.getElement().getPath().length - arg0.getElement().getPath().length;
		if (delta < 0) {
			return -1;
		} else if (delta > 0) {
			return 1;
		} else {
			final String[] arg0Path = arg0.getElement().getPath();
			final String[] arg1Path = arg1.getElement().getPath();
			for (int i = 0; i < arg0.getElement().getPath().length; i++) {
				final int result = arg0Path[i].compareTo(arg1Path[i]);
				if (result != 0) {
					return result;
				}
			}
			return arg0.getSourcePort().getPortId().compareTo(arg1.getSourcePort().getPortId());
		}
	}
}
