package experimental.module.filebrowser.view;

import static framework.model.type.DataElementType.FILE;
import static framework.model.type.DataElementType.FOLDER;
import helper.ResourceHelper;
import helper.TextFormatHelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import module.iface.DataElementEventListener;
import net.miginfocom.swing.MigLayout;
import experimental.module.filebrowser.control.DataElementComparator;
import experimental.module.filebrowser.control.DataElementComparator.SORT_ORDER;
import experimental.module.filebrowser.control.DataElementComparator.SORT_TYPE;
import experimental.module.filebrowser.control.FileBrowserModule;
import experimental.module.filebrowser.model.Resource;
import experimental.module.filebrowser.model.SortedFolderFileListModel;
import framework.constants.GenericModuleCommands;
import framework.control.LocalizationConnector;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.ProsumerPort;
import framework.model.event.DataElementEvent;

/**
 *
 * @author Stefan Werner
 */
public class FileBrowser extends JFrame implements DataElementEventListener {

	private static final long serialVersionUID = 5546728335570450636L;

	public static final Color GUI_EVENTLOG_BGCOLOR_DEFAULT = new Color(97, 129, 165);
	public static final Color GUI_EVENTLOG_BGCOLOR_SYNC = new Color(0, 170, 0);
	public static final Color GUI_EVENTLOG_BGCOLOR_ERROR = new Color(170, 0, 0);
	public static final Color GUI_EVENTLOG_BGCOLOR_WARN = Color.YELLOW;
	public static final Color GUI_EVENTLOG_BGCOLOR_DEBUG = Color.WHITE;
	public static final Color GUI_EVENTLOG_BGCOLOR_INFO = FileBrowser.GUI_EVENTLOG_BGCOLOR_SYNC;
	public static final Color GUI_EVENTLOG_FGCOLOR = Color.WHITE;
	public static final SimpleDateFormat GUI_EVENTLOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	public static final Font GUI_EVENTLOG_FONT = new Font("Dialog", Font.PLAIN, 9);
	public static final Font GUI_BIG_FONT = new Font("Dialog", Font.BOLD, 22);

	private static final String SEPARATOR = "/";

	private JPanel contentPane;
	private JTextArea relativePathTextPane;
	private JScrollPane elementsScrollPane;
	private JList<DataElement> elementList;
	private JPopupMenu popupMenu;
	private JMenuItem downloadMenuItem;
	private JMenuItem renameMenuItem;
	private JMenuItem deleteMenuItem;
	private JMenuItem shredMenuItem;
	private JMenuItem synchronizeMenuItem;
	private SortedFolderFileListModel elementsListModel;
	private JPanel infoPanel;
	private JLabel typeTextLabel;
	private JLabel sizeTextLabel;
	private JLabel lastModifictaionTextLabel;
	private JTextPane nameLabel;
	private JLabel typeLabel;
	private JLabel sizeLabel;
	private JLabel lastModDateLabel;
	private JLabel infoIconlabel;
	private JLabel moduleNameLabel;
	private JLabel addressSeperatorLabel;
	private JPanel locationPanel;
	private JScrollPane addressScrollPane;
	private JLabel moreSuffixLabel;
	private JLabel morePrefixLabel;
	private JSplitPane splitPane;
	private JSeparator separator;
	private JButton AddRemoveSyncButton;
	private JButton deleteButton;
	private JButton upButton;
	private JButton homeButton;
	private JButton sortTypeOrderButton;
	private JPopupMenu popupMenu_1;
	private JMenuItem alphabeticalfoldersFirstMenuItem;
	private JMenuItem alphabeticalMenuItem;
	private JMenuItem sizeMenuItem;
	private JMenuItem lastModificationDateMenuItem;
	private JSeparator separator_1;
	private JMenuItem ascendingMenuItem;
	private JMenuItem descendingMenuItem;

	private final DataElementComparator comparator = new DataElementComparator();

	private final ProsumerConnector connector;
	private final ProsumerPort port;
	private DataElement currentFolderElement;
	private DataElement rootFolderElement;
	private LoadingOverlayPanel loadingOverlayPanel;
	private Thread loadingThread;
	private JButton reloadButton;
	private final LogConnector logConnector;
	private final LocalizationConnector localizationConnector;
	private boolean accessModeSupported = false;
	private boolean closed = false;

