/*******************************************************************************
 * Copyright (c) 2021 Bosch.IO GmbH and others.
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
 *    Bosch IO.GmbH - initial creation
 ******************************************************************************/
package org.eclipse.californium.tools;

import org.eclipse.californium.cli.ClientInitializer;
import org.eclipse.californium.cli.ConnectorConfig.AuthenticationMode;
import org.eclipse.californium.cli.ConnectorConfig.Secret;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.elements.util.Bytes;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.tools.GUIClientFX.GuiClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * The JavaFX controller for the dialog.fxml template
 */
public class GUIDialog {
	private static final Logger LOG = LoggerFactory.getLogger(GUIDialog.class.getName());

	private Stage stage;
	private GuiClientConfig clientConfig;
	private Secret secretConfig;

	@FXML
	private TextField identity;
	@FXML
	private PasswordField secret;
	@FXML
	private TextField secretAlt;
	@FXML
	private RadioButton formatText;
	@FXML
	private RadioButton formatHex;
	@FXML
	private RadioButton formatBase64;

	public GUIDialog() {
	}

	private Secret clone(Secret secret) {
		Secret newSecret = new Secret();
		newSecret.text = secret.text;
		newSecret.hex = secret.hex;
		newSecret.base64 = secret.base64;
		return newSecret;
	}

	public void initialize(Stage stage, GuiClientConfig config) {
		this.stage = stage;
		this.clientConfig = config;
		identity.setText(clientConfig.identity);
		if (clientConfig.secret == null) {
			secretConfig = new Secret();
			secretConfig.text = "";
		} else {
			secretConfig = clone(clientConfig.secret);
		}
		secretToView();
	}

	private void secretToView() {
		String format = "???";
		String secret = null;
		if (secretConfig.text != null) {
			formatText.setSelected(true);
			secret = secretConfig.text;
			format = "text";
		} else if (secretConfig.hex != null) {
			formatHex.setSelected(true);
			secret = secretConfig.hex;
			format = "hex";
		} else if (secretConfig.base64 != null) {
			formatBase64.setSelected(true);
			secret = secretConfig.base64;
			format = "base64";
		} else {
			formatText.setSelected(true);
			secret = "";
			format = "text";
		}
		if (formatText.isSelected()) {
			this.secret.setText(secret);
			this.secret.setVisible(true);
			this.secretAlt.setVisible(false);
		} else {
			this.secretAlt.setText(secret);
			this.secretAlt.setVisible(true);
			this.secret.setVisible(false);
		}
		LOG.info("To view {} - '{}' ({} characters)", format, secret, secret.length());
	}

	private void viewToSecret() {
		String secret = secretConfig.text != null ? this.secret.getText() : this.secretAlt.getText();
		String format = "???";
		if (secretConfig.text != null) {
			secretConfig.text = secret;
			format = "text";
		} else if (secretConfig.hex != null) {
			secretConfig.hex = secret;
			format = "hex";
		} else if (secretConfig.base64 != null) {
			secretConfig.base64 = secret;
			format = "base64";
		}
		LOG.info("From view {} - '{}' ({} characters)", format, secret, secret.length());
	}

	@FXML
	private void selectFormat(ActionEvent event) {
		LOG.info("select {}", event);
		viewToSecret();
		byte[] key;
		try {
			key = secretConfig.toKey();
		} catch (IllegalArgumentException ex) {
			logException(ex);
			key = Bytes.EMPTY;
		}
		Secret newSecret = new Secret();
		RadioButton selected = (RadioButton) event.getSource();
		if (selected == formatText) {
			try {
				newSecret.text = new String(key);
			} catch (IllegalArgumentException ex) {
				logException(ex);
				newSecret.text = "";
			}
		} else if (selected == formatHex) {
			newSecret.hex = StringUtil.byteArray2Hex(key);
		} else if (selected == formatBase64) {
			newSecret.base64 = StringUtil.byteArrayToBase64(key);
		}
		secretConfig = newSecret;
		secretToView();
	}

	@FXML
	private void ok() {
		try {
			String identity = this.identity.getText();
			LOG.info("Identity: {}", identity);
			clientConfig.uri = "coaps://";
			clientConfig.identity = identity;
			viewToSecret();
			clientConfig.secret = secretConfig;
			clientConfig.secretKey = secretConfig.toKey();
			clientConfig.authenticationModes.clear();
			clientConfig.authenticationModes.add(AuthenticationMode.PSK);
			Endpoint endpoint = ClientInitializer.createEndpoint(clientConfig, null);
			EndpointManager.getEndpointManager().setDefaultEndpoint(endpoint);
		} catch (IllegalArgumentException ex) {
			logException(ex);
		}
		close();
	}

	@FXML
	private void cancel() {
		close();
	}

	private void close() {
		Stage stage = this.stage;
		if (stage != null) {
			Platform.runLater(stage::close);
		}
	}

	private void logException(Exception ex) {
		LOG.error(GUIController.logMessage(null, ex));
	}
}
