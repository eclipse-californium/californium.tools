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

import org.eclipse.californium.core.CoapResource;


public class RDLookUpTopResource extends CoapResource {
	
	public RDLookUpTopResource(RDResource rd){
		this("rd-lookup", rd);
	}

	public RDLookUpTopResource(String resourceIdentifier, RDResource rd) {
		super(resourceIdentifier);
		
		getAttributes().addResourceType("core.rd-lookup");
		add(new RDLookUpDomainResource("d", rd));
		add(new RDLookUpEPResource("ep", rd));
		add(new RDLookUpResResource("res", rd));
	}
}
