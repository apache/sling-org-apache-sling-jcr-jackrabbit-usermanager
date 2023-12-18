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
package org.apache.sling.jackrabbit.usermanager;

import static org.junit.Assert.*;

import javax.jcr.Session;

import org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo.PropertyUpdateTypes;
import org.junit.Test;

/**
 * Test coverage for AuthorizablePrivilegesInfo / PropertyUpdateTypes
 */
public class AuthorizablePrivilegesInfoTest {

    /**
     * An implementation to facilitate testing of default methods in the interface
     */
    public static class TestDefaultMethodsAuthorizablePrivlegesInfo implements AuthorizablePrivilegesInfo {

        @Override
        public boolean canAddUser(Session jcrSession) {
            return false;
        }

        @Override
        public boolean canAddGroup(Session jcrSession) {
            return false;
        }

        @Override
        public boolean canUpdateProperties(Session jcrSession, String principalId) {
            return false;
        }

        @Override
        public boolean canRemove(Session jcrSession, String principalId) {
            return false;
        }

        @Override
        public boolean canUpdateGroupMembers(Session jcrSession, String groupId) {
            return false;
        }

    }

    @SuppressWarnings("deprecation")
    @Test
    public void testConvertDeprecated() {
        assertEquals(PropertyUpdateTypes.ADD_PROPERTY, PropertyUpdateTypes.convertDeprecated(PropertyUpdateTypes.addProperty));
        assertEquals(PropertyUpdateTypes.ADD_NESTED_PROPERTY, PropertyUpdateTypes.convertDeprecated(PropertyUpdateTypes.addNestedProperty));
        assertEquals(PropertyUpdateTypes.ALTER_PROPERTY, PropertyUpdateTypes.convertDeprecated(PropertyUpdateTypes.alterProperty));
        assertEquals(PropertyUpdateTypes.REMOVE_PROPERTY, PropertyUpdateTypes.convertDeprecated(PropertyUpdateTypes.removeProperty));

        //and one that doesn't require conversion
        assertEquals(PropertyUpdateTypes.REMOVE_PROPERTY, PropertyUpdateTypes.convertDeprecated(PropertyUpdateTypes.REMOVE_PROPERTY));
    }


    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canUpdateProperties(javax.jcr.Session, java.lang.String, org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo.PropertyUpdateTypes[])}.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testCanUpdatePropertiesSessionStringPropertyUpdateTypesArray() {
        AuthorizablePrivilegesInfo api = new TestDefaultMethodsAuthorizablePrivlegesInfo();
        Session jcrSession = null;
        api.canUpdateProperties(jcrSession, "testuser1", PropertyUpdateTypes.ALTER_PROPERTY);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canDisable(javax.jcr.Session, java.lang.String)}.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testCanDisable() {
        AuthorizablePrivilegesInfo api = new TestDefaultMethodsAuthorizablePrivlegesInfo();
        Session jcrSession = null;
        api.canDisable(jcrSession, "testuser1");
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canChangePassword(javax.jcr.Session, java.lang.String)}.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testCanChangePassword() {
        AuthorizablePrivilegesInfo api = new TestDefaultMethodsAuthorizablePrivlegesInfo();
        Session jcrSession = null;
        api.canChangePassword(jcrSession, "testuser1");
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canChangePasswordWithoutOldPassword(javax.jcr.Session, java.lang.String)}.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testCanChangePasswordWithoutOldPassword() {
        AuthorizablePrivilegesInfo api = new TestDefaultMethodsAuthorizablePrivlegesInfo();
        Session jcrSession = null;
        api.canChangePasswordWithoutOldPassword(jcrSession, "testuser1");
    }

}
