package module.simplesync.control;

import helper.TextFormatHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import module.simplesync.model.JobPriorityComperator;
import module.simplesync.model.SyncJob;
import module.simplesync.model.type.SyncJobType;

/**
 * Manager class to filter, queue, (re)check and postpone synchronization jobs.
 *
 * @author Stefan Werner
 */
public class SyncJobManager {

	private static final int JOB_TRANSFER_INTERVAL_SECONDS = 1;
	private static final int JOB_WAIT_INTERVALS_COUNT = 5;

	private final Runnable delayedJobsHandler = new Runnable() {

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					// TODO: Use a timer here!
					TimeUnit.SECONDS.sleep(SyncJobManager.JOB_TRANSFER_INTERVAL_SECONDS);
				} catch (final InterruptedException e) {
					break;
				}
				if ((System.currentTimeMillis() - SyncJobManager.this.lastJobReceivedTime) > (SyncJobManager.JOB_TRANSFER_INTERVAL_SECONDS * 1000 * SyncJobManager.JOB_WAIT_INTERVALS_COUNT)) {
					SyncJobManager.this.jobLock.lock();
					final TreeSet<SyncJob> jobs = new TreeSet<SyncJob>(new JobPriorityComperator());
					SyncJobManager.this.delayedJobsQueue.drainTo(jobs);
					for (final SyncJob job : jobs) {
						final String intPath = getInternalPathString(job.getElement().getPath());
						final String depPath = getJobDependency(intPath);
						if (depPath == null) {
							SyncJobManager.this.jobQueue.add(job);
						} else {
							TreeSet<SyncJob> otherJobs = SyncJobManager.this.jobDependencies.get(depPath);
							if (otherJobs == null) {
								otherJobs = new TreeSet<SyncJob>(new JobPriorityComperator());
								SyncJobManager.this.jobDependencies.put(depPath, otherJobs);
							}
							otherJobs.add(job);
						}
					}
					SyncJobManager.this.jobLock.unlock();
				}
			}
		}
	};
	private Thread delayedJobsHandlerThread;
	private final DelayQueue<SyncJob> delayedJobsQueue = new DelayQueue<SyncJob>();
	private final HashMap<String, TreeSet<SyncJob>> jobDependencies = new HashMap<String, TreeSet<SyncJob>>();
	private final ReentrantLock jobLock = new ReentrantLock(true);
	private final BlockingQueue<SyncJob> jobQueue = new PriorityBlockingQueue<SyncJob>(10, new JobPriorityComperator());
	private long lastJobReceivedTime = 0;
	private final HashSet<String> pathsInProcessing = new HashSet<String>();

	/**
	 * Checks held jobs. Jobs may be held when another job on the same path is being processed. If that job is done held jobs can be released.
	 *
	 * @param finishedJob the finished job
	 */
	private void checkHeldJobs(final SyncJob finishedJob) {
		final String doneIntPath = getInternalPathString(finishedJob.getElement().getPath());
		this.jobLock.lock();
		final TreeSet<SyncJob> otherJobs = this.jobDependencies.remove(doneIntPath);
		if (otherJobs != null) {
			final SyncJob newJob = otherJobs.pollFirst();
			if (newJob != null) {
				final String intPath = getInternalPathString(newJob.getElement().getPath());
				if (!otherJobs.isEmpty()) {
					this.jobDependencies.put(intPath, otherJobs);
				}
				this.jobQueue.add(newJob);
			}
		}
		this.jobLock.unlock();
	}

	/**
	 * Gets an internal path string.
	 *
	 * @param path the path
	 * @return the internal path string
	 */
	private String getInternalPathString(final String[] path) {
		return TextFormatHelper.getPathString(path);
	}

	/**
	 * Gets the internal path of the active job we need to wait for before the given path can be processed, null if none.
	 *
	 * @param intPath the internal path
	 * @return the job dependency
	 */
	private String getJobDependency(final String intPath) {
		this.jobLock.lock();
		String depPath = null;
		for (final String otherPath : this.pathsInProcessing) {
			if (otherPath.startsWith(intPath) || intPath.startsWith(otherPath)) {
				depPath = otherPath;
				break;
			}
		}
		if (depPath == null) {
			for (final SyncJob otherJob : this.jobQueue) {
				final String otherPath = getInternalPathString(otherJob.getElement().getPath());
				if (otherPath.startsWith(intPath) || intPath.startsWith(otherPath)) {
					depPath = otherPath;
					break;
				}
			}
		}
		this.jobLock.unlock();
		return depPath;
	}

	/**
	 * Checks if given job currently on hold.
	 *
	 * @param job the job
	 * @return true, if on hold
	 */
	private boolean isJobCurrentlyHold(final SyncJob job) {
		this.jobLock.lock();
		for (final TreeSet<SyncJob> jobs : this.jobDependencies.values()) {
			if (jobs.contains(job)) {
				this.jobLock.unlock();
				return true;
			}
		}
		this.jobLock.unlock();
		return false;
	}

	/**
	 * Locks a path.
	 *
	 * @param path the path
	 * @return true, if OK
	 */
	boolean lockPath(final String[] path) {
		final String intPath = getInternalPathString(path);
		if ((path == null) || (path.length == 0) || intPath.isEmpty()) {
			return false;
		}
		boolean result = true;
		this.jobLock.lock();
		if (this.pathsInProcessing.contains(intPath)) {
			result = false;
		} else {
			for (final String otherPath : this.pathsInProcessing) {
				if (otherPath.startsWith(intPath) || intPath.startsWith(otherPath)) {
					result = false;
					break;
				}
			}
		}
		if (result) {
			this.pathsInProcessing.add(intPath);
		}
		this.jobLock.unlock();
		return result;
	}

	/**
	 * Queues a job.
	 *
	 * @param job the job
	 * @return true, if successful
	 */
	public boolean queueJob(final SyncJob job) {
		boolean result = false;
		final String intPath = getInternalPathString(job.getElement().getPath());
		this.jobLock.lock();
		if (this.pathsInProcessing.contains(intPath)) {
			this.jobLock.unlock();
			return false;
		}
		if ((job.getType() == SyncJobType.FORCE_TRANSFER) || (!this.delayedJobsQueue.contains(job) && !this.jobQueue.contains(job) && !isJobCurrentlyHold(job))) {
			this.delayedJobsQueue.add(job);
			result = true;
			this.lastJobReceivedTime = System.currentTimeMillis();
		}
		this.jobLock.unlock();
		return result;
	}

	/**
	 * Removes a finished job from processing list.
	 *
	 * @param job the job
	 * @return true, if successful
	 */
	boolean removeJobFromProcessingList(final SyncJob job) {
		this.jobLock.lock();
		final boolean result = this.pathsInProcessing.remove(getInternalPathString(job.getElement().getPath()));
		if (result) {
			checkHeldJobs(job);
		}
		this.jobLock.unlock();
		return result;
	}

	/**
	 * Requeues a job.
	 *
	 * @param job the job
	 * @return true, if successful
	 */
	boolean requeueJob(final SyncJob job) {
		boolean result;
		this.jobLock.lock();
		this.pathsInProcessing.remove(getInternalPathString(job.getElement().getPath()));
		result = queueJob(job);
		this.jobLock.unlock();
		return result;
	}

	/**
	 * Starts the manager.
	 */
	void start() {
		this.delayedJobsHandlerThread = new Thread(this.delayedJobsHandler);
		this.delayedJobsHandlerThread.start();
	}

	/**
	 * Stops the manager.
	 */
	void stop() {
		if (this.delayedJobsHandlerThread != null) {
			this.delayedJobsHandlerThread.interrupt();
			this.delayedJobsHandlerThread = null;
		}
	}

	/**
	 * Gets the next synchronization job. Blocks is none.
	 *
	 * @return the sync job
	 * @throws InterruptedException if interrupted while blocked
	 */
	SyncJob take() throws InterruptedException {
		final SyncJob job = this.jobQueue.take();
		this.jobLock.lock();
		lockPath(job.getElement().getPath());
		this.jobLock.unlock();
		return job;
	}

	/**
	 * Unlocks a path.
	 *
	 * @param path the path
	 * @return true, if OK
	 */
	boolean unlockPath(final String[] path) {
		this.jobLock.lock();
		final boolean result = this.pathsInProcessing.remove(getInternalPathString(path));
		this.jobLock.unlock();
		return result;
	}
}
