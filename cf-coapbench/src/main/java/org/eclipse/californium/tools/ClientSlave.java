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
package org.eclipse.californium.tools;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.eclipse.californium.tools.coapbench.Command;
import org.eclipse.californium.tools.coapbench.VirtualDeviceManager;


/**
 * The client slave keeps a TCP connection to the master. The master sends
 * commands to the slave.
 */
public class ClientSlave {

	public static final String CMD_PING = "ping";
	public static final String CMD_EXIT = "exit";
	public static final String CMD_STRESS = "stress";
	public static final String CMD_BENCH = "bench";
	public static final String CMD_OBSERVE_BENCH = "observe";
	public static final String CMD_OBSERVE_START = "observe_start";
	public static final String CMD_OBSERVE_READY = "observe_ready";
	public static final String CMD_OBSERVE_FAIL = "observe_fail";
	public static final String CMD_APACHE_BENCH = "ab";
	
	private InetAddress address;
	private int port;
	private Socket socket;
	private boolean verbose;
	
	private VirtualDeviceManager vdm;
	private ApacheBench ab;
	
	public ClientSlave(InetAddress address, int port) throws Exception {
		this.address = address;
		this.port = port;
		System.out.println("Start client slave");
	}
	
	public void start() {
		try {
			while (true) {
				connect();
				runrun();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// Something went wrong. We should not leave the method run()
			System.err.println("SEVERE: Client has stopped working");
		}
	}
	
	private void connect() {
		// Try to connect until it worked
		while (true) {
			try {
				socket = new Socket(
						address, port);
				System.out.println("Connected to "+socket.getRemoteSocketAddress());
				return; // return if successful
			} catch (Exception e) {
				System.err.println("Failed to connect to "+address+":"+port);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void runrun() {
		System.out.println("Waiting for commands");
		Scanner in = null; 
		try {
			socket.setSoTimeout(0);
			in = new Scanner(socket.getInputStream());
			while (true) {
				String command = in.nextLine();
				System.out.println("\nReceived command: "+command);
				
				if (command.startsWith(CMD_PING)) {
					send(CMD_PING); // respond with ping

				} else if (command.startsWith(CMD_STRESS)) {
					stress(new Command(command));
					
				} else if (command.startsWith(CMD_BENCH)) {
					bench(new Command(command));
					
				} else if (command.startsWith(CMD_OBSERVE_START)) {
					observe_start();
					
				} else if (command.startsWith(CMD_OBSERVE_FAIL)) {
					observe_fail();
					
				} else if (command.startsWith(CMD_OBSERVE_BENCH)) {
					observe(new Command(command));
				
				}  else if (command.startsWith(CMD_APACHE_BENCH)) {
					ab(new Command(command));
					
				} else if (command.startsWith(CMD_EXIT)) {
					System.exit(0);
					
				} else {
					System.out.println("Unknown command: "+command);
				}
			}
		} catch (NoSuchElementException e) {
			// When master is shutdown, we arrive here
			System.out.println("Exception when reading from master: \""+e.getMessage()+"\"");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (in != null) in.close();
		}
	}
	
	public void send(String response) {
		try {
			socket.getOutputStream().write(new String(response + "\n").getBytes());
			socket.getOutputStream().flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void stress(Command command) throws Exception {
		if (command.has("start"))
			StressClient.main(null);
		if (command.has("stop"))
			StressClient.stop();
	}

	private void bench(Command command) throws Exception {
		if (this.vdm == null) {
			this.vdm = new VirtualDeviceManager();
			this.vdm.setVerbose(verbose);
		}
		
		int clients = CoapBench.DEFAULT_CLIENTS;
		int time = CoapBench.DEFAULT_TIME;
		if (command.has("-c"))
			clients = command.getInt("-c");
		if (command.has("-t"))
			time = command.getInt("-t");
		if (command.has("-latency"))
			vdm.setEnableLatency(true);
		
		List<String> parameters = command.getParameters();
		if (parameters.size() > 0) {
			URI uri = new URI(parameters.get(0));
			vdm.setURI(uri);
			vdm.start(clients, time * 1000);

		} else if (command.has("-new-log")) {
			vdm.lognew(command.getString("-new-log"));
		
		} else if (command.has("-log")) {
			vdm.log(command.getString("-log"));
		
		} else {
			System.err.println("Error: No target specified");
		}
	}
	
	private void observe(Command command) throws Exception {
		InetSocketAddress targetAddr = null;
		if (this.vdm == null) {
			this.vdm = new VirtualDeviceManager();
			this.vdm.setVerbose(true);
		}
		
		int servers = CoapBench.DEFAULT_CLIENTS;
		int time = CoapBench.DEFAULT_TIME;
		if (command.has("-s"))
			servers = command.getInt("-s");
		if (command.has("-t"))
			time = command.getInt("-t");
		if (command.has("-non"))
			vdm.setConfirmable(false);
		if (command.has("-latency"))
			vdm.setEnableLatency(true);
		
		List<String> parameters = command.getParameters();
		if (parameters.size() > 0) {
			// if there's another test currently running, we stop it
			if (vdm.isRunning())
				vdm.stop();
			
			// setup the benchmark target
			URI uri = new URI(parameters.get(0));
			vdm.setURI(uri);
			targetAddr = findSuitableAddress(uri.getHost());
			if (targetAddr == null) {
				System.out.println("Could not find a suitable interface to establish the connection; falling back to localhost.");
				vdm.setBindAddress(null);
			}
			else if (!targetAddr.equals(vdm.getBindAddress())) {
				vdm.setBindAddress(targetAddr);
			}
			
			// begin operation (spawning devices, registration)
			vdm.start(servers, time * 1000, false);
			
			// wait for all the servers to finish registering with the observer
			int i = 0;
			for (; i < 80; ++i) {
				Thread.sleep(250);
				if (vdm.getNumberOfDevicesAtBarrier() == servers)
					break;
			}
			
			if (i < 80) {
				send(CMD_OBSERVE_READY); 
			}
			else {
				System.err.println("\nNot all virtual servers (" + servers + ") were able to initialize (number of servers ready: " + vdm.getNumberOfDevicesAtBarrier() + ")!");
				send(CMD_OBSERVE_FAIL);
				vdm.stop();
			}

		} else if (command.has("-new-log")) {
			vdm.lognew(command.getString("-new-log"));
		
		} else if (command.has("-log")) {
			vdm.log(command.getString("-log"));
		
		} else {
			System.err.println("Error: No target specified");
		}
	}
	
	private void observe_start() throws Exception {
		if (vdm == null || vdm.getDeviceCount() == 0) {
			System.err.println("Observe benchmark error: Test hasn't been defined yet.");
			return;
		}
		if (vdm.getNumberOfDevicesAtBarrier() == vdm.getDeviceCount())
			vdm.joinBarrier();
		else throw new Exception("Premature test trigger (ready are " + vdm.getNumberOfDevicesAtBarrier() + "/" + vdm.getDeviceCount() + " initialized devices); check the master's status.");
	}
	
	private void observe_fail() throws Exception {
		if (vdm != null)
			vdm.stop();
	}
	
	private void ab(Command command) throws Exception {
		if (this.ab == null)
			this.ab = new ApacheBench();
		ab.start(command);
	}
	
	/* This method is used to find an interface, to the address of which we can bind virtual devices' sockets */
	private InetSocketAddress findSuitableAddress(String hostURI) {
		InetSocketAddress retAddr = null;
		Enumeration<NetworkInterface> nets;
		InetSocketAddress hostAddr = null;
		byte[] inaddr, hostinaddr, mask;
		
		hostAddr = new InetSocketAddress(hostURI, 0);
		hostinaddr = hostAddr.getAddress().getAddress();

		try {
			nets = NetworkInterface.getNetworkInterfaces();
			for (NetworkInterface netint : Collections.list(nets)) {
        		for (InterfaceAddress ia : netint.getInterfaceAddresses()) {
        			if (ia.getAddress().getAddress().length != hostAddr.getAddress().getAddress().length || ia.getAddress().isLoopbackAddress())
        				continue;
        			
        			inaddr = ia.getAddress().getAddress();
        			mask = new byte[inaddr.length];
        			
        			int maskbyte = 0;
        			for (; (maskbyte + 1) * 8 < ia.getNetworkPrefixLength(); ++maskbyte) {
        				mask[maskbyte] = (byte)0xFF;
        			}
        			mask[maskbyte] = (byte)((int)(Math.pow(2.0, (ia.getNetworkPrefixLength() - maskbyte * 8)) - 1) << (8 - ia.getNetworkPrefixLength() + maskbyte * 8));
        			
        			int maskcounter = 0;
        			for (; maskcounter * 8 < ia.getNetworkPrefixLength(); ++maskcounter) {
        				if ((inaddr[maskcounter] & mask[maskcounter]) != (hostinaddr[maskcounter] & mask[maskcounter]))
        					break;
        			}
        			if (maskcounter * 8 < ia.getNetworkPrefixLength())
        				continue;
        			
        			retAddr = new InetSocketAddress(ia.getAddress(), 0);
        			return retAddr;
        		}
        	}
		} catch (SocketException se) { se.printStackTrace(); }
		return retAddr;
	}
	
	public static void main(String[] args) throws Exception {
		InetAddress address = InetAddress.getByName(CoapBench.DEFAULT_MASTER_ADDRESS);
		new ClientSlave(address, CoapBench.DEFAULT_MASTER_PORT).start();
	}

	public boolean isVerbose() {
		return verbose;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
}
