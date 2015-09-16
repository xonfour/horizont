package experimental.module.filebrowser.view;

import helper.ResourceHelper;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import experimental.module.filebrowser.content.control.ContentResolver;
import experimental.module.filebrowser.model.Resource;
import framework.constants.GenericModuleCommandProperties;
import framework.constants.GenericModuleCommands;
import framework.control.LocalizationConnector;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.ProsumerPort;
import framework.model.type.DataElementType;

public class ElementsListRenderer extends DefaultListCellRenderer {

	private static final long serialVersionUID = -7121927237609035067L;

	private final HashMap<String, JLabel> imageCacheMap = new HashMap<String, JLabel>();
	private final ExecutorService executor = Executors.newFixedThreadPool(4, new ThreadFactoryBuilder().setNameFormat(ElementsListRenderer.class.getSimpleName() + "-%d").build());
	private final ProsumerConnector connector;
	private final ProsumerPort port;
	private final LogConnector logConnector;
	private final boolean accessModeSupported;

	public ElementsListRenderer(final ProsumerPort port, final ProsumerConnector connector, final LogConnector logConnector, final LocalizationConnector localizationConnector, final boolean accessModeSupported) {
		this.connector = connector;
		this.port = port;
		this.logConnector = logConnector;
		this.accessModeSupported = accessModeSupported;
	}

	/**
	 * Adds an image cache request to queue
	 *
	 * @param list
	 * @param url URL to fetch (gallery) image from
	 * @param comp Graphical Component where to draw image once it is loaded (asynchronously)
	 */
	public void addImageCacheRequest(final DataElement element, final JLabel label, final JList<?> list) {
		if ((element == null) || (label == null)) {
			return;
		}
		final Runnable imageCacheRequestRunnable = new Runnable() {
			@Override
			public void run() {
				Image previewImage = null;
				try {
					if (element.getType() == DataElementType.FILE) {
						InputStream in = ElementsListRenderer.this.connector.readData(ElementsListRenderer.this.port, element.getPath());
						if (in != null) {
							in = new BufferedInputStream(in);
							previewImage = ContentResolver.getPreviewImageOfContent(element, in, 50, 50);
							try {
								in.close();
							} catch (final IOException e) {
							}
						}
					}
				} catch (BrokerException | ModuleException | AuthorizationException e) {
					ElementsListRenderer.this.logConnector.log(e);
				}
				ImageIcon icon = null;
				if (previewImage != null) {
					icon = new ImageIcon(previewImage);
					label.setIcon(icon);
				} else {
					if (ElementsListRenderer.this.accessModeSupported) {
						try {
							final Map<String, String> result = ElementsListRenderer.this.connector.sendModuleCommand(ElementsListRenderer.this.port, GenericModuleCommands.GET_ACCESS_MODE, element.getPath(), null);
							if (result != null) {
								final String mode = result.get(GenericModuleCommandProperties.KEY___ACCESS_MODE);
								if (mode.equals(GenericModuleCommandProperties.VALUE_ACCESS_MODE___PRIVATE)) {
									if (element.getType() == DataElementType.FILE) {
										icon = ResourceHelper.getImageIconByName(Resource.IMG_FILE_REMOTE_ENC_M);
									} else if (element.getType() == DataElementType.FOLDER) {
										icon = ResourceHelper.getImageIconByName(Resource.IMG_FOLDER_REMOTE_ENC_M);
									}
								} else if (mode.equals(GenericModuleCommandProperties.VALUE_ACCESS_MODE___SHARED)) {
									if (element.getType() == DataElementType.FILE) {
										icon = ResourceHelper.getImageIconByName(Resource.IMG_FILE_REMOTE_M);
									} else if (element.getType() == DataElementType.FOLDER) {
										icon = ResourceHelper.getImageIconByName(Resource.IMG_FOLDER_REMOTE_M);
									}
								} else {
									if (element.getType() == DataElementType.FILE) {
										icon = ResourceHelper.getImageIconByName(Resource.IMG_FILE_LOCAL_M);
									} else if (element.getType() == DataElementType.FOLDER) {
										icon = ResourceHelper.getImageIconByName(Resource.IMG_FOLDER_LOCAL_M);
									}
								}
							}
						} catch (BrokerException | ModuleException | AuthorizationException e) {
							icon = ResourceHelper.getImageIconByName(Resource.IMG_FILE_LOCAL_M);
							ElementsListRenderer.this.logConnector.log(e);
						}
					} else {
						if (element.getType() == DataElementType.FILE) {
							icon = ResourceHelper.getImageIconByName(Resource.IMG_FILE_LOCAL_M);
						} else if (element.getType() == DataElementType.FOLDER) {
							icon = ResourceHelper.getImageIconByName(Resource.IMG_FOLDER_LOCAL_M);
						}
					}
				}
				if (icon != null) {
					label.setIcon(icon);
					// label.setPreferredSize(new Dimension(0, icon.getIconHeight()));
				}

				// label.validate();
				// label.invalidate();
				// label.revalidate();
				list.setFixedCellHeight(icon.getIconHeight() + 4);
				list.setFixedCellHeight(-1);
				// list.validate();

			}
		};
		this.executor.execute(imageCacheRequestRunnable);
	}

	@Override
	public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
		String name;
		DataElement element = null;
		if (value instanceof DataElement) {
			element = (DataElement) value;
			name = element.getName();
		} else {
			name = value.toString();
		}
		JLabel label = null;
		synchronized (this.imageCacheMap) {
			label = this.imageCacheMap.get(name);
			if (label == null) {
				label = new JLabel(name);
				label.setOpaque(true);
				label.setMinimumSize(new Dimension(0, 0));
				label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
				this.imageCacheMap.put(name, label);
				addImageCacheRequest(element, label, list);
			} else {
			}
		}
		if (isSelected) {
			label.setBackground(FileBrowser.GUI_EVENTLOG_FGCOLOR);
			label.setForeground(Color.WHITE);
		} else {
			label.setBackground(Color.WHITE);
			label.setForeground(Color.BLACK);
		}
		return label;
	}
}