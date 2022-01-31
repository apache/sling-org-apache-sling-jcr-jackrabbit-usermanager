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

import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;

/**
 * Base Resource implementation for the common parts
 */
public abstract class BaseResource extends AbstractResource {
    protected final ResourceResolver resourceResolver;
    private final String path;
    private final ResourceMetadata metadata;

    protected BaseResource(ResourceResolver resourceResolver, String path) {
        super();

        this.resourceResolver = resourceResolver;
        this.path = path;

        this.metadata = new ResourceMetadata();
        metadata.setResolutionPath(path);
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

}
