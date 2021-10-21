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
import java.util.Arrays;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.config.UdpConfig;

/**
 * The VirtualClient manager creates the virtual clients for the benchmarks.
 * Each virtual client sends request to the server as fast as the server can
 * handle them.
 */
public class VirtualDeviceManager {

	public static final String LOG_FILE = "coapbench";
	
	static {
		CoapConfig.register();
		UdpConfig.register();
	}

	private Timer timer;

	private URI uri;
	private InetSocketAddress bindAddr;

	private long timestamp;
	private int testlength;
	private int count;
	private boolean confirmable = true;
	
	private ArrayList<VirtualDevice> devices;
	
	private CyclicBarrier barrier;
	
	private LogFile log;
	
	private boolean enableLatency = false;
	private boolean verbose;

	public VirtualDeviceManager() throws Exception {
		this(null);
	}
	
	public VirtualDeviceManager(URI uri) throws Exception {
		this(uri, null);
	}
	
	public VirtualDeviceManager(URI uri, InetSocketAddress bindAddr) throws Exception {
		this.uri = uri;
		this.bindAddr = bindAddr;
		this.devices = new ArrayList<VirtualDevice>();
		this.timer = new Timer();
		this.timestamp = 0L;
		this.ensurelog();
	}
	
	public void runConcurrencySeries(int[] cs, int time) throws Exception {
		int n = cs.length;
		log("Run series: "+Arrays.toString(cs).replace("[","").replace("]", ""));
		
		for (int i=0;i<n;i++) {
			start(cs[i], time);
			
			if (i < n-1) // sleep between two runs
				Thread.sleep(time + 5*1000);
			else Thread.sleep(time + 1000);
		}
	}
	
	public void log(String entry) throws Exception {
		ensurelog();
		log.println(entry);
	}
	
	public void lognew(String name) throws Exception {
		this.log = new LogFile(LOG_FILE + "_" + name);
		this.log.setVerbose(verbose);
	}
	
	private void ensurelog() throws Exception {
		if (log==null) {
			log = new LogFile(LOG_FILE);
		}
	}
	
	/* VirtualClients are created here. */
	public void setDeviceCount(int d, boolean clients) throws Exception {
		VirtualDevice vd;
		
		for (int i = devices.size() - 1; i >= 0; --i) {
			if (devices.get(i).isRunning()) {
				System.err.println ("[VDM] An inactive virtual device #" + i + " is still running; attempting a stop.");
				devices.get(i).stop();
			}
			devices.remove(i).close(); // close and remove
		}

		for (int i=devices.size(); i<d; i++) {
			if (clients)
				vd = new VirtualClient(uri, bindAddr);
			else
				vd = new VirtualServer(uri, bindAddr, true, confirmable, barrier);
			vd.setCheckLatency(enableLatency);
			devices.add(vd);
		}

		if (!clients)
			for (VirtualDevice vs : devices)
				((VirtualServer) vs).setBarrier(barrier);
		this.count = d;
	}
	
	public int getDeviceCount() {
		return devices.size();
	}
	
	public void setURI(URI uri) throws UnknownHostException {
		this.uri = uri;
		System.err.println("VirtualDeviceManager URI set to " + uri + ".");
		for (VirtualDevice vd:devices)
			vd.setURI(uri);
	}
	
	public void start(int count, int time) throws Exception {
		start(count, time, true);
	}
	
	public void start(int count, int time, boolean clients) throws Exception {
		ensurelog();
		timestamp = 0;
		barrier = new CyclicBarrier(count + 1);
		setDeviceCount(count, clients);
		Thread[] threads = new Thread[count];
		for (int i=0;i<count;i++) {
			VirtualDevice d = devices.get(i);
			d.reset();
			if (clients)
				threads[i] = new Thread((VirtualClient)d);
			else {
				threads[i] = new Thread((VirtualServer)d);
			}
		}
		System.err.println("\nSetup "+count+" virtual " + (clients ? "client" + (count == 1 ? "" : "s") : "server" + (count == 1 ? "" : "s")) + " for "+time+" ms");
		for (int i=0;i<count;i++)
			threads[i].start();
		testlength = time;
		
		/* If we're executing a client benchmark, then we have to setup the test timer here */
		if (clients) {  
			timestamp = System.nanoTime();
			timer.schedule(new TimerTask() {
				public void run() {
					stop();
				} }, time);
		}
	}
	
