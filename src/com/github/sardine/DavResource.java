/* Copyright 2009-2011 Jon Stevens et al. Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under the License. */

package com.github.sardine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.github.sardine.model.Creationdate;
import com.github.sardine.model.Displayname;
import com.github.sardine.model.Getcontentlanguage;
import com.github.sardine.model.Getcontentlength;
import com.github.sardine.model.Getcontenttype;
import com.github.sardine.model.Getetag;
import com.github.sardine.model.Getlastmodified;
import com.github.sardine.model.Propstat;
import com.github.sardine.model.Resourcetype;
import com.github.sardine.model.Response;
import com.github.sardine.util.SardineUtil;

/**
 * Describes a resource on a remote server. This could be a directory or an actual file.
 *
 * @author jonstevens
 */
public class DavResource {
	private static Logger log = LoggerFactory.getLogger(DavResource.class);

	/**
	 * The default content-type if {@link Getcontenttype} is not set in the {@link com.github.sardine.model.Multistatus} response.
	 */
	public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

	/**
	 * The default content-lenght if {@link Getcontentlength} is not set in the {@link com.github.sardine.model.Multistatus} response.
	 */
	public static final long DEFAULT_CONTENT_LENGTH = -1;

	/**
	 * content-type for {@link com.github.sardine.model.Collection}.
	 */
	public static final String HTTPD_UNIX_DIRECTORY_CONTENT_TYPE = "httpd/unix-directory";

	/**
	 * Path component seperator
	 */
	private static final String SEPARATOR = "/";

	private final URI href;
	private final Date creation;
	private final Date modified;
	private final String contentType;
	private final String etag;
	private final String displayName;
	private final String contentLanguage;
	private final Long contentLength;
	private final Map<QName, String> customProps;

	/**
	 * Converts the given {@link Response} to a {@link com.github.sardine.DavResource}.
	 *
	 * @param response The response complex type of the multistatus
	 * @throws java.net.URISyntaxException If parsing the href from the response element fails
	 */
	public DavResource(final Response response) throws URISyntaxException {
		this.href = new URI(response.getHref().get(0));
		this.creation = SardineUtil.parseDate(getCreationDate(response));
		this.modified = SardineUtil.parseDate(getModifiedDate(response));
		this.contentType = this.getContentType(response);
		this.contentLength = this.getContentLength(response);
		this.etag = this.getEtag(response);
		this.displayName = this.getDisplayName(response);
		this.contentLanguage = this.getContentLanguage(response);
		this.customProps = this.getCustomProps(response);
	}

	/**
	 * Represents a webdav response block.
	 *
	 * @param href URI to the resource as returned from the server
	 * @throws java.net.URISyntaxException If parsing the href from the response element fails
	 */
	protected DavResource(final String href, final Date creation, final Date modified, final String contentType, final Long contentLength, final String etag, final String displayName, final String contentLanguage, final Map<QName, String> customProps) throws URISyntaxException {
		this.href = new URI(href);
		this.creation = creation;
		this.modified = modified;
		this.contentType = contentType;
		this.contentLength = contentLength;
		this.etag = etag;
		this.displayName = displayName;
		this.contentLanguage = contentLanguage;
		this.customProps = customProps;
	}

	/**
	 * @return Content language
	 */
	public String getContentLanguage() {
		return this.contentLanguage;
	}

