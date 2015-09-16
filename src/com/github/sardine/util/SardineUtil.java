package com.github.sardine.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.github.sardine.model.ObjectFactory;

/**
 * Basic utility code. I borrowed some code from the webdavlib for parsing dates.
 *
 * @author jonstevens
 */
public final class SardineUtil {
	private static final String[] SUPPORTED_DATE_FORMATS = new String[] { "yyyy-MM-dd'T'HH:mm:ss'Z'", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ss.sss'Z'", "yyyy-MM-dd'T'HH:mm:ssZ", "EEE MMM dd HH:mm:ss zzz yyyy", "EEEEEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMMM d HH:mm:ss yyyy" };

	/**
	 * Default namespace prefix
	 */
	public static final String CUSTOM_NAMESPACE_PREFIX = "s";

	/**
	 * Default namespace URI
	 */
	public static final String CUSTOM_NAMESPACE_URI = "SAR:";

	/**
	 * Default namespace prefix
	 */
	public static final String DEFAULT_NAMESPACE_PREFIX = "d";

	/**
	 * Default namespace URI
	 */
	public static final String DEFAULT_NAMESPACE_URI = "DAV:";

	/**
	 * Reusable context for marshalling and unmarshalling
	 */
	private static final JAXBContext JAXB_CONTEXT;

	static {
		try {
			JAXB_CONTEXT = JAXBContext.newInstance(ObjectFactory.class);
		} catch (final JAXBException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Date formats using for Date parsing.
	 */
	private static final List<ThreadLocal<SimpleDateFormat>> DATETIME_FORMATS;

	static {
		final List<ThreadLocal<SimpleDateFormat>> l = new ArrayList<ThreadLocal<SimpleDateFormat>>(SardineUtil.SUPPORTED_DATE_FORMATS.length);
		for (int i = 0; i < SardineUtil.SUPPORTED_DATE_FORMATS.length; i++) {
			l.add(new ThreadLocal<SimpleDateFormat>());
		}
		DATETIME_FORMATS = Collections.unmodifiableList(l);
	}

	/**
	 * @return New XML document from the default document builder factory.
	 */
	private static Document createDocument() {
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		return builder.newDocument();
	}

	/**
	 * @param key Fully qualified element name.
	 */
	public static Element createElement(final QName key) {
		return SardineUtil.createDocument().createElementNS(key.getNamespaceURI(), key.getPrefix() + ":" + key.getLocalPart());
	}

	/**
	 * @return A new marshaller
	 * @throws IOException When there is a JAXB error
	 */
	private static Marshaller createMarshaller() {
		try {
			return SardineUtil.JAXB_CONTEXT.createMarshaller();
		} catch (final JAXBException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * @param key Local element name.
	 */
	public static QName createQNameWithCustomNamespace(final String key) {
		return new QName(SardineUtil.CUSTOM_NAMESPACE_URI, key, SardineUtil.CUSTOM_NAMESPACE_PREFIX);
	}

	/**
	 * @param key Local element name.
	 */
	public static QName createQNameWithDefaultNamespace(final String key) {
		return new QName(SardineUtil.DEFAULT_NAMESPACE_URI, key, SardineUtil.DEFAULT_NAMESPACE_PREFIX);
	}

	/**
	 * Creates an {@link Unmarshaller} from the {@link SardineUtil#JAXB_CONTEXT}. Note: the unmarshaller is not thread safe, so it must be created for every
	 * request.
	 *
	 * @return A new unmarshaller
	 * @throws IOException When there is a JAXB error
	 */
	private static Unmarshaller createUnmarshaller() {
		try {
			return SardineUtil.JAXB_CONTEXT.createUnmarshaller();
		} catch (final JAXBException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * Loops over all the possible date formats and tries to find the right one.
	 *
	 * @param value ISO date string
	 * @return Null if there is a parsing failure
	 */
	public static Date parseDate(final String value) {
		if (value == null) {
			return null;
		}
		Date date = null;
		for (int i = 0; i < SardineUtil.DATETIME_FORMATS.size(); i++) {
			final ThreadLocal<SimpleDateFormat> format = SardineUtil.DATETIME_FORMATS.get(i);
			SimpleDateFormat sdf = format.get();
			if (sdf == null) {
				sdf = new SimpleDateFormat(SardineUtil.SUPPORTED_DATE_FORMATS[i], Locale.US);
				sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
				format.set(sdf);
			}
			try {
				date = sdf.parse(value);
				break;
			} catch (final ParseException e) {
				// We loop through this until we found a valid one.
			}
		}
		return date;
	}

	/** */
	public static List<QName> toQName(final List<String> removeProps) {
		if (removeProps == null) {
			return Collections.emptyList();
		}
		final List<QName> result = new ArrayList<QName>(removeProps.size());
		for (final String entry : removeProps) {
			result.add(SardineUtil.createQNameWithCustomNamespace(entry));
		}
		return result;
	}

	/** */
	public static Map<QName, String> toQName(final Map<String, String> setProps) {
		if (setProps == null) {
			return Collections.emptyMap();
		}
		final Map<QName, String> result = new HashMap<QName, String>(setProps.size());
		for (final Map.Entry<String, String> entry : setProps.entrySet()) {
			result.put(SardineUtil.createQNameWithCustomNamespace(entry.getKey()), entry.getValue());
		}
		return result;
	}

	/**
	 * @param jaxbElement An object from the model
	 * @return The XML string for the WebDAV request
	 * @throws IOException When there is a JAXB error
	 */
	public static String toXml(final Object jaxbElement) {
		final StringWriter writer = new StringWriter();
		try {
			final Marshaller marshaller = SardineUtil.createMarshaller();
			marshaller.marshal(jaxbElement, writer);
		} catch (final JAXBException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		return writer.toString();
	}

	@SuppressWarnings("unchecked")
	public static <T> T unmarshal(final InputStream in) throws IOException {
		final Unmarshaller unmarshaller = SardineUtil.createUnmarshaller();
		try {
			final XMLReader reader = XMLReaderFactory.createXMLReader();
			try {
				reader.setFeature("http://xml.org/sax/features/external-general-entities", Boolean.FALSE);
			} catch (final SAXException e) {
				; // Not all parsers will support this attribute
			}
			try {
				reader.setFeature("http://xml.org/sax/features/external-parameter-entities", Boolean.FALSE);
			} catch (final SAXException e) {
				; // Not all parsers will support this attribute
			}
			try {
				reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", Boolean.FALSE);
			} catch (final SAXException e) {
				; // Not all parsers will support this attribute
			}
			try {
				reader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
			} catch (final SAXException e) {
				; // Not all parsers will support this attribute
			}
			return (T) unmarshaller.unmarshal(new SAXSource(reader, new InputSource(in)));
		} catch (final SAXException e) {
			throw new RuntimeException(e.getMessage(), e);
		} catch (final JAXBException e) {
			// Server does not return any valid WebDAV XML that matches our JAXB context
			final IOException failure = new IOException("Not a valid DAV response");
			// Backward compatibility
			failure.initCause(e);
			throw failure;
		}
	}

	private SardineUtil() {
	}
}
