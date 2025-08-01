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
package org.apache.sling.jackrabbit.usermanager.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.withSettings;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 */
public class AuthorizableAdapterFactoryTest {
    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

    private AuthorizableAdapterFactory factory = null;

    @Before
    public void before() throws RepositoryException {
        factory = context.registerInjectActivateService(AuthorizableAdapterFactory.class);

        ResourceResolver rr = context.resourceResolver();
        Session session = rr.adaptTo(Session.class);
        if (session instanceof JackrabbitSession jrSession) {
            jrSession.getUserManager().createUser("admin", "test");
        }

    }
    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.AuthorizableAdapterFactory#getAdapter(java.lang.Object, java.lang.Class)}.
     */
    @Test
    public void testGetAdapter() {
        ResourceResolver rr = context.resourceResolver();
        User user = factory.getAdapter(rr, User.class);
        assertNotNull(user);
    }

    @Test
    public void testGetAdapterWithoutJackrabbitSession() {
        ResourceResolver mockRR = Mockito.mock(ResourceResolver.class);
        User user = factory.getAdapter(mockRR, User.class);
        assertNull(user);
    }

    @Test
    public void testGetAdapterWithCaughtRepositoryException() throws RepositoryException {
        ResourceResolver mockRR = Mockito.mock(ResourceResolver.class);
        Session mockSession = Mockito.mock(Session.class, withSettings().extraInterfaces(JackrabbitSession.class));
        Mockito.when(mockRR.adaptTo(Session.class)).thenReturn(mockSession);
        Mockito.doThrow(RepositoryException.class).when((JackrabbitSession)mockSession).getUserManager();

        User user = factory.getAdapter(mockRR, User.class);
        assertNull(user);
    }

}
