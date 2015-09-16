package controlinterface.swinguiadvanced.control;

import java.awt.Dialog.ModalExclusionType;
import java.awt.Window;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import controlinterface.iface.GeneralEventListener;
import controlinterface.swinguiadvanced.view.dialog.ComponentManagementDialog;
import controlinterface.swinguiadvanced.view.dialog.GenericDialog;
import controlinterface.swinguiadvanced.view.other.TableSelectionListener;
import controlinterface.swinguiadvanced.view.panel.ComponentRightsPanel;
import controlinterface.swinguiadvanced.view.panel.MessagePanel;
import controlinterface.swinguiadvanced.view.panel.TableSelectionScrollPane;
import controlinterface.swinguiadvanced.view.panel.TextInputPanel;
import framework.constants.ControlInterfaceRight;
import framework.constants.ModuleRight;
import framework.control.ControlInterfaceConnector;
import framework.control.LocalizationConnector;
import framework.control.LogConnector;
import framework.exception.AuthorizationException;
import framework.exception.ControlInterfaceException;
import framework.model.event.GeneralEvent;
import framework.model.event.ModuleUpdateEvent;
import framework.model.event.type.GeneralEventType;
import framework.model.event.type.ModuleUpdateEventType;
import framework.model.summary.ControlInterfaceSummary;
import framework.model.summary.ModuleSummary;

/**
 * Provides methods for managing other components (control interfaces and modules).
 *
 * @author Stefan Werner
 */
public class ComponentManagementController {

	private final String cancelString;
	private final ControlInterfaceConnector ciConnector;
	private String[][] ciData = null;
	private Set<ControlInterfaceSummary> ciSummaries = null;
	private ComponentManagementDialog componentManagementDialog = null;
	private final String errorString;
	private GeneralEventListener listener;
	private final LocalizationConnector localizationConnector;
	private final LogConnector logConnector;
	private String[][] moduleData = null;
	private Set<ModuleSummary> moduleSummaries = null;
	private final String noString;
	private final String okString;
	private String ownCiId;
	private final String yesString;

	/**
	 * Instantiates a new component management controller.
	 *
	 * @param ciConnector the CI connector
	 * @param logConnector the log connector
	 * @param localizationConnector the localization connector
	 */
	ComponentManagementController(final ControlInterfaceConnector ciConnector, final LogConnector logConnector, final LocalizationConnector localizationConnector) {
		this.ciConnector = ciConnector;
		try {
			this.ownCiId = ciConnector.getOwnId();
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.ownCiId = "";
		}
		this.logConnector = logConnector;
		this.localizationConnector = localizationConnector;
		this.yesString = localizationConnector.getLocalizedString("Yes");
		this.noString = localizationConnector.getLocalizedString("No");
		this.okString = localizationConnector.getLocalizedString("OK");
		this.cancelString = localizationConnector.getLocalizedString("Cancel");
		this.errorString = localizationConnector.getLocalizedString("Error");
	}

