package experimental.module.filebrowser.view;

import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

class WrapColumnFactory implements ViewFactory {
	@Override
	public View create(final Element elem) {
		final String kind = elem.getName();
		if (kind != null) {
			if (kind.equals(AbstractDocument.ContentElementName)) {
				return new WrapLabelView(elem);
			} else if (kind.equals(AbstractDocument.ParagraphElementName)) {
				return new ParagraphView(elem);
			} else if (kind.equals(AbstractDocument.SectionElementName)) {
				return new BoxView(elem, View.Y_AXIS);
			} else if (kind.equals(StyleConstants.ComponentElementName)) {
				return new ComponentView(elem);
			} else if (kind.equals(StyleConstants.IconElementName)) {
				return new IconView(elem);
			}
		}

		// default to text display
		return new LabelView(elem);
	}
}

public class WrapEditorKit extends StyledEditorKit {

	private static final long serialVersionUID = -6464741361837921826L;

	ViewFactory defaultFactory = new WrapColumnFactory();

	@Override
	public ViewFactory getViewFactory() {
		return this.defaultFactory;
	}

}

class WrapLabelView extends LabelView {
	public WrapLabelView(final Element elem) {
		super(elem);
	}

	@Override
	public float getMinimumSpan(final int axis) {
		switch (axis) {
		case View.X_AXIS:
			return 0;
		case View.Y_AXIS:
			return super.getMinimumSpan(axis);
		default:
			throw new IllegalArgumentException("Invalid axis: " + axis);
		}
	}

}