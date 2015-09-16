package controlinterface.swinguiadvanced.view.other;

import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;

/**
 * A Class to control the maximum number of lines to be stored in a Document.
 * <p>
 * Taken from {@linkplain https://tips4java.wordpress.com/2008/10/15/limit-lines-in-document/} (19.6.2015).
 */
public class LimitLinesDocumentListener implements DocumentListener {

	private final boolean isRemoveFromStart;
	private int maximumLines;

	/**
	 * Instantiates a new limit lines document listener.
	 *
	 * @param maximumLines the maximum line count
	 */
	public LimitLinesDocumentListener(final int maximumLines) {
		this(maximumLines, true);
	}

	/**
	 * Instantiates a new limit lines document listener.
	 *
	 * @param maximumLines the maximum line count
	 * @param isRemoveFromStart should lines get removed from the start
	 */
	public LimitLinesDocumentListener(final int maximumLines, final boolean isRemoveFromStart) {
		setLimitLines(maximumLines);
		this.isRemoveFromStart = isRemoveFromStart;
	}

	@Override
	public void changedUpdate(final DocumentEvent e) {
		// no op
	}

	/**
	 * Gets the line limit.
	 *
	 * @return the line limit
	 */
	public int getLimitLines() {
		return this.maximumLines;
	}

	@Override
	public void insertUpdate(final DocumentEvent e) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				removeLines(e);
			}
		});
	}

	/**
	 * Removes lines from the end of the document.
	 *
	 * @param document the document
	 * @param root the root element
	 */
	private void removeFromEnd(final Document document, final Element root) {
		final Element line = root.getElement(root.getElementCount() - 1);
		final int start = line.getStartOffset();
		final int end = line.getEndOffset();

		try {
			document.remove(start - 1, end - start);
		} catch (final BadLocationException ble) {
			// ignored
		}
	}

	/**
	 * Removes lines from the start of the document.
	 *
	 * @param document the document
	 * @param root the root element
	 */
	private void removeFromStart(final Document document, final Element root) {
		final Element line = root.getElement(0);
		final int end = line.getEndOffset();

		try {
			document.remove(0, end);
		} catch (final BadLocationException ble) {
			// ignored
		}
	}

	/**
	 * Removes lines from the Document when necessary
	 *
	 * @param event the document event
	 */
	private void removeLines(final DocumentEvent event) {
		final Document document = event.getDocument();
		final Element root = document.getDefaultRootElement();

		while (root.getElementCount() > this.maximumLines) {
			if (this.isRemoveFromStart) {
				removeFromStart(document, root);
			} else {
				removeFromEnd(document, root);
			}
		}
	}

	@Override
	public void removeUpdate(final DocumentEvent e) {
	}

	/**
	 * Sets the line limit.
	 *
	 * @param maximumLines the new line limit
	 */
	public void setLimitLines(final int maximumLines) {
		if (maximumLines < 1) {
			final String message = "Maximum lines must be greater than 0";
			throw new IllegalArgumentException(message);
		}
		this.maximumLines = maximumLines;
	}
}
