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
 *    Achim Kraus (Bosch Software Innovations GmbH) - use SslContextUtil
 ******************************************************************************/
package org.eclipse.californium.tools;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.cli.ClientConfig;
import org.eclipse.californium.cli.ClientInitializer;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.EndpointContextTracer;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.Token;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.observe.NotificationListener;
import org.eclipse.californium.elements.AddressEndpointContext;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.Configuration.DefinitionsProvider;
import org.eclipse.californium.elements.config.TcpConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * This class implements a simple CoAP client for testing purposes. Usage:
 * <p>
 * {@code java -jar cf-client-???.jar --method=METHOD --payload=PAYLOAD URI}
 * {@code java -jar cf-client-???.jar --extended-method=EXTENDED-METHOD URI}
 * <ul>
 * <li>METHOD: {GET, POST, PUT, DELETE}
 * <li>EXTENDED-METHOD: {DISCOVER, OBSERVE}
 * <li>URI: The URI to the remote endpoint or resource}
 * <li>PAYLOAD: The data to send with the request}
 * </ul>
 * Options:
 * <ul>
 * <li>--loop: Loop for multiple responses
 * </ul>
 * Examples:
 * <ul>
 * <li>{@code ConsoleClient --extended-method=DISCOVER coap://localhost}
 * <li>{@code ConsoleClient --method=POST coap://someServer.org:5683 --payload="my data"}
 * </ul>
 */
public class ConsoleClient {

	// resource URI path used for discovery
	private static final String DISCOVERY_RESOURCE = "/.well-known/core";

	// exit codes for runtime errors
	private static final int ERR_MISSING_METHOD = 1;
	private static final int ERR_MISSING_URI = 3;
	private static final int ERR_REQUEST_FAILED = 5;
	private static final int ERR_RESPONSE_FAILED = 6;

	private static final int DEFAULT_MAX_RESOURCE_SIZE = 8192;
	private static final int DEFAULT_BLOCK_SIZE = 1024;

	private static DefinitionsProvider DEFAULTS = new DefinitionsProvider() {

		@Override
		public void applyDefinitions(Configuration config) {
			config.set(CoapConfig.MAX_RESOURCE_BODY_SIZE, DEFAULT_MAX_RESOURCE_SIZE);
			config.set(CoapConfig.MAX_MESSAGE_SIZE, DEFAULT_BLOCK_SIZE);
			config.set(CoapConfig.PREFERRED_BLOCK_SIZE, DEFAULT_BLOCK_SIZE);
			config.set(CoapConfig.MAX_ACTIVE_PEERS, 10);
			config.set(CoapConfig.MAX_PEER_INACTIVITY_PERIOD, 24, TimeUnit.HOURS);
			config.set(TcpConfig.TCP_CONNECTION_IDLE_TIMEOUT, 12, TimeUnit.HOURS);
			config.set(TcpConfig.TCP_CONNECT_TIMEOUT, 20, TimeUnit.SECONDS);
			config.set(TcpConfig.TCP_WORKER_THREADS, 2);
		}
	};

	public enum ExtendedCode {
		DISCOVER, OBSERVE, PING
	}

	@Command(name = "ConsoleClient", version = "(c) 2014, Institute for Pervasive Computing, ETH Zurich and others.")
	private static class Config extends ClientConfig {

		@Option(names = "--extended-method", description = "Extended method.")
		public ExtendedCode extendedMethod;

		@Option(names = "--loop", description = "keep console after request.")
		public boolean loop;

		public URI destination;

		@Override
		public void defaults() {
			super.defaults();
			try {
				destination = new URI(uri);
				if (extendedMethod != null) {
					if (method == null) {
						method = CoAP.Code.GET;
					}
					if (ExtendedCode.DISCOVER.equals(extendedMethod)) {
						String path = destination.getPath();
						if (path == null || path.isEmpty() || path.equals("/")) {
							destination = new URI(destination.getScheme(), destination.getAuthority(),
									DISCOVERY_RESOURCE, destination.getQuery());
						}
					}
				}
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * Main method of this client.
	 */
	public static void main(String[] args) throws IOException, GeneralSecurityException {
		final Config clientConfig = new Config();
		clientConfig.customConfigurationDefaultsProvider = DEFAULTS;

		ClientInitializer.init(args, clientConfig);

		if (clientConfig.helpRequested) {
			System.exit(0);
		}

		// check if mandatory parameters specified
		if (clientConfig.method == null) {
			System.err.println("Method not specified");
			System.exit(ERR_MISSING_METHOD);
		}
		if (clientConfig.destination == null) {
			System.err.println("URI not specified");
			System.exit(ERR_MISSING_URI);
		}

		CountDownLatch ready;

		// execute request
		try {
			if (ExtendedCode.PING.equals(clientConfig.extendedMethod)) {
				ready = ping(clientConfig);
			} else {
				ready = request(clientConfig);
			}

			ready.await(5, TimeUnit.SECONDS);

			// loop for receiving multiple responses
			do {
				Thread.sleep(1000);
			} while (clientConfig.loop);

		} catch (Exception e) {
			System.err.println("Failed to execute request: " + e.getMessage());
			System.exit(ERR_REQUEST_FAILED);
		}
	}

	private static CountDownLatch request(final Config clientConfig) {
		final CountDownLatch ready = new CountDownLatch(1);
		// create request according to specified method
		Type type = Type.CON;
		if (clientConfig.messageType != null && clientConfig.messageType.non) {
			type = Type.NON;
		}
		final Request request = new Request(clientConfig.method, type);
		if (ExtendedCode.OBSERVE.equals(clientConfig.extendedMethod)) {
			request.setObserve();
			clientConfig.loop = true;
		}
		if (clientConfig.proxy != null) {
			request.setDestinationContext(new AddressEndpointContext(clientConfig.proxy.destination));
			if (clientConfig.proxy.scheme != null) {
				request.getOptions().setProxyScheme(clientConfig.proxy.scheme);
			}
		}
		request.setURI(clientConfig.destination);
		if (clientConfig.payload != null) {
			request.setPayload(clientConfig.payload.payloadBytes);
		}
		if (clientConfig.contentType != null) {
			request.getOptions().setContentFormat(clientConfig.contentType.contentType);
		}
		final MessageObserverAdapter observer = new EndpointContextTracer() {
			private boolean requestPrinted;

			@Override
			public void onReadyToSend() {
				if (!requestPrinted) {
					System.out.println(Utils.prettyPrint(request));
					System.out.println();
					requestPrinted = true;
				}
			}

			@Override
			public void onDtlsRetransmission(int flight) {
				System.out.println(">>> DTLS retransmission, flight " + flight);
			}

			@Override
			protected void onContextChanged(EndpointContext endpointContext) {
				System.out.println(Utils.prettyPrint(endpointContext));
				System.out.println();
			}

			@Override
			public void onResponse(final Response response) {
				if (response.getApplicationRttNanos() != null) {
					System.out.println("Time elapsed (ms): " + TimeUnit.NANOSECONDS.toMillis(response.getApplicationRttNanos()));
				}

				// check of response contains resources
				if (response.getOptions().isContentFormat(MediaTypeRegistry.APPLICATION_LINK_FORMAT)) {

					// output discovered resources
					System.out.println("\nDiscovered resources:");

					Set<WebLink> links = LinkFormat.parse(response.getPayloadString());
					for (WebLink link : links) {
						System.out.println(link);
					}

				} else {
					System.out.println(Utils.prettyPrint(response));
					// check if link format was expected by client
					if (ExtendedCode.DISCOVER.equals(clientConfig.extendedMethod)) {
						System.out.println("Server error: Link format not specified");
					}
				}
				ready.countDown();
			}

			@Override
			protected void failed() {
				ready.countDown();
			}
		};
		request.addMessageObserver(observer);

		final Endpoint endpoint = EndpointManager.getEndpointManager().getDefaultEndpoint(request.getScheme());
		if (clientConfig.extendedMethod == ExtendedCode.OBSERVE) {
			endpoint.addNotificationListener(new NotificationListener() {
				@Override
				public void onNotification(Request requestParam, Response response) {
					if (requestParam.getToken().equals(request.getToken())) {
						observer.onResponse(response);
					}
				}
			});
		}

		request.send(endpoint);
		return ready;
	}

	private static CountDownLatch ping(final Config clientConfig) {
		final CountDownLatch ready = new CountDownLatch(1);
		final Request request = new Request(null, Type.CON);
		request.setToken(Token.EMPTY);
		request.setURI(clientConfig.destination);
		request.addMessageObserver(new EndpointContextTracer() {
			private boolean requestPrinted;

			@Override
			public void onReadyToSend() {
				if (!requestPrinted) {
					System.out.println(Utils.prettyPrint(request));
					System.out.println();
					requestPrinted = true;
				}
			}

			@Override
			public void onDtlsRetransmission(int flight) {
				System.out.println(">>> DTLS retransmission, flight " + flight);
			}

			@Override
			protected void onContextChanged(EndpointContext endpointContext) {
				System.out.println(Utils.prettyPrint(endpointContext));
				System.out.println();
			}

			@Override
			public void onReject() {
				System.out.println("ping successful!");
				ready.countDown();
			}

			@Override
			protected void failed() {
				ready.countDown();
			}
		});

		request.send();
		return ready;
	}
}
