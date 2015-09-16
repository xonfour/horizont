package i18n.iface;

import i18n.model.TextOrientationType;

/**
 * Interface for classes implementing the localization subsystem.
 *
 * @author Stefan Werner
 */
public interface LocalizationController {

	/**
	 * Adds an external localization resource to the system.
	 *
	 * @param componentId the component ID adding the resource
	 * @param resourceLocation the resource location
	 * @return true, if successful
	 */
	public boolean addLocalizationResource(String componentId, String resourceLocation);

	/**
	 * Gets a formated date (default style).
	 *
	 * @param date the date
	 * @return the formated date default
	 */
	public String getFormatedDateDefault(long date);

	/**
	 * Gets a formated date (localized style).
	 *
	 * @param date the date
	 * @return the formated date localized
	 */
	public String getFormatedDateLocalized(long date);

	/**
	 * Gets a localized string. Will return the default string if localized version is unavailable.
	 *
	 * @param defaultString the default string
	 * @return the localized string
	 */
	public String getLocalizedString(String defaultString);

	/**
	 * Gets the localized string. Will return the default string if localized version is unavailable. Will optionally only consider internal resources and not
	 * externally added ones.
	 *
	 * @param defaultString the default string
	 * @param onlyUseFrameworkResources set to true to only use internal framework resources
	 * @return the localized string
	 */
	public String getLocalizedString(String defaultString, boolean onlyUseFrameworkResources);

	/**
	 * Gets the local text orientation (left to right or right to left).
	 *
	 * @return the local text orientation
	 */
	public TextOrientationType getLocalTextOrientation();

	/**
	 * Removes an external localization resource from the system.
	 *
	 * @param componentId the component id
	 * @return true, if successful
	 */
	public boolean removeLocalizationResource(String componentId);
}
