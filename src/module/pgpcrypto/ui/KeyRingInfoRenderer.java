package module.pgpcrypto.ui;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;

import module.pgpcrypto.model.KeyRingInfo;

/**
 * Renderer for key ring info objects.
 */
public class KeyRingInfoRenderer extends DefaultListCellRenderer {

	private static final long serialVersionUID = -7121927237609035067L;

	@Override
	public Component getListCellRendererComponent(@SuppressWarnings("rawtypes") final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
		if (value instanceof KeyRingInfo) {
			final KeyRingInfo info = ((KeyRingInfo) value);
			return new KeyRingInfoListElementPanel(info, isSelected);
		} else {
			return new JLabel(value.toString());
		}
	}
}