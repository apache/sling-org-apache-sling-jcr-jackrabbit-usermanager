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

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * SLING-11098 Basic test of AuthorizableResourceProvider component providing
 * resources that are a Principal but not an Authorizable
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class PrincipalResourcesIT extends BaseAuthorizableResourcesIT {

    /**
     * Test changing the usermanager provider.root value
     */
    @Test
    public void getResource() throws LoginException, RepositoryException, IOException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve("/system/userManager/group/" + EveryonePrincipal.NAME);
            assertNotNull(resource);
            assertEquals(EveryonePrincipal.NAME, resource.getName());
        }
    }

    /**
     * Test changing the usermanager provider.root value
     */
    @Test
    public void checkResourceType() throws LoginException, RepositoryException, IOException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve("/system/userManager/group/" + EveryonePrincipal.NAME);
            assertNotNull(resource);
            assertTrue("Expected resource type of sling/group for: " + resource.getPath(),
                    resource.isResourceType("sling/group"));
        }
    }

    /**
     * Test iteration of the usermanager groups resource children
     */
    @Test
    public void listGroupsChildren() throws LoginException, RepositoryException, IOException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource = resourceResolver.resolve("/system/userManager/group");
            assertTrue("Expected resource type of sling/groups for: " + resource.getPath(),
                    resource.isResourceType("sling/groups"));

            boolean foundGroup = false;
            @NotNull
            Iterable<Resource> children = resource.getChildren();
            for (Iterator<Resource> iterator = children.iterator(); iterator.hasNext();) {
                Resource child = (Resource) iterator.next();
                if (child.isResourceType("sling/group") && EveryonePrincipal.NAME.equals(child.getName())) {
                    foundGroup = true;
                }
            }
            assertTrue(foundGroup);
        }
    }

    @Test
    public void adaptResourceToMap() throws LoginException, RepositoryException  {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), EveryonePrincipal.NAME));
            @Nullable
            Map<?, ?> groupMap = groupResource.adaptTo(Map.class);
            assertNotNull(groupMap);
            assertTrue(groupMap.isEmpty());
        }
    }

    @Test
    public void adaptResourceToValueMap() throws LoginException, RepositoryException  {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), EveryonePrincipal.NAME));
            @Nullable
            ValueMap groupMap = groupResource.adaptTo(ValueMap.class);
            assertNotNull(groupMap);
            assertTrue(groupMap.isEmpty());
        }
    }

    @Test
    public void adaptResourceToPrincipal() throws LoginException, RepositoryException  {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), EveryonePrincipal.NAME));
            @Nullable
            Principal groupPrincipal = groupResource.adaptTo(Principal.class);
            assertNotNull(groupPrincipal);
            assertEquals(EveryonePrincipal.NAME, groupPrincipal.getName());
        }
    }

    /**
     * For code coverage, test some adaption that falls through to the super class impl
     */
    @Test
    public void adaptResourceToSomethingElse() throws LoginException, RepositoryException  {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource groupResource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), EveryonePrincipal.NAME));
            @Nullable
            NestedAuthorizableResourcesIT groupObj = groupResource.adaptTo(NestedAuthorizableResourcesIT.class);
            assertNull(groupObj);
        }
    }

}
