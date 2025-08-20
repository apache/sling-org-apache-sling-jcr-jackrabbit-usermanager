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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicNameValuePair;
import org.apache.sling.api.resource.ResourceUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletResponse;

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
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_FORBIDDEN, postParams, null);
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

    /**
     * SLING-10902 Test for group name that is not unique
     */
    @Test
    public void testCreateGroupWithAlreadyUsedName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(marker, testGroupId);

        // second time with the same info fails since it is not unique
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * SLING-10902 Test for group name that is not unique
     */
    @Test
    public void testCreateGroupWithAlreadyUsedNameValueFrom() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name@ValueFrom", "marker"));
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(marker, testGroupId);

        // second time with the same info fails since it is not unique
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    
    /**
     * SLING-10902 Test for group name generated from a hint
     */
    @Test
    public void testCreateGroupWithNameHintAndAlreadyUsedName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String hint = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", hint));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(hint.substring(0, 20), testGroupId);

        // second time with the same info generates a different unique name
        json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId2  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId2);
        assertTrue(testGroupId2.startsWith(hint.substring(0, 20)));
        assertNotEquals(testGroupId, testGroupId2);
    }


    /**
     * SLING-10902 Test for group name generated from the value of another param
     */
    @Test
    public void testCreateGroupWithNameValueFrom() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name@ValueFrom", "marker"));
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(marker, testGroupId);
    }

    /**
     * SLING-10902 Test for group name generated from a hint
     */
    @Test
    public void testCreateGroupWithNameHint() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String hint = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", hint));
        postParams.add(new BasicNameValuePair("marker", testUserId));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(hint.substring(0, 20), testGroupId);
    }

    /**
     * SLING-10902 Test for group name generated from a hint value of another param
     */
    @Test
    public void testCreateGroupWithNameHintValueFrom() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint@ValueFrom", "marker"));
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(marker.substring(0, 20), testGroupId);
    }

    /**
     * SLING-10902 Test for group name generated without a hint
     */
    @Test
    public void testCreateGroupWithNoName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * SLING-10902 Test for group name generated without a hint
     */
    @Test
    public void testCreateGroupWithEmptyName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", ""));
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * SLING-10902 Test for group name generated without a hint
     */
    @Test
    public void testCreateGroupWithEmptyNameHint() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", ""));
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }


    /**
     * SLING-10902 Test for group name generated from a default property name
     */
    @Test
    public void testCreateGroupWithNoNameAndAlternateHintProp() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(marker.substring(0, 20), testGroupId);
    }

    /**
     * SLING-10902 Test for group name generated from a default property name
     */
    @Test
    public void testCreateGroupWithEmptyNameAndAlternateHintProp() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", ""));
        postParams.add(new BasicNameValuePair("displayName", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(marker.substring(0, 20), testGroupId);
    }

    /**
     * SLING-10902 Test for group name generated from a default property name
     */
    @Test
    public void testCreateGroupWithEmptyNameHintAndAlternateHintProp() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", ""));
        postParams.add(new BasicNameValuePair("displayName", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertEquals(marker.substring(0, 20), testGroupId);
    }


    /**
     * SLING-11023 Test for setting jcr:mixinTypes values
     */
    @Test
    public void testCreateGroupMixins() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        // add nested mixin params
        postParams.add(new BasicNameValuePair("jcr:mixinTypes", "mix:lastModified"));
        postParams.add(new BasicNameValuePair("nested/jcr:mixinTypes", "mix:title"));
        postParams.add(new BasicNameValuePair("nested/again/jcr:mixinTypes", "mix:created"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        // get path
        String path = jsonObj.getString("path");
        assertNotNull(path);
        // retrieve property via regular GET servlet
        getUrl = String.format("%s%s.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertContains(jsonObj.getJsonArray("jcr:mixinTypes"), "mix:lastModified");
        // retrieve nested property via regular GET servlet
        getUrl = String.format("%s%s/nested.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertContains(jsonObj.getJsonArray("jcr:mixinTypes"), "mix:title");
        // retrieve nested/again property via regular GET servlet
        getUrl = String.format("%s%s/nested/again.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertContains(jsonObj.getJsonArray("jcr:mixinTypes"), "mix:created");
    }

    /**
     * SLING-11023 Test for setting jcr:primaryType values
     */
    @Test
    public void testCreateGroupNestedPrimaryTypes() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        // add nested primaryType params
        postParams.add(new BasicNameValuePair("nested/jcr:primaryType", "nt:unstructured"));
        postParams.add(new BasicNameValuePair("nested/again/jcr:primaryType", "oak:Unstructured"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        // get path
        String path = jsonObj.getString("path");
        assertNotNull(path);
        // retrieve nested property via regular GET servlet
        getUrl = String.format("%s%s/nested.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertEquals("nt:unstructured", jsonObj.getString("jcr:primaryType"));
        // retrieve nested/again property via regular GET servlet
        getUrl = String.format("%s%s/nested/again.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertEquals("oak:Unstructured", jsonObj.getString("jcr:primaryType"));
    }

    /**
     * SLING-11023 Test for failing attempt to set jcr:primaryType value
     */
    @Test
    public void testCreateGroupPrimaryTypeFails() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.html", baseServerUri);

        testGroupId = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testGroupId));
        // add invalid primaryType param
        postParams.add(new BasicNameValuePair("jcr:primaryType", "nt:unstructured"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_FORBIDDEN, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/group/%s.json", baseServerUri, testGroupId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns no data
        testGroupId = null;
    }

}
