package i18n.control;

import framework.constants.Constants;
import i18n.iface.LocalizationController;
import i18n.model.TextOrientationType;

import java.awt.ComponentOrientation;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link LocalizationController} using Java ResourceBundle subsystem.
 * <p>
 * TODO: Move from unhandy properties-files to something more sophisticated.
 *
 * @author Stefan Werner
 */
public class SimpleLocalizationController implements LocalizationController {

	private final DateFormat fallbackDateFormat;
	private ResourceBundle frameworkBundle;
	private DateFormat localizedDateFormat;
	private final Map<String, ResourceBundle> otherBundles = new ConcurrentHashMap<String, ResourceBundle>();

	/**
	 * Instantiates a new simple localization controller.
	 *
	 * @param internalResourceLocation the internal resource location
	 */
	public SimpleLocalizationController(final String internalResourceLocation) {
		this.fallbackDateFormat = new SimpleDateFormat(Constants.DEFAULT_DATE_FORMAT_PATTERN);
		try {
			this.frameworkBundle = ResourceBundle.getBundle(internalResourceLocation);
			this.localizedDateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, this.frameworkBundle.getLocale());
		} catch (final MissingResourceException e) {
			this.frameworkBundle = null;
			this.localizedDateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see i18n.iface.Localization#addLocalizationResource(java.lang.String, java.lang.String) */
	@Override
	public boolean addLocalizationResource(final String componentId, final String resourceLocation) {
		if ((componentId == null) || componentId.isEmpty() || (resourceLocation == null) || resourceLocation.isEmpty()) {
			return false;
		}
		try {
			final ResourceBundle otherBundle = ResourceBundle.getBundle(resourceLocation);
			this.otherBundles.put(componentId, otherBundle);
			return true;
		} catch (final MissingResourceException e) {
			return false;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see i18n.iface.Localization#getFallbackDateFormat(long) */
	@Override
	public String getFormatedDateDefault(final long date) {
		return this.fallbackDateFormat.format(new Date(date));
	}

	/* (non-Javadoc)
	 * 
	 * @see i18n.iface.Localization#getLocalizedDateFormat(long) */
	@Override
	public String getFormatedDateLocalized(final long date) {
		return this.localizedDateFormat.format(new Date(date));
	}

	/* (non-Javadoc)
	 * 
	 * @see i18n.iface.Localization#getLocalizedString(java.lang.String) */
	@Override
	public String getLocalizedString(final String defaultString) {
		return getLocalizedString(defaultString, false);
	}

	/* (non-Javadoc)
	 * 
	 * @see i18n.iface.Localization#getLocalizedString(java.lang.String, boolean) */
	@Override
	public String getLocalizedString(final String defaultString, final boolean internalResourcesOnly) {
		if ((defaultString == null) || defaultString.isEmpty()) {
			return defaultString;
		}
		String result = defaultString;
		if (this.frameworkBundle != null) {
			try {
				result = this.frameworkBundle.getString(defaultString);
			} catch (final MissingResourceException e) {
				// ignored
			}
		}
		if ((result == null) && !internalResourcesOnly) {
			for (final ResourceBundle rs : this.otherBundles.values()) {
				try {
					result = rs.getString(defaultString);
					break;
				} catch (final MissingResourceException e) {
					// ignored
				}
			}
		}
		return result;
	}

	/* (non-Javadoc)
	 * 
	 * @see i18n.iface.Localization#getLocalizedTextOrientationType() */
	@Override
	public TextOrientationType getLocalTextOrientation() {
		if (ComponentOrientation.getOrientation(this.frameworkBundle.getLocale()) == ComponentOrientation.RIGHT_TO_LEFT) {
			return TextOrientationType.RIGHT_TO_LEFT;
		} else {
			return TextOrientationType.LEFT_TO_RIGHT;
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see i18n.iface.Localization#removeLocalizationResource(java.lang.String) */
	@Override
	public boolean removeLocalizationResource(final String componentId) {
		if ((componentId == null) || componentId.isEmpty()) {
			return false;
		} else {
			return this.otherBundles.remove(componentId) != null;
		}
	}
}
