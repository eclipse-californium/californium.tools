/*******************************************************************************
 * Copyright (c) 2022 Bosch.IO GmbH and others.
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
 *    Bosch.IO GmbH - initial creation
 ******************************************************************************/
package org.eclipse.californium.tools.jmeter.plugin;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.UDPConnector;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.util.DaemonThreadFactory;
import org.eclipse.californium.elements.util.ExecutorsUtil;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedSinglePskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoint manager for the {@link JavaSamplerClient}.
 * 
 * @since 3.5
 */
public class SamplerEndpointsManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(SamplerEndpointsManager.class);

	public static final SamplerEndpointsManager INSTANCE = new SamplerEndpointsManager();

	private final ScheduledExecutorService EXECUTOR = ExecutorsUtil
			.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), new DaemonThreadFactory("COAP#"));
	private final ScheduledExecutorService TIMER = new ScheduledThreadPoolExecutor(2,
			new DaemonThreadFactory("TIMER#"));

	private final ConcurrentMap<String, SamplerEndpoint> ENDPOINTS = new ConcurrentHashMap<String, SamplerEndpoint>();

	private SamplerEndpointsManager() {
		TIMER.scheduleWithFixedDelay(new Runnable() {
			int lastSize = 0;
			int loops = 0;

			public void run() {
				cleanupSamplerEndpoints();
				++loops;
				if ((loops % 30) == 0) {
					int size = ENDPOINTS.size();
					if (lastSize != size) {
						LOGGER.debug("{} endpoints active.", ENDPOINTS.size());
						lastSize = size;
					}
				}
			}
		}, 2000, 2000, TimeUnit.MILLISECONDS);
	}

	private void cleanupSamplerEndpoints() {
		for (SamplerEndpoint sampler : ENDPOINTS.values()) {
			if (sampler.expired()) {
				ENDPOINTS.remove(sampler.key, sampler);
				LOGGER.debug("Endpoint for {} expired.", sampler.key);
				sampler.close();
			}
		}
	}

	public SamplerEndpoint getSampleEndpoint(String scheme, String identity, byte[] secret, long idleTimeMillis,
			Configuration configuration) {
		String key = scheme + "://" + identity + "@";
		SamplerEndpoint samplerEndpoint = ENDPOINTS.get(key);
		if (samplerEndpoint == null || samplerEndpoint.expired()) {
			Connector connector;
			if (CoAP.COAP_SECURE_URI_SCHEME.equals(scheme)) {
				AdvancedPskStore psk = new AdvancedSinglePskStore(identity, secret);
				DtlsConnectorConfig.Builder dtlsBuilder = DtlsConnectorConfig.builder(configuration);
				dtlsBuilder.setAdvancedPskStore(psk);
				DTLSConnector dtlsConnector = new DTLSConnector(dtlsBuilder.build());
				dtlsConnector.setExecutor(EXECUTOR);
				connector = dtlsConnector;
			} else {
				connector = new UDPConnector(null, configuration);
			}

			CoapEndpoint.Builder coapBuilder = new CoapEndpoint.Builder();
			coapBuilder.setConfiguration(configuration);
			coapBuilder.setConnector(connector);
			CoapEndpoint endpoint = coapBuilder.build();
			endpoint.setExecutors(EXECUTOR, TIMER);
			samplerEndpoint = new SamplerEndpoint(key, endpoint, idleTimeMillis);
			SamplerEndpoint previous = ENDPOINTS.putIfAbsent(key, samplerEndpoint);
			if (previous == null) {
				try {
					endpoint.start();
				} catch (IOException e) {
					ENDPOINTS.remove(key, samplerEndpoint);
					samplerEndpoint = null;
					LOGGER.warn("Setup failed for {}!", key, e);
				}
				LOGGER.debug("Endpoint for {} created.", key);
			} else {
				samplerEndpoint.close();
				samplerEndpoint = previous;
			}
		} else {
			samplerEndpoint.setIdleTime(idleTimeMillis);
		}
		return samplerEndpoint;
	}

	public static class SamplerEndpoint {
		private final String key;
		private final CoapEndpoint endpoint;
		private volatile long idleTimeNanos;
		private volatile long lastUsageNanos;
		private volatile boolean used;
		private volatile boolean expired;

		public SamplerEndpoint(String key, CoapEndpoint endpoint, long idleTimeMillis) {
			this.key = key;
			this.endpoint = endpoint;
			this.idleTimeNanos = TimeUnit.MILLISECONDS.toNanos(idleTimeMillis);
			update(false);
		}

		public CoapEndpoint getEndpoint() {
			return endpoint;
		}

		public void setIdleTime(long idleTimeMillis) {
			this.idleTimeNanos = TimeUnit.MILLISECONDS.toNanos(idleTimeMillis);
		}

		public synchronized void expire() {
			expired = true;
		}

		public synchronized boolean update(boolean used) {
			if (expired) {
				return false;
			}
			this.used = used;
			this.lastUsageNanos = System.nanoTime();
			return true;
		}

		private synchronized boolean expired() {
			if (!used && !expired) {
				long diff = System.nanoTime() - lastUsageNanos;
				if (diff > idleTimeNanos) {
					expired = true;
				}
			}
			return expired;
		}

		private void close() {
			endpoint.destroy();
		}
	}

}
