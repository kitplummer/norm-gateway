/*
 * Copyright 2010 Dozer Software, LLC
 * This software is licensed under the Simplified BSD License.
 * See license.txt for details.
 */

package com.dozersoftware.norm;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import mil.navy.nrl.norm.NormEvent;
import mil.navy.nrl.norm.NormFile;
import mil.navy.nrl.norm.NormInstance;
import mil.navy.nrl.norm.NormNode;
import mil.navy.nrl.norm.NormObject;
import mil.navy.nrl.norm.NormSession;
import mil.navy.nrl.norm.NormStream;
import mil.navy.nrl.norm.enums.NormEventType;
import mil.navy.nrl.norm.enums.NormObjectType;

import org.jboss.soa.esb.ConfigurationException;
import org.jboss.soa.esb.Service;
import org.jboss.soa.esb.client.ServiceInvoker;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.listeners.ListenerTagNames;
import org.jboss.soa.esb.listeners.lifecycle.AbstractThreadedManagedLifecycle;
import org.jboss.soa.esb.listeners.lifecycle.ManagedLifecycleException;
import org.jboss.soa.esb.listeners.message.MessageDeliverException;
import org.jboss.soa.esb.message.Message;
import org.jboss.soa.esb.message.format.MessageFactory;

public class NormGatewayListener extends AbstractThreadedManagedLifecycle {

	private ConfigTree listenerConfig;
	private Service service;
	private ServiceInvoker serviceInvoker;

	private NormInstance instance;
	private NormSession session;

	private int MAX_PACKET_LENGTH = 2048;

	public NormGatewayListener(final ConfigTree config)
			throws ConfigurationException {
		super(config);
		this.listenerConfig = config;

		String serviceCategory = listenerConfig
				.getRequiredAttribute(ListenerTagNames.TARGET_SERVICE_CATEGORY_TAG);
		String serviceName = listenerConfig
				.getRequiredAttribute(ListenerTagNames.TARGET_SERVICE_NAME_TAG);

		service = new Service(serviceCategory, serviceName);
	}

	protected void doInitialise() throws ManagedLifecycleException {
		// Create the ServiceInvoker instance for the target service....
		try {

			serviceInvoker = new ServiceInvoker(service);
			instance = NormInstance.getInstance();
			instance.setCacheDirectory("/tmp/norm");
			
			session = instance.createSession("224.1.2.3", 6003,
					NormNode.NORM_NODE_ANY);
			
			session.setRxPortReuse(true, false);

			session.startReceiver(1024 * 1024);
			
			System.out.println("NORM: Setting up Gateway Listener (session): " + session.getHandle());
		} catch (MessageDeliverException e) {
			throw new ManagedLifecycleException(
					"Failed to create ServiceInvoker for Service '" + service
							+ "'.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void doRun() {
		while (isRunning()) {

			try {
				NormEvent event;
				while ((event = instance.getNextEvent()) != null) {
					NormEventType eventType = event.getType();
					NormObject normObject = event.getObject();

					System.out.println("NORM EVENT: " + eventType);

					switch (eventType) {
					case NORM_RX_OBJECT_INFO:
						byte[] info = normObject.getInfo();
						String infoStr = new String(info, "US-ASCII");
						System.out.println("Info: " + infoStr);
						break;

					case NORM_RX_OBJECT_UPDATED:
						int readLength,
						rxPacketLength = 0,
						rxIndex = 0;

						byte[] rxStreamBuffer = new byte[MAX_PACKET_LENGTH + 1];
						if (normObject.getType() == NormObjectType.NORM_OBJECT_STREAM) {
							// Process incoming chat
							boolean msgSync = false;
							System.out
									.println("normChat: NORM_RX_OBJECT_UPDATED received for NORM_OBJECT_STREAM from  "
											+ normObject.getSender().getId());
							while (true) {
								if (!msgSync) {
									System.out
											.println("normChat: ProcessNormEvent msgSync = false, trying to sync");
									msgSync = ((NormStream) normObject)
											.seekMsgStart();

									if (!msgSync) {
										break;
									}
									System.out
											.println("normChat: ProcessNormEvent resync successful.");
								}

								readLength = rxPacketLength != 0 ? (rxPacketLength - rxIndex)
										: 2 - rxIndex;

								if (((NormStream) normObject).read(
										rxStreamBuffer, rxIndex, readLength) != 0) {
									System.out
											.println("Inside NORMStreamRead check - readLength:"
													+ readLength);
									if (readLength > 0) {
										System.out.println("rxIndex: "
												+ rxIndex);
										rxIndex += readLength;

										if (rxPacketLength == 0) {
											if (rxIndex >= 2) {
												System.out
														.println("NORM: Reading Header");
												// HHHMMMMNNNN!

												DataInputStream in = new DataInputStream(
														new ByteArrayInputStream(
																rxStreamBuffer));
												rxPacketLength = in.readChar();
												// System.out.println("Packet Length: "
												// + rxPacketLength);

												if (rxPacketLength < 2
														|| rxPacketLength > MAX_PACKET_LENGTH) {
													System.out
															.println("Resetting!");
													msgSync = false;
													rxIndex = rxPacketLength = 0;
													break;
												}
											} else {
												System.out
														.println("NORM: COntinuing!");
												continue;
											}
										}
									} else {
										System.out
												.println("normChat: ProcessNormEvent read in 0 bytes");
										break;
									}
								} else {
									msgSync = false;
									rxIndex = rxPacketLength = 0;
									System.out
											.println("normChat: Stream broken, request resync/reset");

								}

								if (rxPacketLength > 0
										&& (rxIndex >= rxPacketLength)) {
									rxStreamBuffer[rxIndex] = '\0';

									DataInputStream in = new DataInputStream(
											new ByteArrayInputStream(
													rxStreamBuffer));
									byte[] payload = new byte[rxPacketLength - 2];
									in.skipBytes(2);
									in.read(payload, 0, rxPacketLength - 2);

									String text = new String(payload,
											"US-ASCII");
									// System.out.println("Message: " + text);
									processMessage(text);

									rxIndex = rxPacketLength = 0;
								}
							}
						}
						break;

					case NORM_RX_OBJECT_COMPLETED:
						if (normObject.getType() == NormObjectType.NORM_OBJECT_FILE) {
							NormFile normFile = (NormFile) normObject;
							String filename = normFile.getName();

							System.out.println("NormFileObject: " + filename);
							Message esbMessage = MessageFactory.getInstance()
									.getMessage();

							esbMessage.getBody().add(
									new String("NORM: Have File! " + filename));
							serviceInvoker.deliverAsync(esbMessage);
						}
						break;
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/* Push the message on to the bus */
	public void processMessage(String messageText) {

		Message esbMessage = MessageFactory.getInstance().getMessage();

		esbMessage.getBody().add(new String(messageText));
		try {
			serviceInvoker.deliverAsync(esbMessage);
		} catch (MessageDeliverException e) {
			e.printStackTrace();
		}
	}

}