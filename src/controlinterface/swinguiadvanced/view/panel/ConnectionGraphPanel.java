package controlinterface.swinguiadvanced.view.panel;

import framework.model.event.type.ConnectionEventType;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.ModuleSummary;
import framework.model.summary.PortSummary;
import framework.model.type.PortType;
import helper.PersistentConfigurationHelper;

import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.mxgraph.layout.mxEdgeLabelLayout;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.model.mxIGraphModel;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxDomUtils;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphSelectionModel;
import com.mxgraph.view.mxStylesheet;

import controlinterface.swinguiadvanced.control.SwingAdvancedControlInterface;

/**
 * Visualizes connections between modules/ports as an interactive graph.
 */
public class ConnectionGraphPanel extends JPanel {

	/**
	 * The Class FindRelaxingSpotLayout.
	 *
	 * Used to find a nice initial placement for new modules
	 *
	 * TODO: Broken, currently unused.
	 */
	public static class FindRelaxingSpotLayout extends mxFastOrganicLayout {

		private final mxCell moveMe;

		/**
		 * Instantiates a new find relaxing spot layout.
		 *
		 * @param graph the graph
		 * @param vertex the vertex
		 */
		public FindRelaxingSpotLayout(final mxGraph graph, final Object vertex) {
			super(graph);
			this.moveMe = (mxCell) vertex;
			setForceConstant(500);
			setInitialTemp(10);
			setMaxDistanceLimit(1);
			setMinDistanceLimit(1);
			setDisableEdgeStyle(false);
			setResetEdges(false);
		}

		@Override
		public boolean isVertexIgnored(final Object vertex) {
			return !this.graph.getModel().isVertex(vertex) || !this.graph.isCellVisible(vertex);
		}

		@Override
		public boolean isVertexMovable(final Object vertex) {
			return vertex == this.moveMe;
		}
	}

	public static final String CONFIG___DOMAIN_VIEW_POS = "viewpos";
	public static final String CONFIG___VIEW_POS_KEY_PREFIX_X = "vp_x_";
	public static final String CONFIG___VIEW_POS_KEY_PREFIX_Y = "vp_y_";
	public static final String CONFIG___VIEW_X_POS = "xpos";
	public static final String CONFIG___VIEW_Y_POS = "ypos";
	public static final String CONNECTION_GRAPH___ERROR_MESSAGE_TITLE = "Graph View Error";
	public static final String CONNECTION_HASH = "connhash";
	public static final String CONNECTION_PRIO = "connprio";
	public static final String EDGE_STYLE_BOLD = "strokeWidth=4.0";
	public static final String EDGE_STYLE_DASHEDRED = "strokeColor=red;dashed=1;strokeWidth=2.0";
	public static final String EDGE_STYLE_NORMAL = "strokeWidth=2.0";
	public static final String ELEMENT_CLASS_CONNECTION = "connection";
	public static final String ELEMENT_CLASS_MODULE = "module";
	public static final String ELEMENT_CLASS_PORT = "port";
	public static final String ELEMENT_LABEL = "label";
	public static final int MODULE_CELL_MARGIN = 10;
	public static final int MODULE_CELL_MARGIN_BOTTOM = 10;
	public static final int MODULE_CELL_MARGIN_TOP = 20;
	public static final int MODULE_CELL_MINHEIGHT = 60;
	public static final int MODULE_CELL_PORT_SPACING = 6;
	public static final int MODULE_CELL_WIDTH = 146;
	public static final String MODULE_MODULE_ID = "modid";
	public static final int PORT_CELL_HEIGHT = 20;
	public static final int PORT_CELL_WIDTH = 70;
	public static final int PORT_CELL_XOFFSET = 0;
	public static final String PORT_MODULE_ID = "modid";
	public static final String PORT_PORT_CURCON = "curcon";
	public static final String PORT_PORT_ID = "portid";
	public static final String PORT_PORT_MAXCON = "maxcon";
	public static final String PORT_TYPE = "type";
	private static final long serialVersionUID = -2707712944901661771L;
	public static final String STYLE_PROSUMER_PORT = "spacingTop=2;fillColor=black;strokeColor=black;fontColor=white;movable=0;portConstraint=west";
	public static final String STYLE_PROVIDER_PORT = "spacingTop=2;fillColor=white;movable=0;portConstraint=east";
	public static final String VERTEX_STYLE_NORMAL = "verticalAlign=top;shadow=1;arcSize=5;rounded=1";
	public static final int[] VIEW_MODULE_FALLBACK_POS = { 0, 0 };

