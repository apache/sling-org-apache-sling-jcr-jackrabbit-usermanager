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
package org.apache.sling.jcr.jackrabbit.usermanager.it.resource;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jackrabbit.usermanager.CreateGroup;
import org.apache.sling.jackrabbit.usermanager.CreateUser;
import org.apache.sling.jackrabbit.usermanager.DeleteGroup;
import org.apache.sling.jackrabbit.usermanager.DeleteUser;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.jackrabbit.usermanager.it.UserManagerTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;

/**
 * Basic test of AuthorizableResourceProvider component
 */
public abstract class BaseAuthorizableResourcesIT extends UserManagerTestSupport {
    private static AtomicLong counter = new AtomicLong(0);
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    protected BundleContext bundleContext;

    @Inject
    protected SlingRepository repository;

    @Inject
    protected ResourceResolverFactory resourceResolverFactory;

    @Inject
    protected ConfigurationAdmin configAdmin;

    @Inject
    protected CreateUser createUser;

    @Inject
    protected CreateGroup createGroup;

    @Inject
    protected DeleteUser deleteUser;

    @Inject
    protected DeleteGroup deleteGroup;

    @Inject
    protected SystemUserManagerPaths userManagerPaths;

    @Rule
    public TestName testName = new TestName();

    protected Session adminSession;
    protected User user1;
    protected Group group1;

    @Before
    public void setup() throws RepositoryException, LoginException {
        adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        assertNotNull("Expected adminSession to not be null", adminSession);
    }

    @After
    public void teardown() {
        try {
            adminSession.refresh(false);
            if (user1 != null) {
                deleteUser.deleteUser(adminSession, user1.getID(), new ArrayList<>());
            }

            if (adminSession.hasPendingChanges()) {
                adminSession.save();
            }
        } catch (RepositoryException e) {
            logger.warn(String.format("Failed to delete user: %s", e.getMessage()), e);
        }
        try {
            adminSession.refresh(false);
            if (group1 != null) {
                deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
            }

            if (adminSession.hasPendingChanges()) {
                adminSession.save();
            }
        } catch (RepositoryException e) {
            logger.warn(String.format("Failed to delete group: %s", e.getMessage()), e);
        }

        adminSession.logout();
    }

    protected String createUniqueName(String prefix) {
        return String.format("%s_%s%d", prefix, testName.getMethodName(), counter.incrementAndGet());
    }

    protected void createResourcesForAdaptTo() throws RepositoryException {
        Map<String, Object> nestedProps = new HashMap<>();
        nestedProps.put("key1", "value1");
        nestedProps.put("private/key2", "value2");
        nestedProps.put("private/sub/key3", "value3");

        group1 = createGroup.createGroup(adminSession, createUniqueName("group"), nestedProps, new ArrayList<>());
        assertNotNull("Expected group1 to not be null", group1);

        user1 = createUser.createUser(
                adminSession, createUniqueName("user"), "testPwd", "testPwd", nestedProps, new ArrayList<>());
        assertNotNull("Expected user1 to not be null", user1);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }
    }
}