	/**
	 * Adds a new control interface.
	 */
	public void addNewCI() {
		Set<String> types;
		try {
			types = this.ciConnector.getAvailableControlInterfaceTypes();
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to load available control interface types:\n") + e.getLocalizedMessage(), null, null))).showDialog();
			return;
		}
		if ((types == null) || types.isEmpty()) {
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("No control interface types available."), null, null))).showDialog();
			return;
		}
		final String[] typesArray = types.toArray(new String[0]);
		final String[] typesViewArray = new String[types.size()];
		final Iterator<String> iter = types.iterator();
		for (int i = 0; i < typesViewArray.length; i++) {
			final String s = iter.next();
			if (s != null) {
				final String[] fqn = s.split("\\.");
				typesViewArray[i] = fqn[Math.max(fqn.length - 1, 0)] + "   (" + s + ")";
			} else {
				typesViewArray[i] = "NULL"; // should never happen
			}
		}
		final TableSelectionScrollPane compPane = new TableSelectionScrollPane(typesViewArray, this.localizationConnector.getLocalizedString("Type"));
		final ComponentRightsPanel rightsPanel = new ComponentRightsPanel(ControlInterfaceRight.availableControlInterfaceRights, ControlInterfaceRight.RIGHT___NON, this.localizationConnector);
		final GenericDialog dialog = new GenericDialog(this.componentManagementDialog, this.localizationConnector.getLocalizedString("Select control interface type:"), this.okString, this.cancelString, compPane, this.localizationConnector.getLocalizedString("Select rights:"), rightsPanel);
		dialog.disableFirstButton();
		compPane.addTableSelectionListener(new TableSelectionListener() {

			@Override
			public void onComponentSelected(final int index) {
				if (index < 0) {
					dialog.disableFirstButton();
				} else {
					dialog.enableFirstButton();
				}
			}
		});
		if (dialog.showDialog() == 0) {
			final int index = compPane.getSelectedIndex();
			if (index < 0) {
				return;
			}
			try {
				if (this.ciConnector.addControlInterface(typesArray[index], rightsPanel.getSelectedRights()) == null) {
					(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to add control interface."), null, null))).showDialog();
				} else {
					updateCIData();
				}
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to add control interface:\n") + e.getLocalizedMessage(), null, null))).showDialog();
			}
		}
	}

	/**
	 * Adds a new module.
	 *
	 * @param updateDialogData true, if dialog data needs to be updated
	 */
	public void addNewModule(final boolean updateDialogData) {
		Set<String> types;
		try {
			types = this.ciConnector.getAvailableModuleTypes();
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to load available module types:\n") + e.getLocalizedMessage(), null, null))).showDialog();
			return;
		}
		if ((types == null) || types.isEmpty()) {
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("No module types available."), null, null))).showDialog();
			return;
		}
		final String[] typesArray = types.toArray(new String[0]);
		final String[] typesViewArray = new String[types.size()];
		final Iterator<String> iter = types.iterator();
		for (int i = 0; i < typesViewArray.length; i++) {
			final String s = iter.next();
			if (s != null) {
				final String[] fqn = s.split("\\.");
				typesViewArray[i] = fqn[Math.max(fqn.length - 1, 0)] + "   (" + s + ")";
			} else {
				typesViewArray[i] = "NULL"; // should never happen
			}
		}
		final TableSelectionScrollPane compPane = new TableSelectionScrollPane(typesViewArray, this.localizationConnector.getLocalizedString("Type"));
		final ComponentRightsPanel rightsPanel = new ComponentRightsPanel(ModuleRight.availableModuleRights, ModuleRight.RIGHT___NON, this.localizationConnector);
		final GenericDialog dialog = new GenericDialog(this.componentManagementDialog, this.localizationConnector.getLocalizedString("Select module type:"), this.okString, this.cancelString, compPane, this.localizationConnector.getLocalizedString("Select rights:"), rightsPanel);
		dialog.disableFirstButton();
		compPane.addTableSelectionListener(new TableSelectionListener() {

			@Override
			public void onComponentSelected(final int index) {
				if (index < 0) {
					dialog.disableFirstButton();
				} else {
					dialog.enableFirstButton();
				}
			}
		});
		if (dialog.showDialog() == 0) {
			final int index = compPane.getSelectedIndex();
			if (index < 0) {
				return;
			}
			try {
				if (this.ciConnector.addModule(typesArray[index], rightsPanel.getSelectedRights()) == null) {
					(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to add module."), null, null))).showDialog();
				} else if ((this.listener == null) && updateDialogData) {
					updateModuleData(false);
				}
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to add module:\n") + e.getLocalizedMessage(), null, null))).showDialog();
			}
		}
	}

	/**
	 * Removes the selected control interface.
	 *
	 * @param index the index of the control interface to remove
	 */
	public void removeCI(final int index) {
		if ((index < 0) || (this.ciData == null) || (index >= this.ciData.length)) {
			return;
		}
		final String ciId = this.ciData[index][1];
		// it is OK to remove yourself
		// if (ciId.equals(ownCiId)) {
		// (new GenericDialog(componentManagementDialog, errorString, okString, null, new
		// MessagePanel(localizationConnector.getLocalizedString("A control interface cannot remove itself."), null, null))).showDialog();
		// return;
		// }
		final String ciName = this.ciData[index][0];
		final String title = this.localizationConnector.getLocalizedString("Control Interface Removal");
		final MessagePanel panel = new MessagePanel(this.localizationConnector.getLocalizedString("Remove control interface from running system:") + "\n" + ciName + "/" + ciId + "\n" + "Are you sure?", null, this.localizationConnector.getLocalizedString("Purge control interface data and settings in database"));
		final GenericDialog dialog = new GenericDialog(this.componentManagementDialog, title, this.yesString, this.noString, panel);
		if (dialog.showDialog() == 0) {
			try {
				if (!this.ciConnector.removeControlInterface(ciId, panel.isCheckBoxSelected())) {
					(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to remove control interface."), null, null))).showDialog();
				} else {
					updateCIData();
				}
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Error while removing control interface:\n") + e.getLocalizedMessage(), null, null))).showDialog();
			}
		}
	}

	/**
	 * Removes the selected module.
	 *
	 * @param index the index of the module to remove.
	 */
	public void removeModule(final int index) {
		if ((index < 0) || (this.moduleData == null) || (index >= this.moduleData.length)) {
			return;
		}
		final String moduleId = this.moduleData[index][1];
		final String moduleName = this.moduleData[index][0];
		removeModule(this.componentManagementDialog, moduleId, moduleName, true);
	}

	/**
	 * Removes the selected module.
	 *
	 * @param parent the parent window
	 * @param summary the module summary of the module to remove
	 */
	public void removeModule(final Window parent, final ModuleSummary summary) {
		final String moduleId = summary.getModuleId();
		final String moduleName = summary.getModuleName();
		removeModule(parent, moduleId, moduleName, false);
	}

	/**
	 * Removes the selected module.
	 *
	 * @param parent the parent window
	 * @param moduleId the id of the module to remove
	 * @param moduleName the name of the module to remove
	 * @param updateDialogData true, if dialog data needs to be updated
	 */
	private void removeModule(final Window parent, final String moduleId, final String moduleName, final boolean updateDialogData) {
		final String title = this.localizationConnector.getLocalizedString("Module Removal");
		final MessagePanel panel = new MessagePanel(this.localizationConnector.getLocalizedString("Remove module from running system:") + "\n" + moduleName + "/" + moduleId + "\n" + "Are you sure?", null, this.localizationConnector.getLocalizedString("Purge module data and settings in database"));
		final GenericDialog dialog = new GenericDialog(parent, title, this.yesString, this.noString, panel);
		if (dialog.showDialog() == 0) {
			try {
				if (!this.ciConnector.removeModule(moduleId, panel.isCheckBoxSelected())) {
					(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to remove module."), null, null))).showDialog();
				} else if ((this.listener == null) && updateDialogData) {
					updateModuleData(false);
				}
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Error while removing module:\n") + e.getLocalizedMessage(), null, null))).showDialog();
			}
		}
	}

	/**
	 * Renames control interface.
	 *
	 * @param index the index of the control interface to rename.
	 */
	public void renameCI(final int index) {
		if ((index < 0) || (this.ciData == null) || (index >= this.ciData.length)) {
			return;
		}
		final String ciId = this.ciData[index][1];
		final String ciName = this.ciData[index][0];
		final String title = this.localizationConnector.getLocalizedString("Control Interface Renaming");
		final TextInputPanel panel = new TextInputPanel(ciName, true);
		final GenericDialog dialog = new GenericDialog(this.componentManagementDialog, title, this.okString, this.cancelString, panel);
		if (dialog.showDialog() == 0) {
			final String newName = panel.getText();
			if ((newName != null) && !newName.isEmpty()) {
				try {
					if (!this.ciConnector.renameControlInterface(ciId, newName)) {
						(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to rename control interface."), null, null))).showDialog();
					} else {
						updateCIData();
					}
				} catch (AuthorizationException | ControlInterfaceException e) {
					this.logConnector.log(e);
					(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Error while renaming control interface:\n") + e.getLocalizedMessage(), null, null))).showDialog();
				}
			}
		}
	}

	/**
	 * Renames module.
	 *
	 * @param index the index of the module to rename.
	 */
	public void renameModule(final int index) {
		if ((index < 0) || (this.moduleData == null) || (index >= this.moduleData.length)) {
			return;
		}
		final String moduleId = this.moduleData[index][1];
		final String moduleName = this.moduleData[index][0];
		renameModule(this.componentManagementDialog, moduleId, moduleName, true);
	}

	/**
	 * Renames module.
	 *
	 * @param parent the parent window
	 * @param summary the module summary of the module to rename.
	 */
	public void renameModule(final Window parent, final ModuleSummary summary) {
		if (summary == null) {
			return;
		}
		final String moduleId = summary.getModuleId();
		final String moduleName = summary.getModuleName();
		renameModule(parent, moduleId, moduleName, false);
	}

	/**
	 * Renames module.
	 *
	 * @param parent the parent window
	 * @param moduleId the id of the module to rename
	 * @param moduleName the name of the module to rename
	 * @param updateDialogData true, if dialog data needs to be updated
	 */
	private void renameModule(final Window parent, final String moduleId, final String moduleName, final boolean updateDialogData) {
		final String title = this.localizationConnector.getLocalizedString("Module Renaming");
		final TextInputPanel panel = new TextInputPanel(moduleName, true);
		final GenericDialog dialog = new GenericDialog(parent, title, this.okString, this.cancelString, panel);
		if (dialog.showDialog() == 0) {
			final String newName = panel.getText();
			if ((newName != null) && !newName.isEmpty() && !newName.equals(moduleName)) {
				try {
					if (!this.ciConnector.renameModule(moduleId, newName)) {
						(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to rename module."), null, null))).showDialog();
					} else if ((this.listener == null) && updateDialogData) {
						updateModuleData(false);
					}
				} catch (AuthorizationException | ControlInterfaceException e) {
					this.logConnector.log(e);
					(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Error while renaming module:\n") + e.getLocalizedMessage(), null, null))).showDialog();
				}
			}
		}
	}

	/**
	 * Sets the control interface rights.
	 *
	 * @param index the index of the control interface to set rights for
	 */
	public void setCIRights(final int index) {
		if ((index < 0) || (this.ciData == null) || (index >= this.ciData.length)) {
			return;
		}
		final String ciId = this.ciData[index][1];
		if (ciId.equals(this.ownCiId)) {
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("A control interface cannot modify its own rights."), null, null))).showDialog();
			return;
		}
		final String title = this.localizationConnector.getLocalizedString("Select rights for ") + this.ciData[index][0] + "/" + ciId;
		try {
			final int currentRights = this.ciConnector.getControlInterfaceRights(ciId);
			if (currentRights < 0) {
				(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to retrieve existing rights."), null, null))).showDialog();
				return;
			}
			final ComponentRightsPanel rightsPanel = new ComponentRightsPanel(ControlInterfaceRight.availableControlInterfaceRights, currentRights, this.localizationConnector);
			final GenericDialog dialog = new GenericDialog(this.componentManagementDialog, title, this.okString, this.cancelString, rightsPanel);
			if (dialog.showDialog() == 0) {
				if (!this.ciConnector.setControlInterfaceRights(ciId, rightsPanel.getSelectedRights())) {
					(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to set new rights."), null, null))).showDialog();
				}
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Error while retrieving/setting rights:\n") + e.getLocalizedMessage(), null, null))).showDialog();
		}
	}

	/**
	 * Sets the module rights.
	 *
	 * @param index of the module to set rights for
	 */
	public void setModuleRights(final int index) {
		if ((index < 0) || (this.moduleData == null) || (index >= this.moduleData.length)) {
			return;
		}
		final String moduleId = this.moduleData[index][1];
		final String title = this.localizationConnector.getLocalizedString("Select rights for ") + this.moduleData[index][0] + "/" + moduleId;
		try {
			final int currentRights = this.ciConnector.getModuleRights(moduleId);
			if (currentRights < 0) {
				(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to retrieve existing rights."), null, null))).showDialog();
				return;
			}
			final ComponentRightsPanel rightsPanel = new ComponentRightsPanel(ModuleRight.availableModuleRights, currentRights, this.localizationConnector);
			final GenericDialog dialog = new GenericDialog(this.componentManagementDialog, title, this.okString, this.cancelString, rightsPanel);
			if (dialog.showDialog() == 0) {
				if (!this.ciConnector.setModuleRights(moduleId, rightsPanel.getSelectedRights())) {
					(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to set new rights."), null, null))).showDialog();
				}
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Error while retrieving/setting rights:\n") + e.getLocalizedMessage(), null, null))).showDialog();
		}
	}

	/**
	 * Shows control interface management dialog.
	 */
	void showCIManagementDialog() {
		if (this.componentManagementDialog != null) {
			return;
		}
		int rights;
		try {
			rights = this.ciConnector.getOwnRights();
		} catch (final ControlInterfaceException e) {
			this.logConnector.log(e);
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Error while retrieving own rights:\n") + e.getLocalizedMessage(), null, null))).showDialog();
			return;
		}
		if ((rights & ControlInterfaceRight.MANAGE_CIS) == 0) {
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Insufficient rights for control interface management."), null, null))).showDialog();
			return;
		}
		this.componentManagementDialog = new ComponentManagementDialog(ComponentManagementDialog.TYPE.CI, this.localizationConnector.getLocalizedString("Control Interface Management"), this, this.localizationConnector);
		this.componentManagementDialog.setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);
		updateCIData();
		this.componentManagementDialog.showDialog();
		this.componentManagementDialog = null;
		this.ciData = null;
	}

	/**
	 * Shows module management dialog.
	 */
	void showModuleManagementDialog() {
		if (this.componentManagementDialog != null) {
			return;
		}
		int rights;
		try {
			rights = this.ciConnector.getOwnRights();
		} catch (final ControlInterfaceException e1) {
			this.logConnector.log(e1);
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Error while retrieving own rights:\n") + e1.getLocalizedMessage(), null, null))).showDialog();
			return;
		}
		if ((rights & ControlInterfaceRight.MANAGE_MODULES_AND_CONNECTIONS) == 0) {
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Insufficient rights for module management."), null, null))).showDialog();
			return;
		}
		this.componentManagementDialog = new ComponentManagementDialog(ComponentManagementDialog.TYPE.MODULE, this.localizationConnector.getLocalizedString("Module Management"), this, this.localizationConnector);
		this.componentManagementDialog.setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);
		if ((rights & ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE) == ControlInterfaceRight.RCV_MOD_AND_PORT_UPDATE) {
			try {
				this.listener = new GeneralEventListener() {

					@Override
					public void onGeneralEvent(final GeneralEvent event) {
						if (event instanceof ModuleUpdateEvent) {
							final ModuleSummary summary = ((ModuleUpdateEvent) event).moduleSummary;
							if (((ModuleUpdateEvent) event).type == ModuleUpdateEventType.REMOVE) {
								ComponentManagementController.this.moduleSummaries.remove(summary);
							} else {
								ComponentManagementController.this.moduleSummaries.remove(summary);
								ComponentManagementController.this.moduleSummaries.add(summary);
							}
							updateModuleData(true);
						}
					}
				};
				this.ciConnector.addGeneralEventListener(this.listener, GeneralEventType.MODULE_UPDATE);
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
			}
		}
		updateModuleData(false);
		this.componentManagementDialog.showDialog();
		this.componentManagementDialog = null;
		this.moduleData = null;
		if (this.listener != null) {
			try {
				this.ciConnector.removeGeneralEventListener(this.listener);
			} catch (final ControlInterfaceException e) {
				this.logConnector.log(e);
			}
		}
	}

	/**
	 * Updates visible control interface data.
	 */
	private void updateCIData() {
		if (this.componentManagementDialog == null) {
			return;
		}
		try {
			this.ciSummaries = new HashSet<ControlInterfaceSummary>(this.ciConnector.getActiveControlInterfaces());
			if (this.ciSummaries == null) {
				// TODO: Show error dialog.
				return;
			}
		} catch (AuthorizationException | ControlInterfaceException e) {
			this.logConnector.log(e);
			(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to update control interface data:\n") + e.getLocalizedMessage(), null, null))).showDialog();
			return;
		}
		this.ciData = new String[this.ciSummaries.size()][];
		int i = 0;
		int ownCiIndex = -1;
		for (final ControlInterfaceSummary summary : this.ciSummaries) {
			final String[] info = new String[4];
			info[0] = summary.getCiName();
			if (summary.getCiId().equals(this.ownCiId)) {
				info[0] += this.localizationConnector.getLocalizedString(" (THIS)");
				ownCiIndex = i;
			}
			info[1] = summary.getCiId();
			info[2] = summary.getCiType();
			info[3] = String.valueOf(summary.getCIRights());
			this.ciData[i] = info;
			i++;
		}
		this.componentManagementDialog.setOwnCIIndex(ownCiIndex);
		this.componentManagementDialog.updateData(this.ciData);
	}

	/**
	 * Update visible module data.
	 *
	 * @param isEvent the is event
	 */
	private void updateModuleData(final boolean isEvent) {
		if (this.componentManagementDialog == null) {
			return;
		}
		if (!isEvent) {
			try {
				this.moduleSummaries = new HashSet<ModuleSummary>(this.ciConnector.getActiveModules());
				if (this.moduleSummaries == null) {
					// TODO: Show error dialog.
					return;
				}
			} catch (AuthorizationException | ControlInterfaceException e) {
				this.logConnector.log(e);
				(new GenericDialog(this.componentManagementDialog, this.errorString, this.okString, null, new MessagePanel(this.localizationConnector.getLocalizedString("Unable to update module data:\n") + e.getLocalizedMessage(), null, null))).showDialog();
				return;
			}
		}
		this.moduleData = new String[this.moduleSummaries.size()][];
		int i = 0;
		for (final ModuleSummary summary : this.moduleSummaries) {
			final String[] info = new String[4];
			info[0] = summary.getModuleName();
			info[1] = summary.getModuleId();
			info[2] = summary.getModuleType();
			info[3] = String.valueOf(summary.getModuleRights());
			this.moduleData[i] = info;
			i++;
		}
		this.componentManagementDialog.updateData(this.moduleData);
	}
}
