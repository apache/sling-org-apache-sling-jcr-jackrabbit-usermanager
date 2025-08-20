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

import java.util.Map;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.adapter.annotations.Adaptable;
import org.apache.sling.adapter.annotations.Adapter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;

/**
 * Resource implementation for nested property containers of Authorizable
 */
@Adaptable(
        adaptableClass = Resource.class,
        adapters = {
            @Adapter({Map.class, ValueMap.class, Authorizable.class}),
            @Adapter(
                    condition = "If the resource is an AuthorizableResource and represents a JCR User",
                    value = User.class),
            @Adapter(
                    condition = "If the resource is an AuthorizableResource and represents a JCR Group",
                    value = Group.class)
        })
public class NestedAuthorizableResource extends AuthorizableResource {
    private final String relPropPath;

    public NestedAuthorizableResource(
            Authorizable authorizable,
            ResourceResolver resourceResolver,
            String path,
            SystemUserManagerPaths systemUserManagerPaths,
            String relPropPath) {
        super(authorizable, resourceResolver, path, systemUserManagerPaths);
        this.relPropPath = relPropPath;
    }

    @Override
    protected String toResourceType(Authorizable authorizable) {
        return String.format("%s/properties", super.toResourceType(authorizable));
    }

    /*
     * (non-Javadoc)
     * @see org.apache.sling.api.adapter.Adaptable#adaptTo(java.lang.Class)
     */
    @Override
    public <T> T adaptTo(Class<T> type) {
        if (type == Map.class || type == ValueMap.class) {
            ValueMap valueMap = new NestedAuthorizableValueMap(authorizable, systemUserManagerPaths, relPropPath);
            return type.cast(valueMap);
        }

        return super.adaptTo(type);
    }
}
