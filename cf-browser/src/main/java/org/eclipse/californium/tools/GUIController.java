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
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.californium.cli.ClientInitializer;
import org.eclipse.californium.cli.ConnectorConfig.AuthenticationMode;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.ClientObserveRelation;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.Token;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.observe.NotificationListener;
import org.eclipse.californium.elements.AddressEndpointContext;
import org.eclipse.californium.elements.Definition;
import org.eclipse.californium.elements.DtlsEndpointContext;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.MapBasedEndpointContext;
import org.eclipse.californium.elements.MapBasedEndpointContext.Attributes;
import org.eclipse.californium.elements.util.DaemonThreadFactory;
import org.eclipse.californium.elements.util.StandardCharsets;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.tools.GUIClientFX.GuiClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;

import ch.qos.logback.classic.Level;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * The JavaFX controller for the gui.fxml template
 */
public class GUIController implements NotificationListener {

	private static final Logger LOG = LoggerFactory.getLogger(GUIController.class.getName());
	public static final String DEFAULT_URI = "coap://localhost:5683";
	private static final String SANDBOX_URI = "coap://californium.eclipseprojects.io";
	private static final String SANDBOX_SECURE_URI = "coaps://californium.eclipseprojects.io";

	private final List<String> URIS = new ArrayList<>();
	private GuiClientConfig clientConfig;

	private Stage stage;

	/** Combo boxes of coap URIs and resource URIs of discovered servers */
	@FXML
	private ComboBox<String> uriBox;
	@FXML
	private TextArea logArea;
	@FXML
	private Menu handshakeTypeMenu;
	@FXML
	private Menu messageTypeMenu;
	@FXML
	private Menu contentTypeMenu;
	@FXML
	private Menu acceptMenu;
	@FXML
	private CheckMenuItem prettyJson;
	@FXML
	private CheckMenuItem logEnabled;
	@FXML
	private Menu logLevelMenu;
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
	private TreeView<PathElement> resourceTree;
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
	@FXML
	private Button pingButton;

	@FXML
	private RadioMenuItem handshakeTypeMenuItemNo;

	private String coapHost;
	private int accept = MediaTypeRegistry.UNDEFINED;
	private int contentType = MediaTypeRegistry.TEXT_PLAIN;
	private boolean confirmed = true;

	private Image unknown;
	private Image blank;

	private Request current;

	private ResponsePrinter currentResponsePrinter;

	private ClientObserveRelation currentObserveRelation;

	private Attributes dtlsHandshakeMode;

	private Endpoint endpoint;

