import java.net.InetAddress;
import java.net.SocketTimeoutException;

import no.ntnu.fp.net.co.Connection;
import no.ntnu.fp.net.co.ConnectionImpl;


public class TestRunner extends Thread {

	private final int myPort, port;
	private final boolean listen;
	private final String payload;
	public Exception exception = null;
	public String receivedString = null;
	
	/**
	 * Create server tester. Listen on myPort, send data to connected clients
	 * @param myPort
	 * @param payload
	 */
	public TestRunner(int myPort, String payload) {
		this.myPort = myPort;
		this.port = -1;
		this.listen = true;
		this.payload = payload;
		
	}
	
	public TestRunner(int myPort, int port) {
		this.myPort = myPort;
		this.port = port;
		this.listen = false;
		this.payload = null;
	}
	
	public void run() {
		try {
			if(listen) {			
				Connection ss = new ConnectionImpl(myPort);			
				Connection client = ss.accept();
				
				client.send(payload);
				client.close();
				ss = client = null;
			} else {
				Connection c = new ConnectionImpl(myPort);
				c.connect(InetAddress.getLocalHost(), port);
				
				receivedString = c.receive();
				c.receive(); // Trigger EOF -> close()
			}
		} catch(Exception e) {
			exception = e;
		}
	}
	
	private static String[] testData = new String[]{"Line one", "Line two"};
	
	public static void main(String[] args) throws Exception {
		TestRunner server, client;
		
		for(String s : testData) {
			server = new TestRunner(8001, s);
			client = new TestRunner(8000, 8001);
			server.start();client.start();
			
			server.join();client.join();
			if(client.receivedString.equals(s)) {
				System.err.println("\n\nMATCH\n\n");
				System.exit(1);
			} else {
				System.exit(1);
				System.out.println("Missmatch");
			}
		}
		
		
	}
	
}
