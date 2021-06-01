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

import static org.apache.felix.hc.api.FormattingResultLog.msHumanReadable;
import static org.apache.sling.testing.paxexam.SlingOptions.awaitility;
import static org.apache.sling.testing.paxexam.SlingOptions.sling;
import static org.apache.sling.testing.paxexam.SlingOptions.slingCommonsCompiler;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;
import static org.ops4j.pax.tinybundles.core.TinyBundles.withBnd;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.ResultLog;
import org.apache.felix.hc.api.execution.HealthCheckExecutionResult;
import org.apache.felix.hc.api.execution.HealthCheckExecutor;
import org.apache.felix.hc.api.execution.HealthCheckSelector;
import org.apache.sling.testing.paxexam.TestSupport;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for UserManager related paxexam tests
 */
public abstract class UserManagerTestSupport extends TestSupport {
    private static final String BUNDLE_SYMBOLICNAME = "TEST-CONTENT-BUNDLE";
    private static final String SLING_BUNDLE_RESOURCES_HEADER = "Sling-Bundle-Resources";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    private HealthCheckExecutor hcExecutor;

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

    @Configuration
    public Option[] configuration() {
        final String vmOpt = System.getProperty("pax.vm.options");
        VMOption vmOption = null;
        if (vmOpt != null && !vmOpt.isEmpty()) {
            vmOption = new VMOption(vmOpt);
        }

        final String jacocoOpt = System.getProperty("jacoco.command");
        VMOption jacocoCommand = null;
        if (jacocoOpt != null && !jacocoOpt.isEmpty()) {
            jacocoCommand = new VMOption(jacocoOpt);
        }

        // newer version of sling.api and dependencies for SLING-10034
        //   may remove at a later date if the superclass includes these versions or later
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.api");
        versionResolver.setVersion("org.apache.sling", "org.apache.sling.resourceresolver", "1.7.0"); // to be compatible with current o.a.sling.api
        versionResolver.setVersion("org.apache.sling", "org.apache.sling.scripting.core", "2.3.4"); // to be compatible with current o.a.sling.api
        versionResolver.setVersion("org.apache.sling", "org.apache.sling.scripting.api", "2.2.0"); // to be compatible with current o.a.sling.api
        versionResolver.setVersion("org.apache.sling", "org.apache.sling.servlets.resolver", "2.7.12"); // to be compatible with current o.a.sling.api
        versionResolver.setVersion("org.apache.sling", "org.apache.sling.commons.compiler", "2.4.0"); // to be compatible with current o.a.sling.scripting.core

        return composite(
            super.baseConfiguration(),
            when(vmOption != null).useOptions(vmOption),
            when(jacocoCommand != null).useOptions(jacocoCommand),
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
        ).add(
            // needed by latest version of org.apache.sling.api
            mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.converter").version("1.0.14"),
            // needed by latest version of org.apache.sling.scripting.core
            slingCommonsCompiler()
        ).add(
            additionalOptions()
        ).remove(
            mavenBundle() .groupId("org.apache.sling").artifactId("org.apache.sling.jcr.jackrabbit.usermanager").version(versionResolver)
        ).getOptions();
    }

