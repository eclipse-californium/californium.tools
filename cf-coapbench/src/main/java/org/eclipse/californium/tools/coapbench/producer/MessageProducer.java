/*******************************************************************************
 * Copyright (c) 2014 Institute for Pervasive Computing, ETH Zurich and others.
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
 ******************************************************************************/
package org.eclipse.californium.tools.coapbench.producer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.serialization.Serializer;
import org.eclipse.californium.elements.RawData;

/**
 * This MessageProduces produces a certain number of {@link RawData} in an
 * array. Whenever a new instance is requested is returns the next one. This
 * produces needs a lot of memory but produces instantly and all data are
 * cache-local.
 */
public class MessageProducer implements Iterator<RawData> {

	ArrayList<RawData> array;

	private int amount;
	private int pointer;
	
	public MessageProducer(String targetURI, int amount) throws Exception {
		this.amount = amount;

		Serializer serializer = new Serializer();
		InetAddress source = InetAddress.getByName("192.168.1.33");

		int count = 0;
		this.array = new ArrayList<RawData>(amount);
		for (int port = 1; port < (1 << 16) && count < amount; port++) {
			for (int mid = 0; mid < (1 << 16) && count < amount; mid++) {
				Request request = new Request(Code.GET);
				request.setType(Type.NON);
				request.setToken(new byte[0]);
				request.setMID(mid);
				request.setURI(targetURI);

				RawData raw = serializer.serialize(request);
				raw.setAddress(source);
				raw.setPort(port);
				array.add(raw);
				count++;
			}
		}

		Collections.shuffle(array);
	}

	public static void printUsesMemory() {
		System.out.println("used memory: "
				+ (Runtime.getRuntime().totalMemory() - Runtime.getRuntime()
						.freeMemory()) / 1024 + "	KB");
	}

	@Override
	public boolean hasNext() {
		return pointer < amount;
	}

	@Override
	public RawData next() {
		return array.get(pointer++);
	}

	@Override
	public void remove() {}
}
