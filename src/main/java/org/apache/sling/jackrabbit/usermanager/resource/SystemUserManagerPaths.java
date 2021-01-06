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
package org.apache.sling.jackrabbit.usermanager.resource;

/**
 * Helper for components that need to know the 
 * UserManager resource paths
 */
public interface SystemUserManagerPaths {

    /**
     * The root path for usermanager resources
     */
    String getRootPath();

    /**
     * The root path for all user resources
     */
    String getUsersPath();

    /**
     * The path prefix (before the id) for users
     */
    String getUserPrefix();

    /**
     * The root path for all group resources
     */
    String getGroupsPath();

    /**
     * The path prefix (before the id) for groups
     */
    String getGroupPrefix();

}