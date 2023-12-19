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

import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import javax.servlet.Servlet;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jackrabbit.usermanager.ChangeUserPassword;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.PostResponse;
import org.apache.sling.servlets.post.PostResponseCreator;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <h2>
 * Changes the password associated with a user.
 * </h2>
 * <p>
 * Maps on to nodes of resourceType <code>sling/user</code> like
 * <code>/rep:system/rep:userManager/rep:users/ae/fd/3e/ieb</code> mapped to a resource url
 * <code>/system/userManager/user/ieb</code>. This servlet responds at
 * <code>/system/userManager/user/ieb.changePassword.html</code>
 * </p>
 * <h3>Methods</h3>
 * <ul>
 * <li>POST</li>
 * </ul>
 * <h3>Post Parameters</h3>
 * <dl>
 * <dt>oldPwd</dt>
 * <dd>The current password for the user (required for non-administrators)</dd>
 * <dt>newPwd</dt>
 * <dd>The new password for the user (required)</dd>
 * <dt>newPwdConfirm</dt>
 * <dd>The confirm new password for the user (required)</dd>
 * </dl>
 * <h3>Response</h3>
 * <dl>
 * <dt>200</dt>
 * <dd>Success sent with no body</dd>
 * <dt>404</dt>
 * <dd>If the user was not found.</dd>
 * <dt>500</dt>
 * <dd>Failure, including password validation errors. HTML explains the failure.</dd>
 * </dl>
 * <h3>Example</h3>
 *
 * <code>
 * curl -FoldPwd=oldpassword -FnewPwd=newpassword -FnewPwdConfirm=newpassword http://localhost:8080/system/userManager/user/ieb.changePassword.html
 * </code>
 *
 * <h3>Notes</h3>
 */

@Component(service = {Servlet.class, ChangeUserPassword.class},
           property = {
                   "sling.servlet.resourceTypes=sling/user",
                   "sling.servlet.methods=POST",
                   "sling.servlet.selectors=changePassword",
                   "sling.servlet.prefix:Integer=-1",
                   AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=EEE MMM dd yyyy HH:mm:ss 'GMT'Z",
                   AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                   AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd'T'HH:mm:ss",
                   AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=yyyy-MM-dd",
                   AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=dd.MM.yyyy HH:mm:ss",
                   AbstractAuthorizablePostServlet.PROP_DATE_FORMAT + "=dd.MM.yyyy"
           })
@Designate(ocd=ChangeUserPasswordServlet.Config.class)
public class ChangeUserPasswordServlet extends AbstractAuthorizablePostServlet implements ChangeUserPassword {

    @ObjectClassDefinition(name ="Apache Sling Change User Password")
    public @interface Config {

        @AttributeDefinition(name = "User Admin Group Name",
                description = "Specifies the name of the group whose members are allowed to reset the password of another user.")
        String user_admin_group_name() default DEFAULT_USER_ADMIN_GROUP_NAME; //NOSONAR

        @AttributeDefinition(name = "Allow Self Password Change",
                description = "Specifies whether a user is allowed to change their own password.")
        boolean allowSelfChangePassword() default true;
    }

    private static final long serialVersionUID = 1923614318474654502L;

    /**
     * default log
     */
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The default 'User administrator' group name
     *
     * @see #PAR_USER_ADMIN_GROUP_NAME
     */
    static final String DEFAULT_USER_ADMIN_GROUP_NAME = "UserAdmin";

    /**
     * The name of the configuration parameter providing the
     * name of the group whose members are allowed to reset the password
     * of a user without the 'oldPwd' value.
     */
    static final String PAR_USER_ADMIN_GROUP_NAME = "user.admin.group.name";

    private String userAdminGroupName = DEFAULT_USER_ADMIN_GROUP_NAME;

    private boolean allowSelfChangePassword = true;

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

    /**
     * Activates this component.
     *
     * @param props The component properties
     */
    @Override
    @Activate
    protected void activate(final Map<String, Object> props) {
        super.activate(props);

        if (props.containsKey("alwaysAllowSelfChangePassword")) {
            // log warning about the wrong property name
            log.warn("Obsolete 'alwaysAllowSelfChangePassword' configuration key was detected. Please change the key name in your configuration to 'allowSelfChangePassword'");
            allowSelfChangePassword = OsgiUtil.toBoolean(props.get("alwaysAllowSelfChangePassword"), false);
        } else {
            allowSelfChangePassword = OsgiUtil.toBoolean(props.get("allowSelfChangePassword"), false);
        }

        this.userAdminGroupName = OsgiUtil.toString(props.get(PAR_USER_ADMIN_GROUP_NAME),
                DEFAULT_USER_ADMIN_GROUP_NAME);
        log.debug("User Admin Group Name {}", this.userAdminGroupName);
    }

