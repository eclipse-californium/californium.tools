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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;


public class RDNodeResource extends CoapResource {

	private static final Logger LOGGER = Logger.getLogger(RDNodeResource.class.getCanonicalName());
	
	/*
	 * After the lifetime expires, the endpoint has RD_VALIDATION_TIMEOUT seconds
	 * to update its entry before the RD enforces validation and removes the endpoint
	 * if it does not respond.
	 */
	private Timer lifetimeTimer;
	
	private int lifeTime;
	
	private String endpointIdentifier;
	private String domain;
	private String endpointType;
	private String context;
	
	public RDNodeResource(String endpointID, String domain) {
		super(endpointID);		
		this.endpointIdentifier = endpointID;
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

		LinkAttribute attr;
		
		int newLifeTime = 86400;
		String newContext = "";
		
		/*
		 * get lifetime from option query - only for PUT request.
		 */
		List<String> query = request.getOptions().getUriQuery();
		for (String q : query) {
			// FIXME Do not use Link attributes for URI template variables
			attr = LinkAttribute.parse(q);
			
			if (attr.getName().equals(LinkFormat.LIFE_TIME)) {
				newLifeTime = attr.getIntValue();
				
				if (newLifeTime < 60) {
					LOGGER.info("Enforcing minimal RD lifetime of 60 seconds (was "+newLifeTime+")");
					newLifeTime = 60;
				}
			}
			
			if (attr.getName().equals(LinkFormat.CONTEXT)){
				newContext = attr.getValue();
			}
		}
		
		setLifeTime(newLifeTime);
		
		try {
			URI check;
			if (newContext.equals("")) {
				check = new URI("coap", "", request.getSource().getHostName(), request.getSourcePort(), "", "", ""); // required to set port
				context = check.toString().replace("@", "").replace("?", "").replace("#", ""); // URI is a silly class
			} else {
				check = new URI(context);
			}
		} catch (Exception e) {
			LOGGER.warning(e.toString());
			return false;
		}
		
		return updateEndpointResources(request.getPayloadString());
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
				subResource = new RDTagResource(next,true, this);
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
		
		if (lifetimeTimer!=null) {
			lifetimeTimer.cancel();
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
		
		if (lifetimeTimer != null) {
			lifetimeTimer.cancel();
		}
		
		LOGGER.info("Updating endpoint: "+getContext());
		
		setParameters(exchange.advanced().getRequest());
		
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
		
		if (lifetimeTimer != null) {
			lifetimeTimer.cancel();
		}
		
		lifetimeTimer = new Timer();
		lifetimeTimer.schedule(new ExpiryTask(this), lifeTime * 1000 + 2000);// from sec to ms
	
	}
		
	/**
	 * Creates a new subResource for each resource the node wants
	 * register. Each resource is separated by ",". E.g. A node can
	 * register a resource for reading the temperature and another one
	 * for reading the humidity.
	 */
	private boolean updateEndpointResources(String linkFormat) {

		Scanner scanner = new Scanner(linkFormat);
		
		scanner.useDelimiter(",");
		List<String> pathResources = new ArrayList<String>();
		while (scanner.hasNext()) {
			pathResources.add(scanner.next());
		}
		for (String p : pathResources) {
			scanner = new Scanner(p);

			/*
			 * get the path of the endpoint's resource. E.g. from
			 * </readings/temp> it will select /readings/temp.
			 */
			String path = "", pathTemp = "";
			if ((pathTemp = scanner.findInLine("</.*?>")) != null) {
				path = pathTemp.substring(1, pathTemp.length() - 1);
			} else {
				scanner.close();
				return false;
			}
			
			CoapResource resource = addNodeResource(path);
			/*
			 * Since created the subResource, get all the attributes from
			 * the payload. Each parameter is separated by a ";".
			 */
			scanner.useDelimiter(";");
			//Clear attributes to make registration idempotent
			for(String attribute:resource.getAttributes().getAttributeKeySet()){
				resource.getAttributes().clearAttribute(attribute);
			}
			while (scanner.hasNext()) {
				LinkAttribute attr = LinkAttribute.parse(scanner.next());
				if (attr.getValue() == null)
					resource.getAttributes().addAttribute(attr.getName());
				else resource.getAttributes().addAttribute(attr.getName(), attr.getValue());
			}
			resource.getAttributes().addAttribute(LinkFormat.END_POINT, getEndpointIdentifier());
		}
		scanner.close();
		
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

	public String toLinkFormatItem(Resource resource) {
		StringBuilder linkFormat = new StringBuilder();
		
		linkFormat.append("<"+getContext());
		linkFormat.append(resource.getURI().substring(this.getURI().length()));
		linkFormat.append(">");
		
		return linkFormat.append( LinkFormat.serializeResource(resource).toString().replaceFirst("<.+>", "") ).toString();
	}
	

	private void buildLinkFormat(Resource resource, StringBuilder builder, List<String> query) {
		if (resource.getChildren().size() > 0) {

			// Loop over all sub-resources
			for (Resource res : resource.getChildren()) {
				if (LinkFormat.matches(res, query)) {

					// Convert Resource to string representation and add
					// delimiter
					builder.append(toLinkFormatItem(res));
					builder.append(',');
				}
				// Recurse
				buildLinkFormat(res, builder, query);
			}
		}
	}
	
	
	
	/*
	 * Setter And Getter
	 */

	public String getEndpointIdentifier() {
		return endpointIdentifier;
	}

	public String getDomain() {
		return domain;
	}

	public String getEndpointType() {
		return endpointType;
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
