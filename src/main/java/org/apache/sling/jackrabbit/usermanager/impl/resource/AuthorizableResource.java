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

import javax.jcr.RepositoryException;

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
 * Resource implementation for Authorizable
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
public class AuthorizableResource extends BaseResource {
    protected final Authorizable authorizable;
    private final String resourceType;
    protected final SystemUserManagerPaths systemUserManagerPaths;

    public AuthorizableResource(
            Authorizable authorizable,
            ResourceResolver resourceResolver,
            String path,
            SystemUserManagerPaths systemUserManagerPaths) {
        super(resourceResolver, path);

        this.authorizable = authorizable;
        this.systemUserManagerPaths = systemUserManagerPaths;
        this.resourceType = toResourceType(authorizable);
    }

    /**
     * determine the resource type for the authorizable.
     * @param authorizable the authorizable to consider
     * @return the resource type
     */
    protected String toResourceType(Authorizable authorizable) {
        if (authorizable.isGroup()) {
            return "sling/group";
        } else {
            return "sling/user";
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.sling.api.resource.Resource#getResourceType()
     */
    public String getResourceType() {
        return resourceType;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.sling.api.adapter.Adaptable#adaptTo(java.lang.Class)
     */
    @Override
    public <T> T adaptTo(Class<T> type) {
        if (type == Map.class || type == ValueMap.class) {
            ValueMap valueMap = new AuthorizableValueMap(authorizable, systemUserManagerPaths);
            return type.cast(valueMap);
        } else if (type == Authorizable.class
                || (type == User.class && !authorizable.isGroup())
                || (type == Group.class && authorizable.isGroup())) {
            return type.cast(authorizable);
        }

        return super.adaptTo(type);
    }

    public String toString() {
        String id = null;
        if (authorizable != null) {
            try {
                id = authorizable.getID();
            } catch (RepositoryException e) {
                // ignore it.
            }
        }
        return getClass().getSimpleName() + ", id=" + id + ", path=" + getPath();
    }
}
