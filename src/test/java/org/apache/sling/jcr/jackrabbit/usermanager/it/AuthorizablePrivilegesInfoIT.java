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
import static org.ops4j.pax.exam.CoreOptions.options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo;
import org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo.PropertyUpdateTypes;
import org.apache.sling.jackrabbit.usermanager.CreateGroup;
import org.apache.sling.jackrabbit.usermanager.CreateUser;
import org.apache.sling.jackrabbit.usermanager.DeleteGroup;
import org.apache.sling.jackrabbit.usermanager.DeleteUser;
import org.apache.sling.jackrabbit.usermanager.UpdateGroup;
import org.apache.sling.jackrabbit.usermanager.UpdateUser;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.apache.sling.jcr.jackrabbit.accessmanager.ModifyAce;
import org.apache.sling.servlets.post.Modification;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
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
	private ModifyAce modifyAce;

	@Rule
	public TestName testName = new TestName();

    protected Session adminSession;
    protected User user1;
    protected Session user1Session;

	@Configuration
	public Option[] configuration() {
	    return options(
	        baseConfiguration()
	    );
	}

    @Before
    public void setup() throws Exception {
        adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
		assertNotNull("Expected adminSession to not be null", adminSession);

		user1 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd", 
				Collections.emptyMap(), new ArrayList<Modification>());
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
			logger.warn("Failed to delete user: " + e.getMessage(), e);
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
    public void canAddUser() throws Exception {
		assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);
		
		User user2 = null;
		try {
			// initially user can't do the operations
			assertFalse("Should not be allowed to add user",
					privilegesInfo.canAddUser(user1Session));
			
			String usersPath = userConfig.getParameters().getConfigValue("usersPath", (String)null);
			assertNotNull("Users Path should not be null", usersPath);
			assertTrue("Users Path should exist",
					adminSession.itemExists(usersPath));

			// grant user1 rights
			Map<String, String> privileges = new HashMap<>();
			privileges.put(String.format("privilege@%s", Privilege.JCR_READ), "granted");
			privileges.put(String.format("privilege@%s", Privilege.JCR_READ_ACCESS_CONTROL), "granted");
			privileges.put(String.format("privilege@%s", Privilege.JCR_MODIFY_ACCESS_CONTROL), "granted");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_WRITE), "granted");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_USER_MANAGEMENT), "granted");
			modifyAce.modifyAce(adminSession, usersPath, user1.getID(), 
					privileges, 
					"first");
			assertTrue("Should be allowed to add user",
					privilegesInfo.canAddUser(user1Session));

			// verify that the user can actually add the user
			try {
				Map<String, String> propMap = new HashMap<>();
				propMap.put("prop1", "value1");
				propMap.put("nested/prop2", "value2");
				user2 = createUser.createUser(user1Session, createUniqueName("user"), "testPwd", "testPwd", 
						propMap, new ArrayList<Modification>());
				assertNotNull("Expected user2 to not be null", user2);
			} catch (RepositoryException e) {
				logger.error("Did not expect RepositoryException when adding user: " + e.getMessage(), e);
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
    public void canAddGroup() throws Exception {
		assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);
		
		workaroundMissingGroupsPath();

		Group group1 = null;
		try {
			// initially user can't do the operations
			assertFalse("Should not be allowed to add group",
					privilegesInfo.canAddGroup(user1Session));
			
			String groupsPath = userConfig.getParameters().getConfigValue("groupsPath", (String)null);
			assertNotNull("Groups Path should not be null", groupsPath);
			assertTrue("Groups Path should exist",
					adminSession.itemExists(groupsPath));
			
			// grant user1 rights
			Map<String, String> privileges = new HashMap<>();
			privileges.put(String.format("privilege@%s", Privilege.JCR_READ), "granted");
			privileges.put(String.format("privilege@%s", Privilege.JCR_READ_ACCESS_CONTROL), "granted");
			privileges.put(String.format("privilege@%s", Privilege.JCR_MODIFY_ACCESS_CONTROL), "granted");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_WRITE), "granted");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_USER_MANAGEMENT), "granted");
			modifyAce.modifyAce(adminSession, groupsPath, user1.getID(), 
					privileges, 
					"first");
			assertTrue("Should be allowed to add group",
					privilegesInfo.canAddGroup(user1Session));

			// verify that the user can actually add the user
			try {
				Map<String, String> propMap = new HashMap<>();
				propMap.put("prop1", "value1");
				propMap.put("nested/prop2", "value2");
				group1 = createGroup.createGroup(user1Session, createUniqueName("group"), 
						propMap, new ArrayList<Modification>());
				assertNotNull("Expected group1 to not be null", group1);
			} catch (RepositoryException e) {
				logger.error("Did not expect RepositoryException when adding group: " + e.getMessage(), e);
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
		String groupsPath = userConfig.getParameters().getConfigValue("groupsPath", (String)null);
		assertNotNull("Groups Path should not be null", groupsPath);
		if (!adminSession.itemExists(groupsPath)) {
			// create a group and the remove it
			Group tempGroup = createGroup.createGroup(adminSession, createUniqueName("group"), 
					Collections.emptyMap(), new ArrayList<Modification>());
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
    public void canUpdateProperties() throws Exception {
		assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);

		workaroundMissingGroupsPath();

		User user2 = null;
		Group group1 = null;

		try {
			// create a couple of test users
			user2 = createUser.createUser(adminSession, createUniqueName("group"), "testPwd", "testPwd", 
					Collections.singletonMap("prop1", "value1"), new ArrayList<Modification>());
			assertNotNull("Expected user2 to not be null", user2);

			group1 = createGroup.createGroup(adminSession, createUniqueName("group"), 
					Collections.singletonMap("prop1", "value1"), new ArrayList<Modification>());
			assertNotNull("Expected group1 to not be null", group1);

			String [] principalIds = new String[] { user2.getID(), group1.getID() };

			// initially user can't do the operation
			for (String pid : principalIds) {
				assertFalse("Should not be allowed to update properties for: " + pid, 
						privilegesInfo.canUpdateProperties(user1Session, pid));
			}
			
			// start with only read rights
			Map<String, String> privileges = new HashMap<>();
			privileges.put(String.format("privilege@%s", Privilege.JCR_READ), "granted");
			modifyAce.modifyAce(adminSession, user2.getPath(), user1.getID(), 
					privileges, 
					"first");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			for (String pid : principalIds) {
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.removeProperty));
			}

			// + grant user management rights
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_USER_MANAGEMENT), "granted");
			modifyAce.modifyAce(adminSession, user2.getPath(), user1.getID(), 
					privileges, 
					"first");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			for (String pid : principalIds) {
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.removeProperty));
			}

			
			// grant rights to only remove properties
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_ADD_PROPERTIES), "none");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_ALTER_PROPERTIES), "none");
			privileges.put(String.format("privilege@%s", Privilege.JCR_ADD_CHILD_NODES), "none");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_REMOVE_PROPERTIES), "granted");
			modifyAce.modifyAce(adminSession, user2.getPath(), user1.getID(), 
					privileges, 
					"first");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			for (String pid : principalIds) {
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.removeProperty));
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
				logger.error("Did not expect RepositoryException when deleting property: " + e.getMessage(), e);
				fail("Did not expect RepositoryException when deleting property: " + e.getMessage());
			}
			// verify that the user can not add nested property
			propsMap = new HashMap<>();
			propsMap.put("nested/prop2", "value2");
			try {
				updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
				updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
				assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
				user1Session.save();
				fail("Expected AccessDenied exception when adding nested property");
			} catch (RepositoryException e) {
				// expected
				user1Session.refresh(false);
			}
			
			
			
			// grant rights to only alter (non-nested) properties
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_ADD_PROPERTIES), "granted");
			privileges.put(String.format("privilege@%s", Privilege.JCR_ADD_CHILD_NODES), "none");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_REMOVE_PROPERTIES), "none");
			modifyAce.modifyAce(adminSession, user2.getPath(), user1.getID(), 
					privileges, 
					"first");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			for (String pid : principalIds) {
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.removeProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.removeProperty));
			}
			
			// verify that the user can actually add property
			propsMap = new HashMap<>();
			propsMap.put("prop1", "value1");
			try {
				updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
				updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
				assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
				user1Session.save();
			} catch (RepositoryException e) {
				logger.error("Did not expect RepositoryException when adding property: " + e.getMessage(), e);
				fail("Did not expect RepositoryException when adding property: " + e.getMessage());
			}
			// verify that the user can not add nested property
			propsMap.put("nested/prop2", "value2");
			try {
				updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
				updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
				assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
				user1Session.save();
				fail("Expected AccessDenied exception when adding nested property");
			} catch (RepositoryException e) {
				// expected
				user1Session.refresh(false);
			}
			
			
			
			// grant rights to alter (non-nested or nested) properties
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_ADD_PROPERTIES), "granted");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_ALTER_PROPERTIES), "granted");
			privileges.put(String.format("privilege@%s", Privilege.JCR_ADD_CHILD_NODES), "granted");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_REMOVE_PROPERTIES), "none");
			modifyAce.modifyAce(adminSession, user2.getPath(), user1.getID(), 
					privileges, 
					"first");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			for (String pid : principalIds) {
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.removeProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty));
				assertFalse("Should not be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.removeProperty));
			}
			
			// verify that the user can actually add property and nested property
			propsMap = new HashMap<>();
			propsMap.put("prop1", "value1");
			propsMap.put("nested/prop2", "value2");
			try {
				updateUser.updateUser(user1Session, user2.getID(), propsMap, new ArrayList<>());
				updateGroup.updateGroup(user1Session, group1.getID(), propsMap, new ArrayList<>());
				assertTrue("Expected pending changes in the jcr session", user1Session.hasPendingChanges());
				user1Session.save();
			} catch (RepositoryException e) {
				logger.error("Did not expect RepositoryException when adding properties: " + e.getMessage(), e);
				fail("Did not expect RepositoryException when adding properties: " + e.getMessage());
			}
			
			
			
			// grant rights to alter (non-nested or nested) properties and remove properties
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_ADD_PROPERTIES), "granted");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_ALTER_PROPERTIES), "granted");
			privileges.put(String.format("privilege@%s", Privilege.JCR_ADD_CHILD_NODES), "granted");
			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_REMOVE_PROPERTIES), "granted");
			modifyAce.modifyAce(adminSession, user2.getPath(), user1.getID(), 
					privileges, 
					"first");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			for (String pid : principalIds) {
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.removeProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.removeProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.addNestedProperty));
				assertTrue("Should be allowed to update properties for: " + pid,
						privilegesInfo.canUpdateProperties(user1Session, pid, PropertyUpdateTypes.removeProperty));
			}			
			
			// verify that the user can actually add property and nested property
			propsMap = new HashMap<>();
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
				logger.error("Did not expect RepositoryException when adding or deleting properties: " + e.getMessage(), e);
				fail("Did not expect RepositoryException when adding or deleting properties: " + e.getMessage());
			}
		} finally {
			if (user2 != null) {
				deleteUser.deleteUser(adminSession, user2.getID(), new ArrayList<>());
			}
		}
    }

    /**
     * Checks whether the current user has been granted privileges
     * to remove the specified user or group.
     */
    @Test
    public void canRemove() throws Exception {
		assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);
		
		workaroundMissingGroupsPath();
		
		User user2 = null;
		Group group1 = null;
		try {
			user2 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd", 
					Collections.singletonMap("prop1", "value1"), new ArrayList<Modification>());
			assertNotNull("Expected user2 to not be null", user2);

			group1 = createGroup.createGroup(adminSession, createUniqueName("group"), 
					Collections.singletonMap("prop1", "value1"), new ArrayList<Modification>());
			assertNotNull("Expected group1 to not be null", group1);

			// initially user can't do the operations
			assertFalse("Should not be allowed to remove user", 
					privilegesInfo.canRemove(user1Session, user2.getID()));
			assertFalse("Should not be allowed to remove group", 
					privilegesInfo.canRemove(user1Session, group1.getID()));
			
			// grant user1 rights to user2 profile
			Map<String, String> privileges = new HashMap<>();
			privileges.put(String.format("privilege@%s", Privilege.JCR_READ), "granted");
			modifyAce.modifyAce(adminSession, user2.getPath(), user1.getID(), 
					privileges, 
					"first");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			assertFalse("Should not be allowed to remove user", 
					privilegesInfo.canRemove(user1Session, user2.getID()));
			assertFalse("Should not be allowed to remove group", 
					privilegesInfo.canRemove(user1Session, group1.getID()));

			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_USER_MANAGEMENT), "granted");
			modifyAce.modifyAce(adminSession, user2.getPath(), user1.getID(), 
					privileges, 
					"first");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			assertTrue("Should be allowed to remove user", 
					privilegesInfo.canRemove(user1Session, user2.getID()));
			assertTrue("Should be allowed to remove group", 
					privilegesInfo.canRemove(user1Session, group1.getID()));

			// verify that the user can actually delete the user
			String user2Id = user2.getID();
			deleteUser.deleteUser(user1Session, user2Id, new ArrayList<>());
			user2 = null;
			// verify the user is no longer there
			UserManager um = AccessControlUtil.getUserManager(user1Session);
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
    public void canUpdateGroupMembers() throws Exception {
		assertNotNull("Expected privilegesInfo to not be null", privilegesInfo);
		
		workaroundMissingGroupsPath();
		
		Group group1 = null;
		try {
			group1 = createGroup.createGroup(adminSession, createUniqueName("group"), 
					Collections.singletonMap("prop1", "value1"), new ArrayList<Modification>());
			assertNotNull("Expected group1 to not be null", group1);

			// initially user can't do the operations
			assertFalse("Should not be allowed to update group members", 
					privilegesInfo.canUpdateGroupMembers(user1Session, group1.getID()));
			
			// grant user1 rights to group1 profile
			Map<String, String> privileges = new HashMap<>();
			privileges.put(String.format("privilege@%s", Privilege.JCR_READ), "granted");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
			assertFalse("Should not be allowed to update group members", 
					privilegesInfo.canUpdateGroupMembers(user1Session, group1.getID()));

			privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_USER_MANAGEMENT), "granted");
			modifyAce.modifyAce(adminSession, group1.getPath(), user1.getID(), 
					privileges, 
					"first");
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
				logger.error("Did not expect RepositoryException when adding member to group: " + e.getMessage(), e);
				fail("Did not expect RepositoryException when adding member to group: " + e.getMessage());
			}
		} finally {
			if (group1 != null) {
				deleteGroup.deleteGroup(adminSession, group1.getID(), new ArrayList<>());
			}
		}
    }
    
}
