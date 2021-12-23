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
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;

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
        }
    }

}
