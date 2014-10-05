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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.network.config.NetworkConfig;
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
	private Timer validationTimer;
	
	private int lifeTime;
	private long expiryTime;
	
	private String endpointIdentifier;
	private String domain;
	private String endpointType;
	private String context;
	
	private byte[] etag = null;
	
	
	public RDNodeResource(String name, String endpointID, String domain) {
		super(name);		
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
		
		String newEndpointType = "";
		int newLifeTime = NetworkConfig.getStandard().getInt("RD_DEFAULT_LIFETIME");
		String newContext = "";
		
		/*
		 * get lifetime from option query - only for PUT request.
		 */
		List<String> query = request.getOptions().getURIQueries();
		for (String q : query) {
			// FIXME Do not use Link attributes for URI template variables
			attr = LinkAttribute.parse(q);

			if (attr.getName().equals(LinkFormat.END_POINT_TYPE)) {
				newEndpointType = attr.getValue();
			}
			
			if (attr.getName().equals(LinkFormat.LIFE_TIME)) {
				newLifeTime = attr.getIntValue();
				
				if (newLifeTime < 60) {
					LOGGER.warning("Enforcing minimal RD lifetime of 60 seconds (was "+newLifeTime+")");
					newLifeTime = 60;
				}
			}
			
			if (attr.getName().equals(LinkFormat.CONTEXT)){
				newContext = attr.getValue();
			}
		}
		
		setEndpointType(newEndpointType);
		setLifeTime(newLifeTime);

		// TODO check with draft authors if update should be atomic
		if (newContext.equals("")) {
			InetAddress source = request.getSource();
			String host = source.getHostName();
			if (host == null) {
				host = source.getHostAddress();
			}
			context = "coap://" + host + ":" + request.getSourcePort();
		} else {
			Request checkRequest = Request.newGet();

			try {
				checkRequest.setURI(context);
			} catch (Exception e) {
				LOGGER.warning(e.toString());
				return false;
			}
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
		if (validationTimer!=null) {
			validationTimer.cancel();
		}
		
		super.delete();
	}

	/*
	 * GET only debug return endpoint identifier
	 */
	@Override
	public void handleGET(CoapExchange exchange) {
		exchange.setMaxAge((int) Math.max((expiryTime - System.currentTimeMillis())/1000, 0));
		exchange.respond(ResponseCode.CONTENT, endpointIdentifier+"."+domain, MediaTypeRegistry.TEXT_PLAIN);
	}
	
	/*
	 * PUTs content to this resource. PUT is a periodic request from the
	 * node to update the lifetime.
	 */
	@Override
	public void handlePUT(CoapExchange exchange) {
		
		if (lifetimeTimer != null) {
			lifetimeTimer.cancel();
		}
		if (validationTimer!=null) {
			validationTimer.cancel();
		}
		
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
		
		expiryTime = System.currentTimeMillis() + lifeTime * 1000;
		
		if (lifetimeTimer != null) {
			lifetimeTimer.cancel();
		}
		if (validationTimer!=null) {
			validationTimer.cancel();
		}
		
		lifetimeTimer = new Timer();
		lifetimeTimer.schedule(new ExpiryTask(this), lifeTime * 1000);// from sec to ms
	
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

	// TODO: Merge into LinkFormat class
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
		linkFormat.append(resource.getPath().substring(this.getPath().length()+this.getName().length())+resource.getName());
		linkFormat.append(">");
		
		return linkFormat.append( LinkFormat.serializeResource(resource).toString().replaceFirst("<.+>", "") ).toString();
	}
	

	private void buildLinkFormat(Resource resource, StringBuilder builder, List<String> query) {
		if (resource.getChildren().size() > 0) {

			// Loop over all sub-resources
			for (Resource res : resource.getChildren()) {
				// System.out.println(resource.getSubResources().size());
				// System.out.println(res.getName());
				if (LinkFormat.matches(res, query) && res.getAttributes().getCount() > 0) {

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
			LOGGER.info("Scheduling validation of expired endpoint: "+getContext());
			validationTimer = new Timer();
			validationTimer.schedule(new ValidationTask(resource), NetworkConfig.getStandard().getInt("RD_VALIDATION_TIMEOUT") * 1000);
		}
	}
	
	class ValidationTask extends TimerTask {
		RDNodeResource resource;

		public ValidationTask(RDNodeResource resource) {
			super();
			this.resource = resource;
		}

		@Override
		public void run() {

			LOGGER.info("Validating endpoint: "+getContext());
			
			Request validationRequest = Request.newGet();
			validationRequest.setURI(getContext()+"/.well-known/core");
			if (etag!=null) {
				validationRequest.getOptions().addETag(etag);
			}
			Response response = null;
			
			try {
				validationRequest.send();
				response = validationRequest.waitForResponse();
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if (response == null) {
				
				delete();
				
			} else if(response.getCode() == ResponseCode.VALID) {
				
				LOGGER.fine("Resources up-to-date: "+getContext());
				
			} else if (response.getCode() == ResponseCode.CONTENT) {
	
				List<byte[]> etags = response.getOptions().getETags();
				
				if (!etags.isEmpty()) {
					etag = etags.get(0);
				}
	
				updateEndpointResources(response.getPayloadString());
				setLifeTime(lifeTime);
				
				LOGGER.fine("Updated Resources: " + getContext());
			}
		}
	}
	
}
