/*******************************************************************************
 * Copyright (c) 2014 Institute for Pervasive Computing, ETH Zurich and others.
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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.network.config.NetworkConfig;


/**
 * A CoAP Client to communicate with other CoAP resources.
 */
public class GUIClient extends JPanel {

	private static final long serialVersionUID = -8656652459991661071L;
	
	private static final String DEFAULT_URI = "coap://localhost:5683";
	private static final String TESTSERVER_URI = "coap://vs0.inf.ethz.ch:5683";
	private static final String COAP_PROTOCOL = "coap://";
	
	@SuppressWarnings("rawtypes")
	private JComboBox cboTarget;
	
	private JTextArea txaPayload;
	private JTextArea txaResponse;
	
	private JPanel pnlResponse;
	private TitledBorder responseBorder;
	
	private DefaultMutableTreeNode dmtRes;
	private DefaultTreeModel dtmRes;
	private JTree treRes;
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public GUIClient() {
		JButton btnGet = new JButton("GET");
		JButton btnPos = new JButton("POST");
		JButton btnPut = new JButton("PUT");
		JButton btnDel = new JButton("DELETE");
		JButton btnDisc = new JButton("Discovery");
		
		btnGet.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				performRequest(new Request(Code.GET));
			}
		});
		
		btnPos.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				performRequest(new Request(Code.POST));
			}
		});
		
		btnPut.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				performRequest(new Request(Code.PUT));
			}
		});
		
		btnDel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				performRequest(new Request(Code.DELETE));
			}
		});
		
		btnDisc.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				discover();
			}
		});
		
		cboTarget = new JComboBox();
		cboTarget.setEditable(true);
		cboTarget.setMinimumSize(cboTarget.getPreferredSize());
		cboTarget.addItem(DEFAULT_URI);
		cboTarget.addItem(TESTSERVER_URI);
		cboTarget.setSelectedIndex(0);
		
		txaPayload = new JTextArea("", 8, 50);
		txaResponse = new JTextArea("", 8, 50);
		txaResponse.setEditable(false);
		
		JPanel pnlDisc = new JPanel(new BorderLayout());
		pnlDisc.add(cboTarget, BorderLayout.CENTER);
		pnlDisc.add(btnDisc, BorderLayout.EAST);
		
		JPanel pnlTarget = new JPanel(new BorderLayout());
		pnlTarget.setBorder(new TitledBorder("Target"));
		pnlTarget.add(pnlDisc, BorderLayout.NORTH);
		pnlTarget.setMaximumSize(new Dimension(Integer.MAX_VALUE, pnlTarget.getPreferredSize().height));
		
		JPanel pnlButtons = new JPanel(new GridLayout(1, 4, 10, 10));
		pnlButtons.setBorder(new EmptyBorder(10,10,10,10));
		pnlButtons.add(btnGet);
		pnlButtons.add(btnPos);
		pnlButtons.add(btnPut);
		pnlButtons.add(btnDel);
		
		JPanel pnlRequest = new JPanel(new BorderLayout());
		pnlRequest.setBorder(new TitledBorder("Request"));
		pnlRequest.add(new JScrollPane(txaPayload), BorderLayout.CENTER);
		pnlRequest.add(pnlButtons, BorderLayout.SOUTH);
		
		pnlResponse = new JPanel(new BorderLayout());
		responseBorder = new TitledBorder("Response");
		pnlResponse.setBorder(responseBorder);
		pnlResponse.add(new JScrollPane(txaResponse));
		
		JPanel panelC = new JPanel();
		panelC.setLayout(new BoxLayout(panelC, BoxLayout.Y_AXIS));
		
		panelC.add(pnlTarget);
		panelC.add(pnlRequest);
		
		JSplitPane splReqRes = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		splReqRes.setContinuousLayout(true);
		splReqRes.setResizeWeight(.5f);
		splReqRes.setTopComponent(panelC);
		splReqRes.setBottomComponent(pnlResponse);

		dmtRes = new DefaultMutableTreeNode("Resources");
		dtmRes = new DefaultTreeModel(dmtRes);
		treRes = new JTree(dtmRes);

		JScrollPane scrRes = new JScrollPane(treRes);
		scrRes.setPreferredSize(new Dimension(200,scrRes.getPreferredSize().height));
		
		JPanel panelE = new JPanel(new BorderLayout());
		panelE.setBorder(new TitledBorder("Resources"));
		panelE.add(scrRes,BorderLayout.CENTER);
		
		setLayout(new BorderLayout());
		
		JSplitPane splCE = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splCE.setContinuousLayout(true);
		splCE.setResizeWeight(.5f);
		splCE.setLeftComponent(panelE);
		splCE.setRightComponent(splReqRes);
		add(splCE);
		
		treRes.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				TreePath tp= e.getNewLeadSelectionPath();
				if (tp!=null) {
					Object[] nodes = tp.getPath();
					StringBuffer sb = new StringBuffer(COAP_PROTOCOL+getHost());
					for (int i=1;i<nodes.length;i++) // nodes[0] is Resource and not necessary
						sb.append("/"+nodes[i].toString());
					cboTarget.setSelectedItem(sb.toString());
				}
			}
		});

		discover();
	}
	
	private void discover() {
		dmtRes.removeAllChildren();
		dtmRes.reload();
		
		Request request = new Request(Code.GET);
		request.setURI(COAP_PROTOCOL+getHost()+"/.well-known/core");
		request.addMessageObserver(new MessageObserverAdapter() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			public void onResponse(Response response) {
				String text = response.getPayloadString();
				Scanner scanner = new Scanner(text);
				Pattern pattern = Pattern.compile("<");
				scanner.useDelimiter(pattern);
				
				ArrayList<String> ress1 = new ArrayList<String>();
				ArrayList<String> ress2 = new ArrayList<String>();
				while(scanner.hasNext()) {
					String part = scanner.next();
					String res = part.split(">")[0];
					System.out.println(res);
					ress1.add(COAP_PROTOCOL+getHost()+res);
					ress2.add(res);
				}
				scanner.close();
				cboTarget.setModel(new DefaultComboBoxModel(ress1.toArray(new String[ress1.size()])));
				populateTree(ress2);
			}
		});
		execute(request);
	}
	
	private void populateTree(List<String> ress) {
		Node root = new Node("Resource");
		for (String res:ress) {
			String[] parts = res.split("/");
			Node cur = root;
			for (int i=1;i<parts.length;i++) {
				Node n = cur.get(parts[i]);
				if (n==null)
					cur.children.add(n = new Node(parts[i]));
				cur = n;
			}
		}
		dmtRes.removeAllChildren();
		addNodes(dmtRes,root);
		dtmRes.reload();
		for (int i = 0; i < treRes.getRowCount(); i++) {
			treRes.expandRow(i);
		}
	}
	
	private void addNodes(DefaultMutableTreeNode parent, Node node) {
		for (Node n:node.children) {
			DefaultMutableTreeNode dmt = new DefaultMutableTreeNode(n.name);
			parent.add(dmt);
			addNodes(dmt, n);
		}
	}
	
	private class Node {
		private String name;
		private ArrayList<Node> children = new ArrayList<Node>();
		private Node(String name) {
			this.name = name;
		}
		private Node get(String name) {
			for (Node c:children)
				if (name.equals(c.name))
					return c;
			return null;
		}
	}
	
	private void performRequest(Request request) {
		txaResponse.setText("no response yet");
		responseBorder.setTitle("Response: none");
		pnlResponse.repaint();
		request.addMessageObserver(new ResponsePrinter());
		request.setPayload(txaPayload.getText());
		request.setURI(cboTarget.getSelectedItem().toString().replace(" ", "%20"));
		execute(request);
	}
	
	private void execute(Request request) {
		try {
			request.send();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private class ResponsePrinter extends MessageObserverAdapter {
		@Override
		public void onResponse(Response response) {
			txaResponse.setText(response.getPayloadString());
			responseBorder.setTitle("Response: "+response.getCode());
			pnlResponse.repaint();
		}
	}
	
	public static void main(String[] args) {
		System.out.println("Starting GUI client");
		NetworkConfig.createStandardWithoutFile();
		setLookAndFeel();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				JFrame frame = new JFrame("CoAP Client");
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.add(new GUIClient());
				frame.pack();
				frame.setLocationRelativeTo(null);
				frame.setVisible(true);
			}
		});
	}
	
	private static void setLookAndFeel() {
		try {
	        UIManager.setLookAndFeel(
		        	UIManager.getCrossPlatformLookAndFeelClassName());
//		            UIManager.getSystemLookAndFeelClassName());
//		        	"com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
	}
	
	private String getHost() {
		String uri = (String) cboTarget.getSelectedItem(); 
		StringTokenizer st = new StringTokenizer(uri, "/");
		st.nextToken();
		String host = st.nextToken();
		return host;
	}
}
