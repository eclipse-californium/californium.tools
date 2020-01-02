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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;


public class RDLookUpEPResource extends CoapResource {

	private RDResource rdResource = null;

	public RDLookUpEPResource(String resourceIdentifier, RDResource rd) {
		super(resourceIdentifier);
		this.rdResource = rd;
		getAttributes().addResourceType("core.rd-lookup-ep");
		getAttributes().addContentType(MediaTypeRegistry.APPLICATION_LINK_FORMAT);
	}


	@Override
	public void handleGET(CoapExchange exchange) {
		Collection<Resource> resources = rdResource.getChildren();
		List<String> candidates = new ArrayList<>();
		String sectorQuery = "";
		String endpointQuery = "";
		TreeSet<String> endpointTypeQuery = new TreeSet<String>();
		boolean countPresent = false;
		int count = 0;
		int page = 0;
		HashMap<String, String> extraAttrsQuery = new HashMap<>();

		List<String> query = exchange.getRequestOptions().getUriQuery();
		for (String q : query) {
			KeyValuePair kvp = KeyValuePair.parse(q);
			
			switch (kvp.getName()) {
			case LinkFormat.SECTOR:
				sectorQuery = kvp.getValue();
				break;
			case LinkFormat.END_POINT:
				endpointQuery = kvp.getValue();
				break;
			case LinkFormat.END_POINT_TYPE:
				endpointTypeQuery.add(kvp.getValue());
				break;
			case LinkFormat.COUNT:
        countPresent = true;
				count = kvp.getIntValue();
				break;
			case LinkFormat.PAGE:
					page = kvp.getIntValue();
				break;
			default:
				extraAttrsQuery.put(kvp.getName(), kvp.getValue());
			}
		}
		
		Iterator<Resource>  resIt = resources.iterator();
		
		while (resIt.hasNext()) {
			Resource res = resIt.next();
			if (res instanceof RDNodeResource) {
				RDNodeResource node = (RDNodeResource) res;
				if ( (sectorQuery.isEmpty() || sectorQuery.equals(node.getSector()))
				     && (endpointQuery.isEmpty() || endpointQuery.equals(node.getEndpointName()))
					 && (endpointTypeQuery.isEmpty() || node.getEndpointTypes().containsAll(endpointTypeQuery))
					 && (extraAttrsQuery.isEmpty() || matchExtraAttrsQuery(extraAttrsQuery, node.getExtraAttrs()))) {
				
					String result = "";
					result += "<"+node.getBase()+">;"+LinkFormat.END_POINT+"=\""+node.getEndpointName()+"\"";
					result += ";"+LinkFormat.SECTOR+"=\""+node.getSector()+"\"";
					if(!node.getEndpointTypes().isEmpty()){
						for (String et : node.getEndpointTypes()) {
							result += ";"+LinkFormat.END_POINT_TYPE+"=\""+et+"\"";
						}
					}
					if (!node.getExtraAttrs().isEmpty()){
						for (String key : node.getExtraAttrs().keySet()){
							result += ";"+key+"=\""+node.getExtraAttrs().get(key)+"\"";
						}
					}
					
					candidates.add(result);
				}
			}
		}
		
		if ((count < 0) || (page < 0)) {
			exchange.respond(ResponseCode.BAD_REQUEST);
		}
		else if (candidates.isEmpty() || ((count * page) >= candidates.size()) || (count == 0 && countPresent)) {
			exchange.respond(ResponseCode.CONTENT);
		} else {
			int from, to;
			if (countPresent) {
				from = count * page;
				to = (from + count > candidates.size()) ? candidates.size() : from + count;
			}
			else {
				from = 0;
				to = candidates.size();
			}
			String result = "";
			for (String s : candidates.subList(from, to)) {
				result += s + ",";
			}
			// also remove trailing comma
			exchange.respond(ResponseCode.CONTENT, result.substring(0, result.length()-1), MediaTypeRegistry.APPLICATION_LINK_FORMAT);
		}
	}

	private boolean matchExtraAttrsQuery(HashMap<String, String> queries, HashMap<String, String> nodeExtraAttrs) {
		if (!nodeExtraAttrs.keySet().containsAll(queries.keySet())) {
			return false;
		}
		for (String q : queries.keySet()) {
			if (!nodeExtraAttrs.get(q).equals(queries.get(q))) {
				return false;
			}
		}
		return true;
	}
}
