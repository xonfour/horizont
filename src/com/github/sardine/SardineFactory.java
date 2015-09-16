package com.github.sardine;

import java.net.ProxySelector;

import com.github.sardine.impl.SardineImpl;

/**
 * The perfect name for a class. Provides the static methods for working with the Sardine interface.
 *
 * @author jonstevens
 */
public final class SardineFactory {
	/**
	 * Default begin() for when you don't need anything but no authentication and default settings for SSL.
	 */
	public static Sardine begin() {
		return SardineFactory.begin(null, null);
	}

	/**
	 * Pass in a HTTP Auth username/password for being used with all connections
	 *
	 * @param username Use in authentication header credentials
	 * @param password Use in authentication header credentials
	 */
	public static Sardine begin(final String username, final String password) {
		return SardineFactory.begin(username, password, null);
	}

	/**
	 * @param username Use in authentication header credentials
	 * @param password Use in authentication header credentials
	 * @param proxy Proxy configuration
	 */
	public static Sardine begin(final String username, final String password, final ProxySelector proxy) {
		return new SardineImpl(username, password, proxy);
	}

	private SardineFactory() {
	}
}