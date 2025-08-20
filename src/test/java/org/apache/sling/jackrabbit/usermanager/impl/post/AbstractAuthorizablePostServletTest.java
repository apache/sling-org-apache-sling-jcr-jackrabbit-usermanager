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
package org.apache.sling.jackrabbit.usermanager.impl.post;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator;
import org.apache.sling.jackrabbit.usermanager.impl.resource.AuthorizableResourceProvider;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.servlets.post.JakartaPostResponse;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.impl.helper.RequestProperty;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 */
public class AbstractAuthorizablePostServletTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

    private TestAuthorizablePostServlet taps = new TestAuthorizablePostServlet();

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#bindSystemUserManagerPaths(org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths)}.
     */
    @Test
    public void testBindSystemUserManagerPaths() {
        SystemUserManagerPaths symp = Mockito.mock(SystemUserManagerPaths.class);
        taps.bindSystemUserManagerPaths(symp);
        assertSame(symp, taps.systemUserManagerPaths);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#bindPrincipalNameGenerator(org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator, java.util.Map)}.
     */
    @Test
    public void testBindPrincipalNameGenerator() {
        PrincipalNameGenerator png = Mockito.mock(PrincipalNameGenerator.class);
        taps.bindPrincipalNameGenerator(png, Map.of());
        assertEquals(1, taps.principalNameGenerators.size());
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#unbindPrincipalNameGenerator(org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator)}.
     */
    @Test
    public void testUnbindPrincipalNameGenerator() {
        PrincipalNameGenerator png = Mockito.mock(PrincipalNameGenerator.class);
        taps.bindPrincipalNameGenerator(png, Map.of());
        assertEquals(1, taps.principalNameGenerators.size());
        taps.unbindPrincipalNameGenerator(png);
        assertTrue(taps.principalNameGenerators.isEmpty());
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#bindPrincipalNameFilter(org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter)}.
     */
    @Test
    public void testBindPrincipalNameFilter() {
        PrincipalNameFilter pnf = Mockito.mock(PrincipalNameFilter.class);
        taps.bindPrincipalNameFilter(pnf);
        assertSame(pnf, taps.principalNameFilter);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#unbindPrincipalNameFilter(org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter)}.
     */
    @Test
    public void testUnbindPrincipalNameFilter() {
        PrincipalNameFilter pnf = Mockito.mock(PrincipalNameFilter.class);
        taps.bindPrincipalNameFilter(pnf);
        assertSame(pnf, taps.principalNameFilter);

        // wrong object doesn't change it
        taps.unbindPrincipalNameFilter(Mockito.mock(PrincipalNameFilter.class));
        assertSame(pnf, taps.principalNameFilter);

        // same object clears the field
        taps.unbindPrincipalNameFilter(pnf);
        assertNull(taps.principalNameFilter);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#getOrGeneratePrincipalName(javax.jcr.Session, java.util.Map, org.apache.jackrabbit.oak.spi.security.user.AuthorizableType)}.
     */
    @Test
    public void testGetOrGeneratePrincipalName() throws RepositoryException {
        Session jcrSession = context.resourceResolver().adaptTo(Session.class);

        // try with no bound PrincipalNameGenerator
        assertEquals("test", taps.getOrGeneratePrincipalName(jcrSession, 
                Map.of(SlingPostConstants.RP_NODE_NAME, "test"), AuthorizableType.USER));
        assertEquals("test1", taps.getOrGeneratePrincipalName(jcrSession, 
                Map.of(SlingPostConstants.RP_NODE_NAME, new String[] {"test1"}), AuthorizableType.USER));
        assertNull(taps.getOrGeneratePrincipalName(jcrSession, 
                Map.of(SlingPostConstants.RP_NODE_NAME, new String[] {"test1", "test2"}), AuthorizableType.USER));
        assertNull(taps.getOrGeneratePrincipalName(jcrSession, 
                Map.of(SlingPostConstants.RP_NODE_NAME, new Object()), AuthorizableType.USER));

        // try with bound PrincipalNameGenerator
        taps.bindPrincipalNameGenerator(new PrincipalNameGeneratorImpl(), Map.of());
        assertEquals("test", taps.getOrGeneratePrincipalName(jcrSession, 
                Map.of(SlingPostConstants.RP_NODE_NAME, "test"), AuthorizableType.USER));
        assertEquals("test", taps.getOrGeneratePrincipalName(jcrSession, 
                Map.of(SlingPostConstants.RP_NODE_NAME_HINT, "test"), AuthorizableType.USER));

        // create a user to trigger unique name calculation
        UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
        um.createUser("test", "test");
        String name = taps.getOrGeneratePrincipalName(jcrSession,
                Map.of(SlingPostConstants.RP_NODE_NAME_HINT, "test"), AuthorizableType.USER);
        assertTrue(name.matches("test_\\d+"));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#collectContentMap(java.util.Map)}.
     */
    @Test
    public void testCollectContentMapWithSkipParam() {
        Map<String, RequestProperty> contentMap = taps.collectContentMap(Map.of(
                "_charset_", "UTF-8",
                ":item1", "value1",
                "./item2/../subitem2", "value2",
                "item3", "value3"
                ));
        assertTrue(contentMap.isEmpty());
    }
    @Test
    public void testCollectContentMap() {
        Map<String, RequestProperty> contentMap = taps.collectContentMap(Map.of(
                "item1", "value1",
                "item1@TypeHint", "String",
                "item2@DefaultValue", "defValue2",
                "item3@ValueFrom", "item1",
                "item4@Delete", "true",
                "item5@MoveFrom", "/tmp/item5",
                "item6@CopyFrom", "/tmp/item6"
                ));
        assertEquals(4, contentMap.size());
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#processDeletes(org.apache.jackrabbit.api.security.user.Authorizable, java.util.Collection, java.util.List)}.
     */
    @Test
    public void testProcessDeletes() throws RepositoryException {
        Session jcrSession = context.resourceResolver().adaptTo(Session.class);
        ValueFactory vf = jcrSession.getValueFactory();
        UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
        Authorizable user = um.createUser("test", "test");
        user.setProperty("prop1", vf.createValue("value1"));
        user.setProperty("prop2", vf.createValue("value2"));
        // existing prop with delete set to true
        RequestProperty reqProp1 = new RequestProperty("/prop1");
        reqProp1.setDelete(true);
        // existing prop with delete set to false
        RequestProperty reqProp2 = new RequestProperty("/prop2");
        reqProp2.setDelete(false);
        // not existing prop
        RequestProperty reqProp3 = new RequestProperty("/prop3");
        reqProp3.setDelete(true);
        List<RequestProperty> reqProperties = List.of(
                reqProp1, reqProp2
                );
        List<Modification> changes = new ArrayList<>();
        taps.processDeletes(user, reqProperties, changes);
        assertEquals(1, changes.size());
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#concatPath(java.lang.String, java.lang.String)}.
     */
    @Test
    public void testConcatPath() {
        assertEquals("/parent/child", taps.concatPath("/parent", "child"));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#processCreate(javax.jcr.Session, org.apache.jackrabbit.api.security.user.Authorizable, java.util.Map, java.util.List)}.
     */
    @Test
    public void testProcessCreate() throws RepositoryException {
        Session jcrSession = context.resourceResolver().adaptTo(Session.class);
        UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
        Authorizable user = um.createUser("test", "test");

        String relPath1 = "/subnode1/prop1";
        RequestProperty reqProp1 = toRequestProperty(relPath1, "value1");
        String absPath1 = user.getPath() + relPath1;

        Map<String, RequestProperty> reqProperties = Map.of(
                absPath1, reqProp1
                );
        List<Modification> changes = new ArrayList<>();
        taps.processCreate(jcrSession, user, reqProperties, changes);
        assertTrue(changes.isEmpty());
    }
    @Test
    public void testProcessCreateWithPrimaryTypeOnRoot() throws RepositoryException {
        Session jcrSession = context.resourceResolver().adaptTo(Session.class);
        UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
        Authorizable user = um.createUser("test", "test");

        String relPath1 = "/" + JcrConstants.JCR_PRIMARYTYPE;
        RequestProperty reqProp1 = toRequestProperty(relPath1, "value1");
        String absPath1 = user.getPath() + relPath1;

        Map<String, RequestProperty> reqProperties = Map.of(
                absPath1, reqProp1
                );
        List<Modification> changes = new ArrayList<>();
        assertThrows(AccessDeniedException.class, () -> taps.processCreate(jcrSession, user, reqProperties, changes));
    }
    @Test
    public void testProcessCreateWithPrimaryType() throws RepositoryException {
        Session jcrSession = context.resourceResolver().adaptTo(Session.class);
        UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
        Authorizable user = um.createUser("test", "test");

        String relPath = "/subnode1/" + JcrConstants.JCR_PRIMARYTYPE;
        RequestProperty reqProp1 = toRequestProperty(relPath, "nt:folder");
        String absPath = user.getPath() + relPath;
        Map<String, RequestProperty> reqProperties = Map.of(
                absPath, reqProp1
                );
        List<Modification> changes = new ArrayList<>();
        taps.processCreate(jcrSession, user, reqProperties, changes);
        assertEquals(1, changes.size());

        // call again should have no changes
        changes.clear();
        taps.processCreate(jcrSession, user, reqProperties, changes);
        assertTrue(changes.isEmpty());
    }
    @Test
    public void testProcessCreateWithMixin() throws RepositoryException {
        Session jcrSession = context.resourceResolver().adaptTo(Session.class);
        UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
        Authorizable user = um.createUser("test", "test");

        String relPath1 = "/" + JcrConstants.JCR_MIXINTYPES;
        RequestProperty reqProp1 = toRequestProperty(relPath1, "mix:mixin1");
        String absPath1 = user.getPath() + relPath1;

        String relPath2 = "/subnode1/" + JcrConstants.JCR_MIXINTYPES;
        RequestProperty reqProp2 = toRequestProperty(relPath2, "mix:mixin1");
        String absPath2 = user.getPath() + relPath2;

        String relPath3 = "/subnode2/" + JcrConstants.JCR_MIXINTYPES;
        RequestProperty reqProp3 = new RequestProperty(relPath3);
        String absPath3 = user.getPath() + relPath3;

        Map<String, RequestProperty> reqProperties = Map.of(
                absPath1, reqProp1,
                absPath2, reqProp2,
                absPath3, reqProp3
                );
        List<Modification> changes = new ArrayList<>();
        taps.processCreate(jcrSession, user, reqProperties, changes);
        assertEquals(4, changes.size());

        // call again should have no changes
        changes.clear();
        taps.processCreate(jcrSession, user, reqProperties, changes);
        assertTrue(changes.isEmpty());
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#writeContent(javax.jcr.Session, org.apache.jackrabbit.api.security.user.Authorizable, java.util.Collection, java.util.List)}.
     */
    @Test
    public void testWriteContent() throws RepositoryException {
        mockSystemUserManagerPaths();

        Session jcrSession = context.resourceResolver().adaptTo(Session.class);
        List<Modification> changes = new ArrayList<>();

        UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
        User user = um.createUser("test", "test");

        String relPath = "/subnode1/key1";
        RequestProperty reqProp1 = toRequestProperty(relPath, "value1");
        List<RequestProperty> reqProperties = List.of(reqProp1);
        taps.writeContent(jcrSession, user, reqProperties, changes);
        assertEquals(1, changes.size());
    }

    @Test
    public void testWriteContentWithIgnoredSpecialProps() throws RepositoryException {
        mockSystemUserManagerPaths();

        Session jcrSession = context.resourceResolver().adaptTo(Session.class);
        List<Modification> changes = new ArrayList<>();

        UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
        User user = um.createUser("testUser", "test");

        RequestProperty reqProp1 = toRequestProperty( "/subnode1/" + JcrConstants.JCR_PRIMARYTYPE, "value1");
        RequestProperty reqProp2 = toRequestProperty("/subnode1/" + JcrConstants.JCR_MIXINTYPES, "value1");

        RequestProperty reqProp3 = toRequestProperty("/userId", "value1");
        RequestProperty reqProp4 = toRequestProperty("/pwd", "value1");
        RequestProperty reqProp5 = toRequestProperty("/pwdConfirm", "value1");

        List<RequestProperty> reqProperties = List.of(reqProp1, reqProp2,
                reqProp3, reqProp4, reqProp5);
        taps.writeContent(jcrSession, user, reqProperties, changes);
        assertTrue(changes.isEmpty());

        Group group = um.createGroup("testGroup");

        RequestProperty reqProp6 = toRequestProperty("/groupId", "value1");
        // also a file upload to ignore
        String reqProp7RelPath = "/fileUpload";
        RequestProperty reqProp7 = new RequestProperty(reqProp7RelPath);
        reqProp7.setValues(new RequestParameter[] {
                Builders.newRequestParameter(reqProp7RelPath, "value1".getBytes(), "filename.txt", "text/plain")
                });

        reqProperties = List.of(reqProp1, reqProp2,
                reqProp6, reqProp7);
        changes.clear();
        taps.writeContent(jcrSession, group, reqProperties, changes);
        assertTrue(changes.isEmpty());
    
    }

    protected RequestProperty toRequestProperty(String relPath, String value) {
        RequestProperty reqProp1 = new RequestProperty(relPath);
        reqProp1.setValues(new RequestParameter[] {
                Builders.newRequestParameter(relPath, value)
                });
        return reqProp1;
    }

    /**
     * 
     */
    protected void mockSystemUserManagerPaths() {
        SystemUserManagerPaths mockSump = Mockito.mock(SystemUserManagerPaths.class);
        Mockito.when(mockSump.getUserPrefix()).thenReturn(String.format("%s/user/", AuthorizableResourceProvider.DEFAULT_SYSTEM_USER_MANAGER_PATH));
        Mockito.when(mockSump.getGroupPrefix()).thenReturn(String.format("%s/group/", AuthorizableResourceProvider.DEFAULT_SYSTEM_USER_MANAGER_PATH));
        taps.bindSystemUserManagerPaths(mockSump);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#hasItemPathPrefix(java.lang.String)}.
     */
    @Test
    public void testHasItemPathPrefix() {
        assertTrue(taps.hasItemPathPrefix("/test"));
        assertTrue(taps.hasItemPathPrefix("./test"));
        assertTrue(taps.hasItemPathPrefix("../test"));
        assertFalse(taps.hasItemPathPrefix("test"));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#requireItemPathPrefix(java.util.Map)}.
     */
    @Test
    public void testRequireItemPathPrefix() {
        assertFalse(taps.requireItemPathPrefix(Map.of()));

        assertTrue(taps.requireItemPathPrefix(Map.of("./test", "value")));

        assertFalse(taps.requireItemPathPrefix(Map.of("test1", "value1", "test2", "value2")));

        Map<String, String> orderedMap = new LinkedHashMap<>();
        orderedMap.put("test1", "value1");
        orderedMap.put("./test2", "value2");
        orderedMap.put("test3", "value3");
        assertTrue(taps.requireItemPathPrefix(orderedMap));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#convertToString(java.lang.Object)}.
     */
    @Test
    public void testConvertToString() {
        // value as string
        String str = taps.convertToString("value");
        assertNotNull(str);
        assertEquals("value", str);

        // value as string[]
        str = taps.convertToString(new String[] {"value1", "value2"});
        assertNotNull(str);
        assertEquals("value1", str);
        str = taps.convertToString(new String[0]);
        assertNull(str);

        // value as RequestParameter
        str = taps.convertToString(Builders.newRequestParameter("param1", "value"));
        assertNotNull(str);
        assertEquals("value", str);

        // value as RequestParameter[]
        str = taps.convertToString(new RequestParameter[] {
                Builders.newRequestParameter("param1", "value1"),
                Builders.newRequestParameter("param1", "value2")});
        assertNotNull(str);
        assertEquals("value1", str);
        str = taps.convertToString(new RequestParameter[0]);
        assertNull(str);

        // value as other type
        str = taps.convertToString(new Object());
        assertNull(str);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#convertToStringArray(java.lang.Object)}.
     */
    @Test
    public void testConvertToStringArray() {
        // value as string
        String[] strArray = taps.convertToStringArray("value");
        assertNotNull(strArray);
        assertEquals(1, strArray.length);
        assertEquals("value", strArray[0]);

        // value as string[]
        strArray = taps.convertToStringArray(new String[] {"value1", "value2"});
        assertNotNull(strArray);
        assertEquals(2, strArray.length);
        assertEquals("value1", strArray[0]);
        assertEquals("value2", strArray[1]);

        // value as RequestParameter
        strArray = taps.convertToStringArray(Builders.newRequestParameter("param1", "value"));
        assertNotNull(strArray);
        assertEquals(1, strArray.length);
        assertEquals("value", strArray[0]);

        // value as RequestParameter[]
        strArray = taps.convertToStringArray(new RequestParameter[] {
                Builders.newRequestParameter("param1", "value1"),
                Builders.newRequestParameter("param1", "value2")});
        assertNotNull(strArray);
        assertEquals(2, strArray.length);
        assertEquals("value1", strArray[0]);
        assertEquals("value2", strArray[1]);

        // value as other type
        strArray = taps.convertToStringArray(new Object());
        assertNotNull(strArray);
        assertEquals(0, strArray.length);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#convertToRequestParameterArray(java.lang.String, java.lang.Object)}.
     */
    @Test
    public void testConvertToRequestParameterArray() {
        // value as string
        RequestParameter[] rpArray = taps.convertToRequestParameterArray("param1", "value");
        assertNotNull(rpArray);
        assertEquals(1, rpArray.length);
        assertEquals("value", rpArray[0].getString());

        // value as string[]
        rpArray = taps.convertToRequestParameterArray("param1", new String[] {"value1", "value2"});
        assertNotNull(rpArray);
        assertEquals(2, rpArray.length);
        assertEquals("value1", rpArray[0].getString());
        assertEquals("value2", rpArray[1].getString());

        // value as RequestParameter
        rpArray = taps.convertToRequestParameterArray("param1", Builders.newRequestParameter("param1", "value"));
        assertNotNull(rpArray);
        assertEquals(1, rpArray.length);
        assertEquals("value", rpArray[0].getString());

        // value as RequestParameter[]
        rpArray = taps.convertToRequestParameterArray("param1", new RequestParameter[] {
                Builders.newRequestParameter("param1", "value1"),
                Builders.newRequestParameter("param1", "value2")});
        assertNotNull(rpArray);
        assertEquals(2, rpArray.length);
        assertEquals("value1", rpArray[0].getString());
        assertEquals("value2", rpArray[1].getString());

        // value as other type
        rpArray = taps.convertToRequestParameterArray("param1", new Object());
        assertNotNull(rpArray);
        assertEquals(0, rpArray.length);
    }


    private class TestAuthorizablePostServlet extends AbstractAuthorizablePostServlet {
        private static final long serialVersionUID = -2948341218853558959L;

        @Override
        protected void handleOperation(SlingJakartaHttpServletRequest request, JakartaPostResponse response,
                List<Modification> changes) throws RepositoryException {
            // do nothing
        }
    }
}
