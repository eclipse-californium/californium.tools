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
package org.eclipse.californium.tools;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;

import org.eclipse.californium.tools.coapbench.VirtualDeviceManager;


public class CoapBench {
	
	// TODO: add parameters for methods (GET, POST, ...), payload, checks, and logfile
	
	// Modes: normal, master, slave
	public static final String MASTER = "-master";
	public static final String SLAVE = "-slave";

	// Defaults
	public static final int DEFAULT_CLIENTS = 1;
	public static final int DEFAULT_SERVERS = 1;
	public static final int DEFAULT_TIME = 30; // [s]

	public static final String DEFAULT_MASTER_ADDRESS = "localhost";
	public static final int DEFAULT_MASTER_PORT = 58888; 
	
	public static void main(String[] args) {
		try {
			if (args.length > 0) {
				if ("-usage".equals(args[0]) || "-help".equals(args[0]) || "-?".equals(args[0])) {
					printUsage();
				} else if (args[0].equals(MASTER)) {
					mainMaster(args);
				} else if (args[0].equals(SLAVE)) {
					mainSlave(args);
				} else {
					mainBench(args);
				}
			} else {
				printUsage();
			}
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(-1);
		}
	}
	
	public static void mainBench(String[] args) throws Exception {
		String target = null;
		String bindAddr = null;
		String clients = ""+DEFAULT_CLIENTS;
		int time = DEFAULT_TIME;
		int index = 0;
		boolean withLatency = false;
		while (index < args.length) {
			String arg = args[index];
			
			if ("-c".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -c");
				}
				clients = args[index+1];
			} else if ("-t".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -t");
				}
				time = Integer.parseInt(args[index+1]);
			} else if ("-b".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -b");
				}
				bindAddr = args[index+1];
			} else if ("-latency".equals(arg)) {
				withLatency = true; index++; continue;
			} else if ("-h".equals(arg)) {
				printUsage();
				return;
			} else if (index == args.length - 1) {
				// The last argument is the target address
				target = arg;
			} else {
				System.err.println("Unknwon arg "+arg);
				printUsage();
				return;
			}
			index += 2;
		}
		if (target == null) {
			System.err.println("Error: No target specified");
			printUsage();
			return;
		}
		
		URI uri = new URI(target);
		
		InetSocketAddress bindSAddr = null;
		if (bindAddr != null) {
			InetAddress ba = InetAddress.getByName(bindAddr);
			bindSAddr = new InetSocketAddress(ba, 0);
			System.err.println("Bind clients to local address: "+bindSAddr);
			System.err.println("Note that on some systems (e.g. Windows) it now is not possible to send requests to localhost.");
		}
		
		int[] series = convertSeries(clients);
		VirtualDeviceManager manager = new VirtualDeviceManager(uri, bindSAddr);
		if (withLatency) manager.setEnableLatency(true);
		manager.runConcurrencySeries(series, time*1000);
		
//		Thread.sleep(time*1000 + 1000);
		System.exit(0); // stop all threads from virtual client manager
	}
	
	public static void mainMaster(String[] args) throws Exception {
		int port = DEFAULT_MASTER_PORT;
		int index = 1;
		while (index < args.length) {
			String arg = args[index];
			if ("-p".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -p");
				}
				port = Integer.parseInt(args[index+1]);
			} else {
				System.err.println("Unknwon arg "+arg);
				printUsage();
				return;
			}
			index += 2;
		}
		new ClientMaster(port).start();
	}
	
	public static void mainSlave(String[] args) throws Exception {
		String address = DEFAULT_MASTER_ADDRESS;
		int port = DEFAULT_MASTER_PORT;
		int index = 1;
		boolean verbose = false;
		//String requestType;
		while (index < args.length) {
			String arg = args[index];
			if ("-a".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -a");
				}
				address = args[index+1];
			} else if ("-p".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -p");
				}
				port = Integer.parseInt(args[index+1]);
			} else if ("-v".equals(arg)) {
				verbose = true;
			//} else if ("-m".equals(arg)) {
			//	requestType = args[index+1];
			}
			index += 2;
		}

		ClientSlave slave = new ClientSlave(InetAddress.getByName(address), port);
		slave.setVerbose(verbose);
		slave.start();
	}
	
