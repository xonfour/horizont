package experimental.module.filebrowser.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.AbstractListModel;

import framework.model.DataElement;

/**
 *
 * @author Stefan Werner
 */
public class SortedFolderFileListModel extends AbstractListModel<DataElement> {

	private static final long serialVersionUID = 11789118023695490L;

	SortedSet<DataElement> model;

	Comparator<DataElement> comperator;

	public SortedFolderFileListModel(final Comparator<DataElement> comperator) {
		this.comperator = comperator;
		this.model = Collections.synchronizedSortedSet(new TreeSet<DataElement>(comperator));
	}

	public void add(final DataElement element) {
		if (this.model.add(element)) {
			fireContentsChanged(this, 0, getSize());
		}
	}

	public void addAll(final DataElement elements[]) {
		final Collection<DataElement> c = Arrays.asList(elements);
		this.model.addAll(c);
		fireContentsChanged(this, 0, getSize());
	}

	public void clear() {
		this.model.clear();
		fireContentsChanged(this, 0, getSize());
	}

	public boolean contains(final Object element) {
		return this.model.contains(element);
	}

	public Object firstElement() {
		return this.model.first();
	}

	@Override
	public DataElement getElementAt(final int index) {
		return (DataElement) this.model.toArray()[index];
	}

	@Override
	public int getSize() {
		return this.model.size();
	}

	public Iterator<DataElement> iterator() {
		return this.model.iterator();
	}

	public Object lastElement() {
		return this.model.last();
	}

	public boolean removeElement(final Object element) {
		final boolean removed = this.model.remove(element);
		if (removed) {
			fireContentsChanged(this, 0, getSize());
		}
		return removed;
	}

	public void update() {
		fireContentsChanged(this, 0, getSize());
	}
}
