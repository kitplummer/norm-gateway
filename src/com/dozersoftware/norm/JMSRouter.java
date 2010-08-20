/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package com.dozersoftware.norm;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Properties;

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.jboss.internal.soa.esb.rosetta.pooling.ConnectionException;
import org.jboss.internal.soa.esb.rosetta.pooling.JmsConnectionPool;
import org.jboss.internal.soa.esb.rosetta.pooling.JmsConnectionPoolContainer;
import org.jboss.internal.soa.esb.rosetta.pooling.JmsSession;
import org.jboss.soa.esb.actions.routing.AbstractRouter;
import org.jboss.soa.esb.ConfigurationException;
import org.jboss.soa.esb.actions.ActionProcessingException;
import org.jboss.soa.esb.addressing.EPR;
import org.jboss.soa.esb.addressing.eprs.JMSEpr;
import org.jboss.soa.esb.common.Configuration;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.helpers.KeyValuePair;
import org.jboss.soa.esb.helpers.NamingContextException;
import org.jboss.soa.esb.helpers.NamingContextPool;
import org.jboss.soa.esb.notification.jms.DefaultJMSPropertiesSetter;
import org.jboss.soa.esb.notification.jms.JMSPropertiesSetter;
import org.jboss.soa.esb.util.ClassUtil;
import org.jboss.soa.esb.util.JmsUtil;
import org.jboss.soa.esb.util.JndiUtil;
import org.jboss.soa.esb.util.Util;

/**
 * JMS Routing Action Processor.
 * <p/>
 * Sample Action Configuration:
 * <pre>{@code
 * <action class="org.jboss.soa.esb.actions.routing.JMSRouter">
 *     <property name="jndiName" value="queue/A"/>
 * </action>
 *
 * Option properties:
 *     <property name="unwrap" value="false"/>
 *     <property name="jndi-context-factory" value="org.jnp.interfaces.NamingContextFactory"/>
 *     <property name="jndi-URL" value="127.0.0.1:1099"/>
 *     <property name="jndi-pkg-prefix" value="org.jboss.naming:org.jnp.interfaces"/>
 *     <property name="connection-factory" value="ConnectionFactory"/>
 *     <property name="persistent" value="true"/>
 *     <property name="priority" value="javax.jms.Message.DEFAULT_PRIORITY"/>
 *     <property name="time-to-live" value="javax.jms.Message.DEFAULT_TIME_TO_LIVE"/>
 *     <property name="security-principal" value="username"/>
 *     <property name="security-credential" value="pasword"/>
 *     <property name="property-strategy" value="&lt;property setter class name&gt;" />
 *     <property name="message-prop-<i><prop-name></i>="<i>> value="prop-value"<</i>" />
 *     <property name="jndi-prefixes" value="org.xyz."<</i>" />
 * }</pre>
 * Description of configuration attribues:
 * <ul>
 * <li><i>unwrap</i>:
 * 'true' will extract the message payload from the Message object before sending. false (default) will send the serialized Message object.</li>
 * 
 * <li><i>jndi-context-factory</i>: 
 * The JNDI context factory to use. Default is "org.jnp.interfaces.NamingContextFactory"</li>
 * 
 * <li><i>jndi-URL</i>: 
 * The JNDI URL to use. Default is "127.0.0.1:1099"</li>
 * 
 * <li><i>jndi-pkg-prefix</i>: 
 * The JNDI naming package prefixes to use. Default is "org.jboss.naming:org.jnp.interfaces".</li>
 * 
 * <li><i>connection-factory</i>: 
 * The name of the ConnectionFactory to use. Default is "ConnectionFactory".</li>
 * 
 * <li><i>persistent</i>: 
 * The JMS DeliveryMode. 'true' or 'false'. Default is "true".</li>
 * 
 * <li><i>priority</i>: 
 * The JMS Priority to be used. Default is "javax.jms.Message.DEFAULT_PRIORITY"</li>
 * 
 * <li><i>time-to-live</i>: 
 * The JMS Time-To-Live to be used. Default is "javax.jms.Message.DEFAULT_TIME_TO_LIVE"</li>
 * 
 * <li><i>security-principal</i>: 
 * Security principal use when creating the JMS connection.</li>
 * 
 * <li><i>security-credential</i>: 
 * The security credentials to use when creating the JMS connection. </li>
 * 
 * <li><i>property-strategy</i>: 
 * The implementation of the JMSPropertiesSetter interface, if overriding the default. </li>
 * 
 * <li><i>message-prop</i>: 
 * Properties to be set on the message are prefixed with "message-prop-".</li>
 * 
 * <li><i>jndi-prefixes</i>: 
 * A comma separated string of prefixes. Properties that have these prefixes will be added to the JNDI environment.</li>
 * 
 * <li><i>org.xyz.propertyName</i>: 
 * A jndi environment property that will be added to the jndi environment if the prefix 'org.xyz' was specified in the jndi-prefixes list.</li>
 * 
 * </ul>
 *
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 * @author <a href="mailto:daniel.bevenius@redhat.com">daniel.bevenius@redhat.com</a>
 * @author <a href="mailto:mike@finnclan.org">mike@finnclan.org</a>
 * @since Version 4.0
 */
