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
package org.apache.logging.log4j.async.logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.test.junit.LoggerContextSource;
import org.apache.logging.log4j.test.junit.TempLoggingDir;
import org.apache.logging.log4j.test.junit.UsingStatusListener;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("async")
@UsingStatusListener
public class AsyncLoggerConfig2Test {

    @TempLoggingDir
    private static Path loggingPath;

    @Test
    @LoggerContextSource
    public void testConsecutiveReconfigure(final LoggerContext ctx) throws Exception {
        final File file = loggingPath.resolve("AsyncLoggerConfigTest2.log").toFile();

        final Logger log = ctx.getLogger("com.foo.Bar");
        final String msg = "Message before reconfig";
        log.info(msg);

        ctx.reconfigure();
        ctx.reconfigure();

        final String msg2 = "Message after reconfig";
        log.info(msg2);
        ctx.stop(); // stop async thread

        final BufferedReader reader = new BufferedReader(new FileReader(file));
        final String line1 = reader.readLine();
        final String line2 = reader.readLine();
        reader.close();
        file.delete();
        assertNotNull(line1, "line1");
        assertNotNull(line2, "line2");
        assertTrue(line1.contains(msg), "line1 " + line1);
        assertTrue(line2.contains(msg2), "line2 " + line2);
    }
}
