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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Group;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ValueMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * Basic test of AuthorizableValueMap
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class AuthorizableValueMapIT extends BaseAuthorizableValueMapIT {

    protected Group group2;

    @Override
    public void setup() throws RepositoryException, LoginException {
        super.setup();

        Map<String, Object> groupProps = new HashMap<>();
        groupProps.put(":member", group1.getID());
        group2 = createGroup.createGroup(adminSession, createUniqueName("group"),
                groupProps, new ArrayList<>());
        assertNotNull("Expected group2 to not be null", group2);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }

    }

    @Override
    public void teardown() {
        try {
            adminSession.refresh(false);
            if (group2 != null) {
                deleteGroup.deleteGroup(adminSession, group2.getID(), new ArrayList<>());
            }

            if (adminSession.hasPendingChanges()) {
                adminSession.save();
            }
        } catch (RepositoryException e) {
            logger.warn(String.format("Failed to delete group: %s", e.getMessage()), e);
        }

        super.teardown();
    }

    @Test
    @Override
    public void testSize() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        int size = vm.size();
        assertEquals(30, size);

        ValueMap vm2 = getValueMap(group1);
        int size2 = vm2.size();
        assertEquals(32, size2);
    }

    @Test
    public void testUserPath() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(user1);
        String path = vm.get("path", String.class);
        assertEquals(user1.getPath(), path);
    }

    @Test
    public void testGroupPath() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(group1);
        String path = vm.get("path", String.class);
        assertEquals(group1.getPath(), path);
    }

    @Test
    public void testUserMemberOf() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(user1);
        String[] memberOf = vm.get("memberOf", String[].class);
        assertNotNull(memberOf);
        assertArrayEquals(new String[] { String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()), String.format("%s%s", userManagerPaths.getGroupPrefix(), group2.getID()) }, memberOf);
    }

    @Test
    public void testUserDeclaredMemberOf() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(user1);
        String[] declaredMemberOf = vm.get("declaredMemberOf", String[].class);
        assertNotNull(declaredMemberOf);
        assertArrayEquals(new String[] { String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()) }, declaredMemberOf);
    }

    @Test
    public void testUserMembers() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(user1);
        assertNull(vm.get("members", String[].class));
    }

    @Test
    public void testUserDeclaredMembers() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(user1);
        assertNull(vm.get("declaredMembers", String[].class));
    }

    @Test
    public void testGroupMemberOf() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(group1);
        String[] memberOf = vm.get("memberOf", String[].class);
        assertNotNull(memberOf);
        assertArrayEquals(new String[] { String.format("%s%s", userManagerPaths.getGroupPrefix(), group2.getID()) }, memberOf);

        ValueMap vm2 = getValueMap(group2);
        String[] memberOf2 = vm2.get("memberOf", String[].class);
        assertNotNull(memberOf2);
        assertArrayEquals(new String[0], memberOf2);
    }

    @Test
    public void testGroupDeclaredMemberOf() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(group1);
        String[] declaredMemberOf = vm.get("declaredMemberOf", String[].class);
        assertNotNull(declaredMemberOf);
        assertArrayEquals(new String[] { String.format("%s%s", userManagerPaths.getGroupPrefix(), group2.getID())}, declaredMemberOf);

        ValueMap vm2 = getValueMap(group2);
        String[] declaredMemberOf2 = vm2.get("declaredMemberOf", String[].class);
        assertNotNull(declaredMemberOf2);
        assertArrayEquals(new String[0], declaredMemberOf2);
    }

    @Test
    public void testGroupMembers() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(group1);
        String[] members = vm.get("members", String[].class);
        assertNotNull(members);
        assertArrayEquals(new String[] { String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()) }, members);

        ValueMap vm2 = getValueMap(group2);
        String[] members2 = vm2.get("members", String[].class);
        assertNotNull(members2);
        assertArrayEquals(new String[] { String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()), String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()) }, members2);
    }

    @Test
    public void testGroupDeclaredMembers() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(group1);
        String[] declaredMembers = vm.get("declaredMembers", String[].class);
        assertNotNull(declaredMembers);
        assertArrayEquals(new String[] { String.format("%s%s", userManagerPaths.getUserPrefix(), user1.getID()) }, declaredMembers);

        ValueMap vm2 = getValueMap(group2);
        String[] declaredMembers2 = vm2.get("declaredMembers", String[].class);
        assertNotNull(declaredMembers2);
        assertArrayEquals(new String[] { String.format("%s%s", userManagerPaths.getGroupPrefix(), group1.getID()) }, declaredMembers2);
    }

}
