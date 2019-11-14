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
 *    Martin Lanter - architect and initial implementation
 ******************************************************************************/

package org.eclipse.californium.tools.coapbench;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;

public interface VirtualDevice {

	public void bind(InetSocketAddress addr) throws Exception;
	public void setURI(URI uri) throws UnknownHostException;
	
	public boolean isCheckLatency();
	public void setCheckLatency(boolean checkLatency);
	
	public int getCount();
	public int getTimeouts();
	public ArrayList<Integer> getLatencies();
	
	public boolean isRunning();
	
	public void close();
	public void stop();
	public void reset();
}
