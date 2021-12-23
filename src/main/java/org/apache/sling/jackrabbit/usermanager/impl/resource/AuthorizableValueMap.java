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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;

/**
 * ValueMap implementation for the root property container of Authorizable Resources
 */
public class AuthorizableValueMap extends BaseAuthorizableValueMap {
    private static final String DECLARED_MEMBERS_KEY = "declaredMembers";
    private static final String MEMBERS_KEY = "members";
    private static final String DECLARED_MEMBER_OF_KEY = "declaredMemberOf";
    private static final String MEMBER_OF_KEY = "memberOf";
    private static final String PATH_KEY = "path";

    public AuthorizableValueMap(Authorizable authorizable, SystemUserManagerPaths systemUserManagerPaths) {
        super(authorizable, systemUserManagerPaths);
    }

    @Override
    protected Object read(String key) {
        // if the item has been completely read, we need not check
        // again, as we certainly will not find the key
        if (fullyRead) {
            return null;
        }

        try {
            if (key.equals(MEMBERS_KEY) && authorizable.isGroup()) {
                return getMembers((Group) authorizable, true);
            }
            if (key.equals(DECLARED_MEMBERS_KEY) && authorizable.isGroup()) {
                return getMembers((Group) authorizable, false);
            }
            if (key.equals(MEMBER_OF_KEY)) {
                return getMemberships(true);
            }
            if (key.equals(DECLARED_MEMBER_OF_KEY)) {
                return getMemberships(false);
            }
            if (key.equals(PATH_KEY)) {
                return getPath();
            }
            if (authorizable.hasProperty(key)) {
                final Value[] property = authorizable.getProperty(key);
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
                if (authorizable.isGroup()) {
                    cache.put(MEMBERS_KEY, getMembers((Group) authorizable, true));
                    cache.put(DECLARED_MEMBERS_KEY, getMembers((Group) authorizable, false));
                }
                cache.put(MEMBER_OF_KEY, getMemberships(true));
                cache.put(DECLARED_MEMBER_OF_KEY, getMemberships(false));

                String path = getPath();
                if (path != null) {
                    cache.put(PATH_KEY, path);
                }

                // only direct property
                Iterator<String> pi = authorizable.getPropertyNames();
                while (pi.hasNext()) {
                    String key = pi.next();
                    if (!cache.containsKey(key)) {
                        Value[] property = authorizable.getProperty(key);
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

    private String[] getMembers(Group group, boolean includeAll) throws RepositoryException {
        List<String> results = new ArrayList<>();
        for (Iterator<Authorizable> it = includeAll ? group.getMembers() : group.getDeclaredMembers();
                it.hasNext();) {
            Authorizable auth = it.next();
            if (auth.isGroup()) {
                results.add(systemUserManagerPaths.getGroupPrefix() + auth.getID());
            } else {
                results.add(systemUserManagerPaths.getUserPrefix() + auth.getID());
            }
        }
        return results.toArray(new String[results.size()]);
    }

    private String[] getMemberships(boolean includeAll) throws RepositoryException {
        List<String> results = new ArrayList<>();
        for (Iterator<Group> it = includeAll ? authorizable.memberOf() : authorizable.declaredMemberOf();
                it.hasNext();) {
            Group group = it.next();
            results.add(systemUserManagerPaths.getGroupPrefix() + group.getID());
        }
        return results.toArray(new String[results.size()]);
    }

    private String getPath() throws RepositoryException {
        try {
            return authorizable.getPath();
        } catch (UnsupportedRepositoryOperationException e) {
            log.debug("Could not retrieve path of authorizable {}", authorizable, e);
            return null;
        }
    }
}