public class JMSRouter extends AbstractRouter {
    /**
     * Logger.
     */
    private static Logger logger = Logger.getLogger(JMSRouter.class);
    /**
     * Constant used in configuration
     */
    public static final String PERSISTENT_ATTR = "persistent";
    /**
     * Constant used in configuration
     */
    public static final String PRIORITY_ATTR = "priority";
    /**
     * Constant used in configuration
     */
    public static final String TIME_TO_LIVE_ATTR = "time-to-live";
    /**
     * Security principal used when creating the JMS connection 
     */
	public static final String SECURITY_PRINCIPAL = "security-principal";
	/**
     * Security credential used when creating the JMS connection 
	 */
	public static final String SECURITY_CREDITIAL = "security-credential";
	/**
	 * property strategy class.
	 */
	public static final String PROPERTY_STRATEGY = "property-strategy" ;
    /**
     * Routing properties.
     */
    private ConfigTree properties;
    /**
     * The JMS Destination name from the configuration
     */
    private String destName;
    /**
     * Strategy for setting JMSProperties
     */
    private final JMSPropertiesSetter jmsPropertiesStrategy ;
    /**
     * Whether messages sent by this router should be sent with delivery mode
     * DeliveryMode.PERSISTENT or DeliveryMode.NON_PERSISTENT
     * Default is to send messages persistently
     */
    private int deliveryMode = DeliveryMode.PERSISTENT;
    /**
     * The priority for messages sent with this router
     */
    private int priority = Message.DEFAULT_PRIORITY;
    /**
     * The time-to-live for messages sent with this router
     */
    private long timeToLive = Message.DEFAULT_TIME_TO_LIVE;
	private String jndiContextFactory;
	private String jndiUrl;
	private String jndiPkgPrefix;
	private String connectionFactory;

