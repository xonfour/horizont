/* Copyright 2009-2011 Jon Stevens et al.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */

package com.github.sardine.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.protocol.RequestAcceptEncoding;
import org.apache.http.client.protocol.ResponseContentEncoding;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.github.sardine.DavAce;
import com.github.sardine.DavAcl;
import com.github.sardine.DavPrincipal;
import com.github.sardine.DavQuota;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.Version;
import com.github.sardine.impl.handler.ExistsResponseHandler;
import com.github.sardine.impl.handler.LockResponseHandler;
import com.github.sardine.impl.handler.MultiStatusResponseHandler;
import com.github.sardine.impl.handler.VoidResponseHandler;
import com.github.sardine.impl.io.ConsumingInputStream;
import com.github.sardine.impl.io.ContentLengthInputStream;
import com.github.sardine.impl.methods.HttpAcl;
import com.github.sardine.impl.methods.HttpCopy;
import com.github.sardine.impl.methods.HttpLock;
import com.github.sardine.impl.methods.HttpMkCol;
import com.github.sardine.impl.methods.HttpMove;
import com.github.sardine.impl.methods.HttpPropFind;
import com.github.sardine.impl.methods.HttpPropPatch;
import com.github.sardine.impl.methods.HttpSearch;
import com.github.sardine.impl.methods.HttpUnlock;
import com.github.sardine.model.Ace;
import com.github.sardine.model.Acl;
import com.github.sardine.model.Allprop;
import com.github.sardine.model.Displayname;
import com.github.sardine.model.Exclusive;
import com.github.sardine.model.Group;
import com.github.sardine.model.Lockinfo;
import com.github.sardine.model.Lockscope;
import com.github.sardine.model.Locktype;
import com.github.sardine.model.Multistatus;
import com.github.sardine.model.ObjectFactory;
import com.github.sardine.model.Owner;
import com.github.sardine.model.PrincipalCollectionSet;
import com.github.sardine.model.PrincipalURL;
import com.github.sardine.model.Prop;
import com.github.sardine.model.Propertyupdate;
import com.github.sardine.model.Propfind;
import com.github.sardine.model.Propstat;
import com.github.sardine.model.QuotaAvailableBytes;
import com.github.sardine.model.QuotaUsedBytes;
import com.github.sardine.model.Remove;
import com.github.sardine.model.Resourcetype;
import com.github.sardine.model.Response;
import com.github.sardine.model.SearchRequest;
import com.github.sardine.model.Set;
import com.github.sardine.model.Write;
import com.github.sardine.util.SardineUtil;

/**
 * Implementation of the Sardine interface. This is where the meat of the Sardine library lives.
 *
 * @author jonstevens
 */
@SuppressWarnings("deprecation")
public class SardineImpl implements Sardine {
	private static Logger log = LoggerFactory.getLogger(DavResource.class);

	private static final String UTF_8 = "UTF-8";

	/**
	 * HTTP client implementation
	 */
	private CloseableHttpClient client;

	/**
	 * HTTP client configuration
	 */
	private final HttpClientBuilder builder;

	/**
	 * Local context with authentication cache. Make sure the same context is used to execute logically related requests.
	 */
	private final HttpClientContext context = HttpClientContext.create();

	/**
	 * Access resources with no authentication
	 */
	public SardineImpl() {
		this.builder = configure(null, null);
		this.client = this.builder.build();
	}

	/**
	 * @param builder Custom client configuration
	 */
	public SardineImpl(final HttpClientBuilder builder) {
		this.builder = builder;
		this.client = this.builder.build();
	}

	/**
	 * @param builder Custom client configuration
	 * @param username Use in authentication header credentials
	 * @param password Use in authentication header credentials
	 */
	public SardineImpl(final HttpClientBuilder builder, final String username, final String password) {
		this.builder = builder;
		this.setCredentials(username, password);
		this.client = this.builder.build();
	}

	/**
	 * Supports standard authentication mechanisms
	 *
	 * @param username Use in authentication header credentials
	 * @param password Use in authentication header credentials
	 */
	public SardineImpl(final String username, final String password) {
		this.builder = configure(null, getCredentialsProvider(username, password, null, null));
		this.client = this.builder.build();
	}

