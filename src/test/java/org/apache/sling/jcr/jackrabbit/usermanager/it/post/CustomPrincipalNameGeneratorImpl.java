/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.jcr.jackrabbit.usermanager.it.post;

import java.util.Map;

import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator;

/**
 * Sample implementation of the PrincipalNameGenerator interface.
 */
public class CustomPrincipalNameGeneratorImpl implements PrincipalNameGenerator {

    @Override
    public NameInfo getPrincipalName(Map<String, ?> parameters, AuthorizableType type,
            PrincipalNameFilter principalNameFilter, PrincipalNameGenerator defaultPrincipalNameGenerator) {
        NameInfo nameInfo = defaultPrincipalNameGenerator.getPrincipalName(parameters, type, 
                principalNameFilter, defaultPrincipalNameGenerator);
        if (nameInfo != null && nameInfo.getPrincipalName() != null && nameInfo.isMakeUnique()) {
            String principalName = String.format("custom_%s_%s", type.name().toLowerCase(), nameInfo.getPrincipalName());
            nameInfo = new NameInfo(principalName, nameInfo.isMakeUnique());
        }
        return nameInfo;
    }

}
