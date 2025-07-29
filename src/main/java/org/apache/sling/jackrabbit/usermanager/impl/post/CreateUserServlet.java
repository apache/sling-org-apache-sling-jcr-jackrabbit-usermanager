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
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.jackrabbit.usermanager.CreateUser;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.apache.sling.servlets.post.JakartaPostResponse;
import org.apache.sling.servlets.post.JakartaPostResponseCreator;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.impl.helper.RequestProperty;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Servlet;

/**
 * <p>
 * Sling Post Servlet implementation for creating a user in the jackrabbit UserManager.
 * </p>
 * <h2>Rest Service Description</h2>
 * <p>
 * Creates a new user. Maps on to nodes of resourceType <code>sling/users</code> like
 * <code>/rep:system/rep:userManager/rep:users</code> mapped to a resource url
 * <code>/system/userManager/user</code>. This servlet responds at <code>/system/userManager/user.create.html</code>
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
 * <dt>:pwd</dt>
 * <dd>The password of the new user (required)</dd>
 * <dt>:pwdConfirm</dt>
 * <dd>The password of the new user (required)</dd>
 * <dt>*</dt>
 * <dd>Any additional parameters become properties of the user node (optional)</dd>
 * </dl>
 * <h3>Response</h3>
 * <dl>
 * <dt>200</dt>
 * <dd>Success, a redirect is sent to the users resource locator. The redirect comes with
 * HTML describing the status.</dd>
 * <dt>500</dt>
 * <dd>Failure, including user already exists. HTML explains the failure.</dd>
 * </dl>
 * <h3>Example</h3>
 *
 * <code>
 * curl -F:name=ieb -Fpwd=password -FpwdConfirm=password -Fproperty1=value1 http://localhost:8080/system/userManager/user.create.html
 * </code>
 */
@Component(service = {Servlet.class, CreateUser.class},
    property = {
           "sling.servlet.resourceTypes=sling/users",
           "sling.servlet.methods=POST",
           "sling.servlet.selectors=create",
           "sling.servlet.prefix:Integer=-1",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=EEE MMM dd yyyy HH:mm:ss 'GMT'Z",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd'T'HH:mm:ss.SSSZ",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd'T'HH:mm:ss",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=dd.MM.yyyy HH:mm:ss",
           AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=dd.MM.yyyy"
})
@Designate(ocd = CreateUserServlet.Config.class)
public class CreateUserServlet extends AbstractAuthorizablePostServlet implements CreateUser {
    private static final long serialVersionUID = 6871481922737658675L;

    @ObjectClassDefinition(name = "Apache Sling Create User",
            description = "The Sling operation to handle create user requests in Sling.")
    public @interface Config {

        @AttributeDefinition(name = "Self-Registration Enabled",
                description = "When selected, the anonymous user is allowed to register a new user with the system.")
        boolean self_registration_enabled() default false;  //NOSONAR
    }

    /**
     * default log
     */
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    private boolean selfRegistrationEnabled;

    /**
     * The JCR Repository we access to resolve resources
     */
    @Reference
    private transient SlingRepository repository;

    /**
     * SLING-10014 - To require a service user before becoming active
     */
    @Reference
    private transient ServiceUserMapped serviceUserMapped;
    
    private String usersPath;
    
