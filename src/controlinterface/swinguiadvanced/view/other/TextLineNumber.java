package controlinterface.swinguiadvanced.view.other;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.Utilities;

/**
 * Displays line numbers for a related text component.
 * <p>
 * Taken from {@linkplain https://tips4java.wordpress.com/2009/05/23/text-component-line-number/} (19.6.2015).
 */
public class TextLineNumber extends JPanel implements CaretListener, DocumentListener, PropertyChangeListener {

	private static final long serialVersionUID = -3358670463919634090L;
	public static final float LEFT = 0.0f;
	public static final float CENTER = 0.5f;
	public static final float RIGHT = 1.0f;
	private static final Border OUTER = new MatteBorder(0, 0, 0, 2, Color.GRAY);
	private static final int HEIGHT = Integer.MAX_VALUE - 1000000;

	private final JTextComponent component;
	private boolean updateFont;
	private int borderGap;
	private Color currentLineForeground;
	private float digitAlignment;
	private int minimumDisplayDigits;
	private int lastDigits;
	private int lastHeight;
	private int lastLine;
	private HashMap<String, FontMetrics> fonts;

	/**
	 * Create a line number component for a text component. This minimum display width will be based on 3 digits.
	 *
	 * @param component the related text component
	 */
	public TextLineNumber(final JTextComponent component) {
		this(component, 3);
	}

	/**
	 * Create a line number component for a text component.
	 *
	 * @param component the related text component
	 * @param minimumDisplayDigits the number of digits used to calculate the minimum width of the component
	 */
	public TextLineNumber(final JTextComponent component, final int minimumDisplayDigits) {
		this.component = component;
		setFont(component.getFont());
		setBorderGap(5);
		setCurrentLineForeground(Color.RED);
		setDigitAlignment(TextLineNumber.RIGHT);
		setMinimumDisplayDigits(minimumDisplayDigits);
		component.getDocument().addDocumentListener(this);
		component.addCaretListener(this);
		component.addPropertyChangeListener("font", this);
	}

	@Override
	public void caretUpdate(final CaretEvent e) {
		final int caretPosition = this.component.getCaretPosition();
		final Element root = this.component.getDocument().getDefaultRootElement();
		final int currentLine = root.getElementIndex(caretPosition);
		if (this.lastLine != currentLine) {
			repaint();
			this.lastLine = currentLine;
		}
	}

	@Override
	public void changedUpdate(final DocumentEvent e) {
		documentChanged();
	}

