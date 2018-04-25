package net.schwarzbaer.java.tools.yamahacontrol;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;

final class Ctrl {

	static final int RC_OK = 0;
	static final int RC_NO_RESPONSE = -1;
	static final int RC_CANT_PARSE_XML = -2;
	static final int RC_CANT_FIND_RC_ATTR = -3;
	static final int RC_CANT_PARSE_RC_VALUE = -4;
	
	static int lastRC = RC_OK;
	
	static Document sendCommand_controlled(String address, String command) {
		String xmlStr = sendCommand(address, command);
		if (xmlStr==null) { lastRC = RC_NO_RESPONSE; return null; }
		
		Document document = XML.parse(xmlStr);
		if (document==null) { lastRC = RC_CANT_PARSE_XML; return null; }
		
		String rcStr = XML.getNodeAttribute(document,new TagList("YAMAHA_AV"),"RC");
		if (rcStr==null) { lastRC = RC_CANT_FIND_RC_ATTR; return null; }
		
		try { lastRC = Integer.parseInt(rcStr); }
		catch (NumberFormatException e) { lastRC = RC_CANT_PARSE_RC_VALUE; return null; }
		
		return document;
	}
	
	static int sendPutCommand(String address, TagList tagList, String value) {
		
		String command = buildSimplePutCommand(tagList, value);
		sendCommand_controlled(address, command);
		return lastRC;
	}
	
	public static String sendGetCommand(String address, TagList tagList) {
		
		String command = buildSimpleGetCommand(tagList);
		Document document = sendCommand_controlled(address, command);
		if (lastRC!=RC_OK) return null;
		
		Vector<Node> nodes = XML.getNodes(document, tagList);
		if (nodes.isEmpty()) return null;
		
		return XML.getContentOfSingleChildTextNode(nodes.get(0));
	}

	static String buildSimplePutCommand(TagList tagList, String value) {
		// <YAMAHA_AV cmd="PUT"><System><Power_Control><Power>On</Power></Power_Control></System></YAMAHA_AV>
		return buildSimpleCommand("PUT", tagList, value);
	}
	static String buildSimpleGetCommand(TagList tagList) {
		// <YAMAHA_AV cmd="GET"><System><Power_Control><Power>GetParam</Power></Power_Control></System></YAMAHA_AV>
		return buildSimpleCommand("GET", tagList, "GetParam");
	}
	static String buildSimpleCommand(String cmd, TagList tagList, String value) {
		return "<YAMAHA_AV cmd=\""+cmd+"\">"+tagList.toXML(value)+"</YAMAHA_AV>";
	}
	static void testCommand(String address, String command) {
		testCommand(address, command, true);
	}
	static void testCommand(String address, String command, boolean verbose) {
		String response = sendCommand(address, command, verbose);
		System.out.println("Command : "+command);
		System.out.println("Response: "+response);
		if (response!=null) XML.showXMLformated(response);
		System.out.println();
	}

	static String sendCommand(String address, String command) {
		return sendCommand(address, command, false);
	}

	static String sendCommand(String address, String command, boolean verbose) {
		ByteBuffer byteBuf = StandardCharsets.UTF_8.encode(command);
		byte[] bytes = new byte[byteBuf.limit()];
		byteBuf.get(bytes);
		
		int port = 80; // 50100 bei BD-Playern
		String urlStr = "http://"+address+":"+port+"/YamahaRemoteControl/ctrl";
		
		return sendHTTPRequest(
				urlStr,
				connection -> {
					try { connection.setRequestMethod("POST"); }
					catch (ProtocolException e) { e.printStackTrace(); return false; }
					connection.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
					connection.setRequestProperty("Content-Length", ""+bytes.length);
					connection.setDoOutput(true);
					connection.setDoInput(true);
					return true;
				},
				connection -> {
					try { connection.getOutputStream().write(bytes); return true; }
					catch (IOException e) { e.printStackTrace(); return false; }
				},
				verbose);
	}

	static String getContentFromURL(String urlStr, boolean verbose) {
		return sendHTTPRequest(
				urlStr,
				connection->{ connection.setDoInput(true); return true; },
				null,
				verbose);
	}

	private static interface ConfigureConn {
		public boolean configure(HttpURLConnection connection);
	}

	private static interface WriteRequestContent {
		public boolean writeTo(HttpURLConnection connection);
	}

	private static String sendHTTPRequest(String urlStr, ConfigureConn configureConn, WriteRequestContent writeRequestContent, boolean verbose) {
		return sendHTTPRequest(urlStr, configureConn, writeRequestContent, verbose, true);
	}

	private static String sendHTTPRequest(String urlStr, ConfigureConn configureConn, WriteRequestContent writeRequestContent, boolean verbose, boolean verboseOnError) {
		
		if (verbose) System.out.println("URL: "+urlStr);
		URL url;
		try { url = new URL(urlStr); }
		catch (MalformedURLException e2) { e2.printStackTrace(); return null; }
		
		if (verbose) System.out.println("Open Connection ...");
		HttpURLConnection connection;
		try { connection = (HttpURLConnection)url.openConnection(); }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		if (configureConn!=null) {
			boolean successful = configureConn.configure(connection);
			if (!successful) return null;
		}
		
		try { connection.connect(); }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		if (writeRequestContent!=null) {
			boolean successful = writeRequestContent.writeTo(connection);
			if (!successful) { if (verboseOnError) showConnection(connection); connection.disconnect(); return null; }
		}
		
		Object content;
		if (connection.getContentLength()==0)
			content = null;
		else
			try { content = connection.getContent(); }
			catch (IOException e) { if (verboseOnError) showConnection(connection); e.printStackTrace(); connection.disconnect(); return null; }
		if (verbose) System.out.println("Content: "+content);
		
		String contentStr = null;
		if (content instanceof InputStream) {
			InputStream input = (InputStream)content;
			byte[] responseBytes = new byte[connection.getContentLength()];
			int n,pos=0;
			try { while ( (n=input.read(responseBytes, pos, responseBytes.length-pos))>=0 ) pos += n; }
			catch (IOException e) { e.printStackTrace(); if (verbose) System.out.println("abort reading response");}
			
			if (verbose) {
				String bytesReadStr = pos!=responseBytes.length?(" "+pos+" of "+responseBytes.length+" bytes "):"";
				if (pos<1000) {
					System.out.println("Content (bytes read): "+bytesReadStr+""+Arrays.toString(responseBytes));
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
		
		if (verboseOnError && contentStr==null) showConnection(connection); 
		
		connection.disconnect();
		
		return contentStr;
	}

	private static void showConnection(HttpURLConnection connection) {
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

}