    @Reference(cardinality=ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private void bindUserConfiguration(UserConfiguration userConfig, Map<String, Object> properties) {
        usersPath = (String)properties.get(UserConstants.PARAM_USER_PATH);
    }
    @SuppressWarnings("unused")
    private void unbindUserConfiguration(UserConfiguration userConfig, Map<String, Object> properties) {
        usersPath = null;
    }
    
    /**
     * Returns an administrative session to the default workspace.
     */
    private Session getSession() throws RepositoryException {
        return repository.loginService(null, null);
    }

    /**
     * Return the administrative session and close it.
     */
    private void ungetSession(final Session session) {
        if (session != null) {
            try {
                session.logout();
            } catch (Exception t) {
                log.error(String.format("Unable to log out of session: %s", t.getMessage()), t);
            }
        }
    }

    // ---------- SCR integration ---------------------------------------------

    @Activate
    protected void activate(Config config, Map<String, Object> props) {
        super.activate(props);
        selfRegistrationEnabled = config.self_registration_enabled();
    }

    @Override
    @Deactivate
    protected void deactivate() {
        this.selfRegistrationEnabled = false;
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

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.impl.post.AbstractAuthorizablePostServlet#bindSystemUserManagerPaths(org.apache.sling.jackrabbit.usermanager.impl.resource.SystemUserManagerPaths)
     */
    @Reference
    @Override
    protected void bindSystemUserManagerPaths(SystemUserManagerPaths sump) {
        super.bindSystemUserManagerPaths(sump);
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
        User user = createUser(session,
                            principalName,
                            request.getParameter("pwd"),
                            request.getParameter("pwdConfirm"),
                            request.getRequestParameterMap(),
                            changes);

        String userPath = null;
        if (user == null) {
            if (!changes.isEmpty()) {
                Modification modification = changes.get(0);
                if (modification.getType() == ModificationType.CREATE) {
                    userPath = modification.getSource();
                }
            }
        } else {
            userPath = systemUserManagerPaths.getUserPrefix()
                    + user.getID();
        }

        if (userPath != null) {
            response.setPath(userPath);
            response.setLocation(externalizePath(request, userPath));
        }
        response.setParentLocation(externalizePath(request,
                systemUserManagerPaths.getUsersPath()));
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.CreateUser#createUser(javax.jcr.Session, java.lang.String, java.lang.String, java.lang.String, java.util.Map, java.util.List)
     */
    public User createUser(Session jcrSession,
                            String name,
                            String password,
                            String passwordConfirm,
                            Map<String, ?> properties,
                            List<Modification> changes)
            throws RepositoryException {

        if (jcrSession == null) {
            throw new RepositoryException("JCR Session not found");
        }

        final String principalName;
        if (name == null || name.isEmpty()) {
            principalName = getOrGeneratePrincipalName(jcrSession, properties, AuthorizableType.USER);
        } else {
            principalName = name;
        }

        // check for an administrator
        boolean administrator = false;
        try {
            UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
            User currentUser = (User) um.getAuthorizable(jcrSession.getUserID());
            administrator = currentUser.isAdmin();

            if (!administrator && usersPath != null) {
                //check if the current user has the minimum privileges needed to create a user
                AccessControlManager acm = jcrSession.getAccessControlManager();
                administrator = acm.hasPrivileges(usersPath, new Privilege[] {
                                        acm.privilegeFromName(Privilege.JCR_READ),
                                        acm.privilegeFromName(Privilege.JCR_READ_ACCESS_CONTROL),
                                        acm.privilegeFromName(Privilege.JCR_MODIFY_ACCESS_CONTROL),
                                        acm.privilegeFromName(PrivilegeConstants.REP_WRITE),
                                        acm.privilegeFromName(PrivilegeConstants.REP_USER_MANAGEMENT)
                                });
            }
        } catch ( Exception ex ) {
            log.warn("Failed to determine if the user is an admin, assuming not. Cause: {}", ex.getMessage());
            administrator = false;
        }


        // make sure user self-registration is enabled
        if (!administrator && !selfRegistrationEnabled) {
            throw new RepositoryException(
                "Sorry, registration of new users is not currently enabled.  Please try again later.");
        }


        // check that the submitted parameter values have valid values.
        if (principalName == null || principalName.length() == 0) {
            throw new RepositoryException("User name was not submitted");
        }
        if (password == null) {
            throw new RepositoryException("Password was not submitted");
        }
        if (!password.equals(passwordConfirm)) {
            throw new RepositoryException(
                "Password value does not match the confirmation password");
        }

        User user = null;
        Session selfRegSession = jcrSession;
        boolean useAdminSession = !administrator && selfRegistrationEnabled;
        try {
            if (useAdminSession) {
                //the current user doesn't have permission to create the user,
                // but self-registration is enabled, so use an admin session
                // to do the work.
                selfRegSession = getSession();
            }

            UserManager userManager = ((JackrabbitSession)selfRegSession).getUserManager();
            Authorizable authorizable = userManager.getAuthorizable(principalName);

            if (authorizable != null) {
                // user already exists!
                throw new RepositoryException(
                    "A principal already exists with the requested name: "
                        + principalName);
            } else {
                user = userManager.createUser(principalName, password);
                String userPath = systemUserManagerPaths.getUserPrefix()
                    + user.getID();

                Map<String, RequestProperty> reqPropertiesMap = collectContentMap(properties);
                Collection<RequestProperty> reqPropertyValues = reqPropertiesMap.values();

                changes.add(Modification.onCreated(userPath));

                // ensure root of new content with the expected primary/mixin types
                processCreate(selfRegSession, user, reqPropertiesMap, changes);

                // write content from form
                writeContent(selfRegSession, user, reqPropertyValues, changes);

                if (selfRegSession.hasPendingChanges()) {
                    selfRegSession.save();
                }

                if (useAdminSession) {
                    //lookup the user from the user session so we can return a live object
                    UserManager userManager2 = ((JackrabbitSession)jcrSession).getUserManager();
                    Authorizable authorizable2 = userManager2.getAuthorizable(user.getID());
                    if (authorizable2 instanceof User) {
                        user = (User)authorizable2;
                    } else {
                        user = null;
                    }
                }
            }
        } finally {
            if (useAdminSession) {
                //done with the self-reg admin session, so clean it up
                ungetSession(selfRegSession);
            }
        }

        return user;
    }

}
