import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import no.ntnu.fp.net.co.Connection;
import no.ntnu.fp.net.co.ConnectionImpl;

/**
 * A simple client and server for testing the ConnectionImpl implementation
 * @author runar
 *
 */
public class ClientRunner {

	public static void main(String[] args) throws Exception {
		no.ntnu.fp.net.cl.FailureController.setError(false);
		new Client().start();
	}
	
}

class Client extends Thread {
	
	@Override
	public void run() {
		Connection socket = new ConnectionImpl(8001);
		
		try {
			socket.connect(InetAddress.getLocalHost(), 8002);
		
			System.out.println("CLIENT: Sent to server: 'THIS IS THE MESSAGE!'");
			//socket.send("THIS IS THE MESSAGE!");
			
			//String str = socket.receive();
			//System.out.println("CLIENT: Got from server: '"+str+"'");
			
			//str = socket.receive();
			try {
			Thread.sleep(1000); 
			} catch(Exception e) {}
			
			socket.close();
			
		} catch(ConnectException ex) {
			System.out.println("CLIENT: No connection");
			ex.printStackTrace();
		} catch(SocketTimeoutException ex) {
			System.out.println("CLIENT: Connection to server timedout");
			ex.printStackTrace();
		} catch(IOException ex) {
			System.out.println("CLIENT: IOException during connection");
			ex.printStackTrace();
		}
	}
	
}