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

import java.util.List;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RDResource extends CoapResource {

	private final static Logger LOGGER = LoggerFactory.getLogger(RDResource.class);

	public RDResource() {
		this("rd");
	}

	public RDResource(String resourceIdentifier) {
		super(resourceIdentifier);
		getAttributes().addResourceType("core.rd");
		getAttributes().addContentType(MediaTypeRegistry.APPLICATION_LINK_FORMAT);
	}

	/*
	 * POSTs a new sub-resource to this resource. The name of the new
	 * sub-resource is a random number if not specified in the Option-query.
	 */
	@Override
	public void handlePOST(CoapExchange exchange) {
		
		// get name and lifetime from option query
		String endpointName = "";
		String sector = "local";
		RDNodeResource resource = null;
		
		ResponseCode responseCode;

		LOGGER.info("Registration request from "+exchange.getSourceAddress().getHostName()+":"+exchange.getSourcePort());
		
		List<String> query = exchange.getRequestOptions().getUriQueryStrings();
		for (String q : query) {
			
			KeyValuePair kvp = KeyValuePair.parse(q);
			
			if (LinkFormat.END_POINT.equals(kvp.getName()) && !kvp.isFlag()) {
				endpointName = kvp.getValue();
			}

			if (LinkFormat.SECTOR.equals(kvp.getName()) && !kvp.isFlag()) {
				sector = kvp.getValue();
			}
		}

		// mandatory variables
		if (endpointName.isEmpty()) {
			LOGGER.info("Missing Endpoint Name for "+exchange.getSourceAddress().getHostName()+":"+exchange.getSourcePort());
			exchange.respond(ResponseCode.BAD_REQUEST, "Missing Endpoint Name (?ep)");
			return;
		}
		
		// find already registered EP
		for (Resource node : getChildren()) {
			if (((RDNodeResource) node).getEndpointName().equals(endpointName) && ((RDNodeResource) node).getSector().equals(sector)) {
				resource = (RDNodeResource) node;
			}
		}

		//Endpoint unaware of its previous entry in RD: deleting it to put the latest entry.
		if (resource!=null) {
			resource.delete();
		}
		
		// uncomment to use random resource names instead of registered Endpoint Name
		/*
		String randomName;
		do {
			randomName = Integer.toString((int) (Math.random() * 10000));
		} while (getChild(randomName) != null);
		*/

		resource = new RDNodeResource(endpointName, sector);
		add(resource);
			
		responseCode = ResponseCode.CREATED;
		
		// set parameters of resource or abort on failure
		if (!resource.setParameters(exchange.advanced().getRequest())) {
			resource.delete();
			exchange.respond(ResponseCode.BAD_REQUEST);
			return;
		}
		
		LOGGER.info("Adding new endpoint: "+resource.getBase());

		// inform client about the location of the new resource
		exchange.setLocationPath(resource.getURI());

		// complete the request
		exchange.respond(responseCode);
	}

}
