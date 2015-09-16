/* copyright(c) 2014 SAS Institute, Cary NC 27513 Created on Oct 23, 2014 */
package com.github.sardine.model;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

/**
 * <p>
 * Java class for anonymous complex type.
 * <p>
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 *     &lt;element name="searchrequest">
 *         &lt;complexType>
 *             &lt;any processContents="skip" namespace="##other" minOccurs="1" maxOccurs="1" />
 *         &lt;/complexType>
 *     &lt;/element>
 * </pre>
 */
@XmlType(name = "")
@XmlRootElement(name = "searchrequest")
public class SearchRequest {
	private String language;

	private String query;

	public SearchRequest() {
		this.language = "davbasic";
		this.query = "";
	}

	public SearchRequest(final String language, final String query) {
		this.language = language;
		this.query = query;
	}

	@XmlAnyElement
	public JAXBElement<String> getElement() {
		return new JAXBElement<String>(new QName("DAV:", this.language), String.class, this.query);
	}

	public final String getLanguage() {
		return this.language;
	}

	public final String getQuery() {
		return this.query;
	}

	@XmlTransient
	public void setLanguage(final String language) {
		this.language = language;
	}

	@XmlTransient
	public void setQuery(final String query) {
		this.query = query;
	}
}
