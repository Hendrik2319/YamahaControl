package net.schwarzbaer.java.tools.yamahacontrol;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class CommandList {

	private Document document;
	private JFrame mainWindow;

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Config.readConfig();
		
		//  http://rx-v475/YamahaRemoteControl/desc.xml
		
		CommandList commandList = new CommandList();
		commandList.createGUI();
//		String addr = "192.168.2.34";
//		String addr = "RX_V475";
//		commandList.readCommandList(addr,true);
	}

	private void createGUI() {
		
		JTree tree = new JTree();
		JScrollPane treeScrollPane = new JScrollPane(tree);
		treeScrollPane.setPreferredSize(new Dimension(600,800));
		
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.X_AXIS));
		northPanel.add(createButton("Get Data",e->readCommandList()));
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		contentPane.add(northPanel,BorderLayout.NORTH);
		contentPane.add(treeScrollPane,BorderLayout.CENTER);
		
		// TODO Auto-generated method stub
		mainWindow = new JFrame("YamahaControl - CommandList");
		mainWindow.setContentPane(contentPane);
		mainWindow.pack();
		mainWindow.setLocationRelativeTo(null);
		mainWindow.setVisible(true);
	}

	private JButton createButton(String title, ActionListener l) {
		JButton button = new JButton(title);
		button.addActionListener(l);
		return button;
	}

	private void readCommandList() {
		//Object selectedAddress = JOptionPane.showInputDialog(mainWindow, "message", "title", JOptionPane.QUESTION_MESSAGE, null, Config.knownIPs.toArray(), null);
		String selectedAddress = Config.selectAddress(mainWindow);
		if (selectedAddress!=null)
			readCommandList(selectedAddress.toString(), true);
	}

	private void readCommandList(String addr, boolean verbose) {
		String content = requestContent("http://"+addr+"/YamahaRemoteControl/desc.xml", verbose);
		
		DocumentBuilder docBuilder = null;
		try { docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder(); }
		catch (ParserConfigurationException e) { e.printStackTrace(); }
		
		document = null;
		if (docBuilder!=null)
			try { document = docBuilder.parse(new InputSource(new StringReader(content))); }
			catch (SAXException | IOException e) { e.printStackTrace(); }
		
		if (verbose) System.out.println("done");
	}

	private String requestContent(String urlStr, boolean verbose) {
		if (verbose) System.out.println("URL: "+urlStr);
		
		URL url;
		try { url = new URL(urlStr); }
		catch (MalformedURLException e2) { e2.printStackTrace(); return null; }
		
		if (verbose) System.out.println("Open Connection ...");
		HttpURLConnection connection;
		try { connection = (HttpURLConnection)url.openConnection(); }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		connection.setDoInput(true);
		
		try { connection.connect(); }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		if (verbose) {
			Map<String, List<String>> headerFields = connection.getHeaderFields();
			for (Entry<String, List<String>> entry:headerFields.entrySet()) {
				String key = entry.getKey();
				StringBuilder values = new StringBuilder();
				entry.getValue().stream().forEach(str->values.append(" \"").append(str).append("\""));
				System.out.printf("Header[%-20s]: %s%n", key, values.toString());
			}
			try { System.out.println("ResponseCode   : "+connection.getResponseCode   ()); } catch (IOException e) {}
			try { System.out.println("ResponseMessage: "+connection.getResponseMessage()); } catch (IOException e) {}
			System.out.println("ContentLength  : "+connection.getContentLength  ());
			System.out.println("ContentType    : "+connection.getContentType    ());
			System.out.println("ContentEncoding: "+connection.getContentEncoding());
		}
		
		Object content;
		try { content = connection.getContent(); }
		catch (IOException e) { e.printStackTrace(); connection.disconnect(); return null; }
		if (verbose) System.out.println("Content: "+content); 
		
		String contentStr = null;
		if (content instanceof InputStream) {
			InputStream input = (InputStream)content;
			byte[] responseBytes = new byte[connection.getContentLength()];
			int n,pos=0;
			try { while ( (n=input.read(responseBytes, pos, responseBytes.length-pos))>=0 ) pos += n; }
			catch (IOException e) { e.printStackTrace(); if (verbose) System.out.println("abort reading response");}
			
			if (verbose) {
				String bytesRead = pos!=responseBytes.length?(" "+pos+" of "+responseBytes.length+" bytes "):"";
				if (pos<1000) {
					System.out.println("Content (bytes read): "+bytesRead+""+Arrays.toString(responseBytes));
				}
			}
			
			contentStr = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(responseBytes, 0, pos)).toString();
			
			if (verbose) {
				if (contentStr.length()>1000) {
					System.out.println("Content (as String): "+contentStr.substring(0, 100)+" ...");
					System.out.println("                     ... "+contentStr.substring(contentStr.length()-100,contentStr.length()));
				} else
					System.out.println("Content (as String): "+contentStr);
			}
		}
		
		connection.disconnect();
		
		return contentStr;
	}

}
