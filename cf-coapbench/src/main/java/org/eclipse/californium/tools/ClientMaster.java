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
import java.awt.Toolkit;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.tools.coapbench.Command;


/**
 * The master keeps a TCP connection to all client slaves. The master sends
 * commands to all slaves. Use @1 to send a command only to client with id 1.
 */
public class ClientMaster implements Runnable {

	public static final String CMD_EXIT = "exit";
	public static final String CMD_STATUS = "status";
	public static final String CMD_PING = "ping";
	public static final String CMD_STRESS = "stress";
	public static final String CMD_BENCH = "bench";
	public static final String CMD_WAIT = "wait";
	public static final String CMD_BEEP = "beep";
	public static final String CMD_APACHE_BENCH = "ab";
	public static final String CMD_HELP = "help";
	public static final String CMD_POST = "post";
	
	private ServerSocket masterSocket;
	
	private List<Slave> slaves;
	
	private String last = "";
	
	public ClientMaster(int port) throws Exception {
		this.masterSocket = new ServerSocket(port);
		this.slaves = new LinkedList<Slave>();
	}
	
	public void start() {
		System.out.println("Start client master");
		System.out.println("Type command, e.g., \"help\":");
		new Thread(this).start();
		Scanner in = new Scanner(System.in);
		try {
			while (true) {
				try {
					String line = in.nextLine();
					if (line.equals("-"))
						line = last;
					else last = line;
					String[] commands = line.split(";");
					for (String cmd:commands) {
						Command command = new Command(cmd.trim());
						String body = command.getBody();
						if (body.isEmpty()) {
							continue;
						} else if (body.startsWith(CMD_EXIT)) {
							exit(command);
						} else if (body.startsWith(CMD_STATUS)) {
							status();
						} else if (body.startsWith(CMD_PING)) {
							ping(command);
						} else if (body.startsWith(CMD_STRESS)) {
							command(command);
						} else if (body.startsWith(CMD_BENCH)) {
							command(command);
						} else if (body.startsWith(CMD_APACHE_BENCH)) {
							command(command);
						} else if (body.startsWith(CMD_WAIT)) {
							wait(command);
						} else if (body.startsWith(CMD_BEEP)) {
							Toolkit.getDefaultToolkit().beep();
						} else if (body.startsWith(CMD_POST)) {
							post(command);
						} else if (body.startsWith(CMD_HELP)) {
							printHelp();
							
						} else {
							System.out.println("Unknown command: "+command);
						}
					}
					System.out.println();
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.out.print("Type command: ");
			}
		} finally { in.close(); }
	}
	
	public void status() {
		System.out.println("Connected to "+slaves.size()+" slaves");
		for (Slave s:slaves)
			System.out.println(s);
	}
	
	public void ping(Command command) {
		System.out.println("Ping to slaves");
		for (Slave s:getSlaves(command.getAt())) {
			System.out.println(" - "+s+": "+s.ping()+" ms");
		}
	}
	
	private void command(Command command) {
		for (Slave s:getSlaves(command.getAt())) {
			System.out.println("Send \""+command.getBody()+"\" to "+s);
			s.send(command.getBody());
		}
	}
	
	private void post(Command command) throws InterruptedException {
		List<String> parameters = command.getParameters();
		if (parameters.size() > 0) {
			String uri = parameters.get(0);
			new CoapClient(uri).post("", MediaTypeRegistry.TEXT_PLAIN);
		} else {
			System.out.println("You have to specify a target");
		}
	}
	
	public void exit(Command command) throws Exception {
		System.out.println(command);
		if (command.has("-all")) {
			for (Slave s:getSlaves(command.getAt())) {
				System.out.println("exit "+s);
				s.send(CMD_EXIT);
			}
			Thread.sleep(100);
		} else {
			System.out.println("Only master exits");
		}
		System.out.println("exit");
		System.exit(0);
	}
	
	public void wait(Command command) throws InterruptedException {
		if (command.has("-t")) {
			int time = command.getInt("-t");
			System.out.println("Wait "+(time / 1000f) +" s" );
			Thread.sleep(time);
		} else {
			System.out.println("no time option \"-t X\" found");
		}
	}
	
	public void run() {
		System.out.println("Start masterSocket "+masterSocket.getLocalSocketAddress());
		while (true) {
			try {
				Socket connection = masterSocket.accept();
				System.out.println("Connected to new slave "+connection);
				Slave slave = new Slave(connection);
				slaves.add(slave);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private ArrayList<Slave> getSlaves(int at) {
		if (at == Command.ALL)
			return new ArrayList<Slave>(slaves);
		else {
			ArrayList<Slave> s = new ArrayList<Slave>();
			s.add(slaves.get(at-1));
			return s;
		}
	}
	
	public void remove(Slave slave) {
		System.out.println("Remove slave "+slave);
		slaves.remove(slave);
	}
	
	private class Slave {
		
		private Socket socket;
		private Scanner in;
		
		public Slave(Socket socket) throws Exception {
			this.socket = socket;
			this.in = new Scanner(socket.getInputStream());
		}
		
		public boolean send(String command) {
			try {
				socket.getOutputStream().write(command.getBytes());
				socket.getOutputStream().write("\n".getBytes());
				socket.getOutputStream().flush();
				return true;
				
			} catch (SocketException e) {
				// When slave is shutdown, we arrive here
				System.out.println("Exception while sending to "+this+": \""+e.getMessage()+"\"");
				remove(this);
			} catch (IOException e) {
				e.printStackTrace();
				remove(this);
			}
			return false;
		}
		
		public int ping() {
			try {
				long t0 = System.nanoTime();
				boolean succ = send(CMD_PING);
				if (!succ) return -1;
				in.nextLine(); // wait for response
				long dt = System.nanoTime() - t0;
				return (int) (dt / 1000000);
			} catch (Exception e) {
				e.printStackTrace();
				return -1;
			}
		}
		
		@Override
		public String toString() {
			return socket.getRemoteSocketAddress().toString();
		}
	}
	
	public void printHelp() {
		System.out.println(
			"Send a signal to all clients each starting 50 clients for 60 seconds with the command"
			+ "\n    bench -c 50 -t 60 coap://localhost:5683/fibonacci?n=20"
			+ "\n"
			+ "\nCreate a new log file my_name (no spaces allowed)"
			+ "\n    bench -new-log my_name"
			+ "\n"
			+ "\nInsert a log entry into log file (no spaces allowed)"
			+ "\n    bench -log Test_No_77"
			+ "\n"
			+ "\nOther commands: "
			+ "\n    status       Print the current status"
			+ "\n    ping         Exchange a message with each slave"
			+ "\n    wait -t time Wait for the spe"
			+ "\n    beep         Give a beep sound"
			+ "\n    exit [-all]  Exit the master and all slaves"
			+ "\n"
			+ "\nUse an @ to send a command only to a specific slvaes, e.g., \"@2 ping\" "
			+ "\nto send a ping to slave 2."
		);
	}
	
	public static void main(String[] args) throws Exception {
		new ClientMaster(CoapBench.DEFAULT_MASTER_PORT).start();
	}
}
