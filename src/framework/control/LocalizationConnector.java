package framework.control;

import i18n.iface.LocalizationController;
import i18n.model.TextOrientationType;

/**
 * Connector to the framework for all other parts to access the localization subsystem.
 *
 * @author Stefan Werner
 */
public class LocalizationConnector {

	private final String componentId;
	private final LocalizationController localizationController;

	/**
	 * Instantiates a new localization connector without a connected localization controller. It can be used but does not translate anything. Usefull for
	 * testing.
	 */
	public LocalizationConnector() {
		this(null, null);
	}

	/**
	 * Instantiates a new localization connector.
	 *
	 * @param componentId the component id
	 * @param localizationController the localization controller
	 */
	public LocalizationConnector(final String componentId, final LocalizationController localizationController) {
		this.localizationController = localizationController; // may be NULL to work without any localization
		this.componentId = componentId;
	}

	/**
	 * Adds an external localization resource to the system.
	 *
	 * @param resourceLocation the resource location
	 * @return true, if successful
	 * @see i18n.iface.LocalizationController#addLocalizationResource(java.lang.String, java.lang.String)
	 */
	public boolean addLocalizationResource(final String resourceLocation) {
		if (this.localizationController == null) {
			return false;
		}
		return this.localizationController.addLocalizationResource(this.componentId, resourceLocation);
	}

	/**
	 * Gets a formated date (default style).
	 *
	 * @param date the date
	 * @return the formated date
	 * @see i18n.iface.LocalizationController#getFormatedDateDefault(long)
	 */
	public String getFormatedDateDefault(final long date) {
		if (this.localizationController == null) {
			return String.valueOf(date);
		}
		return this.localizationController.getFormatedDateDefault(date);
	}

	/**
	 * Gets a formated date (localized style).
	 *
	 * @param date the date
	 * @return the formated date
	 * @see i18n.iface.LocalizationController#getFormatedDateLocalized(long)
	 */
	public String getFormatedDateLocalized(final long date) {
		if (this.localizationController == null) {
			return String.valueOf(date);
		}
		return this.localizationController.getFormatedDateLocalized(date);
	}

	/**
	 * Gets a localized string. Will return the default string if localized version is unavailable.
	 *
	 * @param defaultString the default string
	 * @return the localized string
	 * @see i18n.iface.LocalizationController#getLocalizedString(java.lang.String)
	 */
	public String getLocalizedString(final String defaultString) {
		if (this.localizationController == null) {
			return defaultString;
		}
		return this.localizationController.getLocalizedString(defaultString);
	}

	/**
	 * Gets a localized string. Will return the default string if localized version is unavailable. Will optionally only consider internal resources and not
	 * externally added ones.
	 *
	 * @param defaultString the default string
	 * @param onlyUseFrameworkResources set to true to only use internal framework resources
	 * @return the localized string
	 * @see i18n.iface.LocalizationController#getLocalizedString(java.lang.String, boolean)
	 */
	public String getLocalizedString(final String defaultString, final boolean onlyUseFrameworkResources) {
		if (this.localizationController == null) {
			return defaultString;
		}
		return this.localizationController.getLocalizedString(defaultString, onlyUseFrameworkResources);
	}

	/**
	 * Gets the local text orientation (left to right or right to left).
	 *
	 * @return the local text orientation
	 * @see i18n.iface.LocalizationController#getLocalTextOrientation()
	 */
	public TextOrientationType getLocalTextOrientation() {
		if (this.localizationController == null) {
			return TextOrientationType.LEFT_TO_RIGHT;
		}
		return this.localizationController.getLocalTextOrientation();
	}

	/**
	 * Removes an external localization resource from the system.
	 *
	 * @return true, if successful
	 * @see i18n.iface.LocalizationController#removeLocalizationResource(java.lang.String)
	 */
	public boolean removeLocalizationResource() {
		if (this.localizationController == null) {
			return false;
		}
		return this.localizationController.removeLocalizationResource(this.componentId);
	}
}