	/**
	 * @param username Use in authentication header credentials
	 * @param password Use in authentication header credentials
	 * @param selector Proxy configuration
	 */
	public SardineImpl(final String username, final String password, final ProxySelector selector) {
		this.builder = configure(selector, getCredentialsProvider(username, password, null, null));
		this.client = this.builder.build();
	}

	/**
	 * Creates a client with all of the defaults.
	 *
	 * @param selector Proxy configuration or null
	 * @param credentials Authentication credentials or null
	 */
	protected HttpClientBuilder configure(final ProxySelector selector, final CredentialsProvider credentials) {
		final Registry<ConnectionSocketFactory> schemeRegistry = createDefaultSchemeRegistry();
		final HttpClientConnectionManager cm = createDefaultConnectionManager(schemeRegistry);
		String version = Version.getSpecification();
		if (version == null) {
			version = VersionInfo.UNAVAILABLE;
		}
		return HttpClients.custom().setUserAgent("Sardine/" + version).setDefaultCredentialsProvider(credentials).setRedirectStrategy(createDefaultRedirectStrategy()).setDefaultRequestConfig(RequestConfig.custom()
				// Only selectively enable this for PUT but not all entity enclosing methods
				.setExpectContinueEnabled(false).build()).setConnectionManager(cm).setRoutePlanner(createDefaultRoutePlanner(createDefaultSchemePortResolver(), selector));
	}

	@Override
	public void copy(final String sourceUrl, final String destinationUrl) throws IOException {
		copy(sourceUrl, destinationUrl, true);
	}

	@Override
	public void copy(final String sourceUrl, final String destinationUrl, final boolean overwrite) throws IOException {
		final HttpCopy copy = new HttpCopy(sourceUrl, destinationUrl, overwrite);
		this.execute(copy, new VoidResponseHandler());
	}

	/**
	 * Use fail fast connection manager when connections are not released properly.
	 *
	 * @param schemeRegistry Protocol registry
	 * @return Default connection manager
	 */
	protected HttpClientConnectionManager createDefaultConnectionManager(final Registry<ConnectionSocketFactory> schemeRegistry) {
		return new PoolingHttpClientConnectionManager(schemeRegistry);
	}

	protected SardineRedirectStrategy createDefaultRedirectStrategy() {
		return new SardineRedirectStrategy();
	}

	/**
	 * Override to provide proxy configuration
	 *
	 * @param resolver Protocol registry
	 * @param selector Proxy configuration
	 * @return ProxySelectorRoutePlanner configured with schemeRegistry and selector
	 */
	protected HttpRoutePlanner createDefaultRoutePlanner(final SchemePortResolver resolver, final ProxySelector selector) {
		return new SystemDefaultRoutePlanner(resolver, selector);
	}

	protected DefaultSchemePortResolver createDefaultSchemePortResolver() {
		return new DefaultSchemePortResolver();
	}

	/**
	 * Creates a new registry for default ports with socket factories.
	 */
	protected Registry<ConnectionSocketFactory> createDefaultSchemeRegistry() {
		return RegistryBuilder.<ConnectionSocketFactory> create().register("http", createDefaultSocketFactory()).register("https", createDefaultSecureSocketFactory()).build();
	}

	/**
	 * @return Default SSL socket factory
	 */
	protected ConnectionSocketFactory createDefaultSecureSocketFactory() {
		return SSLConnectionSocketFactory.getSocketFactory();
	}

	/**
	 * @return Default socket factory
	 */
	protected ConnectionSocketFactory createDefaultSocketFactory() {
		return PlainConnectionSocketFactory.getSocketFactory();
	}

	@Override
	public void createDirectory(final String url) throws IOException {
		final HttpMkCol mkcol = new HttpMkCol(url);
		this.execute(mkcol, new VoidResponseHandler());
	}

	@Override
	public void delete(final String url) throws IOException {
		final HttpDelete delete = new HttpDelete(url);
		this.execute(delete, new VoidResponseHandler());
	}

	/**
	 * Disable GZIP compression header.
	 */
	@Override
	public void disableCompression() {
		this.builder.disableContentCompression();
		this.client = this.builder.build();
	}

	@Override
	public void disablePreemptiveAuthentication() {
		this.context.removeAttribute(HttpClientContext.AUTH_CACHE);
	}

	/**
	 * Adds handling of GZIP compression to the client.
	 */
	@Override
	public void enableCompression() {
		this.builder.addInterceptorLast(new RequestAcceptEncoding());
		this.builder.addInterceptorLast(new ResponseContentEncoding());
		this.client = this.builder.build();
	}