    /**
     * The pool to use for the jms routing.
     */
    private JmsConnectionPool pool ;
    /**
     * The jms target destination for routing.
     */
    private Destination jmsDestination ;
    /**
     * Thread local used for passing JmsSession between methods.
     * This is to allow modifications without changing the API.
     */
    private ThreadLocal<JmsSession> SESSION = new ThreadLocal<JmsSession>() ;
    /**
     * The JMS reply to destination.
     */
    private String jmsReplyToName ;
    private Properties environment;

    
    /**
     * Sole public constructor.
     * 
     * @param propertiesTree Action properties.
     * @throws ConfigurationException Queue name not configured.
     * @throws JMSException Unable to configure JMS destination.
     * @throws NamingException Unable to configure JMS destination.
     */
    public JMSRouter( final ConfigTree propertiesTree ) throws ConfigurationException, NamingException, JMSException {
        super(propertiesTree);

        this.properties = propertiesTree;

        destName = properties.getAttribute("jndiName");
        if(destName == null) {
            throw new ConfigurationException("JMSRouter must specify a 'jndiName' property.");
        }

        boolean persistent = Boolean.parseBoolean( properties.getAttribute(PERSISTENT_ATTR, "true") );
        deliveryMode = persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;

        String priorityStr = properties.getAttribute(PRIORITY_ATTR);
        if ( priorityStr != null )
	        priority = Integer.parseInt( priorityStr );

        final String ttlStr = properties.getAttribute(TIME_TO_LIVE_ATTR);
        if ( ttlStr != null )
	        timeToLive = Long.parseLong( ttlStr );

        jndiContextFactory = properties.getAttribute( JMSEpr.JNDI_CONTEXT_FACTORY_TAG, Configuration.getJndiServerContextFactory());
        jndiUrl = properties.getAttribute( JMSEpr.JNDI_URL_TAG, Configuration.getJndiServerURL());
        jndiPkgPrefix = properties.getAttribute( JMSEpr.JNDI_PKG_PREFIX_TAG, Configuration.getJndiServerPkgPrefix());
        connectionFactory = properties.getAttribute( JMSEpr.CONNECTION_FACTORY_TAG, "ConnectionFactory");
        
        final String propertyStrategy = properties.getAttribute(PROPERTY_STRATEGY) ;
        if (propertyStrategy == null) {
            jmsPropertiesStrategy = new DefaultJMSPropertiesSetter() ;
        } else {
            try {
                final Class propertyStrategyClass = ClassUtil.forName(propertyStrategy, getClass()) ;
                jmsPropertiesStrategy = (JMSPropertiesSetter)propertyStrategyClass.newInstance() ;
            } catch (final Throwable th) {
                throw new ConfigurationException("Failed to instantiate property strategy class: " + propertyStrategy, th) ;
            }
        }
        
        final String securityPrincipal = properties.getAttribute(SECURITY_PRINCIPAL);
        String securityCredential = properties.getAttribute(SECURITY_CREDITIAL);
        boolean useJMSSecurity = JmsUtil.isSecurityConfigured(securityPrincipal, securityCredential);
        if ( securityPrincipal != null && securityCredential == null ) 
            throw new ConfigurationException("'" + SECURITY_PRINCIPAL + "' must be accompanied by a '" + SECURITY_CREDITIAL + "'");
        else if ( securityCredential != null && securityPrincipal == null ) 
            throw new ConfigurationException("'" + SECURITY_CREDITIAL + "' must be accompanied by a '" + SECURITY_PRINCIPAL + "'");
        if (useJMSSecurity)
        {
            securityCredential = JmsUtil.getPasswordFromFile(securityCredential);
        }
        
        // Extract and environment properties given as properties in the config.
        environment = JndiUtil.parseEnvironmentProperties(propertiesTree);
        environment.setProperty(Context.PROVIDER_URL, jndiUrl);
        environment.setProperty(Context.INITIAL_CONTEXT_FACTORY, jndiContextFactory);
        environment.setProperty(Context.URL_PKG_PREFIXES, jndiPkgPrefix);
        try {
            pool = ( useJMSSecurity )  ? 
                    JmsConnectionPoolContainer.getPool(environment, connectionFactory, securityPrincipal, securityCredential) :
                    JmsConnectionPoolContainer.getPool(environment, connectionFactory );
        } catch (final ConnectionException ce) {
            throw new ConfigurationException("Unexpected error obtaining JMS connection pool") ;
        }
        
		createDestinationSetup(destName, jndiContextFactory, jndiUrl, jndiPkgPrefix, connectionFactory, securityPrincipal, securityCredential);
    }

    /**
	 * Will simply pass the message to the route method unmodified.
	 * @return <code>null</code> which will case the action pipeline processing to stop
	 */
    @Override
	public org.jboss.soa.esb.message.Message process( org.jboss.soa.esb.message.Message message ) throws ActionProcessingException
	{
    	route ( message );

    	return null;
	}

    /* (non-Javadoc)
     * @see org.jboss.soa.esb.actions.routing.AbstractRouter#route(java.lang.Object)
     */
    public void route(Object message) throws ActionProcessingException {
        final JmsSession jmsSession = getJmsSession() ;
        try {
            handleRouting(jmsSession, message) ;
        } catch (final JMSException jmse) {
            try {
                if (jmsSession.getTransacted()) {
                    jmsSession.rollback() ;
                    throw new ActionProcessingException("Unexpected exception routing message", jmse) ;
                } else {
                    // Try to acquire again
                    final JmsSession newJmsSession = getJmsSession() ;
                    try {
                        handleRouting(newJmsSession, message) ;
                    } finally {
                        pool.closeSession(newJmsSession) ;
                    }
                }
            } catch (final JMSException jmse2) {
                throw new ActionProcessingException("Unexpected exception routing message", jmse) ;
            }
        } finally {
            pool.closeSession(jmsSession) ;
        }
    }
    
