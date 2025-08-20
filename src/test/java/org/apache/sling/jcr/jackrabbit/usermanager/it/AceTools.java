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
package org.apache.sling.jcr.jackrabbit.usermanager.it;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.security.Privilege;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.Authorizable;

/**
 * Tools to do work typically done by the o.a.j.j.accessmanager services
 * duplicated here to avoid a dependency on that other bundle just for testing
 */
public class AceTools {

    /**
     *
     */
    private AceTools() {
        // to hide public ctor
    }

    public static void modifyAce(
            Session jcrSession,
            String resourcePath,
            Authorizable authorizable,
            Set<String> grantedPrivileges,
            Set<String> deniedPrivileges)
            throws RepositoryException {
        AccessControlManager accessControlManager = jcrSession.getAccessControlManager();
        Principal principal = authorizable.getPrincipal();
        JackrabbitAccessControlList updatedAcl =
                (JackrabbitAccessControlList) getAccessControlListOrNull(accessControlManager, resourcePath, true);
        if (grantedPrivileges != null) {
            Privilege[] privileges = toPrivilegesArray(grantedPrivileges, accessControlManager);
            updatedAcl.addEntry(principal, privileges, true);
        }
        if (deniedPrivileges != null) {
            Privilege[] privileges = toPrivilegesArray(deniedPrivileges, accessControlManager);
            updatedAcl.addEntry(principal, privileges, false);
        }

        // apply the changed policy
        accessControlManager.setPolicy(resourcePath, updatedAcl);

        // autosave
        jcrSession.save();
    }

    private static Privilege[] toPrivilegesArray(
            Set<String> grantedPrivileges, AccessControlManager accessControlManager) throws RepositoryException {
        List<Privilege> privilegesList = new ArrayList<>();
        for (String name : grantedPrivileges) {
            Privilege privilege = accessControlManager.privilegeFromName(name);
            privilegesList.add(privilege);
        }
        return privilegesList.toArray(Privilege[]::new);
    }

    public static void deleteAces(Session jcrSession, String resourcePath, Authorizable authorizable)
            throws RepositoryException {
        AccessControlManager accessControlManager = jcrSession.getAccessControlManager();
        Principal principal = authorizable.getPrincipal();
        AccessControlList updatedAcl = getAccessControlListOrNull(accessControlManager, resourcePath, false);

        // if there is no AccessControlList, then there is nothing to be deleted
        if (updatedAcl != null) {
            // keep track of the existing Aces for the target principal
            AccessControlEntry[] accessControlEntries = updatedAcl.getAccessControlEntries();
            List<AccessControlEntry> oldAces = new ArrayList<>();
            for (AccessControlEntry ace : accessControlEntries) {
                if (principal.equals(ace.getPrincipal())) {
                    oldAces.add(ace);
                }
            }

            // remove the old aces
            if (!oldAces.isEmpty()) {
                for (AccessControlEntry ace : oldAces) {
                    updatedAcl.removeAccessControlEntry(ace);
                }
            }

            // apply the changed policy
            accessControlManager.setPolicy(resourcePath, updatedAcl);

            // autosave
            jcrSession.save();
        }
    }

    /**
     * Returns an <code>AccessControlList</code> to edit for the node at the
     * given <code>resourcePath</code>.
     *
     * @param accessControlManager The manager providing access control lists
     * @param resourcePath The node path for which to return an access control
     *            list
     * @param mayCreate <code>true</code> if an access control list should be
     *            created if the node does not have one yet.
     * @return The <code>AccessControlList</code> to modify to control access to
     *         the node or null if one could not be located or created
     * @throws RepositoryException if any errors reading the information
     */
    protected static AccessControlList getAccessControlListOrNull(
            final AccessControlManager accessControlManager, final String resourcePath, final boolean mayCreate)
            throws RepositoryException {
        AccessControlList acl = null;
        // check for an existing access control list to edit
        AccessControlPolicy[] policies = accessControlManager.getPolicies(resourcePath);
        for (AccessControlPolicy policy : policies) {
            if (policy instanceof AccessControlList) {
                acl = (AccessControlList) policy;
            }
        }

        if (acl == null && mayCreate) {
            // no existing access control list, try to create if allowed
            AccessControlPolicyIterator applicablePolicies = accessControlManager.getApplicablePolicies(resourcePath);
            while (applicablePolicies.hasNext()) {
                AccessControlPolicy policy = applicablePolicies.nextAccessControlPolicy();
                if (policy instanceof AccessControlList) {
                    acl = (AccessControlList) policy;
                }
            }
        }
        return acl;
    }
}
