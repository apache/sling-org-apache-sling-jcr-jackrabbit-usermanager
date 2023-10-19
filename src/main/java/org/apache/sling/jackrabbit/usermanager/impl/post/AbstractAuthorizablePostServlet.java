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

import java.lang.reflect.Array;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator.NameInfo;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.apache.sling.servlets.post.impl.helper.DateParser;
import org.apache.sling.servlets.post.impl.helper.RequestProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all the POST servlets for the UserManager operations
 */
public abstract class AbstractAuthorizablePostServlet extends
        AbstractPostServlet {
    private static final long serialVersionUID = -5918670409789895333L;

    private static final class PrincipalNameGeneratorHolder {
        private final PrincipalNameGenerator generator;
        private final int ranking;

        private PrincipalNameGeneratorHolder(PrincipalNameGenerator generator, int ranking) {
            this.generator = generator;
            this.ranking = ranking;
        }

        public PrincipalNameGenerator getGenerator() {
            return generator;
        }

    }

    protected static final String RP_NODE_NAME_VALUE_FROM = String.format("%s%s", SlingPostConstants.RP_NODE_NAME, SlingPostConstants.VALUE_FROM_SUFFIX);
    protected static final String RP_NODE_NAME_HINT_VALUE_FROM = String.format("%s%s", SlingPostConstants.RP_NODE_NAME_HINT, SlingPostConstants.VALUE_FROM_SUFFIX);

    public static final String PROP_DATE_FORMAT = "servlet.post.dateFormats";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAuthorizablePostServlet.class);

    private final SecureRandom randomCollisionIndex = new SecureRandom();
    private transient DateParser dateParser;

    protected transient SystemUserManagerPaths systemUserManagerPaths;

    protected void bindSystemUserManagerPaths(SystemUserManagerPaths sump) {
        this.systemUserManagerPaths = sump;
    }

    /**
     * The principal name generators
     */
    protected transient LinkedList<PrincipalNameGeneratorHolder> principalNameGenerators = new LinkedList<>();

    /**
     * The optional principal name filter
     */
    protected transient PrincipalNameFilter principalNameFilter;

    /**
     * Bind a new principal name generator
     */
//    @Reference(service = PrincipalNameGenerator.class)
    protected void bindPrincipalNameGenerator(final PrincipalNameGenerator generator, final Map<String, Object> properties) {
        final PrincipalNameGeneratorHolder pngh = new PrincipalNameGeneratorHolder(generator, getRanking(properties));
        synchronized (principalNameGenerators) {
            this.principalNameGenerators.add(pngh);
            Collections.sort(this.principalNameGenerators, (o1, o2) -> 
                Integer.compare(o1.ranking, o2.ranking));
        }
    }
    protected void unbindPrincipalNameGenerator(final PrincipalNameGenerator generator) {
        synchronized (principalNameGenerators) {
            principalNameGenerators.removeIf(h -> h.generator == generator);
        }
    }

    /**
     * Bind a new principal name filter
     */
