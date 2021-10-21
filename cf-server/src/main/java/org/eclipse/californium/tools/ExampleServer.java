/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 ******************************************************************************/
package org.eclipse.californium.tools;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.config.UdpConfig;
import org.eclipse.californium.tools.resources.FibonacciResource;
import org.eclipse.californium.tools.resources.HelloWorldResource;
import org.eclipse.californium.tools.resources.ImageResource;
import org.eclipse.californium.tools.resources.LargeResource;
import org.eclipse.californium.tools.resources.MirrorResource;
import org.eclipse.californium.tools.resources.StorageResource;

/**
 * This is an example server that contains a few resources for demonstration.
 */
public class ExampleServer {
	
	public static void main(String[] args) throws Exception {
		CoapConfig.register();
		UdpConfig.register();

		CoapServer server = new CoapServer();

		server.add(new HelloWorldResource("hello"));
		server.add(new FibonacciResource("fibonacci"));
		server.add(new StorageResource("storage"));
		server.add(new ImageResource("image"));
		server.add(new MirrorResource("mirror"));
		server.add(new LargeResource("large"));
		
		server.start();
	}
	
	/*
	 *  Sends a GET request to itself
	 */
	public static void selfTest() {
		try {
			Request request = Request.newGet();
			request.setURI("coap://localhost:5683/hello");
			request.send();
			Response response = request.waitForResponse(1000);
			System.out.println("received "+response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
