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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.eclipse.californium.cli.ClientConfig;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.ClientObserveRelation;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.observe.NotificationListener;
import org.eclipse.californium.elements.AddressEndpointContext;
import org.eclipse.californium.elements.DtlsEndpointContext;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.MapBasedEndpointContext;
import org.eclipse.californium.elements.util.DaemonThreadFactory;
import org.eclipse.californium.elements.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

/**
 * The JavaFX controller for the gui.fxml template TODO: coaps support TODO:
 * better display of different media types
 */
public class GUIController {

	private static final Logger LOG = LoggerFactory.getLogger(GUIController.class.getName());
	private static final String DEFAULT_URI = "coap://localhost:5683";
	private static final String SANDBOX_URI = "coap://californium.eclipse.org:5683";

	private final List<String> URIS = new ArrayList<>();
	private ClientConfig clientConfig;

	private Stage stage;

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
	private TitledPane requestTitle;
	@FXML
	private TextArea connectionArea;
	@FXML
	private TitledPane connectionTitle;
	@FXML
	private TextArea responseArea;
	@FXML
	private TitledPane responseTitle;
	@FXML
	private TreeView<String> resourceTree;
	@FXML
	private ImageView mediaTypeView;
	@FXML
	private Button getButton;
	@FXML
	private Button postButton;
	@FXML
	private Button putButton;
	@FXML
	private Button deleteButton;
	@FXML
	private Button observeButton;
	@FXML
	private Button discoverButton;

	private String coapHost;

	private Image unknown;
	private Image blank;

	private NotificationPrinter notificationPrinter;

	private Endpoint endpoint;

	private ClientObserveRelation observe;

