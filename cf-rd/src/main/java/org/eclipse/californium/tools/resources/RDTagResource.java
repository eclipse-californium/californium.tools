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

import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.californium.core.CoapResource;


public class RDTagResource extends CoapResource {

	private HashMap<String, String> tagsMap;
	private RDNodeResource parentNode;
	
	public RDTagResource(String resourceIdentifier, boolean hidden, RDNodeResource parent) {
		super(resourceIdentifier, hidden);
		tagsMap = new HashMap<String,String>();
		parentNode = parent;
		
		setVisible(false);
	}
	
	public boolean containsTag(String tag, String value){
		if(tagsMap.containsKey(tag.toLowerCase())){
			return tagsMap.get(tag.toLowerCase()).equals(value.toLowerCase());
		}
		return false;
	}
	
	public boolean containsMultipleTags(HashMap<String, String> tags){
		for(String tag : tags.keySet()){
			if(!containsTag(tag, tags.get(tag))){
				return false;
			}
		}
		return true;
	}
	
	public HashMap<String, String> getTags(){
		return tagsMap;
	}
	
	public void addTag(String tag, String value){
		tagsMap.put(tag.toLowerCase(), value.toLowerCase());
	}
	
	
	
	public void addMultipleTags(HashMap<String, String> tags){
		for(String tag : tags.keySet()){
			addTag(tag, tags.get(tag));			
		}
	}
	
	
	public void removeMultipleTags(HashSet<String> tags){
		for(String tag : tags){
			tagsMap.remove(tag.toLowerCase());
		}
	}
	
	public RDNodeResource getParentNode(){
		return parentNode;
	}

}
