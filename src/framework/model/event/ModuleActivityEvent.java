package framework.model.event;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import framework.control.Core;

/**
 * Event send on module activities. Activity strings can be found in {@link framework.model.event.type.ModuleActivityEventType}.
 *
 * @author Stefan Werner
 */
public class ModuleActivityEvent implements GeneralEvent {

	private final String activity;
	private final long creationDate;
	private final Map<String, Object> properties;
	private final String sendingModuleId;

	/**
	 * Instantiates a new module activity event.
	 *
	 * @param activity the activity, see {@link framework.model.event.type.ModuleActivityEventType}.
	 * @param sendingModuleId the sending module ID
	 */
	public ModuleActivityEvent(final String activity, final String sendingModuleId) {
		this.creationDate = System.currentTimeMillis();
		this.activity = activity;
		this.sendingModuleId = sendingModuleId;
		this.properties = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
	}

	/**
	 * Adds a property (for example parameters of the module activity).
	 *
	 * @param key the key
	 * @param obj the object
	 * @return the module activity event
	 */
	public ModuleActivityEvent addProperty(final String key, final Object obj) {
		this.properties.put(key, obj);
		return this;
	}

	/**
	 * Gets the activity.
	 *
	 * @return the activity, see {@link framework.model.event.type.ModuleActivityEventType}.
	 */
	public String getActivity() {
		return this.activity;
	}

	/**
	 * Gets the creation date.
	 *
	 * @return the creation date
	 */
	public long getCreationDate() {
		return this.creationDate;
	}

	/**
	 * Gets the properties.
	 *
	 * @return the properties
	 */
	public Map<String, Object> getProperties() {
		return this.properties;
	}

	/**
	 * Gets the sending module ID.
	 *
	 * @return the sending module ID
	 */
	public String getSendingModuleId() {
		return this.sendingModuleId;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "ModuleActivityEvent [" + Core.getDefaultDateFormat().format(new Date(this.creationDate)) + ", activity=" + this.activity + ", sendingModuleId=" + this.sendingModuleId + ", properties=" + this.properties + "]";
	}
}
