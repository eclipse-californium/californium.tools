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
 *    Martin Lanter - architect and initial implementation
 ******************************************************************************/
package org.eclipse.californium.tools.coapbench;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.serialization.Serializer;
import org.eclipse.californium.tools.coapbench.producer.VeryEcoNotificationProducer;

/**
 * This class serves as a virtual server used to handle GET/OBSERVE process with 
 * minimal overhead possible. Upon receiving a GET/OBSERVE request ties itself to
 * a client and pushes notifications to it until 
 */
public class VirtualServer implements Runnable, VirtualDevice {
	
	public static final int TIMEOUT = 2000;
	
	private DatagramSocket socket;
	private DatagramPacket pSend;
	private DatagramPacket pRecv;
	
	private InetSocketAddress bindAddress;
	private InetAddress destAddress;
	private int destPort;
	private URI postURI;
	
	private boolean runnable;
	private boolean confirmable;
	private boolean registered = false;
	
	private boolean checkLatency = false;
	private ArrayList<Integer> latencies;
	
	private int counter;
	private int lost;
	private long timestamp;
	
	private CyclicBarrier barrier;
	
	private VeryEcoNotificationProducer producer;
	
	public VirtualServer(URI uri) throws Exception {
		this(uri, null, false, false);
	}
	
	public VirtualServer(URI uri, InetSocketAddress addr) throws Exception {
		this(uri, addr, false, false);
	}
	
	public VirtualServer(URI uri, InetSocketAddress addr, boolean observable, boolean confirmable) throws Exception {
		this.pSend = new DatagramPacket(new byte[0], 0);
		this.pRecv = new DatagramPacket(new byte[100], 100);
		this.runnable = true;
		this.bindAddress = addr;
		this.postURI = uri;
		this.confirmable = confirmable;
		this.latencies = new ArrayList<Integer>();

		bind(addr);
	}
	
	public VirtualServer(URI uri, InetSocketAddress addr, boolean observable, boolean confirmable, CyclicBarrier barrier) throws Exception {
		this(uri, addr, observable, confirmable);
		this.barrier = barrier;
	}

	public void bind(InetSocketAddress addr) throws Exception {
		if (this.socket == null || this.socket.isClosed()) {
			if (addr == null)
				this.socket = new DatagramSocket();
			else
				this.socket = new DatagramSocket(addr);
		}
	}
	
