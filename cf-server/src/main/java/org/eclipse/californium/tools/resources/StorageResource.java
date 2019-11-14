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

import java.util.Arrays;
import java.util.LinkedList;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;


/**
 * This resource allows to store an arbitrary payload in any subresource. If the
 * target subresource does not yet exist it will be created. Therefore, such a
 * resource can be though off as having all possible children.
 * <p>
 * <ul>
 * <li>A GET request receives the currently stored data within the target
 * resource.
 * <li>A POST request creates the specified resources from the payload.
 * <li>A PUT request stores the payload within the target resource.
 * <li>A DELETE request deletes the target resource.
 * </ul>
 * <p>
 * Assume a single instance of this resource called "storage". Assume a client
 * sends a PUT request with Payload "foo" to the URI storage/A/B/C. When the
 * resource storage receives the request, it creates the resources A, B and C
 * and delivers the request to the resource C. Resource C will process the PUT
 * request and stare "foo". If the client sends a consecutive GET request to the
 * URI storage/A/B/C, resource C will respond with the payload "foo".
 */
public class StorageResource extends CoapResource {

	private String content;
	
	public StorageResource(String name) {
		super(name);
	}
	
	@Override
	public void handleGET(CoapExchange exchange) {
		if (content != null) {
			exchange.respond(content);
		} else {
			String subtree = LinkFormat.serializeTree(this);
			exchange.respond(ResponseCode.CONTENT, subtree, MediaTypeRegistry.APPLICATION_LINK_FORMAT);
		}
	}

	@Override
	public void handlePOST(CoapExchange exchange) {
		String payload = exchange.getRequestText();
		String[] parts = payload.split("\\?");
		String[] path = parts[0].split("/");
		Resource resource = create(new LinkedList<String>(Arrays.asList(path)));
		
		Response response = new Response(ResponseCode.CREATED);
		response.getOptions().setLocationPath(resource.getURI());
		exchange.respond(response);
	}

	@Override
	public void handlePUT(CoapExchange exchange) {
		content = exchange.getRequestText();
		exchange.respond(ResponseCode.CHANGED);
	}

	@Override
	public void handleDELETE(CoapExchange exchange) {
		this.delete();
		exchange.respond(ResponseCode.DELETED);
	}

	/**
	 * Find the requested child. If the child does not exist yet, create it.
	 */
	@Override
	public Resource getChild(String name) {
		Resource resource = super.getChild(name);
		if (resource == null) {
			resource = new StorageResource(name);
			add(resource);
		}
		return resource;
	}
	
	/**
	 * Create a resource hierarchy with according to the specified path.
	 * @param path the path
	 * @return the lowest resource from the hierarchy
	 */
	private Resource create(LinkedList<String> path) {
		String segment;
		do {
			if (path.size() == 0)
				return this;
		
			segment = path.removeFirst();
		} while (segment.isEmpty() || segment.equals("/"));
		
		StorageResource resource = new StorageResource(segment);
		add(resource);
		return resource.create(path);
	}

}
