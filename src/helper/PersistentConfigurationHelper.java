package helper;

import java.util.HashMap;
import java.util.Map;

import db.iface.ComponentConfigurationController;
import framework.exception.DatabaseException;
import framework.model.DataElement;

/**
 * Helper to persist component configurations using {@link ConfigValue} as data elements in the database.
 * <p>
 * TODO: This is a monster (well, a bit less than {@link ConfigValue}). Comment better, rework and/or change to JSON.
 *
 * @author Stefan Werner
 */
public class PersistentConfigurationHelper {

	private final Map<String, ConfigValue> configValueCache = new HashMap<String, ConfigValue>();
	private final ComponentConfigurationController controller;
	private final String domain;
	private final String[] path;

	/**
	 * Instantiates a new persistent configuration helper without database backend. Works as a non-persistent in-memory configruation. Useful for testing.
	 */
	public PersistentConfigurationHelper() {
		this.controller = null;
		this.domain = null;
		this.path = null;
	}

	/**
	 * Instantiates a new persistent configuration helper.
	 *
	 * @param componentConfigurationController the component configruation controller
	 * @param configDomain the database domain to use for storing configuration (should not contain anything else)
	 * @param path the path of the config element
	 * @throws IllegalArgumentException if an illegal argument is given.
	 * @throws DatabaseException if a database error occurs.
	 */
	public PersistentConfigurationHelper(final ComponentConfigurationController componentConfigurationController, final String configDomain, final String[] path) throws IllegalArgumentException, DatabaseException {
		if ((componentConfigurationController != null) && ((configDomain == null) || configDomain.isEmpty() || (path == null))) {
			throw new IllegalArgumentException();
		} else {
			this.controller = componentConfigurationController;
			this.domain = configDomain;
			this.path = path;
			if (!componentConfigurationController.initializeElementDomains(configDomain)) {
				throw new IllegalArgumentException("invalid domain " + configDomain);
			}
			DataElement element = componentConfigurationController.getElement(configDomain, path);
			if (element == null) {
				element = new DataElement(path);
				componentConfigurationController.storeElement(configDomain, path, element);
			} else {
				final Map<String, String> props = element.getAdditionalProperties();
				if (props != null) {
					for (final String key : props.keySet()) {
						final ConfigValue cv = new ConfigValue(key, props.get(key));
						if ((cv != null) && cv.isValid()) {
							this.configValueCache.put(key, cv);
						}
					}
				}
			}
		}
	}

	/**
	 * Gets all values.
	 *
	 * @param otherPropertiesToInclude the other properties to include
	 * @return the all values
	 */
	public Map<String, String> getAllValues(final Map<String, String> otherPropertiesToInclude) {
		final Map<String, String> result = new HashMap<String, String>();
		if (otherPropertiesToInclude != null) {
			result.putAll(otherPropertiesToInclude);
		}
		for (final ConfigValue cv : this.configValueCache.values()) {
			result.put(cv.getKey(), cv.toString());
		}
		return result;
	}

	/**
	 * Gets a boolean.
	 *
	 * @param key the key
	 * @param fallback the fallback
	 * @return the boolean
	 */
	public boolean getBoolean(final String key, final boolean fallback) {
		final ConfigValue cv = this.configValueCache.get(key);
		if (cv == null) {
			return fallback;
		}
		final Boolean value = cv.getCurrentValueBoolean();
		if (value == null) {
			return fallback;
		}
		return value;

	}

	/**
	 * Gets a config value.
	 *
	 * @param key the key
	 * @return the config value
	 */
	public ConfigValue getConfigValue(final String key) {
		return this.configValueCache.get(key);
	}

	/**
	 * Gets a double.
	 *
	 * @param key the key
	 * @param fallback the fallback
	 * @return the double
	 */
	public double getDouble(final String key, final double fallback) {
		final ConfigValue cv = this.configValueCache.get(key);
		if (cv == null) {
			return fallback;
		}
		final Double value = cv.getCurrentValueDouble();
		if (value == null) {
			return fallback;
		}
		return value;
	}

