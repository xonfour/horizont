package com.github.sardine.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
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
 *   &lt;D:owner>
 *           &lt;D:href>http://www.example.com/acl/users/gstein&lt;/D:href>
 * &lt;/D:owner>
 *
 *
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "principal", "grant", "deny", "inherited", "protected1" })
@XmlRootElement(name = "ace")
public class Ace {

	private Principal principal;
	private Grant grant;
	private Deny deny;
	private Inherited inherited;

	@XmlElement(name = "protected")
	private Protected protected1;

	public Deny getDeny() {
		return this.deny;
	}

	public Grant getGrant() {
		return this.grant;
	}

	public Inherited getInherited() {
		return this.inherited;
	}

	public Principal getPrincipal() {
		return this.principal;
	}

	public Protected getProtected() {
		return this.protected1;
	}

	public void setDeny(final Deny deny) {
		this.deny = deny;
	}

	public void setGrant(final Grant grant) {
		this.grant = grant;
	}

	public void setInherited(final Inherited inherited) {
		this.inherited = inherited;
	}

	public void setPrincipal(final Principal principal) {
		this.principal = principal;
	}

	public void setProtected(final Protected protected1) {
		this.protected1 = protected1;
	}

}
