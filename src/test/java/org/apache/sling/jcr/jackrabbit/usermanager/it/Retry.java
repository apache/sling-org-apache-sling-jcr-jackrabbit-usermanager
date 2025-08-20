/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.jackrabbit.usermanager.it;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/** Simple Retry loop for tests */
public abstract class Retry {
    private long timeoutMsec;
    private long nextIterationDelay;

    protected Retry(long timeoutMsec, long nextIterationDelay) {
        this(timeoutMsec, nextIterationDelay, true);
    }

    protected Retry(long timeoutMsec, long nextIterationDelay, boolean autorun) {
        this.timeoutMsec = timeoutMsec;
        this.nextIterationDelay = nextIterationDelay;
        if (autorun) {
            run();
        }
    }

    protected void run() {
        // retry until the exec call returns true and doesn't throw any exception
        await().atMost(timeoutMsec, TimeUnit.MILLISECONDS)
                .pollInterval(nextIterationDelay, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .until(this::exec);
    }

    protected abstract boolean exec();
}
