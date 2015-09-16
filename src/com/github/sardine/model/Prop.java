//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2013.05.31 at 06:14:58 PM MSK
//

package com.github.sardine.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.w3c.dom.Element;

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
 *       &lt;all>
 *         &lt;element ref="{DAV:}creationdate" minOccurs="0"/>
 *         &lt;element ref="{DAV:}displayname" minOccurs="0"/>
 *         &lt;element ref="{DAV:}getcontentlanguage" minOccurs="0"/>
 *         &lt;element ref="{DAV:}getcontentlength" minOccurs="0"/>
 *         &lt;element ref="{DAV:}getcontenttype" minOccurs="0"/>
 *         &lt;element ref="{DAV:}getetag" minOccurs="0"/>
 *         &lt;element ref="{DAV:}getlastmodified" minOccurs="0"/>
 *         &lt;element ref="{DAV:}lockdiscovery" minOccurs="0"/>
 *         &lt;element ref="{DAV:}resourcetype" minOccurs="0"/>
 *         &lt;element ref="{DAV:}supportedlock" minOccurs="0"/>
 *         &lt;element ref="{DAV:}quota-available-bytes" minOccurs="0"/>
 *         &lt;element ref="{DAV:}quota-used-bytes" minOccurs="0"/>
 *         &lt;any processContents='skip' namespace='##other' maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {

})
@XmlRootElement(name = "prop")
public class Prop {

	protected Creationdate creationdate;
	protected Displayname displayname;
	protected Getcontentlanguage getcontentlanguage;
	protected Getcontentlength getcontentlength;
	protected Getcontenttype getcontenttype;
	protected Getetag getetag;
	protected Getlastmodified getlastmodified;
	protected Lockdiscovery lockdiscovery;
	protected Resourcetype resourcetype;
	protected Supportedlock supportedlock;
	@XmlElement(name = "quota-available-bytes")
	protected QuotaAvailableBytes quotaAvailableBytes;
	@XmlElement(name = "quota-used-bytes")
	protected QuotaUsedBytes quotaUsedBytes;
	@XmlAnyElement
	protected List<Element> any;

	// ACL elements
	private Owner owner;
	private Group group;
	private Acl acl;
	@XmlElement(name = "principal-collection-set")
	private PrincipalCollectionSet principalCollectionSet;
	@XmlElement(name = "principal-URL")
	private PrincipalURL principalURL;

	public Acl getAcl() {
		return this.acl;
	}

	/**
	 * Gets the value of the any property.
	 *
	 * <p>
	 * This accessor method returns a reference to the live list, not a snapshot. Therefore any modification you make to the returned list will be present
	 * inside the JAXB object. This is why there is not a <CODE>set</CODE> method for the any property.
	 *
	 * <p>
	 * For example, to add a new item, do as follows:
	 *
	 * <pre>
	 * getAny().add(newItem);
	 * </pre>
	 *
	 *
	 * <p>
	 * Objects of the following type(s) are allowed in the list {@link Element }
	 *
	 *
	 */
	public List<Element> getAny() {
		if (this.any == null) {
			this.any = new ArrayList<Element>();
		}
		return this.any;
	}

	/**
	 * Gets the value of the creationdate property.
	 *
	 * @return possible object is {@link Creationdate }
	 *
	 */
	public Creationdate getCreationdate() {
		return this.creationdate;
	}

	/**
	 * Gets the value of the displayname property.
	 *
	 * @return possible object is {@link Displayname }
	 *
	 */
	public Displayname getDisplayname() {
		return this.displayname;
	}

	/**
	 * Gets the value of the getcontentlanguage property.
	 *
	 * @return possible object is {@link Getcontentlanguage }
	 *
	 */
	public Getcontentlanguage getGetcontentlanguage() {
		return this.getcontentlanguage;
	}

	/**
	 * Gets the value of the getcontentlength property.
	 *
	 * @return possible object is {@link Getcontentlength }
	 *
	 */
	public Getcontentlength getGetcontentlength() {
		return this.getcontentlength;
	}

	/**
	 * Gets the value of the getcontenttype property.
	 *
	 * @return possible object is {@link Getcontenttype }
	 *
	 */
	public Getcontenttype getGetcontenttype() {
		return this.getcontenttype;
	}

	/**
	 * Gets the value of the getetag property.
	 *
	 * @return possible object is {@link Getetag }
	 *
	 */
	public Getetag getGetetag() {
		return this.getetag;
	}

	/**
	 * Gets the value of the getlastmodified property.
	 *
	 * @return possible object is {@link Getlastmodified }
	 *
	 */
	public Getlastmodified getGetlastmodified() {
		return this.getlastmodified;
	}

	public Group getGroup() {
		return this.group;
	}

