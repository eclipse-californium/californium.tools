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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.core.server.resources.ResourceBase;


public class RDLookUpDomainResource extends ResourceBase {

	private RDResource rdResource = null;
	
	public RDLookUpDomainResource(String resourceIdentifier, RDResource rd) {
		super(resourceIdentifier);
		this.rdResource = rd;
	}
	
	
	
	@Override
	public void handleGET(CoapExchange exchange) {
		
		Collection<Resource> resources = rdResource.getChildren();
		TreeSet<String> availableDomains = new TreeSet<String>(); 
		String domainQuery = ""; 
		Iterator<Resource>  resIt = resources.iterator();
		String result = "";
		
		List<String> queries = exchange.getRequestOptions().getURIQueries();
		for (String query:queries) {
			LinkAttribute attr = LinkAttribute.parse(query);
			if (attr.getName().equals(LinkFormat.DOMAIN))
				domainQuery = attr.getValue();
		}
		
		while (resIt.hasNext()){
			Resource res = resIt.next();
			if (res.getClass() == RDNodeResource.class){
				RDNodeResource node = (RDNodeResource) res;
				if ((domainQuery.isEmpty() || domainQuery.equals(node.getDomain()))){
					availableDomains.add(node.getDomain());
				}
			}
		}
		if(availableDomains.isEmpty()){
			exchange.respond(ResponseCode.NOT_FOUND);
			
		} else{
			Iterator<String>  domIt = availableDomains.iterator();
						
			while (domIt.hasNext()){
				String dom = domIt.next();
				result += "</rd>;"+LinkFormat.DOMAIN+"=\""+dom+"\",";
			}

			exchange.respond(ResponseCode.CONTENT, result.substring(0, result.length()-1), MediaTypeRegistry.APPLICATION_LINK_FORMAT);
		}
				
	}

}
