/*******************************************************************************
 * Copyright (c) 2015, 2017 Institute for Pervasive Computing, ETH Zurich and others.
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
 *    Bosch Software Innovations GmbH - migrate to SLF4J
 ******************************************************************************/
package org.eclipse.californium.tools.resources;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeSet;
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
	
	private int lifeTime = 90000;
	
	private String endpointName;
	private String sector;
	private String base;
	private TreeSet<String> endpointType = new TreeSet<>();
	private HashMap<String, String> extraAttrs = new HashMap<>();
	private ScheduledFuture<?> ltExpiryFuture;
	
	public RDNodeResource(String ep, String sector) {
		super(ep);
		
		// check length restriction, but tolerantly accept
		int epLength = ep.getBytes(CoAP.UTF8_CHARSET).length;
		if (epLength>63) {
			LOGGER.warn("Endpoint Name '{}' too long ({} bytes)",
				new Object[] { ep, epLength } );
		}
		
		this.endpointName = ep;
		this.sector = sector;
	}

	/**
	 * Updates the endpoint parameters from POST and PUT requests.
	 *
	 * @param request A POST or PUT request with a {?et,lt,base} URI Template query
	 * 			and a Link Format payload.
	 * 
	 */
	public boolean setParameters(Request request) {

		boolean baseUpdated = false;
		String newBase = "";

		List<String> query = request.getOptions().getUriQuery();
		for (String q : query) {

			KeyValuePair kvp = KeyValuePair.parse(q);

			// we parsed ep and sector already
			if ((kvp.getName().equals(LinkFormat.END_POINT)) || (kvp.getName().equals(LinkFormat.SECTOR))) {
				continue;
			}

			if (kvp.isFlag()) {
				this.extraAttrs.put(kvp.getName(), "");
				continue;
			}

			switch (kvp.getName()) {
			case LinkFormat.END_POINT_TYPE:
				this.endpointType.add(kvp.getValue());
				break;
			case LinkFormat.LIFE_TIME:
				lifeTime = kvp.getIntValue();
				if (lifeTime < 60) {
					LOGGER.info("Enforcing minimal RD lifetime of 60 seconds (was " + lifeTime + ")");
					lifeTime = 60;
				}
				break;
			case LinkFormat.BASE:
				newBase = kvp.getValue();
				baseUpdated = true;
				break;
			default:
				this.extraAttrs.put(kvp.getName(), kvp.getValue());
			}
		}
		// apply base from source address or con variable
		if (base==null || baseUpdated) {
			try {
				setBaseFromRequest(request, newBase);
			} catch (Exception e) {
				LOGGER.warn(
						"Invalid base '{}' from {}:{}: {}",
						new Object[] { newBase, 
								request.getSourceContext().getPeerAddress().getHostString(),
								request.getSourceContext().getPeerAddress().getPort(), e });

				return false;
			}
		}

		// set lifetime on first call
		if (ltExpiryFuture==null) {
			setLifeTime(lifeTime);
		}
		
		return updateEndpointResources(request.getPayloadString());
	}

	private void setBaseFromRequest(Request request, String newBase) 
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
		
		if (!newBase.isEmpty()) {
			// base from URI template variable
			check = new URI(newBase);
			// continue checking as "coap:///" is valid a URI.  
			scheme = check.getScheme();
			host = check.getHost();
			port = check.getPort();
		}
		if(scheme == null) { // Should honor request's scheme
			scheme = request.getScheme();
		}
		if (host == null) {  // RFC says: use source address when not set
			host = request.getSourceContext().getPeerAddress().getHostString();
		}
		if (port < 0) {  // RFC says: use source port when not set
			port = request.getSourceContext().getPeerAddress().getPort();
		}
		
		// set base from gathered values
		check = new URI(scheme, null, host, port, null, null, null); // required to set port
		// CoAP base template: coap[s?]://<host>:<port>
		this.base = check.toString();
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

		LOGGER.info("Removing endpoint: "+getBase());
		
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
		
		LOGGER.info("Updating endpoint: "+getBase());
		
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
	 * the following two methods put the resources of this node
	 * in a list of strings in link format. Each string ends with ','.
	 */
	public List<String> toLinkFormat(List<String> query) {
		// Build the link format recursively
		return buildLinkFormat(this, query);
	}

	private List<String> buildLinkFormat(Resource resource, List<String> query) {
		List<String> resources = new ArrayList<>();
		if (resource.getChildren().size() > 0) {

			// Loop over all sub-resources
			for (Resource res : resource.getChildren()) {
				StringBuilder sb = new StringBuilder();
				if (LinkFormat.matches(res, query)) {
					// Convert Resource to string representation
					sb.append("<"+getBase());
					sb.append(res.getURI().substring(this.getURI().length()));
					sb.append(">");
					sb.append(LinkFormat.serializeResource(res).toString().replaceFirst("<.+>", ""));
				}
				if (sb.length() != 0) {
					resources.add(sb.toString());
				}
				// Recurse
				resources.addAll(buildLinkFormat(res, query));
			}
		}
		return resources;
	}
	
	
	
	/*
	 * Setter And Getter
	 */

	public String getEndpointName() {
		return endpointName;
	}

	public String getSector() {
		return sector;
	}

	public Set<String> getEndpointTypes() {
		return endpointType;
	}

	public void addEndpointType(String endpointType) {
		this.endpointType.add(endpointType);
	}

	public String getBase() {
		return base;
	}

	public void setBase(String base) {
		this.base = base;
	}

	public HashMap<String, String> getExtraAttrs() {
		return extraAttrs;
	}

	public void addExtraAttrs(String name, String value) {
			this.extraAttrs.put(name, value);
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