	public void stop() {
		float dt = (System.nanoTime() - timestamp) / 1000000f;
		if (timestamp == 0)
			return;
		timestamp = 0;
		
		for (VirtualDevice vd:devices)
			vd.stop();
		
		barrier.reset();
		CoapClient client;
		CoapResponse response = null;
		if (verbose) {
			System.out.println("Stopping virtual devices and collecting results.");
			try {
				ensurelog();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		try {
			Thread.sleep(1500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if (!confirmable) {
			client = new CoapClient(uri);
			try {
				response = client.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		int sum = 0;
		int sumTimeout = 0;
		ArrayList<Integer> latencies = new ArrayList<Integer>();
		
		if (verbose)
			System.out.println();
		
		if (verbose)
			System.out.println();
		
		for (int i=0;i<devices.size();i++) {
			VirtualDevice device = devices.get(i);
			int lost = device.getTimeouts();
			latencies.addAll(device.getLatencies());
			int count = device.getCount();
			sum += count;
			
			if (!confirmable) {
				count = Integer.parseInt(response.getResponseText());
			}
			
			sumTimeout += lost;
			
			if (verbose)
				System.out.format("Virtual client %2d received %7d, timeouts: %3d, throughput: %d /s\n"
					, i, count, lost, (int) (count * 1000L / dt));
		}
		
		if (!confirmable) {
			sum = count;
		}
		
		float throughput = (sum * 1000L) / dt;
		
		long latsum = 0;
		for (int l:latencies) latsum += l;
		double mean = (double) latsum / latencies.size();
		double temp = 0;
        for(int l : latencies) temp += (mean-l)*(mean-l);
        double var = Math.sqrt(temp / latencies.size());
           
        if (latencies.size() > 0) {
        	Collections.sort(latencies); // bad if length==0
			int q50 = latencies.get((int) (latencies.size()/2));
			int q66 = latencies.get((int) (latencies.size() * 2L/3));
			int q75 = latencies.get((int) (latencies.size() * 3L/4));
			int q80 = latencies.get((int) (latencies.size() * 4L/5));
			int q90 = latencies.get((int) (latencies.size() * 9L/10));
			int q95 = latencies.get((int) (latencies.size() * 19L/20));
			int q98 = latencies.get((int) (latencies.size() * 98L/100));
			int q99 = latencies.get((int) (latencies.size() * 99L/100));
			int q100 = latencies.get(latencies.size() - 1);
			
			log.format("Timeouts, Concurrency, Time, Completed, Throughput | 50%%, 66%%, 75%%, 80%%, 90%%, 95%%, 98%%, 99%%, 100%%, stdev(ms)\n");
			log.format("%d, %d, %.3f, %d, %.2f | %d, %d, %d, %d, %d, %d, %d, %d, %d, %.1f\n",
					sumTimeout, count, dt/1000f, sum, throughput,
					q50, q66, q75, q80, q90, q95, q98, q99, q100, var);
        
        } else {
        	// no latency
        	log.format("d=%d, t=%.3f, received=%d, timeouts=%d, throughput=%.2f, uri=%s\n", count, dt/1000f, sum, sumTimeout, throughput, uri.toString());
        }
	}
	
	public boolean isRunning() {
		return timestamp != 0;
	}
	
	public void joinBarrier() {
		if (barrier != null)
			try {
				System.err.print("\nVDM: Barrier not null. Currently waiting are " + barrier.getNumberWaiting() + " threads; joining.");
				timer.schedule(new TimerTask() {
					public void run() {
						stop();
					} }, testlength);
				barrier.await();
				timestamp = System.nanoTime();
				System.err.println("\nVirtual servers collected at barrier; starting notification process.");
			} catch (InterruptedException | BrokenBarrierException e) {
				e.printStackTrace();
			}
	}
	
	public int getNumberOfDevicesAtBarrier() {
		return barrier.getNumberWaiting();
	}
	
	public InetSocketAddress getBindAddress() {
		return this.bindAddr;
	}
	
	public void setBindAddress(InetSocketAddress bindAddr) {
		this.bindAddr = bindAddr;
	}

	public boolean isEnableLatency() {
		return enableLatency;
	}

	public void setEnableLatency(boolean enableLatency) {
		System.err.println("Measure latency: "+enableLatency);
		this.enableLatency = enableLatency;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
		if (log != null)
			log.setVerbose(verbose);
	}
	
	public boolean isConfirmable() {
		return confirmable;
	}
	
	public void setConfirmable(boolean confirmable) {
		this.confirmable = confirmable;
		for (VirtualDevice vd : devices)
			if (vd instanceof VirtualServer)
				((VirtualServer)vd).setConfirmable(confirmable);
	}
}

