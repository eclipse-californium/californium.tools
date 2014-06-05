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
 ******************************************************************************/
package org.eclipse.californium.examples.resources;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.Server;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.ResourceBase;

/**
 * This resource contains two subresources: shutdown and restart. Send a POST
 * request to subresource shutdown to stop the server. Send a POST request to
 * the subresource restart to restart the server.
 */
public class RunningResource extends ResourceBase {

	private Server server;
	
	private int restartCount;
	
	public RunningResource(String name, Server s) {
		super(name);
		this.server = s;
		
		add(new ResourceBase("shutdown") {
			public void handlePOST(CoapExchange exchange) {
				exchange.respond(ResponseCode.CHANGED);
				sleep(100);
				server.stop();
			}
		});
		
		add(new ResourceBase("restart") {
			public void handlePOST(CoapExchange exchange) {
				restartCount++;
				server.stop();
				sleep(100);
				server.start();
				exchange.respond(ResponseCode.CHANGED, "Restart count: "+restartCount);
			}
		});
	}
	
	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
