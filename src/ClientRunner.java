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
		String[] data = new String[]{"Line one", "Line two", "Line three", "Line four"};
		String[] rec = new String[data.length];

		try {
			Connection socket = new ConnectionImpl(8001, "127.0.0.1");
			socket.connect(InetAddress.getByName("127.0.0.1"), 8002);			
			for(int i = 0; i < data.length; i++) {
				socket.send(data[i]);	
			}			
			for(int i = 0; i < data.length; i++) {
				rec[i] = socket.receive();
			}			
			socket.close();	
		} catch(Exception ex) {
			System.out.println("CLIENT: exception during transmission");
			ex.printStackTrace();
			System.exit(1);
		}
		for(int i = 0; i < data.length; i++) {
			if(!data[i].equals(rec[i])) {
				System.out.println("\n\nBad data, test failed");
				System.exit(1);
			}
		}
		System.out.println("\n\n\nTEST OK\n\n\n");
		
	}
	
}