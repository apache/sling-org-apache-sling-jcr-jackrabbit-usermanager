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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.options;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jackrabbit.usermanager.CreateGroup;
import org.apache.sling.jackrabbit.usermanager.CreateUser;
import org.apache.sling.jackrabbit.usermanager.DeleteGroup;
import org.apache.sling.jackrabbit.usermanager.DeleteUser;
import org.apache.sling.jackrabbit.usermanager.impl.resource.AuthorizableResourceProvider;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.jackrabbit.usermanager.it.UserManagerTestSupport;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic test of AuthorizableResourceProvider component
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class AuthorizableResourceProviderIT extends UserManagerTestSupport {
    private static final String PEOPLE_ROOT = "/people";
    private static AtomicLong counter = new AtomicLong(0);
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    protected BundleContext bundleContext;
    
    @Inject
    protected SlingRepository repository;

    @Inject
    protected ResourceResolverFactory resourceResolverFactory;

    @Inject
    protected ConfigurationAdmin configAdmin;
    
    @Inject
    private CreateUser createUser;

    @Inject
    private CreateGroup createGroup;

    @Inject
    private DeleteUser deleteUser;

    @Inject
    private DeleteGroup deleteGroup;

    @Rule
    public TestName testName = new TestName();
    
    protected Session adminSession;
    protected User user1;
    protected Group group1;

    @Configuration
    public Option[] configuration() {
        return options(
            baseConfiguration()
        );
    }

    @Before
    public void setup() throws RepositoryException {
        adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        assertNotNull("Expected adminSession to not be null", adminSession);

        user1 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                Collections.emptyMap(), new ArrayList<>());
        assertNotNull("Expected user1 to not be null", user1);

        group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                Collections.emptyMap(), new ArrayList<>());
        assertNotNull("Expected group1 to not be null", group1);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }
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
    
    /**
     * Test changing the usermanager provider.root value
     */
    @Test
    public void changeProviderRoot() throws LoginException, RepositoryException, IOException {
        // the userManager resource should be mounted under /system/userManager
        checkResourceTypes("/system/userManager", PEOPLE_ROOT);

        org.osgi.service.cm.Configuration configuration = configAdmin.getConfiguration("org.apache.sling.jackrabbit.usermanager.impl.resource.AuthorizableResourceProvider", null);
        Dictionary<String, Object> originalServiceProps = configuration.getProperties();
        ServiceReference<SystemUserManagerPaths> serviceReference = null;
        try {
            // update the service configuration to ensure the option is enabled
            Dictionary<String, Object> newServiceProps = replaceConfigProp(originalServiceProps, "provider.root", PEOPLE_ROOT);
            configuration.update(newServiceProps);
            new WaitForServiceUpdated(5000, 100, bundleContext, SystemUserManagerPaths.class, 
                    "provider.root", PEOPLE_ROOT);
            
            serviceReference = bundleContext.getServiceReference(SystemUserManagerPaths.class);
            assertEquals(PEOPLE_ROOT, serviceReference.getProperty("provider.root"));

            // now the userManager resource should be mounted under /people
            checkResourceTypes(PEOPLE_ROOT, "/system/userManager");
        } finally {
            if (serviceReference != null) {
                // done with this.
                bundleContext.ungetService(serviceReference);
            }
            
            //put the original config back
            configuration.update(originalServiceProps);
            new WaitForServiceUpdated(5000, 100, bundleContext, SystemUserManagerPaths.class, "provider.root", 
                    originalServiceProps == null ? AuthorizableResourceProvider.DEFAULT_SYSTEM_USER_MANAGER_PATH : originalServiceProps.get("provider.root"));
        }
    }

    protected void checkResourceTypes(String expectedPrefix, String unexpectedPrefix) throws LoginException, RepositoryException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            // --- expected resources paths ----

            Resource resource = resourceResolver.resolve(expectedPrefix);
            assertTrue("Expected resource type of sling/userManager for: " + resource.getPath(),
                    resource.isResourceType("sling/userManager"));

            resource = resourceResolver.resolve(expectedPrefix + "/user");
            assertTrue("Expected resource type of sling/users for: " + resource.getPath(),
                    resource.isResourceType("sling/users"));

            resource = resourceResolver.resolve(expectedPrefix + "/group");
            assertTrue("Expected resource type of sling/groups for: " + resource.getPath(),
                    resource.isResourceType("sling/groups"));

            resource = resourceResolver.resolve(expectedPrefix + "/user/" + user1.getID());
            assertTrue("Expected resource type of sling/user for: " + resource.getPath(),
                    resource.isResourceType("sling/user"));

            resource = resourceResolver.resolve(expectedPrefix + "/group/" + group1.getID());
            assertTrue("Expected resource type of sling/group for: " + resource.getPath(),
                    resource.isResourceType("sling/group"));

            // --- unexpected resources paths ----

            resource = resourceResolver.resolve(unexpectedPrefix);
            assertTrue("Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));

            resource = resourceResolver.resolve(unexpectedPrefix + "/user");
            assertTrue("Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));

            resource = resourceResolver.resolve(unexpectedPrefix + "/group");
            assertTrue("Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));

            resource = resourceResolver.resolve(unexpectedPrefix + "/user/" + user1.getID());
            assertTrue("Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));

            resource = resourceResolver.resolve(unexpectedPrefix + "/group/" + group1.getID());
            assertTrue("Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));
        }
    }

}
