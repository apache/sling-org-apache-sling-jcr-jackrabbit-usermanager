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
package org.apache.sling.jackrabbit.usermanager.impl.post;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.header.JakartaMediaRangeList;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaResponseWrapper;
import org.apache.sling.servlethelpers.MockSlingHttpServletRequest;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.apache.sling.servlets.post.JakartaHtmlResponse;
import org.apache.sling.servlets.post.JakartaJSONResponse;
import org.apache.sling.servlets.post.JakartaPostResponse;
import org.apache.sling.servlets.post.JakartaPostResponseCreator;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Constants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

/**
 *
 */
public class AbstractPostServletTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

    private TestPostServlet tps = new TestPostServlet();

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#doPost(org.apache.sling.api.SlingJakartaHttpServletRequest, org.apache.sling.api.SlingJakartaHttpServletResponse)}.
     */
    @Test
    public void testDoPost() throws ServletException, IOException, RepositoryException {
        MockSlingHttpServletRequest request = context.request();
        MockSlingHttpServletResponse response = context.response();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);
        SlingJakartaHttpServletResponse jakartaResponse = JavaxToJakartaResponseWrapper.toJakartaResponse(response);

        ResourceResolver rr = context.resourceResolver();
        // create a user to trigger unique name calculation
        Session jcrSession = context.resourceResolver().adaptTo(Session.class);
        UserManager um = ((JackrabbitSession) jcrSession).getUserManager();
        User testUser = um.createUser("test", "test");

        context.currentResource(rr.getResource(testUser.getPath()));

        tps = Mockito.spy(tps);
        Mockito.doAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<Modification> changes = invocation.getArgument(2, List.class);
                    changes.add(Modification.onModified("/modified"));
                    changes.add(Modification.onDeleted("/deleted"));
                    changes.add(Modification.onMoved("/moveSrcPath", "/moveDestPath"));
                    changes.add(Modification.onCopied("/copySrcPath", "/copyDestPath"));
                    changes.add(Modification.onCreated("/created"));
                    changes.add(Modification.onOrder("/ordered", "beforesibling"));
                    changes.add(Modification.onCheckin("/checkin"));

                    return null;
                })
                .when(tps)
                .handleOperation(any(SlingJakartaHttpServletRequest.class), any(JakartaPostResponse.class), anyList());

        tps.doPost(jakartaRequest, jakartaResponse);

        assertEquals(SlingJakartaHttpServletResponse.SC_OK, jakartaResponse.getStatus());
    }

    @Test
    public void testDoPostWithResourceNotFound() throws ServletException, IOException, RepositoryException {
        MockSlingHttpServletRequest request = context.request();
        MockSlingHttpServletResponse response = context.response();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);
        SlingJakartaHttpServletResponse jakartaResponse = JavaxToJakartaResponseWrapper.toJakartaResponse(response);

        ResourceResolver rr = context.resourceResolver();
        context.currentResource(rr.resolve("/system/userManager/user/user1"));

        tps = Mockito.spy(tps);
        Mockito.doThrow(ResourceNotFoundException.class)
                .when(tps)
                .handleOperation(any(SlingJakartaHttpServletRequest.class), any(JakartaPostResponse.class), anyList());

        tps.doPost(jakartaRequest, jakartaResponse);

        assertEquals(SlingJakartaHttpServletResponse.SC_NOT_FOUND, jakartaResponse.getStatus());
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#createPostResponse(org.apache.sling.api.SlingJakartaHttpServletRequest)}.
     */
    @Test
    public void testCreatePostResponseWithJakartaPostResponseCreator() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        JakartaPostResponseCreator mockPostResponseCreator = Mockito.mock(JakartaPostResponseCreator.class);
        Mockito.when(mockPostResponseCreator.createPostResponse(jakartaRequest)).thenReturn(null);
        tps.bindPostResponseCreator(mockPostResponseCreator, Map.of());

        assertTrue(tps.createPostResponse(jakartaRequest) instanceof JakartaHtmlResponse);

        JakartaPostResponse mockPostResponse = Mockito.mock(JakartaPostResponse.class);
        Mockito.when(mockPostResponseCreator.createPostResponse(jakartaRequest)).thenReturn(mockPostResponse);
        assertEquals(mockPostResponse, tps.createPostResponse(jakartaRequest));
    }

    @Test
    public void testCreatePostResponseWithNoAcceptParamOrHeader() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        // first with no response content type specified
        assertTrue(tps.createPostResponse(jakartaRequest) instanceof JakartaHtmlResponse);

        // again with response content type specified
        request.setResponseContentType("application/json");
        assertTrue(tps.createPostResponse(jakartaRequest) instanceof JakartaJSONResponse);
    }

    @Test
    public void testCreatePostResponseWithEmptyAcceptParamOrHeader() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        request.setParameterMap(Map.of(JakartaMediaRangeList.PARAM_ACCEPT, ""));
        request.setHeader(JakartaMediaRangeList.HEADER_ACCEPT, "");
        assertTrue(tps.createPostResponse(jakartaRequest) instanceof JakartaHtmlResponse);
    }

    @Test
    public void testCreatePostResponseWithAcceptParam() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        request.setParameterMap(Map.of(JakartaMediaRangeList.PARAM_ACCEPT, "application/json"));
        assertTrue(tps.createPostResponse(jakartaRequest) instanceof JakartaJSONResponse);
    }

    @Test
    public void testCreatePostResponseWithAcceptHeader() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        request.setHeader(JakartaMediaRangeList.HEADER_ACCEPT, "application/json");
        assertTrue(tps.createPostResponse(jakartaRequest) instanceof JakartaJSONResponse);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#getRedirectUrl(jakarta.servlet.http.HttpServletRequest, org.apache.sling.servlets.post.JakartaPostResponse)}.
     */
    @Test
    public void testGetRedirectUrl() throws IOException {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        JakartaJSONResponse postResponse = new JakartaJSONResponse();
        assertNull(tps.getRedirectUrl(jakartaRequest, postResponse));

        request.setParameterMap(Map.of(SlingPostConstants.RP_REDIRECT_TO, "/content/node"));
        assertEquals("/content/node", tps.getRedirectUrl(jakartaRequest, postResponse));
    }

    @Test
    public void testGetRedirectUrlWithHost() throws IOException {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        JakartaJSONResponse postResponse = new JakartaJSONResponse();
        assertNull(tps.getRedirectUrl(jakartaRequest, postResponse));

        request.setParameterMap(Map.of(SlingPostConstants.RP_REDIRECT_TO, "https://localhost/content/node"));
        assertThrows(IOException.class, () -> tps.getRedirectUrl(jakartaRequest, postResponse));
    }

    @Test
    public void testGetRedirectUrlWithInvalidSyntax() throws IOException {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        JakartaJSONResponse postResponse = new JakartaJSONResponse();
        assertNull(tps.getRedirectUrl(jakartaRequest, postResponse));

        request.setParameterMap(Map.of(SlingPostConstants.RP_REDIRECT_TO, "https://"));
        assertThrows(IOException.class, () -> tps.getRedirectUrl(jakartaRequest, postResponse));
    }

    @Test
    public void testGetRedirectUrlToCreated() throws IOException {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        JakartaJSONResponse postResponse = new JakartaJSONResponse();
        postResponse.setPath("/content/node/node1");
        assertNull(tps.getRedirectUrl(jakartaRequest, postResponse));

        request.setParameterMap(Map.of(SlingPostConstants.RP_REDIRECT_TO, "*"));
        assertEquals("node1", tps.getRedirectUrl(jakartaRequest, postResponse));

        request.setParameterMap(Map.of(SlingPostConstants.RP_REDIRECT_TO, "/content/node/*"));
        assertEquals("/content/node/node1", tps.getRedirectUrl(jakartaRequest, postResponse));

        request.setParameterMap(Map.of(SlingPostConstants.RP_REDIRECT_TO, "/content/node/*.html"));
        assertEquals("/content/node/node1.html", tps.getRedirectUrl(jakartaRequest, postResponse));

        request.setParameterMap(Map.of(SlingPostConstants.RP_REDIRECT_TO, "/content/node/"));
        assertEquals("/content/node/node1", tps.getRedirectUrl(jakartaRequest, postResponse));

        request.setParameterMap(Map.of(SlingPostConstants.RP_REDIRECT_TO, "/content/node/other"));
        assertEquals("/content/node/other", tps.getRedirectUrl(jakartaRequest, postResponse));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#isSetStatus(org.apache.sling.api.SlingJakartaHttpServletRequest)}.
     */
    @Test
    public void testIsSetStatus() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        assertTrue(tps.isSetStatus(jakartaRequest));

        request.setParameterMap(Map.of(SlingPostConstants.RP_STATUS, SlingPostConstants.STATUS_VALUE_BROWSER));
        assertFalse(tps.isSetStatus(jakartaRequest));

        request.setParameterMap(Map.of(SlingPostConstants.RP_STATUS, SlingPostConstants.STATUS_VALUE_STANDARD));
        assertTrue(tps.isSetStatus(jakartaRequest));

        request.setParameterMap(Map.of(SlingPostConstants.RP_STATUS, "other"));
        assertTrue(tps.isSetStatus(jakartaRequest));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#getItemPath(org.apache.sling.api.SlingJakartaHttpServletRequest)}.
     */
    @Test
    public void testGetItemPath() throws RepositoryException {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        ResourceResolver rr = context.resourceResolver();
        Session jcrSession = rr.adaptTo(Session.class);
        jcrSession.getRootNode().addNode("content").addNode("node1");
        context.currentResource(rr.getResource("/content/node1"));

        assertEquals("/content/node1", tps.getItemPath(jakartaRequest));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#externalizePath(org.apache.sling.api.SlingJakartaHttpServletRequest, java.lang.String)}.
     */
    @Test
    public void testExternalizePath() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        assertEquals("/path", tps.externalizePath(jakartaRequest, "/content/path"));
    }

    @Test
    public void testExternalizePathWithDisplayExtension() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        request.setParameterMap(Map.of(SlingPostConstants.RP_DISPLAY_EXTENSION, "ext"));
        assertEquals("/path.ext", tps.externalizePath(jakartaRequest, "/content/path"));

        request.setParameterMap(Map.of(SlingPostConstants.RP_DISPLAY_EXTENSION, ""));
        assertEquals("/path", tps.externalizePath(jakartaRequest, "/content/path"));

        request.setParameterMap(Map.of(SlingPostConstants.RP_DISPLAY_EXTENSION, ".ext"));
        assertEquals("/path.ext", tps.externalizePath(jakartaRequest, "/content/path"));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#bindPostResponseCreator(org.apache.sling.servlets.post.JakartaPostResponseCreator, java.util.Map)}.
     */
    @Test
    public void testBindPostResponseCreator() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        JakartaPostResponseCreator mockCreator1 = Mockito.mock(JakartaPostResponseCreator.class);
        tps.bindPostResponseCreator(mockCreator1, Map.of(Constants.SERVICE_RANKING, 1));

        JakartaPostResponse mockPostResponse1 = Mockito.mock(JakartaPostResponse.class);
        Mockito.when(mockCreator1.createPostResponse(jakartaRequest)).thenReturn(mockPostResponse1);
        assertEquals(mockPostResponse1, tps.createPostResponse(jakartaRequest));

        JakartaPostResponseCreator mockCreator3 = Mockito.mock(JakartaPostResponseCreator.class);
        tps.bindPostResponseCreator(mockCreator3, Map.of(Constants.SERVICE_RANKING, 3));

        JakartaPostResponse mockPostResponse3 = Mockito.mock(JakartaPostResponse.class);
        Mockito.when(mockCreator3.createPostResponse(jakartaRequest)).thenReturn(mockPostResponse3);
        assertEquals(mockPostResponse3, tps.createPostResponse(jakartaRequest));

        JakartaPostResponseCreator mockCreator2 = Mockito.mock(JakartaPostResponseCreator.class);
        tps.bindPostResponseCreator(mockCreator2, Map.of(Constants.SERVICE_RANKING, 2));
        assertEquals(mockPostResponse3, tps.createPostResponse(jakartaRequest));
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#unbindPostResponseCreator(org.apache.sling.servlets.post.JakartaPostResponseCreator, java.util.Map)}.
     */
    @Test
    public void testUnbindPostResponseCreator() {
        MockSlingHttpServletRequest request = context.request();
        SlingJakartaHttpServletRequest jakartaRequest = JavaxToJakartaRequestWrapper.toJakartaRequest(request);

        JakartaPostResponseCreator mockCreator1 = Mockito.mock(JakartaPostResponseCreator.class);
        JakartaPostResponse mockPostResponse1 = Mockito.mock(JakartaPostResponse.class);
        Mockito.when(mockCreator1.createPostResponse(jakartaRequest)).thenReturn(mockPostResponse1);
        Map<String, Object> creatorProps1 = Map.of(Constants.SERVICE_RANKING, 1);
        tps.bindPostResponseCreator(mockCreator1, creatorProps1);
        assertEquals(mockPostResponse1, tps.createPostResponse(jakartaRequest));

        JakartaPostResponseCreator mockCreator2 = Mockito.mock(JakartaPostResponseCreator.class);
        JakartaPostResponse mockPostResponse2 = Mockito.mock(JakartaPostResponse.class);
        Mockito.when(mockCreator2.createPostResponse(jakartaRequest)).thenReturn(mockPostResponse2);
        Map<String, Object> creatorProps2 = Map.of(Constants.SERVICE_RANKING, 1);
        tps.bindPostResponseCreator(mockCreator2, creatorProps2);
        assertEquals(mockPostResponse2, tps.createPostResponse(jakartaRequest));

        tps.unbindPostResponseCreator(mockCreator2, creatorProps1);
        assertEquals(mockPostResponse1, tps.createPostResponse(jakartaRequest));
        tps.unbindPostResponseCreator(mockCreator1, creatorProps2);
        assertTrue(tps.createPostResponse(jakartaRequest) instanceof JakartaHtmlResponse);
    }

    /**
     * Test method for {@link org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#getRanking(java.util.Map)}.
     */
    @Test
    public void testGetRanking() {
        assertEquals(0, tps.getRanking(Map.of(Constants.SERVICE_RANKING, "invalid")));
        assertEquals(2, tps.getRanking(Map.of(Constants.SERVICE_RANKING, 2)));
    }

    private class TestPostServlet extends AbstractPostServlet {
        private static final long serialVersionUID = -2948341218853558959L;

        @Override
        protected void handleOperation(
                SlingJakartaHttpServletRequest request, JakartaPostResponse response, List<Modification> changes)
                throws RepositoryException {
            // do nothing
        }
    }
}
