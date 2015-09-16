package module.webdavclient.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.github.sardine.Sardine;

/**
 * Temporary saves output data streams in memory since the sardine webDAV client is unable to write streams.
 * <p>
 * TODP: This is bad and limits the size of transfered files. Find a better solution.
 *
 * @author Stefan Werner
 */
public class WebDavOutputStream extends ByteArrayOutputStream {

	private final String address;
	private final Sardine sardine;

	/**
	 * Instantiates a new web dav output stream.
	 *
	 * @param sardine the sardine instance
	 * @param address the address to connect to
	 */
	public WebDavOutputStream(final Sardine sardine, final String address) {
		super();
		this.sardine = sardine;
		this.address = address;
	}

	/* (non-Javadoc)
	 *
	 * @see java.io.ByteArrayOutputStream#close() */
	@Override
	public void close() throws IOException {
		super.close();
		try {
			this.sardine.put(this.address, super.toByteArray());
		} catch (final IOException e) {
			throw new IOException();
		}
	}
}