	/**
	 * Gets a float.
	 *
	 * @param key the key
	 * @param fallback the fallback
	 * @return the float
	 */
	public float getFloat(final String key, final float fallback) {
		final ConfigValue cv = this.configValueCache.get(key);
		if (cv == null) {
			return fallback;
		}
		final Float value = cv.getCurrentValueFloat();
		if (value == null) {
			return fallback;
		}
		return value;
	}

	/**
	 * Gets a integer.
	 *
	 * @param key the key
	 * @param fallback the fallback
	 * @return the integer
	 */
	public int getInteger(final String key, final int fallback) {
		final ConfigValue cv = this.configValueCache.get(key);
		if (cv == null) {
			return fallback;
		}
		final Integer value = cv.getCurrentValueInteger();
		if (value == null) {
			return fallback;
		}
		return value;
	}

	/**
	 * Gets a long.
	 *
	 * @param key the key
	 * @param fallback the fallback
	 * @return the long
	 */
	public long getLong(final String key, final long fallback) {
		final ConfigValue cv = this.configValueCache.get(key);
		if (cv == null) {
			return fallback;
		}
		final Long value = cv.getCurrentValueLong();
		if (value == null) {
			return fallback;
		}
		return value;
	}

	/**
	 * Gets a string.
	 *
	 * @param key the key
	 * @param fallback the fallback
	 * @return the string
	 */
	public String getString(final String key, final String fallback) {
		final ConfigValue cv = this.configValueCache.get(key);
		return cv != null ? cv.getCurrentValueString() : fallback;
	}

	/**
	 * Checks if persistent.
	 *
	 * @return true, if persistent
	 */
	public boolean isPersistent() {
		return this.controller != null;
	}

