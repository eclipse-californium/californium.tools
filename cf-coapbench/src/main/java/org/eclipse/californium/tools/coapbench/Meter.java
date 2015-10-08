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


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A meter is used to measure throughput (requests per second). Call
 * {@link #requested()} after sending a request and {@link #responded()} after
 * receiving a response.
 */
public class Meter {

	private int max; // The maximum amount of requests the clients send.
	private int occupation; // The amount of request that the clients send before pausing
	private int clientCount; // The amount of clients there are
	
	// for requests (pause when occupied)
	private AtomicInteger requestCounter = new AtomicInteger();
	
	// for responses
	private AtomicInteger counter = new AtomicInteger();
	private long last_timestamp;
	private float avg[] = new float[10];
	private int avgp = 0;
	
	private BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
	
	public Meter(int occupation) {
		this(occupation, 1);
	}
	
	public Meter(int occupation, int clientCount) {
		this.clientCount = clientCount;
		this.occupation = occupation;
		this.last_timestamp = System.nanoTime();
		this.max = occupation;
	}
	
	public void responded() {
		int d = counter.incrementAndGet();
		if (d % occupation == 0) {
			resume();
			long now = System.nanoTime();
			float dt = (now - last_timestamp) / 1000000f;
			float through = occupation * 1000L / dt;
			System.out.format("received %8d. In %4d  ms, %6d per sec, 10avg: %6d\n",
					d, (int) dt, (int) through, (int) nextAvg(through));
			last_timestamp = now;
		}
	}
	
	public void requested() {
		int c = requestCounter.incrementAndGet();
		if (c >= max) {
			pause();
		}
	}
	
	public void pause() {
		try {
			queue.take();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void resume() {
		// do not overfill it
		max += occupation;
		if (queue.size() <= 2 * clientCount) {
			for (int i=0;i<clientCount;i++) {
				queue.add("release");
			}
		}
	}

	private float nextAvg(float val) {
		avg[avgp++ % avg.length] = val;
		float sum = 0;
		for (float f : avg)
			sum += f;
		return sum / avg.length;
	}
	
}
