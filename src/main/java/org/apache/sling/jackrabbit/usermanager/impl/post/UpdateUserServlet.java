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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.jackrabbit.usermanager.UpdateUser;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.servlets.post.JakartaPostResponse;
import org.apache.sling.servlets.post.JakartaPostResponseCreator;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.impl.helper.RequestProperty;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import jakarta.servlet.Servlet;

/**
 * <p>
 * Sling Post Operation implementation for updating a user in the jackrabbit UserManager.
 * </p>
 * <h2>Rest Service Description</h2>
 * <p>
 * Updates a users properties. Maps on to nodes of resourceType <code>sling/users</code> like
 * <code>/rep:system/rep:userManager/rep:users</code> mapped to a resource url
 * <code>/system/userManager/user/ieb</code>. This servlet responds at
 * <code>/system/userManager/user/ieb.update.html</code>
 * </p>
 * <h3>Methods</h3>
 * <ul>
 * <li>POST</li>
 * </ul>
 * <h3>Post Parameters</h3>
 * <dl>
 * <dt>*</dt>
 * <dd>Any additional parameters become properties of the user node (optional)</dd>
 * <dt>*@Delete</dt>
 * <dd>Delete the property eg prop3@Delete means prop3 will be deleted (optional)</dd>
 * </dl>
 * <h3>Response</h3>
 * <dl>
 * <dt>200</dt>
 * <dd>Success, a redirect is sent to the users resource locator. The redirect comes with
 * HTML describing the status.</dd>
 * <dt>404</dt>
 * <dd>The resource was not found</dd>
 * <dt>500</dt>
 * <dd>Failure</dd>
 * </dl>
 * <h3>Example</h3>
 *
 * <code>
 * curl -Fprop1=value2 -Fproperty1=value1 http://localhost:8080/system/userManager/user/ieb.update.html
 * </code>
 */

@Component(service = {Servlet.class, UpdateUser.class},
property = {
           "sling.servlet.resourceTypes=sling/user",
           "sling.servlet.methods=POST",
           "sling.servlet.selectors=update",
           "sling.servlet.prefix:Integer=-1",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=EEE MMM dd yyyy HH:mm:ss 'GMT'Z",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd'T'HH:mm:ss.SSSZ",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd'T'HH:mm:ss",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=dd.MM.yyyy HH:mm:ss",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=dd.MM.yyyy"
},
reference = {
        @Reference(name="SystemUserManagerPaths",
                bind = "bindSystemUserManagerPaths",
                service = SystemUserManagerPaths.class)
})
public class UpdateUserServlet extends AbstractAuthorizablePostServlet
        implements UpdateUser {
    
    private static final long serialVersionUID = 5874621724096106496L;

    @Override
    @Activate
    protected void activate(final Map<String, Object> props) {
        super.activate(props);
    }

    @Override
    @Deactivate
    protected void deactivate( ) {
        super.deactivate();
    }

    /**
     * Overridden since the @Reference annotation is not inherited from the super method
     *  
     * @see org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#bindPostResponseCreator(org.apache.sling.servlets.post.JakartaPostResponseCreator, java.util.Map)
     */
    @Override
    @Reference(service = JakartaPostResponseCreator.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC)
    protected void bindPostResponseCreator(JakartaPostResponseCreator creator, Map<String, Object> properties) {
        super.bindPostResponseCreator(creator, properties);
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#unbindPostResponseCreator(org.apache.sling.servlets.post.JakartaPostResponseCreator, java.util.Map)
     */
    @Override
    protected void unbindPostResponseCreator(JakartaPostResponseCreator creator, Map<String, Object> properties) { //NOSONAR
        super.unbindPostResponseCreator(creator, properties);
    }

    /*
     * (non-Javadoc)
     * @see
     * org.apache.sling.jackrabbit.usermanager.post.AbstractAuthorizablePostServlet
     * #handleOperation(org.apache.sling.api.SlingJakartaHttpServletRequest,
     * org.apache.sling.servlets.post.JakartaPostResponse, java.util.List)
     */
    @Override
    protected void handleOperation(SlingJakartaHttpServletRequest request,
            JakartaPostResponse response, List<Modification> changes)
            throws RepositoryException {
        Resource resource = request.getResource();
        Session session = request.getResourceResolver().adaptTo(Session.class);
        updateUser(session,
                resource.getName(),
                request.getRequestParameterMap(),
                changes);
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.UpdateUser#updateUser(javax.jcr.Session, java.lang.String, java.util.Map, java.util.List)
     */
    public User updateUser(Session jcrSession, String name,
            Map<String, ?> properties, List<Modification> changes)
            throws RepositoryException {

        User user;
        UserManager userManager = ((JackrabbitSession)jcrSession).getUserManager();
        Authorizable authorizable = userManager.getAuthorizable(name);
        if (authorizable instanceof User u) {
            user = u;
        } else {
            throw new ResourceNotFoundException(
                "User to update could not be determined");
        }

        Map<String, RequestProperty> reqPropertiesMap = collectContentMap(properties);
        Collection<RequestProperty> reqPropertyValues = reqPropertiesMap.values();
        try {
            // cleanup any old content (@Delete parameters)
            processDeletes(user, reqPropertyValues, changes);

            // ensure root of new content with the expected primary/mixin types
            processCreate(jcrSession, user, reqPropertiesMap, changes);

            // write content from form
            writeContent(jcrSession, user, reqPropertyValues, changes);

            //SLING-2072 set the user as enabled or disabled if the request
            // has supplied the relevant properties
            String disabledParam = convertToString(properties.get(":disabled"));
            if ("true".equalsIgnoreCase(disabledParam)) {
                //set the user as disabled
                String disabledReason = convertToString(properties.get(":disabledReason"));
                if (disabledReason == null) {
                    disabledReason = "";
                }
                user.disable(disabledReason);
            } else if ("false".equalsIgnoreCase(disabledParam)) {
                //re-enable a disabled user
                user.disable(null);
            }
        } catch (RepositoryException re) {
            throw new RepositoryException("Failed to update user.", re);
        }
        return user;
    }
}