	private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("Timer#"));

	public GUIController() {
		URIS.add(DEFAULT_URI);
		URIS.add(SANDBOX_URI);
	}

	private boolean addURI(String uri) {
		if (!URIS.contains(uri)) {
			URIS.add(0, uri);
			return true;
		} else {
			return false;
		}
	}

	public void initialize(Stage stage, ClientConfig config) {
		this.stage = stage;
		this.clientConfig = config;
		if (addURI(config.uri)) {
			initializeUriBox();
		}
	}

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

	private void initializeUriBox() {
		ObservableList<String> list = uriBox.itemsProperty().get();
		list.clear();
		for (String uri : URIS) {
			list.add(uri);
		}
		uriBox.getSelectionModel().select(0);
	}

	@FXML
	private void initialize() {
		initializeUriBox();
		// Initialize the
		InputStream imgIS = getClass().getResourceAsStream("/org/eclipse/californium/tools/images/unknown.png");
		unknown = new Image(imgIS);
		mediaTypeView.setImage(unknown);

		imgIS = getClass().getResourceAsStream("/org/eclipse/californium/tools/images/blank.png");
		blank = new Image(imgIS);

		notificationPrinter = new NotificationPrinter();

		resetConnectionTitle();
	}

	private Endpoint getLocalEndpoint(String uri) {
		try {
			String scheme = CoAP.getSchemeFromUri(uri);
			if (scheme == null) {
				scheme = uri;
			}
			Endpoint endpoint = EndpointManager.getEndpointManager().getDefaultEndpoint(scheme);
			synchronized (this) {
				if (this.endpoint != endpoint) {
					if (this.endpoint != null) {
						this.endpoint.removeNotificationListener(notificationPrinter);
					}
					this.endpoint = endpoint;
					if (this.endpoint != null) {
						this.endpoint.addNotificationListener(notificationPrinter);
					}
				}
			}
			return endpoint;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private void resetConnectionTitle() {
		StringBuilder title = new StringBuilder("Connection:");
		Endpoint endpoint = getLocalEndpoint(uriBox.getSelectionModel().getSelectedItem());
		if (endpoint != null) {
			title.append(" from ").append(StringUtil.toString(endpoint.getAddress()));
		}
		connectionTitle.setText(title.toString());
	}

	private void setButtonsDisable(boolean disable) {
		getButton.setDisable(disable);
		postButton.setDisable(disable);
		putButton.setDisable(disable);
		deleteButton.setDisable(disable);
		discoverButton.setDisable(disable);
	}

	@FXML
	private void uriSelected() {
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
		performRequest(Request.newDelete());
	}

	@FXML
	private void observeRequest() {
		if (observe != null) {
			observe.proactiveCancel();
			observeButton.setText("OBSERVE");
			setButtonsDisable(false);
			observe = null;
		} else {
			Endpoint endpoint = getLocalEndpoint(uriBox.getSelectionModel().getSelectedItem());
			if (endpoint != null) {
				Request request = Request.newGet();
				request.getOptions().setObserve(0);
				observe = new ClientObserveRelation(request, endpoint, timer);
				observeButton.setText("CANCEL");
				setButtonsDisable(true);
				performRequest(request);
			}
		}
	}

	@FXML
	private void discoveryRequest() {
		Request request = new Request(CoAP.Code.GET);
		coapHost = getHost();
		request.setURI(coapHost + "/.well-known/core");
		LOG.info("Begin discovery, host={}", coapHost);
		request.addMessageObserver(new MessageObserverAdapter() {

			public void onResponse(Response response) {
				LOG.info("Discovery, response: {}", response);
				String text = response.getPayloadString();
				Scanner scanner = new Scanner(text);
				Pattern pattern = Pattern.compile("<");
				scanner.useDelimiter(pattern);

				ObservableList<String> ress1 = FXCollections.observableArrayList();
				ArrayList<String> ress2 = new ArrayList<>();
				ress1.add(coapHost);
				ress2.add(".");

				while (scanner.hasNext()) {
					String part = scanner.next();
					String res = part.split(">")[0];
					LOG.info(res);
					ress1.add(coapHost + res);
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
		if (item == null) { // Handle case when no node in the tree is selected, just browsed into.
			return;
		}
		StringBuilder path = new StringBuilder(item.getValue());
		while (item.getParent() != null) {
			item = item.getParent();
			path.insert(0, item.getValue() + "/"); // Add slash separator to hierarchical paths.
		}
		// Strip the extra leading slash added in the loop.
		if (path.toString().startsWith("/")) {
			path.delete(0, 1);
		}
		// Prepend the coap uri
		path.insert(0, coapHost);
		LOG.info("selected resource: {}", path);
		uriBox.getSelectionModel().select(path.toString());
	}

	@FXML
	private void resetConnection() {
		Endpoint endpoint = getLocalEndpoint(uriBox.getSelectionModel().getSelectedItem());
		if (endpoint != null) {
			try {
				endpoint.stop();
				endpoint.start();
				resetConnectionTitle();
				connectionArea.setText("");
			} catch (IOException ex) {
				LOG.error("Restart connection:", ex);
			}
		}
	}

	@FXML
	private void toggleLogging() {
		Logger logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		if (ch.qos.logback.classic.Logger.class.isInstance(logger)) {
			if (logEnabled.isSelected()) {
				setRootLoggerLevel(Level.DEBUG);
			} else {
				setRootLoggerLevel(Level.OFF);
			}
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
		Level level = Level.toLevel(text);
		setRootLoggerLevel(level);
	}

	private void setRootLoggerLevel(Level newLevel) {
		Logger logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		if (ch.qos.logback.classic.Logger.class.isInstance(logger)) {
			ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) logger;
			rootLogger.setLevel(newLevel);
			if (Level.OFF.equals(newLevel)) {
				clearLog();
			}
		}
	}

	@FXML
	private void onExit() {
		Stage stage = this.stage;
		if (stage != null) {
			Platform.runLater(stage::close);
		}
	}

	private String getHost() {
		String uri = uriBox.getSelectionModel().getSelectedItem();
		StringTokenizer st = new StringTokenizer(uri, "/");
		String protocol = st.nextToken();
		String host = st.nextToken();
		return protocol + "//" + host;
	}

	private void populateTree(List<String> ress) {
		TreeItem<String> rootItem = new TreeItem<>("/");
		for (String res : ress) {
			String[] parts = res.split("/");
			TreeItem<String> cur = rootItem;
			for (int i = 1; i < parts.length; i++) {
				TreeItem<String> search = new TreeItem<>(parts[i]);
				ObservableList<TreeItem<String>> children = cur.getChildren();
				FilteredList<TreeItem<String>> filteredChildren = children
						.filtered(treeItem -> search.getValue().equals(treeItem.getValue()));
				if (filteredChildren.size() == 0) {
					children.add(search);
					cur = search;
				} else {
					cur = filteredChildren.get(0);
				}
			}
		}
		rootItem.setExpanded(true);
		resourceTree.setRoot(rootItem);
	}

	/**
	 * Perform the given request by adding in the resource uri from the uri combo
	 * box selection, payload from the request text area, and message observer to a
	 * ResponsePrinter.
	 * 
	 * @param request - the coap request type
	 */
	private void performRequest(Request request) {
		responseArea.setText("no response yet");
		responseTitle.setText("Response: none");
		if (clientConfig.proxy != null) {
			request.setDestinationContext(new AddressEndpointContext(clientConfig.proxy.destination));
			if (clientConfig.proxy.scheme != null) {
				request.getOptions().setProxyScheme(clientConfig.proxy.scheme);
			}
		}
		String uri = uriBox.getSelectionModel().getSelectedItem();
		uri = uri.replace(" ", "%20");
		request.setURI(uri);
		request.addMessageObserver(new ResponsePrinter(request));
		if (request.isIntendedPayload()) {
			String text = requestArea.getText();
			if (!text.isEmpty()) {
				request.setPayload(text);
			} else if (clientConfig.payloadBytes != null) {
				request.setPayload(clientConfig.payloadBytes);
				if (clientConfig.contentType != null) {
					request.getOptions().setContentFormat(clientConfig.contentType.contentType);
				}
			}
		}
		execute(request);
	}

	private void execute(Request request) {
		try {
			Endpoint endpoint = getLocalEndpoint(request.getScheme());
			if (endpoint != null) {
				request.send(endpoint);
				LOG.info("Sent request: {}", request);
			}
		} catch (Exception ex) {
			StringBuilder tmp = new StringBuilder(request.toString());
			tmp.append('\n');
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			tmp.append(sw.toString());
			LOG.error(tmp.toString());
		}
	}

	private void showResponse(Response response) {
		String type = response.isNotification() ? "Notification" : "Response";
		LOG.info("Received {}: {}", type, response);
		int size = response.getPayloadSize();
		byte[] payload = response.getPayload();
		InetSocketAddress source = response.getSourceContext().getPeerAddress();
		OptionSet optionSet = response.getOptions();
		int format = optionSet.getContentFormat();
		String mediaType = MediaTypeRegistry.toString(format);
		if (mediaType.startsWith("image")) {
			// Display the image
			Image img = new Image(new ByteArrayInputStream(payload));
			mediaTypeView.setImage(img);
			// Display the image uri if given, otherwise just the type
			// and size
			String uriPath = optionSet.getUriPathString();
			if (uriPath != null && uriPath.length() > 0)
				responseArea.setText(uriPath);
			else
				responseArea.setText(String.format("%s;size=%d", mediaType, size));
		} else {
			// Display media type image icon and payload string
			Image image = blank;
			String ext = MediaTypeRegistry.toFileExtension(format);
			String path = "/org/eclipse/californium/tools/images/" + ext + ".png";
			InputStream imgIS = getClass().getResourceAsStream(path);
			if (imgIS != null) {
				image = new Image(imgIS);
			}
			mediaTypeView.setImage(image);
			responseArea.setText(response.getPayloadString());
		}
		String info = String.format("%s: %s %s/%s;size=%d,mid=%d,source=%s,mediaType=%s", type, response.getType(),
				response.getCode(), response.getCode().name(), size, response.getMID(),
				StringUtil.toDisplayString(source), mediaType);
		responseTitle.setText(info);
	}

	private class ResponsePrinter extends MessageObserverAdapter {
		private final AtomicBoolean reconnect = new AtomicBoolean();
		private final AtomicInteger retransmission = new AtomicInteger();
		private final Request request;
		private final String scheme;
		private final boolean dtls;

		public ResponsePrinter(Request request) {
			this.request = request;
			this.scheme = request.getScheme();
			this.dtls = CoAP.isSecureScheme(scheme) && !CoAP.isTcpScheme(scheme);
		}

		@Override
		public void onReadyToSend() {
			Platform.runLater(() -> {
				StringBuilder text = new StringBuilder("Request:");
				text.append(" token=").append(request.getTokenString());
				text.append(", mid=").append(request.getMID());
				text.append(", ").append(request.getBytes().length).append(" bytes.");
				requestTitle.setText(text.toString());
			});
			super.onReadyToSend();
		}

		@Override
		public void onConnecting() {
			Platform.runLater(() -> {
				LOG.info("connecting");
				mediaTypeView.setImage(blank);
				resetConnectionTitle();
				connectionArea.setText("Connecting ...");
			});
			super.onConnecting();
		}

		@Override
		public void onRetransmission() {
			final int retry = retransmission.incrementAndGet();
			if (dtls && !reconnect.get() && retry == 2) {
				EndpointContext destinationContext = request.getEffectiveDestinationContext();
				String mode = destinationContext.get(DtlsEndpointContext.KEY_HANDSHAKE_MODE);
				if (mode == null) {
					EndpointContext probeContext = MapBasedEndpointContext.addEntries(destinationContext,
							DtlsEndpointContext.KEY_HANDSHAKE_MODE, DtlsEndpointContext.HANDSHAKE_MODE_PROBE);
					request.setEffectiveDestinationContext(probeContext);
					reconnect.set(true);
				}
			}
			Platform.runLater(() -> {
				LOG.info("retransmission");
				mediaTypeView.setImage(blank);
				String text = scheme + ": retransmission " + retry;
				if (reconnect.get()) {
					text += " (reconnect)";
				}
				responseArea.setText(text);
				responseTitle.setText("Response:");
			});
			super.onRetransmission();
		}

		@Override
		public void onReject() {
			Platform.runLater(() -> {
				LOG.info("rejected");
				mediaTypeView.setImage(blank);
				responseArea.setText("Rejected by other peer.");
				String info = String.format("RST: mid=%d,source=%s", request.getMID(),
						StringUtil.toDisplayString(request.getDestinationContext().getPeerAddress()));
				responseTitle.setText(info);
			});
			super.onReject();
		}

		@Override
		public void onTimeout() {
			Platform.runLater(() -> {
				LOG.info("timeout");
				mediaTypeView.setImage(blank);
				responseArea.setText("Timeout.");
				responseTitle.setText("Response:");
			});
			super.onTimeout();
		}

		@Override
		public void onSendError(final Throwable error) {
			Platform.runLater(() -> {
				LOG.info("send error", error);
				mediaTypeView.setImage(blank);
				String text = error.getMessage();
				if (text == null) {
					text = error.getClass().getSimpleName();
				}
				responseArea.setText(text);
				responseTitle.setText("Response: Send Error");
			});
			super.onSendError(error);
		}

		@Override
		public void onContextEstablished(EndpointContext endpointContext) {
			Platform.runLater(() -> {
				StringBuilder title = new StringBuilder("Connection:");
				StringBuilder area = new StringBuilder();
				Endpoint endpoint = getLocalEndpoint(scheme);
				if (endpoint != null) {
					title.append(" from ").append(StringUtil.toString(endpoint.getAddress()));
					title.append(" - to ").append(StringUtil.toString(endpointContext.getPeerAddress()));
					area.append("PEER: ").append(endpointContext.getPeerIdentity()).append(StringUtil.lineSeparator());
					for (Map.Entry<String, String> entry : endpointContext.entries().entrySet()) {
						area.append(entry.getKey()).append(": ").append(entry.getValue())
								.append(StringUtil.lineSeparator());
					}
				}
				connectionTitle.setText(title.toString());
				connectionArea.setText(area.toString());
			});
			super.onReadyToSend();
		}

		@Override
		public void onResponse(final Response response) {
			Platform.runLater(() -> {
				showResponse(response);
			});
			super.onResponse(response);
		}
	}

	private class NotificationPrinter implements NotificationListener {

		@Override
		public void onNotification(final Request request, final Response response) {
			Platform.runLater(() -> {
				if (observe != null && observe.matchRequest(request)) {
					if (observe.onResponse(response)) {
						showResponse(response);
					}
				}
			});
		}
	}

}
