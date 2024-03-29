/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package com.ui.broker;

import com.ui.broker.security.PermitAllAuthorizatorPolicy;
import com.ui.broker.subscriptions.CTrieSubscriptionDirectory;
import com.ui.broker.subscriptions.ISubscriptionsDirectory;
import com.ui.broker.security.IAuthenticator;
import com.ui.persistence.MemorySubscriptionsRepository;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.ui.broker.NettyChannelAssertions.assertEqualsConnAck;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionRegistryTest {

    static final String FAKE_CLIENT_ID = "FAKE_123";
    static final String TEST_USER = "fakeuser";
    static final String TEST_PWD = "fakepwd";

    private MQTTConnection connection;
    private EmbeddedChannel channel;
    private SessionRegistry sut;
    private MqttMessageBuilders.ConnectBuilder connMsg;
    private static final BrokerConfiguration ALLOW_ANONYMOUS_AND_ZEROBYTE_CLIENT_ID =
        new BrokerConfiguration(true, true, false, false);
    private MemoryQueueRepository queueRepository;

    @BeforeEach
    public void setUp() {
        System.out.println("setup invoked");
        connMsg = MqttMessageBuilders.connect().protocolVersion(MqttVersion.MQTT_3_1).cleanSession(true);

        createMQTTConnection(ALLOW_ANONYMOUS_AND_ZEROBYTE_CLIENT_ID);
    }

    private void createMQTTConnection(BrokerConfiguration config) {
        channel = new EmbeddedChannel();
        connection = createMQTTConnection(config, channel);
    }

    private MQTTConnection createMQTTConnection(BrokerConfiguration config, Channel channel) {
        IAuthenticator mockAuthenticator = new MockAuthenticator(singleton(FAKE_CLIENT_ID),
                                                                 singletonMap(TEST_USER, TEST_PWD));

        ISubscriptionsDirectory subscriptions = new CTrieSubscriptionDirectory();
        ISubscriptionsRepository subscriptionsRepository = new MemorySubscriptionsRepository();
        subscriptions.init(subscriptionsRepository);
        queueRepository = new MemoryQueueRepository();

        final PermitAllAuthorizatorPolicy authorizatorPolicy = new PermitAllAuthorizatorPolicy();
        final Authorizator permitAll = new Authorizator(authorizatorPolicy);
        sut = new SessionRegistry(subscriptions, queueRepository, permitAll);
        final PostOffice postOffice = new PostOffice(subscriptions,
            new MemoryRetainedRepository(), sut, ConnectionTestUtils.NO_OBSERVERS_INTERCEPTOR, permitAll);
        return new MQTTConnection(channel, config, mockAuthenticator, sut, postOffice);
    }

    @Test
    public void testConnAckContainsSessionPresentFlag() {
        System.out.println("testConnAckContainsSessionPresentFlag invoked");
        MqttConnectMessage msg = connMsg.clientId(FAKE_CLIENT_ID)
                                        .protocolVersion(MqttVersion.MQTT_3_1_1)
                                        .build();
        NettyUtils.clientID(channel, FAKE_CLIENT_ID);
        NettyUtils.cleanSession(channel, false);

        // Connect a first time
        final SessionRegistry.SessionCreationResult res = sut.createOrReopenSession(msg, FAKE_CLIENT_ID, connection.getUsername());
        // disconnect
        res.session.disconnect();
//        sut.disconnect(FAKE_CLIENT_ID);

        // Exercise, reconnect
        EmbeddedChannel anotherChannel = new EmbeddedChannel();
        MQTTConnection anotherConnection = createMQTTConnection(ALLOW_ANONYMOUS_AND_ZEROBYTE_CLIENT_ID, anotherChannel);
        final SessionRegistry.SessionCreationResult result = sut.createOrReopenSession(msg, FAKE_CLIENT_ID, anotherConnection.getUsername());

        // Verify
        assertEquals(SessionRegistry.CreationModeEnum.CREATED_CLEAN_NEW, result.mode);
        assertTrue(anotherChannel.isOpen(), "Connection is accepted and therefore should remain open");
    }

    @Test
    public void connectWithCleanSessionUpdateClientSession() {
        // first connect with clean session true
        MqttConnectMessage msg = connMsg.clientId(FAKE_CLIENT_ID).cleanSession(true).build();
        connection.processConnect(msg);
        assertEqualsConnAck(CONNECTION_ACCEPTED, channel.readOutbound());
        connection.processDisconnect(null);
        assertFalse(channel.isOpen());

        // second connect with clean session false
        EmbeddedChannel anotherChannel = new EmbeddedChannel();
        MQTTConnection anotherConnection = createMQTTConnection(ALLOW_ANONYMOUS_AND_ZEROBYTE_CLIENT_ID,
                                                                anotherChannel);
        MqttConnectMessage secondConnMsg = MqttMessageBuilders.connect()
            .clientId(FAKE_CLIENT_ID)
            .protocolVersion(MqttVersion.MQTT_3_1)
            .build();

        anotherConnection.processConnect(secondConnMsg);
        assertEqualsConnAck(CONNECTION_ACCEPTED, anotherChannel.readOutbound());

        // Verify client session is clean false
        Session session = sut.retrieve(FAKE_CLIENT_ID);
        assertFalse(session.isClean());
    }
}