	/**
	 * Removes a key.
	 *
	 * @param key the key to remove
	 * @return true, if successful
	 */
	public boolean remove(final String key) {
		try {
			return (this.configValueCache.remove(key) != null) && ((this.controller == null) || this.controller.deleteElementProperty(this.domain, this.path, key));
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Resets permanently.
	 *
	 * @return true, if successful
	 */
	public boolean resetPermanently() {
		try {
			this.controller.deleteAllElementProperties(this.domain, this.path);
			this.configValueCache.clear();
			return true;
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Resets temporary.
	 */
	public void resetTemporary() {
		this.configValueCache.clear();
	}

	/**
	 * Updates all values.
	 *
	 * @param properties the properties
	 * @param addOrOverwrite the add or overwrite
	 * @return true, if successful
	 */
	public boolean updateAllValues(final Map<String, String> properties, final boolean addOrOverwrite) {
		boolean result = false;
		for (final String key : properties.keySet()) {
			final ConfigValue cv = new ConfigValue(key, properties.get(key));
			if (cv.isValid()) {
				result |= updateConfigValue(key, cv, addOrOverwrite);
			}
		}
		return result;
	}

	/**
	 * Updates boolean.
	 *
	 * @param key the key
	 * @param b the b
	 * @return true, if successful
	 */
	public boolean updateBoolean(final String key, final boolean b) {
		if (key == null) {
			return false;
		}
		try {
			ConfigValue cv = this.configValueCache.get(key);
			if (cv == null) {
				cv = new ConfigValue(key);
				this.configValueCache.put(key, cv);
			}
			cv.setCurrentValueBoolean(b);
			if (this.controller != null) {
				this.controller.updateElementProperty(this.domain, this.path, key, cv.toString());
			}
			return true;
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Updates config value.
	 *
	 * @param key the key
	 * @param cv the cv
	 * @param addOrOverwrite the add or overwrite
	 * @return true, if successful
	 */
	public boolean updateConfigValue(final String key, final ConfigValue cv, final boolean addOrOverwrite) {
		if ((key == null) || (cv == null)) {
			return false;
		}
		try {
			ConfigValue existingCV = this.configValueCache.get(key);
			if (addOrOverwrite) {
				existingCV = cv;
				this.configValueCache.put(key, cv);
			} else if (existingCV != null) {
				final ConfigValue newCV = new ConfigValue(key, existingCV.toString());
				newCV.setRawCurrentValue(cv.getCurrentValueString());
				if (!newCV.isValid()) {
					return false;
				}
				existingCV.setRawCurrentValue(cv.getCurrentValueString());
			}
			if ((existingCV != null) && (this.controller != null)) {
				this.controller.updateElementProperty(this.domain, this.path, key, existingCV.toString());
			}
			return true;
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Updates double.
	 *
	 * @param key the key
	 * @param d the d
	 * @return true, if successful
	 */
	public boolean updateDouble(final String key, final double d) {
		if (key == null) {
			return false;
		}
		try {
			ConfigValue cv = this.configValueCache.get(key);
			if (cv == null) {
				cv = new ConfigValue(key);
				this.configValueCache.put(key, cv);
			} else {
				final ConfigValue newCV = new ConfigValue(key, cv.toString());
				newCV.setCurrentValueDouble(d);
				if (!newCV.isValid()) {
					return false;
				}
			}
			cv.setCurrentValueDouble(d);
			if (this.controller != null) {
				this.controller.updateElementProperty(this.domain, this.path, key, cv.toString());
			}
			return true;
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Updates float.
	 *
	 * @param key the key
	 * @param f the f
	 * @return true, if successful
	 */
	public boolean updateFloat(final String key, final float f) {
		if (key == null) {
			return false;
		}
		try {
			ConfigValue cv = this.configValueCache.get(key);
			if (cv == null) {
				cv = new ConfigValue(key);
				this.configValueCache.put(key, cv);
			} else {
				final ConfigValue newCV = new ConfigValue(key, cv.toString());
				newCV.setCurrentValueFloat(f);
				if (!newCV.isValid()) {
					return false;
				}
			}
			cv.setCurrentValueFloat(f);
			if (this.controller != null) {
				this.controller.updateElementProperty(this.domain, this.path, key, cv.toString());
			}
			return true;
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Updates integer.
	 *
	 * @param key the key
	 * @param i the i
	 * @return true, if successful
	 */
	public boolean updateInteger(final String key, final int i) {
		if (key == null) {
			return false;
		}
		try {
			ConfigValue cv = this.configValueCache.get(key);
			if (cv == null) {
				cv = new ConfigValue(key);
				this.configValueCache.put(key, cv);
			} else {
				final ConfigValue newCV = new ConfigValue(key, cv.toString());
				newCV.setCurrentValueInteger(i);
				if (!newCV.isValid()) {
					return false;
				}
			}
			cv.setCurrentValueInteger(i);
			if (this.controller != null) {
				this.controller.updateElementProperty(this.domain, this.path, key, cv.toString());
			}
			return true;
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Updates long.
	 *
	 * @param key the key
	 * @param l the l
	 * @return true, if successful
	 */
	public boolean updateLong(final String key, final long l) {
		if (key == null) {
			return false;
		}
		try {
			ConfigValue cv = this.configValueCache.get(key);
			if (cv == null) {
				cv = new ConfigValue(key);
				this.configValueCache.put(key, cv);
			} else {
				final ConfigValue newCV = new ConfigValue(key, cv.toString());
				newCV.setCurrentValueLong(l);
				if (!newCV.isValid()) {
					return false;
				}
			}
			cv.setCurrentValueLong(l);
			if (this.controller != null) {
				this.controller.updateElementProperty(this.domain, this.path, key, cv.toString());
			}
			return true;
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}

	/**
	 * Updates string.
	 *
	 * @param key the key
	 * @param s the s
	 * @return true, if successful
	 */
	public boolean updateString(final String key, final String s) {
		if ((key == null) || (s == null)) {
			return false;
		}
		try {
			ConfigValue cv = this.configValueCache.get(key);
			if (cv == null) {
				cv = new ConfigValue(key);
				this.configValueCache.put(key, cv);
			} else {
				final ConfigValue newCV = new ConfigValue(key, cv.toString());
				newCV.setCurrentValueString(s);
				if (!newCV.isValid()) {
					return false;
				}
			}
			cv.setCurrentValueString(s);
			if (this.controller != null) {
				this.controller.updateElementProperty(this.domain, this.path, key, cv.toString());
			}
			return true;
		} catch (IllegalArgumentException | DatabaseException e) {
			return false;
		}
	}
}