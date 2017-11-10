/*******************************************************************************
 * Copyright (c) 2015, 2017 Institute for Pervasive Computing, ETH Zurich and others.
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
 *    Bosch Software Innovations GmbH - migrate to SLF4J
 ******************************************************************************/
package org.eclipse.californium.tools.resources;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.util.DaemonThreadFactory;


public class RDNodeResource extends CoapResource {

	private static final Logger LOGGER = LoggerFactory.getLogger(RDNodeResource.class.getCanonicalName());
	
	/*
	 * After the lifetime expires, the endpoint has RD_VALIDATION_TIMEOUT seconds
	 * to update its entry before the RD enforces validation and removes the endpoint
	 * if it does not respond.
	 */
	private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(//
			new DaemonThreadFactory("RDLifeTime#"));
	
	private int lifeTime = 86400;
	
	private String endpointName;
	private String domain;
	private String context;
	private String endpointType = "";
	private ScheduledFuture<?> ltExpiryFuture;
	
	public RDNodeResource(String ep, String domain) {
		super(ep);
		
		// check length restriction, but tolerantly accept
		int epLength = ep.getBytes(CoAP.UTF8_CHARSET).length;
		if (epLength>63) {
			LOGGER.warn("Endpoint Name '{}' too long ({} bytes)",
				new Object[] { ep, epLength } );
		}
		
		this.endpointName = ep;
		this.domain = domain;
	}

	/**
	 * Updates the endpoint parameters from POST and PUT requests.
	 *
	 * @param request A POST or PUT request with a {?et,lt,con} URI Template query
	 * 			and a Link Format payload.
	 * 
	 */
	public boolean setParameters(Request request) {

		boolean contextUpdated = false;
		String newContext = "";

		List<String> query = request.getOptions().getUriQuery();
		for (String q : query) {
			
			KeyValuePair kvp = KeyValuePair.parse(q);
			
			if (LinkFormat.END_POINT_TYPE.equals(kvp.getName()) && !kvp.isFlag()) {
				this.endpointType = kvp.getValue();
			}
			
			if (LinkFormat.LIFE_TIME.equals(kvp.getName()) && !kvp.isFlag()) {
				lifeTime = kvp.getIntValue();
				if (lifeTime < 60) {
					LOGGER.info("Enforcing minimal RD lifetime of 60 seconds (was "+lifeTime+")");
					lifeTime = 60;
				}
			}
			
			if (LinkFormat.CONTEXT.equals(kvp.getName()) && !kvp.isFlag()) {
				newContext = kvp.getValue();
				contextUpdated = true;
			}
		}
		// apply context from source address or con variable
		if (context==null || contextUpdated) {
			try {
				setContextFromRequest(request, newContext);
			} catch (Exception e) {
				LOGGER.warn(
						"Invalid context '{}' from {}:{}: {}",
						new Object[] { newContext, 
								request.getSource().getHostAddress(), 
								request.getSourcePort(), e });

				return false;
			}
		}

		// set lifetime on first call
		if (ltExpiryFuture==null) {
			setLifeTime(lifeTime);
		}
		
		return updateEndpointResources(request.getPayloadString());
	}

	private void setContextFromRequest(Request request, String newContext) 
			throws URISyntaxException {
		URI check;
		String scheme, host = null;
		int port = -1;
		
		// get the scheme from the request.		
		scheme = request.getScheme();  
		if (scheme == null || scheme.isEmpty()) {  // issue #38 & pr #42
			// assume default scheme
			scheme = CoAP.COAP_URI_SCHEME;
			// Check if Uri-Port option is set and to default port.
			if (request.getOptions().getUriPort() != null &&
					request.getOptions().getUriPort().intValue() ==
						CoAP.DEFAULT_COAP_SECURE_PORT) {
				scheme = CoAP.COAP_SECURE_URI_SCHEME;
			}
			request.setScheme(scheme);
		}
		
		if (!newContext.isEmpty()) {
			// context from URI template variable
			check = new URI(newContext);
			// continue checking as "coap:///" is valid a URI.  
			scheme = check.getScheme();
			host = check.getHost();
			port = check.getPort();
		}
		if(scheme == null) { // Should honor request's scheme
			scheme = request.getScheme();
		}
		if (host == null) {  // RFC says: use source address when not set
			host = request.getSource().getHostAddress();
		}
		if (port < 0) {  // RFC says: use source port when not set
			port = request.getSourcePort();
		}
		
		// set context from gathered values
		check = new URI(scheme, null, host, port, null, null, null); // required to set port
		// CoAP context template: coap[s?]://<host>:<port>
		this.context = check.toString();
	}

