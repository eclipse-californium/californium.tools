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
 *    Martin Lanter - architect and initial implementation
 ******************************************************************************/
package org.eclipse.californium.tools.coapbench;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.tools.coapbench.producer.VeryEcoMessageProducer;

/**
 * A virtual client sends request to the server as fast as it can handle them.
 */
public class VirtualClient implements Runnable {

	public static final int TIMEOUT = 10000;
	
	private DatagramSocket socket;
	private DatagramPacket pSend;
	private DatagramPacket pRecv;
	private VeryEcoMessageProducer producer;
	
	private boolean runnable;
	private int counter;
	private int lost;
	
	private InetAddress destAddress;
	private int destPort;
	private byte[] mid;
	private long timestamp;
	
	private IntArray latencies;
	
	private boolean checkMID = true;
	private boolean checkCode = true;
	private boolean checkLatency = false;
	
	public VirtualClient(URI uri) throws Exception {
		this(uri, null);
	}
	
	public VirtualClient(URI uri, InetSocketAddress addr) throws Exception {
		this.mid = new byte[2];
		this.latencies = new IntArray();
		this.producer = new VeryEcoMessageProducer();
		this.pSend = new DatagramPacket(new byte[0], 0);
		this.pRecv = new DatagramPacket(new byte[100], 100);
		this.runnable = true;
		setURI(uri);
		bind(addr);
	}
	
	public void bind(InetSocketAddress addr) throws Exception {
		if (addr == null)
			this.socket = new DatagramSocket();
		else
			this.socket = new DatagramSocket(addr);
		this.socket.setSoTimeout(TIMEOUT);
	}
	
	public void setURI(URI uri)  throws UnknownHostException {
		destAddress = InetAddress.getByName(uri.getHost());
		destPort = uri.getPort();
		producer.setURI(uri);
	}
	
	public void run() {
		try {
			latencies.clear();
			while (runnable) {
				sendRequest();
				receiveResponse();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendRequest() throws IOException {
		byte[] bytes = producer.next();
		saveMID(bytes);
		pSend.setData(bytes);
		pSend.setAddress(destAddress);
		pSend.setPort(destPort);
		timestamp = System.nanoTime();
		socket.send(pSend);
	}
	
	public void receiveResponse() throws IOException {
		try {
			boolean mid_correct;
			long latency;
			do {
				socket.receive(pRecv);
				latency = System.nanoTime() - timestamp;
				byte[] resp = pRecv.getData();
				mid_correct = checkMID(resp);
				checkCode(resp);
			} while (!mid_correct);
			if (checkLatency)
				latencies.add((int) (latency / 1000000));
			counter++;
		} catch (SocketTimeoutException e) {
//			System.out.println("Timeout occured");
			lost++;
		}
	}
	
	public void stop() {
		runnable = false;
	}
	
	public void reset() {
		runnable = true;
		counter = 0;
		lost = 0;
	}
	
	public int getCount() {
		return counter;
	}
	
	public int getTimeouts() {
		return lost;
	}
	
	public IntArray getLatencies() {
		return latencies;
	}
	
	private void saveMID(byte[] bytes) {
		mid[0] = bytes[2];
		mid[1] = bytes[3];
	}
	
	private boolean checkMID(byte[] bytes) {
		int expected = ( ((mid[0] & 0xFF)<<8) + (mid[1] & 0xFF));
		int actual = ( ((bytes[2] & 0xFF)<<8) + (bytes[3] & 0xFF));
		if (checkMID && 
				(bytes[2] != mid[0] || bytes[3]!=mid[1]) ) {
			System.err.println("Received message with wrong MID, expected "+expected+ " but received "+actual);
			return false;
		}
		return true;
	}
	
	private void checkCode(byte[] bytes) {
		int c = 0xFF & bytes[1];
		if (checkCode && c != CoAP.ResponseCode.CONTENT.value) {
			System.err.println("Wrong response code: " + CoAP.ResponseCode.valueOf(c));
			System.exit(-1);
		}
	}

	public boolean isCheckLatency() {
		return checkLatency;
	}

	public void setCheckLatency(boolean checkLatency) {
		this.checkLatency = checkLatency;
	}
	
	public void close() {
		socket.close();
	}
}
