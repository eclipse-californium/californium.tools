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
package org.eclipse.californium.tools.resources;

import java.util.Collection;
import java.util.Collections;
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
	}

	
	@Override
	public void handleGET(CoapExchange exchange) {
		Collection<Resource> resources = rdResource.getChildren();
		String result = "";
		String domainQuery = "";
		String endpointQuery = "";
		TreeSet<String> endpointTypeQuery = new TreeSet<String>();

		List<String> query = exchange.getRequestOptions().getUriQuery();
		for (String q : query) {
			KeyValuePair kvp = KeyValuePair.parse(q);
			
			if (LinkFormat.DOMAIN.equals(kvp.getName())) {
				domainQuery = kvp.getValue();
			}
			
			if (LinkFormat.END_POINT.equals(kvp.getName())) {
				endpointQuery = kvp.getValue();
			}
			
			if (LinkFormat.END_POINT_TYPE.equals(kvp.getName())) {
				Collections.addAll(endpointTypeQuery, kvp.getValue().split(" "));
			}
		}
		
		Iterator<Resource>  resIt = resources.iterator();
		
		while (resIt.hasNext()) {
			Resource res = resIt.next();
			if (res instanceof RDNodeResource) {
				RDNodeResource node = (RDNodeResource) res;
				if ( (domainQuery.isEmpty() || domainQuery.equals(node.getDomain()))
				     && (endpointQuery.isEmpty() || endpointQuery.equals(node.getEndpointName()))
					 && (endpointTypeQuery.isEmpty() || endpointTypeQuery.contains(node.getEndpointType())) ) {
				
					result += "<"+node.getContext()+">;"+LinkFormat.END_POINT+"=\""+node.getEndpointName()+"\"";
					result += ";"+LinkFormat.DOMAIN+"=\""+node.getDomain()+"\"";
					if(!node.getEndpointType().isEmpty()){
						result += ";"+LinkFormat.RESOURCE_TYPE+"=\""+node.getEndpointType()+"\"";
					}
					
					result += ",";
				}
			}
		}
		
		if (result.isEmpty()) {
			exchange.respond(ResponseCode.NOT_FOUND);
		} else {
			// also remove trailing comma
			exchange.respond(ResponseCode.CONTENT, result.substring(0, result.length()-1), MediaTypeRegistry.APPLICATION_LINK_FORMAT);
		}
	}
}
