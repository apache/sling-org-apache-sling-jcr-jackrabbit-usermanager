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
package org.apache.sling.jcr.jackrabbit.usermanager.it.post;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.json.JsonException;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class UserPrivilegesInfoIT extends UserManagerClientTestSupport {

    enum CanAdd {
        USER("canAddUser"),
        GROUP("canAddGroup");

        private String propName;

        CanAdd(String propName) {
            this.propName = propName;
        }

        public String propName() {
            return propName;
        }

    }

    enum CanChangeUser {
        REMOVE("canRemove"),
        UPDATE_PROPERTIES("canUpdateProperties"),
        CHANGE_PASSWORD("canChangePassword"),
        DISABLE("canDisable");

        private String propName;

        CanChangeUser(String propName) {
            this.propName = propName;
        }

        public String propName() {
            return propName;
        }

    }

    enum CanChangeGroup {
        REMOVE("canRemove"),
        UPDATE_PROPERTIES("canUpdateProperties"),
        UPDATE_GROUP_MEMBERS("canUpdateGroupMembers");

        private String propName;

        CanChangeGroup(String propName) {
            this.propName = propName;
        }

        public String propName() {
            return propName;
        }

    }

    @Override
    protected Option buildBundleResourcesBundle() {
        final List<String> resourcePaths = Arrays.asList("/apps/sling/servlet/default/privileges-info.json.esp");
        final String bundleResourcesHeader = String.join(",", resourcePaths);
        return buildBundleResourcesBundle(bundleResourcesHeader, resourcePaths);
    }

    private void grantUserManagerRights(String principalId) throws IOException {
        String postUrl = String.format("%s/home.modifyAce.html", baseServerUri);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("principalId", principalId));
        postParams.add(new BasicNameValuePair("privilege@jcr:read", "granted"));
        postParams.add(new BasicNameValuePair("privilege@rep:write", "granted"));
        postParams.add(new BasicNameValuePair("privilege@jcr:readAccessControl", "granted"));
        postParams.add(new BasicNameValuePair("privilege@jcr:modifyAccessControl", "granted"));
        postParams.add(new BasicNameValuePair("privilege@rep:userManagement", "granted"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);
    }

    /**
     * The common impl for checking add permissions for users and groups
     * @param can specify which type of add to test
     */
    private void testCanAdd(CanAdd can) throws IOException {
        testUserId = createTestUser();
        String getUrl = String.format("%s/system/userManager/user/%s.privileges-info.json", baseServerUri, testUserId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        assertEquals(false, jsonObj.getBoolean(can.propName()));

        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean(can.propName()));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean(can.propName()));
    }

    /**
     * The common impl for checking change permissions for a user
     * @param can specify which type of change to test
     */
    private void testCanChange(CanChangeUser can) throws IOException {
        testUserId = createTestUser();

        //1. verify user can update their own properties
        String getUrl = String.format("%s/system/userManager/user/%s.privileges-info.json", baseServerUri, testUserId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        //user can update their own properties
        assertEquals(true, jsonObj.getBoolean(can.propName()));


        //2. now try another user
        testUserId2 = createTestUser();

        //fetch the JSON for the test page to verify the settings.
        Credentials testUser2Creds = new UsernamePasswordCredentials(testUserId2, "testPwd");

        String json2 = getAuthenticatedContent(testUser2Creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json2);
        JsonObject jsonObj2 = parseJson(json2);

        //user can not update other users properties
        assertEquals(false, jsonObj2.getBoolean(can.propName()));


        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean(can.propName()));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean(can.propName()));
    }

    /**
     * The common impl for checking change permissions for a group
     * @param can specify which type of change to test
     */
    private void testCanChange(CanChangeGroup can) throws IOException {
        testGroupId = createTestGroup();
        testUserId = createTestUser();

        //1. Verify non admin user can not update group properties
        String getUrl = String.format("%s/system/userManager/group/%s.privileges-info.json", baseServerUri, testGroupId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        //normal user can not update group properties
        assertEquals(false, jsonObj.getBoolean(can.propName()));


        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean(can.propName()));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean(can.propName()));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to add a new user.
     */
    @Test
    public void testCanAddUser() throws JsonException, IOException {
        testCanAdd(CanAdd.USER);
    }

    /**
     * Checks whether the current user has been granted privileges
     * to add a new group.
     */
    @Test
    public void testCanAddGroup() throws IOException, JsonException {
        testCanAdd(CanAdd.GROUP);
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user.
     */
    @Test
    public void testCanUpdateUserProperties() throws IOException, JsonException {
        testCanChange(CanChangeUser.UPDATE_PROPERTIES);
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified group.
     */
    @Test
    public void testCanUpdateGroupProperties() throws IOException, JsonException {
        testCanChange(CanChangeGroup.UPDATE_PROPERTIES);
    }

    /**
     * Checks whether the current user has been granted privileges
     * to remove the specified user.
     */
    @Test
    public void testCanRemoveUser() throws IOException, JsonException {
        testCanChange(CanChangeUser.REMOVE);
    }

    /**
     * Checks whether the current user has been granted privileges
     * to remove the specified group.
     */
    @Test
    public void testCanRemoveGroup() throws IOException, JsonException {
        testCanChange(CanChangeGroup.REMOVE);
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the membership of the specified group.
     */
    @Test
    public void testCanUpdateGroupMembers() throws IOException, JsonException {
        testCanChange(CanChangeGroup.UPDATE_GROUP_MEMBERS);
    }

    /**
     * Checks whether the current user has been granted privileges
     * to change the password of the specified user.
     */
    @Test
    public void testCanChangePassword() throws IOException, JsonException {
        testCanChange(CanChangeUser.CHANGE_PASSWORD);
    }

    /**
     * Checks whether the current user has been granted privileges
     * to disable the specified user.
     */
    @Test
    public void testCanDisable() throws IOException, JsonException {
        testCanChange(CanChangeUser.DISABLE);
    }

}
