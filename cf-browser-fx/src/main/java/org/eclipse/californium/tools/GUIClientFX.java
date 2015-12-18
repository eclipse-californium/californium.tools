/*******************************************************************************
 * Copyright (c) 2015 Red Hat
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

import java.io.PrintStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.eclipse.californium.core.CaliforniumLogger;

/**
 * A JavaFX CoAP Client to communicate with other CoAP resources.
 */
public class GUIClientFX extends Application {

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
        CaliforniumLogger.initialize();
        CaliforniumLogger.setLevel(Level.FINE);
        Logger log = Logger.getLogger(GUIClientFX.class.getName());
        log.info(String.format("MainFX.controller=%s\n", controller));
        primaryStage.setTitle("CoAP Client");
        primaryStage.setScene(new Scene(root, 900, 650));
        primaryStage.show();
    }

}
