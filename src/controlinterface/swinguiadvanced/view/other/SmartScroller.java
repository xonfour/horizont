package controlinterface.swinguiadvanced.view.other;

import java.awt.Component;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import javax.swing.BoundedRangeModel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;

/**
 * The SmartScroller will attempt to keep the viewport positioned based on the users interaction with the scrollbar.
 * <p>
 * Taken from {@linkplain https://tips4java.wordpress.com/2013/03/03/smart-scrolling/} (19.6.2015).
 */
public class SmartScroller implements AdjustmentListener {

	public static final int END = 1;
	public static final int HORIZONTAL = 0;
	public static final int START = 0;
	public static final int VERTICAL = 1;

	private boolean adjustScrollBar = true;
	private int previousMaximum = -1;
	private int previousValue = -1;
	private JScrollBar scrollBar;
	private final int viewportPosition;

	/**
	 * Convenience constructor. Scroll direction is VERTICAL and viewport position is at the END.
	 *
	 * @param scrollPane the scroll pane to monitor
	 */
	public SmartScroller(final JScrollPane scrollPane) {
		this(scrollPane, SmartScroller.VERTICAL, SmartScroller.END);
	}

	/**
	 * Convenience constructor. Scroll direction is VERTICAL.
	 *
	 * @param scrollPane the scroll pane to monitor
	 * @param viewportPosition valid values are START and END
	 */
	public SmartScroller(final JScrollPane scrollPane, final int viewportPosition) {
		this(scrollPane, SmartScroller.VERTICAL, viewportPosition);
	}

	/**
	 * Specifies how the SmartScroller will function.
	 *
	 * @param scrollPane the scroll pane to monitor
	 * @param scrollDirection indicates which JScrollBar to monitor. Valid values are HORIZONTAL and VERTICAL.
	 * @param viewportPosition indicates where the viewport will normally be positioned as data is added. Valid values are START and END
	 */
	public SmartScroller(final JScrollPane scrollPane, final int scrollDirection, final int viewportPosition) {
		if ((scrollDirection != SmartScroller.HORIZONTAL) && (scrollDirection != SmartScroller.VERTICAL)) {
			throw new IllegalArgumentException("invalid scroll direction specified");
		}
		if ((viewportPosition != SmartScroller.START) && (viewportPosition != SmartScroller.END)) {
			throw new IllegalArgumentException("invalid viewport position specified");
		}
		this.viewportPosition = viewportPosition;
		if (scrollDirection == SmartScroller.HORIZONTAL) {
			this.scrollBar = scrollPane.getHorizontalScrollBar();
		} else {
			this.scrollBar = scrollPane.getVerticalScrollBar();
		}
		this.scrollBar.addAdjustmentListener(this);
		final Component view = scrollPane.getViewport().getView();
		if (view instanceof JTextComponent) {
			final JTextComponent textComponent = (JTextComponent) view;
			final DefaultCaret caret = (DefaultCaret) textComponent.getCaret();
			caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		}
	}

	@Override
	public void adjustmentValueChanged(final AdjustmentEvent e) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				checkScrollBar(e);
			}
		});
	}

	/**
	 * Analyzes every adjustment event to determine when the viewport needs to be repositioned.
	 *
	 * @param e the adjustment event
	 */
	private void checkScrollBar(final AdjustmentEvent e) {
		final JScrollBar scrollBar = (JScrollBar) e.getSource();
		final BoundedRangeModel listModel = scrollBar.getModel();
		int value = listModel.getValue();
		final int extent = listModel.getExtent();
		final int maximum = listModel.getMaximum();
		final boolean valueChanged = this.previousValue != value;
		final boolean maximumChanged = this.previousMaximum != maximum;
		if (valueChanged && !maximumChanged) {
			if (this.viewportPosition == SmartScroller.START) {
				this.adjustScrollBar = value != 0;
			} else {
				this.adjustScrollBar = (value + extent) >= maximum;
			}
		}
		if (this.adjustScrollBar && (this.viewportPosition == SmartScroller.END)) {
			// Scroll the viewport to the end.
			scrollBar.removeAdjustmentListener(this);
			value = maximum - extent;
			scrollBar.setValue(value);
			scrollBar.addAdjustmentListener(this);
		}
		if (this.adjustScrollBar && (this.viewportPosition == SmartScroller.START)) {
			// Keep the viewport at the same relative viewportPosition
			scrollBar.removeAdjustmentListener(this);
			value = (value + maximum) - this.previousMaximum;
			scrollBar.setValue(value);
			scrollBar.addAdjustmentListener(this);
		}
		this.previousValue = value;
		this.previousMaximum = maximum;
	}
}
