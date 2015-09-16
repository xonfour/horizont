package framework.model;

import java.io.IOException;
import java.io.OutputStream;

import framework.control.Broker;

/**
 * Wraps an output stream before forwarding it to connected modules. This way the framework keeps control on streams.
 * <p>
 * IMPORTANT: Exceptions thrown by encapsulated streams are currently forwarded to calling modules. This can be seen as a security risk (information leakage)
 * and should be avoided in the future!
 *
 * @author Stefan Werner
 */
public class ModuleOutputStream extends OutputStream {

	private final Broker broker;
	private boolean closed = false;
	private long dataTransfered = 0;
	private final OutputStream outputStream;
	private final String[] path;
	private final PortTuple portTuple;

	/**
	 * Instantiates a new module output stream.
	 *
	 * @param outputStream the output stream
	 * @param broker the broker
	 * @param portTuple the port tuple
	 * @param path the path
	 */
	public ModuleOutputStream(final OutputStream outputStream, final Broker broker, final PortTuple portTuple, final String[] path) {
		this.outputStream = outputStream;
		this.broker = broker;
		this.portTuple = portTuple;
		this.path = path;
	}

	@Override
	public void close() throws IOException {
		try {
			this.closed = true;
			this.outputStream.close();
		} finally {
			this.broker.removeOutputStream(this.portTuple, this, this.path, this.dataTransfered);
		}
	}

	@Override
	public void flush() throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		this.outputStream.flush();
	}

	@Override
	public String toString() {
		return this.outputStream.toString();
	}

	@Override
	public void write(final byte[] arg0) throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		this.outputStream.write(arg0);
		this.dataTransfered += arg0.length;
	}

	@Override
	public void write(final byte[] arg0, final int arg1, final int arg2) throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		this.outputStream.write(arg0, arg1, arg2);
		this.dataTransfered += arg2;
	}

	@Override
	public void write(final int arg0) throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		this.outputStream.write(arg0);
		this.dataTransfered++;
	}
}
