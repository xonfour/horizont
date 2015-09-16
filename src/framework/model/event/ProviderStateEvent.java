package framework.model.event;

import java.util.Date;

import framework.control.Core;

/**
 * Event send on provider state changes. See {@link framework.model.type.ModuleStateType} for available states.
 *
 * @author Stefan Werner
 */
public class ProviderStateEvent implements GeneralEvent {

	public final long creationDate;
	public final int state;

	/**
	 * Instantiates a new provider state event.
	 *
	 * @param moduleState the module state, see {@link framework.model.type.ModuleStateType}
	 */
	public ProviderStateEvent(final int moduleState) {
		this.state = moduleState;
		this.creationDate = System.currentTimeMillis();
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "ProviderStateEvent [" + Core.getDefaultDateFormat().format(new Date(this.creationDate)) + ", state=" + this.state + "]";
	}
}
