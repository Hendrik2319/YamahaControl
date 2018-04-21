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

public class YamahaControl {

	public static void main(String[] args) {

//		testByteBuffers();
		
		try {
			test();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

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

}
