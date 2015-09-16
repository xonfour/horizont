package module.simplesync.model;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import module.simplesync.model.type.SyncJobType;
import framework.model.DataElement;
import framework.model.ProsumerPort;

/**
 * Object to store data of a synchronization job. Especially it stores the date when a requeued/postponed job may be retried.
 *
 * @author Stefan Werner
 */
public final class SyncJob implements Delayed {

	private static final long EXPIRE_DATE_DEFAULT = Long.MAX_VALUE;
	private static final int INCREASE_COUNT_DEFAULT_MAX = 4;
	// milliseconds are used here, values result in a maximum retry delay of around half a day
	private static final long INITIAL_DELAY_MSECS = 5000;

	private static final int POSTPONE_DELAY_MSECS = 5000;
	private static final boolean RANDOMIZE_DELAY = true;
	// job will be removed when exceeding this delay count
	private static final int RETRY_COUNT_DEFAULT_MAX = 50;
	private static final int RETRY_DELAY_MULTIPLICATOR = 1;
	private final long creationDate;
	private long currentDelay;
	private DataElement element;
	private long expireDate = SyncJob.EXPIRE_DATE_DEFAULT;
	private int increaseCountMax = SyncJob.INCREASE_COUNT_DEFAULT_MAX;
	private boolean notificationJob = false;
	private Random random;
	private int retryCount = 0;
	private int retryCountMax = SyncJob.RETRY_COUNT_DEFAULT_MAX;
	private final ProsumerPort sourcePort;
	private final SyncJobType type;

	/**
	 * Instantiates a new sync job.
	 *
	 * @param sourcePort the source port
	 * @param element the element
	 * @param type the type
	 * @param notificationJob the notification job
	 */
	public SyncJob(final ProsumerPort sourcePort, final DataElement element, final SyncJobType type, final boolean notificationJob) {
		this.sourcePort = sourcePort;
		this.element = element;
		this.type = type;
		this.creationDate = System.currentTimeMillis();
		this.currentDelay = this.creationDate;
		this.notificationJob = notificationJob;
		if (SyncJob.RANDOMIZE_DELAY) {
			this.random = new Random();
		}
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Comparable#compareTo(java.lang.Object) */
	@Override
	public int compareTo(final Delayed arg0) {
		final long delta = this.creationDate - ((SyncJob) arg0).creationDate;
		if (delta < 0) {
			return -1;
		} else if (delta > 0) {
			return 1;
		} else {
			return 0;
		}
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
		if (!(obj instanceof SyncJob)) {
			return false;
		}
		final SyncJob other = (SyncJob) obj;
		if (!Arrays.equals(this.element.getPath(), other.getElement().getPath())) {
			return false;
		}
		if (this.sourcePort.getPortId() == null) {
			if (other.sourcePort.getPortId() != null) {
				return false;
			}
		} else if (!this.sourcePort.getPortId().equals(other.sourcePort.getPortId())) {
			return false;
		}
		return true;
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
	 * Gets the current delay.
	 *
	 * @return the current delay
	 */
	public long getCurrentDelay() {
		return this.currentDelay;
	}

	/* (non-Javadoc)
	 *
	 * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit) */
	@Override
	public long getDelay(final TimeUnit arg0) {
		final long delta = this.currentDelay - System.currentTimeMillis();
		return arg0.convert(delta, TimeUnit.MILLISECONDS);
	}

	/**
	 * Gets the element.
	 *
	 * @return the element
	 */
	public DataElement getElement() {
		return this.element;
	}

	/**
	 * Gets the expire date.
	 *
	 * @return the expire date
	 */
	public long getExpireDate() {
		return this.expireDate;
	}

	/**
	 * Gets how often the retry interval may be increase.
	 *
	 * @return the maximum increase count
	 */
	public int getIncreaseCountMax() {
		return this.increaseCountMax;
	}

	/**
	 * Gets the retry count.
	 *
	 * @return the retryCount
	 */
	public int getRetryCount() {
		return this.retryCount;
	}

	/**
	 * Gets how often this job may be retried.
	 *
	 * @return the maximum retry count
	 */
	public int getRetryCountMax() {
		return this.retryCountMax;
	}

	/**
	 * Gets the source port.
	 *
	 * @return the source port
	 */
	public ProsumerPort getSourcePort() {
		return this.sourcePort;
	}

	/**
	 * Gets the job type.
	 *
	 * @return the job type
	 */
	public SyncJobType getType() {
		return this.type;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode() */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + Arrays.hashCode(this.element.getPath());
		result = (prime * result) + ((this.sourcePort.getPortId() == null) ? 0 : this.sourcePort.getPortId().hashCode());
		return result;
	}

	/**
	 * Increases retry count.
	 *
	 * @return true, if successful
	 */
	public boolean increaseRetryCount() {
		this.retryCount++;
		if ((this.retryCount > this.retryCountMax) || (System.currentTimeMillis() > this.expireDate)) {
			return false;
		} else {
			if (this.retryCount <= this.increaseCountMax) {
				long delay = this.retryCount * SyncJob.INITIAL_DELAY_MSECS * SyncJob.RETRY_DELAY_MULTIPLICATOR;
				if (SyncJob.RANDOMIZE_DELAY) {
					delay += this.random.nextInt(((int) SyncJob.INITIAL_DELAY_MSECS) * this.retryCount);
				}
				this.currentDelay = System.currentTimeMillis() + delay;
			}
			return true;
		}
	}

	/**
	 * Checks if is notification job.
	 *
	 * @return the notificationJob
	 */
	public boolean isNotificationJob() {
		return this.notificationJob;
	}

	/**
	 * Postpones job.
	 *
	 * @return true, if successful
	 */
	public boolean postpone() {
		this.currentDelay = System.currentTimeMillis() + SyncJob.POSTPONE_DELAY_MSECS;
		return true;
	}

	/**
	 * Sets the current delay.
	 *
	 * @param currentDelay the current delay to set
	 */
	public void setCurrentDelay(final long currentDelay) {
		this.currentDelay = currentDelay;
	}

	/**
	 * Sets the element.
	 *
	 * @param element the element to set
	 */
	public void setElement(final DataElement element) {
		this.element = element;
	}

	/**
	 * Sets the expire date.
	 *
	 * @param expireDate the expire date to set
	 */
	public void setExpireDate(final long expireDate) {
		this.expireDate = expireDate;
	}

	/**
	 * Sets how often the retry interval may be increase.
	 *
	 * @param increaseCountMax the maximum increase count
	 */
	public void setIncreaseCountMax(final int increaseCountMax) {
		this.increaseCountMax = increaseCountMax;
	}

	/**
	 * Sets the retry count.
	 *
	 * @param retryCount the retryCount to set
	 */
	public void setRetryCount(final int retryCount) {
		this.retryCount = retryCount;
	}

	/**
	 * Sets how often this job may be retried.
	 *
	 * @param retryCountMax the maximum retry count to set
	 */
	public void setRetryCountMax(final int retryCountMax) {
		this.retryCountMax = retryCountMax;
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "SyncJob [sourcePort=" + this.sourcePort + ", element=" + this.element + ", creationDate=" + this.creationDate + ", retryCount=" + this.retryCount + ", currentDelay=" + this.currentDelay + ", type=" + this.type + "]";
	}
}
