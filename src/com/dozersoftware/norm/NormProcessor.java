package com.dozersoftware.norm;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import mil.navy.nrl.norm.NormInstance;
import mil.navy.nrl.norm.NormNode;
import mil.navy.nrl.norm.NormSession;
import mil.navy.nrl.norm.NormStream;
import mil.navy.nrl.norm.enums.NormFlushMode;

import org.jboss.soa.esb.actions.AbstractActionPipelineProcessor;
import org.jboss.soa.esb.actions.ActionLifecycleException;
import org.jboss.soa.esb.actions.ActionProcessingException;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.message.Message;

public class NormProcessor extends AbstractActionPipelineProcessor {

	private static long TX_BUFFER_SIZE = 1048576;
	private static int MAX_PACKET_LENGTH = 2048;

	// private String messageBody;
	NormInstance instance;
	NormSession session;
	NormStream stream;

	private String handle;

	public NormProcessor(ConfigTree config) {
		handle = config.getAttribute("handle");
	}

	public void initialise() throws ActionLifecycleException {
		// Initialise resources...
		try {
			instance = NormInstance.getInstance();

			session = instance.createSession("224.1.2.3", 6003,
					NormNode.NORM_NODE_ANY);

			session.setRxPortReuse(true, false);

			session.startSender(1, TX_BUFFER_SIZE, 1400, (short) 16, (short) 4);
			stream = session.streamOpen(TX_BUFFER_SIZE);

			// Report to the network
			String xml = "<MESSAGE type=\"connect\" sender=\"" + handle
					+ "\"></MESSAGE>";

			System.out.println("NORM OUT Stream Setup: " + xml);
			transmit(xml);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Message process(final Message message)
			throws ActionProcessingException {

		String msg = (String) message.getBody().get();

		try {

			transmit(msg);
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

		System.out.println("Shutting Down Norm Processor.");
	}

	private boolean transmit(String message) {
		// Write data into an internal byte array
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		// Write Java data types into the above byte array
		DataOutputStream das = new DataOutputStream(baos);

		int packetLength;

		byte[] msgBytes = message.getBytes();

		if (msgBytes.length > MAX_PACKET_LENGTH - 2) {
			packetLength = MAX_PACKET_LENGTH;
			System.out.println("NORM ERROR: Packet too long!");
		} else {
			packetLength = msgBytes.length + 2;
		}

		try {
			das.writeChar(packetLength);
			das.writeBytes(message);

			das.flush();

			System.out.println("Writing NORM OUT bytes: "
					+ baos.toByteArray().length);
			int msgLeft = packetLength
					- stream.write(baos.toByteArray(), 0, packetLength);
			if (msgLeft > 0) {
				System.out.println("NORM OUT: TX BUFFER FULL!");
			} else {
				stream.flush(true, NormFlushMode.NORM_FLUSH_PASSIVE);
				System.out.println("NORM OUT: FLUSHED Message!");
			}

			das.close();
			baos.close();

		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
}