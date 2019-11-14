/*******************************************************************************
 * Copyright (c) 2015, 2017 Institute for Pervasive Computing, ETH Zurich and others.
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
 *    Bosch Software Innovations GmbH - migrate to SLF4J
 ******************************************************************************/
package org.eclipse.californium.tools;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URL;

import javax.crypto.SecretKey;

import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.SingleNodeConnectionIdGenerator;
import org.eclipse.californium.scandium.dtls.pskstore.StringPskStore;
import org.eclipse.californium.scandium.util.SecretUtil;
import org.eclipse.californium.scandium.util.ServerNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * A JavaFX CoAP Client to communicate with other CoAP resources.
 */
public class GUIClientFX extends Application {

	private static final Logger LOG = LoggerFactory.getLogger(GUIClientFX.class);
	private static final String PSK_IDENTITY_PREFIX = "cali.";
	private static final SecretKey PSK_SECRET = SecretUtil.create(".fornium".getBytes(), "PSK");

	public static class PlugPskStore extends StringPskStore {

		private final String identity;
		private final SecretKey secret;

		public PlugPskStore(String id, byte[] secret) {
			this.identity = id;
			this.secret = secret == null ? null : SecretUtil.create(secret, "PSK");
			LOG.info("DTLS-PSK-Identity: {})", identity);
		}

		public PlugPskStore(String id) {
			identity = PSK_IDENTITY_PREFIX + id;
			secret = null;
			LOG.info("DTLS-PSK-Identity: {} ({} random bytes)", identity, (id.length() / 2));
		}

		@Override
		public SecretKey getKey(String identity) {
			if (secret != null) {
				return SecretUtil.create(secret);
			}
			if (identity.startsWith(PSK_IDENTITY_PREFIX)) {
				return SecretUtil.create(PSK_SECRET);
			}
			return null;
		}

		@Override
		public SecretKey getKey(ServerNames serverNames, String identity) {
			return getKey(identity);
		}

		@Override
		public String getIdentityAsString(InetSocketAddress inetAddress) {
			return identity;
		}

		@Override
		public String getIdentityAsString(InetSocketAddress peerAddress, ServerNames virtualHost) {
			return getIdentityAsString(peerAddress);
		}
	}

	public static void main(String[] args) {
		DtlsConnectorConfig.Builder dtlsBuilder = new DtlsConnectorConfig.Builder();
		dtlsBuilder.setPskStore(new PlugPskStore("ui"));
		dtlsBuilder.setConnectionIdGenerator(new SingleNodeConnectionIdGenerator(0));
		DtlsConnectorConfig config = dtlsBuilder.build();
		DTLSConnector dtls = new DTLSConnector(config);
		CoapEndpoint.Builder coapBuilder = new CoapEndpoint.Builder();
		coapBuilder.setConnector(dtls);
		CoapEndpoint coapEndpoint = coapBuilder.build();
		EndpointManager.getEndpointManager().setDefaultEndpoint(coapEndpoint);
		launch(args);
	}

	@Override
	public void stop() throws Exception {
		super.stop();
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		URL fxml = getClass().getResource("gui.fxml");
		FXMLLoader loader = new FXMLLoader(fxml);
		Parent root = loader.load();
		GUIController controller = loader.getController();
		PrintStream ps = new PrintStream(controller.getLogStream());
		System.setOut(ps);
		System.setErr(ps);
		LOG.info("MainFX.controller={}", controller);
		primaryStage.setTitle("CoAP Client");
		primaryStage.setScene(new Scene(root, 900, 650));
		primaryStage.show();
	}

}
