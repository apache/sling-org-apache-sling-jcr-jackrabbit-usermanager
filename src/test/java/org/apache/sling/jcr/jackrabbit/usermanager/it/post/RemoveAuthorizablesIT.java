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
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * Tests for the 'removeAuthorizable' Sling Post Operation
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RemoveAuthorizablesIT extends UserManagerClientTestSupport {

    @Test
    public void testRemoveUser() throws IOException {
        String userId = createTestUser();

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");

        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, userId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        String postUrl = String.format("%s/system/userManager/user/%s.delete.html", baseServerUri, userId);
        List<NameValuePair> postParams = new ArrayList<>();
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns some data
    }

    @Test
    public void testNotAuthorizedRemoveUser() throws IOException {
        //a user who is not authorized to do the action
        testUserId2 = createTestUser();

        String userId = createTestUser();

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");

        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, userId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        Credentials creds2 = new UsernamePasswordCredentials(testUserId2, "testPwd");
        String postUrl = String.format("%s/system/userManager/user/%s.delete.html", baseServerUri, userId);
        List<NameValuePair> postParams = new ArrayList<>();
        assertAuthenticatedPostStatus(creds2, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);

        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
    }

    @Test
    public void testAuthorizedRemoveUser() throws IOException {
        //a user who is authorized to do the action
        testUserId2 = createTestUser();
        grantUserManagementRights(testUserId2);

        String userId = createTestUser();

        Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");

        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, userId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        String postUrl = String.format("%s/system/userManager/user/%s.delete.html", baseServerUri, userId);
        List<NameValuePair> postParams = new ArrayList<>();
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns some data
    }

    /**
     * Test for SLING-7831
     */
    @Test
    public void testRemoveUserCustomPostResponse() throws IOException {
        String userId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.delete.html", baseServerUri, userId);
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":responseType", "custom"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String content = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_HTML, postParams, HttpServletResponse.SC_OK);
        assertEquals("Thanks!", content); //verify that the content matches the custom response
    }

    @Test
    public void testRemoveGroup() throws IOException {
        String groupId = createTestGroup();

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");

        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, groupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        String postUrl = String.format("%s/system/userManager/group/%s.delete.html", baseServerUri, groupId);
        List<NameValuePair> postParams = new ArrayList<>();
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns some data
    }

    @Test
    public void testNotAuthorizedRemoveGroup() throws IOException {
        //a user who is not authorized to do the action
        testUserId2 = createTestUser();

        String groupId = createTestGroup();

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");

        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, groupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        Credentials creds2 = new UsernamePasswordCredentials(testUserId2, "testPwd");
        String postUrl = String.format("%s/system/userManager/group/%s.delete.html", baseServerUri, groupId);
        List<NameValuePair> postParams = new ArrayList<>();
        assertAuthenticatedPostStatus(creds2, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);

        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
    }

    @Test
    public void testAuthorizedRemoveGroup() throws IOException {
        //a user who is authorized to do the action
        testUserId2 = createTestUser();
        grantUserManagementRights(testUserId2);

        String groupId = createTestGroup();

        Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");

        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, groupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        String postUrl = String.format("%s/system/userManager/group/%s.delete.html", baseServerUri, groupId);
        List<NameValuePair> postParams = new ArrayList<>();
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns some data
    }

    /**
     * Test for SLING-7831
     */
    @Test
    public void testRemoveGroupCustomPostResponse() throws IOException {
        String groupId = createTestGroup();

        String postUrl = String.format("%s/system/userManager/group/%s.delete.html", baseServerUri, groupId);
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":responseType", "custom"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String content = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_HTML, postParams, HttpServletResponse.SC_OK);
        assertEquals("Thanks!", content); //verify that the content matches the custom response
    }

    @Test
    public void testRemoveAuthorizables() throws IOException {
        String userId = createTestUser();
        String groupId = createTestGroup();

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");

        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, userId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, groupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        String postUrl = String.format("%s/system/userManager.delete.html", baseServerUri);
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":applyTo", "group/" + groupId));
        postParams.add(new BasicNameValuePair(":applyTo", "user/" + userId));
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, userId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns some data

        getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, groupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns some data
    }

    /**
     * Test the problem reported as SLING-1237
     */
    @Test
    public void testRemoveGroupWithMembers() throws IOException {
        String groupId = createTestGroup();
        String userId = createTestUser();

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String addMemberPostUrl = String.format("%s/system/userManager/group/%s.update.html", baseServerUri, groupId);
        List<NameValuePair> addMemberPostParams = new ArrayList<>();
        addMemberPostParams.add(new BasicNameValuePair(":member", userId));
        assertAuthenticatedPostStatus(creds, addMemberPostUrl, HttpServletResponse.SC_OK, addMemberPostParams, null);

        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, groupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data

        String postUrl = String.format("%s/system/userManager/group/%s.delete.html", baseServerUri, groupId);
        List<NameValuePair> postParams = new ArrayList<>();
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns some data
    }


    /**
     * Test for SLING-1677
     */
    @Test
    public void testRemoveAuthorizablesResponseAsJSON() throws IOException, JsonException {
        String userId = createTestUser();
        String groupId = createTestGroup();

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");

        String postUrl = String.format("%s/system/userManager.delete.json", baseServerUri);
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":applyTo", "group/" + groupId));
        postParams.add(new BasicNameValuePair(":applyTo", "user/" + userId));
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
    }

    private void testRemoveAuthorizablesRedirect(String redirectTo, int expectedStatus) throws IOException {
        String userId = createTestUser();
        String groupId = createTestGroup();

        String postUrl = String.format("%s/system/userManager.delete.html", baseServerUri);
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":applyTo", "group/" + groupId));
        postParams.add(new BasicNameValuePair(":applyTo", "user/" + userId));
        postParams.add(new BasicNameValuePair(":redirect", redirectTo));
        assertAuthenticatedAdminPostStatus(postUrl, expectedStatus, postParams, null);
    }

    @Test
    public void testRemoveAuthorizableValidRedirect() throws IOException, JsonException {
        testRemoveAuthorizablesRedirect("/*.html", HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @Test
    public void testRemoveAuthorizableInvalidRedirectWithAuthority() throws IOException, JsonException {
        testRemoveAuthorizablesRedirect("https://sling.apache.org", SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void testRemoveAuthorizableInvalidRedirectWithInvalidURI() throws IOException, JsonException {
        testRemoveAuthorizablesRedirect("https://", SC_UNPROCESSABLE_ENTITY);
    }

}