	/*
	 * add a new resource to the node. E.g. the resource temperature or
	 * humidity. If the path is /readings/temp, temp will be a subResource
	 * of readings, which is a subResource of the node.
	 */
	public CoapResource addNodeResource(String path) {
		Scanner scanner = new Scanner(path);
		scanner.useDelimiter("/");
		String next = "";
		boolean resourceExist = false;
		Resource resource = this; // It's the resource that represents the endpoint
		
		CoapResource subResource = null;
		while (scanner.hasNext()) {
			resourceExist = false;
			next = scanner.next();
			for (Resource res : resource.getChildren()) {
				if (res.getName().equals(next)) {
					subResource = (CoapResource) res;
					resourceExist = true;
				}
			}
			if (!resourceExist) {
				subResource = new RDTagResource(next, true, this);
				resource.add(subResource);
			}
			resource = subResource;
		}
		subResource.setPath(resource.getPath());
		subResource.setName(next);
		scanner.close();
		return subResource;
	}

	@Override
	public void delete() {

		LOGGER.info("Removing endpoint: "+getContext());
		
		if (ltExpiryFuture!=null) {
			// delete may be called from within the future
			ltExpiryFuture.cancel(false);
		}
		
		super.delete();
	}

	/*
	 * GET only debug return endpoint identifier
	 */
	@Override
	public void handleGET(CoapExchange exchange) {
		exchange.respond(ResponseCode.FORBIDDEN, "RD update handle");
	}
	
	/*
	 * PUTs content to this resource. PUT is a periodic request from the
	 * node to update the lifetime.
	 */
	@Override
	public void handlePOST(CoapExchange exchange) {
		
		if (ltExpiryFuture != null) {
			ltExpiryFuture.cancel(true); // try to cancel before delete is called
		}
		
		LOGGER.info("Updating endpoint: "+getContext());
		
		setParameters(exchange.advanced().getRequest());
		
		// reset lifetime
		setLifeTime(this.lifeTime);
		
		// complete the request
		exchange.respond(ResponseCode.CHANGED);
		
	}
	
	/*
	 * DELETEs this node resource
	 */
	@Override
	public void handleDELETE(CoapExchange exchange) {
		delete();
		exchange.respond(ResponseCode.DELETED);
	}

	/*
	 * set either a new lifetime (for new resources, POST request) or update
	 * the lifetime (for PUT request)
	 */
	public void setLifeTime(int newLifeTime) {
		
		lifeTime = newLifeTime;
		
		if (ltExpiryFuture != null) {
			ltExpiryFuture.cancel(true);
		}
		
		ltExpiryFuture = scheduler.schedule(new Runnable() {
			@Override
			public void run() {
				delete();
			}			
		}, lifeTime + 2, // contingency time
				TimeUnit.SECONDS);
	
	}
		
	/**
	 * Creates a new subResource for each resource the node wants
	 * register. Each resource is separated by ",". E.g. A node can
	 * register a resource for reading the temperature and another one
	 * for reading the humidity.
	 */
	private boolean updateEndpointResources(String linkFormat) {
		
		Set<WebLink> links = LinkFormat.parse(linkFormat);
		
		for (WebLink l : links) {
			
			CoapResource resource = addNodeResource(l.getURI().substring(l.getURI().indexOf("/")));
			
			// clear attributes to make registration idempotent
			for (String attribute : resource.getAttributes().getAttributeKeySet()) {
				resource.getAttributes().clearAttribute(attribute);
			}
			
			// copy to resource list
			for (String attribute : l.getAttributes().getAttributeKeySet()) {
				for (String value : l.getAttributes().getAttributeValues(attribute)) {
					resource.getAttributes().addAttribute(attribute, value);
				}
			}
			
			resource.getAttributes().setAttribute(LinkFormat.END_POINT, getEndpointName());
		}
		
		return true;
	}

	/*
	 * the following three methods are used to print the right string to put in
	 * the payload to respond to the GET request.
	 */
	public String toLinkFormat(List<String> query) {

		// Create new StringBuilder
		StringBuilder builder = new StringBuilder();
		
		// Build the link format
		buildLinkFormat(this, builder, query);

		// Remove last delimiter
		if (builder.length() > 0) {
			builder.deleteCharAt(builder.length() - 1);
		}

		return builder.toString();
	}

	private void buildLinkFormat(Resource resource, StringBuilder builder, List<String> query) {
		if (resource.getChildren().size() > 0) {

			// Loop over all sub-resources
			for (Resource res : resource.getChildren()) {
				if (LinkFormat.matches(res, query)) {
					// Convert Resource to string representation
					builder.append("<"+getContext());
					builder.append(res.getURI().substring(this.getURI().length()));
					builder.append(">");
					builder.append( LinkFormat.serializeResource(res).toString().replaceFirst("<.+>", "") );
				}
				// Recurse
				buildLinkFormat(res, builder, query);
			}
		}
	}
	
	
	
	/*
	 * Setter And Getter
	 */

	public String getEndpointName() {
		return endpointName;
	}

	public String getDomain() {
		return domain;
	}

	public String getEndpointType() {
		return endpointType==null ? "" : endpointType;
	}

	public void setEndpointType(String endpointType) {
		this.endpointType = endpointType;
	}

	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
	}
	
	class ExpiryTask extends TimerTask {
		RDNodeResource resource;

		public ExpiryTask(RDNodeResource resource) {
			super();
			this.resource = resource;
		}

		@Override
		public void run() {
			delete();
		}
	}
	
}
