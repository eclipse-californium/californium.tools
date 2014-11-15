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
	}

	
	@Override
	public void handleGET(CoapExchange exchange) {
		Collection<Resource> resources = rdResource.getChildren();
		String result = "";
		String domainQuery = "";
		String endpointQuery = "";
		List<String> toRemove = new ArrayList<String>(); 
		
		List<String> query = exchange.getRequestOptions().getUriQuery();
		
		for (String q : query) {
			LinkAttribute attr = LinkAttribute.parse(q);
			if(attr.getName().equals(LinkFormat.DOMAIN)){
				domainQuery=attr.getValue();
				if(domainQuery==null){
					exchange.respond(ResponseCode.BAD_REQUEST);
					return;
				}
				toRemove.add(q);
			}
			if(attr.getName().equals(LinkFormat.END_POINT)){
				endpointQuery = attr.getValue();
				if(endpointQuery==null){
					exchange.respond(ResponseCode.BAD_REQUEST);
					return;
				}
				toRemove.add(q);
			}
		}
		
		
		Iterator<Resource>  resIt = resources.iterator();
		System.out.println(endpointQuery);
				
		query.removeAll(toRemove);
		
		while (resIt.hasNext()){
			Resource res = resIt.next();
			if (res.getClass() == RDNodeResource.class){
				RDNodeResource node = (RDNodeResource) res;
				if ( (domainQuery.isEmpty() || domainQuery.equals(node.getDomain())) && 
					 (endpointQuery.isEmpty() || endpointQuery.equals(node.getEndpointIdentifier())) ) {
					String link = node.toLinkFormat(query);
					result += (!link.isEmpty()) ? link+"," : ""; 
				}
			}
		}
		if(result.isEmpty()){
			exchange.respond(ResponseCode.NOT_FOUND);
		}
		else{
			exchange.respond(ResponseCode.CONTENT, result.substring(0,result.length()-1),MediaTypeRegistry.APPLICATION_LINK_FORMAT);
		}
		
	}
}
