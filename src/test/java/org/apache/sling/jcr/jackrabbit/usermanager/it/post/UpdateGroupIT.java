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

import javax.json.JsonArray;
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
 * Tests for the 'updateAuthorizable' Sling Post Operation on
 * a group resource.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class UpdateGroupIT extends UserManagerClientTestSupport {

    @Test
    public void testUpdateGroup() throws IOException, JsonException {
        testGroupId = createTestGroup();

        String postUrl = String.format("%s/system/userManager/group/%s.update.html", baseServerUri, testGroupId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test Group"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals("My Updated Test Group", jsonObj.getString("displayName"));
        assertEquals("http://www.apache.org/updated", jsonObj.getString("url"));
    }

    @Test
    public void testNotAuthorizedUpdateGroup() throws IOException, JsonException {
        //a user who is not authorized to do the action
        testUserId2 = createTestUser();

        testGroupId = createTestGroup();

        String postUrl = String.format("%s/system/userManager/group/%s.update.html", baseServerUri, testGroupId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test Group"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));

        Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testAuthorizedUpdateGroup() throws IOException, JsonException {
        //a user who is authorized to do the action
        testUserId2 = createTestUser();
        grantUserManagementRights(testUserId2);

        testGroupId = createTestGroup();

        String postUrl = String.format("%s/system/userManager/group/%s.update.html", baseServerUri, testGroupId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test Group"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));

        Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals("My Updated Test Group", jsonObj.getString("displayName"));
        assertEquals("http://www.apache.org/updated", jsonObj.getString("url"));
    }

    /**
     * Test for SLING-7831
     */
    @Test
    public void testUpdateGroupCustomPostResponse() throws IOException, JsonException {
        testGroupId = createTestGroup();

        String postUrl = String.format("%s/system/userManager/group/%s.update.html", baseServerUri, testGroupId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":responseType", "custom"));
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test Group"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String content = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_HTML, postParams, HttpServletResponse.SC_OK);
        assertEquals("Thanks!", content); //verify that the content matches the custom response
    }

    @Test
    public void testUpdateGroupMembers() throws IOException, JsonException {
        testGroupId = createTestGroup();
        testUserId = createTestUser();

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");

        // verify that the members array exists, but is empty
        JsonArray members = getTestGroupMembers(creds);
        assertEquals(0, members.size());

        JsonArray memberships = getTestUserMemberships(creds);
        assertEquals(0, memberships.size());

        String postUrl = String.format("%s/system/userManager/group/%s.update.html", baseServerUri, testGroupId);

        // add a group member
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":member", testUserId));
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        members = getTestGroupMembers(creds);
        assertEquals(1, members.size());
        assertEquals("/system/userManager/user/" + testUserId, members.getString(0));

        memberships = getTestUserMemberships(creds);
        assertEquals(1, memberships.size());
        assertEquals("/system/userManager/group/" + testGroupId, memberships.getString(0));

        // delete a group member
        postParams.clear();
        postParams.add(new BasicNameValuePair(":member@Delete", testUserId));
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        members = getTestGroupMembers(creds);
        assertEquals(0, members.size());

        memberships = getTestUserMemberships(creds);
        assertEquals(0, memberships.size());

    }

    @Test
    public void testAuthorizedUpdateGroupMembers() throws IOException, JsonException {
        //a user who is authorized to do the action
        testUserId2 = createTestUser();
        grantUserManagementRights(testUserId2);

        testGroupId = createTestGroup();
        testUserId = createTestUser();

        Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");

        // verify that the members array exists, but is empty
        JsonArray members = getTestGroupMembers(creds);
        assertEquals(0, members.size());

        JsonArray memberships = getTestUserMemberships(creds);
        assertEquals(0, memberships.size());

        String postUrl = String.format("%s/system/userManager/group/%s.update.html", baseServerUri, testGroupId);

        // add a group member
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":member", testUserId));
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        members = getTestGroupMembers(creds);
        assertEquals(1, members.size());
        assertEquals("/system/userManager/user/" + testUserId, members.getString(0));

        memberships = getTestUserMemberships(creds);
        assertEquals(1, memberships.size());
        assertEquals("/system/userManager/group/" + testGroupId, memberships.getString(0));

        // delete a group member
        postParams.clear();
        postParams.add(new BasicNameValuePair(":member@Delete", testUserId));
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        members = getTestGroupMembers(creds);
        assertEquals(0, members.size());

        memberships = getTestUserMemberships(creds);
        assertEquals(0, memberships.size());

    }

    JsonArray getTestUserMemberships(Credentials creds) throws IOException, JsonException {
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        return jsonObj.getJsonArray("memberOf");
    }

    JsonArray getTestGroupMembers(Credentials creds) throws IOException, JsonException {
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        return jsonObj.getJsonArray("members");
    }

    /**
     * Test for SLING-1677
     */
    @Test
    public void testUpdateGroupResponseAsJSON() throws IOException, JsonException {
        testGroupId = createTestGroup();

        String postUrl = String.format("%s/system/userManager/group/%s.update.json", baseServerUri, testGroupId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test Group"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
    }

}

