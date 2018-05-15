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

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = AdapterFactory.class, property = {
        AdapterFactory.ADAPTER_CLASSES + "=org.apache.jackrabbit.api.security.user.User",
        AdapterFactory.ADAPTER_CLASSES + "=org.apache.jackrabbit.api.security.user.Authorizable",
        AdapterFactory.ADAPTABLE_CLASSES + "=org.apache.sling.api.resource.ResourceResolver" })
public class AuthorizableAdapterFactory implements AdapterFactory {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(final Object adaptable, final Class<AdapterType> type) {
        Session session = ((ResourceResolver) adaptable).adaptTo(Session.class);
        if (session instanceof JackrabbitSession) {
            JackrabbitSession jackrabbitSession = (JackrabbitSession) session;
            try {
                UserManager um = jackrabbitSession.getUserManager();
                Authorizable authorizable = um.getAuthorizable(jackrabbitSession.getUserID());
                return type.cast(authorizable);
            } catch (RepositoryException e) {
                log.warn("User cannot read own authorizable.", e);
            }
        }
        return null;
    }

}
