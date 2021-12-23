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

import java.util.Collections;
import java.util.Iterator;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

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
        // if the item has been completely read, we need not check
        // again, as we certainly will not find the key
        if (fullyRead) {
            return null;
        }

        try {
            // prepend the relPath to the key
            String relPropKey = String.format("%s/%s", relPropPath, key);
            if (authorizable.hasProperty(relPropKey)) {
                final Value[] property = authorizable.getProperty(relPropKey);
                final Object value = valuesToJavaObject(property);
                cache.put(key, value);
                return value;
            }
        } catch (RepositoryException re) {
            log.error("Could not access authorizable property", re);
        }

        // property not found or some error accessing it
        return null;
    }

    @Override
    protected void readFully() {
        if (!fullyRead) {
            try {
                Iterator<String> pi;
                try {
                    // TODO: there isn't any way to check if relPath is valid
                    //    as this call throws an exception instead of returning null
                    //    or an empty iterator.
                    pi = authorizable.getPropertyNames(relPropPath);
                } catch (RepositoryException re) {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to get property names", re);
                    }
                    pi = Collections.emptyIterator();
                }
                while (pi.hasNext()) {
                    String key = pi.next();
                    if (!cache.containsKey(key)) {
                        Value [] property = authorizable.getProperty(String.format("%s/%s", relPropPath, key));
                        Object value = valuesToJavaObject(property);
                        cache.put(key, value);
                    }
                }

                fullyRead = true;
            } catch (RepositoryException re) {
                log.error("Could not access certain properties of user {}", authorizable, re);
            }
        }
    }

}
