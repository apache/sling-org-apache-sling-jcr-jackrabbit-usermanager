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
package org.apache.sling.jcr.jackrabbit.usermanager.it.post;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.sling.jackrabbit.usermanager.ChangeUserPassword;
import org.apache.sling.jackrabbit.usermanager.CreateUser;
import org.apache.sling.jackrabbit.usermanager.DeleteUser;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.jackrabbit.accessmanager.DeleteAces;
import org.apache.sling.jcr.jackrabbit.accessmanager.ModifyAce;
import org.apache.sling.jcr.jackrabbit.usermanager.it.UserManagerTestSupport;
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
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic test of ChangeUserPassword component
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ChangeUserPasswordIT extends UserManagerTestSupport {

    private static AtomicLong counter = new AtomicLong(0);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    protected BundleContext bundleContext;
    
    @Inject
    protected SlingRepository repository;

    @Inject
    protected ConfigurationAdmin configAdmin;
    
    @Inject
    private CreateUser createUser;

    @Inject
    private ModifyAce modifyAce;

    @Inject
    private DeleteAces deleteAces;

    @Inject
    private DeleteUser deleteUser;

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
        
        user1Session = repository.login(new SimpleCredentials(user1.getID(), "testPwd".toCharArray()));
        assertNotNull("Expected user1Session to not be null", user1Session);
        
        //change the ACE for the user home folder to the minimum privileges
        // and without rep:userManagement
        deleteAces.deleteAces(adminSession, user1.getPath(), new String[] {user1.getID()});
        Map<String, String> privileges = new HashMap<>();
        privileges.put(String.format("privilege@%s", Privilege.JCR_READ), "granted");
        privileges.put(String.format("privilege@%s", PrivilegeConstants.REP_ALTER_PROPERTIES), "granted");
        modifyAce.modifyAce(adminSession, user1.getPath(), user1.getID(), 
                privileges, 
                "first");
        if (adminSession.hasPendingChanges()) {
        	adminSession.save();
        }
    }

    @After
    public void teardown() {
        if (user1 != null) {
            try {
                adminSession.refresh(false);
                deleteUser.deleteUser(adminSession, user1.getID(), new ArrayList<>());
            } catch (RepositoryException e) {
                logger.warn("Failed to delete user: " + e.getMessage(), e);
            }
        }
        user1Session.logout();
        adminSession.logout();
    }

    protected String createUniqueName(String prefix) {
        return String.format("%s_%s%d", prefix, testName.getMethodName(), counter.incrementAndGet());
    }

    /**
     * SLING-9808 test changing password when user doesn't have rep:userManagement privilege
     */
    @Test
    public void changePasswordAsSelfGranted() throws Exception {
        org.osgi.service.cm.Configuration configuration = configAdmin.getConfiguration("org.apache.sling.jackrabbit.usermanager.impl.post.ChangeUserPasswordServlet", null);
        Dictionary<String, Object> originalServiceProps = configuration.getProperties();
        ServiceReference<ChangeUserPassword> serviceReference = null;
        try {
            // update the service configuration to ensure the option is enabled
            Dictionary<String, Object> newServiceProps = replaceConfigProp(originalServiceProps, "alwaysAllowSelfChangePassword", Boolean.TRUE);
            configuration.update(newServiceProps);
            new WaitForServiceUpdated(5000, 100, bundleContext, ChangeUserPassword.class, 
                    "alwaysAllowSelfChangePassword", Boolean.TRUE);
            
            serviceReference = bundleContext.getServiceReference(ChangeUserPassword.class);
            assertEquals(Boolean.TRUE, serviceReference.getProperty("alwaysAllowSelfChangePassword"));
            ChangeUserPassword changeUserPassword = bundleContext.getService(serviceReference);
            assertNotNull(changeUserPassword);
            changeUserPassword.changePassword(user1Session, 
                    user1.getID(), 
                    "testPwd", 
                    "testPwdChanged", 
                    "testPwdChanged", 
                    new ArrayList<>());
            try {
                user1Session.save();
            } catch (AccessDeniedException e) {
                logger.error("Did not expect AccessDeniedException when changing user passsword: " + e.getMessage(), e);
                fail("Did not expect AccessDeniedException when changing user passsword: " + e.getMessage());
            }
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
     * SLING-9808 test changing password when user doesn't have rep:userManagement privilege
     */
    @Test
    public void changePasswordAsSelfDenied() throws Exception {
        org.osgi.service.cm.Configuration configuration = configAdmin.getConfiguration("org.apache.sling.jackrabbit.usermanager.impl.post.ChangeUserPasswordServlet", null);
        Dictionary<String, Object> originalServiceProps = configuration.getProperties();
        ServiceReference<ChangeUserPassword> serviceReference = null;
        try {
            // update the service configuration to ensure the option is disabled
            Dictionary<String, Object> newServiceProps = replaceConfigProp(originalServiceProps, "alwaysAllowSelfChangePassword", Boolean.FALSE);
            configuration.update(newServiceProps);
            new WaitForServiceUpdated(5000, 100, bundleContext, ChangeUserPassword.class, 
                    "alwaysAllowSelfChangePassword", Boolean.FALSE);
            
            serviceReference = bundleContext.getServiceReference(ChangeUserPassword.class);
            assertEquals(Boolean.FALSE, serviceReference.getProperty("alwaysAllowSelfChangePassword"));
            ChangeUserPassword changeUserPassword = bundleContext.getService(serviceReference);
            assertNotNull(changeUserPassword);
            changeUserPassword.changePassword(user1Session, 
                    user1.getID(), 
                    "testPwd", 
                    "testPwdChanged", 
                    "testPwdChanged", 
                    new ArrayList<>());
            assertTrue(user1Session.hasPendingChanges());
            try {
                user1Session.save();
                fail("Expected an AccessDeniedException when changing user passsword.");
            } catch (AccessDeniedException e) {
                // expected an Access is denied exception
                Throwable cause = e.getCause();
                assertTrue(cause instanceof CommitFailedException);
                assertEquals("OakAccess0000: Access denied", cause.getMessage());
            }
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

}
