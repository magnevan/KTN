import java.net.InetAddress;
import java.net.SocketTimeoutException;

import no.ntnu.fp.net.co.Connection;
import no.ntnu.fp.net.co.ConnectionImpl;


public class TestRunner extends Thread {

	private final int myPort, port;
	private final String myHost, host;
	private final boolean listen;
	private final String payload;
	public Exception exception = null;
	public String receivedString = null;
	
	/**
	 * Create server tester. Listen on myPort, send data to connected clients
	 * @param myPort
	 * @param payload
	 */
	public TestRunner(int myPort, String myHost, String payload) {
		this.myPort = myPort;
		this.myHost = myHost;
		this.port = -1;
		this.host = null;
		this.listen = true;
		this.payload = payload;
		
	}
	
	public TestRunner(int myPort, String myHost, int port, String host) {
		this.myPort = myPort;
		this.myHost = myHost;
		this.port = port;
		this.host = host;
		this.listen = false;
		this.payload = null;
	}
	
	public void run() {
		try {
			if(listen) {			
				Connection ss = new ConnectionImpl(myPort, myHost);	
				Connection client = ss.accept();
				
				client.send(payload);
				client.close();
				ss = client = null;
			} else {
				Connection c = new ConnectionImpl(myPort, myHost);
				c.connect(InetAddress.getByName(host), port);
				
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
			server = new TestRunner(8001, "127.0.0.1", s);
			client = new TestRunner(8000, "127.0.0.1", 8001, "127.0.0.1");
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
