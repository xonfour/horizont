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
import org.apache.http.client.methods.HttpRequestBase;

/**
 * Simple class for making WebDAV <code>COPY</code> requests. Assumes Overwrite = T.
 *
 */
public class HttpCopy extends HttpRequestBase {
	public static final String METHOD_NAME = "COPY";

	public HttpCopy(final String sourceUrl, final String destinationUrl, final boolean overwrite) {
		this(URI.create(sourceUrl), URI.create(destinationUrl), overwrite);
	}

	public HttpCopy(final URI sourceUrl, final URI destinationUrl, final boolean overwrite) {
		this.setHeader(HttpHeaders.DESTINATION, destinationUrl.toASCIIString());
		this.setHeader(HttpHeaders.OVERWRITE, overwrite ? "T" : "F");
		setURI(sourceUrl);
	}

	@Override
	public String getMethod() {
		return HttpCopy.METHOD_NAME;
	}
}
