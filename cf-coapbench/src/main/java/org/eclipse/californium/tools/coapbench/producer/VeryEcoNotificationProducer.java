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

import java.util.Arrays;
import java.util.Iterator;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.serialization.UdpDataSerializer;
import org.eclipse.californium.elements.RawData;

/**
 * This producer is as economic with memory as possible. It only uses a single
 * {@link RawData} instance. To produce a new request, it only changes the MID
 * (bytes 2 and 3). This producer must only be used in strict single-threaded
 * environment (because there is actually only one single request that is reused
 * infinitely often).
 */
public class VeryEcoNotificationProducer implements Iterator<byte[]> {

	private final UdpDataSerializer serializer = new UdpDataSerializer();
	private byte[] prototype;
	private byte[] token;
	private boolean useCONs = false;

	public VeryEcoNotificationProducer(byte[] token, int MID, boolean useCONs) {
		this.useCONs = useCONs;
		setIDs(token, MID);
	}
	
	public void setIDs(byte[] token, int MID) {
		this.token = token;
		Response response = new Response(ResponseCode.CONTENT);
		
		if (useCONs)
			response.setType(Type.CON);
		else
			response.setType(Type.NON);
		
		response.setToken(token);
		response.setMID(MID);
		response.setOptions(response.getOptions().setObserve(1));
		
		prototype = serializer.serializeResponse(response).getBytes();
	}

	public byte[] getToken() {
		return token;
	}
	
	@Override
	public boolean hasNext() {
		return true;
	}

	@Override
	public byte[] next() {
		int optionIndex = 4 + (prototype[0] & 0x0F);
		int optionLength = prototype[optionIndex] & 0x0F;
		// increase MID at location [2,3]
		if (++prototype[3] == 0)
			++prototype[2];
		// increase the Observe sequence number
		if (++prototype[optionIndex + optionLength] == 0) {
			if ((optionLength) == 1) {
				++prototype[optionIndex];
				prototype = Arrays.copyOfRange(prototype, 0, optionIndex + 3);
			}
			++prototype[optionIndex + prototype[optionIndex] & 0x0F - 1];
		}
		if ((optionLength & 0x0F) >= 2 && prototype[optionIndex + 1] == 0 && prototype[optionIndex + 2] == 0) {
			if (optionLength == 2) {
				++prototype[optionIndex];
				prototype = Arrays.copyOfRange(prototype, 0, optionIndex + 4);
			}
			++prototype[optionIndex + prototype[optionIndex] & 0x0F - 2];
		}
		if (optionLength == 3 && prototype[optionIndex + 1] == 0 && prototype[optionIndex + 2] == 0 && prototype[optionIndex + 3] == 0) {
			prototype[optionIndex] = new Integer((prototype[optionIndex] & 0xF0) + 1).byteValue();
			prototype = Arrays.copyOfRange(prototype, 0, optionIndex + 2);
		}
		return prototype;
	}
	
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 3];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 3] = hexArray[v >>> 4];
	        hexChars[j * 3 + 1] = hexArray[v & 0x0F];
	        hexChars[j * 3 + 2] = ' ';
	    }
	    return new String(hexChars).substring(0, bytes.length * 3 - 1);
	}
	
	@Override
	public void remove() { }
}
