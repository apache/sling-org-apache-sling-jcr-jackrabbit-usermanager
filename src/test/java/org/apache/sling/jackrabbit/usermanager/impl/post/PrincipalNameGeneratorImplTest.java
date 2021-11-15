/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jackrabbit.usermanager.impl.post;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator.NameInfo;
import org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet.RequestParameterImpl;
import org.apache.sling.jcr.jackrabbit.usermanager.it.post.CustomPrincipalNameFilterImpl;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrincipalNameGeneratorImplTest {

    @Parameters(name = "Type: {0}")
    public static Iterable<AuthorizableType> data() {
        return Arrays.asList(AuthorizableType.USER, AuthorizableType.GROUP);
    }

    private PrincipalNameGenerator defaultGenerator = new PrincipalNameGeneratorImpl();
    private AuthorizableType type;

    public PrincipalNameGeneratorImplTest(AuthorizableType type) {
        this.type = type;
    }

    @Test
    public void testPrincipalNameFromName() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put(SlingPostConstants.RP_NODE_NAME, new RequestParameter[] {
                new RequestParameterImpl("name1", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("name1", nameInfo.getPrincipalName());
        assertFalse(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameHint() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put(SlingPostConstants.RP_NODE_NAME_HINT, new RequestParameter[] {
                new RequestParameterImpl("name1", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("name1", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameHintWithFilter() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = new CustomPrincipalNameFilterImpl();
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put(SlingPostConstants.RP_NODE_NAME_HINT, new RequestParameter[] {
                new RequestParameterImpl("Na me1", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("na_me1", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameHintTooLong() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put(SlingPostConstants.RP_NODE_NAME_HINT, new RequestParameter[] {
                new RequestParameterImpl("namethatistoolong123456789", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("namethatistoolong123", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameValueFrom() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName", new RequestParameter[] {
                new RequestParameterImpl("name1", "UTF-8")
        });
        parameters.put(String.format("%s%s", SlingPostConstants.RP_NODE_NAME, SlingPostConstants.VALUE_FROM_SUFFIX), new RequestParameter[] {
                new RequestParameterImpl("displayName", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("name1", nameInfo.getPrincipalName());
        assertFalse(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameValueFromTooLong() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName", new RequestParameter[] {
                new RequestParameterImpl("namethatistoolong123456789", "UTF-8")
        });
        parameters.put(String.format("%s%s", SlingPostConstants.RP_NODE_NAME, SlingPostConstants.VALUE_FROM_SUFFIX), new RequestParameter[] {
                new RequestParameterImpl("displayName", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("namethatistoolong123456789", nameInfo.getPrincipalName());
        assertFalse(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameValueFromWithFilter() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = new CustomPrincipalNameFilterImpl();
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName", new RequestParameter[] {
                new RequestParameterImpl("Na me1", "UTF-8")
        });
        parameters.put(String.format("%s%s", SlingPostConstants.RP_NODE_NAME, SlingPostConstants.VALUE_FROM_SUFFIX), new RequestParameter[] {
                new RequestParameterImpl("displayName", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("Na me1", nameInfo.getPrincipalName());
        assertFalse(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameHintValueFrom() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName", new RequestParameter[] {
                new RequestParameterImpl("name1", "UTF-8")
        });
        parameters.put(String.format("%s%s", SlingPostConstants.RP_NODE_NAME_HINT, SlingPostConstants.VALUE_FROM_SUFFIX), new RequestParameter[] {
                new RequestParameterImpl("displayName", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("name1", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameHintValueFromTooLong() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName", new RequestParameter[] {
                new RequestParameterImpl("namethatistoolong123456789", "UTF-8")
        });
        parameters.put(String.format("%s%s", SlingPostConstants.RP_NODE_NAME_HINT, SlingPostConstants.VALUE_FROM_SUFFIX), new RequestParameter[] {
                new RequestParameterImpl("displayName", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("namethatistoolong123", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromNameHintValueFromWithFilter() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = new CustomPrincipalNameFilterImpl();
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName", new RequestParameter[] {
                new RequestParameterImpl("Na me1", "UTF-8")
        });
        parameters.put(String.format("%s%s", SlingPostConstants.RP_NODE_NAME_HINT, SlingPostConstants.VALUE_FROM_SUFFIX), new RequestParameter[] {
                new RequestParameterImpl("displayName", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("na_me1", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromConfiguredHint() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl(new String[] {
                    "displayName2"
            },
            10);
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName2", new RequestParameter[] {
                new RequestParameterImpl("name1", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("name1", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromConfiguredHintWithFilter() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl(new String[] {
                    "displayName2"
            },
            10);
        PrincipalNameFilter filter = new CustomPrincipalNameFilterImpl();
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName2", new RequestParameter[] {
                new RequestParameterImpl("Na me1", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("na_me1", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameFromConfiguredHintTooLong() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl(new String[] {
                    "displayName2"
            },
            10);
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        parameters.put("displayName2", new RequestParameter[] {
                new RequestParameterImpl("namethatistoolong", "UTF-8")
        });
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNotNull(nameInfo);
        assertEquals("namethatis", nameInfo.getPrincipalName());
        assertTrue(nameInfo.isMakeUnique());
    }

    @Test
    public void testPrincipalNameNotFound() {
        PrincipalNameGenerator generator = new PrincipalNameGeneratorImpl();
        PrincipalNameFilter filter = null;
        Map<String, RequestParameter[]> parameters = new HashMap<>();
        NameInfo nameInfo = generator.getPrincipalName(parameters, type, filter, defaultGenerator);
        assertNull(nameInfo);
    }
}
