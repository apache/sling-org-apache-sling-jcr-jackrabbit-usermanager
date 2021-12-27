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

import java.util.Iterator;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.jetbrains.annotations.NotNull;

/**
 * ValueMap implementation for nested properties of Authorizable Resources
 */
public class NestedAuthorizableValueMap extends BaseAuthorizableValueMap {
    private final String relPropPath;

    public NestedAuthorizableValueMap(Authorizable authorizable, SystemUserManagerPaths systemUserManagerPaths,
            @NotNull String relPropPath) {
        super(authorizable, systemUserManagerPaths);
        this.relPropPath = relPropPath;
    }

    @Override
    protected Object read(String key) {
        Object value = null;
        // if the item has been completely read, we need not check
        // again, as we certainly will not find the key
        if (!fullyRead) {
            try {
                // prepend the relPath to the key
                String relPropKey = String.format("%s/%s", relPropPath, key);
                if (authorizable.hasProperty(relPropKey)) {
                    value = readPropertyAndCache(key, relPropKey);
                } else {
                    // property not found or some error accessing it
                }
            } catch (RepositoryException re) {
                log.error("Could not access authorizable property", re);
            }
        }

        return value;
    }

    @Override
    protected void readFully() {
        if (!fullyRead) {
            try {
                Iterator<String> pi = AuthorizableResourceProvider.getPropertyNames(relPropPath, authorizable);
                while (pi.hasNext()) {
                    String key = pi.next();
                    if (!cache.containsKey(key)) {
                        readPropertyAndCache(key, String.format("%s/%s", relPropPath, key));
                    }
                }

                fullyRead = true;
            } catch (RepositoryException re) {
                log.error("Could not access certain properties of user {}", authorizable, re);
            }
        }
    }

    @Override
    protected <T> T convertToType(String name, Class<T> type) {
        return super.convertToType(String.format("%s/%s", relPropPath, name), type);
    }

}
