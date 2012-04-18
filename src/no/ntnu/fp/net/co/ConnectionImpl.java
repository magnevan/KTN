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
    	
    	state = State.CLOSED;
    	
    	// Store remote parameters
    	this.remoteAddress = remoteAddress.getHostAddress();
    	this.remotePort = remotePort;
    	
    	KtnDatagram syn, synack = null;
	
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
    	sendAck(synack, false);
    	
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
    	KtnDatagram packet = super.receivePacket(internal);
    	
    	if(packet != null) {
    		// First packet, store new seq number
	    	if(next_seq_nr == -1) {
	    		next_seq_nr = packet.getSeq_nr(); 
	    	}
    	
    		// If its a old packet we'll re-ACK and null it
	    	else if(next_seq_nr > packet.getSeq_nr()) {
	    		if(packet.getFlag() != Flag.ACK) {
	    			sendAck(packet, (packet.getFlag() == Flag.SYN));
	    		}
	    		packet = null;
	    	}
    	}
    	
    	if(!internal && packet != null) {
    		// Null invalid data packets
    		if(!isValid(packet)) 
    			packet = null;
    	}
    	
    	if(packet != null)
    		next_seq_nr++;
    	
    	return packet;    	
    }

    /**
     * Listen for, and accept, incoming connections.
     * 
     * @return A new ConnectionImpl-object representing the new connection.
     * @see Connection#accept()
     */
    public Connection accept() throws IOException, SocketTimeoutException {
    	
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
    	
    	ack = sendDataPacketWithRetransmit(data);
    	
    	// TODO Handle this better
    	if(ack == null)
    		throw new IOException("No ACK received");
    	if(ack.getAck() != data.getSeq_nr())
    		throw new IOException("Wrong ACK received expected: "+data.getSeq_nr()+" got: "+ack.getAck());
    	
    	// Data has been sent
    	
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
	    	KtnDatagram data = null;
	    	
	    	while(data == null) {
	    		data = receivePacket(false);
	    		if(!isValid(data)) {
	    			System.err.println("Invalid data packet");
	    			data = null;
	    		}
	    	}
	    	
	    	// ACK the packet and we're done
	    	sendAck(data, false);
	    	
	    	return (String) data.getPayload();
    	} catch(EOFException e) {
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
	        	ack = receivePacket(true);
	        	if(ack != null && (ack.getFlag() != Flag.ACK || ack.getAck() != fin.getSeq_nr()))
	        		ack = null;
	        }
	    	timer.cancel();
			
			if(ack == null)
				throw new IOException("FIN was not ACK-ed, desination is gone");
			
			state = State.FIN_WAIT_2;
			
			fin = null;
			start = System.currentTimeMillis();
	        while(fin == null && (System.currentTimeMillis()-start) < TIMEOUT) {
				fin = receivePacket(true);
				if(fin != null && fin.getFlag() != Flag.FIN)
					fin = null;
			}

			if(fin == null)
				throw new IOException("Got no response FIN");
	        
			disconnectRequest = fin;
			disconnectSeqNo = fin.getSeq_nr();
	    	sendAck(disconnectRequest, false);
	    	
	    	state = State.TIME_WAIT;	    	
	    	
    	}
    	
    	fin = null;
		start = System.currentTimeMillis();
        while(fin == null && (System.currentTimeMillis()-start) < TIMEOUT) {
			fin = receivePacket(true);
			if(fin != null && fin.getFlag() == Flag.FIN) {
		    	sendAck(disconnectRequest, false);
		    	fin = null;
			}
		}
    	
	    // Receiving side
    	if(state == State.CLOSE_WAIT) {

	    	fin = constructInternalPacket(Flag.FIN);
	    	
	    	state = State.LAST_ACK;
	    	
	        Timer timer = new Timer();
	        timer.scheduleAtFixedRate(new SendTimer(new ClSocket(), fin), 0, RETRANSMIT);
	    	
	    	ack = null;
			start = System.currentTimeMillis();
	    	while(ack == null && (System.currentTimeMillis()-start) < TIMEOUT) {
				ack = receivePacket(true);
				if(ack != null && ack.getFlag() == Flag.ACK && ack.getAck() == fin.getSeq_nr()) {
					timer.cancel();
					break;
				}
				ack = null;	
			}
    		
    	}
    	    	
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
