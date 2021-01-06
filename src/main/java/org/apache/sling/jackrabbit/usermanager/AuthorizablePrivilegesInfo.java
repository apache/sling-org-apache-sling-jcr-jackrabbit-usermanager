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
package org.apache.sling.jackrabbit.usermanager;

import javax.jcr.Session;

public interface AuthorizablePrivilegesInfo {

    /**
     * An enumeration of the possible types of property changes
     */
    public enum PropertyUpdateTypes {

        /**
         * @deprecated use {@link #ADD_PROPERTY} instead
         */
        @Deprecated
        addProperty, //NOSONAR
        /**
         * @deprecated use {@link #ADD_NESTED_PROPERTY} instead
         */
        @Deprecated
        addNestedProperty, //NOSONAR
        /**
         * @deprecated use {@link #ALTER_PROPERTY} instead
         */
        @Deprecated
        alterProperty, //NOSONAR
        /**
         * @deprecated use {@link #REMOVE_PROPERTY} instead
         */
        @Deprecated
        removeProperty, //NOSONAR

        ADD_PROPERTY,
        ADD_NESTED_PROPERTY,
        ALTER_PROPERTY,
        REMOVE_PROPERTY;

        /**
         * If the supplied item is one of the deprecated ones, then return the
         * equivalent item that replaced it.
         *
         * @param updateType the item to check
         * @return the non-deprecated equivalent or the original if it is not one of the deprecated items
         */
        public static PropertyUpdateTypes convertDeprecated(PropertyUpdateTypes updateType) {
            PropertyUpdateTypes converted = null;
            if (addProperty.equals(updateType)) { //NOSONAR
                converted = ADD_PROPERTY;
            } else if (addNestedProperty.equals(updateType)) { //NOSONAR
                converted = ADD_NESTED_PROPERTY;
            } else if (alterProperty.equals(updateType)) { //NOSONAR
                converted = ALTER_PROPERTY;
            } else if (removeProperty.equals(updateType)) { //NOSONAR
                converted = REMOVE_PROPERTY;
            }

            if (converted == null) {
                // not one of the deprecated items, so just
                //  return the original
                converted = updateType;
            }
            return converted;
        }
    }

    /**
     * Checks whether the current user has been granted privileges
     * to add a new user.
     *  
     * @param jcrSession the JCR session of the current user
     * @return true if the current user has the privileges, false otherwise
     */
    boolean canAddUser(Session jcrSession);

    /**
     * Checks whether the current user has been granted privileges
     * to add a new group.
     *  
     * @param jcrSession the JCR session of the current user
     * @return true if the current user has the privileges, false otherwise
     */
    boolean canAddGroup(Session jcrSession);
    
    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user or group.
     * Equivalent of: #canUpdateProperties(Session, String, PropertyUpdateTypes.addProperty, PropertyUpdateTypes.addNestedProperty, PropertyUpdateTypes.alterProperty, PropertyUpdateTypes.removeProperty)
     *  
     * @param jcrSession the JCR session of the current user
     * @param principalId the user or group id to check
     * @return true if the current user has the privileges, false otherwise
     */
    boolean canUpdateProperties(Session jcrSession,
            String principalId);

    /**
     * Checks whether the current user has been granted privileges
     * to update the properties of the specified user or group.
     *
     * @param jcrSession the JCR session of the current user
     * @param principalId the user or group id to check
     * @param propertyUpdateTypes specify the types of property updates that may be supplied. See: {@link PropertyUpdateTypes}
     * @return true if the current user has the privileges, false otherwise
     */
    default boolean canUpdateProperties(Session jcrSession,
            String principalId, PropertyUpdateTypes ... propertyUpdateTypes) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks whether the current user has been granted privileges
     * to remove the specified user or group.
     *  
     * @param jcrSession the JCR session of the current user
     * @param principalId the user or group id to check
     * @return true if the current user has the privileges, false otherwise
     */
    boolean canRemove(Session jcrSession,
            String principalId);
    
    /**
     * Checks whether the current user has been granted privileges
     * to update the membership of the specified group.
     *  
     * @param jcrSession the JCR session of the current user
     * @param groupId the group id to check
     * @return true if the current user has the privileges, false otherwise
     */
    boolean canUpdateGroupMembers(Session jcrSession,
            String groupId);

    /**
     * Checks whether the current user has been granted privileges
     * to disable the specified user.
     *
     * @param jcrSession the JCR session of the current user
     * @param userId the user id to check
     * @return true if the current user has the privileges, false otherwise
     */
    default boolean canDisable(Session jcrSession,
            String userId) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks whether the current user has been granted privileges
     * to change the password of the specified user.
     *
     * @param jcrSession the JCR session of the current user
     * @param userId the user id to check
     * @return true if the current user has the privileges, false otherwise
     */
    default boolean canChangePassword(Session jcrSession,
            String userId) {
        throw new UnsupportedOperationException();
    }

}