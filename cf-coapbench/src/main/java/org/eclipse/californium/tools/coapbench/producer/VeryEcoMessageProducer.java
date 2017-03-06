/*******************************************************************************
 * Copyright (c) 2015, 2016 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Martin Lanter - architect and initial implementation
 *    Kai Hudalla (Bosch Software Innovations GmbH) - use static reference to Serializer
 ******************************************************************************/
package org.eclipse.californium.tools.coapbench.producer;

import java.net.URI;
import java.util.Iterator;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.serialization.UdpDataSerializer;
import org.eclipse.californium.elements.RawData;

/**
 * This producer is as economic with memory as possible. It only uses a single
 * {@link RawData} instance. To produce a new request, it only changes the MID
 * (bytes 2 and 3). This producer must only be used in strict single-threaded
 * environment (because there is actually only one single request that is reused
 * infinitely often).
 */
public class VeryEcoMessageProducer implements Iterator<byte[]> {

	private final UdpDataSerializer serializer = new UdpDataSerializer();
	private byte[] prototype;

	public VeryEcoMessageProducer(URI uri) {
		setURI(uri, null, null);
	}

	public VeryEcoMessageProducer() { }
	
	public void setURI(URI uri, String method, String payload) {
		
		Request request;
		
		switch (method){
			case("PUT"):
			case("put"):
				request = new Request(Code.PUT);
				break;
			case("POST"):
			case("post"):
				request = new Request(Code.POST);
				break;
			case("DELETE"):
			case("delete"):
				request = new Request(Code.DELETE);
				break;
			default:
				request = new Request(Code.GET);
				break;
		}
		
		request.setType(Type.CON);
		request.setToken(new byte[0]);
		request.setMID(0);
		request.setURI(uri);
		if (payload != null)
			request.setPayload(payload);
		prototype = serializer.serializeRequest(request).getBytes();
	}

	@Override
	public boolean hasNext() {
		return true;
	}

	@Override
	public byte[] next() {
		// increase MID at location [2,3]
		if (++prototype[3] == 0)
			++prototype[2];
		return prototype;
	}
	
	@Override
	public void remove() { }
}