	public FileBrowser(final FileBrowserModule module, final ProsumerPort port, final ProsumerConnector connector, final LogConnector logConnector, final LocalizationConnector localizationConnector) {
		this.connector = connector;
		this.port = port;
		this.logConnector = logConnector;
		this.localizationConnector = localizationConnector;
		try {
			this.rootFolderElement = connector.getElement(port, new String[0]);
			this.currentFolderElement = this.rootFolderElement;
			final Set<String> supportedCommands = connector.getSupportedModuleCommands(port, null);
			if ((supportedCommands != null) && supportedCommands.contains(GenericModuleCommands.GET_ACCESS_MODE)) {
				this.accessModeSupported = true;
			}
			initialize();
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			logConnector.log(e);
		}
		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosed(final WindowEvent e) {
				FileBrowser.this.closed = true;
				module.setFileBrowserClosed();
			}
		});
	}

	private void abortLoadingThread() {
		if (this.loadingThread != null) {
			this.loadingThread.interrupt();
		}
	}

	private void addElementPopupMenu() {
		this.popupMenu = new JPopupMenu();
		addPopup(this.elementList, this.popupMenu);

		this.synchronizeMenuItem = new JMenuItem("Store locally and synchronize");
		this.synchronizeMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				FileBrowser.this.elementsListModel.update();
			}
		});
		this.synchronizeMenuItem.setIcon(new ImageIcon(FileOrFolderElementPanel.class.getResource("/icons/syncing_xs.png")));
		this.synchronizeMenuItem.setMargin(new Insets(8, 0, 8, 0));
		this.popupMenu.add(this.synchronizeMenuItem);
		this.downloadMenuItem = new JMenuItem("Download once");
		this.downloadMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
			}
		});
		this.downloadMenuItem.setIcon(new ImageIcon(FileOrFolderElementPanel.class.getResource("/icons/arrow_top_xs.png")));
		this.downloadMenuItem.setMargin(new Insets(8, 0, 8, 0));
		this.popupMenu.add(this.downloadMenuItem);

		this.renameMenuItem = new JMenuItem("Rename");
		this.renameMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
			}
		});
		this.renameMenuItem.setIcon(new ImageIcon(FileOrFolderElementPanel.class.getResource("/icons/sign_xs.png")));
		this.renameMenuItem.setMargin(new Insets(8, 0, 8, 0));
		this.popupMenu.add(this.renameMenuItem);

		this.deleteMenuItem = new JMenuItem("Delete");
		this.deleteMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
			}
		});
		this.deleteMenuItem.setIcon(new ImageIcon(FileOrFolderElementPanel.class.getResource("/icons/key_unknown_xs.png")));
		this.deleteMenuItem.setMargin(new Insets(8, 0, 8, 0));
		this.deleteMenuItem.setEnabled(false);
		this.popupMenu.add(this.deleteMenuItem);

		this.shredMenuItem = new JMenuItem("Shred (overwrite before deletion)");
		this.shredMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
			}
		});
		this.shredMenuItem.setIcon(new ImageIcon(FileOrFolderElementPanel.class.getResource("/icons/key_unknown_xs.png")));
		this.shredMenuItem.setMargin(new Insets(8, 0, 8, 0));
		this.popupMenu.add(this.shredMenuItem);

	}

	private void addPopup(final JList<DataElement> component, final JPopupMenu popup) {
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					final int row = FileBrowser.this.elementList.locationToIndex(e.getPoint());
					if (!Arrays.asList(FileBrowser.this.elementList.getSelectedIndices()).contains(row)) {
						FileBrowser.this.elementList.setSelectedIndex(row);
					}
					showMenu(e);
				}
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}

			private void showMenu(final MouseEvent e) {
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}

	private void addSortPopupMenu() {
		this.popupMenu_1 = new JPopupMenu();

		this.alphabeticalfoldersFirstMenuItem = new JMenuItem("Alphabetical (folders first)");
		this.alphabeticalfoldersFirstMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				FileBrowser.this.comparator.setType(SORT_TYPE.ALPHABETICAL_FF);
				resetSortPopupMenu();
				fillElementList(false);
			}
		});
		this.popupMenu_1.add(this.alphabeticalfoldersFirstMenuItem);

		this.alphabeticalMenuItem = new JMenuItem("Alphabetical");
		this.alphabeticalMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				FileBrowser.this.comparator.setType(SORT_TYPE.ALPHABETICAL);
				resetSortPopupMenu();
				fillElementList(false);
			}
		});
		this.popupMenu_1.add(this.alphabeticalMenuItem);

		this.sizeMenuItem = new JMenuItem("Size");
		this.sizeMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				FileBrowser.this.comparator.setType(SORT_TYPE.SIZE);
				resetSortPopupMenu();
				fillElementList(false);
			}
		});
		this.popupMenu_1.add(this.sizeMenuItem);

		this.lastModificationDateMenuItem = new JMenuItem("Last modification date");
		this.lastModificationDateMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				FileBrowser.this.comparator.setType(SORT_TYPE.LAST_MOD_DATE);
				resetSortPopupMenu();
				fillElementList(false);
			}
		});
		this.popupMenu_1.add(this.lastModificationDateMenuItem);

		this.separator_1 = new JSeparator();
		this.popupMenu_1.add(this.separator_1);

		this.ascendingMenuItem = new JMenuItem("Ascending");
		this.ascendingMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				FileBrowser.this.comparator.setOrder(SORT_ORDER.ASC);
				resetSortPopupMenu();
				fillElementList(false);
			}
		});
		this.popupMenu_1.add(this.ascendingMenuItem);

		this.descendingMenuItem = new JMenuItem("Descending");
		this.descendingMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent arg0) {
				FileBrowser.this.comparator.setOrder(SORT_ORDER.DESC);
				resetSortPopupMenu();
				fillElementList(false);
			}
		});
		this.popupMenu_1.add(this.descendingMenuItem);

		this.sortTypeOrderButton.addActionListener(new ActionListener() {
			/* (non-Javadoc)
			 *
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
			@Override
			public void actionPerformed(final ActionEvent e) {
				FileBrowser.this.popupMenu_1.show(FileBrowser.this.sortTypeOrderButton, 0, FileBrowser.this.sortTypeOrderButton.getHeight());
			}
		});
		resetSortPopupMenu();
	}

	private void changePath(final DataElement element) {
		if (element != null) {
			this.currentFolderElement = element;
			fillElementList(false);
			this.relativePathTextPane.setText(TextFormatHelper.getPathString(this.currentFolderElement.getPath()));
		}
	}

	public void close() {
		setVisible(false);
		abortLoadingThread();
		dispose();
	}

	private void enableInfoPanel() {
		this.infoIconlabel.setVisible(true);
		// nameLabel.setVisible(true);
		// separator.setVisible(true);
		this.typeTextLabel.setEnabled(true);
		this.sizeTextLabel.setEnabled(true);
		this.lastModifictaionTextLabel.setEnabled(true);
		this.nameLabel.setEnabled(true);
		this.typeLabel.setEnabled(true);
		this.sizeLabel.setEnabled(true);
		this.lastModDateLabel.setEnabled(true);
	}

	private void fillElementList(final boolean refresh) {
		if (this.closed) {
			return;
		}
		updateControls();
		this.elementList.clearSelection();
		this.elementList.setEnabled(false);
		this.loadingOverlayPanel.resetProgress();
		this.loadingOverlayPanel.setStatusText(this.localizationConnector.getLocalizedString("Loading..."));
		this.loadingThread = new Thread(new Runnable() {
			/* (non-Javadoc)
			 *
			 * @see java.lang.Runnable#run() */
			@Override
			public void run() {
				FileBrowser.this.elementsListModel.clear();
				FileBrowser.this.elementList.removeAll();
				try {
					if (FileBrowser.this.currentFolderElement.getType() == FOLDER) {
						final Set<DataElement> childList = FileBrowser.this.connector.getChildElements(FileBrowser.this.port, FileBrowser.this.currentFolderElement.getPath(), false);
						if (childList != null) {
							EventQueue.invokeLater(new Runnable() {
								/* (non-Javadoc)
								 *
								 * @see java.lang.Runnable#run() */
								@Override
								public void run() {
									FileBrowser.this.loadingOverlayPanel.setMaxProgress(childList.size());
								}
							});
							for (final DataElement elem : childList) {
								if (Thread.currentThread().isInterrupted()) {
									return;
								}
								FileBrowser.this.elementsListModel.add(elem);
								EventQueue.invokeLater(new Runnable() {
									/* (non-Javadoc)
									 *
									 * @see java.lang.Runnable#run() */
									@Override
									public void run() {
										FileBrowser.this.loadingOverlayPanel.increaseProgress();
									}
								});
							}
							EventQueue.invokeLater(new Runnable() {
								/* (non-Javadoc)
								 *
								 * @see java.lang.Runnable#run() */
								@Override
								public void run() {
									FileBrowser.this.loadingOverlayPanel.setStatusText(FileBrowser.this.localizationConnector.getLocalizedString("Done."));
									FileBrowser.this.loadingOverlayPanel.hideLoadingComponents();
								}
							});
						}
					} else {
						EventQueue.invokeLater(new Runnable() {
							/* (non-Javadoc)
							 *
							 * @see java.lang.Runnable#run() */
							@Override
							public void run() {
								FileBrowser.this.loadingOverlayPanel.setStatusText(FileBrowser.this.localizationConnector.getLocalizedString("Error while retrieving children."));
								FileBrowser.this.loadingOverlayPanel.hideLoadingComponents();
							}
						});
					}
				} catch (BrokerException | AuthorizationException | ModuleException e) {
					e.printStackTrace(); // TODO -> log
					FileBrowser.this.loadingOverlayPanel.setStatusText(FileBrowser.this.localizationConnector.getLocalizedString("Failed."));
				}
				FileBrowser.this.elementList.setEnabled(true);
			}
		});
		this.loadingThread.start();
	}

	private void goHome() {
		if (!this.currentFolderElement.equals(this.rootFolderElement)) {
			changePath(this.rootFolderElement);
		}
	}

	private void goUp() {
		if (this.currentFolderElement == null) {
			return;
		}
		final String[] path = this.currentFolderElement.getPath();
		if (this.currentFolderElement.getPath().length > 0) {
			final String[] parentPath = Arrays.copyOf(path, path.length - 1);
			try {

				changePath(this.connector.getElement(this.port, parentPath));
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void initialize() {
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 850, 600);
		this.contentPane = new JPanel();
		setContentPane(this.contentPane);
		this.contentPane.setLayout(new MigLayout("", "[grow]", "[][grow][]"));

		this.upButton = new JButton();
		this.upButton.putClientProperty("JButton.buttonType", "segmented");
		this.upButton.putClientProperty("JButton.segmentPosition", "first");
		this.upButton.setIcon(new ImageIcon(FileBrowser.class.getResource("/icons/arrow_top_xs.png")));
		this.upButton.addActionListener(new ActionListener() {
			/* (non-Javadoc)
			 *
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
			@Override
			public void actionPerformed(final ActionEvent e) {
				goUp();
			}
		});
		this.contentPane.add(this.upButton, "flowx,cell 0 0,alignx center,gapright 0,growy");

		this.reloadButton = new JButton();
		this.reloadButton.putClientProperty("JButton.buttonType", "segmented");
		this.reloadButton.putClientProperty("JButton.segmentPosition", "middle");
		this.reloadButton.setIcon(new ImageIcon(FileBrowser.class.getResource("/icons/syncing_xs.png")));
		this.reloadButton.addActionListener(new ActionListener() {
			/* (non-Javadoc)
			 *
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
			@Override
			public void actionPerformed(final ActionEvent e) {
				fillElementList(true);
			}
		});
		this.contentPane.add(this.reloadButton, "cell 0 0,alignx center,gapx 0,growy");

		this.homeButton = new JButton();
		this.homeButton.putClientProperty("JButton.buttonType", "segmented");
		this.homeButton.putClientProperty("JButton.segmentPosition", "last");
		this.homeButton.setIcon(new ImageIcon(FileBrowser.class.getResource("/icons/padlock_xs.png")));
		this.homeButton.addActionListener(new ActionListener() {
			/* (non-Javadoc)
			 *
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
			@Override
			public void actionPerformed(final ActionEvent e) {
				goHome();
			}
		});
		this.contentPane.add(this.homeButton, "cell 0 0,alignx center,gapleft 0,growy");

		this.locationPanel = new JPanel();
		this.contentPane.add(this.locationPanel, "cell 0 0,growx,aligny top");
		this.locationPanel.setLayout(new MigLayout("ins 0, gap 0,hidemode 3", "[][::50%][::80%,grow]", "[]"));
		this.locationPanel.setBorder(new JTextField().getBorder());

		this.moduleNameLabel = new JLabel("-?-");
		this.moduleNameLabel.setOpaque(true);
		this.moduleNameLabel.setBorder(BorderFactory.createMatteBorder(2, 4, 2, 4, FileBrowser.GUI_EVENTLOG_BGCOLOR_DEFAULT));
		this.moduleNameLabel.setBackground(FileBrowser.GUI_EVENTLOG_BGCOLOR_DEFAULT);
		this.moduleNameLabel.setForeground(FileBrowser.GUI_EVENTLOG_FGCOLOR);
		this.locationPanel.add(this.moduleNameLabel, "flowx,cell 1 0,gapright 0,aligny center");
		this.addressSeperatorLabel = new JLabel(FileBrowser.SEPARATOR);
		this.addressSeperatorLabel.setOpaque(true);
		this.addressSeperatorLabel.setBorder(BorderFactory.createMatteBorder(2, 4, 2, 0, FileBrowser.GUI_EVENTLOG_FGCOLOR));
		this.locationPanel.add(this.addressSeperatorLabel, "flowx,cell 1 0,gapx 0,aligny center");
		this.moreSuffixLabel = new JLabel("...");
		this.moreSuffixLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		this.moreSuffixLabel.setVisible(false);
		this.moreSuffixLabel.setOpaque(true);
		this.moreSuffixLabel.setBorder(BorderFactory.createMatteBorder(2, 4, 2, 4, FileBrowser.GUI_EVENTLOG_FGCOLOR));
		// label_1.setBackground(Constants.GUI_EVENTLOG_BGCOLOR_DEFAULT);
		// label_1.setForeground(Constants.GUI_EVENTLOG_FGCOLOR);
		this.locationPanel.add(this.moreSuffixLabel, "cell 1 0,aligny center");

		this.addressScrollPane = new JScrollPane();
		this.addressScrollPane.setBorder(null);
		this.addressScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
		this.addressScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		this.addressScrollPane.getViewport().addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				FileBrowser.this.moreSuffixLabel.setVisible(FileBrowser.this.addressScrollPane.getViewport().getViewPosition().getX() > 0);
				FileBrowser.this.morePrefixLabel.setVisible((FileBrowser.this.addressScrollPane.getViewport().getViewSize().width - FileBrowser.this.addressScrollPane.getViewport().getViewPosition().x) > FileBrowser.this.addressScrollPane.getViewport().getVisibleRect().getSize().width);
			}
		});

		this.locationPanel.add(this.addressScrollPane, "cell 2 0,growx,aligny center");

		this.relativePathTextPane = new JTextArea();
		setupRelativePathTextArea();
		this.addressScrollPane.setViewportView(this.relativePathTextPane);
		this.relativePathTextPane.setRows(1);
		this.relativePathTextPane.setBorder(null);
		this.relativePathTextPane.setMinimumSize(new Dimension(0, 0));

		this.morePrefixLabel = new JLabel("...");
		this.morePrefixLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		this.morePrefixLabel.setVisible(false);
		this.locationPanel.add(this.morePrefixLabel, "cell 2 0,aligny center");

		this.sortTypeOrderButton = new JButton();
		this.sortTypeOrderButton.setInheritsPopupMenu(true);
		this.sortTypeOrderButton.setIcon(new ImageIcon(FileBrowser.class.getResource("/icons/ok_xs.png")));
		this.contentPane.add(this.sortTypeOrderButton, "cell 0 0");

		// ////////////////////////////////////////

		this.splitPane = new JSplitPane();
		this.splitPane.setResizeWeight(1.0);
		this.splitPane.setOneTouchExpandable(true);
		this.contentPane.add(this.splitPane, "cell 0 1,grow");

		this.elementsScrollPane = new JScrollPane();
		this.elementsScrollPane.setViewportBorder(new SoftBevelBorder(BevelBorder.LOWERED, null, null, null, null));

		this.elementsListModel = new SortedFolderFileListModel(this.comparator);
		final ElementsListRenderer renderer = new ElementsListRenderer(this.port, this.connector, this.logConnector, this.localizationConnector, this.accessModeSupported);
		this.elementList = new JList<DataElement>();
		this.elementList.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(final ListSelectionEvent arg0) {
				updateInfoPanel();
			}
		});
		this.elementList.setModel(this.elementsListModel);
		this.elementList.setCellRenderer(renderer);
		this.elementList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		this.elementList.setLayoutOrientation(JList.VERTICAL_WRAP);
		this.elementList.setVisibleRowCount(-1);
		this.elementList.setFixedCellWidth(200);
		this.elementList.setFixedCellHeight(40);

		this.elementList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent evt) {
				if (evt.getClickCount() == 2) {
					final List<DataElement> selection = FileBrowser.this.elementList.getSelectedValuesList();
					if (selection.size() == 1) {
						changePath(selection.get(0));
					}
				} else if (evt.getClickCount() == 3) {
					// triple click ;)
				}
			}
		});

		this.elementsScrollPane.setViewportView(this.elementList);
		this.splitPane.setLeftComponent(this.elementsScrollPane);

		this.infoPanel = new JPanel();
		this.infoPanel.setMinimumSize(new Dimension(150, 10));
		this.infoPanel.setPreferredSize(new Dimension(150, 10));
		this.infoPanel.setBorder(new JScrollPane().getBorder());
		this.splitPane.setRightComponent(this.infoPanel);
		this.infoPanel.setLayout(new MigLayout("hidemode 3", "[grow]", "[][][][][][][][][][grow][]"));

		this.infoIconlabel = new JLabel();
		this.infoIconlabel.setVisible(false);
		this.infoIconlabel.setIcon(ResourceHelper.getImageIconByName(Resource.IMG_FILE_LOCAL_M));
		this.infoPanel.add(this.infoIconlabel, "cell 0 0,alignx center");

		this.nameLabel = new JTextPane();
		this.nameLabel.setEditorKit(new WrapEditorKit());
		final StyledDocument doc = this.nameLabel.getStyledDocument();
		final SimpleAttributeSet center = new SimpleAttributeSet();
		StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
		doc.setParagraphAttributes(0, doc.getLength(), center, false);
		this.nameLabel.setBorder(null);
		this.nameLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
		this.nameLabel.setMinimumSize(new Dimension(0, 0));
		this.nameLabel.setEditable(false);
		this.infoPanel.add(this.nameLabel, "cell 0 1,growx");

		this.separator = new JSeparator();
		this.infoPanel.add(this.separator, "cell 0 2,growx");

		this.typeTextLabel = new JLabel(this.localizationConnector.getLocalizedString("Type:"));
		this.typeTextLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		this.typeTextLabel.setEnabled(false);
		this.infoPanel.add(this.typeTextLabel, "cell 0 3");

		this.typeLabel = new JLabel();
		this.typeLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
		this.typeLabel.setMinimumSize(new Dimension(0, 0));
		this.infoPanel.add(this.typeLabel, "cell 0 4");

		this.sizeTextLabel = new JLabel(this.localizationConnector.getLocalizedString("Size:"));
		this.sizeTextLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		this.sizeTextLabel.setEnabled(false);
		this.infoPanel.add(this.sizeTextLabel, "cell 0 5");

		this.sizeLabel = new JLabel();
		this.sizeLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
		this.sizeLabel.setMinimumSize(new Dimension(0, 0));
		this.infoPanel.add(this.sizeLabel, "cell 0 6");

		this.lastModifictaionTextLabel = new JLabel(this.localizationConnector.getLocalizedString("Modified:"));
		this.lastModifictaionTextLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		this.lastModifictaionTextLabel.setEnabled(false);
		this.infoPanel.add(this.lastModifictaionTextLabel, "cell 0 7");

		this.lastModDateLabel = new JLabel();
		this.lastModDateLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
		this.lastModDateLabel.setMinimumSize(new Dimension(0, 0));
		this.infoPanel.add(this.lastModDateLabel, "cell 0 8");

		this.AddRemoveSyncButton = new JButton(this.localizationConnector.getLocalizedString("Syncronize"));
		this.AddRemoveSyncButton.setIcon(ResourceHelper.getImageIconByName(Resource.IMG_SYNCING_XS));
		this.infoPanel.add(this.AddRemoveSyncButton, "flowy,cell 0 10,growx");

		this.deleteButton = new JButton(this.localizationConnector.getLocalizedString("Delete"));
		this.deleteButton.setIcon(ResourceHelper.getImageIconByName(Resource.IMG_KEY_UNKNOWN_XS));
		this.infoPanel.add(this.deleteButton, "cell 0 10,growx");

		this.loadingOverlayPanel = new LoadingOverlayPanel(new ActionListener() {
			/* (non-Javadoc)
			 *
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent) */
			@Override
			public void actionPerformed(final ActionEvent e) {
				abortLoadingThread();
			}
		});
		this.loadingOverlayPanel.setBorder(new JScrollPane().getBorder());
		this.contentPane.add(this.loadingOverlayPanel, "cell 0 2,growx");
		resetInfoPanel();
		addElementPopupMenu();
		addSortPopupMenu();
		fillElementList(false);
	}

	/* (non-Javadoc)
	 *
	 * @see module.iface.DataElementEventListener#onElementEvent(framework.model.ProsumerPort, framework.model.event.DataElementEvent) */
	@Override
	public void onElementEvent(final ProsumerPort port, final DataElementEvent event) {
		switch (event.eventType) {
		case ADD:
			break;
		case DELETE:
			break;
		case MODIFY:
			break;
		}
	}

	private void resetInfoPanel() {
		this.infoIconlabel.setVisible(false);
		// nameLabel.setVisible(false);
		// separator.setVisible(false);
		this.typeTextLabel.setEnabled(false);
		this.sizeTextLabel.setEnabled(false);
		this.lastModifictaionTextLabel.setEnabled(false);
		// nameLabel.setEnabled(false);
		this.typeLabel.setEnabled(false);
		this.sizeLabel.setEnabled(false);
		this.lastModDateLabel.setEnabled(false);
		this.nameLabel.setText("no selection");
		this.typeLabel.setText("-");
		this.sizeLabel.setText("-");
		this.lastModDateLabel.setText("-");
	}

	private void resetSortPopupMenu() {
		this.alphabeticalfoldersFirstMenuItem.setFont(new Font("Dialog", Font.PLAIN, 13));
		this.alphabeticalMenuItem.setFont(new Font("Dialog", Font.PLAIN, 13));
		this.sizeMenuItem.setFont(new Font("Dialog", Font.PLAIN, 13));
		this.lastModificationDateMenuItem.setFont(new Font("Dialog", Font.PLAIN, 13));
		this.ascendingMenuItem.setFont(new Font("Dialog", Font.PLAIN, 13));
		this.descendingMenuItem.setFont(new Font("Dialog", Font.PLAIN, 13));

		switch (this.comparator.getType()) {
		case ALPHABETICAL:
			this.alphabeticalMenuItem.setFont(new Font("Dialog", Font.BOLD, 13));
			break;
		case ALPHABETICAL_FF:
			this.alphabeticalfoldersFirstMenuItem.setFont(new Font("Dialog", Font.BOLD, 13));
			break;
		case LAST_MOD_DATE:
			this.lastModificationDateMenuItem.setFont(new Font("Dialog", Font.BOLD, 13));
			break;
		case SIZE:
			this.sizeMenuItem.setFont(new Font("Dialog", Font.BOLD, 13));
			break;
		}

		switch (this.comparator.getOrder()) {
		case ASC:
			this.ascendingMenuItem.setFont(new Font("Dialog", Font.BOLD, 13));
			break;
		case DESC:
			this.descendingMenuItem.setFont(new Font("Dialog", Font.BOLD, 13));
			break;
		}
	}

	private void setupRelativePathTextArea() {
		final InputMap inputMap = this.relativePathTextPane.getInputMap(JComponent.WHEN_FOCUSED);
		final ActionMap actionMap = this.relativePathTextPane.getActionMap();

		// the key stroke we want to capture
		final KeyStroke enterStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);

		// tell input map that we are handling the enter key
		inputMap.put(enterStroke, enterStroke.toString());

		// tell action map just how we want to handle the enter key
		actionMap.put(enterStroke.toString(), new AbstractAction() {

			/**
			 *
			 */
			private static final long serialVersionUID = -7218398124331350728L;

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				// try {
				// // TODO changePath(localStorage.resolveRelativePath(relativePathTextPane.getText()));
				// } catch (BrokerException e) {
				// // TODO Auto-generated catch block
				// e.printStackTrace();
				// }
			}
		});
	}

	// public void onElementEvent(ApacheCommonsVFSStorageModuleConfigurator config, EVENT_TYPE eventType, DataElement element) {
	// switch (eventType) {
	// case ADD:
	// System.out.println("Got add event, comparing " + element.getParentRelativePath() + currentFolderElement.getRelativePath());
	// if (element.getParentRelativePath().equals(currentFolderElement.getRelativePath())) {
	// listModel.add(element);
	// }
	// break;
	// case DELETE:
	// System.out.println("Got del event, comparing " + element.getParentRelativePath() + currentFolderElement.getRelativePath());
	// if (element.getParentRelativePath().equals(currentFolderElement.getRelativePath())) {
	// listModel.removeElement(element);
	// }
	// break;
	// case MODIFY:
	// System.out.println("Got mod event, comparing " + element.getParentRelativePath() + currentFolderElement.getRelativePath());
	// if (element.getParentRelativePath().equals(currentFolderElement.getRelativePath())) {
	// listModel.add(element);
	// }
	// break;
	// case OWN:
	// break;
	// }
	// }

	private void updateControls() {
		if ((this.currentFolderElement != null) && this.currentFolderElement.equals(this.rootFolderElement)) {
			this.upButton.setEnabled(false);
			this.homeButton.setEnabled(false);
		} else {
			this.upButton.setEnabled(true);
			this.homeButton.setEnabled(true);
		}
	}

	private void updateInfoPanel() {
		final List<DataElement> elemList = this.elementList.getSelectedValuesList();

		if (elemList.size() == 0) {
			resetInfoPanel();
		} else if (elemList.size() == 1) {
			final DataElement elem = elemList.get(0);
			if (elem.getType() == FILE) {
				this.infoIconlabel.setIcon(ResourceHelper.getImageIconByName(Resource.IMG_FILE_LOCAL_M));
			} else if (elem.getType() == FOLDER) {
				this.infoIconlabel.setIcon(ResourceHelper.getImageIconByName(Resource.IMG_FOLDER_LOCAL_M));
			} else {
				this.infoIconlabel.setIcon(ResourceHelper.getImageIconByName(Resource.IMG_ERROR_M));
			}
			this.nameLabel.setText(elem.getName());
			this.typeLabel.setText(elem.getType().toString());
			this.sizeLabel.setText(TextFormatHelper.convertSizeValueToHumanReadableFormat(elem.getSize()));
			this.lastModDateLabel.setText(this.localizationConnector.getFormatedDateLocalized(elem.getModificationDate()));
			enableInfoPanel();
		} else {
			int folderCount = 0;
			int fileCount = 0;
			long sumSize = 0;
			long latestModDate = 0;
			for (final DataElement elem : elemList) {
				if (elem.getType() == FOLDER) {
					folderCount++;
				} else if (elem.getType() == FILE) {
					fileCount++;
					sumSize += elem.getSize();
				}
				if (elem.getModificationDate() > latestModDate) {
					latestModDate = elem.getModificationDate();
				}
			}
			String typeText = "";
			if ((folderCount > 0) && (fileCount > 0)) {
				typeText = folderCount + " folder(s), " + fileCount + " file(s)";
			} else if (folderCount > 0) {
				typeText = folderCount + " folder(s)";
			} else if (fileCount > 0) {
				typeText = fileCount + " file(s)";
			} else {
				resetInfoPanel();
				return;
			}
			this.infoIconlabel.setIcon(ResourceHelper.getImageIconByName(Resource.IMG_STOPPED_M));
			this.nameLabel.setText(elemList.size() + " elements selected");
			this.typeLabel.setText(typeText);
			this.sizeLabel.setText(TextFormatHelper.convertSizeValueToHumanReadableFormat(sumSize));
			this.lastModDateLabel.setText(String.valueOf(latestModDate));
			enableInfoPanel();
		}
	}
}
