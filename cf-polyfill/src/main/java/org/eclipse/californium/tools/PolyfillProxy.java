/*******************************************************************************
 * Copyright (c) 2016 Institute for Pervasive Computing, ETH Zurich and others.
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
 *    Yassin N. Hassan - Polyfill implementation
 ******************************************************************************/
package org.eclipse.californium.tools;

import com.google.gson.Gson;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.ServerContainer;
import java.io.IOException;
import java.net.URL;

public class PolyfillProxy
{
	public static void main(String[] args)
	{
		Server server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setPort(8080);
		server.addConnector(connector);

		// Setup the basic application "context" for this application at "/"
		// This is also known as the handler tree (in jetty speak)
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(CoapRequestServlet.class, "/request");

		// add special pathspec of "/home/" content mapped to the homePath
		ServletHolder holderHome = new ServletHolder("static-home", DefaultServlet.class);
		String  baseStr  = "/webapp";  //... contains: helloWorld.html, login.html, etc. and folder: other/xxx.html
		URL baseUrl  = PolyfillProxy.class.getResource( baseStr );
		String  basePath = baseUrl.toExternalForm();

		holderHome.setInitParameter("resourceBase", basePath);
		holderHome.setInitParameter("dirAllowed", "false");
		holderHome.setInitParameter("pathInfoOnly", "true");
		context.addServlet(holderHome, "/*");

		try
		{
			// Initialize javax.websocket layer
			ServerContainer wscontainer = WebSocketServerContainerInitializer.configureContext(context);

			// Add WebSocket endpoint to javax.websocket layer
			wscontainer.addEndpoint(ObserveSocket.class);
			server.start();
			server.dump(System.err);
			server.join();
		}
		catch (Throwable t)
		{
			t.printStackTrace(System.err);
		}
	}
	public static class RequestDefinition {

		public String method;
		public String url;
		public String payload;
	}
	@SuppressWarnings("serial")
	public static class CoapRequestServlet extends HttpServlet {
		@Override
		protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			resp.setStatus(HttpServletResponse.SC_OK);
			addCORSHeaders(resp);
		}

		private void addCORSHeaders(HttpServletResponse resp) {
			resp.addHeader("Access-Control-Allow-Origin", "*");
			resp.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
			resp.addHeader("Access-Control-Allow-Headers", "Content-Type");
		}

		@Override
		protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			resp.setContentType("application/json");
			resp.setStatus(HttpServletResponse.SC_OK);
			addCORSHeaders(resp);
			Gson gson = new Gson();
			RequestDefinition requestDefintion = gson.fromJson(req.getReader().readLine(), RequestDefinition.class);

			CoapClient client = new CoapClient(requestDefintion.url);
			client.setTimeout(10000);
			CoapResponse response = null;
			if (requestDefintion.method.equals("GET")){
				response = client.get();
			} else if (requestDefintion.method.equals("POST")){
				response = client.post(requestDefintion.payload, MediaTypeRegistry.APPLICATION_JSON);
			} else if (requestDefintion.method.equals("PUT")){
				response = client.put(requestDefintion.payload, MediaTypeRegistry.APPLICATION_JSON);
			} else if (requestDefintion.method.equals("DELETE")){
				response = client.delete();
			}
			if(response != null) {
				resp.getWriter().println(gson.toJson(new ResponseDefinition(response.getCode().value, response.getResponseText())));
			} else {
				resp.getWriter().println(gson.toJson(new ErrorDefinition("timeout")));
			}
		}
	}
}