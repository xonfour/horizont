//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.1.4-10/27/2009 06:09 PM(mockbuild)-fcs
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2009.12.23 at 06:27:19 PM PST
//

package com.github.sardine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for anonymous complex type.
 *
 * <p>
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;any/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "any" })
@XmlRootElement(name = "error")
public class Error {

	@XmlAnyElement(lax = true)
	private Object any;

	/**
	 * Gets the value of the any property.
	 *
	 * @return possible object is {@link Object }
	 *
	 */
	public Object getAny() {
		return this.any;
	}

	/**
	 * Sets the value of the any property.
	 *
	 * @param value allowed object is {@link Object }
	 *
	 */
	public void setAny(final Object value) {
		this.any = value;
	}

}
