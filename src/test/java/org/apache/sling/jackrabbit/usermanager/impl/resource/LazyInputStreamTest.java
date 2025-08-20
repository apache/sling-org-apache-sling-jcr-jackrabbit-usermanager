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
package org.apache.sling.jackrabbit.usermanager.impl.resource;

import javax.jcr.ValueFactory;

import java.io.IOException;

import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.sling.jackrabbit.usermanager.impl.resource.BaseAuthorizableValueMap.LazyInputStream;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Provides code coverage for BaseAuthorizableValueMap.LazyInputStream
 */
public class LazyInputStreamTest {

    private LazyInputStream lazyIS = null;

    @Before
    public void setup() {
        ValueFactory vf = ValueFactoryImpl.getInstance();
        lazyIS = new LazyInputStream(vf.createValue("Hello World"));
    }

    public void teardown() {
        if (lazyIS != null) {
            try {
                lazyIS.close();
            } catch (IOException e) {
                // ignore
            }
            lazyIS = null;
        }
    }

    @Test
    public void testClose() throws IOException {
        // first without any reads that would construct the delegatee
        lazyIS.close();
        // then some call that initiates te delegatee
        assertTrue(lazyIS.available() > 0);
        // and close again
        lazyIS.close();
    }

    @Test
    public void testAvailable() throws IOException {
        assertEquals(11, lazyIS.available());
    }

    @Test
    public void testRead() throws IOException {
        assertEquals('H', lazyIS.read());
    }

    @Test
    public void testReadArray() throws IOException {
        byte[] b = new byte[5];
        assertEquals(5, lazyIS.read(b));
        assertArrayEquals("Hello".getBytes(), b);
    }

    @Test
    public void testReadArrayWithOffsetAndLength() throws IOException {
        byte[] b = "AAAAA".getBytes();
        int off = 2;
        int len = 3;
        assertEquals(3, lazyIS.read(b, off, len));
        assertArrayEquals("AAHel".getBytes(), b);
    }

    @Test
    public void testSkip() throws IOException {
        long n = 2;
        lazyIS.skip(n);
        assertEquals('l', lazyIS.read());
    }

    @Test
    public void testMarkSupported() {
        assertTrue(lazyIS.markSupported());
    }

    @Test
    public void testMark() throws IOException {
        int readlimit = 3;
        assertEquals('H', lazyIS.read());
        lazyIS.mark(readlimit);
        assertEquals('e', lazyIS.read());
        assertEquals('l', lazyIS.read());
        lazyIS.reset();
        assertEquals('e', lazyIS.read());
        assertEquals('l', lazyIS.read());

        // also try exceeding the readlimit
        lazyIS.mark(readlimit);
        lazyIS.read(new byte[readlimit + 1]);
        lazyIS.reset();
        byte[] whatsleft = new byte[8];
        assertEquals(8, lazyIS.read(whatsleft));
        assertArrayEquals("lo World".getBytes(), whatsleft);
    }

    @Test
    public void testReset() throws IOException {
        int readlimit = 3;
        lazyIS.mark(readlimit);
        assertEquals('H', lazyIS.read());
        assertEquals('e', lazyIS.read());
        lazyIS.reset();
        assertEquals('H', lazyIS.read());
    }
}