	@Override
	public void enablePreemptiveAuthentication(final String hostname) {
		enablePreemptiveAuthentication(hostname, -1, -1);
	}

	@Override
	public void enablePreemptiveAuthentication(final String hostname, final int httpPort, final int httpsPort) {
		final AuthCache cache = new BasicAuthCache();
		// Generate Basic preemptive scheme object and stick it to the local execution context
		final BasicScheme basicAuth = new BasicScheme();
		// Configure HttpClient to authenticate preemptively by prepopulating the authentication data cache.
		cache.put(new HttpHost(hostname, httpPort, "http"), basicAuth);
		cache.put(new HttpHost(hostname, httpsPort, "https"), basicAuth);
		// Add AuthCache to the execution context
		this.context.setAttribute(HttpClientContext.AUTH_CACHE, cache);
	}

	@Override
	public void enablePreemptiveAuthentication(final URL url) {
		final String host = url.getHost();
		final int port = url.getPort();
		final String protocol = url.getProtocol();
		final int httpPort;
		final int httpsPort;
		if ("https".equals(protocol)) {
			httpsPort = port;
			httpPort = -1;
		} else if ("http".equals(protocol)) {
			httpPort = port;
			httpsPort = -1;
		} else {
			throw new IllegalArgumentException("Unsupported protocol " + protocol);
		}
		enablePreemptiveAuthentication(host, httpPort, httpsPort);
	}

	/**
	 * No validation of the response. Aborts the request if there is an exception.
	 *
	 * @param request Request to execute
	 * @return The response to check the reply status code
	 */
	protected HttpResponse execute(final HttpRequestBase request) throws IOException {
		try {
			// Clear circular redirect cache
			this.context.removeAttribute(HttpClientContext.REDIRECT_LOCATIONS);
			// Execute with no response handler
			return this.client.execute(request, this.context);
		} catch (final IOException e) {
			request.abort();
			throw e;
		}
	}

	/**
	 * Validate the response using the response handler. Aborts the request if there is an exception.
	 *
	 * @param <T> Return type
	 * @param request Request to execute
	 * @param responseHandler Determines the return type.
	 * @return parsed response
	 */
	protected <T> T execute(final HttpRequestBase request, final ResponseHandler<T> responseHandler) throws IOException {
		try {
			// Clear circular redirect cache
			this.context.removeAttribute(HttpClientContext.REDIRECT_LOCATIONS);
			// Execute with response handler
			return this.client.execute(request, responseHandler, this.context);
		} catch (final IOException e) {
			request.abort();
			throw e;
		}
	}

	@Override
	public boolean exists(final String url) throws IOException {
		final HttpHead head = new HttpHead(url);
		return this.execute(head, new ExistsResponseHandler());
	}

	@Override
	public ContentLengthInputStream get(final String url) throws IOException {
		return this.get(url, Collections.<String, String> emptyMap());
	}

	public ContentLengthInputStream get(final String url, final List<Header> headers) throws IOException {
		final HttpGet get = new HttpGet(url);
		for (final Header header : headers) {
			get.addHeader(header);
		}
		// Must use #execute without handler, otherwise the entity is consumed
		// already after the handler exits.
		final HttpResponse response = this.execute(get);
		final VoidResponseHandler handler = new VoidResponseHandler();
		try {
			handler.handleResponse(response);
			// Will consume the entity when the stream is closed.
			return new ConsumingInputStream(response);
		} catch (final IOException ex) {
			get.abort();
			throw ex;
		}
	}

	@Override
	public ContentLengthInputStream get(final String url, final Map<String, String> headers) throws IOException {
		final List<Header> list = new ArrayList<Header>();
		for (final Map.Entry<String, String> h : headers.entrySet()) {
			list.add(new BasicHeader(h.getKey(), h.getValue()));
		}
		return this.get(url, list);
	}

	@Override
	public DavAcl getAcl(final String url) throws IOException {
		final HttpPropFind entity = new HttpPropFind(url);
		entity.setDepth("0");
		final Propfind body = new Propfind();
		final Prop prop = new Prop();
		prop.setOwner(new Owner());
		prop.setGroup(new Group());
		prop.setAcl(new Acl());
		body.setProp(prop);
		entity.setEntity(new StringEntity(SardineUtil.toXml(body), SardineImpl.UTF_8));
		final Multistatus multistatus = this.execute(entity, new MultiStatusResponseHandler());
		final List<Response> responses = multistatus.getResponse();
		if (responses.isEmpty()) {
			return null;
		} else {
			return new DavAcl(responses.get(0));
		}
	}

