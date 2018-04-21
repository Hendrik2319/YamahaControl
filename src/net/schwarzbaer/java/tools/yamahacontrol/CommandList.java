package net.schwarzbaer.java.tools.yamahacontrol;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class CommandList {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		YamahaControl yamahaControl = new YamahaControl();
		yamahaControl.readConfig();
		//yamahaControl.knownIPs;
		
		//  http://rx-v475/YamahaRemoteControl/desc.xml
		
		CommandList commandList = new CommandList();
		commandList.createGUI();
		String content = commandList.requestContent("http://rx-v475/YamahaRemoteControl/desc.xml", true);
		
		System.out.println(content.substring(0, 100)+" ...");
		System.out.println("... "+content.substring(content.length()-100,content.length()));
		
		DocumentBuilder docBuilder = null;
		try { docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder(); }
		catch (ParserConfigurationException e) { e.printStackTrace(); }
		
		Document document = null;
		if (docBuilder!=null)
			try { document = docBuilder.parse(new InputSource(new StringReader(content))); }
			catch (SAXException | IOException e) { e.printStackTrace(); }
		
		System.out.println("done");
	}

	private void createGUI() {
		// TODO Auto-generated method stub
		
	}

	public String requestContent(String urlStr, boolean verbose) {
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
			//if (verbose) System.out.println("Content (bytes read): "+(pos!=responseBytes.length?(" "+pos+" of "+responseBytes.length+" bytes "):"")+""+Arrays.toString(responseBytes)); 
			
			contentStr = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(responseBytes, 0, pos)).toString();
			//if (verbose) System.out.println("Content (as String): "+new String(responseBytes)); 
		}
		
		connection.disconnect();
		
		return contentStr;
	}

}
