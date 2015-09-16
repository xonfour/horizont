/* Copyright 2009-2011 Jon Stevens et al.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */

package com.github.sardine.impl.methods;

import java.net.URI;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

/**
 * Simple class for making WebDAV <code>PROPPATCH</code> requests.
 *
 */
public class HttpPropPatch extends HttpEntityEnclosingRequestBase {
	public static final String METHOD_NAME = "PROPPATCH";

	public HttpPropPatch(final String url) {
		this(URI.create(url));
	}

	public HttpPropPatch(final URI url) {
		setURI(url);
		this.setHeader(HttpHeaders.CONTENT_TYPE, "text/xml; charset=utf-8");
	}

	@Override
	public String getMethod() {
		return HttpPropPatch.METHOD_NAME;
	}
}
