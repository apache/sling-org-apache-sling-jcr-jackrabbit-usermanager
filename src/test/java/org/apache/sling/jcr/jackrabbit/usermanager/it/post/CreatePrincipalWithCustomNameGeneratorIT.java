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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicNameValuePair;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the 'createUser' and 'createGroup' Sling Post Operation
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class CreatePrincipalWithCustomNameGeneratorIT extends UserManagerClientTestSupport {

    protected ServiceRegistration<PrincipalNameGenerator> principalNameGeneratorServiceReg;
    protected ServiceRegistration<PrincipalNameFilter> principalNameFilterServiceReg;

    @Override
    public void before() throws IOException, URISyntaxException {
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        Dictionary<String, Object> props = new Hashtable<>(); // NOSONAR
        props.put(Constants.SERVICE_RANKING, 1);
        principalNameGeneratorServiceReg = bundle.getBundleContext()
                .registerService(PrincipalNameGenerator.class, new CustomPrincipalNameGeneratorImpl(), props);

        Dictionary<String, Object> props2 = new Hashtable<>(); // NOSONAR
        props2.put(Constants.SERVICE_RANKING, 1);
        principalNameFilterServiceReg = bundle.getBundleContext()
                .registerService(PrincipalNameFilter.class, new CustomPrincipalNameFilterImpl(), props2);

        super.before();
    }

    @Override
    public void after() throws IOException {
        if (principalNameGeneratorServiceReg != null) {
            principalNameGeneratorServiceReg.unregister();
            principalNameGeneratorServiceReg = null;
        }
        if (principalNameFilterServiceReg != null) {
            principalNameFilterServiceReg.unregister();
            principalNameFilterServiceReg = null;
        }

        super.after();
    }

    /**
     * Test for user name generated from a hint
     */
    @Test
    public void testCreateUserWithNameHint() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String hint = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", hint));
        postParams.add(new BasicNameValuePair("marker", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json =
                getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        // make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertTrue(testUserId.startsWith("custom_user_"));
    }

    /**
     * Test for user name generated from a hint value of another param
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
        String json =
                getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        // make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertTrue(testUserId.startsWith("custom_user_"));
    }

    /**
     * Test for user name generated without a hint
     */
    @Test
    public void testCreateUserWithNoName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("marker", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(
                creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Test for user name generated without a hint
     */
    @Test
    public void testCreateUserWithEmptyName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", ""));
        postParams.add(new BasicNameValuePair("marker", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(
                creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Test for user name generated without a hint
     */
    @Test
    public void testCreateUserWithEmptyNameHint() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", ""));
        postParams.add(new BasicNameValuePair("marker", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(
                creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Test for user name generated from a default property name
     */
    @Test
    public void testCreateUserWithHintFromDefaultPropertyName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.json", baseServerUri);

        String marker = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("marker", marker));
        postParams.add(new BasicNameValuePair("displayName", marker));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json =
                getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        // make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testUserId = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testUserId);
        assertTrue(testUserId.startsWith("custom_user_"));
    }

    /**
     * Test for group name generated from a hint
     */
    @Test
    public void testCreateGroupWithNameHint() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String hint = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", hint));
        postParams.add(new BasicNameValuePair("marker", testUserId));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json =
                getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        // make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertTrue(testGroupId.startsWith("custom_group_"));
    }

    /**
     * Test for group name generated from a hint value of another param
     */
    @Test
    public void testCreateGroupWithNameHintValueFrom() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint@ValueFrom", "marker"));
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json =
                getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        // make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertTrue(testGroupId.startsWith("custom_group_"));
    }

    /**
     * Test for group name generated without a hint
     */
    @Test
    public void testCreateGroupWithNoName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(
                creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Test for group name generated without a hint
     */
    @Test
    public void testCreateGroupWithEmptyName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", ""));
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(
                creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Test for group name generated without a hint
     */
    @Test
    public void testCreateGroupWithEmptyNameHint() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":nameHint", ""));
        postParams.add(new BasicNameValuePair("marker", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        getAuthenticatedPostContent(
                creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Test for group name generated from a default property name
     */
    @Test
    public void testCreateGroupWithHintFromDefaultPropertyName() throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/group.create.json", baseServerUri);

        String marker = "testGroup" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("marker", marker));
        postParams.add(new BasicNameValuePair("displayName", marker));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        String json =
                getAuthenticatedPostContent(creds, postUrl, CONTENT_TYPE_JSON, postParams, HttpServletResponse.SC_OK);

        // make sure the json response can be parsed as a JSON object
        JsonObject jsonObj = parseJson(json);
        assertNotNull(jsonObj);
        testGroupId = ResourceUtil.getName(jsonObj.getString("path"));
        assertNotNull(testGroupId);
        assertTrue(testGroupId.startsWith("custom_group_"));
    }
}
