package helper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import framework.exception.DataContainerException;

/**
 * Reader for the binary data container format used within the system. Currently only used by {@link module.pgpcrpto.control.PGPCryptoModule}.
 *
 * @author Stefan Werner
 */
public class DataContainerHeaderReader {

	public static final String ENCODING = DataContainerHeaderWriter.ENCODING;
	// not used currently, will make it possible to switch to different decoders later on
	public static final byte[] FORMAT_VERSION = DataContainerHeaderWriter.FORMAT_VERSION;
	// used to identify container files
	public static final byte[] HEADER_MAGIC_NUMBER = DataContainerHeaderWriter.HEADER_MAGIC_NUMBER;
	public static final int MAX_FIELD_LENGTH = DataContainerHeaderWriter.MAX_FIELD_LENGTH;

	private final Map<String, byte[]> fieldMap = new TreeMap<String, byte[]>();
	private final InputStream inputStream;
	private String unlimitedSizeFieldName = null;

	/**
	 * Instantiates a new data container header reader.
	 *
	 * @param inputStream the input stream to read
	 */
	public DataContainerHeaderReader(final InputStream inputStream) {
		this.inputStream = inputStream;
	}

	/**
	 * Check int field length.
	 *
	 * @param i the field length
	 * @throws DataContainerException if invalid data
	 */
	private void checkInt(final int i) throws DataContainerException {
		if ((i <= 0) || (i > DataContainerHeaderReader.MAX_FIELD_LENGTH)) {
			throw new DataContainerException("length of (nonfinal) field is negative, 0 or exceeds maximum: " + i);
		}
	}

	/**
	 * Gets the field map containing all (not unlimited) fields.
	 *
	 * @return the field map
	 */
	public Map<String, byte[]> getFieldMap() {
		return this.fieldMap;
	}

	/**
	 * Gets the unlimited field name if any.
	 *
	 * @return the unlimited field name (may be null)
	 */
	public String getUnlimitedFieldName() {
		return this.unlimitedSizeFieldName;
	}

	/**
	 * Checks for unlimited field.
	 *
	 * @return true, if present
	 */
	public boolean hasUnlimitedField() {
		return this.unlimitedSizeFieldName != null;
	}

	/**
	 * Read header from stream.
	 *
	 * @throws DataContainerException if wrong state or invalid data
	 */
	public void readHeader() throws DataContainerException {
		try {
			final byte[] headerMagicNumberBytes = new byte[DataContainerHeaderReader.HEADER_MAGIC_NUMBER.length];
			this.inputStream.read(headerMagicNumberBytes);
			final byte[] formatVersionBytes = new byte[DataContainerHeaderReader.FORMAT_VERSION.length];
			this.inputStream.read(formatVersionBytes);
			if (!Arrays.equals(headerMagicNumberBytes, DataContainerHeaderReader.HEADER_MAGIC_NUMBER) || !Arrays.equals(formatVersionBytes, DataContainerHeaderReader.FORMAT_VERSION)) {
				throw new DataContainerException("wrong magic number or version, probably not a container file");
			}

			final byte[] tocLengthBytes = new byte[4];
			this.inputStream.read(tocLengthBytes);
			// could be more efficient, also unsigned int should be used
			final int tocLength = ByteBuffer.wrap(tocLengthBytes).order(ByteOrder.BIG_ENDIAN).getInt();
			checkInt(tocLength);

			final byte[] tocElementCountBytes = new byte[4];
			this.inputStream.read(tocElementCountBytes);
			final int tocElementCount = ByteBuffer.wrap(tocElementCountBytes).order(ByteOrder.BIG_ENDIAN).getInt();
			checkInt(tocElementCount);

			for (int i = 0; i < tocElementCount; i++) {
				final byte[] idLengthBytes = new byte[4];
				this.inputStream.read(idLengthBytes);
				final int idLength = ByteBuffer.wrap(idLengthBytes).order(ByteOrder.BIG_ENDIAN).getInt();
				checkInt(idLength);
				final byte[] idBytes = new byte[idLength];
				this.inputStream.read(idBytes);
				final String id = new String(idBytes, DataContainerHeaderReader.ENCODING);
				if (id.isEmpty()) {
					throw new DataContainerException("found empty id");
				}
				final byte[] fieldLengthBytes = new byte[4];
				this.inputStream.read(fieldLengthBytes);
				final int fieldLength = ByteBuffer.wrap(fieldLengthBytes).order(ByteOrder.BIG_ENDIAN).getInt();
				if ((i == (tocElementCount - 1)) && (fieldLength == -1)) {
					this.unlimitedSizeFieldName = id;
				} else {
					checkInt(fieldLength);
					this.fieldMap.put(id, new byte[fieldLength]);
				}
			}
			for (final byte[] bytes : this.fieldMap.values()) {
				this.inputStream.read(bytes);
			}
		} catch (final IOException e) {
			throw new DataContainerException(e);
		}
	}

	/* (non-Javadoc)
	 *
	 * @see java.lang.Object#toString() */
	@Override
	public String toString() {
		return "DataContainerHeaderReader [fieldMap=" + this.fieldMap + ", unlimitedSizeFieldName=" + this.unlimitedSizeFieldName + "]";
	}
}
