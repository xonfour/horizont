package experimental.module.filebrowser.control;

import static framework.model.type.DataElementType.FILE;
import static framework.model.type.DataElementType.FOLDER;

import java.util.Comparator;

import framework.model.DataElement;

/**
 *
 * @author Stefan Werner
 */
public class DataElementComparator implements Comparator<DataElement> {

	public static enum SORT_ORDER {
		ASC, DESC
	}

	public static enum SORT_TYPE {
		ALPHABETICAL, ALPHABETICAL_FF, LAST_MOD_DATE, SIZE
	}

	public static int compareAlphabetical(final DataElement arg0, final DataElement arg1) {
		// if ((arg0.getType() == FOLDER_PARENT && arg1.getType() != FOLDER_PARENT)) {
		// return -2;
		// } else if ((arg0.getType() == FOLDER_PARENT && arg1.getType() == FOLDER_PARENT)) {
		// return 0;
		// } else {
		int result = arg0.getName().compareToIgnoreCase(arg1.getName());
		if (result == 0) {
			result = arg0.getName().compareTo(arg1.getName());
		}
		return result;
		// }
	}

	public static int compareAlphabeticalFF(final DataElement arg0, final DataElement arg1) {
		if ((arg0.getType() == FOLDER) && (arg1.getType() == FILE)) {
			return -1;
		} else if ((arg1.getType() == FOLDER) && (arg0.getType() == FILE)) {
			return 1;
		} else {
			return DataElementComparator.compareAlphabetical(arg0, arg1);
		}
	}

	public static int compareLastModDate(final DataElement arg0, final DataElement arg1) {
		int result = Long.compare(arg0.getModificationDate(), arg1.getModificationDate());
		if (result == 0) {
			result = DataElementComparator.compareAlphabetical(arg0, arg1);
		}
		return result;
	}

	public static int compareSize(final DataElement arg0, final DataElement arg1) {
		int result = Long.compare(arg0.getSize(), arg1.getSize());
		if (result == 0) {
			result = DataElementComparator.compareAlphabetical(arg0, arg1);
		}
		return result;
	}

	private SORT_ORDER order = SORT_ORDER.ASC;

	private SORT_TYPE type = SORT_TYPE.ALPHABETICAL_FF;

	public DataElementComparator() {
	}

	public DataElementComparator(final SORT_TYPE type, final SORT_ORDER order) {
		this.type = type;
		this.order = order;
	}

	/* (non-Javadoc)
	 *
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object) */
	@Override
	public int compare(final DataElement arg0, final DataElement arg1) {
		int result = 0;
		switch (this.type) {
		case ALPHABETICAL:
			result = DataElementComparator.compareAlphabetical(arg0, arg1);
			break;
		case ALPHABETICAL_FF:
			result = DataElementComparator.compareAlphabeticalFF(arg0, arg1);
			break;
		case LAST_MOD_DATE:
			result = DataElementComparator.compareLastModDate(arg0, arg1);
			break;
		case SIZE:
			result = DataElementComparator.compareSize(arg0, arg1);
			break;
		}

		if (this.order == SORT_ORDER.DESC) {
			result = -result;
		}

		return result;
	}

	public SORT_ORDER getOrder() {
		return this.order;
	}

	public SORT_TYPE getType() {
		return this.type;
	}

	public void setOrder(final SORT_ORDER order) {
		this.order = order;
	}

	public void setType(final SORT_TYPE type) {
		this.type = type;
	}

}
