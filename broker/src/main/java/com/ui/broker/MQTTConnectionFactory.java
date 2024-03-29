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

import com.ui.broker.security.IAuthenticator;
import io.netty.channel.Channel;

class MQTTConnectionFactory {

    private final BrokerConfiguration brokerConfig;
    private final IAuthenticator authenticator;
    private final SessionRegistry sessionRegistry;
    private final PostOffice postOffice;

    MQTTConnectionFactory(BrokerConfiguration brokerConfig, IAuthenticator authenticator,
                          SessionRegistry sessionRegistry, PostOffice postOffice) {
        this.brokerConfig = brokerConfig;
        this.authenticator = authenticator;
        this.sessionRegistry = sessionRegistry;
        this.postOffice = postOffice;
    }

    MQTTConnection create(Channel channel) {
        return new MQTTConnection(channel, brokerConfig, authenticator, sessionRegistry, postOffice);
    }
}
