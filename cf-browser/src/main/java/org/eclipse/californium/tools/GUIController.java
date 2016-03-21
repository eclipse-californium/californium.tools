/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.eclipse.californium.core.CaliforniumLogger;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;

/**
 * The JavaFX controller for the gui.fxml template
 * TODO: coaps support
 * TODO: better display of different media types
 */
public class GUIController {
    private static Logger log = Logger.getLogger(GUIController.class.getName());
    private static final String DEFAULT_URI = "coap://localhost:5683";
    private static final String COAP_PROTOCOL = "coap://";

    /** Combo boxes of coap URIs and resource URIs of discovered servers */
    @FXML
    private ComboBox<String> uriBox;
    @FXML
    private TextArea logArea;
    @FXML
    private CheckMenuItem logEnabled;
    @FXML
    private TextArea requestArea;
    @FXML
    private TextArea responseArea;
    @FXML
    private TitledPane responseTitle;
    @FXML
    private TreeView resourceTree;
    @FXML
    private ImageView mediaTypeView;
    private String selectedURI;
    private String coapHost;

    public OutputStream getLogStream() {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Not implemented");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                String line = new String(b, off, len);
                Platform.runLater(() -> logArea.appendText(line));
            }
        };
    }

    @FXML
    private void initialize() {
        uriBox.itemsProperty().get().add(DEFAULT_URI);
        uriBox.getSelectionModel().select(0);
        // Initialize the
        InputStream imgIS = getClass().getResourceAsStream("/org/eclipse/californium/tools/images/unknown.png");
        Image unknown = new Image(imgIS);
        mediaTypeView.setImage(unknown);
    }

    @FXML
    private void uriSelected() {
        selectedURI = uriBox.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void getRequest() {
        performRequest(Request.newGet());
    }
    @FXML
    private void postRequest() {
        performRequest(Request.newPost());
    }
    @FXML
    private void putRequest() {
        performRequest(Request.newPut());
    }
    @FXML
    private void deleteRequest() {
        performRequest(Request.newPost());
    }

    @FXML
    private void discoveryRequest() {
        Request request = new Request(CoAP.Code.GET);
        coapHost = getHost();
        request.setURI(COAP_PROTOCOL + coapHost + "/.well-known/core");
        log.info("Begin discovery, host="+coapHost);
        request.addMessageObserver(new MessageObserverAdapter() {
            @SuppressWarnings({"rawtypes", "unchecked"})
            public void onResponse(Response response) {
                log.info(String.format("Discovery, response: %s", response));
                String text = response.getPayloadString();
                Scanner scanner = new Scanner(text);
                Pattern pattern = Pattern.compile("<");
                scanner.useDelimiter(pattern);

                ObservableList<String> ress1 = FXCollections.observableArrayList();
                ArrayList<String> ress2 = new ArrayList<>();
                while (scanner.hasNext()) {
                    String part = scanner.next();
                    String res = part.split(">")[0];
                    log.info(res);
                    ress1.add(COAP_PROTOCOL + coapHost + res);
                    ress2.add(res);
                }
                scanner.close();
                uriBox.itemsProperty().setValue(ress1);
                Platform.runLater(() -> populateTree(ress2));
            }
        });
        execute(request);
    }

    @FXML
    private void onSelectResource() {
        TreeItem<String> item = (TreeItem<String>) resourceTree.getSelectionModel().getSelectedItem();
        StringBuilder path = new StringBuilder(item.getValue());
        while(item.getParent() != null) {
            item = item.getParent();
            path.insert(0, item.getValue());
        }
        // Prepend the coap uri
        path.insert(0, COAP_PROTOCOL+coapHost);
        log.info("selected resource: "+path);
        uriBox.getSelectionModel().select(path.toString());
    }

    @FXML
    private void toggleLogging() {
        if(logEnabled.isSelected()) {
            CaliforniumLogger.setLevel(Level.FINE);
        } else {
            CaliforniumLogger.disableLogging();
            clearLog();
        }
    }

    @FXML
    private void clearLog() {
        Platform.runLater(logArea::clear);
    }
    @FXML
    private void setLogLevel(ActionEvent event) {
        MenuItem item = (MenuItem) event.getSource();
        String text = item.getText();
        Level level = Level.parse(text);
        CaliforniumLogger.setLevel(level);
    }

    private String getHost() {
        String uri = uriBox.getSelectionModel().getSelectedItem();
        StringTokenizer st = new StringTokenizer(uri, "/");
        st.nextToken();
        String host = st.nextToken();
        return host;
    }

    private void populateTree(List<String> ress) {
        TreeItem<String> rootItem = new TreeItem<>("/");
        for(String res : ress) {
            String[] parts = res.split("/");
            TreeItem cur = rootItem;
            for (int i=1;i<parts.length;i++) {
                TreeItem search = new TreeItem(parts[i]);
                ObservableList<TreeItem<String>> children = cur.getChildren();
                int index = children.indexOf(search);
                if(index < 0) {
                    children.add(search);
                    cur = search;
                }
                else {
                    cur = children.get(index);
                }
            }
        }
        rootItem.setExpanded(true);
        resourceTree.setRoot(rootItem);
    }

    /**
     * Perform the given request by adding in the resource uri from the uri combo box selection,
     * payload from the request text area, and message observer to a ResponsePrinter.
     * @param request - the coap request type
     */
    private void performRequest(Request request) {
        responseArea.setText("no response yet");
        responseTitle.setText("Response: none");
        request.addMessageObserver(new ResponsePrinter());
        request.setPayload(requestArea.getText());
        String uri = uriBox.getSelectionModel().getSelectedItem();
        uri = uri.replace(" ", "%20");
        request.setURI(uri);
        execute(request);
    }

    private void execute(Request request) {
        try {
            request.send();
            log.info("Sent request: "+request);
        } catch (Exception ex) {
            StringBuilder tmp = new StringBuilder(request.toString());
            tmp.append('\n');
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            tmp.append(sw.toString());
            log.severe(tmp.toString());
        }
    }

    private class ResponsePrinter extends MessageObserverAdapter {
        @Override
        public void onResponse(final Response response) {
            Platform.runLater(() -> {
                log.info("Recevied response: "+response);
                int size = response.getPayloadSize();
                byte[] payload = response.getPayload();
                CoAP.Type type = response.getType();
                InetAddress source = response.getSource();
                OptionSet optionSet = response.getOptions();
                int format = optionSet.getContentFormat();
                String mediaType = MediaTypeRegistry.toString(format);
                if(mediaType.startsWith("image")) {
                    // Display the image
                    Image img = new Image(new ByteArrayInputStream(payload));
                    mediaTypeView.setImage(img);
                    // Display the image uri if given, otherwise just the type and size
                    String uriPath = optionSet.getUriPathString();
                    if(uriPath != null && uriPath.length() > 0)
                        responseArea.setText(uriPath);
                    else
                        responseArea.setText(String.format("%s;size=%d", mediaType, size));
                } else {
                    // Display media type image icon and payload string
                    String ext = MediaTypeRegistry.toFileExtension(format);
                    String path = "/org/eclipse/californium/tools/images/"+ext+".png";
                    InputStream imgIS = getClass().getResourceAsStream(path);
                    if(imgIS == null)
                        imgIS = getClass().getResourceAsStream("/org/eclipse/californium/tools/images/blank.png");
                    Image unknown = new Image(imgIS);
                    mediaTypeView.setImage(unknown);
                    responseArea.setText(response.getPayloadString());
                }
                String info = String.format("Response: %s/%s;size=%d,mid=%d,source=%s,mediaType=%s", response.getCode(), response.getCode().name(),
                        size, response.getMID(), source, mediaType);
                responseTitle.setText(info);
            });
        }
    }

}