    private void handleRouting(final JmsSession jmsSession, Object message) throws JMSException, ActionProcessingException {
        SESSION.set(jmsSession) ;
        try {
            if(!(message instanceof org.jboss.soa.esb.message.Message)) {
                throw new ActionProcessingException("Cannot send Object [" + message.getClass().getName() + "] to destination [" + destName + "]. Object must be an instance of org.jboss.soa.esb.message.Message) .");
            }
        
            final org.jboss.soa.esb.message.Message esbMessage = (org.jboss.soa.esb.message.Message)message;

            try {
                Message jmsMessage = null;
                
                if ( unwrap ) {
                    Object objectFromBody = getPayloadProxy().getPayload(esbMessage);
                    jmsMessage = createJMSMessageWithObjectType( objectFromBody );
                } 
                else  {
                    jmsMessage = createObjectMessage(Util.serialize(esbMessage));
                }
                
                setStringProperties(jmsMessage);
                setJMSProperties( esbMessage, jmsMessage );
                setJMSReplyTo( jmsMessage, esbMessage );
                send( jmsMessage );
            } catch (JMSException jmse) {
                throw jmse ;
            } catch(Exception e) {
                final String errorMessage = "Exception while sending message [" + message + "] to destination [" + destName + "]." ;
                logger.error(errorMessage);
                throw new ActionProcessingException(errorMessage, e);
            }
        } finally {
            SESSION.set(null) ;
        }
    }

    private JmsSession getJmsSession() throws ActionProcessingException {
        try {
            return pool.getSession() ;
        } catch (final ConnectionException ce) {
            throw new ActionProcessingException("Unexpected ConnectionException acquiring JMS session", ce) ;
        } catch (NamingException ne) {
            throw new ActionProcessingException("Unexpected NamingException acquiring JMS session", ne) ;
        } catch (JMSException jmse) {
            throw new ActionProcessingException("Unexpected JMSException acquiring JMS session", jmse) ;
        }
    }
    
    protected Message createJMSMessageWithObjectType( Object objectFromBody ) throws JMSException
	{
		Message jmsMessage = null;
		if(objectFromBody instanceof String) {
        	jmsMessage = SESSION.get().createTextMessage();

            if(logger.isDebugEnabled()) {
                logger.debug("Sending Text message: [" + objectFromBody + "] to destination [" + destName + "].");
            }

            ((TextMessage)jmsMessage).setText((String)objectFromBody);
        } else if(objectFromBody instanceof byte[]) {
        	jmsMessage = SESSION.get().createBytesMessage();

            if(logger.isDebugEnabled()) {
                logger.debug("Sending byte[] message: [" + objectFromBody + "] to destination [" + destName + "].");
            }

            ((BytesMessage)jmsMessage).writeBytes((byte[])objectFromBody);
        } else {
        	jmsMessage = createObjectMessage(objectFromBody);
        }

		return jmsMessage;
	}

	protected void send( Message jmsMessage ) throws JMSException
	{
		final MessageProducer jmsProducer = SESSION.get().createProducer(jmsDestination) ;
		try {
			jmsProducer.setPriority(priority) ;
			jmsProducer.setDeliveryMode(deliveryMode) ;
			jmsProducer.setTimeToLive(timeToLive) ;
			
			// The following seems to be broken but is copied for now.
			if (jmsReplyToName != null) {
				final Destination jmsReplyToDestination = SESSION.get().createQueue(jmsReplyToName);
				
				jmsMessage.setJMSReplyTo(jmsReplyToDestination);
			}
			
			jmsProducer.send(jmsMessage);
		} finally {
			jmsProducer.close() ;
		}
	}

	/**
	 * This method will set appropriate JMSProperties on the outgoing JMS Message instance.
	 * </p>
	 * Sublclasses can either override this method to add a different behaviour, or they can
	 * set the strategy by calling {@link #setJmsPropertiesStrategy(JMSPropertiesSetter)}.
	 * </p>
	 * See {@link org.jboss.soa.esb.notification.jms.JMSPropertiesSetter} for more info.
	 */
	protected void setJMSProperties(org.jboss.soa.esb.message.Message fromESBMessage, Message toJMSMessage ) throws JMSException {
		jmsPropertiesStrategy.setJMSProperties( fromESBMessage, toJMSMessage );
	}