    @Override
    @Deactivate
    protected void deactivate() {
        super.deactivate();
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
     * @see org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#bindPostResponseCreator(org.apache.sling.servlets.post.PostResponseCreator, java.util.Map)
     */
    @Override
    @Reference(service = PostResponseCreator.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC)
    protected void bindPostResponseCreator(PostResponseCreator creator, Map<String, Object> properties) {
        super.bindPostResponseCreator(creator, properties);
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.impl.post.AbstractPostServlet#unbindPostResponseCreator(org.apache.sling.servlets.post.PostResponseCreator, java.util.Map)
     */
    @Override
    protected void unbindPostResponseCreator(PostResponseCreator creator, Map<String, Object> properties) { //NOSONAR
        super.unbindPostResponseCreator(creator, properties);
    }

    /*
     * (non-Javadoc)
     * @see
     * org.apache.sling.jackrabbit.usermanager.post.AbstractAuthorizablePostServlet
     * #handleOperation(org.apache.sling.api.SlingHttpServletRequest,
     * org.apache.sling.api.servlets.HtmlResponse, java.util.List)
     */
    @Override
    protected void handleOperation(SlingHttpServletRequest request,
            PostResponse response, List<Modification> changes)
            throws RepositoryException {

        Resource resource = request.getResource();
        Session session = request.getResourceResolver().adaptTo(Session.class);
        changePassword(session,
                resource.getName(),
                request.getParameter("oldPwd"),
                request.getParameter("newPwd"),
                request.getParameter("newPwdConfirm"),
                changes);
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.ChangeUserPassword#changePassword(javax.jcr.Session, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.util.List)
     */
    public User changePassword(Session jcrSession,
                                String name,
                                String oldPassword,
                                String newPassword,
                                String newPasswordConfirm,
                                List<Modification> changes)
                throws RepositoryException {

        if ("anonymous".equals(name)) {
            throw new RepositoryException(
                "Can not change the password of the anonymous user.");
        }

        User user;
        UserManager userManager = ((JackrabbitSession)jcrSession).getUserManager();
        Authorizable authorizable = userManager.getAuthorizable(name);
        if (authorizable instanceof User) {
            user = (User)authorizable;
        } else {
            throw new ResourceNotFoundException(
                "User to update could not be determined");
        }

        //SLING-2069: if the current user is an administrator, then a missing oldPwd is ok,
        // otherwise the oldPwd must be supplied.
        boolean administrator = false;

        // check that the submitted parameter values have valid values.
        if (oldPassword == null || oldPassword.length() == 0) {
            try {
                UserManager um = ((JackrabbitSession)jcrSession).getUserManager();
                User currentUser = (User) um.getAuthorizable(jcrSession.getUserID());
                administrator = currentUser.isAdmin();

                if (!administrator) {
                    //check if the user is a member of the 'User administrator' group
                    Authorizable userAdmin = um.getAuthorizable(this.userAdminGroupName);
                    if (userAdmin instanceof Group) {
                        boolean isMember = ((Group)userAdmin).isMember(currentUser);
                        if (isMember) {
                            administrator = true;
                        }
                    }

                }
            } catch ( Exception ex ) {
                log.warn("Failed to determine if the user is an admin, assuming not. Cause: {}", ex.getMessage());
                administrator = false;
            }
            if (!administrator) {
                throw new RepositoryException("Old Password was not submitted");
            }
        }
        if (newPassword == null || newPassword.length() == 0) {
            throw new RepositoryException("New Password was not submitted");
        }
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new RepositoryException(
                "New Password does not match the confirmation password");
        }

        if (oldPassword != null && oldPassword.length() > 0) {
            // verify old password
            if (allowSelfChangePassword && jcrSession.getUserID().equals(name)) {
                // first check if the current user has enough permissions to do this without
                //   the aid of a service session
                AccessControlManager acm = jcrSession.getAccessControlManager();
                boolean hasRights = acm.hasPrivileges(authorizable.getPath(), new Privilege[] {
                                        acm.privilegeFromName(PrivilegeConstants.REP_USER_MANAGEMENT)
                                });

                if (hasRights) {
                    // we are good to do this without an extra service session
                    user.changePassword(newPassword, oldPassword);
                } else {
                    // the current user doesn't have enough permissions, so we'll need do
                    //   do the work on their behalf as a service user
                    Session svcSession = null;
                    try {
                        svcSession = repository.loginService(null, null);
                        UserManager um = ((JackrabbitSession)svcSession).getUserManager();
                        User user2 = (User) um.getAuthorizable(name);
                        user2.changePassword(newPassword, oldPassword);
                        if (svcSession.hasPendingChanges()) {
                            svcSession.save();
                        }
                    } finally {
                        if (svcSession != null) {
                            svcSession.logout();
                        }
                    }
                }
            } else {
                user.changePassword(newPassword, oldPassword);
            }
        } else {
            user.changePassword(newPassword);
        }

        final String passwordPath = systemUserManagerPaths.getUserPrefix() + user.getID() + "/rep:password";

        changes.add(Modification.onModified(passwordPath));

        return user;
    }
}