	/**
	 * Retrieves the content-language from prop.
	 *
	 * @param response The response complex type of the multistatus
	 * @return the content language; {@code null} if it is not avaialble
	 */
	private String getContentLanguage(final Response response) {
		// Make sure that directories have the correct content type.
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			if (propstat.getProp() != null) {
				final Resourcetype resourcetype = propstat.getProp().getResourcetype();
				if ((resourcetype != null) && (resourcetype.getCollection() != null)) {
					// Need to correct the contentType to identify as a directory.
					return DavResource.HTTPD_UNIX_DIRECTORY_CONTENT_TYPE;
				} else {
					final Getcontentlanguage gtl = propstat.getProp().getGetcontentlanguage();
					if ((gtl != null) && (gtl.getContent().size() == 1)) {
						return gtl.getContent().get(0);
					}
				}
			}
		}
		return null;
	}

	/**
	 * @return Size
	 */
	public Long getContentLength() {
		return this.contentLength;
	}

	/**
	 * Retrieves content-length from props. If it is not available return {@link #DEFAULT_CONTENT_LENGTH}.
	 *
	 * @param response The response complex type of the multistatus
	 * @return contentlength
	 */
	private long getContentLength(final Response response) {
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return DavResource.DEFAULT_CONTENT_LENGTH;
		}
		for (final Propstat propstat : list) {
			if (propstat.getProp() != null) {
				final Getcontentlength gcl = propstat.getProp().getGetcontentlength();
				if ((gcl != null) && (gcl.getContent().size() == 1)) {
					try {
						return Long.parseLong(gcl.getContent().get(0));
					} catch (final NumberFormatException e) {
						DavResource.log.warn(String.format("Failed to parse content length %s", gcl.getContent().get(0)));
					}
				}
			}
		}
		return DavResource.DEFAULT_CONTENT_LENGTH;
	}

	/**
	 * @return MIME Type
	 */
	public String getContentType() {
		return this.contentType;
	}

	/**
	 * Retrieves the content-type from prop or set it to {@link #DEFAULT_CONTENT_TYPE}. If isDirectory always set the content-type to
	 * {@link #HTTPD_UNIX_DIRECTORY_CONTENT_TYPE}.
	 *
	 * @param response The response complex type of the multistatus
	 * @return the content type.
	 */
	private String getContentType(final Response response) {
		// Make sure that directories have the correct content type.
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			if (propstat.getProp() != null) {
				final Resourcetype resourcetype = propstat.getProp().getResourcetype();
				if ((resourcetype != null) && (resourcetype.getCollection() != null)) {
					// Need to correct the contentType to identify as a directory.
					return DavResource.HTTPD_UNIX_DIRECTORY_CONTENT_TYPE;
				} else {
					final Getcontenttype gtt = propstat.getProp().getGetcontenttype();
					if ((gtt != null) && (gtt.getContent().size() == 1)) {
						return gtt.getContent().get(0);
					}
				}
			}
		}
		return DavResource.DEFAULT_CONTENT_TYPE;
	}

	/**
	 * @return Timestamp
	 */
	public Date getCreation() {
		return this.creation;
	}

	/**
	 * Retrieves creationdate from props. If it is not available return null.
	 *
	 * @param response The response complex type of the multistatus
	 * @return Null if not found in props
	 */
	private String getCreationDate(final Response response) {
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			if (propstat.getProp() != null) {
				final Creationdate gcd = propstat.getProp().getCreationdate();
				if ((gcd != null) && (gcd.getContent().size() == 1)) {
					return gcd.getContent().get(0);
				}
			}
		}
		return null;
	}

	/**
	 * @return Additional metadata. This implementation does not take namespaces into account.
	 */
	public Map<String, String> getCustomProps() {
		final Map<String, String> local = new HashMap<String, String>();
		final Map<QName, String> properties = getCustomPropsNS();
		for (final QName key : properties.keySet()) {
			local.put(key.getLocalPart(), properties.get(key));
		}
		return local;
	}

	/**
	 * Creates a simple complex Map from the given custom properties of a response. This implementation does take namespaces into account.
	 *
	 * @param response The response complex type of the multistatus
	 * @return Custom properties
	 */
	private Map<QName, String> getCustomProps(final Response response) {
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		final Map<QName, String> customPropsMap = new HashMap<QName, String>();
		for (final Propstat propstat : list) {
			if (propstat.getProp() != null) {
				final List<Element> props = propstat.getProp().getAny();
				for (final Element element : props) {
					final String namespace = element.getNamespaceURI();
					if (namespace == null) {
						customPropsMap.put(new QName(SardineUtil.DEFAULT_NAMESPACE_URI, element.getLocalName(), SardineUtil.DEFAULT_NAMESPACE_PREFIX), element.getTextContent());
					} else {
						if (element.getPrefix() == null) {
							customPropsMap.put(new QName(element.getNamespaceURI(), element.getLocalName()), element.getTextContent());
						} else {
							customPropsMap.put(new QName(element.getNamespaceURI(), element.getLocalName(), element.getPrefix()), element.getTextContent());
						}
					}

				}
			}
		}
		return customPropsMap;
	}

	/**
	 * @return Additional metadata with namespace informations
	 */
	public Map<QName, String> getCustomPropsNS() {
		return this.customProps;
	}

	/**
	 * @return Display name
	 */
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Retrieves displayName from props.
	 *
	 * @param response The response complex type of the multistatus
	 * @return the display name; {@code null} if it is not available
	 */
	private String getDisplayName(final Response response) {
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			if (propstat.getProp() != null) {
				final Displayname dn = propstat.getProp().getDisplayname();
				if ((dn != null) && (dn.getContent().size() == 1)) {
					return dn.getContent().get(0);
				}
			}
		}
		return null;
	}

	/**
	 * @return Fingerprint
	 */
	public String getEtag() {
		return this.etag;
	}

	/**
	 * Retrieves content-length from props. If it is not available return {@link #DEFAULT_CONTENT_LENGTH}.
	 *
	 * @param response The response complex type of the multistatus
	 * @return contentlength
	 */
	private String getEtag(final Response response) {
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			if (propstat.getProp() != null) {
				final Getetag e = propstat.getProp().getGetetag();
				if ((e != null) && (e.getContent().size() == 1)) {
					return e.getContent().get(0);
				}
			}
		}
		return null;
	}

	/**
	 * @return URI of the resource.
	 */
	public URI getHref() {
		return this.href;
	}

	/**
	 * @return Timestamp
	 */
	public Date getModified() {
		return this.modified;
	}

	/**
	 * Retrieves modifieddate from props. If it is not available return null.
	 *
	 * @param response The response complex type of the multistatus
	 * @return Null if not found in props
	 */
	private String getModifiedDate(final Response response) {
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			if (propstat.getProp() != null) {
				final Getlastmodified glm = propstat.getProp().getGetlastmodified();
				if ((glm != null) && (glm.getContent().size() == 1)) {
					return glm.getContent().get(0);
				}
			}
		}
		return null;
	}

	/**
	 * Last path component.
	 *
	 * @return The name of the resource URI decoded. An empty string if this resource denotes a directory.
	 * @see #getHref()
	 */
	public String getName() {
		String path = this.href.getPath();
		try {
			if (path.endsWith(DavResource.SEPARATOR)) {
				path = path.substring(0, path.length() - 1);
			}
			return path.substring(path.lastIndexOf('/') + 1);
		} catch (final StringIndexOutOfBoundsException e) {
			DavResource.log.warn(String.format("Failed to parse name from path %s", path));
			return null;
		}
	}

	/**
	 * @return Path component of the URI of the resource.
	 * @see #getHref()
	 */
	public String getPath() {
		return this.href.getPath();
	}

	/**
	 * Implementation assumes that every resource with a content type of <code>httpd/unix-directory</code> is a directory.
	 *
	 * @return True if this resource denotes a directory
	 */
	public boolean isDirectory() {
		return DavResource.HTTPD_UNIX_DIRECTORY_CONTENT_TYPE.equals(this.contentType);
	}

	/**
	 * @see #getPath()
	 */
	@Override
	public String toString() {
		return getPath();
	}
}
