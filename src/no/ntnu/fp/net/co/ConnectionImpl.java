/*
 * Created on Oct 27, 2004
 */
package no.ntnu.fp.net.co;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import no.ntnu.fp.net.admin.Log;
import no.ntnu.fp.net.cl.ClException;
import no.ntnu.fp.net.cl.ClSocket;
import no.ntnu.fp.net.cl.KtnDatagram;
import no.ntnu.fp.net.cl.KtnDatagram.Flag;

/**
 * Implementation of the Connection-interface. <br>
 * <br>
 * This class implements the behaviour in the methods specified in the interface
 * {@link Connection} over the unreliable, connectionless network realised in
 * {@link ClSocket}. The base class, {@link AbstractConnection} implements some
 * of the functionality, leaving message passing and error handling to this
 * implementation.
 * 
 * @author Sebj�rn Birkeland and Stein Jakob Nordb�
 * @see no.ntnu.fp.net.co.Connection
 * @see no.ntnu.fp.net.cl.ClSocket
 */
public class ConnectionImpl extends AbstractConnection {

    /** Keeps track of the used ports for each server port. */
    private static Map<Integer, Boolean> usedPorts = Collections.synchronizedMap(new HashMap<Integer, Boolean>());
    
    private int next_seq_nr = -1;
    
    private KtnDatagram lastSentAck = null;
    
    /**
     * Initialise initial sequence number and setup state machine.
     * 
     * @param myPort
     *            - the local port to associate with this connection
     */
    public ConnectionImpl(int myPort) {
    	super();
    	this.myPort = myPort;
    	myAddress = getIPv4Address();
    }
    
    public ConnectionImpl(int myPort, String host) {
    	super();
    	this.myPort = myPort;
    	myAddress = host;
    }

    /**
     * Get local ip address
     * 
     * @return
     */
    private String getIPv4Address() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /**
     * Establish a connection to a remote location.
     * 
     * @param remoteAddress
     *            - the remote IP-address to connect to
     * @param remotePort
     *            - the remote portnumber to connect to
     * @throws IOException
     *             If there's an I/O error.
     * @throws java.net.SocketTimeoutException
     *             If timeout expires before connection is completed.
     * @see Connection#connect(InetAddress, int)
     */
    public void connect(InetAddress remoteAddress, int remotePort) throws IOException,
            SocketTimeoutException {
    	
    	nextSequenceNo = 1;
    	state = State.CLOSED;
    	
    	// Store remote parameters
    	this.remoteAddress = remoteAddress.getHostAddress();
    	this.remotePort = remotePort;
    	
    	KtnDatagram ack, syn, synack = null;
	
    	// Create SYN packet and send it off
    	syn = constructInternalPacket(Flag.SYN);
    	state = State.SYN_SENT;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new SendTimer(new ClSocket(), syn), 0, RETRANSMIT);
    	
        long start = System.currentTimeMillis();
        while(synack == null && (System.currentTimeMillis()-start) < TIMEOUT) {
        	synack = receivePacket(true);
        	if(synack != null && (synack.getFlag() != Flag.SYN_ACK || synack.getAck() != syn.getSeq_nr()))
        		synack = null;
        }
    	timer.cancel();
    	
    	// No ACK received within timeout
    	if(synack == null)
    		throw new SocketTimeoutException("Connection timed out");
    	
    	// ACK the SYN_ACK
    	ack = constructInternalPacket(Flag.ACK);
    	ack.setAck(synack.getSeq_nr());
    	lastSentAck = ack;
    	send(ack);
    	
