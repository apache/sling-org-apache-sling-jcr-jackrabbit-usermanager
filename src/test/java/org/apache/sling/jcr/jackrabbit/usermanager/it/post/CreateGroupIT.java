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
 * Tests for the 'createGroup' Sling Post Operation
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class CreateGroupIT extends UserManagerClientTestSupport {

    @Test
    public void testCreateGroup() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        postParams.add(new BasicNameValuePair("marker", testGroupId));
        assertAuthenticatedAdminPostStatus(postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the group profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals(testGroupId, jsonObj.getString("marker"));
    }

    @Test
    public void testNotAuthorizedCreateGroup() throws IOException, JsonException {
        testUserId = createTestUser();
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");

        String testGroupId2 = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId2));
        postParams.add(new BasicNameValuePair("marker", testGroupId2));
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testAuthorizedCreateGroup() throws IOException, JsonException {
        testUserId = createTestUser();
        grantUserManagementRights(testUserId);

        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        postParams.add(new BasicNameValuePair("marker", testGroupId));
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the group profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals(testGroupId, jsonObj.getString("marker"));
    }

    /**
     * Test for SLING-7831
     */
    @Test
    public void testCreateGroupCustomPostResponse() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":responseType", "custom"));
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        postParams.add(new BasicNameValuePair("marker", testGroupId));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String content = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_HTML, postParams, HttpServletResponse.SC_OK);
        assertEquals("Thanks!", content); //verify that the content matches the custom response
    }

    @Test
    public void testCreateGroupMissingGroupId() throws IOException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        List<NameValuePair> postParams = new ArrayList<>();
        assertAuthenticatedAdminPostStatus(postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testCreateGroupAlreadyExists() throws IOException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        assertAuthenticatedAdminPostStatus(postUrl, HttpServletResponse.SC_OK, postParams, null);

        //post the same info again, should fail
        assertAuthenticatedAdminPostStatus(postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testCreateGroupWithExtraProperties() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        postParams.add(new BasicNameValuePair("marker", testGroupId));
        postParams.add(new BasicNameValuePair("displayName", "My Test Group"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org"));
        assertAuthenticatedAdminPostStatus(postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the group profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals(testGroupId, jsonObj.getString("marker"));
        assertEquals("My Test Group", jsonObj.getString("displayName"));
        assertEquals("http://www.apache.org", jsonObj.getString("url"));
    }


    /**
     * Test for SLING-1677
     */
    @Test
    public void testCreateGroupResponseAsJSON() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        postParams.add(new BasicNameValuePair("marker", testGroupId));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
    }

    private void testCreateGroupRedirect(String redirectTo, int expectedStatus) throws IOException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        postParams.add(new BasicNameValuePair(":redirect", redirectTo));
        assertAuthenticatedAdminPostStatus(postUrl, expectedStatus, postParams, null);
    }

    @Test
    public void testCreateGroupValidRedirect() throws IOException, JsonException {
        testCreateGroupRedirect("/*.html", HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @Test
    public void testCreateGroupInvalidRedirectWithAuthority() throws IOException, JsonException {
        testCreateGroupRedirect("https://sling.apache.org", SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void testCreateGroupInvalidRedirectWithInvalidURI() throws IOException, JsonException {
        testCreateGroupRedirect("https://", SC_UNPROCESSABLE_ENTITY);
    }

}
