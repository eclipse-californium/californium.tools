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

/**
 * This class implements attributes of the CoRE Link Format.
 */
public class KeyValuePair {
	
// Members /////////////////////////////////////////////////////////////////////
	
	private String name;
	private String value;

// Constructors ////////////////////////////////////////////////////////////////
	
	public KeyValuePair() {
		
	}
	
	public KeyValuePair(String name, String value) {
		this.name = name;
		this.value = value;
	}
	public KeyValuePair(String name, int value) {
		this.name = name;
		this.value = Integer.valueOf(value).toString();
	}
	public KeyValuePair(String name) {
		this.name = name;
		this.value = null;
	}

// Serialization ///////////////////////////////////////////////////////////////
	
	public static KeyValuePair parse(String str) {
		String[] keyValue = str.split("=");
		if (keyValue.length==1) {
			return new KeyValuePair(keyValue[0]);
		} else {
			return new KeyValuePair(keyValue[0], keyValue[1]);
		}
	}
	
	public boolean isFlag() {
		return this.value==null;
	}
	
	public String getName() {
		return name;
	}
	
	public String getValue() {
		return value==null ? "" : value;
	}
	
	public int getIntValue() {
		return Integer.parseInt(value);
	}

}
