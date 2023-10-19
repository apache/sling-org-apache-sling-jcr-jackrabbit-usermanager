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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.jackrabbit.oak.spi.security.user.AuthorizableType;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameFilter;
import org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Default implementation that generates a principal name based on a set of 
 * well-known request parameters
 * 
 * <p>
 * The value is resolved by the locating the first request parameter that is a 
 * match of one of the choices in the following order:
 * </p>
 * <ol>
 * <li>":name" - value is the exact name to use</li>
 * <li>":name@ValueFrom" - value is the name of another submitted parameter whose value is the exact name to use</li>
 * <li>":nameHint" - value is filtered, trimmed and made unique</li>
 * <li>":nameHint@ValueFrom" - value is the name of another submitted parameter whose value is filtered, trimmed and made unique</li>
 * <li>otherwise, try the value of any configured "principalNameHints" parameters to treat as a hint that is filtered, trimmed and made unique</li>
 * </ol>
 */
@Component(
        configurationPid = "org.apache.sling.jackrabbit.usermanager.PrincipalNameGenerator",
        service = {PrincipalNameGenerator.class})
@Designate(ocd = PrincipalNameGeneratorImpl.Config.class)
public class PrincipalNameGeneratorImpl implements PrincipalNameGenerator {

    @ObjectClassDefinition(name = "Apache Sling Principal Name Generator",
            description = "The Sling helper to generate a principal name from a hint")
    public @interface Config {

        @AttributeDefinition(name = "Maximum Principal Name Length",
                description = "Maximum number of characters to "+
                 "use for automatically generated principal names. The default value is 20. Note, "+
                 "that actual principal names may be generated with at most 4 more characters if "+
                 "numeric suffixes must be appended to make the name unique.")
        int principalNameMaxLength() default DEFAULT_MAX_NAME_LENGTH;

        @AttributeDefinition(name = "Principal Name Hint Properties",
                description = "The list of properties whose values "+
                 "may be used to derive a name for newly created principal. When handling a request "+
                 "to create a new principal, the name is automatically generated from this set if "+
                 "no \":name\" or \":nameHint\" property is provided. In this case the request "+
                 "parameters listed in this configuration value may be used as a hint to create the name.")
        String[] principalNameHints();

    }

    private String[] parameterNames;

    public static final int DEFAULT_MAX_NAME_LENGTH = 20;

    private int maxLength = DEFAULT_MAX_NAME_LENGTH;

    public PrincipalNameGeneratorImpl() {
        this(null, -1);
    }

    public PrincipalNameGeneratorImpl(String[] parameterNames, int maxNameLength) {
        if (parameterNames == null) {
            this.parameterNames = new String[0];
        } else {
            this.parameterNames = parameterNames;
        }

        this.maxLength = (maxNameLength > 0)
                ? maxNameLength
                : DEFAULT_MAX_NAME_LENGTH;
    }

    @Activate
    protected void activate(Config config) {
        this.maxLength = config.principalNameMaxLength();
        this.parameterNames = config.principalNameHints();
    }

    /**
     * Convert the value to a list of strings
     */
    protected @NotNull List<String> valueToList(Object value) {
        final List<String> valuesList;
        if (value instanceof String[]) {
            valuesList = Arrays.asList((String[])value);
        } else if (value instanceof String) {
            valuesList = Collections.singletonList((String)value);
        } else if (value instanceof RequestParameter[]) {
            valuesList = new ArrayList<>();
            for (RequestParameter rp : (RequestParameter[])value) {
                valuesList.add(rp.getString());
            }
        } else {
            valuesList = Collections.emptyList();
        }
        return valuesList;
    }

    /**
     * Determine the value to use for the specified parameter. This also
     * considers the parameter with a {@link SlingPostConstants#VALUE_FROM_SUFFIX}
     *
     * @param parameters the map of request parameters
     * @param paramName the parameter to get the value for
     * @return the value to use for the parameter or null if it could not be determined
     */
    protected String getValueToUse(Map<String, ?> parameters, String paramName) {
        String valueToUse = null;
        List<String> values = valueToList(parameters.get(paramName));
        if (!values.isEmpty()) {
            for (String specialParam : values) {
                if (specialParam != null && !specialParam.isEmpty()) {
                    valueToUse = specialParam;
                }

                if (valueToUse != null) {
                    if (valueToUse.isEmpty()) {
                        // empty value is not usable
                        valueToUse = null;
                    } else {
                        // found value, so stop looping
                        break;
                    }
                }
            }
        } else {
            // check for a paramName@ValueFrom param
            // SLING-130: VALUE_FROM_SUFFIX means take the value of this
            // property from a different field
            values = valueToList(parameters.get(String.format("%s%s", paramName, SlingPostConstants.VALUE_FROM_SUFFIX)));
            if (!values.isEmpty()) {
                for (String specialParam : values) {
                    if (specialParam != null && !specialParam.isEmpty()) {
                        // retrieve the reference parameter value
                        List<String> refValues = valueToList(parameters.get(specialParam));
                        // @ValueFrom params must have exactly one value, else ignored
                        if (refValues.size() == 1) {
                            specialParam = refValues.get(0);
                            if (specialParam != null && !specialParam.isEmpty()) {
                                valueToUse = specialParam;
                            }
                        }
                    }

                    if (valueToUse != null) {
                        if (valueToUse.isEmpty()) {
                            // empty value is not usable
                            valueToUse = null;
                        } else {
                            // found value, so stop looping
                            break;
                        }
                    }
                }
            }
        }
        return valueToUse;
    }

    /**
     * Get a "nice" principal name, if possible, based on given request
     *
     * @param parameters the properties to consider when generating a name
     * @param type the type of principal
     * @param principalNameFilter the filter to make a value work as a principal name
     * @param defaultPrincipalNameGenerator the default principal name generator
     * @return the principal name to be created or null if other PrincipalNameGenerators should be consulted
     */
    @Override
    public NameInfo getPrincipalName(Map<String, ?> parameters, AuthorizableType type,
            PrincipalNameFilter principalNameFilter, PrincipalNameGenerator defaultPrincipalNameGenerator) {
        String valueToUse = null;
        boolean doFilter = true;

        // find the first request parameter that matches one of
        // our parameterNames, in order, and has a value
        // we first check for the special sling parameters
        valueToUse = getValueToUse(parameters, SlingPostConstants.RP_NODE_NAME);
        if (valueToUse != null) {
            doFilter = false;
        }
        if ( valueToUse == null ) {
            valueToUse = getValueToUse(parameters, SlingPostConstants.RP_NODE_NAME_HINT);

            if (valueToUse == null && parameterNames != null) {
                for (String param : parameterNames) {
                    valueToUse = getValueToUse(parameters, param);
                    if (valueToUse != null) {
                        break;
                    }
                }
            }
        }

        String result = valueToUse;
        // should we filter?
        if (doFilter && result != null && principalNameFilter != null) {
            // filter value so that it works as a principal name
            result = principalNameFilter.filter(result);
        }

        // max length
        if (doFilter && result != null && result.length() > maxLength) {
            result = result.substring(0, maxLength);
        }

        if (result != null) {
            return new NameInfo(result, doFilter);
        } else {
            return null;
        }
    }

}
