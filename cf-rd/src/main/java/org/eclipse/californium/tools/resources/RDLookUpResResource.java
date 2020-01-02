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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;


public class RDLookUpResResource extends CoapResource {

	private RDResource rdResource = null;

	public RDLookUpResResource(String resourceIdentifier, RDResource rd) {
		super(resourceIdentifier);
		this.rdResource = rd;
		getAttributes().addResourceType("core.rd-lookup-res");
		getAttributes().addContentType(MediaTypeRegistry.APPLICATION_LINK_FORMAT);
	}


	@Override
	public void handleGET(CoapExchange exchange) {
		Collection<Resource> resources = rdResource.getChildren();
		List<String> candidates = new ArrayList<>();
		String sectorQuery = "";
		String endpointQuery = "";
		boolean countPresent = false;
		int count = 0;
		int page = 0;
		List<String> toRemove = new ArrayList<String>(); 
		
		List<String> query = exchange.getRequestOptions().getUriQuery();
		for (String q : query) {
			KeyValuePair kvp = KeyValuePair.parse(q);
			
			switch (kvp.getName()) {
			case LinkFormat.SECTOR:
				if (kvp.isFlag()) {
					exchange.respond(ResponseCode.BAD_REQUEST, "Empty sector query");
					return;
				} else {
					sectorQuery = kvp.getValue();
					toRemove.add(q);
				}
				break;
			case LinkFormat.END_POINT:
				if (kvp.isFlag()) {
					exchange.respond(ResponseCode.BAD_REQUEST, "Empty endpoint query");
					return;
				} else {
					endpointQuery = kvp.getValue();
					toRemove.add(q);
				}
				break;
			case LinkFormat.COUNT:
				countPresent = true;
				count = kvp.getIntValue();
				toRemove.add(q);
				break;
			case LinkFormat.PAGE:
				page = kvp.getIntValue();
				toRemove.add(q);
				break;
			}
		}
		
		// clear handled queries from list
		query.removeAll(toRemove);
		
		// check registered resources
		Iterator<Resource>  resIt = resources.iterator();
		
		while (resIt.hasNext()) {
			Resource res = resIt.next();
			if (res instanceof RDNodeResource) {
				RDNodeResource node = (RDNodeResource) res;
				if ( (sectorQuery.isEmpty() || sectorQuery.equals(node.getSector()))
					 && (endpointQuery.isEmpty() || endpointQuery.equals(node.getEndpointName())) ) {
					candidates.addAll(node.toLinkFormat(query));
				}
			}
		}
		
		if ((count < 0) || (page < 0)) {
			exchange.respond(ResponseCode.BAD_REQUEST);
		} else if (candidates.isEmpty() || ((count * page) >= candidates.size()) || (count == 0 && countPresent)) {
			exchange.respond(ResponseCode.CONTENT);
		} else {
			int from, to;
			if (countPresent) {
				from = count * page;
				to = (from + count > candidates.size()) ? candidates.size() : from + count;
			} else {
				from = 0;
				to = candidates.size();
			}
			String result = "";
			for (String s : candidates.subList(from, to)) {
				result += s;
			}
			// also remove trailing comma
			exchange.respond(ResponseCode.CONTENT, result.substring(0, result.length()-1), MediaTypeRegistry.APPLICATION_LINK_FORMAT);
		}
	}
}
