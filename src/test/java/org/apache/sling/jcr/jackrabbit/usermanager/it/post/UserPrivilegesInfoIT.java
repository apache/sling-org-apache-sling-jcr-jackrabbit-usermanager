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
     * Checks whether the current user has been granted privileges
     * to add a new user.
     */
    @Test
    public void testCanAddUser() throws JsonException, IOException {
        testUserId = createTestUser();

        String getUrl = String.format("%s/system/userManager/user/%s.privileges-info.json", baseServerUri, testUserId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        assertEquals(false, jsonObj.getBoolean("canAddUser"));

        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canAddUser"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canAddUser"));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to add a new group.
     */
    @Test
    public void testCanAddGroup() throws IOException, JsonException {
        testUserId = createTestUser();

        String getUrl = String.format("%s/system/userManager/user/%s.privileges-info.json", baseServerUri, testUserId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        assertEquals(false, jsonObj.getBoolean("canAddGroup"));

        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canAddGroup"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canAddGroup"));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user.
     */
    @Test
    public void testCanUpdateUserProperties() throws IOException, JsonException {
        testUserId = createTestUser();

        //1. verify user can update thier own properties
        String getUrl = String.format("%s/system/userManager/user/%s.privileges-info.json", baseServerUri, testUserId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        //user can update their own properties
        assertEquals(true, jsonObj.getBoolean("canUpdateProperties"));


        //2. now try another user
        testUserId2 = createTestUser();

        //fetch the JSON for the test page to verify the settings.
        Credentials testUser2Creds = new UsernamePasswordCredentials(testUserId2, "testPwd");

        String json2 = getAuthenticatedContent(testUser2Creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json2);
        JsonObject jsonObj2 = parseJson(json2);

        //user can not update other users properties
        assertEquals(false, jsonObj2.getBoolean("canUpdateProperties"));


        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canUpdateProperties"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canUpdateProperties"));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified group.
     */
    @Test
    public void testCanUpdateGroupProperties() throws IOException, JsonException {
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
        assertEquals(false, jsonObj.getBoolean("canUpdateProperties"));


        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canUpdateProperties"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canUpdateProperties"));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to remove the specified user.
     */
    @Test
    public void testCanRemoveUser() throws IOException, JsonException {
        testUserId = createTestUser();

        //1. verify user can remove themselves as they have jcr:all permissions by default in the starter
        String getUrl = String.format("%s/system/userManager/user/%s.privileges-info.json", baseServerUri, testUserId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        //user can remove themself
        assertEquals(true, jsonObj.getBoolean("canRemove"));


        //2. now try another user
        testUserId2 = createTestUser();

        //fetch the JSON for the test page to verify the settings.
        Credentials testUser2Creds = new UsernamePasswordCredentials(testUserId2, "testPwd");

        String json2 = getAuthenticatedContent(testUser2Creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json2);
        JsonObject jsonObj2 = parseJson(json2);

        //user can not delete other users
        assertEquals(false, jsonObj2.getBoolean("canRemove"));


        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canRemove"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canRemove"));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to remove the specified group.
     */
    @Test
    public void testCanRemoveGroup() throws IOException, JsonException {
        testGroupId = createTestGroup();
        testUserId = createTestUser();

        //1. Verify non admin user can not remove group
        String getUrl = String.format("%s/system/userManager/group/%s.privileges-info.json", baseServerUri, testGroupId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        //normal user can not remove group
        assertEquals(false, jsonObj.getBoolean("canRemove"));

        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canRemove"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canRemove"));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to update the membership of the specified group.
     */
    @Test
    public void testCanUpdateGroupMembers() throws IOException, JsonException {
        testGroupId = createTestGroup();
        testUserId = createTestUser();

        //1. Verify non admin user can not update group membership
        String getUrl = String.format("%s/system/userManager/group/%s.privileges-info.json", baseServerUri, testGroupId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        //normal user can not remove group
        assertEquals(false, jsonObj.getBoolean("canUpdateGroupMembers"));

        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canUpdateGroupMembers"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canUpdateGroupMembers"));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to change the password of the specified user.
     */
    @Test
    public void testCanChangePassword() throws IOException, JsonException {
        testUserId = createTestUser();

        //1. verify user can update thier own password
        String getUrl = String.format("%s/system/userManager/user/%s.privileges-info.json", baseServerUri, testUserId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        //user can update their own password
        assertEquals(true, jsonObj.getBoolean("canChangePassword"));


        //2. now try another user
        testUserId2 = createTestUser();

        //fetch the JSON for the test page to verify the settings.
        Credentials testUser2Creds = new UsernamePasswordCredentials(testUserId2, "testPwd");

        String json2 = getAuthenticatedContent(testUser2Creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json2);
        JsonObject jsonObj2 = parseJson(json2);

        //user can not update other users password
        assertEquals(false, jsonObj2.getBoolean("canChangePassword"));


        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        assertEquals(true, jsonObj.getBoolean("canChangePassword"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        //user can update other users password
        assertEquals(true, jsonObj.getBoolean("canChangePassword"));
    }

    /**
     * Checks whether the current user has been granted privileges
     * to disable the specified user.
     */
    @Test
    public void testCanDisable() throws IOException, JsonException {
        testUserId = createTestUser();

        //1. verify user can disable themselves
        String getUrl = String.format("%s/system/userManager/user/%s.privileges-info.json", baseServerUri, testUserId);

        //fetch the JSON for the test page to verify the settings.
        Credentials testUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);

        //user can can disable themselves
        assertEquals(true, jsonObj.getBoolean("canDisable"));


        //2. now try another user
        testUserId2 = createTestUser();

        //fetch the JSON for the test page to verify the settings.
        Credentials testUser2Creds = new UsernamePasswordCredentials(testUserId2, "testPwd");

        String json2 = getAuthenticatedContent(testUser2Creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json2);
        JsonObject jsonObj2 = parseJson(json2);

        //user can not disable other user
        assertEquals(false, jsonObj2.getBoolean("canDisable"));


        //try admin user
        testUserCreds = new UsernamePasswordCredentials("admin", "admin");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        //admin can disable other user
        assertEquals(true, jsonObj.getBoolean("canDisable"));

        //try non-admin with sufficient privileges
        testUserId3 = createTestUser();
        grantUserManagerRights(testUserId3);

        testUserCreds = new UsernamePasswordCredentials(testUserId3, "testPwd");

        json = getAuthenticatedContent(testUserCreds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);

        //user can disable other user
        assertEquals(true, jsonObj.getBoolean("canDisable"));
    }

}