    protected Option[] additionalOptions() {
        return new Option[]{};
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
        Hashtable<String, Object> newProps = new Hashtable<>(); // NOSONAR
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

    /**
     * Wait for the health check to be ok
     *
     * @param timeoutMsec the max time to wait for the health check to be ok
     * @param nextIterationDelay the sleep time between the check attempts
     */
    protected void waitForServerReady(long timeoutMsec, long nextIterationDelay) {
        // retry until the exec call returns true and doesn't throw any exception
        await().atMost(timeoutMsec, TimeUnit.MILLISECONDS)
                .pollInterval(nextIterationDelay, TimeUnit.MILLISECONDS)
                .until(this::doHealthCheck);
    }

    /**
     * @return true if health checks are ok
     */
    protected boolean doHealthCheck() throws IOException {
        boolean isOk = true;
        logger.info("Performing health check");
        HealthCheckSelector hcs = HealthCheckSelector.tags("systemalive");
        List<HealthCheckExecutionResult> results = hcExecutor.execute(hcs);
        logger.info("systemalive health check got {} results", results.size());
        isOk &= !results.isEmpty();
        for (final HealthCheckExecutionResult exR : results) {
            final Result r = exR.getHealthCheckResult();
            if (logger.isInfoEnabled()) {
                logger.info("systemalive health check: {}", toHealthCheckResultInfo(exR, false));
            }
            isOk &= r.isOk();
            if (!isOk) {
                break; // found a failure so stop checking further
            }
        }

        if (isOk) {
            hcs = HealthCheckSelector.tags("bundles");
            results = hcExecutor.execute(hcs);
            logger.info("bundles health check got {} results", results.size());
            isOk &= !results.isEmpty();
            for (final HealthCheckExecutionResult exR : results) {
                final Result r = exR.getHealthCheckResult();
                if (logger.isInfoEnabled()) {
                    logger.info("bundles health check: {}", toHealthCheckResultInfo(exR, false));
                }
                isOk &= r.isOk();
                if (!isOk) {
                    break; // found a failure so stop checking further
                }
            }
        }
        return isOk;
    }

    /**
     * Produce a human readable report of the health check results that is suitable for
     * debugging or writing to a log
     */
    protected String toHealthCheckResultInfo(final HealthCheckExecutionResult exResult, final boolean debug)  throws IOException {
        String value = null;
        try (StringWriter resultWriter = new StringWriter(); BufferedWriter writer = new BufferedWriter(resultWriter)) {
            final Result result = exResult.getHealthCheckResult();

            writer.append('"').append(exResult.getHealthCheckMetadata().getTitle()).append('"');
            writer.append(" result is: ").append(result.getStatus().toString());
            writer.newLine();
            writer.append("   Finished: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(exResult.getFinishedAt()) + " after "
                    + msHumanReadable(exResult.getElapsedTimeInMs()));

            for (final ResultLog.Entry e : result) {
                if (!debug && e.isDebug()) {
                    continue;
                }
                writer.newLine();
                writer.append("   ");
                writer.append(e.getStatus().toString());
                writer.append(' ');
                writer.append(e.getMessage());
                if (e.getException() != null) {
                    writer.append(" ");
                    writer.append(e.getException().toString());
                }
            }
            writer.flush();
            value = resultWriter.toString();
        }
        return value;
    }

    /**
     * Add content to our test bundle
     */
    protected void addContent(final TinyBundle bundle, String resourcePath) throws IOException {
        String pathInBundle = resourcePath;
        resourcePath = "/content" + resourcePath;
        try (final InputStream is = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull("Expecting resource to be found:" + resourcePath, is);
            logger.info("Adding resource to bundle, path={}, resource={}", pathInBundle, resourcePath);
            bundle.add(pathInBundle, is);
        }
    }

    /**
     * Override to provide the option for your test
     *
     * @return the tinybundle Option or null if none
     */
    protected Option buildBundleResourcesBundle() {
        return null;
    }

    /**
     * Build a test bundle containing the specified bundle resources
     *
     * @param header the value for the {@link #SLING_BUNDLE_RESOURCES_HEADER} header
     * @param content the collection of files to embed in the tinybundle
     * @return the tinybundle Option
     */
    protected Option buildBundleResourcesBundle(final String header, final Collection<String> content) {
        final TinyBundle bundle = TinyBundles.bundle();
        bundle.set(Constants.BUNDLE_SYMBOLICNAME, BUNDLE_SYMBOLICNAME);
        bundle.set(SLING_BUNDLE_RESOURCES_HEADER, header);
        bundle.set("Require-Capability", "osgi.extender;filter:=\"(&(osgi.extender=org.apache.sling.bundleresource)(version<=1.1.0)(!(version>=2.0.0)))\"");

        for (final String entry : content) {
            try {
                addContent(bundle, entry);
            } catch (IOException e) {
                fail(String.format("Failed to add content to the bundle: %s.  Reason: %s", entry, e.getMessage()));
            }
        }
        return streamBundle(
            bundle.build(withBnd())
        ).start();
    }

}
