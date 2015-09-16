package framework.model.summary;

/**
 * Summarizes a control interface.
 *
 * @author Stefan Werner
 */
public class ControlInterfaceSummary implements Summary {

	private final String ciId;
	private final String ciName;
	private final int ciRights;
	private final String ciType;

	/**
	 * Instantiates a new control interface summary.
	 *
	 * @param ciId the CI ID
	 * @param ciName the CI name
	 * @param ciType the CI type
	 * @param ciRights the CI rights
	 */
	public ControlInterfaceSummary(final String ciId, final String ciName, final String ciType, final int ciRights) {
		this.ciId = ciId;
		this.ciName = ciName;
		this.ciType = ciType;
		this.ciRights = ciRights;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object) */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ControlInterfaceSummary)) {
			return false;
		}
		final ControlInterfaceSummary other = (ControlInterfaceSummary) obj;
		if (this.ciId == null) {
			if (other.ciId != null) {
				return false;
			}
		} else if (!this.ciId.equals(other.ciId)) {
			return false;
		}
		if (this.ciType == null) {
			if (other.ciType != null) {
				return false;
			}
		} else if (!this.ciType.equals(other.ciType)) {
			return false;
		}
		return true;
	}

	/**
	 * Gets the CI ID.
	 *
	 * @return the CI ID
	 */
	public String getCiId() {
		return this.ciId;
	}

	/**
	 * Gets the CI name.
	 *
	 * @return the CI Name
	 */
	public String getCiName() {
		return this.ciName;
	}

	/**
	 * Gets the CI rights.
	 *
	 * @return the CI rights
	 */
	public int getCIRights() {
		return this.ciRights;
	}

	/**
	 * Gets the CI type.
	 *
	 * @return the CI type
	 */
	public String getCiType() {
		return this.ciType;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.ciId == null) ? 0 : this.ciId.hashCode());
		result = (prime * result) + ((this.ciType == null) ? 0 : this.ciType.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return this.ciName + " (" + this.ciType + " / " + this.ciId + ")";
	}
}
