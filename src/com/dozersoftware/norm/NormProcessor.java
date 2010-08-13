package com.dozersoftware.norm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import mil.navy.nrl.norm.NormInstance;
import mil.navy.nrl.norm.NormNode;
import mil.navy.nrl.norm.NormSession;

import org.jboss.soa.esb.actions.AbstractActionPipelineProcessor;
import org.jboss.soa.esb.actions.ActionLifecycleException;
import org.jboss.soa.esb.actions.ActionProcessingException;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.message.Message;

public class NormProcessor extends AbstractActionPipelineProcessor {
    
	//private String messageBody;
	NormInstance instance;
	NormSession session;
	
	public NormProcessor(ConfigTree config){
		//messageBody = config.getAttribute("messageBody");
	}
    public void initialise() throws ActionLifecycleException {
        // Initialise resources...
/*    	try {
			instance = NormInstance.getInstance();
			session = instance.createSession("224.1.2.3", 6003,
					NormNode.NORM_NODE_ANY);
			session.startSender(1, 1024 * 1024, 1400, (short)64, (short)16);
		} catch (IOException e) {
			e.printStackTrace();
		}*/
    }
        
    public Message process(final Message message) throws ActionProcessingException {
    	
    	Charset charset = Charset.forName("US-ASCII");
    	CharsetEncoder encoder = charset.newEncoder();
//    	CharsetDecoder decoder = charset.newDecoder();
    	
    	String msg = (String) message.getBody().get();
        

        
        try {
			//session.dataEnqueue(encoder.encode(CharBuffer.wrap(msg)),0,msg.length());
        	System.out.println("Message Out: " + msg);
        	System.out.println("Message Out: " + msg);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        return message;
    }
        
    public void destroy() throws ActionLifecycleException {
        // Cleanup resources...
    	session.stopSender();
    	session.destroySession();
    	instance.destroyInstance();
    }
}