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

import java.util.Collections;
import java.util.Map;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

/**
 * Basic test of NestedAuthorizableValueMap
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class NestedAuthorizableValueMapIT extends BaseAuthorizableValueMapIT {

    @Override
    protected Option[] additionalOptions() {
        return new Option[] {
            newConfiguration("org.apache.sling.jackrabbit.usermanager.impl.resource.AuthorizableResourceProvider")
                    .put("resources.for.nested.properties", true)
                    .asOption()
        };
    }

    @Override
    protected Map<String, Object> createAuthorizableProps() throws LoginException {
        return createAuthorizableProps("nested/");
    }

    @Override
    protected ValueMap getValueMap(Authorizable a) throws LoginException, RepositoryException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(
                Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource;
            if (a.isGroup()) {
                resource = resourceResolver.resolve(
                        String.format("%s%s/nested", userManagerPaths.getGroupPrefix(), a.getID()));
            } else {
                resource = resourceResolver.resolve(
                        String.format("%s%s/nested", userManagerPaths.getUserPrefix(), a.getID()));
            }
            assertNotNull(resource);
            ValueMap vm = resource.adaptTo(ValueMap.class);
            assertNotNull(vm);
            return vm;
        }
    }

    @Test
    @Override
    public void testSize() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        int size = vm.size();
        assertEquals(27, size);

        ValueMap vm2 = getValueMap(group1);
        int size2 = vm2.size();
        assertEquals(27, size2);
    }
}