//	private static int[] convertSeries(String clientSeries) {
//		// clientSeries is in format <from>:<step>:<to>
//		int from = 0;
//		int to = 0;
//		int step = 1;
//		if (clientSeries == null)
//			return new int[] { DEFAULT_CLIENTS };
//		
//		else if (clientSeries.matches("\\d+"))
//			return new int[] { Integer.parseInt(clientSeries) };
//		
//		else if (clientSeries.matches("\\d+:\\d+")) {
//			from = Integer.parseInt(clientSeries.split(":")[0]);
//			to =   Integer.parseInt(clientSeries.split(":")[1]);
//		
//		} else if (clientSeries.matches("\\d+:\\d+:\\d+")) {
//			from = Integer.parseInt(clientSeries.split(":")[0]);
//			step = Integer.parseInt(clientSeries.split(":")[1]);
//			to =   Integer.parseInt(clientSeries.split(":")[2]);
//		}
//		int length = (to-from)/step + 1;
//		int[] series = new int[length];
//		for (int i=0;i<length;i++)
//			series[i] = from + i*step;
//		return series;
//	}
	
	private static int[] convertSeries(String clientSeries) {
		// clientSeries is in format first,second,third...
		String[] parts = clientSeries.split(",");
		int[] series = new int[parts.length];
		for (int i=0;i<parts.length;i++)
			series[i] = Integer.parseInt(parts[i]);
		return series;
	}
	
	public static void printUsage() {
		System.out.println(
				"SYNOPSIS"
				+ "\n    CoAPBench [[OPTIONS] URI | -master OPTIONS | -slave OPTIONS] [-v]" 
				+ "\n"
				+ "\nURI: The target URI to benchmark"
				+ "\n"
				+ "\nOPTIONS are:"
				+ "\n    -c CONCURRENCY"
				+ "\n            Concurrency level, i.e., the number of parallel clients (default is "+ DEFAULT_CLIENTS + ")."
				+ "\n            This value can be of the form <from>:<step>:<to>, e.g., 10:2:16 for a subsequent run of 10, 12, 14, 16 clients."
				+ "\n    -t TIME"
				+ "\n            Limit the duration of the benchmark to TIME seconds (default is " + DEFAULT_TIME + ")."
				+ "\n    -b ADDRESS"
				+ "\n            Bind the clients to the specified local address (by default the system chooses)."
				+ "\n"
				+ "\nOPTIONS for the master are:"
				+ "\n    -p PORT"
				+ "\n            The port on which the master waits for slaves."
				+ "\n"
				+ "\nOPTIONS for the slave are:"
				+ "\n    -a ADDRESS"
				+ "\n            The address of the master."
				+ "\n    -p PORT"
				+ "\n            The port of the master."
				+ "\n	 -s"
				+ "\n			 Specifies whether the resource should be observed (applies if the request type is set to GET)."
				+ "\n"
				+ "\nExamples:"
				+ "\nStart 50 clients that concurrently send GET requests for 60 seconds"
				+ "\n    java -jar coapbench.jar -c 50 -t 60 coap://localhost:5683/benchmark"
				+ "\n"
				+ "\nStart a master listening on port 8888 for slaves"
				+ "\n    java -jar coapbench.jar -master -p 8888"
				+ "\n"
				+ "\nStart a slave which connects with the specified master"
				+ "\n    java -jar coapbench.jar -slave -a 192.168.1.33 -p 8888"
			);
		// TODO: add parameters for methods (GET, POST, ...), payload, checks, and logfile
		// TODO: stepwise increase
	}
	
}
