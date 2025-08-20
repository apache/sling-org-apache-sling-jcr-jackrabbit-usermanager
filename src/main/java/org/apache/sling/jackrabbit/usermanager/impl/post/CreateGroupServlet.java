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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jackrabbit.usermanager.CreateGroup;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.servlets.post.JakartaPostResponse;
import org.apache.sling.servlets.post.JakartaPostResponseCreator;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.impl.helper.RequestProperty;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import jakarta.servlet.Servlet;

/**
 * <p>
 * Sling Post Servlet implementation for creating a group in the jackrabbit UserManager.
 * </p>
 * <h2>Rest Service Description</h2>
 * <p>
 * Creates a new group. Maps on to nodes of resourceType <code>sling/groups</code> like
 * <code>/rep:system/rep:userManager/rep:groups</code> mapped to a resource url
 * <code>/system/userManager/group</code>. This servlet responds at
 * <code>/system/userManager/group.create.html</code>
 * </p>
 * <h3>Methods</h3>
 * <ul>
 * <li>POST</li>
 * </ul>
 * <h3>Post Parameters</h3>
 * <dl>
 * <dt>one of these</dt>
 * <dd>
 *   <ul>
 *     <li><b>:name</b> - The value is the exact name to use</li>
 *     <li><b>:name@ValueFrom</b> - The value is the name of another submitted parameter whose value is the exact name to use</li>
 *     <li><b>:nameHint</b> - The value is filtered, trimmed and made unique</li>
 *     <li><b>:nameHint@ValueFrom</b> - The value is the name of another submitted parameter whose value is filtered, trimmed and made unique</li>
 *     <li><b>otherwise</b> - Try the value of any server-side configured "principalNameHints" parameter to treat as a hint that is filtered, trimmed and made unique</li>
 *   </ul>
 * </dd>
 * <dt>*</dt>
 * <dd>Any additional parameters become properties of the group node (optional)</dd>
 * </dl>
 * <h3>Response</h3>
 * <dl>
 * <dt>200</dt>
 * <dd>Success, a redirect is sent to the group resource locator. The redirect comes with
 * HTML describing the status.</dd>
 * <dt>500</dt>
 * <dd>Failure, including group already exists. HTML explains the failure.</dd>
 * </dl>
 * <h3>Example</h3>
 * 
 * <code>
 * curl -F:name=newGroupA  -Fproperty1=value1 http://localhost:8080/system/userManager/group.create.html
 * </code>
 * 
 * <h4>Notes</h4>
 */

@Component(service = {Servlet.class, CreateGroup.class},
property = {
           "sling.servlet.resourceTypes=sling/groups",
           "sling.servlet.methods=POST",
           "sling.servlet.selectors=create",
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
public class CreateGroupServlet extends AbstractGroupPostServlet implements CreateGroup {
    private static final long serialVersionUID = -1084915263933901466L;

    @Reference
    private transient ResourceResolverFactory resourceResolverFactory;
    
    @Override
    @Activate
    protected void activate(final Map<String, Object> props) {
        super.activate(props);
    }

    @Override
    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC)
    @Override
    protected void bindPrincipalNameGenerator(PrincipalNameGenerator generator, Map<String, Object> properties) {
        super.bindPrincipalNameGenerator(generator, properties);
    }

    @Override
    protected void unbindPrincipalNameGenerator(PrincipalNameGenerator generator) { // NOSONAR
        super.unbindPrincipalNameGenerator(generator);
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    @Override
    protected void bindPrincipalNameFilter(PrincipalNameFilter filter) {
        super.bindPrincipalNameFilter(filter);
    }

    @Override
    protected void unbindPrincipalNameFilter(PrincipalNameFilter filter) { // NOSONAR
        super.unbindPrincipalNameFilter(filter);
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

        Session session = request.getResourceResolver().adaptTo(Session.class);
        String principalName = request.getParameter(SlingPostConstants.RP_NODE_NAME);
        Group group = createGroup(session, 
                principalName, 
                request.getRequestParameterMap(), 
                changes);

        String groupPath = systemUserManagerPaths.getGroupPrefix()
            + group.getID();
        response.setPath(groupPath);
        response.setLocation(externalizePath(request, groupPath));
        response.setParentLocation(externalizePath(request,
                systemUserManagerPaths.getGroupsPath()));
        
    }
    
    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.CreateGroup#createGroup(javax.jcr.Session, java.lang.String, java.util.Map, java.util.List)
     */
    public Group createGroup(Session jcrSession, final String name,
            Map<String, ?> properties, List<Modification> changes)
            throws RepositoryException {
        // check that the parameter values have valid values.
        if (jcrSession == null) {
            throw new IllegalArgumentException("JCR Session not found");
        }

        final String principalName;
        if (name == null || name.isEmpty()) {
            principalName = getOrGeneratePrincipalName(jcrSession, properties, AuthorizableType.GROUP);
        } else {
            principalName = name;
        }

        if (principalName == null || principalName.length() == 0) {
            throw new IllegalArgumentException("Group name was not supplied");
        }

        UserManager userManager = ((JackrabbitSession)jcrSession).getUserManager();
        Authorizable authorizable = userManager.getAuthorizable(principalName);

        Group group = null;
        if (authorizable != null) {
            // principal already exists!
            throw new RepositoryException(
                "A group already exists with the requested name: "
                    + principalName);
        } else {
            group = userManager.createGroup(() -> principalName);

            String groupPath = systemUserManagerPaths.getGroupPrefix()
                + group.getID();
            
            Map<String, RequestProperty> reqPropertiesMap = collectContentMap(properties);
            Collection<RequestProperty> reqPropertyValues = reqPropertiesMap.values();
            changes.add(Modification.onCreated(groupPath));

            // ensure root of new content with the expected primary/mixin types
            processCreate(jcrSession, group, reqPropertiesMap, changes);

            // write content from form
            writeContent(jcrSession, group, reqPropertyValues, changes);

            // update the group memberships
            ResourceResolver resourceResolver = null;
            try {
                //create a resource resolver to resolve the relative paths used for group membership values
                final Map<String, Object> authInfo = new HashMap<>();
                authInfo.put(org.apache.sling.jcr.resource.api.JcrResourceConstants.AUTHENTICATION_INFO_SESSION, jcrSession);
                resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
                Resource baseResource = resourceResolver.getResource(systemUserManagerPaths.getGroupsPath());
                updateGroupMembership(baseResource, properties, group, changes);
            } catch (LoginException e) {
                throw new RepositoryException(e);
            } finally {
                if (resourceResolver != null) {
                    resourceResolver.close();
                }
            }
        }
        
        return group;
    }
    
}
