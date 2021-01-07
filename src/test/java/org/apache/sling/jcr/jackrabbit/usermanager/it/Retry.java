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

import static org.junit.Assert.fail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple Retry loop for tests */
public abstract class Retry {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private long timeoutMsec;
    private long nextIterationDelay;

    public Retry(long timeoutMsec, long nextIterationDelay) throws InterruptedException {
        this(timeoutMsec, nextIterationDelay, true);
    }

    public Retry(long timeoutMsec, long nextIterationDelay, boolean autorun) throws InterruptedException {
        this.timeoutMsec = timeoutMsec;
        this.nextIterationDelay = nextIterationDelay;
        if (autorun) {
            run();
        }
    }

    protected void run() throws InterruptedException {
        final long timeout = System.currentTimeMillis() + timeoutMsec;
        Throwable lastT = null;
        while (System.currentTimeMillis() < timeout) {
            try {
                lastT = null;
                exec();
                break;
            } catch(Throwable t) {
                if (logger.isDebugEnabled()) {
                    logger.warn(String.format("exec failed: %s", t.getMessage()), t);
                } else {
                    logger.warn("exec failed: {}", t.getMessage());
                }
                lastT = t;
                Thread.sleep(nextIterationDelay);               
            }
        }

        if (lastT != null) {
            fail("Failed after " + timeoutMsec + " msec: " + lastT);
        }
    }

    protected abstract void exec();
}
