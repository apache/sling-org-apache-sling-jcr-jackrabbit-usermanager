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
package org.apache.sling.jcr.jackrabbit.usermanager.it;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo;
import org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo.PropertyUpdateTypes;
import org.apache.sling.jackrabbit.usermanager.ChangeUserPassword;
import org.apache.sling.jackrabbit.usermanager.CreateGroup;
import org.apache.sling.jackrabbit.usermanager.CreateUser;
import org.apache.sling.jackrabbit.usermanager.DeleteGroup;
import org.apache.sling.jackrabbit.usermanager.DeleteUser;
import org.apache.sling.jackrabbit.usermanager.UpdateGroup;
import org.apache.sling.jackrabbit.usermanager.UpdateUser;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic test of AuthorizablePrivilegesInfo component
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class AuthorizablePrivilegesInfoIT extends UserManagerTestSupport {
    private static AtomicLong counter = new AtomicLong(0);
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    protected BundleContext bundleContext;

    @Inject
    protected SlingRepository repository;

    @Inject
    protected ConfigurationAdmin configAdmin;

    @Inject
    private AuthorizablePrivilegesInfo privilegesInfo;

    @Inject
    private UserConfiguration userConfig;

    @Inject
    private CreateUser createUser;

    @Inject
    private CreateGroup createGroup;

    @Inject
    private UpdateUser updateUser;

    @Inject
    private UpdateGroup updateGroup;

    @Inject
    private DeleteUser deleteUser;

    @Inject
    private DeleteGroup deleteGroup;

    @Inject
    private ChangeUserPassword changeUserPassword;

    @Rule
    public TestName testName = new TestName();

    protected Session adminSession;
    protected User user1;
    protected Session user1Session;

    @Before
    public void setup() throws RepositoryException {
        adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        assertNotNull("Expected adminSession to not be null", adminSession);

        user1 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                Collections.emptyMap(), new ArrayList<>());
        assertNotNull("Expected user1 to not be null", user1);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }

        user1Session = repository.login(new SimpleCredentials(user1.getID(), "testPwd".toCharArray()));
        assertNotNull("Expected user1Session to not be null", user1Session);
    }

    @After
    public void teardown() {
        try {
            adminSession.refresh(false);
            if (user1 != null) {
                deleteUser.deleteUser(adminSession, user1.getID(), new ArrayList<>());
            }

            if (adminSession.hasPendingChanges()) {
                adminSession.save();
            }
        } catch (RepositoryException e) {
            logger.warn(String.format("Failed to delete user: %s", e.getMessage()), e);
        }

        user1Session.logout();
        adminSession.logout();
    }

    protected String createUniqueName(String prefix) {
        return String.format("%s_%s%d", prefix, testName.getMethodName(), counter.incrementAndGet());
    }

    /**
     * Checks whether the current user has been granted privileges
     * to add a new user.  Equivalent of: #canAddUser(Session, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty)
     */
    @Test
    public void canAddUser() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        User user2 = null;
        try {
            // initially user can't do the operations
            assertFalse("Should not be allowed to add user",
                    privilegesInfo.canAddUser(user1Session));

            String usersPath = userConfig.getParameters().getConfigValue("usersPath", null, String.class);
            assertNotNull("Users Path should not be null", usersPath);
            assertTrue("Users Path should exist",
                    adminSession.itemExists(usersPath));

            // grant user1 rights
            AceTools.modifyAce(adminSession, usersPath, user1, Set.of(
                        Privilege.JCR_READ,
                        Privilege.JCR_READ_ACCESS_CONTROL,
                        Privilege.JCR_MODIFY_ACCESS_CONTROL,
                        PrivilegeConstants.REP_WRITE,
                        PrivilegeConstants.REP_USER_MANAGEMENT
                    ), null);
            assertTrue("Should be allowed to add user",
                    privilegesInfo.canAddUser(user1Session));

            // verify that the user can actually add the user
            try {
                Map<String, String> propMap = new HashMap<>();
                propMap.put("prop1", "value1");
                propMap.put("nested/prop2", "value2");
                user2 = createUser.createUser(user1Session, createUniqueName("user"), "testPwd", "testPwd",
                        propMap, new ArrayList<>());
                assertNotNull("Expected user2 to not be null", user2);
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when adding user: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when adding user: " + e.getMessage());
            }
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * Checks whether the current user has been granted privileges
     * to add a new group.  Equivalent of: #canAddGroup(Session, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty)
     */
    @Test
    public void canAddGroup() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        workaroundMissingGroupsPath();

        Group group1 = null;
        try {
            // initially user can't do the operations
            assertFalse("Should not be allowed to add group",
                    privilegesInfo.canAddGroup(user1Session));

            String groupsPath = userConfig.getParameters().getConfigValue("groupsPath", null, String.class);
            assertNotNull("Groups Path should not be null", groupsPath);
            assertTrue("Groups Path should exist",
                    adminSession.itemExists(groupsPath));

            // grant user1 rights
            AceTools.modifyAce(adminSession, groupsPath, user1, Set.of(
                        Privilege.JCR_READ,
                        Privilege.JCR_READ_ACCESS_CONTROL,
                        Privilege.JCR_MODIFY_ACCESS_CONTROL,
                        PrivilegeConstants.REP_WRITE,
                        PrivilegeConstants.REP_USER_MANAGEMENT
                    ), null);
            assertTrue("Should be allowed to add group",
                    privilegesInfo.canAddGroup(user1Session));

            // verify that the user can actually add the user
            try {
                Map<String, String> propMap = new HashMap<>();
                propMap.put("prop1", "value1");
                propMap.put("nested/prop2", "value2");
                group1 = createGroup.createGroup(user1Session, createUniqueName("group"),
                        propMap, new ArrayList<>());
                assertNotNull("Expected group1 to not be null", group1);
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when adding group: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when adding group: " + e.getMessage());
            }
        } finally {
            if (group1 != null) {
                deleteGroup.deleteGroup(user1Session, group1.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * The oak groupsPath appears to be missing until the first group is created.
     * So create a group as the admin user to get it bootstrapped. This makes it
     * possible for non-admin users to create groups without requiring extra access
     * rights to the intermediate parents of the groupsPath folder.
     */
    protected void workaroundMissingGroupsPath() throws RepositoryException {
        String groupsPath = userConfig.getParameters().getConfigValue("groupsPath", null, String.class);
        assertNotNull("Groups Path should not be null", groupsPath);
        if (!adminSession.itemExists(groupsPath)) {
            // create a group and the remove it
            Group tempGroup = createGroup.createGroup(adminSession, createUniqueName("group"),
                    Collections.emptyMap(), new ArrayList<>());
            deleteGroup.deleteGroup(adminSession, tempGroup.getID(), new ArrayList<>());
        }
        assertTrue("Groups Path should exist",
                adminSession.itemExists(groupsPath));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user or group.
     */
    @Test
    public void canUpdatePropertiesWithGrantedRead() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        workaroundMissingGroupsPath();

        User user2 = null;
        Group group1 = null;

        try {
            // create a couple of test users
            user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected user2 to not be null", user2);

            group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected group1 to not be null", group1);

            String [] principalIds = new String[] { user2.getID(), group1.getID() };

            // initially user can't do the operation
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
            }

            // start with only read rights
            AceTools.modifyAce(adminSession, user2.getPath(), user1, Set.of(Privilege.JCR_READ), null);
            AceTools.modifyAce(adminSession, group1.getPath(), user1, Set.of(Privilege.JCR_READ), null);
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.REMOVE_PROPERTY));
            }
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
            if (group1 != null) {
                deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user or group.
     */
    @Test
    public void canUpdatePropertiesWithGrantedRemoveProperties() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        workaroundMissingGroupsPath();

        User user2 = null;
        Group group1 = null;

        try {
            // create a couple of test users
            user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected user2 to not be null", user2);

            group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected group1 to not be null", group1);

            String [] principalIds = new String[] { user2.getID(), group1.getID() };

            // initially user can't do the operation
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
            }

            // start with only read rights
            Set<String> grantedPrivilegeNames = Set.of(
                        Privilege.JCR_READ,
                        // + grant rights to only remove properties
                        PrivilegeConstants.REP_REMOVE_PROPERTIES
                    );
            AceTools.modifyAce(adminSession, user2.getPath(), user1, grantedPrivilegeNames, null);
            AceTools.modifyAce(adminSession, group1.getPath(), user1, grantedPrivilegeNames, null);
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.REMOVE_PROPERTY));
            }

            // verify that the user can actually delete property
            Map<String, Object> propsMap = new HashMap<>();
            propsMap.put("prop1@Delete", "value1");
            try {
                updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
                updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
                assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
                user1Session.save();
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when deleting property: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when deleting property: " + e.getMessage());
            }
            // verify that the user can not add nested property
            propsMap = new HashMap<>();
            propsMap.put("nested/prop2", "value2");
            updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
            updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
            assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
            try {
                user1Session.save();
                fail("Expected AccessDeniedException when adding nested property");
            } catch (AccessDeniedException e) {
                // expected
                user1Session.refresh(false);
            }
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
            if (group1 != null) {
                deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user or group.
     */
    @Test
    public void canUpdatePropertiesWithGrantedAlterNonNestedProperties() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        workaroundMissingGroupsPath();

        User user2 = null;
        Group group1 = null;

        try {
            // create a couple of test users
            user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected user2 to not be null", user2);

            group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected group1 to not be null", group1);

            String [] principalIds = new String[] { user2.getID(), group1.getID() };

            // initially user can't do the operation
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
            }

            // start with only read rights
            Set<String> grantedPrivilegeNames = Set.of(
                        Privilege.JCR_READ,
                        // + grant rights to only alter (non-nested) properties
                        PrivilegeConstants.REP_ADD_PROPERTIES
                    );
            AceTools.modifyAce(adminSession, user2.getPath(), user1, grantedPrivilegeNames, null);
            AceTools.modifyAce(adminSession, group1.getPath(), user1, grantedPrivilegeNames, null);
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.REMOVE_PROPERTY));
            }

            // verify that the user can actually add property
            Map<String, Object> propsMap = new HashMap<>();
            propsMap.put("prop1", "value1");
            try {
                updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
                updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
                assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
                user1Session.save();
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when adding property: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when adding property: " + e.getMessage());
            }
            // verify that the user can not add nested property
            propsMap.put("nested/prop2", "value2");
            updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
            updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
            assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
            try {
                user1Session.save();
                fail("Expected AccessDeniedException when adding nested property");
            } catch (AccessDeniedException e) {
                // expected
                user1Session.refresh(false);
            }
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
            if (group1 != null) {
                deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user or group.
     */
    @Test
    public void canUpdatePropertiesWithGrantedAlterProperties() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        workaroundMissingGroupsPath();

        User user2 = null;
        Group group1 = null;

        try {
            // create a couple of test users
            user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected user2 to not be null", user2);

            group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected group1 to not be null", group1);

            String [] principalIds = new String[] { user2.getID(), group1.getID() };

            // initially user can't do the operation
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
            }

            // start with only read rights
            Set<String> grantedPrivilegeNames = Set.of(
                        Privilege.JCR_READ,
                        // + grant rights to alter (non-nested or nested) properties
                        PrivilegeConstants.REP_ADD_PROPERTIES,
                        PrivilegeConstants.REP_ALTER_PROPERTIES,
                        Privilege.JCR_ADD_CHILD_NODES
                    );
            AceTools.modifyAce(adminSession, user2.getPath(), user1, grantedPrivilegeNames, null);
            AceTools.modifyAce(adminSession, group1.getPath(), user1, grantedPrivilegeNames, null);
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.REMOVE_PROPERTY));
            }

            // verify that the user can actually add property and nested property
            Map<String, Object> propsMap = new HashMap<>();
            propsMap.put("prop1", "value1");
            propsMap.put("nested/prop2", "value2");
            try {
                updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
                updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
                assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
                user1Session.save();
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when adding properties: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when adding properties: " + e.getMessage());
            }
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
            if (group1 != null) {
                deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user or group.
     */
    @Test
    public void canUpdatePropertiesWithGrantedAlterAndRemoveProperties() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        workaroundMissingGroupsPath();

        User user2 = null;
        Group group1 = null;

        try {
            // create a couple of test users
            user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected user2 to not be null", user2);

            group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected group1 to not be null", group1);

            String [] principalIds = new String[] { user2.getID(), group1.getID() };

            // initially user can't do the operation
            for (String pid : principalIds) {
                assertFalse("Should not be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
            }

            // start with only read rights
            Set<String> grantedPrivilegeNames = Set.of(
                        Privilege.JCR_READ,
                        // + grant rights to alter (non-nested or nested) properties and remove properties
                        PrivilegeConstants.REP_ADD_PROPERTIES,
                        PrivilegeConstants.REP_ALTER_PROPERTIES,
                        Privilege.JCR_ADD_CHILD_NODES,
                        PrivilegeConstants.REP_REMOVE_PROPERTIES
                    );
            AceTools.modifyAce(adminSession, user2.getPath(), user1, grantedPrivilegeNames, null);
            AceTools.modifyAce(adminSession, group1.getPath(), user1, grantedPrivilegeNames, null);
            for (String pid : principalIds) {
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.REMOVE_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.ADD_NESTED_PROPERTY));
                assertTrue("Should be allowed to update properties for: " + pid,
                        privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.REMOVE_PROPERTY));
            }

            // verify that the user can actually add property and nested property
            Map<String, Object> propsMap = new HashMap<>();
            propsMap.put("prop3", "value3");
            propsMap.put("nested/prop4", "value4");
            propsMap.put("prop1@Delete", "value1");
            propsMap.put("nested/prop2@Delete", "value1");
            try {
                updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
                updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
                assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
                user1Session.save();
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when adding or deleting properties: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when adding or deleting properties: " + e.getMessage());
            }
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
            if (group1 != null) {
                deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * Checks whether the current user has been granted privileges
     * to remove the specified user or group.
     */
    @Test
    public void canRemove() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        workaroundMissingGroupsPath();

        User user2 = null;
        Group group1 = null;
        try {
            user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected user2 to not be null", user2);

            group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected group1 to not be null", group1);

            // initially user can't do the operations
            assertFalse("Should not be allowed to remove user",
                    privilegesInfo.canRemove(user1Session, user2.getID()));
            assertFalse("Should not be allowed to remove group",
                    privilegesInfo.canRemove(user1Session, group1.getID()));

            // grant user1 rights to user2 profile
            AceTools.modifyAce(adminSession, user2.getPath(), user1, Set.of(Privilege.JCR_READ), null);
            AceTools.modifyAce(adminSession, group1.getPath(), user1, Set.of(Privilege.JCR_READ), null);
            assertFalse("Should not be allowed to remove user",
                    privilegesInfo.canRemove(user1Session, user2.getID()));
            assertFalse("Should not be allowed to remove group",
                    privilegesInfo.canRemove(user1Session, group1.getID()));

            AceTools.modifyAce(adminSession, user2.getPath(), user1, Set.of(
                    Privilege.JCR_READ,
                    PrivilegeConstants.REP_USER_MANAGEMENT
                    ), null);
            AceTools.modifyAce(adminSession, group1.getPath(), user1, Set.of(
                    Privilege.JCR_READ,
                    PrivilegeConstants.REP_USER_MANAGEMENT
                    ), null);
            AceTools.modifyAce(adminSession, group1.getPath(), user1, Set.of(Privilege.JCR_READ), null);
            assertTrue("Should be allowed to remove user",
                    privilegesInfo.canRemove(user1Session, user2.getID()));
            assertTrue("Should be allowed to remove group",
                    privilegesInfo.canRemove(user1Session, group1.getID()));

            // verify that the user can actually delete the user
            String user2Id = user2.getID();
            deleteUser.deleteUser(user1Session, user2Id, new ArrayList<>());
            user2 = null;
            // verify the user is no longer there
            UserManager um = ((JackrabbitSession)user1Session).getUserManager();
            assertNull("Expected user to be gone: " + user2Id, um.getAuthorizable(user2Id));

            // verify that the user can actually delete the group
            String group1Id = group1.getID();
            deleteGroup.deleteGroup(user1Session, group1Id, new ArrayList<>());
            group1 = null;
            assertNull("Expected group to be gone: " + group1Id, um.getAuthorizable(group1Id));
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
            if (group1 != null) {
                deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the membership of the specified group.
     */
    @Test
    public void canUpdateGroupMembers() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        workaroundMissingGroupsPath();

        Group group1 = null;
        try {
            group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected group1 to not be null", group1);

            // initially user can't do the operations
            assertFalse("Should not be allowed to update group members",
                    privilegesInfo.canUpdateGroupMembers(user1Session, group1.getID()));

            // grant user1 rights to group1 profile
            AceTools.modifyAce(adminSession, group1.getPath(), user1, Set.of(Privilege.JCR_READ), null);
            assertFalse("Should not be allowed to update group members",
                    privilegesInfo.canUpdateGroupMembers(user1Session, group1.getID()));

            AceTools.modifyAce(adminSession, group1.getPath(), user1, Set.of(
                    Privilege.JCR_READ,
                    PrivilegeConstants.REP_USER_MANAGEMENT
                    ), null);
            assertTrue("Should be allowed to update group members",
                    privilegesInfo.canUpdateGroupMembers(user1Session, group1.getID()));

            // verify that the user can actually change the group members
            try {
                Map<String, Object> propsMap = new HashMap<>();
                propsMap.put(":member", user1.getID());
                updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
                assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
                user1Session.save();
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when adding member to group: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when adding member to group: " + e.getMessage());
            }
        } finally {
            if (group1 != null) {
                deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
            }
        }
    }

    protected void configMinimumUserPrivileges(User user) throws RepositoryException {
        //change the ACE for the user home folder to the minimum privileges
        // and without rep:userManagement
        AceTools.deleteAces(adminSession, user.getPath(), user);
        AceTools.modifyAce(adminSession, user.getPath(), user, Set.of(
                    Privilege.JCR_READ,
                    PrivilegeConstants.REP_ALTER_PROPERTIES
                ), null);
    }

    /**
     * SLING-9814 Checks whether the current user has been granted privileges
     * to disable a user.
     */
    @Test
    public void canDisableUser() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        User user2 = null;
        try {
            // setup the user privileges
            configMinimumUserPrivileges(user1);

            // create another test user
            user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected user2 to not be null", user2);
            // setup the user privileges
            configMinimumUserPrivileges(user2);

            // initially user can't do the operations
            assertFalse("Should not be allowed to disable yourself",
                    privilegesInfo.canDisable(user1Session, user1.getID()));
            assertFalse("Should not be allowed to disable user",
                    privilegesInfo.canDisable(user1Session, user2.getID()));

            // grant user1 rights to user2 profile
            AceTools.modifyAce(adminSession, user2.getPath(), user1, Set.of(
                        Privilege.JCR_READ,
                        PrivilegeConstants.REP_USER_MANAGEMENT
                    ), null);
            assertTrue("Should be allowed to disable user",
                    privilegesInfo.canDisable(user1Session, user2.getID()));

            // verify that the user can actually disable the other user
            Map<String, Object> propsMap = new HashMap<>();
            propsMap.put(":disabled", "true");
            propsMap.put(":disabledReason", "Just a test");
            try {
                updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
                assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
                user1Session.save();
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when disabling the user: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when disabling the user: " + e.getMessage());
            }
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * SLING-9814 Checks whether the current user has been granted privileges
     * to change a user's password.
     */
    @Test
    public void canChangePassword() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        User user2 = null;
        try {
            // create another test user
            user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                    Collections.singletonMap("prop1", "value1"), new ArrayList<>());
            assertNotNull("Expected user2 to not be null", user2);

            // initially user can't do the operations
            assertFalse("Should not be allowed to change the user password",
                    privilegesInfo.canChangePassword(user1Session, user2.getID()));

            // grant user1 rights to user2 profile
            AceTools.modifyAce(adminSession, user2.getPath(), user1, Set.of(
                        Privilege.JCR_READ,
                        PrivilegeConstants.REP_USER_MANAGEMENT
                    ), null);
            assertTrue("Should be allowed to change the user password user",
                    privilegesInfo.canChangePassword(user1Session, user2.getID()));

            // verify that the user can actually change the password of the other user
            try {
                changeUserPassword.changePassword(user1Session, user2.getID(), "testPwd", "newPassword", "newPassword", new ArrayList<>());
                assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
                user1Session.save();
            } catch (RepositoryException e) {
                logger.error(String.format("Did not expect RepositoryException when changing the user password: %s", e.getMessage()), e);
                fail("Did not expect RepositoryException when changing the user password: " + e.getMessage());
            }
        } finally {
            if (user2 != null) {
                deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
            }
        }
    }

    /**
     * Checks whether the current user can change their own password
     */
    @Test
    public void canChangePasswordForSelf() throws RepositoryException {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        assertTrue("Should be allowed to change the user password",
                privilegesInfo.canChangePassword(user1Session, user1.getID()));
    }

    /**
     * Checks whether the current user can change their own password using the obsolete configuration key
     */
    @Test
    public void canChangePasswordForSelfWithObsoleteConfigurationKey() throws RepositoryException, IOException {
        // deny user1 rights to their own profile
        AceTools.modifyAce(adminSession, user1.getPath(), user1, null, Set.of(PrivilegeConstants.REP_USER_MANAGEMENT));

        // first try with the allowSelfChangePassword set to false
        org.osgi.service.cm.Configuration configuration = configAdmin.getConfiguration("org.apache.sling.jackrabbit.usermanager.impl.post.ChangeUserPasswordServlet", null);
        Dictionary<String, Object> originalServiceProps = configuration.getProperties();
        ServiceReference<ChangeUserPassword> serviceReference = null;
        try {
            // update the service configuration to ensure the option is disabled
            Dictionary<String, Object> newServiceProps = replaceConfigProp(originalServiceProps, "alwaysAllowSelfChangePassword", Boolean.FALSE);
            configuration.update(newServiceProps);
            new WaitForServiceUpdated(5000, 100, bundleContext, ChangeUserPassword.class,
                    "alwaysAllowSelfChangePassword", Boolean.FALSE);

            assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

            assertFalse("Should be not allowed to change the user password",
                    privilegesInfo.canChangePassword(user1Session, user1.getID()));
        } finally {
            if (serviceReference != null) {
                // done with this.
                bundleContext.ungetService(serviceReference);
            }

            //put the original config back
            configuration.update(originalServiceProps);
            new WaitForServiceUpdated(5000, 100, bundleContext, ChangeUserPassword.class, "alwaysAllowSelfChangePassword",
                    originalServiceProps == null ? null :originalServiceProps.get("alwaysAllowSelfChangePassword"));
        }

        // second try with the allowSelfChangePassword set to true
        configuration = configAdmin.getConfiguration("org.apache.sling.jackrabbit.usermanager.impl.post.ChangeUserPasswordServlet", null);
        originalServiceProps = configuration.getProperties();
        serviceReference = null;
        try {
            // update the service configuration to ensure the option is disabled
            Dictionary<String, Object> newServiceProps = replaceConfigProp(originalServiceProps, "alwaysAllowSelfChangePassword", Boolean.TRUE);
            configuration.update(newServiceProps);
            new WaitForServiceUpdated(5000, 100, bundleContext, ChangeUserPassword.class,
                    "alwaysAllowSelfChangePassword", Boolean.TRUE);

            assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

            assertTrue("Should be allowed to change the user password",
                    privilegesInfo.canChangePassword(user1Session, user1.getID()));
        } finally {
            if (serviceReference != null) {
                // done with this.
                bundleContext.ungetService(serviceReference);
            }

            //put the original config back
            configuration.update(originalServiceProps);
            new WaitForServiceUpdated(5000, 100, bundleContext, ChangeUserPassword.class, "alwaysAllowSelfChangePassword",
                    originalServiceProps == null ? null :originalServiceProps.get("alwaysAllowSelfChangePassword"));
        }
    }

    /**
     * SLING-9814 Checks whether the current user has been granted privileges
     * to change the anonymous user's password.
     */
    @Test
    public void cannotChangePasswordForAnonymous() {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        // anonymous user has no password to change
        assertFalse("Should not be allowed to change the user password",
                privilegesInfo.canChangePassword(user1Session, "anonymous"));
        assertFalse("Should not be allowed to change the user password",
                privilegesInfo.canChangePassword(adminSession, "anonymous"));
    }

    /**
     * SLING-9814 Checks whether the current user has been granted privileges
     * to change the anonymous user's password.
     */
    @Test
    public void cannotChangePasswordForServiceUser() {
        assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

        // service user has no password to change
        assertFalse("Should not be allowed to change the user password",
                privilegesInfo.canChangePassword(user1Session, "sling-jcr-usermanager"));
        assertFalse("Should not be allowed to change the user password",
                privilegesInfo.canChangePassword(adminSession, "sling-jcr-usermanager"));
    }

    /**
     * Tests for SLING-12202
     */
    @Test
    public void canChangePasswordWithoutOldPasswordForSelf() throws RepositoryException {
        assertFalse(privilegesInfo.canChangePasswordWithoutOldPassword(adminSession, "admin"));
    }
    @Test
    public void canChangePasswordWithoutOldPasswordForAdminUser() throws RepositoryException {
        assertTrue(privilegesInfo.canChangePasswordWithoutOldPassword(adminSession, user1.getID()));
    }
    @Test
    public void canChangePasswordWithoutOldPasswordForAnonymousUser() throws RepositoryException {
        assertFalse(privilegesInfo.canChangePasswordWithoutOldPassword(adminSession, "anonymous"));
    }
    @Test
    public void canChangePasswordWithoutOldPasswordForServiceUser() throws RepositoryException {
        User systemuser1 = ((JackrabbitSession)adminSession).getUserManager().createSystemUser("systemuser1", null);
        assertFalse(privilegesInfo.canChangePasswordWithoutOldPassword(adminSession, systemuser1.getID()));
    }
    @Test
    public void canChangePasswordWithoutOldPasswordForUserAdminGroupMember() throws RepositoryException {
        User testuser2 = ((JackrabbitSession)adminSession).getUserManager().createUser("testuser2", "testPwd");
        Group userAdmin = createGroup.createGroup(adminSession, "UserAdmin", new HashMap<>(), new ArrayList<>());
        // grant user1 rights to user2 profile
        AceTools.modifyAce(adminSession, userAdmin.getPath(), user1, Set.of(Privilege.JCR_READ), null);
        AceTools.modifyAce(adminSession, testuser2.getPath(), user1, Set.of(Privilege.JCR_READ), null);
        adminSession.save();
        user1Session.refresh(true);
        assertFalse(privilegesInfo.canChangePasswordWithoutOldPassword(user1Session, testuser2.getID()));

        userAdmin.addMember(user1);
        adminSession.save();
        user1Session.refresh(true);
        assertTrue(privilegesInfo.canChangePasswordWithoutOldPassword(user1Session, testuser2.getID()));
    }

}
