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
package org.apache.sling.jackrabbit.usermanager.impl.resource;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import org.apache.jackrabbit.api.security.principal.GroupPrincipal;
import org.apache.sling.adapter.annotations.Adaptable;
import org.apache.sling.adapter.annotations.Adapter;
import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

/**
 * Resource implementation for Principal (SLING-11098)
 */
@Adaptable(adaptableClass = Resource.class, adapters = {
    @Adapter({Map.class, ValueMap.class, Principal.class}),
    @Adapter(condition="If the resource is an PrincipalResource and represents a JCR principal", value = Principal.class)
})
public class PrincipalResource extends AbstractResource {
    protected final ResourceResolver resourceResolver;
    protected final Principal principal;
    private final String path;
    private final String resourceType;
    private final ResourceMetadata metadata;

    public PrincipalResource(Principal principal,
            ResourceResolver resourceResolver, String path) {
        super();

        this.resourceResolver = resourceResolver;
        this.principal = principal;
        this.path = path;
        this.resourceType = toResourceType(principal);

        this.metadata = new ResourceMetadata();
        metadata.setResolutionPath(path);
    }

    /**
     * determine the resource type for the principal.
     * @param principal the principal to consider
     * @return the resource type
     */
    protected String toResourceType(Principal principal) {
        if (principal instanceof GroupPrincipal) {
            return "sling/group";
        } else {
            return "sling/user";
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.sling.api.resource.Resource#getPath()
     */
    public String getPath() {
        return path;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.sling.api.resource.Resource#getResourceMetadata()
     */
    public ResourceMetadata getResourceMetadata() {
        return metadata;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.sling.api.resource.Resource#getResourceResolver()
     */
    public ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.sling.api.resource.Resource#getResourceSuperType()
     */
    public String getResourceSuperType() {
        return null;
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
            ValueMap valueMap = new ValueMapDecorator(Collections.emptyMap());
            return type.cast(valueMap);
        } else if (type == Principal.class) {
            return type.cast(principal);
        }

        return super.adaptTo(type);
    }

    public String toString() {
        String id = null;
        if (principal != null) {
            id = principal.getName();
        }
        return getClass().getSimpleName() + ", id=" + id + ", path="
            + getPath();
    }
}
