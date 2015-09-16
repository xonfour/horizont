package controlinterface.swinguiadvanced.control;

import helper.TextFormatHelper;

import java.awt.Dialog.ModalExclusionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import controlinterface.iface.GeneralEventListener;
import controlinterface.swinguiadvanced.view.dialog.GenericDialog;
import controlinterface.swinguiadvanced.view.panel.ConnectionManagementPanel;
import controlinterface.swinguiadvanced.view.panel.MessagePanel;
import framework.constants.ControlInterfaceRight;
import framework.control.ControlInterfaceConnector;
import framework.control.LocalizationConnector;
import framework.control.LogConnector;
import framework.exception.AuthorizationException;
import framework.exception.ControlInterfaceException;
import framework.model.event.ConnectionUpdateEvent;
import framework.model.event.GeneralEvent;
import framework.model.event.type.ConnectionEventType;
import framework.model.event.type.GeneralEventType;
import framework.model.summary.ConnectionSummary;
import framework.model.summary.ModuleSummary;
import framework.model.summary.PortSummary;

/**
 * Provides methods for managing connections between modules.
 *
 * @author Stefan Werner
 */
public class ConnectionManagementController implements GeneralEventListener {

	private final ControlInterfaceConnector ciConnector;
	private final String closeString;
	private final Map<ConnectionSummary, ConnectionEventType> connectionEvents = new HashMap<ConnectionSummary, ConnectionEventType>();
	private String[][] data = null;
	private GenericDialog dialog = null;
	private final String errorString;
	private final LocalizationConnector localizationConnector;
	private final LogConnector logConnector;
	private final Map<String, String> moduleIdsAndNames = new ConcurrentHashMap<String, String>();
	private final String noString;
	private final String okString;
	private ConnectionManagementPanel panel = null;
	private List<ConnectionSummary> summaries;
	private final String yesString;

	/**
	 * Instantiates a new connection management controller.
	 *
	 * @param ciConnector the CI connector
	 * @param logConnector the log connector
	 * @param localizationConnector the localization connector
	 */
	public ConnectionManagementController(final ControlInterfaceConnector ciConnector, final LogConnector logConnector, final LocalizationConnector localizationConnector) {
		this.ciConnector = ciConnector;
		this.logConnector = logConnector;
		this.localizationConnector = localizationConnector;
		this.yesString = localizationConnector.getLocalizedString("Yes");
		this.noString = localizationConnector.getLocalizedString("No");
		this.okString = localizationConnector.getLocalizedString("OK");
		this.errorString = localizationConnector.getLocalizedString("Error");
		this.closeString = localizationConnector.getLocalizedString("Close");
	}

