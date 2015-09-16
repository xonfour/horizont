package module.pgpcrypto.ui;

import helper.ResourceHelper;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import module.pgpcrypto.helper.HumanReadableIdGenerationHelper;
import module.pgpcrypto.model.KeyRingInfo;
import module.pgpcrypto.model.KeyRingInfo.KEY_TYPE;
import module.pgpcrypto.model.Resource;
import net.miginfocom.swing.MigLayout;
import framework.constants.Constants;

/**
 * Panel that presents a nice visualization of key ring info objects.
 *
 * @author Stefan Werner
 */
public class KeyRingInfoListElementPanel extends JPanel {

	public static final String PATH_SEPARATOR = "/";
	private static final long serialVersionUID = 1133081108531214426L;

	private final KeyRingInfo info;
	private final JLabel masterKeyIdLabel;
	private final JLabel masterUserIdLabel;
	private JLabel relatedPathLabel;
	private int subKeyRow = 2;

	/**
	 * Creates the panel.
	 *
	 * @param info the info
	 * @param isSelected the is selected
	 */
	public KeyRingInfoListElementPanel(final KeyRingInfo info, final boolean isSelected) {
		this.info = info;
		Color fgColor;
		Color bgColor;
		if (!isSelected || !info.isUsable()) {
			fgColor = Color.BLACK;
			bgColor = Color.WHITE;
		} else {
			fgColor = Color.WHITE;
			bgColor = Constants.DARK_BG_COLOR;
		}

		setBackground(bgColor);
		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
		setLayout(new MigLayout("gapy 5, ins 6 n 10 n", "[25px][grow]", "[]"));

		final String masterUserIdText = info.getUserId() + " (" + HumanReadableIdGenerationHelper.getHumanReadablePresentation(info.getKeyId()) + ")";
		this.masterUserIdLabel = new JLabel(masterUserIdText);
		if (info.isUsable()) {
			this.masterUserIdLabel.setIcon(new ImageIcon(ResourceHelper.getResource(Resource.IMG_OK_M_CUT)));
		} else {
			this.masterUserIdLabel.setIcon(new ImageIcon(ResourceHelper.getResource(Resource.IMG_DENY_M_CUT)));
		}
		this.masterUserIdLabel.setForeground(fgColor);
		add(this.masterUserIdLabel, "flowx,cell 0 0 2 1,growx");

		final JLabel arrowLabel = new JLabel(new ImageIcon(ResourceHelper.getResource(Resource.IMG_ARROWTOP_XS)));
		add(arrowLabel, "alignx trailing, cell 0 1");
		this.masterKeyIdLabel = new JLabel(Long.toHexString(info.getKeyId()).toUpperCase());
		this.masterKeyIdLabel.setForeground(fgColor);
		this.masterKeyIdLabel.setFont(new Font("Monospaced", Font.PLAIN, 14));
		if (info.getKeyType() == KEY_TYPE.ENC) {
			this.masterKeyIdLabel.setIcon(new ImageIcon(ResourceHelper.getResource(Resource.IMG_KEY_OK_XS)));
		} else if (info.getKeyType() == KEY_TYPE.SIGN) {
			this.masterKeyIdLabel.setIcon(new ImageIcon(ResourceHelper.getResource(Resource.IMG_SIGN_XS)));
		} else {
			this.masterKeyIdLabel.setIcon(new ImageIcon(ResourceHelper.getResource(Resource.IMG_KEY_UNKNOWN_XS)));
		}
		add(this.masterKeyIdLabel, "cell 1 1");

		for (final KeyRingInfo subInfo : info.getSubKeyInfoList()) {
			final JLabel arrowSubLabel = new JLabel(new ImageIcon(ResourceHelper.getResource(Resource.IMG_ARROWTOP_XS)));
			add(arrowSubLabel, "alignx trailing, cell 0 " + this.subKeyRow);
			final JLabel subLabel = new JLabel(Long.toHexString(subInfo.getKeyId()).toUpperCase());
			subLabel.setForeground(fgColor);
			subLabel.setFont(new Font("Monospaced", Font.PLAIN, 14));
			if (subInfo.getKeyType() == KEY_TYPE.ENC) {
				subLabel.setIcon(new ImageIcon(ResourceHelper.getResource(Resource.IMG_KEY_OK_XS)));
			} else if (subInfo.getKeyType() == KEY_TYPE.SIGN) {
				subLabel.setIcon(new ImageIcon(ResourceHelper.getResource(Resource.IMG_SIGN_XS)));
			} else {
				subLabel.setIcon(new ImageIcon(ResourceHelper.getResource(Resource.IMG_KEY_UNKNOWN_XS)));
			}
			add(subLabel, "cell 1 " + this.subKeyRow);
			this.subKeyRow++;
		}

		final String[] relatedPath = info.getRelatingPath();
		if (relatedPath != null) {
			String relatedPathInfo = "(";
			if (relatedPath.length == 0) {
				relatedPathInfo += KeyRingInfoListElementPanel.PATH_SEPARATOR + ")";
			} else {
				for (final String s : relatedPath) {
					relatedPathInfo += KeyRingInfoListElementPanel.PATH_SEPARATOR + s;
				}
				relatedPathInfo += ")";
			}
			this.relatedPathLabel = new JLabel(relatedPathInfo);
			this.relatedPathLabel.setForeground(fgColor);
			add(this.relatedPathLabel, "flowx,cell 0 " + this.subKeyRow + " 2 1,growx");
		}
	}

	/**
	 * Gets the key ring info.
	 *
	 * @return the key ring info
	 */
	public KeyRingInfo getKeyRingInfo() {
		return this.info;
	}
}