	private final PersistentConfigurationHelper config;
	private final Map<ConnectionSummary, mxCell> connectionEdges = new HashMap<ConnectionSummary, mxCell>();
	private final Map<String, ConnectionSummary> connections = new HashMap<String, ConnectionSummary>();
	private final SwingAdvancedControlInterface controller;
	private mxGraph graph = null;
	private mxGraphComponent graphComponent;
	private final ReentrantLock modificationLock = new ReentrantLock(true); // TODO: Everything locked?
	private final Map<String, ModuleSummary> modules = new HashMap<String, ModuleSummary>();
	private final Map<String, mxCell> moduleVertices = new HashMap<String, mxCell>();
	private Object parent = null;
	private final Map<PortSummary, mxCell> portVertices = new HashMap<PortSummary, mxCell>();

	/**
	 * Instantiates a new connection graph panel.
	 *
	 * @param controller the advaned CI controller
	 * @param config the config helper to store positions to
	 */
	public ConnectionGraphPanel(final SwingAdvancedControlInterface controller, final PersistentConfigurationHelper config) {
		this.controller = controller;
		this.config = config;
		initialize();
	}

	/**
	 * Adds or updates a connection.
	 *
	 * @param connection the connection
	 */
	public void addOrUpdateConnection(final ConnectionSummary connection) {
		addOrUpdateConnection(connection, null);
	}