	/**
	 * Gets the formated name and id from port summary.
	 *
	 * @param portSummary the port summary
	 * @return the formated name and id string
	 */
	private String getNameAndIdString(final PortSummary portSummary) {
		final String moduleName = this.moduleIdsAndNames.get(portSummary.getModuleId());
		if (moduleName != null) {
			return moduleName + " (" + portSummary.getModuleId() + ")";
		} else {
			return portSummary.getModuleId();
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see controlinterface.iface.GeneralEventListener#onGeneralEvent(framework.model.event.GeneralEvent) */
	@Override
	public void onGeneralEvent(final GeneralEvent event) {
		if (event instanceof ConnectionUpdateEvent) {
			final ConnectionUpdateEvent cuEvent = (ConnectionUpdateEvent) event;
			if (this.summaries != null) {
				if (((ConnectionUpdateEvent) event).type == ConnectionEventType.REMOVED) {
					this.summaries.remove(cuEvent.connectionSummary);
				} else {
					this.summaries.remove(cuEvent.connectionSummary);
					this.summaries.add(cuEvent.connectionSummary);
				}
				this.connectionEvents.put(cuEvent.connectionSummary, cuEvent.type);
				updateData(true);
			}
		}
	}

	/**
	 * Removes a connection.
	 *
	 * @param index the index
	 */
	public void removeConnection(final int index) {
		if ((this.data != null) && (this.data.length > index)) {
			final ConnectionSummary summary = this.summaries.get(index);
			if (summary != null) {
				try {
					// TODO: Show confirmation dialog.
					if (!this.ciConnector.removeConnection(summary)) {
						showErrorDialog(this.localizationConnector.getLocalizedString("Unable to remove connection."));
					}
				} catch (AuthorizationException | ControlInterfaceException e) {
					this.logConnector.log(e);
					showErrorDialog(this.localizationConnector.getLocalizedString("Error while removing connection:"), e);
				}
			}
		}
	}

	/**
	 * Shows connection management dialog.
	 */
	void showConnectionManagementDialog() {
		if (this.panel == null) {
			int rights;
			try {
				rights = this.ciConnector.getOwnRights();
			} catch (final ControlInterfaceException e1) {
				this.logConnector.log(e1);
				showErrorDialog("Error while retrieving own rights:", e1);
				return;
			}
			if ((rights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) == 0) {
				(new GenericDialog(this.dialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Insufficient rights for module management."), null, null))).showDialog();
				return;
			}
			this.panel = new ConnectionManagementPanel(this, this.localizationConnector);
			if ((rights & ControlInterfaceRight.RCV_CONN_UPDATE) == ControlInterfaceRight.RCV_CONN_UPDATE) {
				try {
					this.ciConnector.addGeneralEventListener(this, GeneralEventType.CONNECTION_UPDATE);
				} catch (AuthorizationException | ControlInterfaceException e) {
					this.logConnector.log(e);
				}
			}
			updateData(false);
			this.dialog = new GenericDialog(this.dialog, this.localizationConnector.getLocalizedString("Connection Management"), this.closeString, null, this.panel);
			this.dialog.setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);
			this.dialog.showDialog();
			if ((rights & ControlInterfaceRight.RCV_CONN_UPDATE) == ControlInterfaceRight.RCV_CONN_UPDATE) {
				try {
					this.ciConnector.removeGeneralEventListener(this);
				} catch (final ControlInterfaceException e) {
					this.logConnector.log(e);
				}
			}
			this.panel = null;
		}
	}

	/**
	 * Shows an error dialog.
	 *
	 * @param msg the message to display
	 */
	public void showErrorDialog(final String msg) {
		GenericDialog.showGenericMessageDialog(this.errorString, msg, this.okString);
	}

	/**
	 * Shows an error dialog.
	 *
	 * @param msg the message to display
	 * @param e the Exception to display
	 */
	public void showErrorDialog(final String msg, final Exception e) {
		GenericDialog.showGenericMessageDialog(this.errorString, msg + "\n" + e.getLocalizedMessage(), this.okString);
	}

	/**
	 * Shows a message dialog.
	 *
	 * @param title the title to display
	 * @param message the message to display
	 */
	public void showMessageDialog(final String title, final String message) {
		GenericDialog.showGenericMessageDialog(title, message, this.okString);
	}

	/**
	 * Converts a connection summary to a string array.
	 *
	 * @param summary the summary
	 * @return the string array
	 */
	private String[] toStringArray(final ConnectionSummary summary) {
		if (summary == null) {
			return null;
		}
		final String[] entry = new String[9];
		entry[0] = summary.getProviderPortSummary().getPortId();
		entry[1] = getNameAndIdString(summary.getProviderPortSummary());
		entry[2] = summary.getProsumerPortSummary().getPortId();
		entry[3] = getNameAndIdString(summary.getProsumerPortSummary());
		entry[4] = summary.isActive() ? this.yesString : this.noString;
		entry[5] = String.valueOf(summary.getPriority());
		entry[6] = summary.getLatestRefreshDate() == 0 ? "-" : TextFormatHelper.convertSizeValueToHumanReadableFormat(summary.getDataTransfered());
		entry[7] = this.localizationConnector.getFormatedDateLocalized(summary.getLatestRefreshDate());
		final ConnectionEventType type = this.connectionEvents.get(summary);
		if (type != null) {
			entry[8] = this.localizationConnector.getLocalizedString(type.name());
		} else {
			entry[8] = "-";
		}
		return entry;
	}

	/**
	 * Updates visible data.
	 *
	 * @param isEvent true if called by an event
	 */
	public void updateData(final boolean isEvent) {
		if (this.panel == null) {
			return;
		}
		updateModuleNames();
		if (!isEvent) {
			try {
				final Set<ConnectionSummary> tmpSummaries = this.ciConnector.getConnections();
				if (tmpSummaries == null) {
					showErrorDialog(this.localizationConnector.getLocalizedString("Unable to load connections."));
					return;
				}
				this.summaries = new ArrayList<ConnectionSummary>(this.ciConnector.getConnections());
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				showErrorDialog(this.localizationConnector.getLocalizedString("Error while loading connections:"), e);
				return;
			}
		}
		if (this.summaries != null) {
			this.data = new String[this.summaries.size()][];
			int i = 0;
			for (final ConnectionSummary summary : this.summaries) {
				this.data[i] = toStringArray(summary);
				i++;
			}
			this.panel.updateData(this.data);
		}
	}

	/**
	 * Updates module names.
	 */
	private void updateModuleNames() {
		try {
			final Set<ModuleSummary> moduleSummaries = this.ciConnector.getActiveModules();
			for (final ModuleSummary moduleSummary : moduleSummaries) {
				this.moduleIdsAndNames.put(moduleSummary.getModuleId(), moduleSummary.getModuleName());
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			return;
		}
	}
}
