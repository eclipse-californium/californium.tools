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
package org.eclipse.californium.tools.resources;

import org.eclipse.californium.core.CoapExchange;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;

/**
 * This resource contains two subresources: shutdown and restart. Send a POST
 * request to subresource shutdown to stop the server. Send a POST request to
 * the subresource restart to restart the server.
 */
public class RunningResource extends CoapResource {

	private CoapServer server;
	
	private int restartCount;
	
	public RunningResource(String name, CoapServer s) {
		super(name);
		this.server = s;
		
		add(new CoapResource("shutdown") {
			public void handlePOST(CoapExchange exchange) {
				exchange.respond(ResponseCode.CHANGED);
				sleep(100);
				server.stop();
			}
		});
		
		add(new CoapResource("restart") {
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
