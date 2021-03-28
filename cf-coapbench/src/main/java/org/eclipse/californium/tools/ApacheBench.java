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

import java.util.Scanner;

import org.eclipse.californium.tools.coapbench.Command;
import org.eclipse.californium.tools.coapbench.LogFile;


/**
 * Wrapper to invoke ApacheBench
 */
public class ApacheBench {

	// Format: Conurrency Level, Time for tests, completed req, req per sec
	// NOTE: The -t option must come before the -n option. The order matters here!

	public static final String PATH_APACHE_BENCH = "";
	public static final String REQ_PER_SEC = "Requests per second";
	public static final String CON_LEV = "Concurrency Level";
	public static final String COM_REQ = "Complete requests";
	public static final String TIME = "Time taken for tests";

	public static final String LOG_FILE = "ab_log";
	
	private LogFile log;
	
	public ApacheBench() throws Exception {
		this.log = new LogFile(LOG_FILE);
		log.println("Conurrency Level, Time for tests, completed req, req per sec | 50%%, 66%%, 75%%, 80%%, 90%%, 95%%, 98%%, 99%%, 100%%, stdev (ms)");
	}
	
	public void start(Command command) {
		start(PATH_APACHE_BENCH + command.getBody());
	}
	
	public void start(String command) {
		System.out.println("Command: "+command);
		try {
			Process p = Runtime.getRuntime().exec(command);
			StringBuilder buffer = new StringBuilder("ab, ");
			Scanner scanner = new Scanner(p.getInputStream());
			try {
				while (scanner.hasNext()) {
					String line = scanner.nextLine().trim();
					System.out.println(":"+line);
					if (line.startsWith(CON_LEV))
						buffer.append(readIntArgument(line, ":")).append(", ");
					if (line.startsWith(TIME))
						buffer.append(readDoubleArgument(line, ":")).append(", ");
					if (line.startsWith(COM_REQ))
						buffer.append(readIntArgument(line, ":")).append(", ");
					if (line.startsWith(REQ_PER_SEC))
						buffer.append(readDoubleArgument(line, ":")).append(", ");
					if (line.startsWith("50%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
					if (line.startsWith("66%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
					if (line.startsWith("75%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
					if (line.startsWith("80%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
					if (line.startsWith("90%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
					if (line.startsWith("95%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
					if (line.startsWith("98%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
					if (line.startsWith("99%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
					if (line.startsWith("100%"))
						buffer.append(readIntArgument(line, "%")).append(", ");
				}
			} finally {
				scanner.close();
			}
			p.destroy();
			log.println(buffer.toString());
		} catch (Exception e) {
			e.printStackTrace();
			log.println("ERROR: "+command);
		}
	}

	private static int readIntArgument(String line, String separator) {
		try (Scanner scanner = new Scanner(line.split(separator)[1])) {
			return scanner.nextInt();
		}
	}

	private static double readDoubleArgument(String line, String separator) {
		try (Scanner scanner = new Scanner(line.split(separator)[1])) {
			return scanner.nextDouble();
		}
	}

	public static void main(String[] args) throws Exception {
		args = new String[] {"-t", "5", "-n", "10000000", "-uri", "http://localhost:8000/benchmark/"
				, "-cs", "2", "10", "20"};
		int t = -1;
		int c = 20;
		int[] cs = null;
		int n = -1;
		String uri = null;
		boolean k = false;
		int index = 0;
		while (index < args.length) {
			String arg = args[index];
			if ("-uri".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -uri");
				}
				uri = args[index+1];
			} else if ("-n".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -n");
				}
				n = Integer.parseInt(args[index+1]);
			} else if ("-t".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -t");
				}
				t = Integer.parseInt(args[index+1]);
			} else if ("-k".equals(arg)) {
				k = true; index--;
			} else if ("-c".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -c");
				}
				c = Integer.parseInt(args[index+1]);
			} else if ("-cs".equals(arg)) {
				if (index + 1 == args.length) {
					throw new IllegalArgumentException("Missing argument for -cs");
				}
				int cn = Integer.parseInt(args[index + 1]);
				if (index + 2 + cn >= args.length) {
					throw new IllegalArgumentException("Missing argument for -cs " + cn);
				}
				cs = new int[cn];
				for (int i = 0; i < cn; i++) {
					cs[i] = Integer.parseInt(args[index + 2 + i]);
				}
				index += cn;
			}
			index += 2;
		}
		
		ApacheBench ab = new ApacheBench();
		if (cs != null) {
			for (int i=0;i<cs.length;i++) {
				ab.start(PATH_APACHE_BENCH
						+ "ab " + (t!=-1 ? "-t "+t+" " : "") + (n!=-1 ? "-n "+n+" " : "") 
						+ (k ? "-k " : "") + "-c "+ cs[i] + " " + uri);
				Thread.sleep(5000);
			}
			
		} else {
			ab.start(PATH_APACHE_BENCH
				+ "ab " + (t!=-1 ? "-t "+t+" " : "") + (n!=-1 ? "-n "+n+" " : "") 
				+ (k ? "-k " : "") + "-c "+ c + " " + uri);
		}
	}
}
