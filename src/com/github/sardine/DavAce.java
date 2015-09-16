package com.github.sardine;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import com.github.sardine.model.Ace;
import com.github.sardine.model.All;
import com.github.sardine.model.Authenticated;
import com.github.sardine.model.Bind;
import com.github.sardine.model.Deny;
import com.github.sardine.model.Grant;
import com.github.sardine.model.Principal;
import com.github.sardine.model.Privilege;
import com.github.sardine.model.Property;
import com.github.sardine.model.Read;
import com.github.sardine.model.ReadAcl;
import com.github.sardine.model.ReadCurrentUserPrivilegeSet;
import com.github.sardine.model.Self;
import com.github.sardine.model.SimplePrivilege;
import com.github.sardine.model.UnBind;
import com.github.sardine.model.Unauthenticated;
import com.github.sardine.model.Unlock;
import com.github.sardine.model.Write;
import com.github.sardine.model.WriteContent;
import com.github.sardine.model.WriteProperties;
import com.github.sardine.util.SardineUtil;

/**
 * An Access control element (ACE) either grants or denies a particular set of (non- abstract) privileges for a particular principal.
 *
 * @author David Delbecq
 */
public class DavAce {
	/**
	 * A "principal" is a distinct human or computational actor that initiates access to network resources. In this protocol, a principal is an HTTP resource
	 * that represents such an actor.
	 * <p/>
	 * The DAV:principal element identifies the principal to which this ACE applies.
	 * <p/>
	 * <!ELEMENT principal (href | all | authenticated | unauthenticated | property | self)>
	 * <p/>
	 * The current user matches DAV:href only if that user is authenticated as being (or being a member of) the principal identified by the URL contained by
	 * that DAV:href.
	 * <p/>
	 * Either a href or one of all,authenticated,unauthenticated,property,self.
	 * <p/>
	 * DAV:property not supported.
	 */
	private final DavPrincipal principal;

	/**
	 * List of granted privileges.
	 */
	private final List<String> granted;

	/**
	 * List of denied privileges.
	 */
	private final List<String> denied;

	/**
	 * The presence of a DAV:inherited element indicates that this ACE is inherited from another resource that is identified by the URL contained in a DAV:href
	 * element. An inherited ACE cannot be modified directly, but instead the ACL on the resource from which it is inherited must be modified.
	 * <p/>
	 * Null or a href to the inherited resource.
	 */
	private final String inherited;

	private final boolean isprotected;

	public DavAce(final Ace ace) {
		this.principal = new DavPrincipal(ace.getPrincipal());

		this.granted = new ArrayList<String>();
		this.denied = new ArrayList<String>();
		if (ace.getGrant() != null) {
			for (final Privilege p : ace.getGrant().getPrivilege()) {
				for (final Object o : p.getContent()) {
					if (o instanceof SimplePrivilege) {
						this.granted.add(o.getClass().getAnnotation(XmlRootElement.class).name());
					}
				}
			}
		}
		if (ace.getDeny() != null) {
			for (final Privilege p : ace.getDeny().getPrivilege()) {
				for (final Object o : p.getContent()) {
					if (o instanceof SimplePrivilege) {
						this.denied.add(o.getClass().getAnnotation(XmlRootElement.class).name());
					}
				}
			}
		}
		if (ace.getInherited() != null) {
			this.inherited = ace.getInherited().getHref();
		} else {
			this.inherited = null;
		}
		this.isprotected = (ace.getProtected() != null);
	}

	public DavAce(final DavPrincipal principal) {
		this.principal = principal;
		this.granted = new ArrayList<String>();
		this.denied = new ArrayList<String>();
		this.inherited = null;
		this.isprotected = false;
	}

	public List<String> getDenied() {
		return this.denied;
	}

	public List<String> getGranted() {
		return this.granted;
	}

	public String getInherited() {
		return this.inherited;
	}

	public DavPrincipal getPrincipal() {
		return this.principal;
	}

	public boolean isProtected() {
		return this.isprotected;
	}

	public Ace toModel() {
		final Ace ace = new Ace();
		final Principal p = new Principal();
		switch (this.principal.getPrincipalType()) {
		case HREF:
			p.setHref(this.principal.getValue());
			break;
		case PROPERTY:
			p.setProperty(new Property());
			p.getProperty().setProperty(SardineUtil.createElement(this.principal.getProperty()));
			break;
		case KEY:
			if (DavPrincipal.KEY_ALL.equals(this.principal.getValue())) {
				p.setAll(new All());
			} else if (DavPrincipal.KEY_AUTHENTICATED.equals(this.principal.getValue())) {
				p.setAuthenticated(new Authenticated());
			} else if (DavPrincipal.KEY_UNAUTHENTICATED.equals(this.principal.getValue())) {
				p.setUnauthenticated(new Unauthenticated());
			} else if (DavPrincipal.KEY_SELF.equals(this.principal.getValue())) {
				p.setSelf(new Self());
			}
		}
		ace.setPrincipal(p);
		if ((this.granted != null) && (this.granted.size() > 0)) {
			ace.setGrant(new Grant());
			ace.getGrant().setPrivilege(toPrivilege(this.granted));
		}
		if ((this.denied != null) && (this.denied.size() > 0)) {
			ace.setDeny(new Deny());
			ace.getDeny().setPrivilege(toPrivilege(this.denied));
		}
		return ace;
	}

	private List<Privilege> toPrivilege(final List<String> rights) {
		final List<Privilege> privileges = new ArrayList<Privilege>();
		for (final String right : rights) {
			final Privilege p = new Privilege();
			if ("all".equals(right)) {
				p.getContent().add(new All());
			} else if ("bind".equals(right)) {
				p.getContent().add(new Bind());
			} else if ("read".equals(right)) {
				p.getContent().add(new Read());
			} else if ("read-acl".equals(right)) {
				p.getContent().add(new ReadAcl());
			} else if ("read-current-user-privilege-set".equals(right)) {
				p.getContent().add(new ReadCurrentUserPrivilegeSet());
			} else if ("unbind".equals(right)) {
				p.getContent().add(new UnBind());
			} else if ("unlock".equals(right)) {
				p.getContent().add(new Unlock());
			} else if ("write".equals(right)) {
				p.getContent().add(new Write());
			} else if ("write-content".equals(right)) {
				p.getContent().add(new WriteContent());
			} else if ("write-properties".equals(right)) {
				p.getContent().add(new WriteProperties());
			} else {
				continue;
			}
			privileges.add(p);
		}
		return privileges;
	}
}
