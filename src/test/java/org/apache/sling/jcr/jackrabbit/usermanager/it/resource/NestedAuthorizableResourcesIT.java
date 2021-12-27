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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * Testing that nested property container resources are available when that 
 * capability is enabled via configuration.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class NestedAuthorizableResourcesIT extends BaseAuthorizableResourcesIT {

    @Override
    protected Option[] additionalOptions() {
        return new Option[] {
                newConfiguration("org.apache.sling.jackrabbit.usermanager.impl.resource.AuthorizableResourceProvider")
                    .put("resources.for.nested.properties", true)
                    .asOption()
        };
    }

    /**
     * Test resolving the nested properties of resources fails reasonably when the 
     * path doesn't exist
     */
    @Test
    public void checkNotExistingNestedResources() throws LoginException, RepositoryException, IOException {
        Map<String, Object> nestedProps = new HashMap<>();
        nestedProps.put("key1", "value1");
        nestedProps.put("private/key2", "value2");
        user1 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                nestedProps, new ArrayList<>());
        assertNotNull("Expected user1 to not be null", user1);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()));
            assertTrue("Expected resource type of sling/user for: " + resource.getPath(),
                    resource.isResourceType("sling/user"));

            @NotNull
            Resource notExisting = resourceResolver.resolve(String.format("%s%s/notexisting", userManagerPaths.getUserPrefix(), user1.getID()));
            assertNotNull(notExisting);
            assertTrue(notExisting.isResourceType(Resource.RESOURCE_TYPE_NON_EXISTING));

            @NotNull
            Resource nestedNotExisting = resourceResolver.resolve(String.format("%s%s/private/notexisting", userManagerPaths.getUserPrefix(), user1.getID()));
            assertNotNull(nestedNotExisting);
            assertTrue(nestedNotExisting.isResourceType(Resource.RESOURCE_TYPE_NON_EXISTING));
        }
    }

    /**
     * Test resolving the nested properties of user resources
     */
    @Test
    public void checkNestedUserPropertyResources() throws LoginException, RepositoryException, IOException {
        Map<String, Object> nestedProps = new HashMap<>();
        nestedProps.put("key1", "value1");
        nestedProps.put("private/key2", "value2");
        nestedProps.put("private/sub/key3", "value3");
        user1 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                nestedProps, new ArrayList<>());
        assertNotNull("Expected user1 to not be null", user1);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()));
            assertTrue("Expected resource type of sling/user for: " + resource.getPath(),
                    resource.isResourceType("sling/user"));

            @NotNull
            ValueMap valueMap = resource.getValueMap();
            assertEquals("Expected value1 for key1 property of: " + resource.getPath(),
                    "value1", valueMap.get("key1"));

            @Nullable
            Resource child = resource.getChild("private");
            assertNotNull(child);
            assertTrue("Expected resource type of sling/user/properties for: " + child.getPath(),
                    child.isResourceType("sling/user/properties"));
            @NotNull
            ValueMap childValueMap = child.getValueMap();
            assertEquals("Expected value2 for key2 property of: " + child.getPath(),
                    "value2", childValueMap.get("key2"));

            @Nullable
            Resource grandchild = child.getChild("sub");
            assertNotNull(grandchild);
            assertTrue("Expected resource type of sling/user/properties for: " + grandchild.getPath(),
                    grandchild.isResourceType("sling/user/properties"));
            @NotNull
            ValueMap grandchildValueMap = grandchild.getValueMap();
            assertEquals("Expected value3 for key3 property of: " + grandchild.getPath(),
                    "value3", grandchildValueMap.get("key3"));

            //try access via iteration over the children
            @NotNull
            Iterable<Resource> children = resource.getChildren();
            assertNotNull(children);
            for (Resource child2 : children) {
                assertNotNull(child2);
                assertTrue("Expected resource type of sling/user/properties for: " + child2.getPath(),
                        child2.isResourceType("sling/user/properties"));
            }
            @NotNull
            Iterable<Resource> grandchildren = grandchild.getChildren();
            assertNotNull(grandchildren);
            for (Resource child2 : grandchildren) {
                assertNotNull(child2);
                assertTrue("Expected resource type of sling/user/properties for: " + child2.getPath(),
                        child2.isResourceType("sling/user/properties"));
            }
        }
    }

    /**
     * Test resolving the nested properties of group resources
     */
    @Test
    public void checkNestedGroupPropertyResources() throws LoginException, RepositoryException, IOException {
        Map<String, Object> nestedProps = new HashMap<>();
        nestedProps.put("key1", "value1");
        nestedProps.put("private/key2", "value2");
        nestedProps.put("private/sub/key3", "value3");

        group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                nestedProps, new ArrayList<>());
        assertNotNull("Expected group1 to not be null", group1);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()));
            assertTrue("Expected resource type of sling/group for: " + resource.getPath(),
                    resource.isResourceType("sling/group"));

            @NotNull
            ValueMap valueMap = resource.getValueMap();
            assertEquals("Expected value1 for key1 property of: " + resource.getPath(),
                    "value1", valueMap.get("key1"));

            @Nullable
            Resource child = resource.getChild("private");
            assertNotNull(child);
            assertTrue("Expected resource type of sling/group/properties for: " + child.getPath(),
                    child.isResourceType("sling/group/properties"));
            @NotNull
            ValueMap childValueMap = child.getValueMap();
            assertEquals("Expected value2 for key2 property of: " + child.getPath(),
                    "value2", childValueMap.get("key2"));

            @Nullable
            Resource grandchild = child.getChild("sub");
            assertNotNull(grandchild);
            assertTrue("Expected resource type of sling/group/properties for: " + grandchild.getPath(),
                    grandchild.isResourceType("sling/group/properties"));
            @NotNull
            ValueMap grandchildValueMap = grandchild.getValueMap();
            assertEquals("Expected value3 for key3 property of: " + grandchild.getPath(),
                    "value3", grandchildValueMap.get("key3"));

            //try access via iteration over the children
            @NotNull
            Iterable<Resource> children = resource.getChildren();
            assertNotNull(children);
            for (Resource child2 : children) {
                assertNotNull(child2);
                assertTrue("Expected resource type of sling/group/properties for: " + child2.getPath(),
                        child2.isResourceType("sling/group/properties"));
            }
            @NotNull
            Iterable<Resource> grandchildren = grandchild.getChildren();
            assertNotNull(grandchildren);
            for (Resource child2 : grandchildren) {
                assertNotNull(child2);
                assertTrue("Expected resource type of sling/group/properties for: " + child2.getPath(),
                        child2.isResourceType("sling/group/properties"));
            }
        }
    }

    @Test
    public void adaptNestedResourceToMap() throws LoginException, RepositoryException  {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable
            Map<?, ?> groupMap = groupResource.adaptTo(Map.class);
            assertNotNull(groupMap);
            assertEquals("value2", groupMap.get("key2"));

            Resource userResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable
            Map<?, ?> userMap = userResource.adaptTo(Map.class);
            assertNotNull(userMap);
            assertEquals("value2", userMap.get("key2"));
        }
    }

    @Test
    public void adaptNestedResourceToValueMap() throws LoginException, RepositoryException  {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable
            ValueMap groupMap = groupResource.adaptTo(ValueMap.class);
            assertNotNull(groupMap);
            assertEquals("NestedAuthorizableValueMap", groupMap.getClass().getSimpleName());
            assertEquals("value2", groupMap.get("key2"));

            Resource userResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable
            ValueMap userMap = userResource.adaptTo(ValueMap.class);
            assertNotNull(userMap);
            assertEquals("NestedAuthorizableValueMap", userMap.getClass().getSimpleName());
            assertEquals("value2", userMap.get("key2"));
        }
    }

    @Test
    public void adaptNestedResourceToAuthorizable() throws LoginException, RepositoryException  {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable
            Authorizable groupAuthorizable = groupResource.adaptTo(Authorizable.class);
            assertNotNull(groupAuthorizable);
            assertEquals(group1.getID(), groupAuthorizable.getID());

            Resource userResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable
            Authorizable userAuthorizable = userResource.adaptTo(Authorizable.class);
            assertNotNull(userAuthorizable);
            assertEquals(user1.getID(), userAuthorizable.getID());
        }
    }

    @Test
    public void adaptNestedResourceToUserOrGroup() throws LoginException, RepositoryException  {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable
            Group group = groupResource.adaptTo(Group.class);
            assertNotNull(group);
            assertEquals(group1.getID(), group.getID());
            assertNull(groupResource.adaptTo(User.class));

            Resource userResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable
            User user = userResource.adaptTo(User.class);
            assertNotNull(user);
            assertEquals(user1.getID(), user.getID());
            assertNull(userResource.adaptTo(Group.class));
        }
    }

    /**
     * For code coverage, test some adaption that falls through to the super class impl
     */
    @Test
    public void adaptNestedResourceToSomethingElse() throws LoginException, RepositoryException  {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getGroupPrefix(), group1.getID()));
            @Nullable
            NestedAuthorizableResourcesIT groupObj = groupResource.adaptTo(NestedAuthorizableResourcesIT.class);
            assertNull(groupObj);

            Resource userResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getUserPrefix(), user1.getID()));
            @Nullable
            NestedAuthorizableResourcesIT userObj = userResource.adaptTo(NestedAuthorizableResourcesIT.class);
            assertNull(userObj);
        }
    }

    /**
     * Test iteration of the usermanager nested resource children
     */
    @Test
    public void listNestedChildren() throws LoginException, RepositoryException, IOException {
        createResourcesForAdaptTo();

        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getGroupPrefix(), group1.getID()));
            @NotNull
            Iterator<Resource> children = groupResource.listChildren();
            assertNotNull(children);
            assertTrue(children.hasNext());
            for (Iterator<Resource> iterator = children; iterator.hasNext();) {
                Resource child = (Resource) iterator.next();
                assertTrue(child.isResourceType("sling/group/properties"));
            }

            Resource userResource = resourceResolver.resolve(String.format("%s%s/private", userManagerPaths.getUserPrefix(), user1.getID()));
            @NotNull
            Iterator<Resource> children2 = userResource.listChildren();
            assertNotNull(children2);
            assertTrue(children2.hasNext());
            for (Iterator<Resource> iterator = children2; iterator.hasNext();) {
                Resource child = (Resource) iterator.next();
                assertTrue(child.isResourceType("sling/user/properties"));
            }
        }
    }
    
}
