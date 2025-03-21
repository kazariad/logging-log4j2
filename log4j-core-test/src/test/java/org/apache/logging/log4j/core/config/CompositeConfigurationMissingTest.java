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
package org.apache.logging.log4j.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.test.TestConstants;
import org.apache.logging.log4j.test.junit.SetTestProperty;
import org.junit.jupiter.api.Test;

@SetTestProperty(
        key = TestConstants.CONFIGURATION_FILE,
        value = "classpath:log4j-comp-logger-root.xml,log4j-does-not-exist.json")
public class CompositeConfigurationMissingTest {

    @Test
    public void testMissingConfig() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);

        final AbstractConfiguration config = (AbstractConfiguration) ctx.getConfiguration();
        assertNotNull(config, "No configuration returned");
        // Test for Root log level override
        assertEquals(Level.ERROR, config.getRootLogger().getLevel(), "Expected Root logger log level to be ERROR");

        // Test for no cat2 level override
        final LoggerConfig cat2 = config.getLogger("cat2");
        assertEquals(Level.DEBUG, cat2.getLevel(), "Expected cat2 log level to be INFO");
    }
}
