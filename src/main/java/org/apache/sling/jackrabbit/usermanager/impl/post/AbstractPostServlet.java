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

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.header.JakartaMediaRangeList;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet;
import org.apache.sling.api.wrappers.SlingRequestPaths;
import org.apache.sling.servlets.post.JakartaHtmlResponse;
import org.apache.sling.servlets.post.JakartaJSONResponse;
import org.apache.sling.servlets.post.JakartaPostResponse;
import org.apache.sling.servlets.post.JakartaPostResponseCreator;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all the POST servlets for the UserManager operations
 */
public abstract class AbstractPostServlet extends SlingJakartaAllMethodsServlet {

    private static final long serialVersionUID = 7408267654653472120L;

    /**
     * default log
     */
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    /** Sorted list of post response creator holders. */
    private final List<JakartaPostResponseCreatorHolder> postResponseCreators = new ArrayList<>();

    /** Cached array of post response creators used during request processing. */
    private transient JakartaPostResponseCreator[] cachedPostResponseCreators = new JakartaPostResponseCreator[0];

    /*
     * (non-Javadoc)
     * @see
     * org.apache.sling.api.servlets.SlingJakartaAllMethodsServlet#doPost(org.apache
     * .sling.api.SlingJakartaHttpServletRequest,
     * org.apache.sling.api.SlingJakartaHttpServletResponse)
     */
    @Override
    protected void doPost(SlingJakartaHttpServletRequest request, SlingJakartaHttpServletResponse httpResponse)
            throws ServletException, IOException {
        // prepare the response
        JakartaPostResponse response = createPostResponse(request);
        response.setReferer(request.getHeader("referer"));

        // calculate the paths
        String path = getItemPath(request);
        response.setPath(path);

        // location
        response.setLocation(externalizePath(request, path));

        // parent location
        path = ResourceUtil.getParent(path);
        if (path != null) {
            response.setParentLocation(externalizePath(request, path));
        }

        Session session = request.getResourceResolver().adaptTo(Session.class);

        final List<Modification> changes = new ArrayList<>();

        try {
            handleOperation(request, response, changes);

            // set changes on html response
            for (Modification change : changes) {
                switch (change.getType()) {
                    case MODIFY:
                        response.onModified(change.getSource());
                        break;
                    case DELETE:
                        response.onDeleted(change.getSource());
                        break;
                    case MOVE:
                        response.onMoved(change.getSource(), change.getDestination());
                        break;
                    case COPY:
                        response.onCopied(change.getSource(), change.getDestination());
                        break;
                    case CREATE:
                        response.onCreated(change.getSource());
                        break;
                    case ORDER:
                        response.onChange("ordered", change.getSource(), change.getDestination());
                        break;
                    default:
                        break;
                }
            }

            if (session.hasPendingChanges()) {
                session.save();
            }
        } catch (ResourceNotFoundException rnfe) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND, rnfe.getMessage());
        } catch (Exception throwable) {
            if (log.isDebugEnabled()) {
                log.debug(
                        String.format(
                                "Exception while handling POST %s with %s",
                                request.getResource().getPath(), getClass().getName()),
                        throwable);
            }
            Throwable cause = throwable.getCause();
            if (cause == null) {
                cause = throwable;
            }
            if (cause instanceof AccessDeniedException) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN, cause.getMessage());
            } else {
                response.setError(throwable);
            }
        } finally {
            try {
                if (session.hasPendingChanges()) {
                    session.refresh(false);
                }
            } catch (RepositoryException e) {
                log.warn("RepositoryException in finally block: {}", e.getMessage(), e);
            }
        }

        // check for redirect URL if processing succeeded
        if (response.isSuccessful()) {
            String redirect = null;
            try {
                redirect = getRedirectUrl(request, response);
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            String.format(
                                    "Exception while handling redirect for POST %s with %s",
                                    request.getResource().getPath(), getClass().getName()),
                            e);
                }
                // http status code for 422 Unprocessable Entity
                response.setStatus(422, "invalid redirect");
                response.setError(e);
            }
            if (redirect != null) {
                httpResponse.sendRedirect(redirect); // NOSONAR
                return;
            }
        }

        // create a html response and send if unsuccessful or no redirect
        response.send(httpResponse, isSetStatus(request));
    }

    /**
     * Creates an instance of a PostResponse.
     * @param req The request being serviced
     * @return a {@link org.apache.sling.servlets.post.impl.helper.JSONResponse} if any of these conditions are true:
     * <ul>
     *   <li> the request has an <code>Accept</code> header of <code>application/json</code></li>
     *   <li>the request is a JSON POST request (see SLING-1172)</li>
     *   <li>the request has a request parameter <code>:accept=application/json</code></li>
     * </ul>
     * or a {@link org.apache.sling.servlets.post.JakartaPostResponse} otherwise
     */
    JakartaPostResponse createPostResponse(final SlingJakartaHttpServletRequest req) {
        for (final JakartaPostResponseCreator creator : cachedPostResponseCreators) {
            final JakartaPostResponse response = creator.createPostResponse(req);
            if (response != null) {
                return response;
            }
        }

        // for backward compatibility, if no "accept" request param or header is supplied
        // then prefer the SlingHttpServletRequest#getResponseContentType value
        JakartaMediaRangeList mediaRangeList = null;
        String queryParam = req.getParameter(JakartaMediaRangeList.PARAM_ACCEPT);
        if (queryParam == null || queryParam.trim().length() == 0) {
            String headerValue = req.getHeader(JakartaMediaRangeList.HEADER_ACCEPT);
            if (headerValue == null || headerValue.trim().length() == 0) {
                // no param or header supplied, so try the response content type
                mediaRangeList = new JakartaMediaRangeList(req.getResponseContentType());
            }
        }

        // Fall through to default behavior
        if (mediaRangeList == null) {
            mediaRangeList = new JakartaMediaRangeList(req);
        }
        if (JakartaJSONResponse.RESPONSE_CONTENT_TYPE.equals(
                mediaRangeList.prefer("text/html", JakartaJSONResponse.RESPONSE_CONTENT_TYPE))) {
            return new JakartaJSONResponse();
        } else {
            return new JakartaHtmlResponse();
        }
    }

    /**
     * Extending Servlet should implement this operation to do the work
     *
     * @param request the sling http request to process
     * @param response the response
     * @param changes the changes to report
     * @throws RepositoryException in case of exceptions during the operation
     */
    protected abstract void handleOperation(
            SlingJakartaHttpServletRequest request, JakartaPostResponse response, List<Modification> changes)
            throws RepositoryException;

    /**
     * compute redirect URL (SLING-126)
     * @param request the request
     * @param ctx the post processor
     * @return the redirect location or <code>null</code>
     * @throws IOException if there is something invalid with the :redirect value
     */
    protected String getRedirectUrl(HttpServletRequest request, JakartaPostResponse ctx) throws IOException {
        // redirect param has priority (but see below, magic star)
        String result = request.getParameter(SlingPostConstants.RP_REDIRECT_TO);
        if (result != null) {
            try {
                URI redirectUri = new URI(result);
                if (redirectUri.getAuthority() != null) {
                    // if it has a host information
                    throw new IOException(
                            "The redirect target included host information. This is not allowed for security reasons!");
                }
            } catch (URISyntaxException e) {
                throw new IOException("The redirect target was not a valid uri");
            }

            if (ctx.getPath() != null) {
                // redirect to created/modified Resource
                final int star = result.indexOf('*');
                if (star >= 0) {
                    StringBuilder buf = new StringBuilder();

                    // anything before the star
                    if (star > 0) {
                        buf.append(result.substring(0, star));
                    }

                    // append the name of the manipulated node
                    buf.append(ResourceUtil.getName(ctx.getPath()));

                    // anything after the star
                    if (star < result.length() - 1) {
                        buf.append(result.substring(star + 1));
                    }

                    // use the created path as the redirect result
                    result = buf.toString();

                } else if (result.endsWith(SlingPostConstants.DEFAULT_CREATE_SUFFIX)) {
                    // if the redirect has a trailing slash, append modified node
                    // name
                    result = result.concat(ResourceUtil.getName(ctx.getPath()));
                }
            }
        }
        return result;
    }

    protected boolean isSetStatus(SlingJakartaHttpServletRequest request) {
        String statusParam = request.getParameter(SlingPostConstants.RP_STATUS);
        if (statusParam == null) {
            log.debug(
                    "getStatusMode: Parameter {} not set, assuming standard status code", SlingPostConstants.RP_STATUS);
            return true;
        }

        if (SlingPostConstants.STATUS_VALUE_BROWSER.equals(statusParam)) {
            log.debug("getStatusMode: Parameter {} asks for user-friendly status code", SlingPostConstants.RP_STATUS);
            return false;
        }

        if (SlingPostConstants.STATUS_VALUE_STANDARD.equals(statusParam)) {
            log.debug("getStatusMode: Parameter {} asks for standard status code", SlingPostConstants.RP_STATUS);
            return true;
        }

        log.debug(
                "getStatusMode: Parameter {} set to unknown value, assuming standard status code",
                SlingPostConstants.RP_STATUS);
        return true;
    }

    // ------ These methods were copied from AbstractSlingPostOperation ------

    /**
     * Returns the path of the resource of the request as the item path.
     * <p>
     * This method may be overwritten by extension if the operation has
     * different requirements on path processing.
     * @param request the current request
     * @return the path of the resource
     */
    protected String getItemPath(SlingJakartaHttpServletRequest request) {
        return request.getResource().getPath();
    }

    /**
     * Returns an external form of the given path prepending the context path
     * and appending a display extension.
     *
     * @param request the request
     * @param path the path to externalize
     * @return the url
     */
    protected final String externalizePath(SlingJakartaHttpServletRequest request, String path) {
        StringBuilder ret = new StringBuilder();
        ret.append(SlingRequestPaths.getContextPath(request));
        ret.append(request.getResourceResolver().map(path));

        // append optional extension
        String ext = request.getParameter(SlingPostConstants.RP_DISPLAY_EXTENSION);
        if (ext != null && ext.length() > 0) {
            if (ext.charAt(0) != '.') {
                ret.append('.');
            }
            ret.append(ext);
        }

        return ret.toString();
    }

    /**
     * Bind a new post response creator
     */
    // NOTE: the @Reference annotation is not inherited, so subclasses will need to override the
    // #bindPostResponseCreator
    // and #unbindPostResponseCreator methods to provide the @Reference annotation.
    //
    // @Reference(service = PostResponseCreator.class,
    //         cardinality = ReferenceCardinality.MULTIPLE,
    //         policy = ReferencePolicy.DYNAMIC)
    protected void bindPostResponseCreator(
            final JakartaPostResponseCreator creator, final Map<String, Object> properties) {
        final JakartaPostResponseCreatorHolder nngh =
                new JakartaPostResponseCreatorHolder(creator, getRanking(properties));

        synchronized (this.postResponseCreators) {
            int index = 0;
            while (index < this.postResponseCreators.size()
                    && nngh.ranking() < this.postResponseCreators.get(index).ranking()) {
                index++;
            }
            if (index == this.postResponseCreators.size()) {
                this.postResponseCreators.add(nngh);
            } else {
                this.postResponseCreators.add(index, nngh);
            }
            this.updatePostResponseCreatorCache();
        }
    }

    /**
     * Unbind a post response creator
     */
    protected void unbindPostResponseCreator(
            final JakartaPostResponseCreator creator, final Map<String, Object> properties) { // NOSONAR
        synchronized (this.postResponseCreators) {
            final Iterator<JakartaPostResponseCreatorHolder> i = this.postResponseCreators.iterator();
            while (i.hasNext()) {
                final JakartaPostResponseCreatorHolder current = i.next();
                if (current.creator() == creator) {
                    i.remove();
                }
            }
            this.updatePostResponseCreatorCache();
        }
    }

    /**
     * Update the post response creator cache
     * This method is called by sync'ed methods, no need to add additional syncing.
     */
    private void updatePostResponseCreatorCache() {
        final JakartaPostResponseCreator[] localCache =
                new JakartaPostResponseCreator[this.postResponseCreators.size()];
        int index = 0;
        for (final JakartaPostResponseCreatorHolder current : this.postResponseCreators) {
            localCache[index] = current.creator;
            index++;
        }
        this.cachedPostResponseCreators = localCache;
    }

    protected int getRanking(final Map<String, Object> properties) {
        final Object val = properties.get(Constants.SERVICE_RANKING);
        return val instanceof Integer intVal ? intVal : 0;
    }

    private static final record JakartaPostResponseCreatorHolder(JakartaPostResponseCreator creator, int ranking) {}
}
