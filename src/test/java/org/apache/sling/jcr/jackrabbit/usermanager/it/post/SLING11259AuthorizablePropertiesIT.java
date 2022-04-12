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
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicNameValuePair;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;


/**
 * Test to verify that setting authorizable properties can determine
 * the required property types via various techniques
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class SLING11259AuthorizablePropertiesIT extends UserManagerClientTestSupport {

    @Inject
    protected SlingRepository repository;

    @Override
    protected Option[] additionalOptions() {
        return composite(super.additionalOptions())
                .add(newConfiguration("org.apache.sling.jackrabbit.usermanager.impl.resource.AuthorizableResourceProvider")
                        .put("resources.for.nested.properties", true).asOption())
                .getOptions();
    }

    /**
     * Override to:
     * 1. register custom node types
     */
    @Before
    public void before() throws IOException, URISyntaxException {
        super.before();
        try {
            // register our custom node types
            Session adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));

            try (InputStream is = getClass().getResourceAsStream("sling11259.cnd");
                    Reader reader = new InputStreamReader(is)) {
                CndImporter.registerNodeTypes(reader, adminSession);
            }
        } catch (ParseException | RepositoryException re) {
            fail("Unexpected error while registering custom node type. Reason: " + re.getMessage());
        }
    }

    /**
     * Test for setting properties with unstructured property types
     */
    @Test
    public void testCreateUserPropsWithUndefinedPropertyTypes() throws IOException, JsonException {
        createUserPropsWithPropertyTypes(Collections.emptyList(),
                VERFIY_WEAKLY_TYPED_JSON);
    }

    /**
     * Test for setting properties with property types provided with @TypeHint request parameters
     */
    @Test
    public void testCreateUserPropsWithTypeHints() throws IOException, JsonException {
        createUserPropsWithPropertyTypes(Arrays.asList(
                    new BasicNameValuePair("nested/booleanProp@TypeHint", "Boolean"),
                    new BasicNameValuePair("nested/longProp@TypeHint", "Long"),
                    new BasicNameValuePair("nested/booleanMultiProp1@TypeHint", "Boolean[]"),
                    new BasicNameValuePair("nested/longMultiProp1@TypeHint", "Long[]"),
                    new BasicNameValuePair("nested/booleanMultiProp2@TypeHint", "Boolean[]"),
                    new BasicNameValuePair("nested/longMultiProp2@TypeHint", "Long[]")
                ),
                VERIFY_STONGLY_TYPED_JSON);
    }

    /**
     * Test for setting properties with property types determined by inspecting the primary type
     * definition of the parent node
     */
    @Test
    public void testCreateUserPropsWithPropertyTypesDefinedByPrimaryType() throws IOException, JsonException {
        createUserPropsWithPropertyTypes(Collections.singletonList(new BasicNameValuePair("nested/jcr:primaryType", "sling11259:userPrivate")),
                VERIFY_STONGLY_TYPED_JSON);
    }

    /**
     * Test for setting properties with property types not determined by inspecting the primary type
     * definition of the parent node
     */
    @Test
    public void testCreateUserPropsWithPropertyTypesNotDefinedByPrimaryType() throws IOException, JsonException {
        createUserPropsWithPropertyTypes(Collections.singletonList(new BasicNameValuePair("nested/jcr:primaryType", "nt:unstructured")),
                VERFIY_WEAKLY_TYPED_JSON);
    }

    /**
     * Test for setting properties with property types determined by inspecting the mixin type
     * definition of the parent node
     */
    @Test
    public void testCreateUserPropsWithPropertyTypesDefinedByMixinType() throws IOException, JsonException {
        createUserPropsWithPropertyTypes(Collections.singletonList(new BasicNameValuePair("nested/jcr:mixinTypes", "sling11259:userPublic")),
                VERIFY_STONGLY_TYPED_JSON);
    }

    private void createUserPropsWithPropertyTypes(List<NameValuePair> extraPostParams,
            Consumer<JsonObject> verifyJsonObject) throws IOException, JsonException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        testUserId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", testUserId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        postParams.addAll(extraPostParams);
        postParams.add(new BasicNameValuePair("nested/booleanProp", "true"));
        postParams.add(new BasicNameValuePair("nested/longProp", "1234"));
        postParams.add(new BasicNameValuePair("nested/booleanMultiProp1", "true"));
        postParams.add(new BasicNameValuePair("nested/longMultiProp1", "1234"));
        postParams.add(new BasicNameValuePair("nested/booleanMultiProp2", "true"));
        postParams.add(new BasicNameValuePair("nested/booleanMultiProp2", "false"));
        postParams.add(new BasicNameValuePair("nested/longMultiProp2", "1234"));
        postParams.add(new BasicNameValuePair("nested/longMultiProp2", "5678"));
        postParams.add(new BasicNameValuePair("nested/undefinedBooleanProp", "true"));
        Credentials creds = new UsernamePasswordCredentials("admin", "admin");
        assertAuthenticatedPostStatus(creds, postUrl, HttpServletResponse.SC_OK, postParams, null);

        //fetch the user profile json to verify the settings
        String getUrl = String.format("%s/system/userManager/user/%s/nested.json", baseServerUri, testUserId);
        assertAuthenticatedHttpStatus(creds, getUrl, HttpServletResponse.SC_OK, null); //make sure the profile request returns some data
        String json = getAuthenticatedContent(creds, getUrl, CONTENT_TYPE_JSON, HttpServletResponse.SC_OK);
        assertNotNull(json);
        JsonObject jsonObj = parseJson(json);
        verifyJsonObject.accept(jsonObj);
    }

    private static final Consumer<JsonObject> VERIFY_STONGLY_TYPED_JSON = jsonObj -> {
        // should be a boolean prop in this context
        assertEquals(ValueType.TRUE, jsonObj.get("booleanProp").getValueType());
        assertEquals(true, jsonObj.getBoolean("booleanProp"));

        // should be a number prop in this context
        assertEquals(ValueType.NUMBER, jsonObj.get("longProp").getValueType());
        assertEquals(1234L, jsonObj.getJsonNumber("longProp").longValue());

        // should be a boolean prop in this context
        assertEquals(ValueType.TRUE, jsonObj.get("booleanMultiProp1").getValueType());
        assertEquals(true, jsonObj.getBoolean("booleanMultiProp1"));

        // should be a number prop in this context
        assertEquals(ValueType.NUMBER, jsonObj.get("longMultiProp1").getValueType());
        assertEquals(1234L, jsonObj.getJsonNumber("longMultiProp1").longValue());

        // should be a multi-boolean prop in this context
        JsonValue booleanMultiPropValue = jsonObj.get("booleanMultiProp2");
        assertEquals(ValueType.ARRAY, booleanMultiPropValue.getValueType());
        assertEquals(2, ((JsonArray)booleanMultiPropValue).size());
        assertEquals(true, ((JsonArray)booleanMultiPropValue).getBoolean(0));
        assertEquals(false, ((JsonArray)booleanMultiPropValue).getBoolean(1));

        // should be a number prop in this context
        JsonValue longMultiPropValue = jsonObj.get("longMultiProp2");
        assertEquals(ValueType.ARRAY, longMultiPropValue.getValueType());
        assertEquals(2, ((JsonArray)longMultiPropValue).size());
        assertEquals(1234L, ((JsonArray)longMultiPropValue).getJsonNumber(0).longValue());
        assertEquals(5678L, ((JsonArray)longMultiPropValue).getJsonNumber(1).longValue());

        // should be a string prop in this context
        assertEquals(ValueType.STRING, jsonObj.get("undefinedBooleanProp").getValueType());
        assertEquals("true", jsonObj.getString("undefinedBooleanProp"));
    };

    private static final Consumer<JsonObject> VERFIY_WEAKLY_TYPED_JSON = jsonObj -> {
        // should be a string prop in this context
        assertEquals(ValueType.STRING, jsonObj.get("booleanProp").getValueType());
        assertEquals("true", jsonObj.getString("booleanProp"));

        // should be a string prop in this context
        assertEquals(ValueType.STRING, jsonObj.get("longProp").getValueType());
        assertEquals("1234", jsonObj.getString("longProp"));

        // should be a boolean prop in this context
        assertEquals(ValueType.STRING, jsonObj.get("booleanMultiProp1").getValueType());
        assertEquals("true", jsonObj.getString("booleanMultiProp1"));

        // should be a string prop in this context
        assertEquals(ValueType.STRING, jsonObj.get("longMultiProp1").getValueType());
        assertEquals("1234", jsonObj.getString("longMultiProp1"));

        // should be a multi-string prop in this context
        JsonValue booleanMultiPropValue = jsonObj.get("booleanMultiProp2");
        assertEquals(ValueType.ARRAY, booleanMultiPropValue.getValueType());
        assertEquals(2, ((JsonArray)booleanMultiPropValue).size());
        assertEquals("true", ((JsonArray)booleanMultiPropValue).getString(0));
        assertEquals("false", ((JsonArray)booleanMultiPropValue).getString(1));

        // should be a string prop in this context
        JsonValue longMultiPropValue = jsonObj.get("longMultiProp2");
        assertEquals(ValueType.ARRAY, longMultiPropValue.getValueType());
        assertEquals(2, ((JsonArray)longMultiPropValue).size());
        assertEquals("1234", ((JsonArray)longMultiPropValue).getString(0));
        assertEquals("5678", ((JsonArray)longMultiPropValue).getString(1));

        // should be a string prop in this context
        assertEquals(ValueType.STRING, jsonObj.get("undefinedBooleanProp").getValueType());
        assertEquals("true", jsonObj.getString("undefinedBooleanProp"));
    };

}
