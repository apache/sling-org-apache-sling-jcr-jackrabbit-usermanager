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

import javax.jcr.RepositoryException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.Map;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jackrabbit.usermanager.impl.resource.AuthorizableResourceProvider;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.ServiceReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Basic test of AuthorizableResourceProvider component
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class AuthorizableResourceProviderIT extends BaseAuthorizableResourcesIT {
    private static final String PEOPLE_ROOT = "/people";

    @Before
    public void setup() throws RepositoryException, LoginException {
        super.setup();

        user1 = createUser.createUser(
                adminSession,
                createUniqueName("user"),
                "testPwd",
                "testPwd",
                Collections.emptyMap(),
                new ArrayList<>());
        assertNotNull("Expected user1 to not be null", user1);

        group1 = createGroup.createGroup(
                adminSession, createUniqueName("group"), Collections.emptyMap(), new ArrayList<>());
        assertNotNull("Expected group1 to not be null", group1);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }
    }

    /**
     * Test changing the usermanager provider.root value
     */
    @Test
    public void changeProviderRoot() throws LoginException, RepositoryException, IOException {
        // the userManager resource should be mounted under /system/userManager
        checkResourceTypes("/system/userManager", PEOPLE_ROOT);

        org.osgi.service.cm.Configuration configuration = configAdmin.getConfiguration(
                "org.apache.sling.jackrabbit.usermanager.impl.resource.AuthorizableResourceProvider", null);
        Dictionary<String, Object> originalServiceProps = configuration.getProperties();
        ServiceReference<SystemUserManagerPaths> serviceReference = null;
        try {
            // update the service configuration to ensure the option is enabled
            Dictionary<String, Object> newServiceProps =
                    replaceConfigProp(originalServiceProps, "provider.root", PEOPLE_ROOT);
            configuration.update(newServiceProps);
            new WaitForServiceUpdated(
                    5000, 100, bundleContext, SystemUserManagerPaths.class, "provider.root", PEOPLE_ROOT);

            serviceReference = bundleContext.getServiceReference(SystemUserManagerPaths.class);
            assertEquals(PEOPLE_ROOT, serviceReference.getProperty("provider.root"));

            SystemUserManagerPaths service = bundleContext.getService(serviceReference);
            assertNotNull(service);
            assertEquals(PEOPLE_ROOT, service.getRootPath());

            // now the userManager resource should be mounted under /people
            checkResourceTypes(PEOPLE_ROOT, "/system/userManager");
        } finally {
            if (serviceReference != null) {
                // done with this.
                bundleContext.ungetService(serviceReference);
            }

            // put the original config back
            configuration.update(originalServiceProps);
            new WaitForServiceUpdated(
                    5000,
                    100,
                    bundleContext,
                    SystemUserManagerPaths.class,
                    "provider.root",
                    originalServiceProps == null
                            ? AuthorizableResourceProvider.DEFAULT_SYSTEM_USER_MANAGER_PATH
                            : originalServiceProps.get("provider.root"));
        }
    }

    protected void checkResourceTypes(String expectedPrefix, String unexpectedPrefix)
            throws LoginException, RepositoryException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            // --- expected resources paths ----

            Resource resource = resourceResolver.resolve(expectedPrefix);
            assertTrue(
                    "Expected resource type of sling/userManager for: " + resource.getPath(),
                    resource.isResourceType("sling/userManager"));

            resource = resourceResolver.resolve(expectedPrefix + "/user");
            assertTrue(
                    "Expected resource type of sling/users for: " + resource.getPath(),
                    resource.isResourceType("sling/users"));

            resource = resourceResolver.resolve(expectedPrefix + "/group");
            assertTrue(
                    "Expected resource type of sling/groups for: " + resource.getPath(),
                    resource.isResourceType("sling/groups"));

            resource = resourceResolver.resolve(expectedPrefix + "/user/" + user1.getID());
            assertTrue(
                    "Expected resource type of sling/user for: " + resource.getPath(),
                    resource.isResourceType("sling/user"));

            resource = resourceResolver.resolve(expectedPrefix + "/group/" + group1.getID());
            assertTrue(
                    "Expected resource type of sling/group for: " + resource.getPath(),
                    resource.isResourceType("sling/group"));

            // --- unexpected resources paths ----

            resource = resourceResolver.resolve(unexpectedPrefix);
            assertTrue(
                    "Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));

            resource = resourceResolver.resolve(unexpectedPrefix + "/user");
            assertTrue(
                    "Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));

            resource = resourceResolver.resolve(unexpectedPrefix + "/group");
            assertTrue(
                    "Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));

            resource = resourceResolver.resolve(unexpectedPrefix + "/user/" + user1.getID());
            assertTrue(
                    "Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));

            resource = resourceResolver.resolve(unexpectedPrefix + "/group/" + group1.getID());
            assertTrue(
                    "Expected resource type of sling:nonexisting for: " + resource.getPath(),
                    resource.isResourceType("sling:nonexisting"));
        }
    }

    /**
     * Test iteration of the usermanager root resource children
     */
    @Test
    public void listRootChildren() throws LoginException, RepositoryException, IOException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve("/system/userManager");
            assertTrue(
                    "Expected resource type of sling/userManager for: " + resource.getPath(),
                    resource.isResourceType("sling/userManager"));

            boolean foundUsers = false;
            boolean foundGroups = false;
            @NotNull Iterable<Resource> children = resource.getChildren();
            for (Iterator<Resource> iterator = children.iterator(); iterator.hasNext(); ) {
                Resource child = (Resource) iterator.next();
                if (child.isResourceType("sling/users")) {
                    foundUsers = true;
                } else if (child.isResourceType("sling/groups")) {
                    foundGroups = true;
                }
            }
            assertTrue(foundUsers);
            assertTrue(foundGroups);
        }
    }

    /**
     * Test iteration of the usermanager users resource children
     */
    @Test
    public void listUsersChildren() throws LoginException, RepositoryException, IOException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve("/system/userManager/user");
            assertTrue(
                    "Expected resource type of sling/users for: " + resource.getPath(),
                    resource.isResourceType("sling/users"));

            boolean foundUser = false;
            @NotNull Iterable<Resource> children = resource.getChildren();
            for (Iterator<Resource> iterator = children.iterator(); iterator.hasNext(); ) {
                Resource child = (Resource) iterator.next();
                if (child.isResourceType("sling/user") && user1.getID().equals(child.getName())) {
                    foundUser = true;
                }
            }
            assertTrue(foundUser);
        }
    }

    /**
     * Test iteration of the usermanager groups resource children
     */
    @Test
    public void listGroupsChildren() throws LoginException, RepositoryException, IOException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve("/system/userManager/group");
            assertTrue(
                    "Expected resource type of sling/groups for: " + resource.getPath(),
                    resource.isResourceType("sling/groups"));

            boolean foundGroup = false;
            @NotNull Iterable<Resource> children = resource.getChildren();
            for (Iterator<Resource> iterator = children.iterator(); iterator.hasNext(); ) {
                Resource child = (Resource) iterator.next();
                if (child.isResourceType("sling/group") && group1.getID().equals(child.getName())) {
                    foundGroup = true;
                }
            }
            assertTrue(foundGroup);
        }
    }

    @Test
    public void adaptResourceToMap() throws LoginException, RepositoryException {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable Map<?, ?> groupMap = groupResource.adaptTo(Map.class);
            assertNotNull(groupMap);
            assertEquals("value1", groupMap.get("key1"));

            Resource userResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable Map<?, ?> userMap = userResource.adaptTo(Map.class);
            assertNotNull(userMap);
            assertEquals("value1", userMap.get("key1"));
        }
    }

    @Test
    public void adaptResourceToValueMap() throws LoginException, RepositoryException {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable ValueMap groupMap = groupResource.adaptTo(ValueMap.class);
            assertNotNull(groupMap);
            assertEquals("AuthorizableValueMap", groupMap.getClass().getSimpleName());
            assertEquals("value1", groupMap.get("key1"));

            Resource userResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable ValueMap userMap = userResource.adaptTo(ValueMap.class);
            assertNotNull(userMap);
            assertEquals("AuthorizableValueMap", userMap.getClass().getSimpleName());
            assertEquals("value1", userMap.get("key1"));
        }
    }

    @Test
    public void adaptResourceToAuthorizable() throws LoginException, RepositoryException {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable Authorizable groupAuthorizable = groupResource.adaptTo(Authorizable.class);
            assertNotNull(groupAuthorizable);
            assertEquals(group1.getID(), groupAuthorizable.getID());

            Resource userResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable Authorizable userAuthorizable = userResource.adaptTo(Authorizable.class);
            assertNotNull(userAuthorizable);
            assertEquals(user1.getID(), userAuthorizable.getID());
        }
    }

    @Test
    public void adaptResourceToUserOrGroup() throws LoginException, RepositoryException {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable Group group = groupResource.adaptTo(Group.class);
            assertNotNull(group);
            assertEquals(group1.getID(), group.getID());
            assertNull(groupResource.adaptTo(User.class));

            Resource userResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable User user = userResource.adaptTo(User.class);
            assertNotNull(user);
            assertEquals(user1.getID(), user.getID());
            assertNull(userResource.adaptTo(Group.class));
        }
    }

    /**
     * For code coverage, test some adaption that falls through to the super class impl
     */
    @Test
    public void adaptResourceToSomethingElse() throws LoginException, RepositoryException {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable
            NestedAuthorizableResourcesIT groupObj = groupResource.adaptTo(NestedAuthorizableResourcesIT.class);
            assertNull(groupObj);

            Resource userResource =
                    resourceResolver.resolve(String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable NestedAuthorizableResourcesIT userObj = userResource.adaptTo(NestedAuthorizableResourcesIT.class);
            assertNull(userObj);
        }
    }

    /**
     * Test to verify the fix for SLING-12185
     */
    @Test
    public void getResourceWithWrongPathPrefix() throws LoginException, RepositoryException {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.getResource(
                    String.format("%s%s", userManagerPaths.getUserPrefix(), group1.getID()));
            assertNull(groupResource);

            Resource userResource = resourceResolver.getResource(
                    String.format("%s%s", userManagerPaths.getGroupPrefix(), user1.getID()));
            assertNull(userResource);
        }
    }
}
