package net.schwarzbaer.java.tools.yamahacontrol;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import net.schwarzbaer.java.tools.yamahacontrol.XML.TagList;
import net.schwarzbaer.java.tools.yamahacontrol.YamahaControl.Log;

final class Ctrl {
	
	static class ProtocolEntry {
		String name;
		final String xml;
		ProtocolEntry(String xml) {
			this.name = "";
			this.xml = xml;
		}
		@Override public int hashCode() { return xml.hashCode(); }
		@Override public boolean equals(Object obj) {
			if (!(obj instanceof ProtocolEntry)) return false;
			return xml.equals(((ProtocolEntry)obj).xml);
		}
		
	}
	static class Command extends ProtocolEntry {
		HashSet<Response> responses;
		Command(String xml) {
			super(xml);
			responses = new HashSet<>();
		}
		public Response addResponse(String responseStr) {
			Response response = new Response(responseStr);
			responses.add(response);
			return response;
		}
	}
	static class Response extends ProtocolEntry {
		Response(String xml) {
			super(xml);
		}
	}
	
	private static final String COMMPROTOCOL_FILENAME = "YamahaControl.CommProtocol.ini";
	static final HashMap<String,Command> commprotocol = new HashMap<>();
	
	static void addToCommProtocol(String commandStr, String responseStr) {
		Command command = commprotocol.get(commandStr);
		if (command==null) commprotocol.put(commandStr,command = new Command(commandStr));
		command.addResponse(responseStr);
	}

	static Vector<Command> getSortedCommProtocol() {
		Vector<Command> list = new Vector<>(commprotocol.values());
		list.sort(Comparator.nullsLast(Comparator.comparing(c->c.xml)));
		return list;
	}

	static void writeCommProtocolToFile() {
		Vector<Command> list = getSortedCommProtocol();
		try (PrintWriter out = new PrintWriter( new OutputStreamWriter( new FileOutputStream(COMMPROTOCOL_FILENAME), StandardCharsets.UTF_8) )) {
			for (Command command:list) {
				out.printf("command=%s%n", command.xml);
				if (!command.name.isEmpty())
					out.printf("name=%s%n", command.name);
				for (Response response:command.responses) {
					out.printf("response=%s%n", response.xml);
					if (!response.name.isEmpty())
						out.printf("name=%s%n", response.name);
				}
			}
		}
		catch (FileNotFoundException e) {}
	}