	/**
	 * Adds or updates a connection.
	 *
	 * @param connection the connection
	 * @param type the type of the connection event causing this call
	 */
	public void addOrUpdateConnection(final ConnectionSummary connection, final ConnectionEventType type) {
		if (connection == null) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				ConnectionGraphPanel.this.modificationLock.lock();
				final mxCell prosumerPort = ConnectionGraphPanel.this.portVertices.get(connection.getProsumerPortSummary());
				final mxCell providerPort = ConnectionGraphPanel.this.portVertices.get(connection.getProviderPortSummary());
				if ((prosumerPort != null) && (providerPort != null)) {
					mxCell edge = ConnectionGraphPanel.this.connectionEdges.get(connection);
					if (edge == null) {
						ConnectionGraphPanel.this.graph.getModel().beginUpdate();
						try {
							edge = (mxCell) ConnectionGraphPanel.this.graph.insertEdge(ConnectionGraphPanel.this.parent, null, getElement(connection), prosumerPort, providerPort, ConnectionGraphPanel.EDGE_STYLE_NORMAL);
							ConnectionGraphPanel.this.connectionEdges.put(connection, edge);
							ConnectionGraphPanel.this.connections.put(String.valueOf(connection.hashCode()), connection);
						} catch (final Exception e) {
							ConnectionGraphPanel.this.controller.showMessageDialog(ConnectionGraphPanel.CONNECTION_GRAPH___ERROR_MESSAGE_TITLE, e.getLocalizedMessage());
						} finally {
							ConnectionGraphPanel.this.graph.getModel().endUpdate();
						}
					} else {
						ConnectionGraphPanel.this.connections.put(String.valueOf(connection.hashCode()), connection);
						ConnectionGraphPanel.this.connectionEdges.put(connection, edge);
					}
					if (edge != null) {
						if (connection.isActive()) {
							if (type != null) {
								switch (type) {
								case BUSY:
									ConnectionGraphPanel.this.graph.getModel().setStyle(edge, ConnectionGraphPanel.EDGE_STYLE_BOLD);
									break;
								case ERROR:
									ConnectionGraphPanel.this.graph.getModel().setStyle(edge, ConnectionGraphPanel.EDGE_STYLE_DASHEDRED);
									break;
								default:
									ConnectionGraphPanel.this.graph.getModel().setStyle(edge, ConnectionGraphPanel.EDGE_STYLE_NORMAL);
									break;
								}
							} else {
								ConnectionGraphPanel.this.graph.getModel().setStyle(edge, ConnectionGraphPanel.EDGE_STYLE_NORMAL);
							}
						} else {
							ConnectionGraphPanel.this.graph.getModel().setStyle(edge, ConnectionGraphPanel.EDGE_STYLE_DASHEDRED);
						}
					}
					addOrUpdatePort(connection.getProsumerPortSummary());
					addOrUpdatePort(connection.getProviderPortSummary());
				} else if (ConnectionGraphPanel.this.connectionEdges.containsKey(connection)) {
					removeConnection(connection);
				}
				ConnectionGraphPanel.this.modificationLock.unlock();
			}
		});
	}

	/**
	 * Adds or updates a module.
	 *
	 * @param moduleSummary the module summary
	 */
	public void addOrUpdateModule(final ModuleSummary moduleSummary) {
		if (moduleSummary == null) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				ConnectionGraphPanel.this.modificationLock.lock();
				if (!ConnectionGraphPanel.this.moduleVertices.containsKey(moduleSummary.getModuleId())) {
					ConnectionGraphPanel.this.graph.getModel().beginUpdate();
					try {
						final int[] pos = new int[2];
						pos[0] = ConnectionGraphPanel.this.config.getInteger(ConnectionGraphPanel.CONFIG___VIEW_POS_KEY_PREFIX_X + moduleSummary.getModuleId(), 0);
						pos[1] = ConnectionGraphPanel.this.config.getInteger(ConnectionGraphPanel.CONFIG___VIEW_POS_KEY_PREFIX_Y + moduleSummary.getModuleId(), 0);
						final mxCell cell = (mxCell) ConnectionGraphPanel.this.graph.insertVertex(ConnectionGraphPanel.this.parent, null, getElement(moduleSummary), pos[0], pos[1], ConnectionGraphPanel.MODULE_CELL_WIDTH, ConnectionGraphPanel.MODULE_CELL_MINHEIGHT, ConnectionGraphPanel.VERTEX_STYLE_NORMAL);
						cell.setConnectable(false);
						// layoutGraph(cell); // currently broken as layout causes issues when moving elements later on
						ConnectionGraphPanel.this.moduleVertices.put(moduleSummary.getModuleId(), cell);
						ConnectionGraphPanel.this.modules.put(moduleSummary.getModuleId(), moduleSummary);
					} catch (final Exception e) {
						ConnectionGraphPanel.this.controller.showMessageDialog(ConnectionGraphPanel.CONNECTION_GRAPH___ERROR_MESSAGE_TITLE, e.getLocalizedMessage());
					} finally {
						ConnectionGraphPanel.this.graph.getModel().endUpdate();
						ConnectionGraphPanel.this.modificationLock.unlock();
					}
				} else {
					ConnectionGraphPanel.this.graph.getModel().beginUpdate();
					try {
						final mxCell cell = ConnectionGraphPanel.this.moduleVertices.get(moduleSummary.getModuleId());
						if (cell != null) {
							cell.setValue(getElement(moduleSummary));
						}
						ConnectionGraphPanel.this.modules.put(moduleSummary.getModuleId(), moduleSummary);
					} catch (final Exception e) {
						ConnectionGraphPanel.this.controller.showMessageDialog(ConnectionGraphPanel.CONNECTION_GRAPH___ERROR_MESSAGE_TITLE, e.getLocalizedMessage());
					} finally {
						ConnectionGraphPanel.this.graph.getModel().endUpdate();
						ConnectionGraphPanel.this.graph.refresh();
						ConnectionGraphPanel.this.modificationLock.unlock();
					}
				}
			}
		});
	}

	/**
	 * Adds or updates a port.
	 *
	 * @param parent the parent of the port (module cell)
	 * @param portSummary the port summary
	 * @return true, if successful
	 */
	private boolean addOrUpdatePort(final mxCell parent, final PortSummary portSummary) {
		if ((parent == null) || (portSummary == null)) {
			return false;
		}
		boolean result = false;
		this.modificationLock.lock();
		if (!this.portVertices.containsKey(portSummary)) {
			this.graph.getModel().beginUpdate();
			try {
				final Element portElem = getElement(portSummary);
				mxCell port;
				if (portSummary.getType() == PortType.PROSUMER) {
					final mxGeometry geo1 = new mxGeometry();
					port = new mxCell(portElem, geo1, ConnectionGraphPanel.STYLE_PROSUMER_PORT);
				} else if (portSummary.getType() == PortType.PROVIDER) {
					final mxGeometry geo1 = new mxGeometry();
					port = new mxCell(portElem, geo1, ConnectionGraphPanel.STYLE_PROVIDER_PORT);
				} else {
					this.graph.getModel().endUpdate();
					this.modificationLock.unlock();
					return false;
				}
				port.setVertex(true);
				port.setConnectable(true);
				this.graph.addCell(port, parent);
				this.portVertices.put(portSummary, port);
				resizeModule(parent);
				result = true;
			} finally {
				this.graph.getModel().endUpdate();
				this.graph.refresh();
				this.modificationLock.unlock();
			}
		} else {
			this.graph.getModel().beginUpdate();
			try {
				final mxCell cell = this.portVertices.get(portSummary);
				if (cell != null) {
					cell.setValue(getElement(portSummary));
					result = true;
				}
				this.portVertices.put(portSummary, cell);
			} catch (final Exception e) {
				this.controller.showMessageDialog(ConnectionGraphPanel.CONNECTION_GRAPH___ERROR_MESSAGE_TITLE, e.getLocalizedMessage());
			} finally {
				this.graph.getModel().endUpdate();
				this.graph.refresh();
				this.modificationLock.unlock();
			}
		}
		return result;
	}

	/**
	 * Adds or updates a port.
	 *
	 * @param portSummary the port summary
	 */
	public void addOrUpdatePort(final PortSummary portSummary) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				ConnectionGraphPanel.this.modificationLock.lock();
				final mxCell parent = ConnectionGraphPanel.this.moduleVertices.get(portSummary.getModuleId());
				if (parent != null) {
					addOrUpdatePort(parent, portSummary);
				}
				ConnectionGraphPanel.this.modificationLock.unlock();
			}
		});
	}

	/**
	 * Gets the connection summary of an element (if any).
	 *
	 * @param element the element
	 * @return the connection summary
	 */
	private ConnectionSummary getConnectionSummary(final Element element) {
		if (element == null) {
			return null;
		}

		if (element.getTagName().equals(ConnectionGraphPanel.ELEMENT_CLASS_CONNECTION)) {
			try {
				final String hash = element.getAttribute(ConnectionGraphPanel.CONNECTION_HASH);
				return this.connections.get(hash);
			} catch (final NumberFormatException e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}

	/**
	 * Gets the element of a connection summary.
	 *
	 * @param connection the connection
	 * @return the element
	 */
	public Element getElement(final ConnectionSummary connection) {
		final Document doc = mxDomUtils.createDocument();
		final Element connElem = doc.createElement(ConnectionGraphPanel.ELEMENT_CLASS_CONNECTION);
		// currently no labels on edges
		connElem.setAttribute(ConnectionGraphPanel.ELEMENT_LABEL, String.valueOf(connection.getPriority()));
		connElem.setAttribute(ConnectionGraphPanel.CONNECTION_HASH, String.valueOf(connection.hashCode()));
		connElem.setAttribute(ConnectionGraphPanel.CONNECTION_PRIO, String.valueOf(connection.getPriority()));
		return connElem;
	}

	/**
	 * Gets the element of a module summary.
	 *
	 * @param moduleSummary the module summary
	 * @return the element
	 */
	public Element getElement(final ModuleSummary moduleSummary) {
		final Document doc = mxDomUtils.createDocument();
		final Element modElem = doc.createElement(ConnectionGraphPanel.ELEMENT_CLASS_MODULE);
		modElem.setAttribute(ConnectionGraphPanel.ELEMENT_LABEL, moduleSummary.getModuleName());
		modElem.setAttribute(ConnectionGraphPanel.PORT_MODULE_ID, moduleSummary.getModuleId());
		return modElem;
	}

	/**
	 * Gets the element of an mxCell (if it has one).
	 *
	 * @param obj the mxCell
	 * @return the element
	 */
	private Element getElement(final Object obj) {
		if ((obj == null) || !(obj instanceof mxCell)) {
			return null;
		}
		final mxCell cell = (mxCell) obj;
		final Object value = cell.getValue();
		if (value instanceof Element) {
			return (Element) value;
		}
		return null;
	}

	/**
	 * Gets the element of a port summary
	 *
	 * @param portSummary the port summary
	 * @return the element
	 */
	public Element getElement(final PortSummary portSummary) {
		final Document doc = mxDomUtils.createDocument();
		final Element portElem = doc.createElement(ConnectionGraphPanel.ELEMENT_CLASS_PORT);
		portElem.setAttribute(ConnectionGraphPanel.ELEMENT_LABEL, portSummary.getPortId());
		portElem.setAttribute(ConnectionGraphPanel.PORT_MODULE_ID, portSummary.getModuleId());
		portElem.setAttribute(ConnectionGraphPanel.PORT_PORT_ID, portSummary.getPortId());
		portElem.setAttribute(ConnectionGraphPanel.PORT_TYPE, portSummary.getType().name());
		portElem.setAttribute(ConnectionGraphPanel.PORT_PORT_MAXCON, String.valueOf(portSummary.getMaxConnections()));
		portElem.setAttribute(ConnectionGraphPanel.PORT_PORT_CURCON, String.valueOf(portSummary.getCurrentConnections()));
		return portElem;
	}

	/**
	 * Gets the module ID of an element.
	 *
	 * @param element the element
	 * @return the module id
	 */
	private String getModuleId(final Element element) {
		if (element == null) {
			return null;
		}
		if (element.getTagName().equals(ConnectionGraphPanel.ELEMENT_CLASS_MODULE)) {
			final String moduleId = element.getAttribute(ConnectionGraphPanel.MODULE_MODULE_ID);
			if (moduleId != null) {
				return moduleId;
			}
		}
		return null;
	}

	/**
	 * Gets the module summary of an element.
	 *
	 * @param element the element
	 * @return the module summary
	 */
	private ModuleSummary getModuleSummary(final Element element) {
		if (element == null) {
			return null;
		}
		if (element.getTagName().equals(ConnectionGraphPanel.ELEMENT_CLASS_MODULE)) {
			final String moduleId = element.getAttribute(ConnectionGraphPanel.MODULE_MODULE_ID);
			if (moduleId != null) {
				return this.modules.get(moduleId);
			}
		}
		return null;
	}

	/**
	 * Gets the port summary of an element.
	 *
	 * @param element the element
	 * @return the port summary
	 */
	private PortSummary getPortSummary(final Element element) {
		if (element == null) {
			return null;
		}

		if (element.getTagName().equals(ConnectionGraphPanel.ELEMENT_CLASS_PORT)) {
			try {
				final String portId = element.getAttribute(ConnectionGraphPanel.PORT_PORT_ID);
				final String moduleId = element.getAttribute(ConnectionGraphPanel.PORT_MODULE_ID);
				final PortType type = PortType.valueOf(element.getAttribute(ConnectionGraphPanel.PORT_TYPE));
				final int curCon = Integer.parseInt(element.getAttribute(ConnectionGraphPanel.PORT_PORT_CURCON));
				final int maxCon = Integer.parseInt(element.getAttribute(ConnectionGraphPanel.PORT_PORT_MAXCON));
				return new PortSummary(moduleId, type, portId, maxCon, curCon);
			} catch (final NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Gets map of sorted module ports.
	 *
	 * @param parent the parent cell
	 * @return the sorted ports
	 */
	private TreeMap<String, mxCell>[] getSortedPorts(final mxCell parent) {
		@SuppressWarnings("unchecked")
		final TreeMap<String, mxCell>[] result = new TreeMap[2];
		result[0] = new TreeMap<String, mxCell>(String.CASE_INSENSITIVE_ORDER);
		result[1] = new TreeMap<String, mxCell>(String.CASE_INSENSITIVE_ORDER);
		for (final Object obj : this.graph.getChildVertices(parent)) {
			if (obj instanceof mxCell) {
				final mxCell cell = (mxCell) obj;
				final Object value = cell.getValue();
				if (value instanceof Element) {
					final Element element = (Element) value;
					if (element.getTagName().equals(ConnectionGraphPanel.ELEMENT_CLASS_PORT)) {
						final String id = element.getAttribute(ConnectionGraphPanel.PORT_PORT_ID);
						final String sType = element.getAttribute(ConnectionGraphPanel.PORT_TYPE);
						if ((id != null) && (sType != null)) {
							final PortType type = PortType.valueOf(sType);
							if (type == PortType.PROSUMER) {
								result[0].put(id, cell);
							} else if (type == PortType.PROVIDER) {
								result[1].put(id, cell);
							}
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Initializes the panel.
	 */
	private void initialize() {
		setLayout(new BorderLayout());
		this.graph = new mxGraph() {

			@Override
			public String convertValueToString(final Object cell) {
				if (cell instanceof mxCell) {
					final Object value = ((mxCell) cell).getValue();
					if (value instanceof Element) {
						final Element element = (Element) value;
						final String type = element.getTagName();
						if (type != null) {
							final String id = element.getAttribute(ConnectionGraphPanel.ELEMENT_LABEL);
							if (id != null) {
								return id;
							}
						}
					} else if (value instanceof String) {
						return (String) value;
					}
				}
				return "-?-";
			}

			@Override
			public boolean isCellFoldable(final Object cell, final boolean collapse) {
				return false;
			}

			@Override
			public boolean isValidConnection(final Object source, final Object target) {
				final Element srcElem = getElement(source);
				final Element destElem = getElement(target);
				if ((srcElem != null) && (destElem != null)) {
					final PortSummary srcPort = getPortSummary(srcElem);
					final PortSummary destPort = getPortSummary(destElem);
					if ((srcPort != null) && (destPort != null)) {
						return ConnectionGraphPanel.this.controller.mayConnect(srcPort, destPort);
					}
				}
				return false;
			}
		};

		this.graph.setAllowDanglingEdges(false);
		this.graph.setEdgeLabelsMovable(false);
		this.graph.setAllowLoops(true);
		this.graph.setMultigraph(true);
		this.graph.setCellsDisconnectable(false);
		this.graph.setLabelsClipped(false);
		this.graph.setCellsEditable(false);
		this.graph.setDropEnabled(false);
		this.graph.setCellsResizable(false);
		this.graph.setPortsEnabled(true);
		this.graph.getSelectionModel().setSingleSelection(true);
		mxConstants.RECTANGLE_ROUNDING_FACTOR = 0.05;

		// default edge style
		// TODO: Should be changed to separate static final String constants.
		final Map<String, Object> style = new HashMap<String, Object>();
		style.put(mxConstants.STYLE_ROUNDED, true);
		style.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_ENTITY_RELATION);
		style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CONNECTOR);
		style.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
		style.put(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_CLASSIC);
		style.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE);
		style.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER);
		style.put(mxConstants.STYLE_STROKECOLOR, "#6482B9");
		style.put(mxConstants.STYLE_FONTCOLOR, "#446299");
		style.put(mxConstants.STYLE_LABEL_BACKGROUNDCOLOR, "#ffffff");
		final mxStylesheet foo = new mxStylesheet();
		foo.setDefaultEdgeStyle(style);
		this.graph.setStylesheet(foo);
		this.parent = this.graph.getDefaultParent();
		this.graphComponent = new mxGraphComponent(this.graph);
		this.graphComponent.getConnectionHandler().addListener(mxEvent.CONNECT, new mxIEventListener() {

			@Override
			public void invoke(final Object sender, final mxEventObject evt) {
				final mxCell edge = (mxCell) evt.getProperty("cell");
				final Element srcElem = getElement(edge.getSource());
				final Element destElem = getElement(edge.getTarget());
				if ((srcElem != null) && (destElem != null)) {
					final PortSummary srcPort = getPortSummary(srcElem);
					final PortSummary destPort = getPortSummary(destElem);
					if ((srcPort != null) && (destPort != null)) {
						ConnectionGraphPanel.this.controller.connect(srcPort, destPort);
					}
				}
				edge.removeFromParent();
			}
		});

		this.graph.getSelectionModel().addListener(mxEvent.CHANGE, new mxIEventListener() {

			@Override
			public void invoke(final Object sender, final mxEventObject evt) {
				if (sender instanceof mxGraphSelectionModel) {
					Object cell;
					if ((cell = ((mxGraphSelectionModel) sender).getCell()) != null) {
						processElementSelected(getElement(cell));
					} else {
						ConnectionGraphPanel.this.controller.selectedNothingInGraph();
					}
				}
			}
		});

		this.graph.addListener(mxEvent.CELLS_MOVED, new mxIEventListener() {

			@Override
			public void invoke(final Object sender, final mxEventObject evt) {
				final Object[] cells = (Object[]) evt.getProperty("cells");
				if ((cells != null) && (cells.length > 0) && (cells[0] != null)) {
					final mxCell cell = (mxCell) cells[0];
					final String moduleId = getModuleId(getElement(cell));
					if ((moduleId != null) && !moduleId.isEmpty()) {
						ConnectionGraphPanel.this.config.updateInteger(ConnectionGraphPanel.CONFIG___VIEW_POS_KEY_PREFIX_X + moduleId, (int) cell.getGeometry().getX());
						ConnectionGraphPanel.this.config.updateInteger(ConnectionGraphPanel.CONFIG___VIEW_POS_KEY_PREFIX_Y + moduleId, (int) cell.getGeometry().getY());
					}
				}
			}
		});
		setEdgeLabelLayout();
		add(this.graphComponent);
	}

	/**
	 * Processes selected elements.
	 *
	 * @param elem the selected element
	 */
	public void processElementSelected(final Element elem) {
		final PortSummary portSummary = getPortSummary(elem);
		if (portSummary != null) {
			this.controller.selectedPortInGraph(portSummary);
			return;
		}
		final ModuleSummary moduleSummary = getModuleSummary(elem);
		if (moduleSummary != null) {
			this.controller.selectedModuleInGraph(moduleSummary);
			return;
		}
		final ConnectionSummary connectionSummary = getConnectionSummary(elem);
		if (connectionSummary != null) {
			this.controller.selectedConnectionInGraph(connectionSummary);
			return;
		}
	}

	/**
	 * Removes a connection.
	 *
	 * @param connection the summary of the connection to remove
	 */
	public void removeConnection(final ConnectionSummary connection) {
		if (connection == null) {
			return;
		}
		final Runnable runnable = new Runnable() {

			@Override
			public void run() {
				ConnectionGraphPanel.this.modificationLock.lock();
				if (ConnectionGraphPanel.this.connectionEdges.containsKey(connection)) {
					ConnectionGraphPanel.this.graph.getModel().beginUpdate();
					try {
						final mxCell cell = ConnectionGraphPanel.this.connectionEdges.remove(connection);
						if (cell != null) {
							ConnectionGraphPanel.this.graph.removeCells(new Object[] { cell });
							ConnectionGraphPanel.this.graph.refresh();
						}
						addOrUpdatePort(connection.getProsumerPortSummary());
						addOrUpdatePort(connection.getProviderPortSummary());
					} catch (final Exception e) {
						ConnectionGraphPanel.this.controller.showMessageDialog(ConnectionGraphPanel.CONNECTION_GRAPH___ERROR_MESSAGE_TITLE, e.getLocalizedMessage());
					} finally {
						ConnectionGraphPanel.this.graph.getModel().endUpdate();
					}
				}
				ConnectionGraphPanel.this.modificationLock.unlock();
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			runnable.run();
		} else {
			SwingUtilities.invokeLater(runnable);
		}
	}

	/**
	 * Removes a module.
	 *
	 * @param summary the summary of the module to remove
	 */
	public void removeModule(final ModuleSummary summary) {
		if (summary == null) {
			return;
		}
		final String moduleId = summary.getModuleId();
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				ConnectionGraphPanel.this.modificationLock.lock();
				if (ConnectionGraphPanel.this.moduleVertices.containsKey(moduleId)) {
					ConnectionGraphPanel.this.graph.getModel().beginUpdate();
					try {
						final mxCell cell = ConnectionGraphPanel.this.moduleVertices.remove(moduleId);
						if (cell != null) {
							cell.removeFromParent();
							ConnectionGraphPanel.this.graph.refresh();
						}
					} catch (final Exception e) {
						ConnectionGraphPanel.this.controller.showMessageDialog(ConnectionGraphPanel.CONNECTION_GRAPH___ERROR_MESSAGE_TITLE, e.getLocalizedMessage());
					} finally {
						ConnectionGraphPanel.this.graph.getModel().endUpdate();
					}
				}
				ConnectionGraphPanel.this.modules.remove(moduleId);
				ConnectionGraphPanel.this.modificationLock.unlock();
			}
		});
	}

	/**
	 * Removes a port.
	 *
	 * @param portSummary the summary of the port to remove
	 */
	public void removePort(final PortSummary portSummary) {
		if (portSummary == null) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				ConnectionGraphPanel.this.modificationLock.lock();
				if (ConnectionGraphPanel.this.portVertices.containsKey(portSummary)) {
					ConnectionGraphPanel.this.graph.getModel().beginUpdate();
					try {
						final mxCell cell = ConnectionGraphPanel.this.portVertices.remove(portSummary);
						if (cell != null) {
							final mxCell parent = (mxCell) cell.getParent();
							final Object[] edges = ConnectionGraphPanel.this.graph.getEdges(parent);
							ConnectionGraphPanel.this.graph.removeCells(edges);
							cell.removeFromParent();
							if (parent != null) {
								resizeModule(parent);
							}
						}
					} catch (final Exception e) {
						ConnectionGraphPanel.this.controller.showMessageDialog(ConnectionGraphPanel.CONNECTION_GRAPH___ERROR_MESSAGE_TITLE, e.getLocalizedMessage());
					} finally {
						ConnectionGraphPanel.this.graph.getModel().endUpdate();
					}
				}
				ConnectionGraphPanel.this.modificationLock.unlock();
			}
		});
	}

	/**
	 * Resizes a module according to number of ports.
	 *
	 * @param parent the parent cell
	 */
	private void resizeModule(final mxCell parent) {
		if (parent == null) {
			return;
		}
		this.modificationLock.lock();
		this.graph.getModel().beginUpdate();
		try {
			final mxIGraphModel model = this.graph.getModel();
			final mxGeometry parentGeo = (mxGeometry) model.getGeometry(parent).clone();
			// resort, resize and redraw
			final TreeMap<String, mxCell>[] ports = getSortedPorts(parent);
			final int max = Math.max(ports[0].size(), ports[1].size());
			final double parentHeight = (max * ConnectionGraphPanel.PORT_CELL_HEIGHT) + ((max - 1) * ConnectionGraphPanel.MODULE_CELL_PORT_SPACING);
			parentGeo.setHeight(parentHeight + ConnectionGraphPanel.MODULE_CELL_MARGIN_TOP + ConnectionGraphPanel.MODULE_CELL_MARGIN_BOTTOM);
			final double parentWidth = parentGeo.getWidth();
			model.setGeometry(parent, parentGeo);
			int i;
			final double steps = ConnectionGraphPanel.PORT_CELL_HEIGHT + ConnectionGraphPanel.MODULE_CELL_PORT_SPACING;
			if (!ports[0].isEmpty()) {
				i = 0;
				for (final mxCell prosumerPort : ports[0].values()) {
					final mxGeometry pGeo = new mxGeometry(-ConnectionGraphPanel.PORT_CELL_XOFFSET, (i * steps) + ConnectionGraphPanel.MODULE_CELL_MARGIN_TOP, ConnectionGraphPanel.PORT_CELL_WIDTH, ConnectionGraphPanel.PORT_CELL_HEIGHT);
					model.setGeometry(prosumerPort, pGeo);
					i++;
				}
			}
			if (!ports[1].isEmpty()) {
				i = 0;
				for (final mxCell providerPort : ports[1].values()) {
					final mxGeometry pGeo = new mxGeometry((parentWidth - ConnectionGraphPanel.PORT_CELL_WIDTH) + ConnectionGraphPanel.PORT_CELL_XOFFSET, (i * steps) + ConnectionGraphPanel.MODULE_CELL_MARGIN_TOP, ConnectionGraphPanel.PORT_CELL_WIDTH, ConnectionGraphPanel.PORT_CELL_HEIGHT);
					model.setGeometry(providerPort, pGeo);
					i++;
				}
			}
			this.graph.refresh();
		} finally {
			this.graph.getModel().endUpdate();
			this.modificationLock.unlock();
		}
	}

	/**
	 * Sets the connection to bold style (while busy).
	 *
	 * @param connection the summary of the connection to set to bold
	 */
	public void setConnectionBold(final ConnectionSummary connection) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				final mxCell edge = ConnectionGraphPanel.this.connectionEdges.get(connection);
				if (edge != null) {
					ConnectionGraphPanel.this.graph.getModel().setStyle(edge, ConnectionGraphPanel.EDGE_STYLE_BOLD);
				}
			}
		});
	}

	/**
	 * Sets the connection to default style (for example while idle).
	 *
	 * @param connection the summary of the connection to set to default
	 */
	public void setConnectionDefault(final ConnectionSummary connection) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				final mxCell edge = ConnectionGraphPanel.this.connectionEdges.get(connection);
				if (edge != null) {
					ConnectionGraphPanel.this.graph.getModel().setStyle(edge, ConnectionGraphPanel.EDGE_STYLE_NORMAL);
				}
			}
		});
	}

	/**
	 * Sets the connection to error style.
	 *
	 * @param connection the summary of the connection to set to error
	 */
	public void setConnectionError(final ConnectionSummary connection) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				final mxCell edge = ConnectionGraphPanel.this.connectionEdges.get(connection);
				if (edge != null) {
					ConnectionGraphPanel.this.graph.getModel().setStyle(edge, ConnectionGraphPanel.EDGE_STYLE_DASHEDRED);
				}
			}
		});
	}

	/**
	 * Sets the edge label layout.
	 */
	private void setEdgeLabelLayout() {
		this.graph.getModel().beginUpdate();
		try {
			final mxEdgeLabelLayout layout = new mxEdgeLabelLayout(this.graph);
			layout.execute(this.parent);
		} finally {
			this.graph.getModel().endUpdate();
		}
	}
}
