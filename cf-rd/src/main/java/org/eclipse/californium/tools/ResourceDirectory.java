/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
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
package org.eclipse.californium.tools;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.tools.resources.RDLookUpTopResource;
import org.eclipse.californium.tools.resources.RDResource;
import org.eclipse.californium.tools.resources.RDTagTopResource;

/**
 * The class ResourceDirectory provides an experimental RD
 * as described in draft-ietf-core-resource-directory-04.
 */
public class ResourceDirectory extends CoapServer {

	// exit codes for runtime errors
	public static final int ERR_INIT_FAILED = 1;

	public static void main(String[] args) {

		// create server
		CoapServer server = new ResourceDirectory();

		// explicitly bind to each address to avoid the wildcard address reply problem
		// (default interface address instead of original destination)
		for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
			if (!addr.isLinkLocalAddress()) {
				server.addEndpoint(new CoapEndpoint(new InetSocketAddress(addr, CoAP.DEFAULT_COAP_PORT)));
			}
		}

		server.start();

		System.out.printf(ResourceDirectory.class.getSimpleName() + " listening on port %d.\n", server.getEndpoints().get(0).getAddress().getPort());
	}

	public ResourceDirectory() {

		RDResource rdResource = new RDResource();

		// add resources to the server
		add(rdResource);
		add(new RDLookUpTopResource(rdResource));
		add(new RDTagTopResource(rdResource));
	}
}
