package experimental.module.eventfilterproxy.model;

import java.io.IOException;
import java.io.OutputStream;

import experimental.module.eventfilterproxy.control.EventFilterProxyModule;

/**
 *
 * @author Stefan Werner
 */
public final class EventFilterOutputStream extends OutputStream {

	private final OutputStream out;
	private final EventFilterProxyModule controller;
	private final String intPath;

	public EventFilterOutputStream(final EventFilterProxyModule controller, final OutputStream out, final String intPath) {
		this.controller = controller;
		this.out = out;
		this.intPath = intPath;
	}

	/**
	 * @throws IOException
	 * @see java.io.OutputStream#close()
	 */
	@Override
	public void close() throws IOException {
		this.controller.removeOutputStream(this.intPath);
		this.out.close();
	}

	/**
	 * @param arg0
	 * @return
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object arg0) {
		return this.out.equals(arg0);
	}

	/**
	 * @throws IOException
	 * @see java.io.OutputStream#flush()
	 */
	@Override
	public void flush() throws IOException {
		this.out.flush();
	}

	/**
	 * @return
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.out.hashCode();
	}

	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.out.toString();
	}

	/**
	 * @param arg0
	 * @throws IOException
	 * @see java.io.OutputStream#write(byte[])
	 */
	@Override
	public void write(final byte[] arg0) throws IOException {
		this.out.write(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @param arg2
	 * @throws IOException
	 * @see java.io.OutputStream#write(byte[], int, int)
	 */
	@Override
	public void write(final byte[] arg0, final int arg1, final int arg2) throws IOException {
		this.out.write(arg0, arg1, arg2);
	}

	/* (non-Javadoc)
	 *
	 * @see java.io.OutputStream#write(int) */
	@Override
	public void write(final int arg0) throws IOException {
		this.out.write(arg0);
	}

}
