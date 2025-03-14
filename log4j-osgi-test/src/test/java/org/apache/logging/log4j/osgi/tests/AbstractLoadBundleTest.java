/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
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
package org.apache.logging.log4j.osgi.tests;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * Tests a basic Log4J 'setup' in an OSGi container.
 */
abstract class AbstractLoadBundleTest {

    private BundleContext bundleContext;

    @Rule
    public final OsgiRule osgi;

    AbstractLoadBundleTest(final FrameworkFactory frameworkFactory) {
        this.osgi = new OsgiRule(frameworkFactory);
    }

    @Before
    public void before() {
        bundleContext = osgi.getFramework().getBundleContext();
    }

    private Bundle installBundle(final String symbolicName) throws BundleException {
        // The links are generated by 'exam-maven-plugin'
        final String url = String.format("link:classpath:%s.link", symbolicName);
        return bundleContext.installBundle(url);
    }

    private List<Bundle> startApacheSpiFly() throws BundleException {
        final List<Bundle> bundles = List.of(
                installBundle("org.apache.aries.spifly.dynamic.bundle"),
                installBundle("org.objectweb.asm"),
                installBundle("org.objectweb.asm.commons"),
                installBundle("org.objectweb.asm.tree"),
                installBundle("org.objectweb.asm.tree.analysis"),
                installBundle("org.objectweb.asm.util"));
        bundles.get(0).start();
        return bundles;
    }

    private void uninstall(final List<Bundle> bundles) throws BundleException {
        for (Bundle bundle : bundles) {
            bundle.uninstall();
        }
    }

    private Bundle getApiBundle() throws BundleException {
        return installBundle("org.apache.logging.log4j.api");
    }

    private Bundle getPluginsBundle() throws BundleException {
        return installBundle("org.apache.logging.log4j.plugins");
    }

    private Bundle getKitBundle() throws BundleException {
        return installBundle("org.apache.logging.log4j.kit");
    }

    private Bundle getCoreBundle() throws BundleException {
        return installBundle("org.apache.logging.log4j.core");
    }

    private Bundle getApiTestsBundle() throws BundleException {
        return installBundle("org.apache.logging.log4j.api.test");
    }

    /**
     * Tests starting, then stopping, then restarting, then stopping, and finally uninstalling the API and Core bundles
     */
    @Test
    public void testApiCoreStartStopStartStop() throws BundleException {

        final List<Bundle> spiFly = startApacheSpiFly();
        final Bundle api = getApiBundle();
        final Bundle plugins = getPluginsBundle();
        final Bundle kit = getKitBundle();
        final Bundle core = getCoreBundle();

        assertEquals("api is not in INSTALLED state", Bundle.INSTALLED, api.getState());
        assertEquals("plugins is not in INSTALLED state", Bundle.INSTALLED, plugins.getState());
        assertEquals("kit is not in INSTALLED state", Bundle.INSTALLED, kit.getState());
        assertEquals("core is not in INSTALLED state", Bundle.INSTALLED, core.getState());

        // 1st start-stop
        doOnBundlesAndVerifyState(Bundle::start, Bundle.ACTIVE, api, kit, plugins, core);
        doOnBundlesAndVerifyState(Bundle::stop, Bundle.RESOLVED, core, plugins, kit, api);

        // 2nd start-stop
        doOnBundlesAndVerifyState(Bundle::start, Bundle.ACTIVE, api, kit, plugins, core);
        doOnBundlesAndVerifyState(Bundle::stop, Bundle.RESOLVED, core, plugins, kit, api);

        doOnBundlesAndVerifyState(Bundle::uninstall, Bundle.UNINSTALLED, core, plugins, kit, api);
        uninstall(spiFly);
    }

    /**
     * Tests LOG4J2-1637.
     */
    @Test
    public void testClassNotFoundErrorLogger() throws BundleException {

        final List<Bundle> spiFly = startApacheSpiFly();
        final Bundle api = getApiBundle();
        final Bundle plugins = getPluginsBundle();
        final Bundle kit = getKitBundle();
        final Bundle core = getCoreBundle();

        doOnBundlesAndVerifyState(Bundle::start, Bundle.ACTIVE, api, kit, plugins);
        // fails if LOG4J2-1637 is not fixed
        try {
            core.start();
        } catch (final BundleException error0) {
            boolean log4jClassNotFound = false;
            final Throwable error1 = error0.getCause();
            if (error1 != null) {
                final Throwable error2 = error1.getCause();
                if (error2 != null) {
                    log4jClassNotFound = error2.toString()
                            .startsWith("java.lang.ClassNotFoundException: org.apache.logging.log4j.Logger");
                }
            }
            if (!log4jClassNotFound) {
                throw error0;
            }
        }
        assertEquals(String.format("`%s` bundle state mismatch", core), Bundle.ACTIVE, core.getState());

        doOnBundlesAndVerifyState(Bundle::stop, Bundle.RESOLVED, core, plugins, kit, api);
        doOnBundlesAndVerifyState(Bundle::uninstall, Bundle.UNINSTALLED, core, plugins, kit, api);
        uninstall(spiFly);
    }

    private static void doOnBundlesAndVerifyState(
            final ThrowingConsumer<Bundle> operation, final int expectedState, final Bundle... bundles) {
        for (final Bundle bundle : bundles) {
            try {
                operation.accept(bundle);
            } catch (final Throwable error) {
                final String message = String.format("operation failure for bundle `%s`", bundle);
                throw new RuntimeException(message, error);
            }
            assertEquals(String.format("`%s` bundle state mismatch", bundle), expectedState, bundle.getState());
        }
    }
}
