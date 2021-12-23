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
package org.apache.sling.jackrabbit.usermanager.impl.resource;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource Provider implementation for jackrabbit UserManager resources.
 */
@Component(service = { ResourceProvider.class, SystemUserManagerPaths.class },
    property={
            "service.description=Resource provider implementation for UserManager resources",
            "service.vendor=The Apache Software Foundation",
            ResourceProvider.PROPERTY_ROOT + "=" + AuthorizableResourceProvider.DEFAULT_SYSTEM_USER_MANAGER_PATH
    })
@Designate(ocd=AuthorizableResourceProvider.Config.class)
public class AuthorizableResourceProvider extends ResourceProvider<Object> implements SystemUserManagerPaths {

    @ObjectClassDefinition(name ="Apache Sling UserManager Resource Provider")
    public @interface Config {

        @AttributeDefinition(name = "Provider Root",
                description = "Specifies the root path for the UserManager resources.")
        String provider_root() default DEFAULT_SYSTEM_USER_MANAGER_PATH; //NOSONAR

        @AttributeDefinition(name = "Provide Resources For Nested Properties",
                description = "Specifies whether container resources are provided for any nested authorizable properties. "
                        + "The resourceType for these ancestor resources would be 'sling/[user|group]/properties'")
        boolean resources_for_nested_properties() default false; //NOSONAR
    }

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private String systemUserManagerPath;
    private String systemUserManagerUserPath;
    private String systemUserManagerUserPrefix;
    private String systemUserManagerGroupPath;
    private String systemUserManagerGroupPrefix;

    public static final String DEFAULT_SYSTEM_USER_MANAGER_PATH = "/system/userManager"; //NOSONAR

    /**
     * @deprecated no longer used.  Use {@link SystemUserManagerPaths} service instead.
     */
    @Deprecated
    public static final String SYSTEM_USER_MANAGER_PATH = "/system/userManager";  //NOSONAR

    /**
     * @deprecated no longer used.  Use {@link SystemUserManagerPaths} service instead.
     */
    @Deprecated
    public static final String SYSTEM_USER_MANAGER_USER_PATH = SYSTEM_USER_MANAGER_PATH //NOSONAR
        + "/user";

    /**
     * @deprecated no longer used.  Use {@link SystemUserManagerPaths} service instead.
     */
    @Deprecated
    public static final String SYSTEM_USER_MANAGER_GROUP_PATH = SYSTEM_USER_MANAGER_PATH //NOSONAR
        + "/group";

    /**
     * @deprecated no longer used.  Use {@link SystemUserManagerPaths} service instead.
     */
    @Deprecated
    public static final String SYSTEM_USER_MANAGER_USER_PREFIX = SYSTEM_USER_MANAGER_USER_PATH //NOSONAR
        + "/";

    /**
     * @deprecated no longer used.  Use {@link SystemUserManagerPaths} service instead.
     */
    @Deprecated
    public static final String SYSTEM_USER_MANAGER_GROUP_PREFIX = SYSTEM_USER_MANAGER_GROUP_PATH //NOSONAR
        + "/";

    private boolean resourcesForNestedProperties = true;