    	state = State.ESTABLISHED;
    	System.err.println("CLIENT: Esablished");
    }
    
    /**
     * Receive a packet
     * 
     * If internal is true the return will be a packet received within the timeout
     * or null if none was received. If internal is false it will block untill
     * a valid data packet has been found
     * 
     */
    @Override
    protected KtnDatagram receivePacket(boolean internal) throws IOException, ConnectException {
    	System.out.println("receivePacket()");
    	KtnDatagram packet = super.receivePacket(internal);
    	
    	if(packet != null) {
    		System.out.println(packet.getSeq_nr() + " " + packet.getFlag());
    		// First packet, store new seq number
	    	if(next_seq_nr == -1) {
	    		next_seq_nr = packet.getSeq_nr(); 
	    	}
    	
    		// If its a old packet we'll re-ACK and null it
	    	else if(next_seq_nr != packet.getSeq_nr()) {
	    		if(lastSentAck != null && lastSentAck.getAck() == packet.getSeq_nr()) {
	    			send(lastSentAck);
	    		}
	    		System.out.println("Dropped packet due to bad seq nr expected "+next_seq_nr + " got "+packet.getSeq_nr());
	    		packet = null;
	    	}
    	}
    	
    	if(!internal && packet != null) {
    		// Null invalid data packets
    		if(!isValid(packet)) {
    			System.out.println("Invalid data packet!");
    			packet = null;
    		}
    	}
    	
    	if(packet != null)
    		next_seq_nr++;
    	
    	return packet;    	
    }
    
    /**
     * Simply send packet
     * 
     * @param packet
     * @throws IOException
     */
    protected void send(KtnDatagram packet) throws IOException {
		boolean sent = false;
		int tries = 3;
		do {
            try {
                new ClSocket().send(packet);
                sent = true;
            }
            catch (ClException e) {
                Log.writeToLog(packet, "CLException: Could not establish a "
                        + "connection to the specified address/port!", "AbstractConnection");
            }
            catch (ConnectException e) {
                // Silently ignore: Maybe recipient was processing and didn't
                // manage to call receiveAck() before we were ready to send.
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException ex) {
                }
            }
        }
        while (!sent && (tries-- > 0));
    }

    /**
     * Listen for, and accept, incoming connections.
     * 
     * @return A new ConnectionImpl-object representing the new connection.
     * @see Connection#accept()
     */
    public Connection accept() throws IOException, SocketTimeoutException {
    	nextSequenceNo = 100;
    	
    	KtnDatagram syn = null, ack = null;
    	
    	state = State.LISTEN;
    	
    	// Keep listening till we get a SYN packet
    	while(syn == null) {
    		syn = receivePacket(true);
    		
    		if(syn != null && syn.getFlag() != Flag.SYN) {
    			System.err.println("Dropped internal non-SYN packet in accept");
    			syn = null;
    		}
    	}
    	
    	state = State.SYN_RCVD;
    	remoteAddress = syn.getSrc_addr();
    	remotePort = syn.getSrc_port();
    	
    	// SYN_ACK the SYN
    	KtnDatagram synack = constructInternalPacket(Flag.SYN_ACK);
    	synack.setAck(syn.getSeq_nr());
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new SendTimer(new ClSocket(), synack), 0, RETRANSMIT);
    
        // Read ack
        long start = System.currentTimeMillis();
        while(ack == null && (System.currentTimeMillis()-start) < TIMEOUT) {
        	ack = receivePacket(true);
        	if(ack != null && (ack.getFlag() != Flag.ACK || ack.getAck() != synack.getSeq_nr())) {
        		System.out.println("dropped non ack");
        		ack = null;
        	}
        }
    	timer.cancel();
    	
    	if(ack == null)
    		throw new SocketTimeoutException("Destination timed out");

    	// Construct new connection to handle the client
    	ConnectionImpl c = new ConnectionImpl(myPort);
    	c.myAddress = myAddress;
    	c.remoteAddress = remoteAddress;
    	c.remotePort = remotePort;
    	c.state = State.ESTABLISHED;
    	c.nextSequenceNo = nextSequenceNo;
    	
    	// Reset this connection object
    	remoteAddress = null;
    	remotePort = 0;    	
    	state = State.CLOSED;
    	
    	System.err.println("SERVER: New connection ESABLISHED, original CLOSED");
    	return c;
    	
    }

    /**
     * Send a message from the application.
     * 
     * @param msg
     *            - the String to be sent.
     * @throws ConnectException
     *             If no connection exists.
     * @throws IOException
     *             If no ACK was received.
     * @see AbstractConnection#sendDataPacketWithRetransmit(KtnDatagram)
     * @see no.ntnu.fp.net.co.Connection#send(String)
     */
    public void send(String msg) throws ConnectException, IOException {
    	if(state != State.ESTABLISHED)
    		throw new ConnectException("No connection");
    	
    	KtnDatagram data, ack = null;
    	
    	// Construct data packet
    	data = constructDataPacket(msg);
    	
    	// Send data
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new SendTimer(new ClSocket(), data), 0, RETRANSMIT);
    
        // Read ACK
        long start = System.currentTimeMillis();
        while(ack == null && (System.currentTimeMillis()-start) < TIMEOUT) {
        	ack = receivePacket(true);
        	if(ack != null && (ack.getFlag() != Flag.ACK || ack.getAck() != data.getSeq_nr())) {
        		System.out.println("Dropped packet "+ack);
        		ack = null;
        	}
        }
    	timer.cancel();
    	
    	// TODO Handle this better
    	if(ack == null)
    		throw new IOException("No ACK received");    	
    }

    /**
     * Wait for incoming data.
     * 
     * @return The received data's payload as a String.
     * @see Connection#receive()
     * @see AbstractConnection#receivePacket(boolean)
     * @see AbstractConnection#sendAck(KtnDatagram, boolean)
     */
    public String receive() throws ConnectException, IOException {
    	try {
	    	KtnDatagram ack, data = null;
	    	
	    	while(data == null) {
	    		data = receivePacket(false);
	    		if(data != null && !isValid(data)) {
	    			System.err.println("Invalid data packet");
	    			data = null;
	    		}
	    	}
	    	
	    	// ACK the packet and we're done
	    	ack = constructInternalPacket(Flag.ACK);
	    	ack.setAck(data.getSeq_nr());
	    	lastSentAck = ack;
	    	send(ack);
	    	
	    	return (String) data.getPayload();
    	} catch(EOFException e) {
    		System.out.println(disconnectRequest.getSeq_nr());
    		next_seq_nr++; // Packet not read by readPacket(), make sure sequence number is incremented
    		System.out.println(next_seq_nr);
    		state = State.CLOSE_WAIT;
    		close();
    	}
    	return null;
    }

    /**
     * Close the connection.
     * 
     * @TODO There are more states that needs to be visited here
     * 
     * @see Connection#close()
     */
    public void close() throws IOException {  	
    	KtnDatagram fin, ack = null;
    	long start;
    	
    	// We're initializing the tear-down
    	if(state == State.ESTABLISHED) {
    		fin = constructInternalPacket(Flag.FIN);

			state = State.FIN_WAIT_1;
			
			// SYN_ACK the SYN
	        Timer timer = new Timer();
	        timer.scheduleAtFixedRate(new SendTimer(new ClSocket(), fin), 0, RETRANSMIT);
	    
	        // Read ACK
	        start = System.currentTimeMillis();
	        while(ack == null && (System.currentTimeMillis()-start) < TIMEOUT) {
	        	//System.out.println(ack);
	        	ack = receivePacket(true);
	        	if(ack != null && (ack.getFlag() != Flag.ACK || ack.getAck() != fin.getSeq_nr())) {
	        		System.out.println("Dropped packet "+ack);
	        		ack = null;
	        	}
	        }
	    	timer.cancel();
			
			if(ack == null)
				throw new IOException("FIN was not ACK-ed, desination is gone");
			
			state = State.FIN_WAIT_2;
			System.out.println("State changed FIN_WAIT_2");
			
			fin = null;
			start = System.currentTimeMillis();
	        while(fin == null && (System.currentTimeMillis()-start) < 4*TIMEOUT) {
				fin = receivePacket(true);
				if(fin != null && fin.getFlag() != Flag.FIN) {
					System.out.println("FIN_WAIT_2: dropped non FIN packet");
					fin = null;
				}
			}

			if(fin == null)
				throw new IOException("Got no response FIN");
	        
			disconnectRequest = fin;

	    	state = State.TIME_WAIT;
	    	
	    	System.out.println("State changed TIME_WAIT");
	    	
    	}
    	
    	disconnectSeqNo = disconnectRequest.getSeq_nr();
    	ack = constructInternalPacket(Flag.ACK);
		ack.setAck(disconnectSeqNo);
		lastSentAck = ack;
    	send(ack);    	
    	
    	fin = null;
		start = System.currentTimeMillis();
        while((System.currentTimeMillis()-start) < TIMEOUT) {
			fin = receivePacket(true);
		}
    	
    	if(state == State.CLOSE_WAIT) {

	    	fin = constructInternalPacket(Flag.FIN);
	    	
	    	state = State.LAST_ACK;
	    	
	        Timer timer = new Timer();
	        timer.scheduleAtFixedRate(new SendTimer(new ClSocket(), fin), 0, RETRANSMIT);
	    	
	    	ack = null;
			start = System.currentTimeMillis();
	    	while(ack == null && (System.currentTimeMillis()-start) < TIMEOUT) {
				ack = receivePacket(true);
				if(ack != null && ack.getFlag() == Flag.ACK && ack.getAck() == fin.getSeq_nr()) 
					break;				
				ack = null;	
			}
	    	timer.cancel();
    		
    	}
    	    	
    	System.out.println("State changed CLOSED");
    	state = State.CLOSED;
    	remoteAddress = null;
    	remotePort = 0;
    }

    /**
     * Test a packet for transmission errors. This function should only called
     * with data or ACK packets in the ESTABLISHED state.
     * 
     * @param packet
     *            Packet to test.
     * @return true if packet is free of errors, false otherwise.
     */
    protected boolean isValid(KtnDatagram packet) {
    	return packet.getDest_addr().equals(myAddress) 
    			&& packet.getDest_port() == myPort
    			&& packet.getChecksum() == packet.calculateChecksum(); 
    }
}
