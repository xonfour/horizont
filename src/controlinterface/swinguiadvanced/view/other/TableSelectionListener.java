package controlinterface.swinguiadvanced.view.other;

/**
 * Listener for receiving table selection events.
 *
 * @author Stefan Werner
 */
public interface TableSelectionListener {

	/**
	 * Called on component selection.
	 *
	 * @param index the index of the selection
	 */
	public void onComponentSelected(int index);
}