	private CredentialsProvider getCredentialsProvider(final String username, final String password, final String domain, final String workstation) {
		final CredentialsProvider provider = new BasicCredentialsProvider();
		if (username != null) {
			provider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthPolicy.NTLM), new NTCredentials(username, password, workstation, domain));
			provider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthPolicy.BASIC), new UsernamePasswordCredentials(username, password));
			provider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthPolicy.DIGEST), new UsernamePasswordCredentials(username, password));
			provider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthPolicy.SPNEGO), new UsernamePasswordCredentials(username, password));
			provider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthPolicy.KERBEROS), new UsernamePasswordCredentials(username, password));
		}
		return provider;
	}

	@Override
	public List<String> getPrincipalCollectionSet(final String url) throws IOException {
		final HttpPropFind entity = new HttpPropFind(url);
		entity.setDepth("0");
		final Propfind body = new Propfind();
		final Prop prop = new Prop();
		prop.setPrincipalCollectionSet(new PrincipalCollectionSet());
		body.setProp(prop);
		entity.setEntity(new StringEntity(SardineUtil.toXml(body), SardineImpl.UTF_8));
		final Multistatus multistatus = this.execute(entity, new MultiStatusResponseHandler());
		final List<Response> responses = multistatus.getResponse();
		if (responses.isEmpty()) {
			return null;
		} else {
			final List<String> collections = new ArrayList<String>();
			for (final Response r : responses) {
				if (r.getPropstat() != null) {
					for (final Propstat propstat : r.getPropstat()) {
						if ((propstat.getProp() != null) && (propstat.getProp().getPrincipalCollectionSet() != null) && (propstat.getProp().getPrincipalCollectionSet().getHref() != null)) {
							collections.addAll(propstat.getProp().getPrincipalCollectionSet().getHref());
						}
					}
				}
			}
			return collections;
		}
	}

	@Override
	public List<DavPrincipal> getPrincipals(final String url) throws IOException {
		final HttpPropFind entity = new HttpPropFind(url);
		entity.setDepth("1");
		final Propfind body = new Propfind();
		final Prop prop = new Prop();
		prop.setDisplayname(new Displayname());
		prop.setResourcetype(new Resourcetype());
		prop.setPrincipalURL(new PrincipalURL());
		body.setProp(prop);
		entity.setEntity(new StringEntity(SardineUtil.toXml(body), SardineImpl.UTF_8));
		final Multistatus multistatus = this.execute(entity, new MultiStatusResponseHandler());
		final List<Response> responses = multistatus.getResponse();
		if (responses.isEmpty()) {
			return null;
		} else {
			final List<DavPrincipal> collections = new ArrayList<DavPrincipal>();
			for (final Response r : responses) {
				if (r.getPropstat() != null) {
					for (final Propstat propstat : r.getPropstat()) {
						if ((propstat.getProp() != null) && (propstat.getProp().getResourcetype() != null) && (propstat.getProp().getResourcetype().getPrincipal() != null)) {
							collections.add(new DavPrincipal(DavPrincipal.PrincipalType.HREF, r.getHref().get(0), propstat.getProp().getDisplayname().getContent().get(0)));
						}
					}
				}
			}
			return collections;
		}
	}

	@Override
	public DavQuota getQuota(final String url) throws IOException {
		final HttpPropFind entity = new HttpPropFind(url);
		entity.setDepth("0");
		final Propfind body = new Propfind();
		final Prop prop = new Prop();
		prop.setQuotaAvailableBytes(new QuotaAvailableBytes());
		prop.setQuotaUsedBytes(new QuotaUsedBytes());
		body.setProp(prop);
		entity.setEntity(new StringEntity(SardineUtil.toXml(body), SardineImpl.UTF_8));
		final Multistatus multistatus = this.execute(entity, new MultiStatusResponseHandler());
		final List<Response> responses = multistatus.getResponse();
		if (responses.isEmpty()) {
			return null;
		} else {
			return new DavQuota(responses.get(0));
		}
	}

	@Override
	public List<DavResource> getResources(final String url) throws IOException {
		return this.list(url);
	}

	@Override
	public List<DavResource> list(final String url) throws IOException {
		return this.list(url, 1);
	}

	@Override
	public List<DavResource> list(final String url, final int depth) throws IOException {
		return list(url, depth, true);
	}

	@Override
	public List<DavResource> list(final String url, final int depth, final boolean allProp) throws IOException {
		if (allProp) {
			final Propfind body = new Propfind();
			body.setAllprop(new Allprop());
			return list(url, depth, body);
		} else {
			return list(url, depth, Collections.<QName> emptySet());
		}
	}

	@Override
	public List<DavResource> list(final String url, final int depth, final java.util.Set<QName> props) throws IOException {
		final Propfind body = new Propfind();
		final Prop prop = new Prop();
		final ObjectFactory objectFactory = new ObjectFactory();
		prop.setGetcontentlength(objectFactory.createGetcontentlength());
		prop.setGetlastmodified(objectFactory.createGetlastmodified());
		prop.setCreationdate(objectFactory.createCreationdate());
		prop.setDisplayname(objectFactory.createDisplayname());
		prop.setGetcontenttype(objectFactory.createGetcontenttype());
		prop.setResourcetype(objectFactory.createResourcetype());
		prop.setGetetag(objectFactory.createGetetag());
		final List<Element> any = prop.getAny();
		for (final QName entry : props) {
			final Element element = SardineUtil.createElement(entry);
			any.add(element);
		}
		body.setProp(prop);
		return list(url, depth, body);
	}

	protected List<DavResource> list(final String url, final int depth, final Propfind body) throws IOException {
		final HttpPropFind entity = new HttpPropFind(url);
		entity.setDepth(Integer.toString(depth));
		entity.setEntity(new StringEntity(SardineUtil.toXml(body), SardineImpl.UTF_8));
		final Multistatus multistatus = this.execute(entity, new MultiStatusResponseHandler());
		final List<Response> responses = multistatus.getResponse();
		final List<DavResource> resources = new ArrayList<DavResource>(responses.size());
		for (final Response response : responses) {
			try {
				resources.add(new DavResource(response));
			} catch (final URISyntaxException e) {
				SardineImpl.log.warn(String.format("Ignore resource with invalid URI %s", response.getHref().get(0)));
			}
		}
		return resources;
	}

	@Override
	public String lock(final String url) throws IOException {
		final HttpLock entity = new HttpLock(url);
		final Lockinfo body = new Lockinfo();
		final Lockscope scopeType = new Lockscope();
		scopeType.setExclusive(new Exclusive());
		body.setLockscope(scopeType);
		final Locktype lockType = new Locktype();
		lockType.setWrite(new Write());
		body.setLocktype(lockType);
		entity.setEntity(new StringEntity(SardineUtil.toXml(body), SardineImpl.UTF_8));
		// Return the lock token
		return this.execute(entity, new LockResponseHandler());
	}

	@Override
	public void move(final String sourceUrl, final String destinationUrl) throws IOException {
		move(sourceUrl, destinationUrl, true);
	}

	@Override
	public void move(final String sourceUrl, final String destinationUrl, final boolean overwrite) throws IOException {
		final HttpMove move = new HttpMove(sourceUrl, destinationUrl, overwrite);
		this.execute(move, new VoidResponseHandler());
	}

	@Override
	public List<DavResource> patch(final String url, final Map<QName, String> setProps) throws IOException {
		return this.patch(url, setProps, Collections.<QName> emptyList());
	}

	/**
	 * Creates a {@link com.github.sardine.model.Propertyupdate} element containing all properties to set from setProps and all properties to remove from
	 * removeProps. Note this method will use a {@link com.github.sardine.util.SardineUtil#CUSTOM_NAMESPACE_URI} as namespace and
	 * {@link com.github.sardine.util.SardineUtil#CUSTOM_NAMESPACE_PREFIX} as prefix.
	 */
	@Override
	public List<DavResource> patch(final String url, final Map<QName, String> setProps, final List<QName> removeProps) throws IOException {
		final HttpPropPatch entity = new HttpPropPatch(url);
		// Build WebDAV <code>PROPPATCH</code> entity.
		final Propertyupdate body = new Propertyupdate();
		// Add properties
		{
			final Set set = new Set();
			body.getRemoveOrSet().add(set);
			final Prop prop = new Prop();
			// Returns a reference to the live list
			final List<Element> any = prop.getAny();
			for (final Map.Entry<QName, String> entry : setProps.entrySet()) {
				final Element element = SardineUtil.createElement(entry.getKey());
				element.setTextContent(entry.getValue());
				any.add(element);
			}
			set.setProp(prop);
		}
		// Remove properties
		{
			final Remove remove = new Remove();
			body.getRemoveOrSet().add(remove);
			final Prop prop = new Prop();
			// Returns a reference to the live list
			final List<Element> any = prop.getAny();
			for (final QName entry : removeProps) {
				final Element element = SardineUtil.createElement(entry);
				any.add(element);
			}
			remove.setProp(prop);
		}
		entity.setEntity(new StringEntity(SardineUtil.toXml(body), SardineImpl.UTF_8));
		final Multistatus multistatus = this.execute(entity, new MultiStatusResponseHandler());
		final List<Response> responses = multistatus.getResponse();
		final List<DavResource> resources = new ArrayList<DavResource>(responses.size());
		for (final Response response : responses) {
			try {
				resources.add(new DavResource(response));
			} catch (final URISyntaxException e) {
				SardineImpl.log.warn(String.format("Ignore resource with invalid URI %s", response.getHref().get(0)));
			}
		}
		return resources;
	}

	@Override
	public void put(final String url, final byte[] data) throws IOException {
		this.put(url, data, null);
	}

	@Override
	public void put(final String url, final byte[] data, final String contentType) throws IOException {
		final ByteArrayEntity entity = new ByteArrayEntity(data);
		this.put(url, entity, contentType, true);
	}

	@Override
	public void put(final String url, final File localFile, final String contentType) throws IOException {
		final FileEntity content = new FileEntity(localFile);
		// don't use ExpectContinue for repetable FileEntity, some web server (IIS for exmaple) may return 400 bad request after retry
		this.put(url, content, contentType, false);
	}

	/**
	 * Upload the entity using <code>PUT</code>
	 *
	 * @param url Resource
	 * @param entity The entity to read from
	 * @param headers Headers to add to request
	 */
	public void put(final String url, final HttpEntity entity, final List<Header> headers) throws IOException {
		this.put(url, entity, headers, new VoidResponseHandler());
	}

	public <T> T put(final String url, final HttpEntity entity, final List<Header> headers, final ResponseHandler<T> handler) throws IOException {
		final HttpPut put = new HttpPut(url);
		put.setEntity(entity);
		for (final Header header : headers) {
			put.addHeader(header);
		}
		if ((entity.getContentType() == null) && !put.containsHeader(HttpHeaders.CONTENT_TYPE)) {
			put.addHeader(HttpHeaders.CONTENT_TYPE, HTTP.DEF_CONTENT_CHARSET.name());
		}
		try {
			return this.execute(put, handler);
		} catch (final HttpResponseException e) {
			if (e.getStatusCode() == HttpStatus.SC_EXPECTATION_FAILED) {
				// Retry with the Expect header removed
				put.removeHeaders(HTTP.EXPECT_DIRECTIVE);
				if (entity.isRepeatable()) {
					return this.execute(put, handler);
				}
			}
			throw e;
		}
	}

	/**
	 * Upload the entity using <code>PUT</code>
	 *
	 * @param url Resource
	 * @param entity The entity to read from
	 * @param contentType Content Type header
	 * @param expectContinue Add <code>Expect: continue</code> header
	 */
	public void put(final String url, final HttpEntity entity, final String contentType, final boolean expectContinue) throws IOException {
		final List<Header> headers = new ArrayList<Header>();
		if (contentType != null) {
			headers.add(new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType));
		}
		if (expectContinue) {
			headers.add(new BasicHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE));
		}
		this.put(url, entity, headers);
	}

	@Override
	public void put(final String url, final InputStream dataStream) throws IOException {
		this.put(url, dataStream, (String) null);
	}

	public void put(final String url, final InputStream dataStream, final List<Header> headers) throws IOException {
		// A length of -1 means "go until end of stream"
		final InputStreamEntity entity = new InputStreamEntity(dataStream, -1);
		this.put(url, entity, headers);
	}

	@Override
	public void put(final String url, final InputStream dataStream, final Map<String, String> headers) throws IOException {
		final List<Header> list = new ArrayList<Header>();
		for (final Map.Entry<String, String> h : headers.entrySet()) {
			list.add(new BasicHeader(h.getKey(), h.getValue()));
		}
		this.put(url, dataStream, list);
	}

	@Override
	public void put(final String url, final InputStream dataStream, final String contentType) throws IOException {
		this.put(url, dataStream, contentType, true);
	}

	@Override
	public void put(final String url, final InputStream dataStream, final String contentType, final boolean expectContinue) throws IOException {
		// A length of -1 means "go until end of stream"
		put(url, dataStream, contentType, expectContinue, -1);
	}

	@Override
	public void put(final String url, final InputStream dataStream, final String contentType, final boolean expectContinue, final long contentLength) throws IOException {
		final InputStreamEntity entity = new InputStreamEntity(dataStream, contentLength);
		this.put(url, entity, contentType, expectContinue);
	}

	@Override
	public String refreshLock(final String url, final String token, final String file) throws IOException {
		final HttpLock entity = new HttpLock(url);
		entity.setHeader("If", "<" + file + "> (<" + token + ">)");
		return this.execute(entity, new LockResponseHandler());
	}

	@Override
	public List<DavResource> search(final String url, final String language, final String query) throws IOException {
		final HttpEntityEnclosingRequestBase search = new HttpSearch(url);
		final SearchRequest searchBody = new SearchRequest(language, query);
		final String body = SardineUtil.toXml(searchBody);
		search.setEntity(new StringEntity(body, SardineImpl.UTF_8));
		final Multistatus multistatus = this.execute(search, new MultiStatusResponseHandler());
		final List<Response> responses = multistatus.getResponse();
		final List<DavResource> resources = new ArrayList<DavResource>(responses.size());
		for (final Response response : responses) {
			try {
				resources.add(new DavResource(response));
			} catch (final URISyntaxException e) {
				SardineImpl.log.warn(String.format("Ignore resource with invalid URI %s", response.getHref().get(0)));
			}
		}
		return resources;
	}

	@Override
	public void setAcl(final String url, final List<DavAce> aces) throws IOException {
		final HttpAcl entity = new HttpAcl(url);
		// Build WebDAV <code>ACL</code> entity.
		final Acl body = new Acl();
		body.setAce(new ArrayList<Ace>());
		for (final DavAce davAce : aces) {
			// protected and inherited acl must not be part of ACL http request
			if ((davAce.getInherited() != null) || davAce.isProtected()) {
				continue;
			}
			final Ace ace = davAce.toModel();
			body.getAce().add(ace);
		}
		entity.setEntity(new StringEntity(SardineUtil.toXml(body), SardineImpl.UTF_8));
		this.execute(entity, new VoidResponseHandler());
	}

	/**
	 * Add credentials to any scope. Supports Basic, Digest and NTLM authentication methods.
	 *
	 * @param username Use in authentication header credentials
	 * @param password Use in authentication header credentials
	 */
	@Override
	public void setCredentials(final String username, final String password) {
		this.setCredentials(username, password, "", "");
	}

	/**
	 * @param username Use in authentication header credentials
	 * @param password Use in authentication header credentials
	 * @param domain NTLM authentication
	 * @param workstation NTLM authentication
	 */
	@Override
	public void setCredentials(final String username, final String password, final String domain, final String workstation) {
		this.context.setCredentialsProvider(getCredentialsProvider(username, password, domain, workstation));
		this.context.setAttribute(HttpClientContext.TARGET_AUTH_STATE, new AuthState());
	}

	@Override
	public void setCustomProps(final String url, final Map<String, String> set, final List<String> remove) throws IOException {
		this.patch(url, SardineUtil.toQName(set), SardineUtil.toQName(remove));
	}

	@Override
	public void shutdown() throws IOException {
		this.client.close();
	}

	@Override
	public void unlock(final String url, final String token) throws IOException {
		final HttpUnlock entity = new HttpUnlock(url, token);
		final Lockinfo body = new Lockinfo();
		final Lockscope scopeType = new Lockscope();
		scopeType.setExclusive(new Exclusive());
		body.setLockscope(scopeType);
		final Locktype lockType = new Locktype();
		lockType.setWrite(new Write());
		body.setLocktype(lockType);
		this.execute(entity, new VoidResponseHandler());
	}
}