    @Activate
    protected void activate(final Config config) {
        systemUserManagerPath = OsgiUtil.toString(config.provider_root(), DEFAULT_SYSTEM_USER_MANAGER_PATH);
        systemUserManagerUserPath = String.format("%s/user", systemUserManagerPath);
        systemUserManagerUserPrefix = String.format("%s/", systemUserManagerUserPath);
        systemUserManagerGroupPath = String.format("%s/group", systemUserManagerPath);
        systemUserManagerGroupPrefix = String.format("%s/", systemUserManagerGroupPath);
        resourcesForNestedProperties = config.resources_for_nested_properties();
    }
    
    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.impl.resource.SystemUserManagerPaths#getPath()
     */
    @Override
    public String getRootPath() {
        return systemUserManagerPath;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.impl.resource.SystemUserManagerPaths#getUserPath()
     */
    @Override
    public String getUsersPath() {
        return systemUserManagerUserPath;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.impl.resource.SystemUserManagerPaths#getUserPrefix()
     */
    @Override
    public String getUserPrefix() {
        return systemUserManagerUserPrefix;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.impl.resource.SystemUserManagerPaths#getGroupPath()
     */
    @Override
    public String getGroupsPath() {
        return systemUserManagerGroupPath;
    }

    /* (non-Javadoc)
     * @see org.apache.sling.jackrabbit.usermanager.impl.resource.SystemUserManagerPaths#getGroupPrefix()
     */
    @Override
    public String getGroupPrefix() {
        return systemUserManagerGroupPrefix;
    }

    @Override
    public Resource getResource(ResolveContext<Object> ctx,
            String path,
            ResourceContext resourceContext,
            Resource parent) {

        // handle resources for the virtual container resources
        if (path.equals(systemUserManagerPath)) {
            return new SyntheticResource(ctx.getResourceResolver(), path,
                "sling/userManager");
        } else if (path.equals(systemUserManagerUserPath)) {
            return new SyntheticResource(ctx.getResourceResolver(), path, "sling/users");
        } else if (path.equals(systemUserManagerGroupPath)) {
            return new SyntheticResource(ctx.getResourceResolver(), path, "sling/groups");
        }

        // the principalId should be the first segment after the prefix
        String suffix = null;
        if (path.startsWith(systemUserManagerUserPrefix)) {
            suffix = path.substring(systemUserManagerUserPrefix.length());
        } else if (path.startsWith(systemUserManagerGroupPrefix)) {
            suffix = path.substring(systemUserManagerGroupPrefix.length());
        }

        if (suffix != null) {
            String pid;
            String relPath;
            int firstSlash = suffix.indexOf('/');
            if (firstSlash == -1) {
                pid = suffix;
                relPath = null;
            } else {
                pid = suffix.substring(0, firstSlash);
                relPath = suffix.substring(firstSlash + 1);
            }
            try {
                Session session = ctx.getResourceResolver().adaptTo(Session.class);
                if (session != null) {
                    UserManager userManager = AccessControlUtil.getUserManager(session);
                    if (userManager != null) {
                        Authorizable authorizable = userManager.getAuthorizable(pid);
                        if (authorizable != null) {
                            // found the Authorizable, so return the resource
                            // that wraps it.
                            if (relPath == null) {
                                return new AuthorizableResource(authorizable,
                                        ctx.getResourceResolver(), path,
                                        AuthorizableResourceProvider.this);
                            } else if (resourcesForNestedProperties) {
                                // check if the relPath resolves valid property names
                                Iterator<String> propertyNames = getPropertyNames(relPath, authorizable);
                                if (propertyNames.hasNext()) {
                                    // provide a resource that wraps for the specific nested properties
                                    return new NestedAuthorizableResource(authorizable,
                                            ctx.getResourceResolver(), path,
                                            AuthorizableResourceProvider.this,
                                            relPath);
                                }
                            }
                        }
                    }
                }
            } catch (RepositoryException re) {
                throw new SlingException(
                    "Error looking up Authorizable for principal: " + pid, re);
            }
        }
        return null;
    }

    protected static Iterator<String> getPropertyNames(String relPath, Authorizable authorizable) {
        Iterator<String> propertyNames;
        try {
            // TODO: there isn't any way to check if relPath is valid
            //    as this call throws an exception instead of returning null
            //    or an empty iterator.
            propertyNames = authorizable.getPropertyNames(relPath);
        } catch (RepositoryException re) {
            Logger logger = LoggerFactory.getLogger(AuthorizableResourceProvider.class);
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to get property names", re);
            }
            propertyNames = Collections.emptyIterator();
        }
        return propertyNames;
    }

    @Override
    public Iterator<Resource> listChildren(ResolveContext<Object> ctx, Resource parent) {
        try {
            String path = parent.getPath();

            // handle children of /system/userManager
            if (systemUserManagerPath.equals(path)) {
                List<Resource> resources = new ArrayList<>();
                resources.add(getResource(ctx,
                        systemUserManagerUserPath, null, null));
                    resources.add(getResource(ctx,
                        systemUserManagerGroupPath, null, null));
                return resources.iterator();
            }

            int searchType = -1;
            if (systemUserManagerUserPath.equals(path)) {
                searchType = PrincipalManager.SEARCH_TYPE_NOT_GROUP;
            } else if (systemUserManagerGroupPath.equals(path)) {
                searchType = PrincipalManager.SEARCH_TYPE_GROUP;
            }
            if (searchType != -1) {
                PrincipalIterator principals = null;
                ResourceResolver resourceResolver = parent.getResourceResolver();
                Session session = resourceResolver.adaptTo(Session.class);
                if (session != null) {
                    PrincipalManager principalManager = AccessControlUtil.getPrincipalManager(session);
                    principals = principalManager.getPrincipals(searchType);
                }

                if (principals != null) {
                    return new ChildrenIterator(parent, principals);
                }
            } else if (resourcesForNestedProperties) {
                // handle nested property containers

                String suffix = null;
                if (path.startsWith(systemUserManagerUserPrefix)) {
                    suffix = path.substring(systemUserManagerUserPrefix.length());
                } else if (path.startsWith(systemUserManagerGroupPrefix)) {
                    suffix = path.substring(systemUserManagerGroupPrefix.length());
                }

                if (suffix != null) {
                    // the principalId should be the first segment after the prefix
                    String pid;
                    // the relPath is whatever is leftover
                    String relPath;
                    int firstSlash = suffix.indexOf('/');
                    if (firstSlash == -1) {
                        pid = suffix;
                        relPath = null;
                    } else {
                        pid = suffix.substring(0, firstSlash);
                        relPath = suffix.substring(firstSlash + 1);
                    }
                    try {
                        Session session = ctx.getResourceResolver().adaptTo(Session.class);
                        if (session != null) {
                            UserManager userManager = AccessControlUtil.getUserManager(session);
                            if (userManager != null) {
                                Authorizable authorizable = userManager.getAuthorizable(pid);
                                if (authorizable != null) {
                                    Resource r = ctx.getResourceResolver().resolve(authorizable.getPath());
                                    if (relPath != null) {
                                        r = r.getChild(relPath);
                                    }
                                    if (r != null) {
                                        // only include the children that are nested property containers
                                        List<Resource> propContainers = filterPropertyContainers(relPath, authorizable, r);
                                        if (!propContainers.isEmpty()) {
                                            return new NestedChildrenIterator(parent, pid, r.getChildren().iterator());
                                        }
                                    }
                                }
                            }
                        }
                    } catch (RepositoryException re) {
                        throw new SlingException(
                            "Error looking up Authorizable for principal: " + pid, re);
                    }
                }
            }
        } catch (RepositoryException re) {
            throw new SlingException("Error listing children of resource: "
                + parent.getPath(), re);
        }

        return null;
    }

    /**
     * Filter the resource children to return only the resources that are
     * nested property containers
     * 
     * @param relPath the relative path to start from
     * @param authorizable the user or group
     * @param r the resource to filter the children of
     * @return list of resources that are property containers
     */
    protected List<Resource> filterPropertyContainers(String relPath, Authorizable authorizable, Resource r) {
        List<Resource> propContainers = new ArrayList<>();
        for (Resource cr : r.getChildren()) {
            String childRelPath;
            if (relPath == null) {
                childRelPath = cr.getName();
            } else {
                childRelPath = String.format("%s/%s", relPath, cr.getName());
            }
            if (getPropertyNames(childRelPath, authorizable).hasNext()) {
                propContainers.add(cr);
            } else {
                // child is not a property container?
                if (log.isDebugEnabled()) {
                    log.debug("skipping child that is not appear to be a nested property container: {}", cr.getName());
                }
            }
        }
        return propContainers;
    }

    private abstract class BaseChildrenIterator implements Iterator<Resource> {
        private Resource parent;
        private Iterator<?> children;

        private BaseChildrenIterator(Resource parent, Iterator<?> children) {
            this.parent = parent;
            this.children = children;
        }

        @Override
        public boolean hasNext() {
            return children.hasNext();
        }

        @Override
        public Resource next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Resource next = null;
            Object child = children.next();
            String principalName = toPrincipalName(child);
            try {
                ResourceResolver resourceResolver = parent.getResourceResolver();
                Session session = resourceResolver.adaptTo(Session.class);
                if (session != null) {
                    UserManager userManager = AccessControlUtil.getUserManager(session);
                    if (userManager != null) {
                        Authorizable authorizable = userManager.getAuthorizable(principalName);
                        if (authorizable != null) {
                            String path;
                            if (authorizable.isGroup()) {
                                path = systemUserManagerGroupPrefix
                                    + principalName;
                            } else {
                                path = systemUserManagerUserPrefix
                                    + principalName;
                            }
                            next = createNext(child, resourceResolver, authorizable, path);
                        }
                    }
                }
            } catch (RepositoryException re) {
                log.error("Exception while looking up authorizable resource.",
                    re);
            }
            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        protected abstract String toPrincipalName(Object child);

        protected abstract Resource createNext(Object child, ResourceResolver resourceResolver,
                Authorizable authorizable, String path) throws RepositoryException; 

    }

    private final class NestedChildrenIterator extends BaseChildrenIterator {

        private String principalName;

        private NestedChildrenIterator(Resource parent, String principalName, Iterator<Resource> children) {
            super(parent, children);
            this.principalName = principalName;
        }

        @Override
        protected String toPrincipalName(Object child) {
            return principalName;
        }

        @Override
        protected Resource createNext(Object child, ResourceResolver resourceResolver, Authorizable authorizable,
                String path) throws RepositoryException {
            Resource next = null;
            if (child instanceof Resource) {
                Resource childResource = (Resource)child;
                //calculate the path relative to the home folder root
                String relPath = childResource.getPath().substring(authorizable.getPath().length() + 1);

                // check if the relPath resolves any valid property names
                Iterator<String> propertyNames = getPropertyNames(relPath, authorizable);
                if (propertyNames.hasNext()) {
                    next = new NestedAuthorizableResource(authorizable,
                            resourceResolver, String.format("%s/%s", path, relPath),
                            AuthorizableResourceProvider.this,
                            relPath);
                }
            }
            return next;
        }

    }

    private final class ChildrenIterator extends BaseChildrenIterator {

        public ChildrenIterator(Resource parent, PrincipalIterator principals) {
            super(parent, principals);
        }

        @Override
        protected String toPrincipalName(Object child) {
            String principalName = null;
            if (child instanceof Principal) {
                principalName = ((Principal)child).getName();
            }
            return principalName;
        }

        @Override
        protected Resource createNext(Object child, ResourceResolver resourceResolver, Authorizable authorizable,
                String path) throws RepositoryException {
            return new AuthorizableResource(authorizable,
                    resourceResolver, path,
                    AuthorizableResourceProvider.this);
        }

    }

}