	static void readCommProtocolFromFile() {
		commprotocol.clear();
		try (BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream(COMMPROTOCOL_FILENAME), StandardCharsets.UTF_8) )) {
			String line;
			Command command = null;
			ProtocolEntry lastEntry = null;
			while ( (line=in.readLine())!=null ) {
				if (line.startsWith("command=")) {
					command = new Command(line.substring("command=".length()));
					commprotocol.put(command.xml,command);
					lastEntry = command;
				}
				if (line.startsWith("response=") && command!=null) {
					lastEntry = command.addResponse(line.substring("response=".length()));
				}
				if (line.startsWith("name=") && lastEntry!=null) {
					lastEntry.name = line.substring("name=".length());
				}
			}
		}
		catch (FileNotFoundException e) {}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static final int RC_DEVICE_IN_STANDBY = 4;
	static final int RC_OK = 0;
	static final int RC_NO_RESPONSE = -1;
	static final int RC_CANT_PARSE_XML = -2;
	static final int RC_CANT_FIND_RC_ATTR = -3;
	static final int RC_CANT_PARSE_RC_VALUE = -4;
	static final int RC_CONNECT_TIMEOUT = -5;
	static final int RC_CONNECT_REFUSED = -6;
	
	public static String getRCcode(int rc) {
		switch (rc) {
		case RC_DEVICE_IN_STANDBY   : return "DEVICE IN STANDBY";
		case RC_OK                  : return "OK";
		case RC_NO_RESPONSE         : return "NO RESPONSE";
		case RC_CANT_PARSE_XML      : return "CANT PARSE XML";
		case RC_CANT_FIND_RC_ATTR   : return "CANT FIND RC ATTR";
		case RC_CANT_PARSE_RC_VALUE : return "CANT PARSE RC VALUE";
		case RC_CONNECT_TIMEOUT     : return "CONNECT TIMEOUT";
		case RC_CONNECT_REFUSED     : return "CONNECTION REFUSED";
		}
		return "Unknown "+rc;
	}

	static int lastRC = RC_OK;
	static boolean showCommands = false;
	
	static Document sendCommand_controlled(String address, String command) {
		lastRC = RC_OK;
				
		HttpResponse response = http.sendCommand(address, command);
		if (response==null) { if (lastRC==RC_OK) lastRC = RC_NO_RESPONSE; return null; }
		
		Document document = XML.parse(response.string);
		if (document==null) { lastRC = RC_CANT_PARSE_XML; return null; }
		
		String rcStr = XML.getNodeAttribute(document,new TagList("YAMAHA_AV"),"RC");
		if (rcStr==null) { lastRC = RC_CANT_FIND_RC_ATTR; return null; }
		
		try { lastRC = Integer.parseInt(rcStr); }
		catch (NumberFormatException e) { lastRC = RC_CANT_PARSE_RC_VALUE; return null; }
		
		return document;
	}
	
	static int sendPutCommand(String address, Device.KnownCommand knownCommand, String value) {
		
		if (showCommands) Log.info(Ctrl.class, "PUT: %s( %s )", knownCommand.toFullString(), value);
		String command = buildPutCommand(knownCommand.getTagList(), value);
		sendCommand_controlled(address, command);
		return lastRC;
	}
	 
	public static <T extends Device.Value> T sendGetCommand(String address, Device.KnownCommand knownCommand, T[] values) {
		String str = sendGetCommand_String(address, knownCommand);
		if (str==null) return null;
		for (T v:values)
			if (str.equals(v.getLabel()))
				return v;
		return null;
	}
	 
	public static String sendGetCommand_String(String address, Device.KnownCommand knownCommand) {
		Node node = sendGetCommand_Node(address,knownCommand);
		if (node==null) return null;
		return XML.getSubValue(node);
	}
	
	public static Node sendGetCommand_Node(String address, Device.KnownCommand knownCommand) {
		
		if (showCommands) Log.info(Ctrl.class, "GET: %s", knownCommand.toFullString());
		String command = buildGetCommand(knownCommand.getTagList());
		Document document = sendCommand_controlled(address, command);
		if (lastRC!=RC_OK) return null;
		if (document==null) return null;
		
//		StringBuilder sb = new StringBuilder();
//		XML.showXMLformated(sb,"",document);
		
		TagList tagList = knownCommand.getTagList().addBefore("YAMAHA_AV");
		Vector<Node> nodes = XML.getNodes(document, tagList);
		if (nodes.isEmpty()) { Log.error(XML.class, "Can't find subnode in response: TagList=%s", tagList); return null; }
		if (nodes.size()>1) Log.warning(XML.class, "Found more than one subnode in response: TagList=%s", tagList);
		
		return nodes.get(0);
	}

	static String buildPutCommand(TagList tagList, String value) {
		// <YAMAHA_AV cmd="PUT"><System><Power_Control><Power>On</Power></Power_Control></System></YAMAHA_AV>
		return buildCommand("PUT", tagList, value);
	}
	static String buildGetCommand(TagList tagList) {
		// <YAMAHA_AV cmd="GET"><System><Power_Control><Power>GetParam</Power></Power_Control></System></YAMAHA_AV>
		return buildCommand("GET", tagList, "GetParam");
	}
	static String buildCommand(String cmd, TagList tagList, String value) {
		return "<YAMAHA_AV cmd=\""+cmd+"\">"+tagList.toXML(value)+"</YAMAHA_AV>";
	}
	static HttpResponse testCommand(String address, String command) {
		return testCommand(address, command, true);
	}
	static HttpResponse testCommand(String address, String command, boolean verbose) {
		HttpResponse response = http.sendCommand(address, command, verbose);
		System.out.println("Command : "+command);
		System.out.println("Response: "+response);
		if (response!=null) XML.showXMLformated(response.string);
		System.out.println();
		return response;
	}
	
	static HttpInterface http = new HttpInterfaceImplementation();

	public static abstract class HttpInterface {
		public HttpResponse sendCommand(String address, String command) {
			return sendCommand(address, command, false);
		}
		public abstract HttpResponse sendCommand(String address, String command, boolean verbose);
		public abstract String getContentFromURL(String urlStr, boolean verbose);
	}

	public static class HttpResponse {
		byte[] bytes;
		String string;
		public HttpResponse() {
			this(null,null);
		}
		public HttpResponse(byte[] bytes, String string) {
			this.bytes = bytes;
			this.string = string;
		}
		@Override public String toString() { return string; }
	}
	private static final class HttpInterfaceImplementation extends HttpInterface {

		@Override
		public HttpResponse sendCommand(String address, String command, boolean verbose) {
			ByteBuffer byteBuf = StandardCharsets.UTF_8.encode(command);
			byte[] bytes = new byte[byteBuf.limit()];
			byteBuf.get(bytes);
			
			int port = 80; // 50100 bei BD-Playern
			String urlStr = "http://"+address+":"+port+"/YamahaRemoteControl/ctrl";
			
			HttpResponse response = sendHTTPRequest(
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
			
			if (response!=null)
				addToCommProtocol(command,response.string);

			return response;
		}

		@Override
		public String getContentFromURL(String urlStr, boolean verbose) {
			HttpResponse response = sendHTTPRequest(
					urlStr,
					connection->{ connection.setDoInput(true); return true; },
					null,
					verbose);
			if (response==null) return null;
			return response.string;
		}

		private static interface ConfigureConn {
			public boolean configure(HttpURLConnection connection);
		}

		private static interface WriteRequestContent {
			public boolean writeTo(HttpURLConnection connection);
		}
		
		private HttpResponse sendHTTPRequest(String urlStr, ConfigureConn configureConn, WriteRequestContent writeRequestContent, boolean verbose) {
			return sendHTTPRequest(urlStr, configureConn, writeRequestContent, verbose, true);
		}

		private HttpResponse sendHTTPRequest(String urlStr, ConfigureConn configureConn, WriteRequestContent writeRequestContent, boolean verbose, boolean verboseOnError) {
			
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
			
			connection.setConnectTimeout(2000);
			try { connection.connect(); }
			catch (ConnectException e) {
				switch (e.getMessage()) {
				case "Connection refused: connect":
					lastRC = RC_CONNECT_REFUSED;
					if (verboseOnError) System.err.println("Connection refused at connect");
					break;
				case "Connection timed out: connect":
					lastRC = RC_CONNECT_TIMEOUT;
					if (verboseOnError) System.err.println("Connection timed out at connect");
					break;
				default:
					e.printStackTrace();
				}
				return null;
			}
			catch (SocketTimeoutException e) {
				if (e.getMessage().equals("connect timed out")) {
					lastRC = RC_CONNECT_TIMEOUT;
					if (verboseOnError) System.err.println("Socket Timeout");
				} else
					e.printStackTrace();
				return null;
			}
			catch (IOException e) { e.printStackTrace(); return null; }
			
			if (writeRequestContent!=null) {
				boolean successful = writeRequestContent.writeTo(connection);
				if (!successful) { if (verboseOnError) showConnection(connection); connection.disconnect(); return null; }
			}
			
			int httpResponseCode;
			try { httpResponseCode = connection.getResponseCode(); } catch (IOException e1) { httpResponseCode=111111; }
			if (httpResponseCode!=200) {
				lastRC = httpResponseCode;
				String responseMessage;
				try { responseMessage = connection.getResponseMessage(); } catch (IOException e) { responseMessage="????"; }
				if (verboseOnError) System.err.println("HTTP Response: "+httpResponseCode+" \""+responseMessage+"\"");
				return null;
			}
			
			Object content;
			if (connection.getContentLength()>0)
				try { content = connection.getContent(); }
				catch (IOException e) { if (verboseOnError) showConnection(connection); e.printStackTrace(); connection.disconnect(); return null; }
			else
				content = null;
			if (verbose) System.out.println("Content: "+content);
			
			HttpResponse response = null;
			if (content instanceof InputStream) {
				response = new HttpResponse();
				InputStream input = (InputStream)content;
				response.bytes = new byte[connection.getContentLength()];
				int n,pos=0;
				try { while ( (n=input.read(response.bytes, pos, response.bytes.length-pos))>=0 ) pos += n; }
				catch (IOException e) { e.printStackTrace(); if (verbose) System.out.println("abort reading response");}
				
				if (verbose) {
					String bytesReadStr = pos!=response.bytes.length?(" "+pos+" of "+response.bytes.length+" bytes "):"";
					if (pos<1000) {
						System.out.println("Content (bytes read): "+bytesReadStr+""+Arrays.toString(response.bytes));
					}
				}
				
				response.string = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(response.bytes, 0, pos)).toString();
				
				if (verbose) {
					if (response.string.length()>1000) {
						System.out.println("Content (as String): "+response.string.substring(0, 100)+" ...");
						System.out.println("                     ... "+response.string.substring(response.string.length()-100,response.string.length()));
					} else
						System.out.println("Content (as String): "+response.string);
				}
			}
			
			if (verboseOnError && response==null) showConnection(connection); 
			
			connection.disconnect();
			
			return response;
		}

		private void showConnection(HttpURLConnection connection) {
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

}