//    @Reference(service = PrincipalNameFilter.class)
    protected void bindPrincipalNameFilter(final PrincipalNameFilter filter) {
        this.principalNameFilter = filter;
    }
    protected void unbindPrincipalNameFilter(final PrincipalNameFilter filter) {
        if (filter != null && filter.equals(this.principalNameFilter)) {
            this.principalNameFilter = null;
        }
    }

    /**
     * Get or generate the name of the principal being created
     * 
     * @param request the current request
     * @return the principal name
     */
    protected String getOrGeneratePrincipalName(Session jcrSession, Map<String, ?> properties, AuthorizableType type) throws RepositoryException {
        String principalName = null;
        PrincipalNameGenerator defaultPrincipalNameGenerator = null;
        PrincipalNameGenerator principalNameGenerator = null;
        synchronized (principalNameGenerators) {
            if (!principalNameGenerators.isEmpty()) {
                defaultPrincipalNameGenerator = principalNameGenerators.getFirst().getGenerator();
                principalNameGenerator = principalNameGenerators.getLast().getGenerator();
            }
        }
        if (principalNameGenerator != null) {
            NameInfo nameInfo = principalNameGenerator.getPrincipalName(properties, type, 
                    principalNameFilter, defaultPrincipalNameGenerator);
            if (nameInfo == null && defaultPrincipalNameGenerator != null) {
                // fallback to the default impl
                nameInfo = defaultPrincipalNameGenerator.getPrincipalName(properties, type, 
                        principalNameFilter, defaultPrincipalNameGenerator);
            }
            if (nameInfo != null) {
                principalName = nameInfo.getPrincipalName();
                if (principalName != null && nameInfo.isMakeUnique()) {
                    // make sure the name is not already used
                    UserManager um = AccessControlUtil.getUserManager(jcrSession);

                    // if resulting authorizable exists, add a random suffix until it's not the case
                    // anymore
                    final int MAX_TRIES = 1000;
                    if (um.getAuthorizable(principalName) != null ) {
                        for (int i=0; i < MAX_TRIES; i++) {
                            final int uniqueIndex = randomCollisionIndex.nextInt(9999);
                            String newPrincipalName = principalName + "_" + uniqueIndex;
                            if (um.getAuthorizable(newPrincipalName) == null) {
                                // found unused value, so use it
                                principalName = newPrincipalName;
                                break;
                            }
                        }

                        // Give up after MAX_TRIES
                        if (um.getAuthorizable(principalName) != null ) {
                            throw new RepositoryException(
                                "Collision in generated principal names, generated name " + principalName + " already exists");
                        }
                    }
                }
            }
        } else {
            // fallback to the old behavior
            Object obj = properties.get(SlingPostConstants.RP_NODE_NAME);
            if (obj instanceof String[] && Array.getLength(obj) == 1) {
                principalName = ((String[])obj)[0];
            } else if (obj instanceof String) {
                principalName= ((String)obj);
            }
        }

        return principalName;
    }

    // ---------- SCR Integration ----------------------------------------------

    protected void activate(Map<String, Object> props) {
        dateParser = new DateParser();
        String[] dateFormats = OsgiUtil.toStringArray(props.get(PROP_DATE_FORMAT));
        for (String dateFormat : dateFormats) {
            dateParser.register(dateFormat);
        }
    }

    protected void deactivate() {
        dateParser = null;
    }

    // ------ The methods below are based on the private methods from the
    // ModifyOperation class -----

    /**
     * Collects the properties that form the content to be written back to the
     * repository.
     * @param properties the properties out of which to generate the {@link RequestProperty}s
     * @return the list of {@link RequestProperty}s
     * @deprecated use {@link #collectContentMap(Map)} instead since 2.2.18
     */
    @Deprecated
    protected Collection<RequestProperty> collectContent(
            Map<String, ?> properties) {
        return collectContentMap(properties).values();
    }
    /**
     * Collects the properties that form the content to be written back to the
     * repository.
     * @param properties the properties out of which to generate the {@link RequestProperty}s
     * @return the list of {@link RequestProperty}s
     */
    protected Map<String, RequestProperty> collectContentMap(
            Map<String, ?> properties) {

        boolean requireItemPrefix = requireItemPathPrefix(properties);

        // walk the request parameters and collect the properties (the key is the property path).
        Map<String, RequestProperty> reqProperties = new HashMap<>();
        for (Map.Entry<String, ?> e : properties.entrySet()) {
            final String paramName = e.getKey();

            // do not store parameters with names starting with sling:post
            boolean skipParam = paramName.startsWith(SlingPostConstants.RP_PREFIX);
            // SLING-298: skip form encoding parameter
            if (paramName.equals("_charset_")) {
                skipParam = true;
            }
            // skip parameters that do not start with the save prefix
            if (requireItemPrefix && !hasItemPathPrefix(paramName)) {
                skipParam = true;
            }

            // ensure the paramName is an absolute property path (i.e. starts with "/", where root refers to the authorizable's root, https://issues.apache.org/jira/browse/SLING-1577)
            String propPath;
            if (paramName.startsWith("./")) {
                propPath = paramName.substring(1);
            } else {
                propPath = String.format("/%s", paramName);
            }

            if (propPath.indexOf("..") != -1) {
                // it is not supported to set properties potentially outside of the authorizable node
                LOG.warn("Property path containing '..' is not supported, skipping parameter {}", paramName);
                skipParam = true;
            }

            if (!skipParam) {
                // @TypeHint example
                // <input type="text" name="./age" />
                // <input type="hidden" name="./age@TypeHint" value="long" />
                // causes the setProperty using the 'long' property type
                if (propPath.endsWith(SlingPostConstants.TYPE_HINT_SUFFIX)) {
                    RequestProperty prop = getOrCreateRequestProperty(
                        reqProperties, propPath,
                        SlingPostConstants.TYPE_HINT_SUFFIX);

                    String typeHintValue = convertToString(e.getValue());
                    if (typeHintValue != null) {
                        prop.setTypeHintValue(typeHintValue);
                    }
                } else if (propPath.endsWith(SlingPostConstants.DEFAULT_VALUE_SUFFIX)) {
                    // @DefaultValue
                    RequestProperty prop = getOrCreateRequestProperty(
                        reqProperties, propPath,
                        SlingPostConstants.DEFAULT_VALUE_SUFFIX);

                    prop.setDefaultValues(convertToRequestParameterArray(e.getValue()));
                } else if (propPath.endsWith(SlingPostConstants.VALUE_FROM_SUFFIX)) {
                    // SLING-130: VALUE_FROM_SUFFIX means take the value of this
                    // property from a different field
                    // @ValueFrom example:
                    // <input name="./Text@ValueFrom" type="hidden" value="fulltext" />
                    // causes the JCR Text property to be set to the value of the
                    // fulltext form field.
                    RequestProperty prop = getOrCreateRequestProperty(
                        reqProperties, propPath,
                        SlingPostConstants.VALUE_FROM_SUFFIX);

                    // @ValueFrom params must have exactly one value, else ignored
                    String [] valueFrom = convertToStringArray(e.getValue());
                    if (valueFrom.length == 1) {
                        String refName = valueFrom[0];
                        prop.setValues(convertToRequestParameterArray(refName));
                    }
                } else if (propPath.endsWith(SlingPostConstants.SUFFIX_DELETE)) {
                    // SLING-458: Allow Removal of properties prior to update
                    // @Delete example:
                    // <input name="./Text@Delete" type="hidden" />
                    // causes the JCR Text property to be deleted before update
                    RequestProperty prop = getOrCreateRequestProperty(
                        reqProperties, propPath, SlingPostConstants.SUFFIX_DELETE);

                    prop.setDelete(true);
                } else if (propPath.endsWith(SlingPostConstants.SUFFIX_MOVE_FROM)) {
                    // SLING-455: @MoveFrom means moving content to another location
                    // @MoveFrom example:
                    // <input name="./Text@MoveFrom" type="hidden" value="/tmp/path" />
                    // causes the JCR Text property to be set by moving the /tmp/path
                    // property to Text.

                    // don't support @MoveFrom here
                    LOG.warn("Suffix {} not supported, skipping parameter {}", SlingPostConstants.SUFFIX_MOVE_FROM, paramName);
                } else if (propPath.endsWith(SlingPostConstants.SUFFIX_COPY_FROM)) {
                    // SLING-455: @CopyFrom means moving content to another location
                    // @CopyFrom example:
                    // <input name="./Text@CopyFrom" type="hidden" value="/tmp/path" />
                    // causes the JCR Text property to be set by copying the /tmp/path
                    // property to Text.

                    // don't support @CopyFrom here
                    LOG.warn("Suffix {} not supported, skipping parameter {}", SlingPostConstants.SUFFIX_COPY_FROM, paramName);
                } else {
                    // plain property, create from values
                    RequestProperty prop = getOrCreateRequestProperty(reqProperties,
                        propPath, null);
                    prop.setValues(convertToRequestParameterArray(e.getValue()));
                }
            }
        }

        return reqProperties;
    }

    /**
     * Returns the request property for the given property path. If such a
     * request property does not exist yet it is created and stored in the
     * <code>props</code>.
     *
     * @param props The map of already seen request properties 
     *               (key is the property path).
     * @param paramPath The absolute path of the property including the
     *            <code>suffix</code> to be looked up.
     * @param suffix The (optional) suffix to remove from the
     *            <code>paramName</code> before looking it up.
     * @return The {@link RequestProperty} for the <code>paramName</code>.
     */
    private RequestProperty getOrCreateRequestProperty(
            Map<String, RequestProperty> props, String paramPath, String suffix) {
        if (suffix != null && paramPath.endsWith(suffix)) {
            paramPath = paramPath.substring(0, paramPath.length()
                - suffix.length());
        }

        return props.computeIfAbsent(paramPath, RequestProperty::new);
    }

    /**
     * Removes all properties listed as {@link RequestProperty#isDelete()} from
     * the authorizable.
     *
     * @param authorizable The
     *            <code>org.apache.jackrabbit.api.security.user.Authorizable</code>
     *            that should have properties deleted.
     * @param reqProperties The collection of request properties to check for
     *            properties to be removed.
     * @param changes The <code>List</code> to be updated with
     *            information on deleted properties.
     * @throws RepositoryException Is thrown if an error occurrs checking or
     *             removing properties.
     */
    protected void processDeletes(Authorizable authorizable,
            Collection<RequestProperty> reqProperties,
            List<Modification> changes) throws RepositoryException {

        for (RequestProperty property : reqProperties) {
            if (property.isDelete()) {
                // SLING-7901 - remove artificial "/" prepended to the prop path
                String relativePath = property.getPath().substring(1);

                if (authorizable.hasProperty(relativePath)) {
                    authorizable.removeProperty(relativePath);
                    changes.add(Modification.onDeleted(relativePath));
                }
            }
        }
    }

    /**
     * Helper utility to join a path with a descandant subpath
     *
     * @param parentPath the parent path
     * @param other the descendant path
     * @return the joined path
     */
    protected String concatPath(@NotNull String parentPath, @NotNull String other) {
        return String.format("%s/%s", parentPath, other);
    }

    /**
     * Create resource(s) according to current request
     *
     * @param session the sessioin to write the authorizable properties
     * @param authorizable The
     *            <code>org.apache.jackrabbit.api.security.user.Authorizable</code>
     *            that should have properties deleted.
     * @param reqProperties The collection of request properties to check for
     *            properties to be removed.
     * @param changes The <code>List</code> to be updated with
     *            information on deleted properties.
     * @throws RepositoryException Is thrown if an error occurrs checking or
     *             removing properties.
     */
    protected void processCreate(Session session, Authorizable authorizable,
            Map<String, RequestProperty> reqProperties,
            List<Modification> changes) throws RepositoryException {

        @NotNull
        String path = authorizable.getPath();
        for (RequestProperty prop : reqProperties.values()) {
            String propName = prop.getName();
            if (JcrConstants.JCR_PRIMARYTYPE.equals(propName) || JcrConstants.JCR_MIXINTYPES.equals(propName)) {
                String parentPath = prop.getParentPath();
                if (parentPath == null && JcrConstants.JCR_PRIMARYTYPE.equals(propName)) {
                    // don't allow changing the primaryType of the user/group root node
                    continue;
                }

                String tp = null;
                if (parentPath == null || "/".equals(parentPath)) {
                    tp = path;
                } else if (parentPath.startsWith("/")){
                    tp = path.concat(parentPath);
                }
                if (tp != null && (tp.equals(path) || tp.startsWith(path.concat("/")))) {
                    Node node = null;
                    if (session.nodeExists(tp)) {
                        node = session.getNode(tp);
                    } else {
                        Node tempNode = session.getNode(path);
                        // create any missing intermediate nodes
                        if (parentPath != null) {
                            for (String segment : parentPath.split("/")) {
                                String tempPath = concatPath(tempNode.getPath(), segment);
                                if (session.nodeExists(tempPath)) {
                                    tempNode = session.getNode(tempPath);
                                } else {
                                    String primaryType = getPrimaryType(reqProperties, tempPath);
                                    if (primaryType != null) {
                                        tempNode = tempNode.addNode(segment, primaryType);
                                    } else {
                                        tempNode = tempNode.addNode(segment);
                                    }
                                    changes.add(Modification.onCreated(tempNode.getPath()));
                                }
                            }
                        }
                        node = tempNode;
                    }

                    if (node != null) {
                        // only allow changing the primaryType of the ancestors, not the root
                        if (JcrConstants.JCR_PRIMARYTYPE.equals(propName)) {
                            if (tp.equals(path)) {
                                // don't allow changing the primaryType of the user home root
                                throw new AccessDeniedException("Access denied.");
                            } else {
                                final String nodeType = prop == null ? null : prop.getStringValues()[0];
                                if (nodeType != null && !node.isNodeType(nodeType)) {
                                    node.setPrimaryType(nodeType);
                                    changes.add(Modification.onModified(concatPath(node.getPath(), propName)));
                                }
                            }
                        } else if (JcrConstants.JCR_MIXINTYPES.equals(propName)) {
                            String[] mixins = (prop == null) || !prop.hasValues() ? null : prop.getStringValues();
                            if (mixins != null) {
                                for (final String mixin : mixins) {
                                    if (!node.isNodeType(mixin)) {
                                        node.addMixin(mixin);
                                        changes.add(Modification.onModified(concatPath(node.getPath(), propName)));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks the collected content for a jcr:primaryType property at the
     * specified path.
     *
     * @param path path to check
     * @return the primary type or <code>null</code>
     */
    private String getPrimaryType(Map<String, RequestProperty> reqProperties,
            String path) {
        RequestProperty prop = reqProperties.get(concatPath(path, JcrConstants.JCR_PRIMARYTYPE));
        return prop == null ? null : prop.getStringValues()[0];
    }

    /**
     * Writes back the content
     * @param session the sessioin to write the authorizable properties
     * @param authorizable the authorizable to modify
     * @param reqProperties the properties to write
     * @param changes the list of changes which is supposed to be extended
     *
     * @throws RepositoryException if a repository error occurs
     */
    protected void writeContent(Session session, Authorizable authorizable,
            Collection<RequestProperty> reqProperties,
            List<Modification> changes) throws RepositoryException {

        for (RequestProperty prop : reqProperties) {
            if (prop.hasValues()) {
                // remove artificial "/" prepended to the prop path
                String relativePath = prop.getPath().substring(1);
                
                // skip jcr special properties
                String name = prop.getName();
                boolean isSpecialProp = name.equals(JcrConstants.JCR_PRIMARYTYPE)
                    || name.equals(JcrConstants.JCR_MIXINTYPES);
                if (authorizable.isGroup()) {
                    if (relativePath.equals("groupId")) {
                        // skip these
                        isSpecialProp = true;
                    }
                } else {
                    if (relativePath.equals("userId")
                        || relativePath.equals("pwd")
                        || relativePath.equals("pwdConfirm")) {
                        // skip these
                        isSpecialProp = true;
                    }
                }
                if (!isSpecialProp &&  // skip these
                        !prop.isFileUpload()) { // don't handle files for user properties for now.
                    setPropertyAsIs(session, authorizable, prop, changes);
                }
            }
        }
    }

    /**
     * Find the PropertyDefinition for the specified propName
     * 
     * @param propName the propertyName to check
     * @param parentNode the parent node where the property will be set
     * @return the property definition of the property or null if it could not be determined
     */
    private @Nullable PropertyDefinition resolvePropertyDefinition(@NotNull String propName, @NotNull Node parentNode) throws RepositoryException {
        NodeType primaryNodeType = parentNode.getPrimaryNodeType();
        // try the primary type
        PropertyDefinition propDef = resolvePropertyDefinition(propName, primaryNodeType);
        if (propDef == null) {
            // not found in the primary type, so try the mixins
            NodeType[] mixinNodeTypes = parentNode.getMixinNodeTypes();
            for (NodeType mixinNodeType : mixinNodeTypes) {
                propDef = resolvePropertyDefinition(propName, mixinNodeType);
                if (propDef != null) {
                    break;
                }
            }
        }
        return propDef;
    }

    /**
     * Inspect the NodeType definition to try to determine the
     * requiredType for the specified property
     * 
     * @param propName the propertyName to check
     * @param parentNode the parent node where the property will be set
     * @return the required type of the property or {@link PropertyType#UNDEFINED} if it could not be determined
     */
    private @Nullable PropertyDefinition resolvePropertyDefinition(@NotNull String propName, @NotNull NodeType nodeType) {
        return Stream.of(nodeType.getPropertyDefinitions())
            .filter(pd -> propName.equals(pd.getName()))
            .findFirst()
            .orElse(null);
    }

    /**
     * set property without processing, except for type hints
     *
     * @param parent the parent node
     * @param prop the request property
     * @throws RepositoryException if a repository error occurs.
     */
    private void setPropertyAsIs(Session session, Authorizable parent,
            RequestProperty prop, List<Modification> changes)
            throws RepositoryException {

        String parentPath;
        if (parent.isGroup()) {
            parentPath = systemUserManagerPaths.getGroupPrefix()
                + parent.getID();
        } else {
            parentPath = systemUserManagerPaths.getUserPrefix()
                + parent.getID();
        }

        // no explicit typehint
        int type = PropertyType.UNDEFINED;
        boolean multiValue = false;
        if (prop.getTypeHint() != null) {
            try {
                type = PropertyType.valueFromName(prop.getTypeHint());
                multiValue = prop.hasMultiValueTypeHint();
            } catch (Exception e) {
                // ignore
            }
        } else {
            // inspect the node type definitions to see if there is a known PropertyDefintion
            //  for the target property that we can get the required type from
            String propParentPath = String.format("%s%s", parent.getPath(), prop.getParentPath());
            if (session.nodeExists(propParentPath)) {
                // try to determine required property type from the NodeType definition
                Node parentNode = session.getNode(propParentPath);
                @Nullable
                PropertyDefinition propDef = resolvePropertyDefinition(prop.getName(), parentNode);
                if (propDef != null) {
                    type = propDef.getRequiredType();
                    multiValue = propDef.isMultiple();
                }
            }
        }
        // remove artificial "/" prepended to the prop path
        String relativePath = prop.getPath().substring(1);

        String[] values = prop.getStringValues();
        if (values == null) {
            // remove property
            boolean removedProp = removePropertyIfExists(parent, relativePath);
            if (removedProp) {
                changes.add(Modification.onDeleted(parentPath + "/"
                    + relativePath));
            }
        } else if (values.length == 0) {
            // do not create new prop here, but clear existing
            if (parent.hasProperty(relativePath)) {
                Value val = session.getValueFactory().createValue("");
                parent.setProperty(relativePath, val);
                changes.add(Modification.onModified(parentPath + "/"
                    + relativePath));
            }
        } else if (values.length == 1) {
            // if the provided value is the empty string, we don't have to do
            // anything.
            if (values[0].length() == 0) {
                boolean removedProp = removePropertyIfExists(parent, relativePath);
                if (removedProp) {
                    changes.add(Modification.onDeleted(parentPath + "/"
                        + relativePath));
                }
            } else {
                // modify property
                if (type == PropertyType.DATE) {
                    // try conversion
                    Calendar c = dateParser.parse(values[0]);
                    if (c != null) {
                        if (multiValue) {
                            final Value[] array = new Value[1];
                            array[0] = session.getValueFactory().createValue(c);
                            parent.setProperty(relativePath, array);
                            changes.add(Modification.onModified(parentPath
                                + "/" + relativePath));
                        } else {
                            Value cVal = session.getValueFactory().createValue(
                                c);
                            parent.setProperty(relativePath, cVal);
                            changes.add(Modification.onModified(parentPath
                                + "/" + relativePath));
                        }
                        return;
                    }
                    // fall back to default behaviour
                }
                if (type == PropertyType.UNDEFINED) {
                    Value val = session.getValueFactory().createValue(
                        values[0], PropertyType.STRING);
                    parent.setProperty(relativePath, val);
                } else {
                    if (multiValue) {
                        final Value[] array = new Value[1];
                        array[0] = session.getValueFactory().createValue(
                            values[0], type);
                        parent.setProperty(relativePath, array);
                    } else {
                        Value val = session.getValueFactory().createValue(
                            values[0], type);
                        parent.setProperty(relativePath, val);
                    }
                }
                changes.add(Modification.onModified(parentPath + "/"
                    + relativePath));
            }
        } else {
            if (type == PropertyType.DATE) {
                // try conversion
                Calendar[] c = dateParser.parse(values);
                if (c != null) {
                    ValueFactory vf = session.getValueFactory();
                    Value[] cVals = Stream.of(c)
                            .map(vf::createValue)
                            .toArray(Value[]::new);
                    parent.setProperty(relativePath, cVals);
                    changes.add(Modification.onModified(parentPath + "/"
                        + relativePath));
                    return;
                }
                // fall back to default behaviour
            }

            Value[] vals = new Value[values.length];
            if (type == PropertyType.UNDEFINED) {
                for (int i = 0; i < values.length; i++) {
                    vals[i] = session.getValueFactory().createValue(values[i]);
                }
            } else {
                for (int i = 0; i < values.length; i++) {
                    vals[i] = session.getValueFactory().createValue(values[i],
                        type);
                }
            }
            parent.setProperty(relativePath, vals);
            changes.add(Modification.onModified(parentPath + "/"
                + relativePath));
        }

    }

    /**
     * Removes the property with the given name from the authorizable if it
     * exists.
     *
     * @param authorizable the <code>org.apache.jackrabbit.api.security.user.Authorizable</code>
     *         that should have properties deleted.
     * @param path the path of the property to remove
     * @return path of the property that was removed or <code>null</code> if it
     *         was not removed
     * @throws RepositoryException if a repository error occurs.
     */
    private boolean removePropertyIfExists(Authorizable authorizable, String path)
            throws RepositoryException {
        if (authorizable.getProperty(path) != null) {
            authorizable.removeProperty(path);
            return true;
        }
        return false;
    }

    // ------ These methods were copied from AbstractSlingPostOperation ------

    /**
     * @param name the name
     * @return <code>true</code> if the <code>name</code> starts with either of
     * the prefixes {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_CURRENT},
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_PARENT} and 
     * {@link SlingPostConstants#ITEM_PREFIX_ABSOLUTE}
     */
    protected boolean hasItemPathPrefix(String name) {
        return name.startsWith(SlingPostConstants.ITEM_PREFIX_ABSOLUTE)
            || name.startsWith(SlingPostConstants.ITEM_PREFIX_RELATIVE_CURRENT)
            || name.startsWith(SlingPostConstants.ITEM_PREFIX_RELATIVE_PARENT);
    }

    /**
     * @param properties the request parameters
     * @return {@code true} if any of the request parameters starts with
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_CURRENT}.
     * In this case only parameters starting with either of the prefixes
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_CURRENT},
     * {@link SlingPostConstants#ITEM_PREFIX_RELATIVE_PARENT}
     * and {@link SlingPostConstants#ITEM_PREFIX_ABSOLUTE} are
     * considered as providing content to be stored. Otherwise all parameters
     * not starting with the command prefix <code>:</code> are considered as
     * parameters to be stored.
     */
    protected final boolean requireItemPathPrefix(
            Map<String, ?> properties) {

        boolean requirePrefix = false;

        Iterator<String> iterator = properties.keySet().iterator();
        while (iterator.hasNext() && !requirePrefix) {
            String name = iterator.next();
            requirePrefix = name.startsWith(SlingPostConstants.ITEM_PREFIX_RELATIVE_CURRENT);
        }

        return requirePrefix;
    }


    protected String convertToString(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof String) {
            return (String)obj;
        } else if (obj instanceof String[]) {
            String [] values = (String[])obj;
            if (values.length > 0) {
                return values[0];
            }
            return null;
        } else if (obj instanceof RequestParameter) {
            ((RequestParameter)obj).getString();
        } else if (obj instanceof RequestParameter[]) {
            RequestParameter[] values = (RequestParameter[])obj;
            if (values.length > 0) {
                return values[0].getString();
            }
            return null;
        }
        return null;
    }

    protected @NotNull String[] convertToStringArray(Object obj) {
        String [] strArray = null;
        if (obj instanceof String) {
            strArray = new String[] {(String)obj};
        } else if (obj instanceof String[]) {
            strArray = (String[])obj;
        } else if (obj instanceof RequestParameter) {
            strArray = new String[] {((RequestParameter)obj).getString()};
        } else if (obj instanceof RequestParameter[]) {
            RequestParameter[] values = (RequestParameter[])obj;
            strArray = new String[values.length];
            for (int i=0; i < values.length; i++) {
                strArray[i] = values[i].getString();
            }
        }

        return strArray == null ? new String[0] : strArray;
    }

    protected @NotNull RequestParameter[] convertToRequestParameterArray(Object obj) {
        RequestParameter [] paramArray = null;
        if (obj instanceof String) {
            paramArray = new RequestParameter[] {
                    Builders.newRequestParameter(null, (String)obj)
            };
        } else if (obj instanceof String[]) {
            String [] strValues = (String[])obj;
            paramArray = new RequestParameter[strValues.length];
            for (int i=0; i < strValues.length; i++) {
                paramArray[i] = Builders.newRequestParameter(null, strValues[i]);
            }
        } else if (obj instanceof RequestParameter) {
            paramArray = new RequestParameter[] {(RequestParameter)obj};
        } else if (obj instanceof RequestParameter[]) {
            paramArray = (RequestParameter[])obj;
        }

        return paramArray == null ? new RequestParameter[0] : paramArray;
    }

}
