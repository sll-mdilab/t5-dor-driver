package net.sllmdilab.dordriver.network;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.model.Message;

public class Hl7Client {
	private Logger logger = LoggerFactory.getLogger(Hl7Client.class);
	
	private HapiContext context;
	private Connection connection;
	private String host;
	private int port;
	private boolean useTls;
	
	public Hl7Client(HapiContext context, String host, int port, boolean useTls) {
		this.context = context;
		this.host = host;
		this.port = port;
		this.useTls = useTls;
	}
	
	public void connect() throws HL7Exception {
		connection = context.newClient(host, port, useTls);
	}
	
	public void disconnect() {
		if(connection.isOpen()) {
			connection.close();
			connection = null;
		}
	}
	
	public void sendMessage(Message message) throws HL7Exception, LLPException, IOException {
		if(!connection.isOpen()) {
			logger.debug("Connection was unexpectedly closed, reconnecting.");
			connect();
		}
		
		Message response = connection.getInitiator().sendAndReceive(message);
		
		if(logger.isDebugEnabled()) {
			logger.debug("Got response: " + response.encode());
		}
	}
}
