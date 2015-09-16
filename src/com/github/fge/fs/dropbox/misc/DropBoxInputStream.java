package com.github.fge.fs.dropbox.misc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import com.dropbox.core.DbxClient;
import com.github.fge.filesystem.driver.FileSystemDriver;

/**
 * Wrapper over {@link DbxClient.Downloader} extending {@link InputStream}
 *
 * <p>
 * This class wraps a DropBox downloader class by extending {@code InputStream} and delegating all of its methods to the downloader's included stream. As such,
 * this means this class is usable in a try-with-resources statement (which the DropBox class isn't).
 * </p>
 *
 * <p>
 * Note about exception handling: unfortunately, the DropBox API class used to wrap an input stream defines a close method which is not declared to throw an
 * exception; which means it may throw none, or it may throw an <em>unchecked</em> exception. As such, the {@link #close()} method of this class captures all
 * {@link RuntimeException}s which {@link DbxClient.Downloader#close()} may throw and wrap it into a {@link DropBoxIOException}. If the underlying input stream
 * <em>did</em> throw an exception, however, then such an exception is {@link Throwable#addSuppressed(Throwable) suppressed}.
 * </p>
 *
 * @see FileSystemDriver#newInputStream(Path, OpenOption...)
 */
public final class DropBoxInputStream extends InputStream {
	private final DbxClient.Downloader downloader;
	private final InputStream delegate;

	public DropBoxInputStream(final DbxClient.Downloader downloader) {
		this.downloader = downloader;
		this.delegate = downloader.body;
	}

	@Override
	public int available() throws IOException {
		return this.delegate.available();
	}

	@Override
	public void close() throws IOException {
		IOException exception = null;

		try {
			this.delegate.close();
		} catch (final IOException e) {
			exception = e;
		}

		try {
			this.downloader.close();
		} catch (final RuntimeException e) {
			if (exception != null) {
				exception.addSuppressed(e);
			} else {
				exception = new IOException("tell me what to do, please", e);
			}
		}

		if (exception != null) {
			throw exception;
		}
	}

	@Override
	public synchronized void mark(final int readlimit) {
		this.delegate.mark(readlimit);
	}

	@Override
	public boolean markSupported() {
		return this.delegate.markSupported();
	}

	@Override
	public int read() throws IOException {
		return this.delegate.read();
	}

	@Override
	public int read(final byte[] b) throws IOException {
		return this.delegate.read(b);
	}

	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		return this.delegate.read(b, off, len);
	}

	@Override
	public synchronized void reset() throws IOException {
		this.delegate.reset();
	}

	@Override
	public long skip(final long n) throws IOException {
		return this.delegate.skip(n);
	}
}
