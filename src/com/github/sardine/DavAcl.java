package com.github.sardine;

import java.util.ArrayList;
import java.util.List;

import com.github.sardine.model.Ace;
import com.github.sardine.model.Acl;
import com.github.sardine.model.Group;
import com.github.sardine.model.Owner;
import com.github.sardine.model.Propstat;
import com.github.sardine.model.Response;

/**
 * Describe access rights on a remote server. An access control list (ACL) is a list of access control elements that define access control to a particular
 * resource.
 *
 * @author David Delbecq
 */
public class DavAcl {
	/**
	 * The value of the DAV:owner property is a single DAV:href XML element containing the URL of the principal that owns this resource.
	 */
	private final String owner;

	/**
	 * This property identifies a particular principal as being the "group" of the resource. This property is commonly found on repositories that implement the
	 * Unix privileges model.
	 */
	private final String group;

	/**
	 *
	 */
	private final List<DavAce> aces;

	public DavAcl(final Response response) {
		this.owner = getOwner(response);
		this.group = getGroup(response);
		this.aces = getAces(response);
	}

	public List<DavAce> getAces() {
		return this.aces;
	}

	private List<DavAce> getAces(final Response response) {
		final ArrayList<DavAce> result = new ArrayList<DavAce>();
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			final Acl a = propstat.getProp().getAcl();
			if ((a != null) && (a.getAce() != null)) {
				for (final Ace ace : a.getAce()) {
					result.add(new DavAce(ace));
				}
			}
		}
		return result;
	}

	public String getGroup() {
		return this.group;
	}

	private String getGroup(final Response response) {
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			final Group o = propstat.getProp().getGroup();
			if (o != null) {
				if (o.getHref() != null) {
					return o.getHref();
				}
			}
		}
		return null;
	}

	public String getOwner() {
		return this.owner;
	}

	private String getOwner(final Response response) {
		final List<Propstat> list = response.getPropstat();
		if (list.isEmpty()) {
			return null;
		}
		for (final Propstat propstat : list) {
			final Owner o = propstat.getProp().getOwner();
			if (o != null) {
				if (o.getUnauthenticated() != null) {
					return "unauthenticated";
				} else if (o.getHref() != null) {
					return o.getHref();
				}
			}
		}
		return null;
	}
}