	private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("Timer#"));

	private Random random = new Random();

	private String requestText;

	private ConcurrentMap<InetSocketAddress, Date> connectionTime = new ConcurrentHashMap<>();

	public GUIController() {
		URIS.add(DEFAULT_URI);
		URIS.add(SANDBOX_URI);
		URIS.add(SANDBOX_SECURE_URI);
	}

	private boolean addURI(String uri) {
		if (!URIS.contains(uri)) {
			URIS.add(0, uri);
			return true;
		} else {
			return false;
		}
	}

	private void applyRequestContent() {
		if (requestText == null || requestText.equals(requestArea.getText())) {
			if (clientConfig.payload != null) {
				String text = clientConfig.payload.text;
				if (text == null) {
					text = new String(clientConfig.payload.payloadBytes, CoAP.UTF8_CHARSET);
				}
				if (clientConfig.payloadFormat) {
					text = String.format(text, random.nextInt(100), System.currentTimeMillis() / 1000);
				}
				requestText = text;
				requestArea.setText(text);
			}
		}
	}

	public void initialize(Stage stage, GuiClientConfig config) {
		this.stage = stage;
		this.clientConfig = config;
		boolean init = false;
		if (config.uris != null) {
			Collections.reverse(config.uris);
			for (String uri : config.uris) {
				if (addURI(uri)) {
					init = true;
				}
			}
		}
		if (addURI(config.uri)) {
			init = true;
		}
		if (init) {
			initializeUriBox();
		}
		Font monospaceFont = Font.font("monospace");
		requestArea.setFont(monospaceFont);
		responseArea.setFont(monospaceFont);
		logArea.setFont(monospaceFont);
		connectionArea.setFont(monospaceFont);
		applyRequestContent();
		if (config.contentType != null) {
			selectContentTypeMenu(contentTypeMenu, config.contentType.contentType);
			contentType = config.contentType.contentType;
			selectContentTypeMenu(acceptMenu, config.contentType.contentType);
			accept = config.contentType.contentType;
		}
		if (config.messageType != null) {
			confirmed = config.messageType.con;
			String name = confirmed ? "CON" : "NON";
			for (MenuItem item : messageTypeMenu.getItems()) {
				if (name.equals(item.getText())) {
					if (item instanceof RadioMenuItem) {
						((RadioMenuItem) item).setSelected(true);
						break;
					}
				}
			}
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

	private void selectContentTypeMenu(Menu menu, int contentType) {
		String name = MediaTypeRegistry.toString(contentType);
		for (MenuItem item : menu.getItems()) {
			if (name.equals(item.getText())) {
				if (item instanceof RadioMenuItem) {
					((RadioMenuItem) item).setSelected(true);
					break;
				}
			}
		}
	}

	private void initializeContentTypeMenu(Menu menu) {
		ObservableList<MenuItem> items = menu.getItems();
		if (items.size() == 1) {
			MenuItem example = items.get(0);
			EventHandler<ActionEvent> onAction = example.getOnAction();
			ToggleGroup group = null;
			if (example instanceof RadioMenuItem) {
				RadioMenuItem radio = (RadioMenuItem) example;
				group = radio.getToggleGroup();
				radio.setSelected(true);
			}
			List<Integer> allMediaTypes = new ArrayList<Integer>(MediaTypeRegistry.getAllMediaTypes());
			allMediaTypes.add(MediaTypeRegistry.UNDEFINED);
			allMediaTypes.sort(null);
			for (int type : allMediaTypes) {
				String name = MediaTypeRegistry.toString(type);
				if (example != null) {
					example.setText(name);
					example = null;
				} else {
					MenuItem item;
					if (group != null) {
						RadioMenuItem radio = new RadioMenuItem(name);
						radio.setToggleGroup(group);
						item = radio;
					} else {
						item = new MenuItem(name);
					}
					item.setOnAction(onAction);
					items.add(item);
				}
			}
		}
	}

	private void initializeUriBox() {
		ObservableList<String> list = uriBox.itemsProperty().get();
		list.clear();
		for (String uri : URIS) {
			list.add(uri);
		}
		uriBox.getSelectionModel().select(0);
	}

	private void updateUriBox(String uri) {
		ObservableList<String> list = uriBox.itemsProperty().get();
		if (!list.contains(uri)) {
			LOG.info("Add {}", uri);
			list.add(0, uri);
			uriBox.getSelectionModel().select(0);
		}
	}

	@FXML
	private void resetUris() {
		initializeUriBox();
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

		initializeRootLoggerLevel();
		initializeContentTypeMenu(contentTypeMenu);
		initializeContentTypeMenu(acceptMenu);
		resetConnectionTitle();
	}

	private Endpoint getLocalEndpoint(String scheme) {
		return getLocalEndpoint(scheme, false);
	}

	private Endpoint getLocalEndpoint(String scheme, boolean reset) {
		Endpoint endpoint = null;
		if (CoAP.isSupportedScheme(scheme)) {
			try {
				endpoint = EndpointManager.getEndpointManager().getDefaultEndpoint(scheme);
				if (reset) {
					endpoint.stop();
					endpoint.destroy();
					throw new IllegalStateException("reset endpoint!");
				}
			} catch (IllegalStateException e) {
				clientConfig.uri = scheme + CoAP.URI_SCHEME_SEPARATOR;
				if (clientConfig.authenticationModes.isEmpty()) {
					clientConfig.authenticationModes.add(AuthenticationMode.PSK);
				}
				endpoint = ClientInitializer.createEndpoint(clientConfig, null);
				EndpointManager.getEndpointManager().setDefaultEndpoint(endpoint);
			} catch (RuntimeException e) {
			}
			synchronized (this) {
				if (this.endpoint != endpoint) {
					if (this.endpoint != null) {
						this.endpoint.removeNotificationListener(this);
					}
					this.endpoint = endpoint;
					if (this.endpoint != null) {
						this.endpoint.addNotificationListener(this);
					}
				}
			}
		}
		return endpoint;
	}

	private void resetConnectionTitle() {
		StringBuilder title = new StringBuilder("Connection:");
		Endpoint endpoint = getLocalEndpoint(getScheme());
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
		pingButton.setDisable(disable && clientConfig.proxy == null);
	}

	private void setNormalButtonMode() {
		Platform.runLater(() -> {
			if (getObserverRelation() == null) {
				observeButton.setText("OBSERVE");
				setButtonsDisable(false);
			}
		});
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
		ClientObserveRelation observerRelation = getObserverRelation();
		if (observerRelation != null) {
			observerRelation.proactiveCancel();
		} else {
			Endpoint endpoint = getLocalEndpoint(getScheme());
			if (endpoint != null) {
				Request request = Request.newGet();
				request.getOptions().setObserve(0);
				observerRelation = new ClientObserveRelation(request, endpoint, timer);
				setCurrentRequest(request, observerRelation);
				observeButton.setText("CANCEL");
				setButtonsDisable(true);
				performRequest(request);
			}
		}
	}

	@FXML
	private void discoveryRequest() {
		coapHost = getHost();
		final Request request = Request.newGet();
		if (clientConfig.proxy != null) {
			request.setDestinationContext(new AddressEndpointContext(clientConfig.proxy.destination));
			if (clientConfig.proxy.scheme != null) {
				request.getOptions().setProxyScheme(clientConfig.proxy.scheme);
			}
		}
		request.addMessageObserver(new ResponsePrinter(request, CoAP.getSchemeFromUri(coapHost)) {

			public void onResponse(final Response response) {
				Platform.runLater(() -> {
					if (resetCurrentRequest(request)) {
						showEndpointContext(scheme, response.getSourceContext());
						showResponse(response, null);
					}
				});
			}
		});
		LOG.info("Begin discovery, host={}", coapHost);
		execute(request, coapHost + "/.well-known/core");
	}

	@FXML
	private void pingRequest() {
		final String host = getHost();
		final Request request = Request.newPing();
		request.setToken(Token.EMPTY);
		request.addMessageObserver(new ResponsePrinter(request, CoAP.getSchemeFromUri(host)) {

			@Override
			public void onReject() {
				Platform.runLater(() -> {
					if (resetCurrentRequest(request)) {
						LOG.info("ping rejected => success!");
						mediaTypeView.setImage(blank);
						String info = String.format("RST: mid=%d,source=%s", request.getMID(),
								StringUtil.toDisplayString(request.getDestinationContext().getPeerAddress()));
						responseTitle.setText(info);
						responseArea.setPromptText("pong.");
					}
				});
			}

			public void onResponse(final Response response) {
				Platform.runLater(() -> {
					if (resetCurrentRequest(request)) {
						showEndpointContext(scheme, response.getSourceContext());
						showResponse(response, Collections.emptyList());
						LOG.info("response for ping => failure!");
						String text = responseTitle.getText();
						responseTitle.setText("unexpected " + text);
					}
				});
			}
		});
		LOG.info("Begin ping, host={}", host);
		execute(request, host);
	}

	@FXML
	private void onSelectResource() {
		TreeItem<PathElement> item = (TreeItem<PathElement>) resourceTree.getSelectionModel().getSelectedItem();
		if (item == null) { // Handle case when no node in the tree is selected, just browsed into.
			return;
		}
		StringBuilder path = new StringBuilder(item.getValue().uriElement);
		while (item.getParent() != null) {
			item = item.getParent();
			path.insert(0, item.getValue().uriElement + "/"); // Add slash separator to hierarchical paths.
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
		Endpoint endpoint = getLocalEndpoint(getScheme(), true);
		if (endpoint != null) {
			connectionTime.clear();
			resetConnectionTitle();
			connectionArea.setText("");
		}
	}

	@FXML
	private void setHandshakeType(ActionEvent event) {
		MenuItem item = (MenuItem) event.getSource();
		if (item == handshakeTypeMenuItemNo) {
			dtlsHandshakeMode = null;
		} else {
			String id = item.getId();
			if (id.equalsIgnoreCase("handshakeTypeMenuItemResume")) {
				dtlsHandshakeMode = DtlsEndpointContext.ATTRIBUTE_HANDSHAKE_MODE_FORCE;
			} else if (id.equalsIgnoreCase("handshakeTypeMenuItemFull")) {
				dtlsHandshakeMode = DtlsEndpointContext.ATTRIBUE_HANDSHAKE_MODE_FORCE_FULL;
			}
		}
	}

	private void resetHandshakeType() {
		if (handshakeTypeMenuItemNo != null) {
			handshakeTypeMenuItemNo.setSelected(true);
			dtlsHandshakeMode = null;
		}
	}

	@FXML
	private void restartConnection() {
		Endpoint endpoint = getLocalEndpoint(getScheme());
		if (endpoint != null) {
			try {
				endpoint.stop();
				endpoint.start();
				connectionTime.clear();
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

	private void initializeRootLoggerLevel() {
		Logger logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		if (ch.qos.logback.classic.Logger.class.isInstance(logger)) {
			ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) logger;
			Level level = rootLogger.getEffectiveLevel();
			for (MenuItem logItem : logLevelMenu.getItems()) {
				Level itemLevel = Level.toLevel(logItem.getText());
				if (level.equals(itemLevel)) {
					if (logItem instanceof RadioMenuItem) {
						((RadioMenuItem) logItem).setSelected(true);
					}
					break;
				}
			}
		}
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
	private void setMessageType(ActionEvent event) {
		MenuItem item = (MenuItem) event.getSource();
		confirmed = item.getText().equals("CON");
	}

	@FXML
	private void setContentType(ActionEvent event) {
		MenuItem item = (MenuItem) event.getSource();
		String text = item.getText();
		contentType = MediaTypeRegistry.parse(text);
	}

	@FXML
	private void setAcceptType(ActionEvent event) {
		MenuItem item = (MenuItem) event.getSource();
		String text = item.getText();
		accept = MediaTypeRegistry.parse(text);
	}

	@FXML
	private void onExit() {
		Stage stage = this.stage;
		if (stage != null) {
			Platform.runLater(stage::close);
		}
	}

	@FXML
	private void credentialsDialog() {
		try {
			URL fxml = getClass().getResource("dialog.fxml");
			FXMLLoader loader = new FXMLLoader(fxml);
			loader.setCharset(StandardCharsets.UTF_8);
			Parent root = loader.load();
			GUIDialog dialog = loader.getController();

			Stage dialogStage = new Stage();
			dialogStage.initModality(Modality.WINDOW_MODAL);

			dialog.initialize(dialogStage, clientConfig);
			dialogStage.setTitle("CoAP Client Credentials");
			dialogStage.setScene(new Scene(root));
			dialogStage.show();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getScheme() {
		return CoAP.getSchemeFromUri(uriBox.getSelectionModel().getSelectedItem());
	}

	private String getHost() {
		String uri = uriBox.getSelectionModel().getSelectedItem();
		StringTokenizer st = new StringTokenizer(uri, "/");
		String protocol = st.nextToken();
		String host = st.nextToken();
		return protocol + "//" + host;
	}

	private String getUri() {
		return uriBox.getSelectionModel().getSelectedItem().replace(" ", "%20");
	}

	private void populateTree(Set<WebLink> links, boolean clear) {
		boolean freshTree = false;
		TreeItem<PathElement> rootItem = resourceTree.getRoot();
		String currentHost = getHost();
		if (clear || rootItem == null) {
			rootItem = new TreeItem<>(new PathElement("/"));
			freshTree = true;
		}
		if (freshTree || coapHost == null) {
			coapHost = currentHost;
		} else if (!coapHost.equals(currentHost)) {
			return;
		}
		amendTree(rootItem, links);
		if (freshTree) {
			rootItem.setExpanded(true);
			resourceTree.setRoot(rootItem);
		}
	}

	private void removeChildren(List<String> path) {
		String currentHost = getHost();
		if (coapHost != null && coapHost.equals(currentHost)) {
			TreeItem<PathElement> rootItem = resourceTree.getRoot();
			TreeItem<PathElement> cur = rootItem;
			for (int i = 0; i < path.size(); i++) {
				String part = path.get(i);
				ObservableList<TreeItem<PathElement>> children = cur.getChildren();
				FilteredList<TreeItem<PathElement>> filteredChildren = children
						.filtered(treeItem -> part.equals(treeItem.getValue().displayElement));
				if (filteredChildren.size() == 0) {
					cur = null;
					break;
				} else {
					cur = filteredChildren.get(0);
				}
			}
			if (cur != null) {
				ObservableList<TreeItem<PathElement>> children = cur.getChildren();
				children.clear();
			}
		}
	}

	private void amendTree(TreeItem<PathElement> rootItem, Set<WebLink> links) {
		for (WebLink link : links) {
			String[] parts = link.getURI().split("/");
			TreeItem<PathElement> cur = rootItem;
			for (int i = 1; i < parts.length; i++) {
				String part = parts[i];
				ObservableList<TreeItem<PathElement>> children = cur.getChildren();
				FilteredList<TreeItem<PathElement>> filteredChildren = children
						.filtered(treeItem -> part.equals(treeItem.getValue().uriElement));
				if (filteredChildren.size() == 0) {
					cur = new TreeItem<>(new PathElement(part));
					children.add(cur);
				} else {
					cur = filteredChildren.get(0);
				}
			}
		}
	}

	/**
	 * Perform the given request by adding in the resource uri from the uri combo
	 * box selection, payload from the request text area, and message observer to a
	 * ResponsePrinter.
	 * 
	 * @param request - the coap request type
	 */
	private void performRequest(final Request request) {
		try {
			final String uri = getUri();
			if (clientConfig.proxy != null) {
				request.setDestinationContext(new AddressEndpointContext(clientConfig.proxy.destination));
				if (clientConfig.proxy.scheme != null) {
					request.getOptions().setProxyScheme(clientConfig.proxy.scheme);
				}
			}
			request.addMessageObserver(new ResponsePrinter(request, CoAP.getSchemeFromUri(uri)));
			if (request.isIntendedPayload()) {
				applyRequestContent();
				String text = requestArea.getText();
				if (!text.isEmpty()) {
					request.setPayload(text);
				}
				if (contentType != MediaTypeRegistry.UNDEFINED) {
					request.getOptions().setContentFormat(contentType);
				}
			}
			if (accept != MediaTypeRegistry.UNDEFINED) {
				request.getOptions().setAccept(accept);
			}
			request.setConfirmable(confirmed);
			execute(request, uri);
		} catch (Exception ex) {
			logException(request, ex);
		}
	}

	private void execute(final Request request, final String uri) {
		timer.execute(() -> {
			try {
				// resolve URI outside the UI thread
				request.setURI(uri);
				Platform.runLater(() -> {
					// finally send request (again) inside the UI thread
					try {
						String scheme = request.getScheme();
						Endpoint endpoint = getLocalEndpoint(scheme);
						if (endpoint != null) {
							setCurrentRequest(request, null);
							responseTitle.setText("Response: none");
							responseArea.setText("");
							responseArea.setPromptText("No response yet ...");
							if (dtlsHandshakeMode != null && CoAP.COAP_SECURE_URI_SCHEME.equals(scheme)) {
								EndpointContext context = request.getDestinationContext();
								context = MapBasedEndpointContext.addEntries(context, dtlsHandshakeMode);
								request.setDestinationContext(context);
								resetHandshakeType();
							}
							request.send(endpoint);
							LOG.info("Sent request: {}", request);
						}
					} catch (Exception ex) {
						logException(request, ex);
					}
				});
			} catch (Exception ex) {
				logException(request, ex);
			}
		});
	}

	private void logException(Request request, Exception ex) {
		LOG.error(logMessage(request, ex));
	}

	private void showResponse(Response response, List<String> path) {

		String type = response.isNotification() ? "Notification" : "Response";
		LOG.info("Received {}: {}", type, response);
		int size = response.getPayloadSize();
		byte[] payload = response.getPayload();
		OptionSet optionSet = response.getOptions();
		int contentFormat = optionSet.getContentFormat();
		String mediaType = MediaTypeRegistry.toString(contentFormat);
		boolean mediaTypeShown = false;
		if (mediaType.startsWith("image")) {
			// Display the image
			String meta = String.format("%s;size=%d", mediaType, size);
			try {
				Image img = new Image(new ByteArrayInputStream(payload));
				mediaTypeView.setImage(img);
				responseArea.setText(meta);
			} catch (Throwable t) {
				LOG.error("Image response:", t);
				showContentType(contentFormat);
				String text = getErrorMessage("Image-error: ", t.getMessage(), payload, meta);
				responseArea.setText(text);
			}
		} else {
			// Display media type image icon and payload string
			mediaTypeShown = showContentType(contentFormat);
			String text = "";
			boolean pretty = false;
			if (contentFormat == MediaTypeRegistry.APPLICATION_OCTET_STREAM) {
				text = StringUtil.byteArray2HexString(payload, StringUtil.NO_SEPARATOR, 256);
			} else if (contentFormat == MediaTypeRegistry.APPLICATION_CBOR) {
				try {
					CBOREncodeOptions options = new CBOREncodeOptions("keepkeyorder=true");
					text = CBORObject.DecodeFromBytes(payload, options).toString();
					pretty = prettyJson.isSelected();
				} catch (Throwable t) {
					LOG.error("CBOR response:", t);
					text = getErrorMessage("CBOR-error: ", t.getMessage(), payload, null);
				}
			} else {
				text = response.getPayloadString();
				if (contentFormat == MediaTypeRegistry.APPLICATION_LINK_FORMAT) {
					try {
						Set<WebLink> webLinks = LinkFormat.parse(text);
						if (path != null) {
							removeChildren(path);
						}
						populateTree(webLinks, path == null);
						StringBuilder links = new StringBuilder();
						for (WebLink link : webLinks) {
							links.append(link).append(StringUtil.lineSeparator());
						}
						text = links.toString();
					} catch (Throwable t) {
						LOG.error("Link response:", t);
						text = getErrorMessage("Link-error: ", t.getMessage(), payload, null);
					}
				} else if (contentFormat == MediaTypeRegistry.APPLICATION_JSON) {
					pretty = prettyJson.isSelected();
				}
			}
			if (pretty) {
				try {
					GsonBuilder builder = new GsonBuilder();
					builder.setPrettyPrinting();
					Gson gson = builder.create();
					JsonElement jsonElement = JsonParser.parseString(text);
					text = gson.toJson(jsonElement);
				} catch (Throwable t) {
					LOG.error("Pretty JSON printing:", t);
				}
			}
			responseArea.setText(text);
		}
		String info = String.format("%s: %s %s/%s, token=%s, mid=%d", type, response.getType(), response.getCode(),
				response.getCode().name(), response.getTokenString(), response.getMID());
		Long rtt = response.getApplicationRttNanos();
		if (rtt != null) {
			info += String.format(", rtt=%d[ms]", TimeUnit.NANOSECONDS.toMillis(rtt));
		}
		if (!mediaTypeShown) {
			info += ", mediaType=" + mediaType;
		}
		if (response.getBytes() != null) {
			info += ", " + response.getBytes().length + " bytes.";
		}
		responseTitle.setText(info);
	}

	private boolean showContentType(int contentFormat) {
		Image image = blank;
		String fileExtension = MediaTypeRegistry.toFileExtension(contentFormat);
		String path = "/org/eclipse/californium/tools/images/" + fileExtension + ".png";
		InputStream imgIS = getClass().getResourceAsStream(path);
		if (imgIS != null) {
			image = new Image(imgIS);
		}
		mediaTypeView.setImage(image);
		return image != blank;
	}

	private String getErrorMessage(String header, String error, byte[] payload, String extraInformation) {
		StringBuilder builder = new StringBuilder();
		builder.append(header).append(error).append(":").append(StringUtil.lineSeparator());
		if (extraInformation != null && !extraInformation.isEmpty()) {
			builder.append(extraInformation).append(StringUtil.lineSeparator());
		}
		builder.append(StringUtil.toDisplayString(payload, 256));
		return builder.toString();
	}

	@Override
	public void onNotification(Request request, Response response) {
		LOG.info("Received notification {}", response);
		ClientObserveRelation observerRelation;
		ResponsePrinter printer;
		synchronized (this) {
			observerRelation = currentObserveRelation;
			printer = currentResponsePrinter;
		}
		if (printer != null && observerRelation != null && observerRelation.matchRequest(request)) {
			printer.onResponse(response);
		} else {
			LOG.info("notification not matching current observe");
		}
	}

	private ClientObserveRelation getObserverRelation() {
		synchronized (this) {
			return currentObserveRelation;
		}
	}

	private void setCurrentRequest(Request request, ClientObserveRelation observe) {
		Request previous = null;
		ClientObserveRelation previousObserve = null;
		synchronized (this) {
			if (!isCurrentRequest(request)) {
				previous = current;
				current = request;
				if (currentObserveRelation != observe) {
					previousObserve = currentObserveRelation;
					currentObserveRelation = observe;
				}
			}
			currentResponsePrinter = request.getMessageObserver(ResponsePrinter.class);
		}
		if (previousObserve != null) {
			previousObserve.proactiveCancel();
			if (observe == null) {
				setNormalButtonMode();
			}
		}
		if (previous != null) {
			previous.cancel();
		}
	}

	private boolean resetCurrentRequest(Request request) {
		boolean reset;
		boolean ui = false;
		synchronized (this) {
			reset = isCurrentRequest(request);
			if (reset) {
				current = null;
				currentResponsePrinter = null;
				if (currentObserveRelation != null) {
					currentObserveRelation = null;
					ui = true;
				}
			}
		}
		if (ui) {
			setNormalButtonMode();
		}
		return reset;
	}

	private boolean isCurrentRequest(Request request) {
		synchronized (this) {
			return current == request;
		}
	}

	private void showEndpointContext(String scheme, EndpointContext context) {
		InetSocketAddress address = context.getPeerAddress();
		StringBuilder title = new StringBuilder("Connection:");
		StringBuilder area = new StringBuilder();
		Endpoint endpoint = getLocalEndpoint(scheme);
		if (endpoint != null) {
			title.append(" from ").append(StringUtil.toString(endpoint.getAddress()));
			title.append(" - to ").append(address);
		}
		connectionTime.putIfAbsent(address, new Date());
		Date time = connectionTime.get(address);
		area.append(FORMAT.format(time)).append(StringUtil.lineSeparator());
		if (context.getPeerIdentity() != null) {
			area.append("PEER: ").append(context.getPeerIdentity()).append(StringUtil.lineSeparator());
		}
		for (Map.Entry<Definition<?>, Object> entry : context.entries().entrySet()) {
			area.append(entry.getKey().getKey()).append(": ").append(entry.getValue())
					.append(StringUtil.lineSeparator());
		}
		connectionTitle.setText(title.toString());
		connectionArea.setText(area.toString());
	}

	private static final SimpleDateFormat FORMAT = new SimpleDateFormat("MMM d. HH:mm:ss [SSS]");

	private class ResponsePrinter extends MessageObserverAdapter {
		private final AtomicBoolean connect = new AtomicBoolean();
		private final AtomicBoolean reconnect = new AtomicBoolean();
		private final AtomicInteger retransmission = new AtomicInteger();
		protected final Request request;
		protected final String scheme;
		protected final boolean dtls;

		public ResponsePrinter(Request request, String scheme) {
			this.request = request;
			this.scheme = scheme;
			this.dtls = CoAP.isSecureScheme(scheme) && !CoAP.isTcpScheme(scheme);
		}

		@Override
		public void onReadyToSend() {
			Platform.runLater(() -> {
				if (isCurrentRequest(request)) {
					StringBuilder text = new StringBuilder("Request:");
					text.append(" ").append(request.getType());
					text.append(", token=").append(request.getTokenString());
					text.append(", mid=").append(request.getMID());
					if (request.getBytes() != null) {
						text.append(", ").append(request.getBytes().length).append(" bytes.");
					}
					requestTitle.setText(text.toString());
				}
			});
			super.onReadyToSend();
		}

		@Override
		public void onConnecting() {
			connect.set(true);
			Platform.runLater(() -> {
				if (isCurrentRequest(request)) {
					LOG.info("connecting");
					mediaTypeView.setImage(blank);
					resetConnectionTitle();
					connectionArea.setText("Connecting ...");
				}
			});
			super.onConnecting();
		}

		@Override
		public void onRetransmission() {
			final int retry = retransmission.incrementAndGet();
			if (dtls && !connect.get() && !reconnect.get() && retry == 2) {
				if (isCurrentRequest(request)) {
					EndpointContext destinationContext = request.getEffectiveDestinationContext();
					String mode = destinationContext.getString(DtlsEndpointContext.KEY_HANDSHAKE_MODE);
					if (mode == null) {
						EndpointContext probeContext = MapBasedEndpointContext.addEntries(destinationContext,
								DtlsEndpointContext.ATTRIBUTE_HANDSHAKE_MODE_PROBE);
						request.setEffectiveDestinationContext(probeContext);
						reconnect.set(true);
					}
				}
			}
			Platform.runLater(() -> {
				if (isCurrentRequest(request)) {
					LOG.info("retransmission");
					mediaTypeView.setImage(blank);
					String text = scheme + ": retransmission " + retry;
					if (reconnect.get()) {
						text += " (reconnect)";
					}
					responseArea.setText(text);
					responseTitle.setText("Response:");
				}
			});
			super.onRetransmission();
		}

		@Override
		public void onReject() {
			Platform.runLater(() -> {
				if (resetCurrentRequest(request)) {
					LOG.info("rejected");
					mediaTypeView.setImage(blank);
					responseArea.setText("Rejected by other peer.");
					String info = String.format("RST: mid=%d,source=%s", request.getMID(),
							StringUtil.toDisplayString(request.getDestinationContext().getPeerAddress()));
					responseTitle.setText(info);
				}
			});
			super.onReject();
		}

		@Override
		public void onTimeout() {
			Platform.runLater(() -> {
				if (resetCurrentRequest(request)) {
					LOG.info("timeout");
					mediaTypeView.setImage(blank);
					responseArea.setText("Timeout.");
					responseTitle.setText("Response:");
				}
			});
			super.onTimeout();
		}

		@Override
		public void onCancel() {
			Platform.runLater(() -> {
				if (resetCurrentRequest(request)) {
					LOG.info("cancel request");
					responseArea.setText("");
					responseTitle.setText("Response: Canceled");
					if (connect.get()) {
						connectionArea.setText("");
					}
				}
			});
			super.onCancel();
		}

		@Override
		public void onSendError(final Throwable error) {
			Platform.runLater(() -> {
				if (resetCurrentRequest(request)) {
					LOG.info("send error", error);
					mediaTypeView.setImage(blank);
					String text = error.getMessage();
					if (text == null) {
						text = error.getClass().getSimpleName();
					}
					responseArea.setText(text);
					responseTitle.setText("Response: Send Error");
					if (connect.get()) {
						connectionArea.setText("");
					}
				}
			});
			super.onSendError(error);
		}

		@Override
		public void onContextEstablished(final EndpointContext endpointContext) {
			if (connect.get()) {
				connectionTime.put(endpointContext.getPeerAddress(), new Date());
				Platform.runLater(() -> {
					if (isCurrentRequest(request)) {
						showEndpointContext(scheme, endpointContext);
					}
				});
			}
			super.onContextEstablished(endpointContext);
		}

		@Override
		public void onResponse(final Response response) {
			Platform.runLater(() -> {
				ClientObserveRelation observerRelation = getObserverRelation();
				if (observerRelation != null && observerRelation.matchRequest(request)) {
					if (!observerRelation.onResponse(response)) {
						LOG.info("CoAP-server drops out of order notification!");
						return;
					}
					if (!response.isNotification()) {
						LOG.info("CoAP-server stopped observe!");
					}
				}
				if (response.isNotification()) {
					if (!isCurrentRequest(request)) {
						LOG.info("Drop unexpected notification!");
						return;
					}
				} else if (!resetCurrentRequest(request)) {
					LOG.info("Drop unexpected response!");
					return;
				}
				showEndpointContext(scheme, response.getSourceContext());
				showResponse(response, request.getOptions().getUriPath());
				updateUriBox(request.getURI());
			});
			super.onResponse(response);
		}
	}

	public static String logMessage(Request request, Exception ex) {
		StringBuilder tmp = new StringBuilder();
		if (request != null) {
			tmp.append(request).append("\n");
		}
		tmp.append("\t").append(ex);
		StackTraceElement[] stackTrace = ex.getStackTrace();
		for (StackTraceElement element : stackTrace) {
			if (element.getClassName().startsWith("sun.reflect.")) {
				break;
			}
			tmp.append("\n\t\t").append(element);
		}
		return tmp.toString();
	}

	private static class PathElement {
		private String displayElement;
		private String uriElement;

		private PathElement(String uriElement) {
			this.uriElement = uriElement;
			String display = uriElement;
			try {
				display = URLDecoder.decode(uriElement, CoAP.UTF8_CHARSET.name());
			} catch (UnsupportedEncodingException e) {
				// UTF-8 must be supported, otherwise many functions will fail
			}
			this.displayElement = display;
		}

		@Override
		public String toString() {
			return displayElement;
		}
	}
}
