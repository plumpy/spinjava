/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.cats.redis.cluster;

import java.lang.management.ManagementFactory;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultNodeIdentity implements NodeIdentity {

    public static final String UNKNOWN_HOST = "UnknownHost";
    private static final long REFRESH_INTERVAL = TimeUnit.SECONDS.toMillis(30);

    private static String getHostName(String validationHost, int validationPort) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return UNKNOWN_HOST;
            }

            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (networkInterface.isLoopback()) {
                    continue;
                }

                if (!networkInterface.isUp()) {
                    continue;
                }

                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    Socket socket = null;
                    try {
                        socket = new Socket();
                        socket.bind(new InetSocketAddress(address, 0));
                        socket.connect(new InetSocketAddress(validationHost, validationPort), 125);
                        return address.getHostName();
                    } catch (Exception ignored) {
                        //
                    } finally {
                        if (socket != null) {
                            try {
                                socket.close();
                            } catch (Exception ignored) {
                                //
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return UNKNOWN_HOST;
    }

    private final String validationAddress;
    private final int validationPort;
    private final String runtimeName;
    private final AtomicReference<String> identity = new AtomicReference<>(null);
    private final AtomicBoolean validIdentity = new AtomicBoolean(false);
    private final AtomicLong refreshTime = new AtomicLong(0);
    private final Lock refreshLock = new ReentrantLock();

    public DefaultNodeIdentity() {
        this("www.google.com", 80);
    }

    public DefaultNodeIdentity(String validationAddress, int validationPort) {
        this.validationAddress = validationAddress;
        this.validationPort = validationPort;
        this.runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        loadIdentity();

    }

    @Override
    public String getNodeIdentity() {
        if (!validIdentity.get()) {
            if (shouldRefresh()) {
                refreshLock.lock();
                try {
                    if (shouldRefresh()) {
                        loadIdentity();
                    }
                } finally {
                    refreshLock.unlock();
                }
            }
        }
        return identity.get();
    }

    private boolean shouldRefresh() {
        return (System.currentTimeMillis() - refreshTime.get() > REFRESH_INTERVAL);
    }

    private void loadIdentity() {
        identity.set(String.format("%s:%s", getHostName(validationAddress, validationPort), runtimeName));
        validIdentity.set(identity.get().contains(UNKNOWN_HOST));
        refreshTime.set(System.currentTimeMillis());
    }
}
