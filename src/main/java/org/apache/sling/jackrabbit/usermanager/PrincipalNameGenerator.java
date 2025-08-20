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
package org.apache.sling.jackrabbit.usermanager;

import java.util.Map;

import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;

/**
 * Service interface which allows for custom principal name generation
 */
public interface PrincipalNameGenerator {
    public static class NameInfo {
        private String principalName;
        private boolean makeUnique = false;

        public NameInfo(String principalName, boolean makeUnique) {
            this.principalName = principalName;
            this.makeUnique = makeUnique;
        }

        public String getPrincipalName() {
            return principalName;
        }

        public boolean isMakeUnique() {
            return makeUnique;
        }
    }

    /**
     * Get the to-be-created principal name candidate from the request.
     *
     * @param parameters the current request parameters map
     * @param type the type of principal
     * @param principalNameFilter for filtering what characters are allowed in a name
     * @param defaultPrincipalNameGenerator the default principal name generator
     *
     * @return the info about the principal name to be created or null if unable to do so
     */
    public NameInfo getPrincipalName(
            Map<String, ?> parameters,
            AuthorizableType type,
            PrincipalNameFilter principalNameFilter,
            PrincipalNameGenerator defaultPrincipalNameGenerator);
}
