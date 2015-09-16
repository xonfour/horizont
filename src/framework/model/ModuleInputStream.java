package framework.model;

import java.io.IOException;
import java.io.InputStream;

import framework.control.Broker;

/**
 * Wraps an input stream before forwarding it to connected modules. This way the framework keeps control on streams.
 * <p>
 * IMPORTANT: Exceptions thrown by encapsulated streams are currently forwarded to calling modules. This can be seen as a security risk (information leakage)
 * and should be avoided in the future!
 *
 * @author Stefan Werner
 */
public class ModuleInputStream extends InputStream {

	private final Broker broker;
	private boolean closed = false;
	private long dataTransfered = 0;
	private final InputStream InputStream;
	private final String[] path;
	private final PortTuple portTuple;

	/**
	 * Instantiates a new module input stream.
	 *
	 * @param inputStream the input stream
	 * @param broker the broker
	 * @param portTuple the port tuple
	 * @param path the path
	 */
	public ModuleInputStream(final InputStream inputStream, final Broker broker, final PortTuple portTuple, final String[] path) {
		this.InputStream = inputStream;
		this.broker = broker;
		this.portTuple = portTuple;
		this.path = path;
	}

	@Override
	public int available() throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		return this.InputStream.available();
	}

	@Override
	public void close() throws IOException {
		try {
			this.closed = true;
			this.InputStream.close();
		} finally {
			this.broker.removeInputStream(this.portTuple, this, this.path, this.dataTransfered);
		}
	}

	@Override
	public void mark(final int arg0) {
		this.InputStream.mark(arg0);
	}

	@Override
	public boolean markSupported() {
		return this.InputStream.markSupported();
	}

	@Override
	public int read() throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		final int i = this.InputStream.read();
		if (i >= 0) {
			this.dataTransfered++;
		}
		return i;
	}

	@Override
	public int read(final byte[] arg0) throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		final int i = this.InputStream.read(arg0);
		if (i > 0) {
			this.dataTransfered += i;
		}
		return i;
	}

	@Override
	public int read(final byte[] arg0, final int arg1, final int arg2) throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		final int i = this.InputStream.read(arg0, arg1, arg2);
		if (i > 0) {
			this.dataTransfered += i;
		}
		return i;
	}

	@Override
	public void reset() throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		this.InputStream.reset();
	}

	@Override
	public long skip(final long arg0) throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		return this.InputStream.skip(arg0);
	}

	@Override
	public String toString() {
		return this.InputStream.toString();
	}
}
