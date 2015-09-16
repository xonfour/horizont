package helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import framework.exception.DataContainerException;

/**
 * Writer for the binary data container format used within the system. Currently only used by {@link module.pgpcrpto.control.PGPCryptoModule}.
 *
 * @author Stefan Werner
 */
public class DataContainerHeaderWriter {

	public static final String ENCODING = "UTF-8";
	// not used currently, will make it possible to switch to different decoders later on
	public static final byte[] FORMAT_VERSION = { 1 };
	// used to identify container files
	public static final byte[] HEADER_MAGIC_NUMBER = { 45, 89 };
	public static final int MAX_FIELD_LENGTH = 1024 * 1024;

	private final Map<String, byte[]> fieldMap = new TreeMap<String, byte[]>();
	// lock for multithreaded access
	private final ReentrantLock lock = new ReentrantLock(true);
	private final OutputStream outputStream;
	private boolean tocFinalized = false;
	private String unlimitedSizeFieldName = null;

	/**
	 * Instantiates a new data container header writer.
	 *
	 * @param outputStream the output stream to write to
	 */
	public DataContainerHeaderWriter(final OutputStream outputStream) {
		this.outputStream = outputStream;
	}

	/**
	 * Adds a field.
	 *
	 * @param fieldId the field ID
	 * @param data the data as byte array
	 * @throws DataContainerException if wrong state or invalid data
	 */
	public void addField(final String fieldId, final byte[] data) throws DataContainerException {
		this.lock.lock();
		if (this.tocFinalized) {
			this.lock.unlock();
			throw new DataContainerException("TOC already finalized/written");
		}
		if ((fieldId == null) || fieldId.isEmpty() || (data == null) || (data.length == 0)) {
			this.lock.unlock();
			throw new DataContainerException("null/empty id or data not allowed");
		}
		if (data.length > DataContainerHeaderWriter.MAX_FIELD_LENGTH) {
			throw new DataContainerException("field length exceeds maximum");
		}
		this.fieldMap.put(fieldId, data);
		this.lock.unlock();
	}

	/**
	 * Adds the unlimited size field.
	 *
	 * @param fieldId the field ID
	 * @throws DataContainerException if wrong state or invalid data
	 */
	public void addUnlimitedSizeField(final String fieldId) throws DataContainerException {
		this.lock.lock();
		if (this.tocFinalized) {
			this.lock.unlock();
			throw new DataContainerException("TOC already finalized/written");
		}
		if ((fieldId == null) || fieldId.isEmpty()) {
			this.lock.unlock();
			throw new DataContainerException("null/empty id not allowed");
		}
		this.unlimitedSizeFieldName = fieldId;
		this.lock.unlock();
	}

	/**
	 * Convert int value to byte array.
	 *
	 * @param value the value
	 * @return the byte[]
	 */
	public byte[] convertIntByteArray(final int value) {
		return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}

	/**
	 * Generate table of contents (TOC) and write to byte array.
	 *
	 * @return the byte array
	 * @throws IOException if an I/O exception has occurred
	 */
	private byte[] generateTOC() throws IOException {
		this.lock.lock();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (final String id : this.fieldMap.keySet()) {
			final byte[] idBytes = id.getBytes(DataContainerHeaderWriter.ENCODING);
			out.write(convertIntByteArray(idBytes.length));
			out.write(idBytes);
			out.write(convertIntByteArray(this.fieldMap.get(id).length));
		}
		if (this.unlimitedSizeFieldName != null) {
			final byte[] idBytes = this.unlimitedSizeFieldName.getBytes(DataContainerHeaderWriter.ENCODING);
			out.write(convertIntByteArray(idBytes.length));
			out.write(idBytes);
			out.write(convertIntByteArray(-1));
		}
		this.lock.unlock();
		return out.toByteArray();
	}

	/**
	 * Write header to stream.
	 *
	 * @throws DataContainerException if wrong state or other error
	 */
	public void writeHeader() throws DataContainerException {
		this.lock.lock();
		this.tocFinalized = true;

		try {
			this.outputStream.write(DataContainerHeaderWriter.HEADER_MAGIC_NUMBER);
			this.outputStream.write(DataContainerHeaderWriter.FORMAT_VERSION);

			final byte[] toc = generateTOC();
			this.outputStream.write(convertIntByteArray(toc.length));
			if (this.unlimitedSizeFieldName != null) {
				this.outputStream.write(convertIntByteArray(this.fieldMap.size() + 1));
			} else {
				this.outputStream.write(convertIntByteArray(this.fieldMap.size()));
			}
			this.outputStream.write(toc);
			for (final byte[] bytes : this.fieldMap.values()) {
				this.outputStream.write(bytes);
			}
		} catch (final IOException e) {
			throw new DataContainerException(e);
		} finally {
			this.lock.unlock();
		}
	}
}
