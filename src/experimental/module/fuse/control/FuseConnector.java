package experimental.module.fuse.control;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import module.iface.Provider;
import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.XattrFiller;
import net.fusejna.XattrListFiller;
import net.fusejna.types.TypeMode;
import net.fusejna.types.TypeMode.ModeWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterFull;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

import framework.constants.GenericModuleCommandProperties;
import framework.constants.GenericModuleCommands;
import framework.control.LogConnector;
import framework.control.ProsumerConnector;
import framework.exception.AuthorizationException;
import framework.exception.BrokerException;
import framework.exception.ModuleException;
import framework.model.DataElement;
import framework.model.ProsumerPort;
import framework.model.event.type.LogEventLevelType;
import framework.model.type.DataElementType;

public class FuseConnector extends FuseFilesystemAdapterFull {

	private final class FuseInputStreamInfo {

		private final InputStream stream;
		private final String fusePath;
		private long offset = 0;
		private long lastRefresh = System.currentTimeMillis();

		private FuseInputStreamInfo(final InputStream stream, final String fusePath) {
			this.stream = stream;
			this.fusePath = fusePath;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof FuseInputStreamInfo)) {
				return false;
			}
			final FuseInputStreamInfo other = (FuseInputStreamInfo) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (this.fusePath == null) {
				if (other.fusePath != null) {
					return false;
				}
			} else if (!this.fusePath.equals(other.fusePath)) {
				return false;
			}
			if (this.lastRefresh != other.lastRefresh) {
				return false;
			}
			if (this.offset != other.offset) {
				return false;
			}
			if (this.stream == null) {
				if (other.stream != null) {
					return false;
				}
			} else if (!this.stream.equals(other.stream)) {
				return false;
			}
			return true;
		}

		private FuseConnector getOuterType() {
			return FuseConnector.this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = (prime * result) + getOuterType().hashCode();
			result = (prime * result) + ((this.fusePath == null) ? 0 : this.fusePath.hashCode());
			result = (prime * result) + (int) (this.lastRefresh ^ (this.lastRefresh >>> 32));
			result = (prime * result) + (int) (this.offset ^ (this.offset >>> 32));
			result = (prime * result) + ((this.stream == null) ? 0 : this.stream.hashCode());
			return result;
		}
	}

	private final class FuseOutputStreamInfo {

		private final OutputStream stream;
		private final String fusePath;
		private long offset = 0;
		private long lastRefresh = System.currentTimeMillis();

		private FuseOutputStreamInfo(final OutputStream stream, final String fusePath) {
			this.stream = stream;
			this.fusePath = fusePath;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof FuseOutputStreamInfo)) {
				return false;
			}
			final FuseOutputStreamInfo other = (FuseOutputStreamInfo) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (this.fusePath == null) {
				if (other.fusePath != null) {
					return false;
				}
			} else if (!this.fusePath.equals(other.fusePath)) {
				return false;
			}
			if (this.lastRefresh != other.lastRefresh) {
				return false;
			}
			if (this.offset != other.offset) {
				return false;
			}
			if (this.stream == null) {
				if (other.stream != null) {
					return false;
				}
			} else if (!this.stream.equals(other.stream)) {
				return false;
			}
			return true;
		}

		private FuseConnector getOuterType() {
			return FuseConnector.this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = (prime * result) + getOuterType().hashCode();
			result = (prime * result) + ((this.fusePath == null) ? 0 : this.fusePath.hashCode());
			result = (prime * result) + (int) (this.lastRefresh ^ (this.lastRefresh >>> 32));
			result = (prime * result) + (int) (this.offset ^ (this.offset >>> 32));
			result = (prime * result) + ((this.stream == null) ? 0 : this.stream.hashCode());
			return result;
		}
	}

	public static final String FS_NAME_PREFIX = "fluentCloudFS-";
	public static final long CONNECTION_TIMEOUT_SECS = 20;

	public static final int MAX_XATTR_VAL_SIZE = 255;
	public static final int MAX_BUFFER_MARK_VALUE = 2 ^ 27; // maximum rewind buffer is ~128MB
	private final String fsName;
	private final ProsumerConnector connector;
	private final ProsumerPort port;
	private final LogConnector logConnector;
	private final Splitter splitter = Splitter.on(File.separator).omitEmptyStrings().trimResults();
	private final Map<String, FuseInputStreamInfo> openInputStreams = new ConcurrentHashMap<String, FuseInputStreamInfo>();
	private final Map<String, FuseOutputStreamInfo> openOutputStreams = new ConcurrentHashMap<String, FuseOutputStreamInfo>();
	private Thread cleanUpThread;
	private final ReentrantLock streamsLock = new ReentrantLock(true);
	private boolean started = false;
	private boolean connected = false;

	private boolean readOnly;

	private final Runnable openStreamsCleaner = new Runnable() {

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				FuseConnector.this.streamsLock.lock();
				for (final String fusePath : FuseConnector.this.openInputStreams.keySet()) {
					final FuseInputStreamInfo info = FuseConnector.this.openInputStreams.get(fusePath);
					if ((System.currentTimeMillis() - info.lastRefresh) > (FuseConnector.CONNECTION_TIMEOUT_SECS * 1000)) {
						FuseConnector.this.openInputStreams.remove(fusePath);
						try {
							info.stream.close();
						} catch (final Exception e) {
							// ignored
						}
					}
				}
				for (final String fusePath : FuseConnector.this.openOutputStreams.keySet()) {
					final FuseOutputStreamInfo info = FuseConnector.this.openOutputStreams.get(fusePath);
					if ((System.currentTimeMillis() - info.lastRefresh) > (FuseConnector.CONNECTION_TIMEOUT_SECS * 1000)) {
						FuseConnector.this.openOutputStreams.remove(fusePath);
						try {
							info.stream.close();
						} catch (final Exception e) {
							// ignored
						}
					}
				}
				FuseConnector.this.streamsLock.unlock();
				try {
					Thread.sleep(FuseConnector.CONNECTION_TIMEOUT_SECS * 1000);
				} catch (final InterruptedException e) {
					break;
				}
			}
		}
	};

	FuseConnector(final ProsumerPort port, final ProsumerConnector connector, final LogConnector logConnector, final String fsName, final boolean readOnly) {
		this.port = port;
		this.connector = connector;
		this.logConnector = logConnector;
		this.fsName = fsName;
		this.readOnly = readOnly;
	}

	@Override
	public void afterUnmount(final File mountPoint) {
		stop();
	}

	@Override
	public void beforeMount(final File mountPoint) {
		start();
	}

	private boolean checkState() {
		return this.started && this.connected;
	}

	@Override
	public int chmod(final String fusePath, final ModeWrapper mode) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		if (((mode.mode() & TypeMode.S_IWOTH) > 0) || ((mode.mode() & TypeMode.S_IXOTH) > 0)) {
			try {
				this.connector.sendModuleCommand(this.port, GenericModuleCommands.SET_PUBLIC, path, null);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return -ErrorCodes.EIO();
			}
		} else if (((mode.mode() & TypeMode.S_IWGRP) > 0) || ((mode.mode() & TypeMode.S_IXGRP) > 0)) {
			try {
				this.connector.sendModuleCommand(this.port, GenericModuleCommands.SET_SHARED, path, null);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return -ErrorCodes.EIO();
			}
		} else {
			try {
				this.connector.sendModuleCommand(this.port, GenericModuleCommands.SET_PRIVATE, path, null);
			} catch (BrokerException | ModuleException | AuthorizationException e) {
				this.logConnector.log(e);
				return -ErrorCodes.EIO();
			}
		}
		return 0;
	}

	// @Override
	// public int access(final String path, final int access) {
	// return -ErrorCodes.ENOSYS();
	// }

	@Override
	public int chown(final String path, final long uid, final long gid) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		return 0;
	}

	@Override
	public int create(final String fusePath, final ModeWrapper mode, final FileInfoWrapper info) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (mode.type() != NodeType.FILE) {
			return -ErrorCodes.ENOSYS();
		}
		final String[] path = getPath(fusePath);
		try {
			final DataElement element = this.connector.getElement(this.port, path);
			if (element == null) {
				final OutputStream out = this.connector.writeData(this.port, path);
				try {
					out.flush();
					out.close();
				} catch (final IOException e) {
					// ignored
				}
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
		info.truncate(false).append(false);
		return 0;
	}

	// @Override
	// public int bmap(final String path, final FileInfoWrapper info) {
	// return 0;
	// }

	@Override
	public int flush(final String path, final FileInfoWrapper info) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state");
			return -ErrorCodes.EIO();
		}
		this.streamsLock.lock();
		final FuseOutputStreamInfo oInfo = this.openOutputStreams.get(path);
		if (oInfo != null) {
			try {
				oInfo.stream.flush();
				oInfo.lastRefresh = System.currentTimeMillis();
			} catch (final IOException e) {
				this.streamsLock.unlock();
				this.logConnector.log(LogEventLevelType.ERROR, "wrong module state");
				return -ErrorCodes.EIO();
			}
		}
		this.streamsLock.unlock();
		return 0;
	}

	@Override
	public int fsync(final String path, final int datasync, final FileInfoWrapper info) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		return 0;
	}

	@Override
	public int fsyncdir(final String path, final int datasync, final FileInfoWrapper info) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		return 0;
	}

	// @Override
	// public void destroy() {
	// }

	// @Override
	// public int fgetattr(final String path, final StatWrapper stat, final FileInfoWrapper info) {
	// return getattr(path, stat);
	// }

	@Override
	public int getattr(final String fusePath, final StatWrapper stat) {
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		try {
			final DataElement element = this.connector.getElement(this.port, path);
			if (element == null) {
				return -ErrorCodes.ENOENT();
			}
			NodeType type;
			boolean isFolder = false;
			if (element.getType() == DataElementType.FILE) {
				type = NodeType.FILE;
			} else if (element.getType() == DataElementType.FOLDER) {
				type = NodeType.DIRECTORY;
				isFolder = true;
			} else {
				type = NodeType.SOCKET; // may change in the future
			}
			stat.setAllTimesMillis(element.getModificationDate());
			stat.size(element.getSize());

			final Map<String, String> result = this.connector.sendModuleCommand(this.port, GenericModuleCommands.GET_ACCESS_MODE, path, null);
			String internalMode = null;
			if ((result != null) && ((internalMode = result.get(GenericModuleCommandProperties.KEY___ACCESS_MODE)) != null)) {
				if (internalMode.equals(GenericModuleCommandProperties.VALUE_ACCESS_MODE___SHARED)) {
					stat.setMode(type, true, true, isFolder, true, true, isFolder, false, false, false);
				} else {
					stat.setMode(type, true, true, isFolder, false, false, false, false, false, false);
				}
			} else if ((internalMode == null) || internalMode.equals(GenericModuleCommandProperties.VALUE_ACCESS_MODE___PUBLIC)) {
				stat.setMode(type, true, true, isFolder, true, true, isFolder, true, true, isFolder);
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
		return 0;
	}

	@Override
	protected String getName() {
		return this.fsName;
	}

	@Override
	protected String[] getOptions() {
		return new String[] { "-o", "big_writes", "-o", "max_write=4194304", "-o", "max_read=4194304", "-o", "direct_io", "-o", "entry_timeout=10", "-o", "negative_timeout=5", "-o", "attr_timeout=10" };
	}

	// @Override
	// public int ftruncate(final String path, final long offset, final FileInfoWrapper info) {
	// return truncate(path, offset);
	// }

	private String[] getPath(String fusePath) {
		fusePath = CharMatcher.JAVA_ISO_CONTROL.removeFrom(fusePath);
		final List<String> parts = new ArrayList<String>(this.splitter.splitToList(fusePath));
		parts.remove(".");
		parts.remove("..");
		return parts.toArray(new String[0]);
	}

	@Override
	public int getxattr(final String fusePath, final String xattr, final XattrFiller filler, final long size, final long position) {
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		try {
			final DataElement element = this.connector.getElement(this.port, path);
			if (element == null) {
				if (ErrorCodes.ENOATTR() != null) {
					return -ErrorCodes.ENOATTR();
				} else {
					return -ErrorCodes.ENODATA();
				}
			}
			String value;
			if (!element.hasAdditionalProperties() || ((value = element.getAdditionalProperty(xattr)) == null)) {
				if (ErrorCodes.ENOATTR() != null) {
					return -ErrorCodes.ENOATTR();
				} else {
					return -ErrorCodes.ENODATA();
				}
			}
			final byte[] valueBytes = value.getBytes();
			if (valueBytes.length > size) {
				return -ErrorCodes.ERANGE();
			}
			filler.set(valueBytes);
			return valueBytes.length;
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
	}

	// @Override
	// protected String[] getOptions() {
	// return null;
	// }

	@Override
	public int link(final String path, final String target) {
		return -ErrorCodes.ENOSYS();
	}

	// @Override
	// public void init() {
	// }

	@Override
	public int listxattr(final String fusePath, final XattrListFiller filler) {
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 10");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		try {
			final DataElement element = this.connector.getElement(this.port, path);
			if (element == null) {
				return -ErrorCodes.ENOENT();
			}
			if (!element.hasAdditionalProperties()) {
				return 0;
			}
			final Map<String, String> properties = element.getAdditionalProperties();
			filler.add(properties.keySet());
			return 0;
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int mkdir(final String fusePath, final ModeWrapper mode) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 11");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		try {
			final DataElement element = this.connector.getElement(this.port, path);
			if (element != null) {
				return -ErrorCodes.EEXIST();
			}
			final int result = this.connector.createFolder(this.port, path);
			if (result == 0) {
				return 0;
			} else if (result == 1) {
				return -ErrorCodes.EEXIST();
			} else if (result == -1) {
				return -ErrorCodes.EROFS();
			} else {
				return -ErrorCodes.EIO();
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
	}

	// @Override
	// public int lock(final String path, final FileInfoWrapper info, final FlockCommand command, final FlockWrapper flock) {
	// return -ErrorCodes.ENOSYS();
	// }

	@Override
	public int mknod(final String path, final ModeWrapper mode, final long dev) {
		return -ErrorCodes.ENODEV();
	}

	@Override
	public int open(final String path, final FileInfoWrapper info) {
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 13");
			return -ErrorCodes.EIO();
		}
		info.append(false).truncate(false);
		return 0;
	}

	@Override
	public int read(final String fusePath, final ByteBuffer buffer, final long size, final long offset, final FileInfoWrapper info) {
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 14");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		int errCode = -ErrorCodes.EIO();
		this.streamsLock.lock();
		FuseInputStreamInfo streamInfo = this.openInputStreams.get(fusePath);
		InputStream in = null;
		boolean nonseekable = true;
		if (streamInfo != null) {
			in = streamInfo.stream;
			if ((offset < streamInfo.offset) && !in.markSupported()) {
				errCode = -ErrorCodes.ESPIPE();
				in = null;
			} else {
				final long delta = offset - streamInfo.offset;
				if (delta > 0) {
					try {
						in.skip(delta);
					} catch (final IOException e) {
						errCode = -ErrorCodes.ESPIPE();
						in = null;
					}
				} else if (delta < 0) {
					try {
						in.reset();
						in.skip(offset);
					} catch (final IOException e) {
						errCode = -ErrorCodes.ESPIPE();
						in = null;
					}
				}
				streamInfo.offset = offset;
				streamInfo.lastRefresh = System.currentTimeMillis();
			}
		} else {
			if (offset != 0) {
				errCode = -ErrorCodes.ESPIPE();
			} else {
				try {
					in = this.connector.readData(this.port, path);
					if (in == null) {
						errCode = -ErrorCodes.EISDIR();
					} else {
						// in = new BufferedInputStream(in);
						if (in.markSupported()) {
							in.mark(FuseConnector.MAX_BUFFER_MARK_VALUE);
							nonseekable = false;
						}
						streamInfo = new FuseInputStreamInfo(in, fusePath);
						this.openInputStreams.put(fusePath, streamInfo);
					}
				} catch (BrokerException | ModuleException | AuthorizationException e) {
					this.logConnector.log(e);
					errCode = -ErrorCodes.EIO();
				}
			}
		}
		this.streamsLock.unlock();
		if (in != null) {
			final byte[] bytes = new byte[(int) size];
			try {
				final int count = in.read(bytes);
				if (count <= 0) {
					errCode = 0;
				} else {
					buffer.put(bytes, 0, count);
					errCode = count;
					streamInfo.offset += count;
					streamInfo.lastRefresh = System.currentTimeMillis();
				}
			} catch (final IOException e) {
				errCode = -ErrorCodes.EIO();
			}
			info.nonseekable(nonseekable);
		}
		return errCode;
	}

	// @Override
	// public int opendir(final String path, final FileInfoWrapper info) {
	// return 0;
	// }

	@Override
	public int readdir(final String fusePath, final DirectoryFiller filler) {
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 16");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		Set<DataElement> children;
		try {
			children = this.connector.getChildElements(this.port, path, false);
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
		if (children == null) {
			System.out.println("------1");
			return -ErrorCodes.ENOTDIR();
		}
		for (final DataElement child : children) {
			filler.add(child.getName());
		}
		return 0;
	}

	@Override
	public int readlink(final String path, final ByteBuffer buffer, final long size) {
		return -ErrorCodes.ENOSYS();
	}

	@Override
	public int release(final String fusePath, final FileInfoWrapper info) {
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 17");
			return -ErrorCodes.EIO();
		}
		this.streamsLock.lock();
		final FuseInputStreamInfo iInfo = this.openInputStreams.remove(fusePath);
		if (iInfo != null) {
			try {
				iInfo.stream.close();
			} catch (final IOException e) {
				// ignored
			}
		}
		final FuseOutputStreamInfo oInfo = this.openOutputStreams.remove(fusePath);
		this.streamsLock.unlock();
		if (oInfo != null) {
			try {
				oInfo.stream.close();
			} catch (final IOException e) {
				// ignored
			}
		}
		return 0;
	}

	@Override
	public int removexattr(final String fusePath, final String xattr) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 18");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		if ((xattr == null) || xattr.isEmpty()) {
			return -ErrorCodes.ENOTSUP();
		}
		try {
			this.connector.sendModuleCommand(this.port, GenericModuleCommands.REMOVE_ELEMENT_PROPERTIES, path, ImmutableMap.of(xattr, xattr));
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
		return 0;
	}

	// @Override
	// public int releasedir(final String path, final FileInfoWrapper info) {
	// return 0;
	// }

	@Override
	public int rename(final String fuseSrcPath, final String fuseDestPath) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 19");
			return -ErrorCodes.EIO();
		}
		final String[] srcPath = getPath(fuseSrcPath);
		final String[] destPath = getPath(fuseDestPath);
		try {
			final int result = this.connector.move(this.port, srcPath, destPath);
			if (result == Provider.RESULT_CODE___OK) {
				return 0;
			} else if (result == Provider.RESULT_CODE___ERROR_NO_SUCH_FILE) {
				return -ErrorCodes.ENOENT();
			} else if (result == Provider.RESULT_CODE___ERROR_ALREADY_EXISTENT) {
				return -ErrorCodes.EEXIST();
			} else {
				return -ErrorCodes.EIO();
			}

		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int rmdir(final String fusePath) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 20");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		try {
			final DataElement element = this.connector.getElement(this.port, path);
			if (element == null) {
				return -ErrorCodes.ENOENT();
			}
			final Set<DataElement> children = this.connector.getChildElements(this.port, path, false);
			if (children == null) {
				return -ErrorCodes.ENOTDIR();
			}
			if (!children.isEmpty()) {
				return -ErrorCodes.ENOTEMPTY();
			}
			final int result = this.connector.delete(this.port, path);
			if (result == Provider.RESULT_CODE___OK) {
				return 0;
			} else if (result == Provider.RESULT_CODE___INVALID_READONLY) {
				return -ErrorCodes.EROFS();
			} else {
				return -ErrorCodes.EIO();
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
	}

	void setConnected(final boolean connected) {
		this.connected = connected;
	}

	void setReadOnly(final boolean readOnly) {
		this.readOnly = readOnly;
	}

	// @Override
	// public int statfs(final String path, final StatvfsWrapper wrapper) {
	// return 0;
	// }

	@Override
	public int setxattr(final String fusePath, final String xattr, final ByteBuffer buf, final long size, final int flags, final int position) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 21");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		if ((xattr == null) || xattr.isEmpty() || (size < 0) || (size > FuseConnector.MAX_XATTR_VAL_SIZE)) {
			return -ErrorCodes.ENOTSUP();
		}
		final byte[] valueBytes = new byte[(int) size];
		buf.get(valueBytes, position, (int) size);
		final String value = new String(valueBytes);
		if (!CharMatcher.ASCII.matchesAllOf(value)) {
			return -ErrorCodes.ENOTSUP();
		}
		try {
			this.connector.sendModuleCommand(this.port, GenericModuleCommands.PUT_ELEMENT_PROPERTIES, path, ImmutableMap.of(xattr, value));
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
		return 0;
	}

	// @Override
	// public int truncate(final String path, final long offset) {
	// return 0;
	// }

	void start() {
		if (!this.started) {
			this.cleanUpThread = new Thread(this.openStreamsCleaner);
			this.cleanUpThread.start();
			this.started = true;
		}
	}

	// @Override
	// public int utimens(final String path, final TimeBufferWrapper wrapper) {
	// return -ErrorCodes.ENOSYS();
	// }

	void stop() {
		if (this.started) {
			this.started = false;
			if (this.cleanUpThread != null) {
				this.cleanUpThread.interrupt();
			}
			this.streamsLock.lock();
			for (final FuseInputStreamInfo info : this.openInputStreams.values()) {
				try {
					info.stream.close();
				} catch (final Exception e) {
					// ignored
				}
			}
			for (final FuseOutputStreamInfo info : this.openOutputStreams.values()) {
				try {
					info.stream.close();
				} catch (final Exception e) {
					// ignored
				}
			}
			this.streamsLock.unlock();
		}
	}

	@Override
	public int symlink(final String path, final String target) {
		return -ErrorCodes.ENOSYS();
	}

	@Override
	public int unlink(final String fusePath) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 22");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		try {
			final DataElement element = this.connector.getElement(this.port, path);
			if (element == null) {
				return -ErrorCodes.ENOENT();
			}
			if (element.getType() == DataElementType.FOLDER) {
				return -ErrorCodes.EISDIR();
			}
			final int result = this.connector.delete(this.port, path);
			if (result == Provider.RESULT_CODE___OK) {
				return 0;
			} else if (result == Provider.RESULT_CODE___INVALID_READONLY) {
				return -ErrorCodes.EROFS();
			} else {
				System.out.println("------2");
				return -ErrorCodes.EIO();
			}
		} catch (BrokerException | ModuleException | AuthorizationException e) {
			this.logConnector.log(e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int write(final String fusePath, final ByteBuffer buf, final long bufSize, final long offset, final FileInfoWrapper wrapper) {
		if (this.readOnly) {
			return -ErrorCodes.EROFS();
		}
		if (!checkState()) {
			this.logConnector.log(LogEventLevelType.ERROR, "wrong module state 23");
			return -ErrorCodes.EIO();
		}
		final String[] path = getPath(fusePath);
		int errCode = -ErrorCodes.EIO();
		this.streamsLock.lock();
		FuseOutputStreamInfo streamInfo = this.openOutputStreams.get(fusePath);
		OutputStream out = null;
		if (streamInfo != null) {
			if (offset != streamInfo.offset) {
				errCode = -ErrorCodes.ESPIPE();
			} else {
				out = streamInfo.stream;
				streamInfo.lastRefresh = System.currentTimeMillis();
			}
		} else {
			if (offset != 0) {
				errCode = -ErrorCodes.ESPIPE();
			} else {
				try {
					out = this.connector.writeData(this.port, path);
					if (out == null) {
						errCode = -ErrorCodes.EISDIR();
					} else {
						streamInfo = new FuseOutputStreamInfo(out, fusePath);
						this.openOutputStreams.put(fusePath, streamInfo);
					}
				} catch (BrokerException | ModuleException | AuthorizationException e) {
					this.logConnector.log(e);
					errCode = -ErrorCodes.EIO();
				}
			}
		}
		this.streamsLock.unlock();
		if (out != null) {
			final byte[] bytes = new byte[(int) bufSize];
			buf.get(bytes, 0, (int) bufSize);
			try {
				out.write(bytes);
				errCode = (int) bufSize;
				streamInfo.offset += bufSize;
				streamInfo.lastRefresh = System.currentTimeMillis();
			} catch (final IOException e) {
				errCode = -ErrorCodes.EIO();
			}
		}
		return errCode;
	}
}