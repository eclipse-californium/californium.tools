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
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.eclipse.californium.tools.coapbench.Command;
import org.eclipse.californium.tools.coapbench.VirtualClientManager;


/**
 * The client slave keeps a TCP connection to the master. The master sends
 * commands to the slave.
 */
public class ClientSlave {

	public static final String CMD_PING = "ping";
	public static final String CMD_EXIT = "exit";
	public static final String CMD_STRESS = "stress";
	public static final String CMD_BENCH = "bench";
	public static final String CMD_APACHE_BENCH = "ab";
	
	private InetAddress address;
	private int port;
	private Socket socket;
	private boolean verbose;
	
	private VirtualClientManager vcm;
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
			in = new Scanner(socket.getInputStream());
			while (true) {
				String command = in.nextLine();
				System.out.println("Received command: "+command);
				
				if (command.startsWith(CMD_PING)) {
					send(CMD_PING); // respond with ping

				} else if (command.startsWith(CMD_STRESS)) {
					stress(new Command(command));
					
				} else if (command.startsWith(CMD_BENCH)) {
					bench(new Command(command));
					
				} else if (command.startsWith(CMD_APACHE_BENCH)) {
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
//			System.out.println("Send "+response);
			socket.getOutputStream().write(response.getBytes());
			socket.getOutputStream().write("\n".getBytes());
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
		if (this.vcm == null) {
			this.vcm = new VirtualClientManager();
			this.vcm.setVerbose(verbose);
		}
		
		int clients = CoapBench.DEFAULT_CLIENTS;
		int time = CoapBench.DEFAULT_TIME;
		if (command.has("-c"))
			clients = command.getInt("-c");
		if (command.has("-t"))
			time = command.getInt("-t");
		if (command.has("-latency"))
			vcm.setEnableLatency(true);
		
		List<String> parameters = command.getParameters();
		if (parameters.size() > 0) {
			URI uri = new URI(parameters.get(0));
			vcm.setURI(uri);
			vcm.start(clients, time * 1000);

		} else if (command.has("-new-log")) {
			vcm.lognew(command.getString("-new-log"));
		
		} else if (command.has("-log")) {
			vcm.log(command.getString("-log"));
		
		} else {
			System.err.println("Error: No target specified");
		}
	}
	
	private void ab(Command command) throws Exception {
		if (this.ab == null)
			this.ab = new ApacheBench();
		ab.start(command);
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
