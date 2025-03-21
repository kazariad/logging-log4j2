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
package org.apache.logging.log4j.core.appender.rolling;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.test.junit.LoggerContextSource;
import org.apache.logging.log4j.test.junit.CleanUpDirectories;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * LOG4J2-2602.
 */
@Tag("sleepy")
public class RollingAppenderSizeWithTimeTest {

    private static final String CONFIG = "log4j-rolling-size-with-time.xml";

    private static final String DIR = "target/rolling-size-test";

    @Test
    @CleanUpDirectories(DIR)
    @LoggerContextSource(CONFIG)
    public void testAppender(final Logger logger, final LoggerContext context) throws Exception {
        final List<String> messages = new ArrayList<>();
        for (int i = 0; i < 5000; ++i) {
            final String message = "This is test message number " + i;
            messages.add(message);
            logger.debug(message);
            if (i % 100 == 0) {
                Thread.sleep(10);
            }
        }
        assertTrue(context.stop(30, TimeUnit.SECONDS), () -> "Could not stop cleanly " + context + " for " + this);
        final File dir = new File(DIR);
        assertTrue(dir.exists(), "Directory not created");
        final File[] files = dir.listFiles();
        assertNotNull(files);
        for (final File file : files) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (final FileInputStream fis = new FileInputStream(file)) {
                try {
                    IOUtils.copy(fis, baos);
                } catch (final Exception ex) {
                    fail("Unable to read " + file.getAbsolutePath(), ex);
                }
            }
            final String text = baos.toString(Charset.defaultCharset());
            final String[] lines = text.split("[\\r\\n]+");
            for (final String line : lines) {
                messages.remove(line);
            }
        }
        assertTrue(messages.isEmpty(), "Log messages lost : " + messages.size());
        assertTrue(files.length > 2, "Files not rolled : " + files.length);
    }
}
