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
package org.apache.sling.jackrabbit.usermanager.impl;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo;
import org.apache.sling.jackrabbit.usermanager.CreateUser;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to assist in the usage of access control of users/groups from scripts.
 * 
 * The default access control policy defined by this provider has the following
 * characteristics:
 * <ul>
 * <li>everybody has READ permission to all items,</li>
 *
 * <li>every known user is allowed to modify it's own properties except for
 * her/his group membership,</li>
 * </ul>
 */
@Component(service=AuthorizablePrivilegesInfo.class,
    property={
            AuthorizablePrivilegesInfoImpl.PAR_USER_ADMIN_GROUP_NAME + "=" + AuthorizablePrivilegesInfoImpl.DEFAULT_USER_ADMIN_GROUP_NAME,
            AuthorizablePrivilegesInfoImpl.PAR_GROUP_ADMIN_GROUP_NAME + "=" + AuthorizablePrivilegesInfoImpl.DEFAULT_GROUP_ADMIN_GROUP_NAME
    })
public class AuthorizablePrivilegesInfoImpl implements AuthorizablePrivilegesInfo {

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The default 'User administrator' group name
     *
     * @see #PAR_USER_ADMIN_GROUP_NAME
     */
    static final String DEFAULT_USER_ADMIN_GROUP_NAME = "UserAdmin";
 
    /**
     * The name of the configuration parameter providing the 
     * 'User administrator' group name.
     */
    static final String PAR_USER_ADMIN_GROUP_NAME = "user.admin.group.name";

    /**
     * The default 'User administrator' group name
     *
     * @see #PAR_GROUP_ADMIN_GROUP_NAME
     */
    static final String DEFAULT_GROUP_ADMIN_GROUP_NAME = "GroupAdmin";
 
    /**
     * The name of the configuration parameter providing the 
     * 'Group administrator' group name.
     */
    static final String PAR_GROUP_ADMIN_GROUP_NAME = "group.admin.group.name";

    private String usersPath;
    private String groupsPath;
    private boolean selfRegistrationEnabled;
    