	/**
	 * Gets the value of the lockdiscovery property.
	 *
	 * @return possible object is {@link Lockdiscovery }
	 *
	 */
	public Lockdiscovery getLockdiscovery() {
		return this.lockdiscovery;
	}

	public Owner getOwner() {
		return this.owner;
	}

	public PrincipalCollectionSet getPrincipalCollectionSet() {
		return this.principalCollectionSet;
	}

	public PrincipalURL getPrincipalURL() {
		return this.principalURL;
	}

	/**
	 * Gets the value of the quotaAvailableBytes property.
	 *
	 * @return possible object is {@link QuotaAvailableBytes }
	 *
	 */
	public QuotaAvailableBytes getQuotaAvailableBytes() {
		return this.quotaAvailableBytes;
	}

	/**
	 * Gets the value of the quotaUsedBytes property.
	 *
	 * @return possible object is {@link QuotaUsedBytes }
	 *
	 */
	public QuotaUsedBytes getQuotaUsedBytes() {
		return this.quotaUsedBytes;
	}

	/**
	 * Gets the value of the resourcetype property.
	 *
	 * @return possible object is {@link Resourcetype }
	 *
	 */
	public Resourcetype getResourcetype() {
		return this.resourcetype;
	}

	/**
	 * Gets the value of the supportedlock property.
	 *
	 * @return possible object is {@link Supportedlock }
	 *
	 */
	public Supportedlock getSupportedlock() {
		return this.supportedlock;
	}

	public void setAcl(final Acl acl) {
		this.acl = acl;
	}

	/**
	 * Sets the value of the creationdate property.
	 *
	 * @param value allowed object is {@link Creationdate }
	 *
	 */
	public void setCreationdate(final Creationdate value) {
		this.creationdate = value;
	}

	/**
	 * Sets the value of the displayname property.
	 *
	 * @param value allowed object is {@link Displayname }
	 *
	 */
	public void setDisplayname(final Displayname value) {
		this.displayname = value;
	}

	/**
	 * Sets the value of the getcontentlanguage property.
	 *
	 * @param value allowed object is {@link Getcontentlanguage }
	 *
	 */
	public void setGetcontentlanguage(final Getcontentlanguage value) {
		this.getcontentlanguage = value;
	}

	/**
	 * Sets the value of the getcontentlength property.
	 *
	 * @param value allowed object is {@link Getcontentlength }
	 *
	 */
	public void setGetcontentlength(final Getcontentlength value) {
		this.getcontentlength = value;
	}

	/**
	 * Sets the value of the getcontenttype property.
	 *
	 * @param value allowed object is {@link Getcontenttype }
	 *
	 */
	public void setGetcontenttype(final Getcontenttype value) {
		this.getcontenttype = value;
	}

	/**
	 * Sets the value of the getetag property.
	 *
	 * @param value allowed object is {@link Getetag }
	 *
	 */
	public void setGetetag(final Getetag value) {
		this.getetag = value;
	}

	/**
	 * Sets the value of the getlastmodified property.
	 *
	 * @param value allowed object is {@link Getlastmodified }
	 *
	 */
	public void setGetlastmodified(final Getlastmodified value) {
		this.getlastmodified = value;
	}

	public void setGroup(final Group group) {
		this.group = group;
	}

	/**
	 * Sets the value of the lockdiscovery property.
	 *
	 * @param value allowed object is {@link Lockdiscovery }
	 *
	 */
	public void setLockdiscovery(final Lockdiscovery value) {
		this.lockdiscovery = value;
	}

	public void setOwner(final Owner owner) {
		this.owner = owner;
	}

	public void setPrincipalCollectionSet(final PrincipalCollectionSet principalCollectionSet) {
		this.principalCollectionSet = principalCollectionSet;
	}

	public void setPrincipalURL(final PrincipalURL principalURL) {
		this.principalURL = principalURL;
	}

	/**
	 * Sets the value of the quotaAvailableBytes property.
	 *
	 * @param value allowed object is {@link QuotaAvailableBytes }
	 *
	 */
	public void setQuotaAvailableBytes(final QuotaAvailableBytes value) {
		this.quotaAvailableBytes = value;
	}

	/**
	 * Sets the value of the quotaUsedBytes property.
	 *
	 * @param value allowed object is {@link QuotaUsedBytes }
	 *
	 */
	public void setQuotaUsedBytes(final QuotaUsedBytes value) {
		this.quotaUsedBytes = value;
	}

	/**
	 * Sets the value of the resourcetype property.
	 *
	 * @param value allowed object is {@link Resourcetype }
	 *
	 */
	public void setResourcetype(final Resourcetype value) {
		this.resourcetype = value;
	}

	/**
	 * Sets the value of the supportedlock property.
	 *
	 * @param value allowed object is {@link Supportedlock }
	 *
	 */
	public void setSupportedlock(final Supportedlock value) {
		this.supportedlock = value;
	}
}
