import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import no.ntnu.fp.net.co.Connection;
import no.ntnu.fp.net.co.ConnectionImpl;


public class ServerRunner {
	public static void main(String[] args) {
		no.ntnu.fp.net.cl.FailureController.setError(false);
		no.ntnu.fp.net.cl.FailureController.setLoss_prob(0.5);
		new Server().start();
	}
}

class Server extends Thread {
	
	@Override
	public void run() {
		Connection ss = new ConnectionImpl(8002);		
		try {
			Connection socket = ss.accept();
		
			System.out.println(socket);
			
			String str = socket.receive();
			System.out.println("SERVER: Got from client: '"+str+"'");
			
			System.out.println("SERVER: Sent to client: 'Server response'");
			socket.send("Server response");
			
			socket.close();
			
		} catch(ConnectException ex) {
			System.out.println("SERVER: No connection");
			ex.printStackTrace();
		} catch(SocketTimeoutException ex) {
			System.out.println("SERVER: Connection to server timedout");
			ex.printStackTrace();
		} catch(IOException ex) {
			System.out.println("SERVER: IOException during connection");
			ex.printStackTrace();
		}
	}
	
}