	protected Message createObjectMessage(Object message) throws JMSException {
		Message jmsMessage;
		jmsMessage = SESSION.get().createObjectMessage();

		if(logger.isDebugEnabled()) {
		    logger.debug("Sending Object message: [" + message + "] to destination [" + destName + "].");
		}
		((ObjectMessage)jmsMessage).setObject((Serializable) message);
		return jmsMessage;
	}

    private void setStringProperties(Message msg) throws JMSException {
        String messagePropPrefix = "message-prop-";

        for(KeyValuePair property : properties.attributesAsList()) {
            String key = property.getKey();

            if(key.startsWith(messagePropPrefix) && key.length() > messagePropPrefix.length()) {
                msg.setStringProperty(key.substring(messagePropPrefix.length()), property.getValue());
            }
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.soa.esb.actions.ActionProcessor#getOkNotification(java.lang.Object)
     */
    public Serializable getOkNotification(org.jboss.soa.esb.message.Message message) {
        return null;
    }

    /* (non-Javadoc)
     * @see org.jboss.soa.esb.actions.ActionProcessor#getErrorNotification(java.lang.Object)
     */
    public Serializable getErrorNotification(org.jboss.soa.esb.message.Message message) {
        return null;
    }

    protected void createDestinationSetup( String destName,
    		String jndiContextFactory,
    		String jndiUrl,
    		String jndiPkgPrefix,
    		String connectionFactory,
    		String securityPrincipal,
    		String securityCredential) throws ConfigurationException
	{
        final Properties environment = getEnvironment() ;
		try 
		{
            final JmsSession jmsSession = pool.getSession();
            try {
                Context oCtx = NamingContextPool.getNamingContext(environment);
                try {
                    try {
                        jmsDestination = (Destination) oCtx.lookup(destName);
                    } catch (NamingException ne) {
                        try {
                            oCtx = NamingContextPool.replaceNamingContext(oCtx, environment);
                            jmsDestination = (Destination) oCtx.lookup(destName);
                        } catch (NamingException nex) {
                            //ActiveMQ
                            jmsDestination = jmsSession.createQueue(destName);
                        }
                    }
                    final MessageProducer jmsProducer = jmsSession.createProducer(jmsDestination);
                    jmsProducer.close() ;
                } finally {
                    NamingContextPool.releaseNamingContext(oCtx) ;
                }
            } finally {
                pool.closeSession(jmsSession) ;
            }
		} 
		catch (Throwable t) 
		{
			throw new ConfigurationException("Failed to configure JMS Queue for routing.", t);
		}
    }
    
    Properties getEnvironment()
    {
        return environment ;
    }
    
    protected void createDestinationSetup( String destName ) throws ConfigurationException
	{
    	createDestinationSetup(destName, null, null, null, null, null, null);
	}
    
    

	protected void setJMSReplyTo( final Message jmsMessage, final org.jboss.soa.esb.message.Message esbMessage ) throws URISyntaxException, JMSException, NamingException, ConnectionException, NamingContextException
	{
		EPR replyToEpr = esbMessage.getHeader().getCall().getReplyTo();
		if( !( replyToEpr instanceof JMSEpr) )
			return;
		
		JMSEpr jmsEpr = (JMSEpr) replyToEpr;
		String destinationType = jmsEpr.getDestinationType();
		if ( destinationType.equals( JMSEpr.QUEUE_TYPE ))
		{
            jmsReplyToName = jmsEpr.getDestinationName() ;
		}
	}

	/**
	 * The delivery mode in use.
	 * @return true if the delivery mode is DeliveryMode.PERSISTENT
	 */
	public boolean isDeliveryModePersistent()
	{
		return deliveryMode == DeliveryMode.PERSISTENT ;
	}

	/**
	 * The priority used when sending messages.
	 *
	 * @return int	the priorty
	 */
	public int getPriority()
	{
		return priority;
	}

	/**
	 * The time-to-live used when sending messages.
	 *
	 * @return int	the time-to-live for messages
	 */
	public long getTimeToLive()
	{
		return timeToLive;
	}

	public String getContextFactoryName()
	{
		return jndiContextFactory;
	}

	public String getJndiURL()
	{
		return jndiUrl;
	}

	public String getJndiPkgPrefix()
	{
		return jndiPkgPrefix;
	}

	public String getConnectionFactory()
	{
		return connectionFactory;
	}

}
