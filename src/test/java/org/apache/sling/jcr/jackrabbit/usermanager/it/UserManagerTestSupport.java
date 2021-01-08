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

import static org.apache.sling.testing.paxexam.SlingOptions.awaitility;
import static org.apache.sling.testing.paxexam.SlingOptions.sling;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

import org.apache.sling.testing.paxexam.SlingOptions;
import org.apache.sling.testing.paxexam.TestSupport;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Base class for UserManager related paxexam tests
 */
public abstract class UserManagerTestSupport extends TestSupport {

    /**
     * Use after using ConfigurationAdmin to change the configuration of
     * a service and you need to wait for the component to be re-activated
     * with the new configuration.
     */
    public static final class WaitForServiceUpdated extends Retry {
        private BundleContext bundleContext;
        private String expectedKey;
        private Object expectedValue;
        private Class<?> serviceClass;

        public WaitForServiceUpdated(long timeoutMsec, long nextIterationDelay, BundleContext bundleContext, 
                Class<?> serviceClass, String expectedKey, Object expectedValue) {
            super(timeoutMsec, nextIterationDelay, false);
            this.bundleContext = bundleContext;
            this.serviceClass = serviceClass;
            this.expectedKey = expectedKey;
            this.expectedValue = expectedValue;
            run();
        }

        @Override
        protected boolean exec() {
            ServiceReference<?> serviceReference = bundleContext.getServiceReference(serviceClass);
            assertNotNull(serviceReference);
            assertEquals(expectedValue, serviceReference.getProperty(expectedKey));
            return true;
        }
    }


    @Override
    public ModifiableCompositeOption baseConfiguration() {
        final Option usermanager = mavenBundle()
                .groupId("org.apache.sling")
                .artifactId("org.apache.sling.jcr.jackrabbit.usermanager")
                .version(SlingOptions.versionResolver.getVersion("org.apache.sling", "org.apache.sling.jcr.jackrabbit.usermanager"));
        return composite(
            super.baseConfiguration(),
            optionalRemoteDebug(),
            quickstart(),
            sling(),
            // SLING-9809 - add server user for the o.a.s.jcr.jackrabbit.usermanager bundle
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                .put("scripts", new String[] {
                        "create service user sling-jcr-usermanager with path system/sling\n" +
                        "\n" +
                        "set ACL for sling-jcr-usermanager\n" +
                        "    allow jcr:read,jcr:readAccessControl,jcr:modifyAccessControl,rep:write,rep:userManagement on /home\n" +
                        "end"})
                .asOption(),
            factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                .put("user.mapping", new String[]{"org.apache.sling.jcr.jackrabbit.usermanager=sling-jcr-usermanager"})
                .asOption(),
            
            // Sling JCR UserManager
            testBundle("bundle.filename"),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.jcr.jackrabbit.accessmanager").versionAsInProject(),
            junitBundles(),
            awaitility()
        ).remove(
            usermanager
        );
    }

    /**
     * Optionally configure remote debugging on the port supplied by the "debugPort"
     * system property.
     */
    protected ModifiableCompositeOption optionalRemoteDebug() {
        VMOption option = null;
        String property = System.getProperty("debugPort");
        if (property != null) {
            option = vmOption(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%s", property));
        }
        return composite(option);
    }
    protected ModifiableCompositeOption quickstart() {
        final int httpPort = findFreePort();
        final String workingDirectory = workingDirectory();
        return slingQuickstartOakTar(workingDirectory, httpPort);
    }

    protected Dictionary<String, Object> replaceConfigProp(Dictionary<String, Object> originalProps, String newPropKey, Object newPropValue) {
        Hashtable<String, Object> newProps = new Hashtable<>();
        if (originalProps != null) {
            Enumeration<String> keys = originalProps.keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                Object value = originalProps.get(key);
                newProps.put(key, value);
            }
        }

        newProps.put(newPropKey, newPropValue);
        
        return newProps;
    }
    
}