	/**
	 * A document change may affect the number of displayed lines of text. Therefore the lines numbers will also change.
	 */
	private void documentChanged() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					final int endPos = TextLineNumber.this.component.getDocument().getLength();
					final Rectangle rect = TextLineNumber.this.component.modelToView(endPos);
					if ((rect != null) && (rect.y != TextLineNumber.this.lastHeight)) {
						setPreferredWidth();
						repaint();
						TextLineNumber.this.lastHeight = rect.y;
					}
				} catch (final BadLocationException ex) {
					// nothing to do
				}
			}
		});
	}

	/**
	 * Gets the border gap.
	 *
	 * @return the border gap in pixels
	 */
	public int getBorderGap() {
		return this.borderGap;
	}

	/**
	 * Gets the current line rendering Color.
	 *
	 * @return the Color used to render the current line number
	 */
	public Color getCurrentLineForeground() {
		return this.currentLineForeground == null ? getForeground() : this.currentLineForeground;
	}

	/**
	 * Gets the digit alignment.
	 *
	 * @return the alignment of the painted digits
	 */
	public float getDigitAlignment() {
		return this.digitAlignment;
	}

	/**
	 * Gets the minimum display digits.
	 *
	 * @return the minimum display digits
	 */
	public int getMinimumDisplayDigits() {
		return this.minimumDisplayDigits;
	}

	/**
	 * Determines the X offset to properly align the line number when drawn
	 *
	 * @param availableWidth the available width
	 * @param stringWidth the string width
	 * @return the offset x
	 */
	private int getOffsetX(final int availableWidth, final int stringWidth) {
		return (int) ((availableWidth - stringWidth) * this.digitAlignment);
	}

	/**
	 * Determines the Y offset for the current row.
	 *
	 * @param rowStartOffset the row start offset
	 * @param fontMetrics the font metrics
	 * @return the offset y
	 * @throws BadLocationException the bad location exception
	 */
	private int getOffsetY(final int rowStartOffset, final FontMetrics fontMetrics) throws BadLocationException {
		final Rectangle r = this.component.modelToView(rowStartOffset);
		final int lineHeight = fontMetrics.getHeight();
		final int y = r.y + r.height;
		int descent = 0;
		if (r.height == lineHeight) {
			descent = fontMetrics.getDescent();
		} else {
			if (this.fonts == null) {
				this.fonts = new HashMap<String, FontMetrics>();
			}
			final Element root = this.component.getDocument().getDefaultRootElement();
			final int index = root.getElementIndex(rowStartOffset);
			final Element line = root.getElement(index);
			for (int i = 0; i < line.getElementCount(); i++) {
				final Element child = line.getElement(i);
				final AttributeSet as = child.getAttributes();
				final String fontFamily = (String) as.getAttribute(StyleConstants.FontFamily);
				final Integer fontSize = (Integer) as.getAttribute(StyleConstants.FontSize);
				final String key = fontFamily + fontSize;
				FontMetrics fm = this.fonts.get(key);
				if (fm == null) {
					final Font font = new Font(fontFamily, Font.PLAIN, fontSize);
					fm = this.component.getFontMetrics(font);
					this.fonts.put(key, fm);
				}
				descent = Math.max(descent, fm.getDescent());
			}
		}
		return y - descent;
	}

	/**
	 * Gets the line number to be drawn. The empty string will be returned when a line of text has wrapped.
	 *
	 * @param rowStartOffset the row to start offset
	 * @return the text line number
	 */
	protected String getTextLineNumber(final int rowStartOffset) {
		final Element root = this.component.getDocument().getDefaultRootElement();
		final int index = root.getElementIndex(rowStartOffset);
		final Element line = root.getElement(index);

		if (line.getStartOffset() == rowStartOffset) {
			return String.valueOf(index + 1);
		} else {
			return "";
		}
	}

	/**
	 * Gets the update font property.
	 *
	 * @return the update font property
	 */
	public boolean getUpdateFont() {
		return this.updateFont;
	}

	@Override
	public void insertUpdate(final DocumentEvent e) {
		documentChanged();
	}

	/**
	 * Checks if the caret is currently positioned on the line we are about to paint so the line number can be highlighted.
	 *
	 * @param rowStartOffset the row start offset
	 * @return true, if is current line
	 */
	private boolean isCurrentLine(final int rowStartOffset) {
		final int caretPosition = this.component.getCaretPosition();
		final Element root = this.component.getDocument().getDefaultRootElement();

		if (root.getElementIndex(rowStartOffset) == root.getElementIndex(caretPosition)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Draws the line numbers
	 */
	@Override
	public void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final FontMetrics fontMetrics = this.component.getFontMetrics(this.component.getFont());
		final Insets insets = getInsets();
		final int availableWidth = getSize().width - insets.left - insets.right;
		final Rectangle clip = g.getClipBounds();
		int rowStartOffset = this.component.viewToModel(new Point(0, clip.y));
		final int endOffset = this.component.viewToModel(new Point(0, clip.y + clip.height));
		while (rowStartOffset <= endOffset) {
			try {
				if (isCurrentLine(rowStartOffset)) {
					g.setColor(getCurrentLineForeground());
				} else {
					g.setColor(getForeground());
				}
				final String lineNumber = getTextLineNumber(rowStartOffset);
				final int stringWidth = fontMetrics.stringWidth(lineNumber);
				final int x = getOffsetX(availableWidth, stringWidth) + insets.left;
				final int y = getOffsetY(rowStartOffset, fontMetrics);
				g.drawString(lineNumber, x, y);
				rowStartOffset = Utilities.getRowEnd(this.component, rowStartOffset) + 1;
			} catch (final Exception e) {
				break;
			}
		}
	}

	@Override
	public void propertyChange(final PropertyChangeEvent evt) {
		if (evt.getNewValue() instanceof Font) {
			if (this.updateFont) {
				final Font newFont = (Font) evt.getNewValue();
				setFont(newFont);
				this.lastDigits = 0;
				setPreferredWidth();
			} else {
				repaint();
			}
		}
	}

	@Override
	public void removeUpdate(final DocumentEvent e) {
		documentChanged();
	}

	/**
	 * Sets the border gap is used in calculating the left and right insets of the border. Default value is 5.
	 *
	 * @param borderGap the gap in pixels
	 */
	public void setBorderGap(final int borderGap) {
		this.borderGap = borderGap;
		final Border inner = new EmptyBorder(0, borderGap, 0, borderGap);
		setBorder(new CompoundBorder(TextLineNumber.OUTER, inner));
		this.lastDigits = 0;
		setPreferredWidth();
	}

	/**
	 * Sets the Color used to render the current line digits. Default is Coolor.RED.
	 *
	 * @param currentLineForeground the Color used to render the current line
	 */
	public void setCurrentLineForeground(final Color currentLineForeground) {
		this.currentLineForeground = currentLineForeground;
	}

	/**
	 * Specifies the horizontal alignment of the digits within the component. Common values would be:
	 * <ul>
	 * <li>TextLineNumber.LEFT
	 * <li>TextLineNumber.CENTER
	 * <li>TextLineNumber.RIGHT (default)
	 * </ul>
	 *
	 * @param digitAlignment the new digit alignment
	 */
	public void setDigitAlignment(final float digitAlignment) {
		this.digitAlignment = digitAlignment > 1.0f ? 1.0f : digitAlignment < 0.0f ? -1.0f : digitAlignment;
	}

	/**
	 * Specifies the minimum number of digits used to calculate the preferred width of the component. Default is 3.
	 *
	 * @param minimumDisplayDigits the number digits used in the preferred width calculation
	 */
	public void setMinimumDisplayDigits(final int minimumDisplayDigits) {
		this.minimumDisplayDigits = minimumDisplayDigits;
		setPreferredWidth();
	}

	/**
	 * Calculates the width needed to display the maximum line number.
	 */
	private void setPreferredWidth() {
		final Element root = this.component.getDocument().getDefaultRootElement();
		final int lines = root.getElementCount();
		final int digits = Math.max(String.valueOf(lines).length(), this.minimumDisplayDigits);
		if (this.lastDigits != digits) {
			this.lastDigits = digits;
			final FontMetrics fontMetrics = getFontMetrics(getFont());
			final int width = fontMetrics.charWidth('0') * digits;
			final Insets insets = getInsets();
			final int preferredWidth = insets.left + insets.right + width;

			final Dimension d = getPreferredSize();
			d.setSize(preferredWidth, TextLineNumber.HEIGHT);
			setPreferredSize(d);
			setSize(d);
		}
	}

	/**
	 * Sets the update font property. Indicates whether this Font should be updated automatically when the Font of the related text component is changed.
	 *
	 * @param updateFont when true update the Font and repaint the line numbers, otherwise just repaint the line numbers.
	 */
	public void setUpdateFont(final boolean updateFont) {
		this.updateFont = updateFont;
	}
}
