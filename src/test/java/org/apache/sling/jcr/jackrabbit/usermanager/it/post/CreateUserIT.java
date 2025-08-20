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
import static org.junit.Assert.assertFalse;
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
 * Tests for the 'createUser' Sling Post Operation
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class CreateUserIT extends UserManagerClientTestSupport {

    /*
        <form action="/system/userManager/user.create.html" method="POST">
           <div>Name: <input type="text" name=":name" value="testUser" /></div>
           <div>Password: <input type="text" name="pwd" value="testUser" /></div>
           <div>Password Confirm: <input type="text" name="pwdConfirm" value="testUser" /></div>
           <input type="submit" value="Submit" />
        </form>
     */
    @Test
    public void testCreateUser() throws IOException, JsonException {
        testUserId = "testUser" + getNextInt();
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);
        final List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("marker", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        final Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        // fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        final String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        final JsonObject jsonObj = parseJson(json);
        assertEquals(testUserId, jsonObj.getString("marker"));
        assertFalse(jsonObj.containsKey(":name"));
        assertFalse(jsonObj.containsKey("pwd"));
        assertFalse(jsonObj.containsKey("pwdConfirm"));

        // fetch the session info to verify that the user can log in
        final Credentials newUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");
        final String getUrl2 = String.format("%s/system/sling/info.sessionInfo.json", baseServerUri);
        final String json2 = getAuthenticatedContent(newUserCreds, getUrl2, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json2);
        final JsonObject jsonObj2 = parseJson(json2);
        assertEquals(testUserId, jsonObj2.getString("userID"));
    }

    @Test
    public void testNotAuthorizedCreateUser() throws IOException, JsonException {
        testUserId2 = createTestUser();

        String testUserId3 = "testUser" + getNextInt();
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);
        final List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId3));
        postParams.add(new BasicNameValuePair("marker", testUserId3));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        final Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testAuthorizedCreateUser() throws IOException, JsonException {
        testUserId2 = createTestUser();
        grantUserManagementRights(testUserId2);

        testUserId = "testUser" + getNextInt();
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);
        final List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("marker", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        final Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        // fetch the user profile json to verify the settings
        final String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        final String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        final JsonObject jsonObj = parseJson(json);
        assertEquals(testUserId, jsonObj.getString("marker"));
        assertFalse(jsonObj.containsKey(":name"));
        assertFalse(jsonObj.containsKey("pwd"));
        assertFalse(jsonObj.containsKey("pwdConfirm"));

        // fetch the session info to verify that the user can log in
        final Credentials newUserCreds = new UsernamePasswordCredentials(testUserId, "testPwd");
        final String getUrl2 = String.format("%s/system/sling/info.sessionInfo.json", baseServerUri);
        final String json2 = getAuthenticatedContent(newUserCreds, getUrl2, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json2);
        final JsonObject jsonObj2 = parseJson(json2);
        assertEquals(testUserId, jsonObj2.getString("userID"));
    }

    @Test
    public void testCreateUserMissingUserId() throws IOException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        List<NameValuePair> postParams = new ArrayList<>();
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testCreateUserMissingPwd() throws IOException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        String userId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", userId));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testCreateUserWrongConfirmPwd() throws IOException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        String userId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", userId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd2"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testCreateUserUserAlreadyExists() throws IOException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        testUserId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //post the same info again, should fail
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    /*
    <form action="/system/userManager/user.create.html" method="POST">
       <div>Name: <input type="text" name=":name" value="testUser" /></div>
       <div>Password: <input type="text" name="pwd" value="testUser" /></div>
       <div>Password Confirm: <input type="text" name="pwdConfirm" value="testUser" /></div>
       <div>Extra Property #1: <input type="text" name="displayName" value="My Test User" /></div>
       <div>Extra Property #2: <input type="text" name="url" value="http://www.apache.org" /></div>
       <input type="submit" value="Submit" />
    </form>
    */
    @Test
    public void testCreateUserWithExtraProperties() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        testUserId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("marker", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        postParams.add(new BasicNameValuePair("displayName", "My Test User"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals(testUserId, jsonObj.getString("marker"));
        assertEquals("My Test User", jsonObj.getString("displayName"));
        assertEquals("http://www.apache.org", jsonObj.getString("url"));
        assertFalse(jsonObj.containsKey(":name"));
        assertFalse(jsonObj.containsKey("pwd"));
        assertFalse(jsonObj.containsKey("pwdConfirm"));
    }

    /**
     * Test for SLING-1642 to verify that user self-registration by the anonymous
     * user is not allowed by default.
     */
    @Test
    public void testAnonymousSelfRegistrationDisabled() throws IOException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        String userId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", userId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        //user create without logging in as a privileged user should return a 500 error
        assertAuthenticatedPostStatus(null, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }


    /**
     * Test for SLING-1677
     */
    @Test
    public void testCreateUserResponseAsJSON() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        testUserId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("marker", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
    }

    /**
     * Test for SLING-7831
     */
    @Test
    public void testCreateUserCustomPostResponse() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        testUserId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":responseType", "custom"));
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String content = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_HTML, postParams, HttpServletResponse.SC_OK);
        assertEquals("Thanks!", content); //verify that the content matches the custom response
    }

    private void testCreateUserRedirect(String redirectTo, int expectedStatus) throws IOException {
        testUserId = "testUser" + getNextInt();
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);
        final List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        postParams.add(new BasicNameValuePair(":redirect", redirectTo));
        assertAuthenticatedAdminPostStatus(postUrl, expectedStatus, postParams, null);
    }

    @Test
    public void testCreateUserValidRedirect() throws IOException, JsonException {
        testCreateUserRedirect("/*.html", HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @Test
    public void testCreateUserInvalidRedirectWithAuthority() throws IOException, JsonException {
        testCreateUserRedirect("https://sling.apache.org", SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void testCreateUserInvalidRedirectWithInvalidURI() throws IOException, JsonException {
        testCreateUserRedirect("https://", SC_UNPROCESSABLE_ENTITY);
    }

    /**
     * SLING-10902 Test for user name that is not unique
     */
    @Test
    public void testCreateUserWithAlreadyUsedName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(marker, testUserId);

        // second time with the same info fails since it is not unique
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * SLING-10902 Test for user name that is not unique
     */
    @Test
    public void testCreateUserWithAlreadyUsedNameValueFrom() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name@ValueFrom", "marker"));
        postParams.add(new BasicNameValuePair("marker", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(marker, testUserId);

        // second time with the same info fails since it is not unique
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * SLING-10902 Test for user name generated from a hint
     */
    @Test
    public void testCreateUserWithNameHintAndAlreadyUsedName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String hint = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", hint));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(hint.substring(0, 20), testUserId);

        // second time with the same info generates a different unique name
        json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId2  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId2);
        assertTrue(testUserId2.startsWith(hint.substring(0, 20)));
        assertNotEquals(testUserId, testUserId2);
    }


    /**
     * SLING-10902 Test for user name generated from the value of another param
     */
    @Test
    public void testCreateUserWithNameValueFrom() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name@ValueFrom", "marker"));
        postParams.add(new BasicNameValuePair("marker", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(marker, testUserId);
    }

    /**
     * SLING-10902 Test for user name generated from a hint
     */
    @Test
    public void testCreateUserWithNameHint() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String hint = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", hint));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(hint.substring(0, 20), testUserId);
    }

    /**
     * SLING-10902 Test for user name generated from a hint value of another param
     */
    @Test
    public void testCreateUserWithNameHintValueFrom() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint@ValueFrom", "marker"));
        postParams.add(new BasicNameValuePair("marker", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(marker.substring(0, 20), testUserId);
    }

    /**
     * SLING-10902 Test for user name generated without a hint but one of the alternate name hint
     * properties is supplied
     */
    @Test
    public void testCreateUserWithNoName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * SLING-10902 Test for user name generated without a hint but one of the alternate name hint
     * properties is supplied
     */
    @Test
    public void testCreateUserWithEmptyName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", ""));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * SLING-10902 Test for user name generated without a hint but one of the alternate name hint
     * properties is supplied
     */
    @Test
    public void testCreateUserWithEmptyNameHint() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", ""));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * SLING-10902 Test for user name generated without a hint but one of the alternate name hint
     * properties is supplied
     */
    @Test
    public void testCreateUserWithNoNameAndAlternateHintProp() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(marker.substring(0, 20), testUserId);
    }

    /**
     * SLING-10902 Test for user name generated without a hint but one of the alternate name hint
     * properties is supplied
     */
    @Test
    public void testCreateUserWithEmptyNameAndAlternateHintProp() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", ""));
        postParams.add(new BasicNameValuePair("displayName", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(marker.substring(0, 20), testUserId);
    }

    /**
     * SLING-10902 Test for user name generated without a hint but one of the alternate name hint
     * properties is supplied
     */
    @Test
    public void testCreateUserWithEmptyNameHintAndAlternateHintProp() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", ""));
        postParams.add(new BasicNameValuePair("displayName", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId  = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertEquals(marker.substring(0, 20), testUserId);
    }

    /**
     * SLING-11023 Test for setting jcr:mixinTypes values
     */
    @Test
    public void testCreateUserMixins() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        testUserId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        postParams.add(new BasicNameValuePair("jcr:mixinTypes", "mix:lastModified"));
        postParams.add(new BasicNameValuePair("nested/jcr:mixinTypes", "mix:title"));
        postParams.add(new BasicNameValuePair("nested/again/jcr:mixinTypes", "mix:created"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
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
    public void testCreateUserNestedPrimaryTypes() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        testUserId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        postParams.add(new BasicNameValuePair("nested/jcr:primaryType", "nt:unstructured"));
        postParams.add(new BasicNameValuePair("nested/again/jcr:primaryType", "oak:Unstructured"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
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
    public void testCreateUserPrimaryTypeFails() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        testUserId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        postParams.add(new BasicNameValuePair("jcr:primaryType", "nt:unstructured"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_FORBIDDEN, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request returns no data
        testUserId = null;
    }

}
