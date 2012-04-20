import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import no.ntnu.fp.net.co.Connection;
import no.ntnu.fp.net.co.ConnectionImpl;


public class ServerRunner {
	public static void main(String[] args) {
		no.ntnu.fp.net.cl.FailureController.setError(true);
		no.ntnu.fp.net.cl.FailureController.setLoss_prob(0.1);
		no.ntnu.fp.net.cl.FailureController.setDelay_prob(0.3);
		no.ntnu.fp.net.cl.FailureController.setGhost_prob(0.02);
		no.ntnu.fp.net.cl.FailureController.setMaxDelay(1000);
		no.ntnu.fp.net.cl.FailureController.setOnlyDataError(false);
		no.ntnu.fp.net.cl.FailureController.setPayload_error_prob(0.5);
		//no.ntnu.fp.net.cl.FailureController.setHader_error_prob(0.5);
		no.ntnu.fp.net.cl.FailureController.setHeader_error_prob(0.0);
		new Server().start();
	}
}

class Server extends Thread {
	
	@Override
	public void run() {
		Connection ss = new ConnectionImpl(8002, "78.91.3.24");	
		String[] data = new String[4];
		try {
			Connection socket = ss.accept();
			try {
				for(int i = 0; i < data.length; i++) {
					data[i] = socket.receive();				
				}
				for(int i = 0; i < data.length; i++) {
					socket.send(data[i]);
				}
				socket.receive();
			} catch(IOException e) {
				System.err.println("Expected exception");
				e.printStackTrace();
			}
			//System.out.println("SERVER: Got from client: '"+str+"'");
			
			//System.out.println("SERVER: Sent to client: 'Server response'");
			//socket.send("Server response");
			
			//socket.close();
			
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