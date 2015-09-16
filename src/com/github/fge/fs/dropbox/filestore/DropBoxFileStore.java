package com.github.fge.fs.dropbox.filestore;

import java.io.IOException;
import java.nio.file.FileStore;

import com.dropbox.core.DbxAccountInfo;
import com.dropbox.core.DbxAccountInfo.Quota;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxException;
import com.github.fge.filesystem.attributes.FileAttributesFactory;
import com.github.fge.filesystem.filestore.FileStoreBase;

/**
 * A simple DropBox {@link FileStore}
 *
 * <p>
 * This makes use of information available in {@link DbxAccountInfo.Quota}. Information is computed in "real time".
 * </p>
 */
public final class DropBoxFileStore extends FileStoreBase {
	private final DbxClient client;

	/**
	 * Constructor
	 *
	 * @param client the (valid) DropBox client to use
	 */
	public DropBoxFileStore(final DbxClient client, final FileAttributesFactory factory) {
		super("dropbox", factory, false);
		this.client = client;
	}

	private Quota getQuota() throws IOException {
		try {
			return this.client.getAccountInfo().quota;
		} catch (final DbxException e) {
			throw new IOException("cannot get quota info from account", e);
		}
	}

	/**
	 * Returns the size, in bytes, of the file store.
	 *
	 * @return the size of the file store, in bytes
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public long getTotalSpace() throws IOException {
		return getQuota().total;
	}

	/**
	 * Returns the number of unallocated bytes in the file store.
	 * <p>
	 * The returned number of unallocated bytes is a hint, but not a guarantee, that it is possible to use most or any of these bytes. The number of unallocated
	 * bytes is most likely to be accurate immediately after the space attributes are obtained. It is likely to be made inaccurate by any external I/O
	 * operations including those made on the system outside of this virtual machine.
	 *
	 * @return the number of unallocated bytes
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public long getUnallocatedSpace() throws IOException {
		final Quota quota = getQuota();
		return quota.total - quota.normal;
	}

	/**
	 * Returns the number of bytes available to this Java virtual machine on the file store.
	 * <p>
	 * The returned number of available bytes is a hint, but not a guarantee, that it is possible to use most or any of these bytes. The number of usable bytes
	 * is most likely to be accurate immediately after the space attributes are obtained. It is likely to be made inaccurate by any external I/O operations
	 * including those made on the system outside of this Java virtual machine.
	 *
	 * @return the number of bytes available
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public long getUsableSpace() throws IOException {
		final Quota quota = getQuota();
		return quota.total - quota.normal;
	}
}
