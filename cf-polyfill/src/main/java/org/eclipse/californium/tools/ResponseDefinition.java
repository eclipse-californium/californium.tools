/*******************************************************************************
 * Copyright (c) 2016 Institute for Pervasive Computing, ETH Zurich and others.
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
 *    Yassin N. Hassan - Polyfill implementation
 ******************************************************************************/
package org.eclipse.californium.tools;

public class ResponseDefinition {
	public final int code;
	public final String payload;

	public ResponseDefinition(int code, String payload) {
		this.code = code;
		this.payload = payload;
	}
}