    @Reference(cardinality=ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private void bindUserConfiguration(UserConfiguration userConfig, Map<String, Object> properties) {
        usersPath = (String)properties.get(UserConstants.PARAM_USER_PATH);
        groupsPath = (String)properties.get(UserConstants.PARAM_GROUP_PATH);
    }
    @SuppressWarnings("unused")
    private void unbindUserConfiguration(UserConfiguration userConfig, Map<String, Object> properties) {
        usersPath = null;
        groupsPath = null;
    }

    @Reference(cardinality=ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private void bindCreateUser(CreateUser createUser, Map<String, Object> properties) {
        selfRegistrationEnabled = Boolean.TRUE.equals(properties.get("self.registration.enabled"));
    }
    @SuppressWarnings("unused")
    private void unbindCreateUser(CreateUser createUser, Map<String, Object> properties) {
        selfRegistrationEnabled = false;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canAddGroup(javax.jcr.Session)
     */
    public boolean canAddGroup(Session jcrSession) {
        boolean hasRights = false;
        try {
            UserManager userManager = AccessControlUtil.getUserManager(jcrSession);
            Authorizable currentUser = userManager.getAuthorizable(jcrSession.getUserID());

            if (currentUser instanceof User && ((User)currentUser).isAdmin()) {
                hasRights = true; //admin user has full control
            } else {
                if (groupsPath != null) {
                    //check if the non-admin user has sufficient rights on the home folder
                    AccessControlManager acm = jcrSession.getAccessControlManager();
                    hasRights = acm.hasPrivileges(groupsPath, new Privilege[] {
                                            acm.privilegeFromName(Privilege.JCR_READ),
                                            acm.privilegeFromName(Privilege.JCR_READ_ACCESS_CONTROL),
                                            acm.privilegeFromName(Privilege.JCR_MODIFY_ACCESS_CONTROL),
                                            acm.privilegeFromName(PrivilegeConstants.REP_WRITE),
                                            acm.privilegeFromName(PrivilegeConstants.REP_USER_MANAGEMENT)
                                    });
                }
            }
        } catch (RepositoryException e) {
            log.warn("Failed to determine if {} can add a new group", jcrSession.getUserID());
        }
        return hasRights;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canAddUser(javax.jcr.Session)
     */
    public boolean canAddUser(Session jcrSession) {
        boolean hasRights = false;
        try {
            //if self-registration is enabled, then anyone can create a user
            if (selfRegistrationEnabled) {
                hasRights = true;
            } else {
                UserManager userManager = AccessControlUtil.getUserManager(jcrSession);
                Authorizable currentUser = userManager.getAuthorizable(jcrSession.getUserID());
                if (currentUser instanceof User && ((User)currentUser).isAdmin()) {
                    hasRights = true;  //admin user has full control
                } else {
                    if (usersPath != null) {
                        //check if the non-admin user has sufficient rights on the home folder
                        AccessControlManager acm = jcrSession.getAccessControlManager();
                        hasRights = acm.hasPrivileges(usersPath, new Privilege[] {
                                                acm.privilegeFromName(Privilege.JCR_READ),
                                                acm.privilegeFromName(Privilege.JCR_READ_ACCESS_CONTROL),
                                                acm.privilegeFromName(Privilege.JCR_MODIFY_ACCESS_CONTROL),
                                                acm.privilegeFromName(PrivilegeConstants.REP_WRITE),
                                                acm.privilegeFromName(PrivilegeConstants.REP_USER_MANAGEMENT)
                                        });
                    }
                }
            }
        } catch (RepositoryException e) {
            log.warn("Failed to determine if {} can add a new user", jcrSession.getUserID());
        }
        return hasRights;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canRemove(javax.jcr.Session, java.lang.String)
     */
    public boolean canRemove(Session jcrSession, String principalId) {
        boolean hasRights = false;
        try {
            UserManager userManager = AccessControlUtil.getUserManager(jcrSession);
            Authorizable currentUser = userManager.getAuthorizable(jcrSession.getUserID());

            if (currentUser instanceof User && ((User)currentUser).isAdmin()) {
                hasRights = true; //admin user has full control
            } else {
                Authorizable authorizable = userManager.getAuthorizable(principalId);
                if (authorizable == null) {
                    log.debug("Failed to find authorizable: {}", principalId);
                } else {
                    String path = authorizable.getPath();
                    if (path != null) {
                        //check if the non-admin user has sufficient rights on the home folder
                        AccessControlManager acm = jcrSession.getAccessControlManager();
                        hasRights = acm.hasPrivileges(path, new Privilege[] {
                                                acm.privilegeFromName(Privilege.JCR_READ),
                                                acm.privilegeFromName(PrivilegeConstants.REP_USER_MANAGEMENT)
                                        });
                    }
                }
            }
        } catch (RepositoryException e) {
            log.warn("Failed to determine if {} can remove authorizable {}", jcrSession.getUserID(), principalId);
        }
        return hasRights;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canUpdateGroupMembers(javax.jcr.Session, java.lang.String)
     */
    public boolean canUpdateGroupMembers(Session jcrSession, String groupId) {
        boolean hasRights = false;
        try {
            UserManager userManager = AccessControlUtil.getUserManager(jcrSession);
            Authorizable currentUser = userManager.getAuthorizable(jcrSession.getUserID());

            if (currentUser instanceof User && ((User)currentUser).isAdmin()) {
                hasRights = true; //admin user has full control
            } else {
                Authorizable authorizable = userManager.getAuthorizable(groupId);
                if (authorizable == null) {
                    log.debug("Failed to find group: {}", groupId);
                } else {
                    String path = authorizable.getPath();
                    if (path != null) {
                        //check if the non-admin user has sufficient rights on the home folder
                        AccessControlManager acm = jcrSession.getAccessControlManager();
                        hasRights = acm.hasPrivileges(path, new Privilege[] {
                                                acm.privilegeFromName(Privilege.JCR_READ),
                                                acm.privilegeFromName(PrivilegeConstants.REP_USER_MANAGEMENT)
                                        });
                    }
                }
            }
        } catch (RepositoryException e) {
            log.warn("Failed to determine if {} can remove authorizable {}", jcrSession.getUserID(), groupId);
        }
        return hasRights;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canUpdateProperties(javax.jcr.Session, java.lang.String)
     */
    public boolean canUpdateProperties(Session jcrSession, String principalId) {
        return canUpdateProperties(jcrSession, principalId,
                PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty,
                PropertyUpdateTypes.alterProperty, PropertyUpdateTypes.removeProperty);
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo#canUpdateProperties(javax.jcr.Session, java.lang.String, org.apache.sling.jackrabbit.usermanager.AuthorizablePrivilegesInfo.PropertyUpdateTypes[])
     */
    @Override
    public boolean canUpdateProperties(Session jcrSession, String principalId,
            PropertyUpdateTypes... propertyUpdateTypes) {
        boolean hasRights = false;
        try {
            if (jcrSession.getUserID().equals(principalId)) {
                //user is allowed to update it's own properties
                hasRights = true;
            } else {
                UserManager userManager = AccessControlUtil.getUserManager(jcrSession);
                Authorizable currentUser = userManager.getAuthorizable(jcrSession.getUserID());

                if (currentUser instanceof User) {
                    if (((User)currentUser).isAdmin()) {
                        return true; //admin user has full control
                    }
                }

                Authorizable authorizable = userManager.getAuthorizable(principalId);
                if (authorizable == null) {
                    log.debug("Failed to find authorizable: {}", principalId);
                } else {
                    String path = authorizable.getPath();
                    if (path != null) {
                        //check if the non-admin user has sufficient rights on the home folder
                        AccessControlManager acm = jcrSession.getAccessControlManager();
                        Set<Privilege> requiredPrivileges = new HashSet<>();
                        requiredPrivileges.add(acm.privilegeFromName(Privilege.JCR_READ));
                        if (propertyUpdateTypes != null) {
                            for (PropertyUpdateTypes updateType : propertyUpdateTypes) {
                                switch (updateType) {
                                case addNestedProperty:
                                    requiredPrivileges.add(acm.privilegeFromName(PrivilegeConstants.REP_ADD_PROPERTIES));
                                    requiredPrivileges.add(acm.privilegeFromName(Privilege.JCR_ADD_CHILD_NODES));
                                    break;
                                case addProperty:
                                    requiredPrivileges.add(acm.privilegeFromName(PrivilegeConstants.REP_ADD_PROPERTIES));
                                    break;
                                case alterProperty:
                                    requiredPrivileges.add(acm.privilegeFromName(PrivilegeConstants.REP_ALTER_PROPERTIES));
                                    break;
                                case removeProperty:
                                    requiredPrivileges.add(acm.privilegeFromName(PrivilegeConstants.REP_REMOVE_PROPERTIES));
                                    break;
                                default:
                                    log.warn("Unexpected property update type: {}", updateType);
                                    break;
                                }
                            }
                        }

                        hasRights = acm.hasPrivileges(path, requiredPrivileges.toArray(new Privilege[requiredPrivileges.size()]));
                    }
                }
            }
        } catch (RepositoryException e) {
            log.warn("Failed to determine if {} can update properties of authorizable {}", jcrSession.getUserID(), principalId);
        }
        return hasRights;
    }


    // ---------- SCR Integration ----------------------------------------------


    @Activate
    protected void activate(BundleContext bundleContext, Map<String, Object> properties)
            throws InvalidKeyException, NoSuchAlgorithmException,
            IllegalStateException, UnsupportedEncodingException {

        String userAdminGroupName = OsgiUtil.toString(properties.get(PAR_USER_ADMIN_GROUP_NAME), null);
        if ( userAdminGroupName != null && ! DEFAULT_USER_ADMIN_GROUP_NAME.equals(userAdminGroupName)) {
            log.warn("Configuration setting for {} is deprecated and will not have any effect", PAR_USER_ADMIN_GROUP_NAME);
        }

        String groupAdminGroupName = OsgiUtil.toString(properties.get(PAR_GROUP_ADMIN_GROUP_NAME), null);
        if ( groupAdminGroupName != null && ! DEFAULT_GROUP_ADMIN_GROUP_NAME.equals(userAdminGroupName)) {
            log.warn("Configuration setting for {} is deprecated and will not have any effect", PAR_GROUP_ADMIN_GROUP_NAME);
        }
    }
}