	@Override
	public void run() {
		try {
			bind(bindAddress);
			
			postResource();
			registerObserver();
			if (!runnable || !registered) {
				return;
			}
			
			socket.setSoTimeout(1000);
			if (barrier != null) 
				barrier.await();
			
			pSend.setAddress(destAddress);
			pSend.setPort(destPort);

			// the if is moved outside of the test loop to reduce the logic processing overhead
			if (confirmable) {
				while (runnable) {
					notifyObserver();
					awaitAck();
				}
			}
			else {
				while (runnable) {
					notifyObserver();
				}
			}
			
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			
			byte[] next = producer.next();
			byte[] deleteMessage = Arrays.copyOfRange(next, 0, 4 + next[0] & 0x0F);
			deleteMessage[1] = (byte) 0x84; // Not found.
			pSend.setData(deleteMessage);
			
			int i = 10;
			socket.setSoTimeout(1000);
			for (;; --i) {
				try {
					socket.send(pSend);
					if (confirmable)
						socket.receive(pRecv);
					break;
				} catch (SocketTimeoutException ste) {
					continue;
				}
			}

			if (i == 0)
				System.err.println("Virtual server (:" + socket.getLocalPort() + ") didn't manage to break the relation!");
			
		} catch (BrokenBarrierException bbe) {
			if (registered) {
				byte[] next = producer.next();
				byte[] deleteMessage = Arrays.copyOfRange(next, 0, 4 + next[0] & 0x0F);
				deleteMessage[1] = (byte) 0x84; // Not found.
				pSend.setData(deleteMessage);
				
				try {
					socket.send(pSend);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			stop();
			close();
		}
	}
	
	public void postResource() throws IllegalArgumentException {
		Request req = new Request(Code.POST);
		if (bindAddress != null)
			req.setPayload("coap://" + bindAddress.getHostString() + ":" + socket.getLocalPort() + "/benchmark");
		else
			req.setPayload("coap://127.0.0.1:" + socket.getLocalPort() + "/benchmark");
		req.setToken(new byte[0]);
		req.setMID(0);
		req.setType(Type.CON);
		req.setURI(postURI);
		
		pSend.setData(new Serializer().serialize(req).getBytes());
		try {
			pSend.setAddress(InetAddress.getByName(postURI.getHost()));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		for (int i = 10; i > 0 && runnable; --i) {
			try {
				socket.setSoTimeout(1000);
				if (postURI.getPort() != -1)
					pSend.setPort(postURI.getPort());
				else
					pSend.setPort(5683);
				socket.send(pSend);
				socket.receive(pRecv);
				return;
			} catch (SocketTimeoutException ste) {
				continue;
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
		
		System.err.println("Virtual server (:" + socket.getLocalPort() + ") timed out waiting for a POST response.");
		runnable = false;
		return;
		
	}
	
	public void registerObserver() throws IOException {
		while (!registered && runnable) {
			try {
				socket.setSoTimeout(5000);
				socket.receive(pRecv);
				byte[] req = pRecv.getData();
			
				if ((req[0] & 0xC0) == 0x40 && req[1] == 0x01 && req[8] == 0x60) {
					ByteArrayWrapper token = new ByteArrayWrapper(Arrays.copyOfRange(req, 4, 4 + new Integer(req[0] & 0x0F)));
					
					destAddress = pRecv.getAddress();
					destPort = pRecv.getPort();
					
					producer = new VeryEcoNotificationProducer(token.getData(), (getIntFromWord(ByteBuffer.wrap(Arrays.copyOfRange(req, 2, 4)).array()) + 1) % 65536, confirmable);
					registered = true;
				} 
				
			} catch (SocketException se) {
				se.printStackTrace();
				runnable = false;

			} catch (SocketTimeoutException ste) {
				System.err.println("Virtual server (:" + socket.getLocalPort() + ") timed out waiting for an observer.");
				break;
			}
		}
	}
	
	private int getIntFromWord(byte[] array) {
		if (array.length != 2) {
			return -1;
		}
		
		return array[0] << 8 & 0xFF00 | array[1] & 0xFF;
	}
	

	
	/* notifyAllObservers() simply iterates over the registered observer descriptors and sends
	 * an empty notification to each of them.
	 */
	public void notifyObserver() throws IOException {
		pSend.setData(producer.next());
		socket.send(pSend);
		
		if (!confirmable) {
			++counter;
		}
		
		else if (checkLatency) {
			timestamp = System.nanoTime();
		}
	}
	
	/* Should the server be configured to send confirmable notifications, it will have to wait
	 * for the acknowledgements from the clients. It also counts the number of acknowledgements
	 * for statistics.
	 */
	public void awaitAck() {		
		try {
			while (true) {
				socket.receive(pRecv);
				if (checkLatency) latencies.add((int)(System.nanoTime() - timestamp));
				++counter;
				return;
			}
				
		} catch (SocketTimeoutException e) { // regular timeout
			++lost;
			return;
			
		} catch (Exception e) { // pass-through
			e.printStackTrace();
			return;
		}
	}
	
	public void startNotifications() {
		runnable = true;
	}
	
	@Override
	public int getCount() {
		return counter;
	}

	@Override
	public int getTimeouts() {
		return lost;
	}
	
	@Override
	public boolean isCheckLatency() {
		return checkLatency;
	}

	@Override
	public void setCheckLatency(boolean checkLatency) { 
		this.checkLatency = checkLatency;
	}

	@Override
	public ArrayList<Integer> getLatencies() {
		return latencies;
	}
	
	@Override
	public void setURI(URI uri)  throws UnknownHostException {
		postURI = uri;
	}

	@Override
	public void reset() { 
		lost = 0; 
		counter = 0; 
		runnable = true;
		registered = false;
	}
	
	public void setBarrier(CyclicBarrier barrier) {
		this.barrier = barrier;
	}
	
	public void setConfirmable(boolean confirmable) {
		this.confirmable = confirmable;
	}
	
	@Override
	public boolean isRunning() {
		return runnable;
	}
	
	public void stop() {
		runnable = false;		
	}
	
	public void close() {
		if (socket != null && !socket.isClosed())
			socket.close();
	}
	
	private final class ByteArrayWrapper
	{
	    private final byte[] data;

	    public ByteArrayWrapper(byte[] data)
	    {
	        if (data == null)
	        {
	            throw new NullPointerException();
	        }
	        this.data = data;
	    }

	    @Override
	    public boolean equals(Object other)
	    {
	        if (!(other instanceof ByteArrayWrapper))
	        {
	            return false;
	        }
	        return Arrays.equals(data, ((ByteArrayWrapper)other).data);
	    }

	    @Override
	    public int hashCode()
	    {
	        return Arrays.hashCode(data);
	    }
	    
	    public byte[] getData()
	    {
	    	return data.clone();
	    }
	}

}
