/* Copyright 2009-2011 Jon Stevens et al.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */

package com.github.sardine.impl.handler;

import java.io.IOException;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;

/**
 * @version $Id: VoidResponseHandler.java 233 2011-05-24 11:55:15Z dkocher@sudo.ch $
 */
public class ETagResponseHandler extends ValidatingResponseHandler<String> {
	@Override
	public String handleResponse(final HttpResponse response) throws IOException {
		validateResponse(response);
		if (response.containsHeader(HttpHeaders.ETAG)) {
			return response.getFirstHeader(HttpHeaders.ETAG).getValue();
		}
		return null;
	}
}
