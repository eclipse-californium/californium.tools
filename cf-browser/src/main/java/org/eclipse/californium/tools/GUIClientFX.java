/*******************************************************************************
 * Copyright (c) 2015, 2017 Institute for Pervasive Computing, ETH Zurich and others.
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
 *    Bosch Software Innovations GmbH - migrate to SLF4J
 ******************************************************************************/
package org.eclipse.californium.tools;

import java.io.PrintStream;
import java.net.URL;

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

	public static void main(String[] args) {
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
