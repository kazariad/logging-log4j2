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
package org.apache.logging.log4j.csv.layout;

import org.apache.commons.csv.CSVFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.async.logger.AsyncLoggerContextSelector;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.test.TestConstants;
import org.apache.logging.log4j.core.test.categories.Layouts;
import org.jspecify.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Tests {@link AbstractCsvLayout} with all loggers async.
 *
 * @since 2.6
 */
@Category(Layouts.Csv.class)
public class CsvParameterLayoutAllAsyncTest {

    static @Nullable String oldSelector;

    @BeforeClass
    public static void beforeClass() {
        oldSelector = TestConstants.setSystemProperty(
                TestConstants.LOGGER_CONTEXT_SELECTOR, AsyncLoggerContextSelector.class.getName());
        System.setProperty(TestConstants.CONFIGURATION_FILE, "AsyncLoggerTest.xml");
    }

    @AfterClass
    public static void afterClass() {
        TestConstants.setSystemProperty(TestConstants.LOGGER_CONTEXT_SELECTOR, oldSelector);
    }

    @Test
    public void testLayoutDefaultNormal() throws Exception {
        final Logger root = (Logger) LogManager.getRootLogger();
        CsvParameterLayoutTest.testLayoutNormalApi(root, CsvParameterLayout.createDefaultLayout(), false);
    }

    @Test
    public void testLayoutDefaultObjectArrayMessage() throws Exception {
        final Logger root = (Logger) LogManager.getRootLogger();
        CsvParameterLayoutTest.testLayoutNormalApi(root, CsvParameterLayout.createDefaultLayout(), true);
    }

    @Test
    public void testLayoutTab() throws Exception {
        final Logger root = (Logger) LogManager.getRootLogger();
        CsvParameterLayoutTest.testLayoutNormalApi(root, CsvParameterLayout.createLayout(CSVFormat.TDF), true);
    }
}
