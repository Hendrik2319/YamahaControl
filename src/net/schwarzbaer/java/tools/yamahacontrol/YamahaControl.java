package net.schwarzbaer.java.tools.yamahacontrol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class YamahaControl {

	@SuppressWarnings("unused")
	private static void testByteBuffers() {
		String command = "<YAMAHA_AV cmd=\"GET\"><System><Power_Control><Power>GetParam</Power></Power_Control></System></YAMAHA_AV>";
		ByteBuffer bytes = StandardCharsets.UTF_8.encode(command);
		System.out.println("capacity: "+bytes.capacity());
		System.out.println("limit   : "+bytes.limit   ());
		System.out.println("position: "+bytes.position());
		
		byte[] array = new byte[bytes.limit()];
		bytes.get(array);		
		System.out.println("array.length: "+array.length);
		System.out.println("array: "+Arrays.toString(array));
	}

	@SuppressWarnings("unused")
	private static void test() throws MalformedURLException {
		
		String command = "<YAMAHA_AV cmd=\"GET\"><System><Power_Control><Power>GetParam</Power></Power_Control></System></YAMAHA_AV>";
		ByteBuffer byteBuf = StandardCharsets.UTF_8.encode(command);
		byte[] bytes = new byte[byteBuf.limit()];
		byteBuf.get(bytes);		
		
		String ip = "rx-v475";
		int port = 80; // 50100 bei BD-Playern
		URL url = new URL("http://"+ip+":"+port+"/YamahaRemoteControl/ctrl");
		
		System.out.println("Open Connection ...");
		HttpURLConnection connection;
		try { connection = (HttpURLConnection)url.openConnection(); }
		catch (IOException e) { e.printStackTrace(); return; }
		
		try { connection.setRequestMethod("POST"); }
		catch (ProtocolException e) { e.printStackTrace(); return; }
		
		connection.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
		connection.setRequestProperty("Content-Length", ""+bytes.length);
		connection.setDoOutput(true);
		connection.setDoInput(true);
		
		try { connection.connect(); }
		catch (IOException e) { e.printStackTrace(); return; }
		
		OutputStream outputStream;
		try { outputStream = connection.getOutputStream(); }
		catch (IOException e) { e.printStackTrace(); connection.disconnect(); return; }
		
		try { outputStream.write(bytes); }
		catch (IOException e) { e.printStackTrace(); try { outputStream.close(); } catch (IOException e1) { e1.printStackTrace(); } connection.disconnect(); return; }
		
		System.out.println("ContentLength  : "+connection.getContentLength  ());
		System.out.println("ContentType    : "+connection.getContentType    ());
		System.out.println("ContentEncoding: "+connection.getContentEncoding());
		
		Object content;
		try { content = connection.getContent(); }
		catch (IOException e) { e.printStackTrace(); connection.disconnect(); return; }
		System.out.println("Content: "+content); 
		
		if (content instanceof InputStream) {
			InputStream input = (InputStream)content;
			byte[] responseBytes = new byte[connection.getContentLength()];
			int n,pos=0;
			try { while ( (n=input.read(responseBytes, pos, responseBytes.length-pos))>=0 ) pos += n; }
			catch (IOException e) { e.printStackTrace(); System.out.println("abort reading response");}
			System.out.println("Content (bytes read): "+(pos!=responseBytes.length?(" "+pos+" of "+responseBytes.length+" bytes "):"")+""+Arrays.toString(responseBytes)); 
			System.out.println("Content (as String): "+new String(responseBytes)); 
		}
		
		connection.disconnect();
	}

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		Config.readConfig();

//		testByteBuffers();
		
//		try {
//			test();
//		} catch (MalformedURLException e) {
//			e.printStackTrace();
//		}
		
		YamahaControl yamahaControl = new YamahaControl();
		yamahaControl.createGUI();
		//yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><System><Power_Control><Power>GetParam</Power></Power_Control></System></YAMAHA_AV>");
		//yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status><Power_Control><Power>GetParam</Power></Power_Control></Basic_Status></Main_Zone></YAMAHA_AV>");
		//yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status><Power_Control><Power>On</Power></Power_Control></Basic_Status></Main_Zone></YAMAHA_AV>");
		//yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status><Power_Control><Power>On</Power></Power_Control></Basic_Status></Main_Zone></YAMAHA_AV>");
		
//		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Input><Input_Sel>GetParam</Input_Sel></Input></Main_Zone></YAMAHA_AV>");
//		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Input><Input_Sel_Item>GetParam</Input_Sel_Item></Input></Main_Zone></YAMAHA_AV>");
//		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Scene><Scene_Sel>GetParam</Scene_Sel></Scene></Main_Zone></YAMAHA_AV>");
//		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Scene><Scene_Sel_Item>GetParam</Scene_Sel_Item></Scene></Main_Zone></YAMAHA_AV>");
		
		//yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status><Power_Control><Power></Power></Power_Control></Basic_Status></Main_Zone></YAMAHA_AV>");
		//yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"PUT\"><Main_Zone><Power_Control><Power>On</Power></Power_Control></Main_Zone></YAMAHA_AV>");
		//yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"PUT\"><Main_Zone><Power_Control><Power>Standby</Power></Power_Control></Main_Zone></YAMAHA_AV>");
		//yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Power_Control><Power>GetParam</Power></Power_Control></Main_Zone></YAMAHA_AV>");
		
//		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Basic_Status>GetParam</Basic_Status></Main_Zone></YAMAHA_AV>");
//		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><Main_Zone><Config>GetParam</Config></Main_Zone></YAMAHA_AV>");
		
//		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><NET_RADIO><List_Info>GetParam</List_Info></NET_RADIO></YAMAHA_AV>");
		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><NET_RADIO><Play_Info>GetParam</Play_Info></NET_RADIO></YAMAHA_AV>");
//		yamahaControl.testCommand("192.168.2.34","<YAMAHA_AV cmd=\"GET\"><NET_RADIO><Config>GetParam</Config></NET_RADIO></YAMAHA_AV>");
	}
	
	YamahaControl() {
	}

	public void testCommand(String ip, String command) {
		String response = sendCommand(ip, command, true);
		System.out.println("Command : "+command);
		System.out.println("Response: "+response);
		XML.showXMLformated(response);
		System.out.println();
	}

	public String sendCommand(String ip, String command) {
		return sendCommand(ip, command, false);
	}

	public String sendCommand(String ip, String command, boolean verbose) {
		ByteBuffer byteBuf = StandardCharsets.UTF_8.encode(command);
		byte[] bytes = new byte[byteBuf.limit()];
		byteBuf.get(bytes);
		
		int port = 80; // 50100 bei BD-Playern
		String urlStr = "http://"+ip+":"+port+"/YamahaRemoteControl/ctrl";
		if (verbose) System.out.println("URL: "+urlStr);
		URL url;
		try { url = new URL(urlStr); }
		catch (MalformedURLException e2) { e2.printStackTrace(); return null; }
		
		if (verbose) System.out.println("Open Connection ...");
		HttpURLConnection connection;
		try { connection = (HttpURLConnection)url.openConnection(); }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		try { connection.setRequestMethod("POST"); }
		catch (ProtocolException e) { e.printStackTrace(); return null; }
		
		connection.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
		connection.setRequestProperty("Content-Length", ""+bytes.length);
		connection.setDoOutput(true);
		connection.setDoInput(true);
		
		try { connection.connect(); }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		OutputStream outputStream;
		try { outputStream = connection.getOutputStream(); }
		catch (IOException e) { e.printStackTrace(); connection.disconnect(); return null; }
		
		try { outputStream.write(bytes); }
		catch (IOException e) { e.printStackTrace(); try { outputStream.close(); } catch (IOException e1) { e1.printStackTrace(); } connection.disconnect(); return null; }
		
		Object content;
		try { content = connection.getContent(); }
		catch (IOException e) { e.printStackTrace(); if (verbose) showConnection(connection); connection.disconnect(); return null; }
		if (verbose) System.out.println("Content: "+content); 
		
		String contentStr = null;
		if (content instanceof InputStream) {
			InputStream input = (InputStream)content;
			byte[] responseBytes = new byte[connection.getContentLength()];
			int n,pos=0;
			try { while ( (n=input.read(responseBytes, pos, responseBytes.length-pos))>=0 ) pos += n; }
			catch (IOException e) { e.printStackTrace(); if (verbose) System.out.println("abort reading response");}
			if (verbose) System.out.println("Content (bytes read): "+(pos!=responseBytes.length?(" "+pos+" of "+responseBytes.length+" bytes "):"")+""+Arrays.toString(responseBytes)); 
			
			contentStr = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(responseBytes, 0, pos)).toString();
			if (verbose) System.out.println("Content (as String): "+new String(responseBytes)); 
		}
		
		if (verbose && contentStr==null) showConnection(connection);
		
		connection.disconnect();
		
		return contentStr;
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

	private void createGUI() {
		// TODO Auto-generated method stub
		
	}

}
