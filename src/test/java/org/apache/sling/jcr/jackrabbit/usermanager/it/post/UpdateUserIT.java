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

import static org.apache.sling.testing.paxexam.SlingOptions.slingAuthForm;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.ops4j.pax.exam.CoreOptions.composite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests for the 'updateAuthorizable' and 'changePassword' Sling Post
 * Operations on a user resource.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class UpdateUserIT extends UserManagerClientTestSupport {

    @Override
    protected Option[] additionalOptions() {
        return composite(super.additionalOptions())
                .add(slingAuthForm()) // needed for testDisableUser
                .getOptions();
    }

    @Test
    public void testUpdateUser() throws IOException, JsonException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test User"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));
        // add nested param (SLING-6747)
        postParams.add(new BasicNameValuePair("nested/param", "value"));
        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals("My Updated Test User", jsonObj.getString("displayName"));
        assertEquals("http://www.apache.org/updated", jsonObj.getString("url"));
        // get path (SLING-6753)
        String path = jsonObj.getString("path");
        assertNotNull(path);
        // retrieve nested property via regular GET servlet
        getUrl = String.format("%s%s/nested.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertEquals("value", jsonObj.getString("param"));
    }

    /**
     * SLING-7901 test to verify update user delete nested property functionality
     */
    @Test
    public void testUpdateUserDeleteProperties() throws IOException, JsonException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));
        // add nested param (SLING-6747)
        postParams.add(new BasicNameValuePair("nested/param", "value"));
        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals("http://www.apache.org/updated", jsonObj.getString("url"));
        // get path (SLING-6753)
        String path = jsonObj.getString("path");
        assertNotNull(path);
        // retrieve nested property via regular GET servlet
        getUrl = String.format("%s%s/nested.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertEquals("value", jsonObj.getString("param"));

        //now remove
        postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("url@Delete", "true"));
        // remove nested param
        postParams.add(new BasicNameValuePair("nested/param@Delete", "true"));
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);


        //and verify
        getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertFalse(jsonObj.containsKey("url"));
        // get path (SLING-6753)
        path = jsonObj.getString("path");
        assertNotNull(path);
        // retrieve nested property via regular GET servlet
        getUrl = String.format("%s%s/nested.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertFalse("Nested property should not exist", jsonObj.containsKey("param"));
    }

    @Test
    public void testNotAuthorizedUpdateUser() throws IOException, JsonException {
        //a user who is not authorized to do the action
        testUserId2 = createTestUser();

        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test User"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));
        // add nested param (SLING-6747)
        postParams.add(new BasicNameValuePair("nested/param", "value"));
        Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_NOT_FOUND, null); //make sure the profile request is not there
    }

    @Test
    public void testAuthorizedUpdateUser() throws IOException, JsonException {
        //a user who is authorized to do the action
        testUserId2 = createTestUser();
        grantUserManagementRights(testUserId2);

        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test User"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));
        // add nested param (SLING-6747)
        postParams.add(new BasicNameValuePair("nested/param", "value"));
        Credentials creds = new UsernamePasswordCredentials(testUserId2, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        assertEquals("My Updated Test User", jsonObj.getString("displayName"));
        assertEquals("http://www.apache.org/updated", jsonObj.getString("url"));
        // get path (SLING-6753)
        String path = jsonObj.getString("path");
        assertNotNull(path);
        // retrieve nested property via regular GET servlet
        getUrl = String.format("%s%s/nested.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertEquals("value", jsonObj.getString("param"));
    }

    /**
     * Test for SLING-7831
     */
    @Test
    public void testUpdateUserCustomPostResponse() throws IOException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":responseType", "custom"));
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test User"));

        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        String content = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_HTML, postParams, HttpServletResponse.SC_OK);
        assertEquals("Thanks!", content); //verify that the content matches the custom response
    }

    @Test
    public void testChangeUserPassword() throws IOException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.changePassword.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("oldPwd", "testPwd"));
        postParams.add(new BasicNameValuePair("newPwd", "testNewPwd"));
        postParams.add(new BasicNameValuePair("newPwdConfirm", "testNewPwd"));

        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);
    }

    /**
     * Test for SLING-7831
     */
    @Test
    public void testChangeUserPasswordCustomPostResponse() throws IOException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.changePassword.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":responseType", "custom"));
        postParams.add(new BasicNameValuePair("oldPwd", "testPwd"));
        postParams.add(new BasicNameValuePair("newPwd", "testNewPwd"));
        postParams.add(new BasicNameValuePair("newPwdConfirm", "testNewPwd"));

        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        String content = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_HTML, postParams, HttpServletResponse.SC_OK);
        assertEquals("Thanks!", content); //verify that the content matches the custom response
    }

    @Test
    public void testChangeUserPasswordWrongOldPwd() throws IOException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.changePassword.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("oldPwd", "wrongTestPwd"));
        postParams.add(new BasicNameValuePair("newPwd", "testNewPwd"));
        postParams.add(new BasicNameValuePair("newPwdConfirm", "testNewPwd"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    @Test
    public void testChangeUserPasswordWrongConfirmPwd() throws IOException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.changePassword.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("oldPwd", "testPwd"));
        postParams.add(new BasicNameValuePair("newPwd", "testNewPwd"));
        postParams.add(new BasicNameValuePair("newPwdConfirm", "wrongTestNewPwd"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, postParams, null);
    }

    /**
     * Test for SLING-1677
     */
    @Test
    public void testUpdateUserResponseAsJSON() throws IOException, JsonException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.json", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test User"));
        postParams.add(new BasicNameValuePair("url", "http://www.apache.org/updated"));
        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        String json = getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
    }


    /**
     * Test for SLING-2069
     * @throws IOException
     */
    @Test
    public void testChangeUserPasswordAsAdministratorWithoutOldPwd() throws IOException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.changePassword.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("newPwd", "testNewPwd"));
        postParams.add(new BasicNameValuePair("newPwdConfirm", "testNewPwd"));

        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);
    }

    /**
     * Test for SLING-2072
     */
    @Test
    public void testDisableUser() throws IOException {
        testUserId = createTestUser();

        //login before the user is disabled, so login should work
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("j_username", testUserId));
        params.add(new BasicNameValuePair("j_password", "testPwd"));
        params.add(new BasicNameValuePair("j_validate", "true"));
        String postUrl = String.format("%s/j_security_check", baseServerUri);
        assertAuthenticatedPostStatus(null, postUrl, HttpServletResponse.SC_OK, params, null,
                response -> assertNull(response.getFirstHeader("X-Reason")));
        httpContext.getCredentialsProvider().clear();
        httpContext.getCookieStore().clear();

        //update the user to disable it
        postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":disabled", "true"));
        postParams.add(new BasicNameValuePair(":disabledReason", "Just Testing"));
        assertAuthenticatedAdminPostStatus(postUrl, HttpServletResponse.SC_OK, postParams, null);

        //the user is now disabled, so login should fail
        postUrl = String.format("%s/j_security_check", baseServerUri);
        assertAuthenticatedPostStatus(null, postUrl, HttpServletResponse.SC_FORBIDDEN, params, null,
                response -> assertNotNull(response.getFirstHeader("X-Reason")));
        httpContext.getCredentialsProvider().clear();
        httpContext.getCookieStore().clear();

        //enable the user again
        postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);
        postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":disabled", "false"));
        assertAuthenticatedAdminPostStatus(postUrl, HttpServletResponse.SC_OK, postParams, null);

        //login after the user is enabled, so login should work
        postUrl = String.format("%s/j_security_check", baseServerUri);
        assertAuthenticatedPostStatus(null, postUrl, HttpServletResponse.SC_OK, params, null,
                response -> assertNull(response.getFirstHeader("X-Reason")));
        httpContext.getCredentialsProvider().clear();
        httpContext.getCookieStore().clear();
    }

    private void testChangeUserPasswordRedirect(String redirectTo, int expectedStatus) throws IOException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.changePassword.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("oldPwd", "testPwd"));
        postParams.add(new BasicNameValuePair("newPwd", "testNewPwd"));
        postParams.add(new BasicNameValuePair("newPwdConfirm", "testNewPwd"));
        postParams.add(new BasicNameValuePair(":redirect", redirectTo));

        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, expectedStatus, postParams, null);
    }

    @Test
    public void testChangeUserPasswordValidRedirect() throws IOException, JsonException {
        testChangeUserPasswordRedirect("/*.html", HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @Test
    public void testChangeUserPasswordInvalidRedirectWithAuthority() throws IOException, JsonException {
        testChangeUserPasswordRedirect("https://sling.apache.org", SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void testChangeUserPasswordInvalidRedirectWithInvalidURI() throws IOException, JsonException {
        testChangeUserPasswordRedirect("https://", SC_UNPROCESSABLE_ENTITY);
    }

    private void testUpdateUserRedirect(String redirectTo, int expectedStatus) throws IOException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("displayName", "My Updated Test User"));
        postParams.add(new BasicNameValuePair(":redirect", redirectTo));
        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, expectedStatus, postParams, null);
    }

    @Test
    public void testUpdateUserValidRedirect() throws IOException, JsonException {
        testUpdateUserRedirect("/*.html", HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @Test
    public void testUpdateUserInvalidRedirectWithAuthority() throws IOException, JsonException {
        testUpdateUserRedirect("https://sling.apache.org", SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void testUpdateUserInvalidRedirectWithInvalidURI() throws IOException, JsonException {
        testUpdateUserRedirect("https://", SC_UNPROCESSABLE_ENTITY);
    }

    /**
     * SLING-11023 Test for setting jcr:mixinTypes values
     */
    @Test
    public void testUpdateUserMixins() throws IOException, JsonException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        // add nested mixin params
        postParams.add(new BasicNameValuePair("jcr:mixinTypes", "mix:lastModified"));
        postParams.add(new BasicNameValuePair("nested/jcr:mixinTypes", "mix:title"));
        postParams.add(new BasicNameValuePair("nested/again/jcr:mixinTypes", "mix:created"));
        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
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
    public void testUpdateUserNestedPrimaryTypes() throws IOException, JsonException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.html", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        // add nested primaryType params
        postParams.add(new BasicNameValuePair("nested/jcr:primaryType", "nt:unstructured"));
        postParams.add(new BasicNameValuePair("nested/again/jcr:primaryType", "oak:Unstructured"));
        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
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
    public void testUpdateUserPrimaryTypeFails() throws IOException, JsonException {
        testUserId = createTestUser();

        String postUrl = String.format("%s/system/userManager/user/%s.update.json", baseServerUri, testUserId);

        List<NameValuePair> postParams = new ArrayList<>();
        // add nested primaryType params
        postParams.add(new BasicNameValuePair("jcr:primaryType", "nt:unstructured"));
        Credentials creds = new UsernamePasswordCredentials(testUserId, "testPwd");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_FORBIDDEN, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        // get path
        String path = jsonObj.getString("path");
        assertNotNull(path);
        // retrieve content via regular GET servlet
        getUrl = String.format("%s%s.json", baseServerUri, path);
        json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        jsonObj = parseJson(json);
        assertEquals("rep:User", jsonObj.getString("jcr:primaryType"));
    }

}
