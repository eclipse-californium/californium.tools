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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.EndpointContextTracer;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;
import org.eclipse.californium.elements.util.Statistic;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.elements.util.TimeStatistic;
import org.eclipse.californium.scandium.config.DtlsConfig;
import org.eclipse.californium.scandium.config.DtlsConfig.DtlsRole;
import org.eclipse.californium.tools.jmeter.plugin.SamplerEndpointsManager.SamplerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sampler client for CoAP.
 * 
 * Also intended to be used with
 * <a href="https://www.eclipse.org/hono/" target="_blank">Eclipse/Hono</a>
 * 
 * Supports plain coap and coaps with PSK.
 * 
 * @since 3.5
 */
public class CoapSampler extends AbstractJavaSamplerClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJavaSamplerClient.class);

	static {
		DtlsConfig.register();
		CoapConfig.register();
	}

	private static final String DEFAULT_METHOD = "POST";
	private static final String DEFAULT_IDENTITY = "cali.jm-${__threadNum}";
	private static final String DEFAULT_SECRET_KEY64 = StringUtil.byteArrayToBase64(".fornium".getBytes());
	private static final String DEFAULT_SERVER_ENDPOINT = "coaps://californium.eclipseprojects.io/telemetry";
	private static final String DEFAULT_PAYLOAD = "Enter payload here";
	private static final String DEFAULT_CONTENT_TYPE = MediaTypeRegistry.toString(MediaTypeRegistry.TEXT_PLAIN);
	private static final String DEFAULT_MESSAGE_TYPE = CoAP.Type.CON.name();
	private static final String DEFAULT_QUIET_MILLIS = "0..0";
	private static final Long DEFAULT_AUTO_RESUMPTION_TIMEOUT = 30L;
	private static final Boolean DEFAULT_RECONNECT = Boolean.TRUE;
	private static final Long DEFAULT_TIMEOUT = 10L;
	private static final Long DEFAULT_IDLE_TIME = 6L;
	private static final Long DEFAULT_503_RETRIES = 3L;
	private static final Long DEFAULT_EMPTY_RETRIES = 3L;
	private static final Long DEFAULT_RETRIES = 3L;
	private static final Boolean DEFAULT_EMPTY_AS_ERROR = Boolean.TRUE;

	private static final String SERVER_ENDPOINT = "SERVER_ENDPOINT";
	private static final String MESSAGE_PAYLOAD = "MESSAGE_PAYLOAD";
	private static final String IDENTITY = "IDENTITY";
	private static final String SECRET_KEY64 = "SECRET_KEY64";
	private static final String CONTENT_TYPE = "CONTENT_TYPE";
	private static final String ACCEPT_TYPE = "ACCEPT_TYPE";
	private static final String MESSAGE_TYPE = "MESSAGE_TYPE";
	private static final String CONNECTION_TIME_OUT = "CONNECTION_TIME_OUT";
	private static final String CONNECTION_IDLE_TIME = "CONNECTION_IDLE_TIME";
	private static final String AUTO_RESUMPTION_TIME_OUT = "AUTO_RESUMPTION_TIME_OUT";
	private static final String RECONNECT = "RECONNECT";
	private static final String RETRY_503 = "RETRY_503";
	private static final String RETRY_EMPTY = "RETRY_EMPTY";
	private static final String RETRY = "RETRY";
	private static final String EMPTY_AS_ERROR = "EMPTY_AS_ERROR";
	private static final String METHOD = "METHOD";
	private static final String QUIET = "QUIET_MILLIS";

	/**
	 * Count all tests (clients).
	 */
	private static final AtomicInteger COUNTER = new AtomicInteger();
	/**
	 * Overall request counter.
	 */
	private static final AtomicInteger TEST_COUNTER = new AtomicInteger();
	/**
	 * Count active tests (clients).
	 */
	private static final AtomicInteger ACTIVE_COUNTER = new AtomicInteger();

	private static final AtomicReference<Statistics> statistics = new AtomicReference<>(new Statistics());

	/**
	 * The DNS lookup may return multiple ip-addresses. Keep the selected ip-address
	 * of the first request in order to send the follow-up request to the same
	 * ip-address and prevent further DNS lookups for this test.
	 */
	private final AtomicReference<EndpointContext> destination = new AtomicReference<>();
	private final int id = COUNTER.incrementAndGet();
	private final Random random = new Random();
	private Configuration configuration;
	private SamplerEndpoint samplerEndpoint;
	private long idleTimeMillis;
	private String scheme;
	private String identity;
	private byte[] secret;
	private boolean emptyAsError;
	private boolean reconnect;
	private long quietMillis;
	private long quietRange;
	private Long lastResponse;

	public CoapSampler() {
	}

	@Override
	public Arguments getDefaultParameters() {
		Arguments result = new Arguments();
		result.addArgument(SERVER_ENDPOINT, DEFAULT_SERVER_ENDPOINT);
		result.addArgument(MESSAGE_PAYLOAD, DEFAULT_PAYLOAD);
		result.addArgument(IDENTITY, DEFAULT_IDENTITY);
		result.addArgument(SECRET_KEY64, DEFAULT_SECRET_KEY64);
		result.addArgument(CONNECTION_TIME_OUT, DEFAULT_TIMEOUT.toString());
		result.addArgument(CONNECTION_IDLE_TIME, DEFAULT_IDLE_TIME.toString());
		result.addArgument(AUTO_RESUMPTION_TIME_OUT, DEFAULT_AUTO_RESUMPTION_TIMEOUT.toString());
		result.addArgument(RECONNECT, DEFAULT_RECONNECT.toString());
		result.addArgument(METHOD, DEFAULT_METHOD);
		result.addArgument(CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
		result.addArgument(ACCEPT_TYPE, DEFAULT_CONTENT_TYPE);
		result.addArgument(MESSAGE_TYPE, DEFAULT_MESSAGE_TYPE);
		result.addArgument(EMPTY_AS_ERROR, DEFAULT_EMPTY_AS_ERROR.toString());
		result.addArgument(RETRY_503, DEFAULT_503_RETRIES.toString());
		result.addArgument(RETRY_EMPTY, DEFAULT_EMPTY_RETRIES.toString());
		result.addArgument(RETRY, DEFAULT_RETRIES.toString());
		result.addArgument(QUIET, DEFAULT_QUIET_MILLIS);
		return result;
	}

	@Override
	public void setupTest(JavaSamplerContext context) {
		ACTIVE_COUNTER.incrementAndGet();
		String serverEndPoint = context.getParameter(SERVER_ENDPOINT, DEFAULT_SERVER_ENDPOINT);
		scheme = CoAP.getSchemeFromUri(serverEndPoint);

		long autoResumptionTimeoutSeconds = context.getLongParameter(AUTO_RESUMPTION_TIME_OUT,
				DEFAULT_AUTO_RESUMPTION_TIMEOUT);
		idleTimeMillis = TimeUnit.SECONDS.toMillis(context.getLongParameter(CONNECTION_IDLE_TIME, DEFAULT_IDLE_TIME));
		identity = context.getParameter(IDENTITY, DEFAULT_IDENTITY);
		String value = context.getParameter(SECRET_KEY64, DEFAULT_SECRET_KEY64);
		secret = StringUtil.base64ToByteArray(value);
		value = context.getParameter(EMPTY_AS_ERROR, DEFAULT_EMPTY_AS_ERROR.toString());
		emptyAsError = Boolean.valueOf(value);
		value = context.getParameter(RECONNECT, DEFAULT_RECONNECT.toString());
		reconnect = Boolean.valueOf(value);
		value = context.getParameter(QUIET, DEFAULT_QUIET_MILLIS);
		String[] range = value.split("\\.\\.");
		try {
			quietMillis = Long.valueOf(range[0]);
			if (range.length == 2) {
				quietRange = Long.valueOf(range[1]);
			} else {
				quietRange = quietMillis;
			}
		} catch (NumberFormatException ex) {
			quietRange = quietMillis;
		}
		configuration = new Configuration();
		configuration.set(DtlsConfig.DTLS_ROLE, DtlsRole.CLIENT_ONLY);
		configuration.set(DtlsConfig.DTLS_MAX_CONNECTIONS, 8);
		configuration.set(DtlsConfig.DTLS_CONNECTOR_THREAD_COUNT, 1);
		configuration.set(DtlsConfig.DTLS_RECEIVER_THREAD_COUNT, 1);
		configuration.set(DtlsConfig.DTLS_AUTO_HANDSHAKE_TIMEOUT, autoResumptionTimeoutSeconds, TimeUnit.SECONDS);
		configuration.set(UdpConfig.UDP_RECEIVER_THREAD_COUNT, 1);
		configuration.set(UdpConfig.UDP_SENDER_THREAD_COUNT, 1);

		samplerEndpoint = SamplerEndpointsManager.INSTANCE.getSampleEndpoint(scheme, identity, secret, idleTimeMillis,
				configuration);
		LOGGER.info("Setup Test ID{} for {} [{}..{}] ({})", id, identity, quietMillis, quietRange,
				context.getJMeterContext().getThreadNum());
	}

	public SampleResult runTest(JavaSamplerContext context) {
		int count = TEST_COUNTER.incrementAndGet();
		LOGGER.debug("Run Test ID{} - {}: {}", id, count, identity);
		final SampleResult result = new SampleResult();
		long connectionTimeout = context.getLongParameter(CONNECTION_TIME_OUT, DEFAULT_TIMEOUT);
		long retries503 = context.getLongParameter(RETRY_503, DEFAULT_503_RETRIES);
		long retriesEmpty = context.getLongParameter(RETRY_EMPTY, DEFAULT_EMPTY_RETRIES);
		long retries = context.getLongParameter(RETRY, DEFAULT_RETRIES);
		long retry = 0;
		long retry503 = 0;
		long retryError = 0;
		long retryEmpty = 0;
		long transmissionTimeouts = 0;
		long reconnects = 0;

		RetransmissionCounter retransmissions = new RetransmissionCounter();
		Request request = newRequest(context, count);

		String uri = request.getURI();
		String scheme = CoAP.getSchemeFromUri(uri);
		result.setSamplerData(
				String.format("ID%s: %s, url: %s, msg: %s", id, identity, uri, request.getPayloadString()));
		if (COAP_URL_SCHEME != null) {
			// support for "coap:" and "coaps" active
			try {
				URL url = new URL(uri);
				result.setURL(url);
				LOGGER.trace("Run Test ID{}: {} URL {}", id, identity, url);
			} catch (MalformedURLException e) {
				LOGGER.info("Run Test ID{}:", id, e);
			}
		}
		result.setRequestHeaders(request.getOptions().toString());
		result.sampleStart();
		if (samplerEndpoint == null) {
			LOGGER.warn("Run Test ID{} - {} failed, setup broken!", id, count);
			result.setResponseMessage("Setup Failed!");
			result.setSuccessful(false);
			result.sampleEnd();
		}
		request.addMessageObserver(new EndpointContextTracer() {

			private volatile boolean connect;

			@Override
			public void onConnecting() {
				connect = true;
			}

			@Override
			public void onDtlsRetransmission(int flight) {
				LOGGER.debug("Run Test ID{}: dtls retransmission {}", id, flight);
			}

			@Override
			protected void onContextChanged(EndpointContext endpointContext) {
				if (connect) {
					synchronized (result) {
						result.connectEnd();
					}
				}
				LOGGER.debug("Run Test ID{}: dtls-context {}", id, Utils.prettyPrint(endpointContext));
			}
		});
		request.addMessageObserver(retransmissions);
		Statistics statistics = CoapSampler.statistics.get();
		Response response = null;
		try {
			long minimumQuiet = 0;
			boolean send = true;
			while (send) {
				quiet(count, minimumQuiet);
				if (!samplerEndpoint.update(true)) {
					// expired ... reopen
					reconnects++;
					samplerEndpoint = SamplerEndpointsManager.INSTANCE.getSampleEndpoint(scheme, identity, secret,
							idleTimeMillis, configuration);
					samplerEndpoint.update(true);
				}
				LOGGER.debug("Run Test ID{} - {}: {} send request ...", id, count, identity);
				request.send(samplerEndpoint.getEndpoint());
				response = request.waitForResponse(TimeUnit.SECONDS.toMillis(connectionTimeout));
				lastResponse = System.nanoTime();
				statistics.retransmissionStatistic.add(retransmissions.counter.get());
				send = false;
				if (response != null) {
					statistics.rttStatistic.add(response.getApplicationRttNanos(), TimeUnit.NANOSECONDS);
					ResponseCode code = response.getCode();
					if (ResponseCode.SERVICE_UNAVAILABLE.equals(code)) {
						if (retry503 < retries503) {
							++retry503;
							send = true;
							Long maxAge = response.getOptions().getMaxAge();
							minimumQuiet = TimeUnit.SECONDS.toMillis(maxAge);
						}
					} else if (response.isError()) {
						++retryError;
					} else if (ResponseCode.CHANGED.equals(code) && response.getPayloadSize() == 0) {
						if (retryEmpty < retriesEmpty) {
							++retryEmpty;
							send = true;
						}
					}
				} else {
					if (request.isAcknowledged() || !reconnect) {
						// received ack, lost response, common retry
						send = true;
					} else {
						// no ack received.
						// reset ip-address, expire dtls-endpoint.
						// => trigger new handshake to new dns-resolved host.
						++transmissionTimeouts;
						EndpointContext destinationContext = destination.getAndSet(null);
						if (destinationContext != null) {
							samplerEndpoint.expire();
							send = true;
						}
					}
				}
				if (send) {
					if (retry < retries) {
						++retry;
					} else {
						send = false;
					}
				}
				if (send) {
					request = newRequest(context, count);
					retransmissions = new RetransmissionCounter();
					request.addMessageObserver(retransmissions);
				}
			}
		} catch (InterruptedException e) {
			LOGGER.warn("Run Test ID{} - {}: Request interrupted:", id, count, e);
			result.setResponseMessage("Request Interrupted");
		}
		samplerEndpoint.update(false);
		synchronized (result) {
			result.latencyEnd();
			result.sampleEnd();
		}
		statistics.retriesStatistic.add(retry);
		statistics.retriesEmptyStatistic.add(retryEmpty);
		statistics.error503Statistic.add(retry503);
		statistics.errorsStatistic.add(retryError);
		statistics.reconnectStatistic.add(reconnects);
		statistics.timeoutsStatistic.add(transmissionTimeouts);
		int payload = request.getPayloadSize();
		result.setBodySize((long) payload);
		byte[] bytes = request.getBytes();
		if (bytes != null) {
			result.setHeadersSize(bytes.length - payload);
		}

		if (response == null) {
			if (request.isTimedOut()) {
				result.setResponseMessage("Request Timed Out");
			} else if (request.isRejected()) {
				result.setResponseMessage("Request Rejected");
			} else if (request.getSendError() != null) {
				result.setResponseMessage("Request Send Error '" + request.getSendError().getMessage() + "'");
			} else {
				result.setResponseMessage(
						"Response Timed Out " + (request.isAcknowledged() ? "with ACK." : "without ACK!"));
			}
			LOGGER.info("Run Test ID{} - {}: failed after {}ms: {}{}{}{}{}", id, count, result.getTime(),
					result.getResponseMessage(), StringUtil.lineSeparator(),
					request.getDestinationContext().getPeerAddress(), StringUtil.lineSeparator(),
					Utils.prettyPrint(request));
			result.setSuccessful(false);
		} else {
			boolean failed = !response.isSuccess();
			boolean failedEmpty = !failed && emptyAsError && response.getPayloadSize() == 0;
			if (failed) {
				LOGGER.info("Run Test ID{} - {}: failed with response after {}ms!{}{}", id, count, result.getTime(),
						StringUtil.lineSeparator(), Utils.prettyPrint(request));
			} else if (failedEmpty) {
				LOGGER.info("Run Test ID{} - {}: failed with empty response after {}ms!{}{}", id, count,
						result.getTime(), StringUtil.lineSeparator(), Utils.prettyPrint(request));
			} else {
				LOGGER.debug("Run Test ID{} - {}: succeeded after {}ms{}{}", id, count, result.getTime(),
						StringUtil.lineSeparator(), Utils.prettyPrint(request));
			}
			LOGGER.debug("Run Test ID{} - {}:{}{}", id, count, StringUtil.lineSeparator(), Utils.prettyPrint(response));
			result.setResponseCode(response.getCode().toString() + " - " + response.getCode().name());
			result.setResponseData(response.getPayload());
			result.setEncodingAndType(MediaTypeRegistry.toString(response.getOptions().getContentFormat()));
			result.setResponseHeaders(response.getOptions().toString());
			result.setResponseMessage(Utils.prettyPrint(response));
			result.setSuccessful(!failed && !failedEmpty);
		}
		return result;
	}

	private void quiet(int count, long minimumMillis) throws InterruptedException {
		if (lastResponse != null) {
			long range = quietRange - quietMillis;
			if (minimumMillis < quietMillis) {
				minimumMillis = quietMillis;
			} else if (minimumMillis < quietRange) {
				range = quietRange - minimumMillis;
			} else {
				range = 0;
			}
			if (range > 0) {
				range = random.nextInt((int) range);
			}
			range += minimumMillis;
			if (range > 0) {
				// adjust past time
				range += TimeUnit.NANOSECONDS.toMillis(lastResponse - System.nanoTime());
				LOGGER.debug("Run Test ID{} - {}: {}ms delay", id, count, range);
				Thread.sleep(range);
			}
		}
	}

	@Override
	public void teardownTest(JavaSamplerContext context) {
		LOGGER.debug("Teardown Test ID{}", id);
		if (ACTIVE_COUNTER.decrementAndGet() == 0) {
			Statistics result = statistics.getAndSet(new Statistics());
			LOGGER.info("RTT           : {}", result.rttStatistic.getSummaryAsText());
			LOGGER.info("Retransmission: {}", result.retransmissionStatistic.getSummaryAsText());
			LOGGER.info("Trans. Timeout: {}", result.timeoutsStatistic.getSummaryAsText());
			LOGGER.info("Retries       : {}", result.retriesStatistic.getSummaryAsText());
			LOGGER.info("Retries/Empty : {}", result.retriesEmptyStatistic.getSummaryAsText());
			LOGGER.info("5.03          : {}", result.error503Statistic.getSummaryAsText());
			LOGGER.info("Errors        : {}", result.errorsStatistic.getSummaryAsText());
			LOGGER.info("Reconnects    : {}", result.reconnectStatistic.getSummaryAsText());
		}
	}

	private Request newRequest(JavaSamplerContext context, int count) {
		String method = context.getParameter(METHOD, DEFAULT_METHOD);
		String serverEndPoint = context.getParameter(SERVER_ENDPOINT, DEFAULT_SERVER_ENDPOINT);
		String messagePayload = context.getParameter(MESSAGE_PAYLOAD, DEFAULT_PAYLOAD).replace("\\n", "\n");
		String contentType = context.getParameter(CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
		String acceptType = context.getParameter(ACCEPT_TYPE, DEFAULT_CONTENT_TYPE);
		String messageType = context.getParameter(MESSAGE_TYPE, DEFAULT_MESSAGE_TYPE);

		LOGGER.debug("New Request ID{} - {}: {}, {}", id, count, identity, messagePayload);

		Request request;
		if (method.equals("GET")) {
			request = Request.newGet();
		} else if (method.equals("POST")) {
			request = Request.newPost();
		} else if (method.equals("PUT")) {
			request = Request.newPut();
		} else if (method.equals("DELETE")) {
			request = Request.newDelete();
		} else {
			request = Request.newPost();
		}
		EndpointContext destinationContext = destination.get();
		if (destinationContext != null) {
			// pin ip-address of first ns-lookup
			request.setDestinationContext(destinationContext);
		}
		request.setURI(serverEndPoint);
		destination.compareAndSet(null, request.getDestinationContext());
		if (messageType.equals(CoAP.Type.CON.name())) {
			request.setConfirmable(true);
		} else if (messageType.equals(CoAP.Type.NON.name())) {
			request.setConfirmable(false);
		}
		if (request.isIntendedPayload()) {
			request.setPayload(messagePayload);
			int ctype = MediaTypeRegistry.parse(contentType);
			request.getOptions().setContentFormat(ctype);
		}
		int atype = MediaTypeRegistry.parse(acceptType);
		request.getOptions().setAccept(atype);

		return request;
	}

	private static final URLStreamHandlerFactory COAP_URL_SCHEME;

	static {
		URLStreamHandlerFactory coap = new URLStreamHandlerFactory() {

			public URLStreamHandler createURLStreamHandler(final String protocol) {
				if (CoAP.isSupportedScheme(protocol)) {
					return new URLStreamHandler() {

						@Override
						protected URLConnection openConnection(URL u) throws IOException {
							throw new IOException(protocol + " is no stream protocol!");
						}
					};
				} else {
					return null;
				}
			}

		};
		try {
			URL.setURLStreamHandlerFactory(coap);
		} catch (Error e) {
			LOGGER.error("Add coap protocol handler:", e);
			coap = null;
		}
		COAP_URL_SCHEME = coap;
	}

	private static class RetransmissionCounter extends MessageObserverAdapter {
		private AtomicInteger counter = new AtomicInteger();

		@Override
		public void onRetransmission() {
			counter.incrementAndGet();
		}

	}

	private static class Statistics {
		private final TimeStatistic rttStatistic = new TimeStatistic(30000, 10, TimeUnit.MILLISECONDS);
		private final Statistic retransmissionStatistic = new Statistic(10, 1);
		private final Statistic retriesStatistic = new Statistic(10, 1);
		private final Statistic retriesEmptyStatistic = new Statistic(10, 1);
		private final Statistic error503Statistic = new Statistic(10, 1);
		private final Statistic errorsStatistic = new Statistic(10, 1);
		private final Statistic timeoutsStatistic = new Statistic(10, 1);
		private final Statistic reconnectStatistic = new Statistic(10, 1);
	}
}
