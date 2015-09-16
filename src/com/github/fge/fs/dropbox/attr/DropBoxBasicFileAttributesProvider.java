package com.github.fge.fs.dropbox.attr;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import com.dropbox.core.DbxEntry;
import com.github.fge.filesystem.attributes.provider.BasicFileAttributesProvider;

/**
 * {@link BasicFileAttributes} implementation for DropBox
 *
 * <p>
 * Note: DropBox has poor support for file times; as required by the {@link BasicFileAttributes} contract, all methods returning a {@link FileTime} for which
 * there is no support will return Unix's epoch (that is, Jan 1st, 1970 at 00:00:00 GMT).
 * </p>
 */
public final class DropBoxBasicFileAttributesProvider extends BasicFileAttributesProvider {
	private final DbxEntry entry;
	private final boolean isDirectory;

	public DropBoxBasicFileAttributesProvider(final DbxEntry entry) throws IOException {
		if (entry == null) {
			throw new NoSuchFileException(null);
		}
		this.entry = entry;
		if (entry.isFolder()) {
			this.isDirectory = true;
		} else {
			this.isDirectory = false;
		}
	}

	/**
	 * Tells whether the file is a directory.
	 */
	@Override
	public boolean isDirectory() {
		return (this.entry != null) && this.isDirectory;
	}

	/**
	 * Tells whether the file is a regular file with opaque content.
	 */
	@Override
	public boolean isRegularFile() {
		return (this.entry != null) && !this.isDirectory;
	}

	/**
	 * Returns the time of last modification.
	 * <p>
	 * If the file system implementation does not support a time stamp to indicate the time of last modification then this method returns an implementation
	 * specific default value, typically a {@code FileTime} representing the epoch (1970-01-01T00:00:00Z).
	 *
	 * @return a {@code FileTime} representing the time the file was last modified
	 */
	@Override
	public FileTime lastModifiedTime() {
		return (this.entry == null) || this.isDirectory ? BasicFileAttributesProvider.UNIX_EPOCH : FileTime.fromMillis(this.entry.asFile().lastModified.getTime());
	}

	/**
	 * Returns the size of the file (in bytes). The size may differ from the actual size on the file system due to compression, support for sparse files, or
	 * other reasons. The size of files that are not {@link #isRegularFile regular} files is implementation specific and therefore unspecified.
	 *
	 * @return the file size, in bytes
	 */
	@Override
	public long size() {
		return (this.entry == null) || this.isDirectory ? 0L : this.entry.asFile().numBytes;
	}
}
