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
package org.apache.logging.log4j.gctests;

import static java.lang.System.getProperty;
import static org.apache.logging.log4j.core.test.TestConstants.CONFIGURATION_FILE;
import static org.apache.logging.log4j.core.test.TestConstants.GC_ENABLE_DIRECT_ENCODERS;
import static org.apache.logging.log4j.core.test.TestConstants.WEB_IS_WEB_APP;
import static org.apache.logging.log4j.core.test.TestConstants.setSystemProperty;
import static org.apache.logging.log4j.core.util.Constants.ENABLE_DIRECT_ENCODERS;
import static org.apache.logging.log4j.util.Constants.IS_WEB_APP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;
import com.google.monitoring.runtime.instrumentation.Sampler;
import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.impl.CoreProperties.ConfigurationProperties;
import org.apache.logging.log4j.core.test.TestConstants;
import org.apache.logging.log4j.kit.env.PropertyEnvironment;
import org.apache.logging.log4j.message.StringMapMessage;

/**
 * Utility methods for the GC-free logging tests.
 */
enum GcFreeLoggingTestUtil {
    ;

    static void executeLogging(final String configurationFile, final Class<?> testClass) throws Exception {

        setSystemProperty(GC_ENABLE_DIRECT_ENCODERS, "true");
        setSystemProperty(WEB_IS_WEB_APP, "false");
        setSystemProperty(CONFIGURATION_FILE, configurationFile);

        assertTrue(ENABLE_DIRECT_ENCODERS, "Constants.ENABLE_DIRECT_ENCODERS");
        assertFalse(IS_WEB_APP, "Constants.isWebApp()");

        final MyCharSeq myCharSeq = new MyCharSeq();
        final Marker testGrandParent = MarkerManager.getMarker("testGrandParent");
        final Marker testParent = MarkerManager.getMarker("testParent").setParents(testGrandParent);
        final Marker test = MarkerManager.getMarker("test").setParents(testParent); // initial creation, value is cached
        final StringMapMessage mapMessage = new StringMapMessage().with("eventId", "Login");

        // initialize LoggerContext etc.
        // This is not steady-state logging and will allocate objects.
        ThreadContext.put("aKey", "value1");
        ThreadContext.put("key2", "value2");

        final org.apache.logging.log4j.Logger logger = LogManager.getLogger(testClass.getName());
        logger.debug("debug not set");
        logger.fatal(test, "This message is logged to the console");
        logger.error("Sample error message");
        logger.error("Test parameterized message {}", "param");
        logger.error(new StringMapMessage().with("eventId", "Login")); // initialize GelfLayout's messageStringBuilder
        singleLoggingIteration(logger, myCharSeq, mapMessage);
        for (int i = 0; i < 256; i++) {
            logger.debug("ensure all ringbuffer slots have been used once"); // allocate MutableLogEvent.messageText
        }
        ThreadContext.remove("aKey");
        ThreadContext.remove("key2");

        // BlockingWaitStrategy uses ReentrantLock which allocates Node objects. Ignore this.
        final String[] exclude = new String[] {
            "java/util/concurrent/locks/AbstractQueuedSynchronizer$Node", //
            "com/google/monitoring/runtime/instrumentation/Sampler"
        };
        final AtomicBoolean samplingEnabled = new AtomicBoolean(true);
        final Sampler sampler = (count, desc, newObj, size) -> {
            if (!samplingEnabled.get()) {
                return;
            }
            for (int i = 0; i < exclude.length; i++) {
                if (exclude[i].equals(desc)) {
                    return; // exclude
                }
            }
            System.err.println("I just allocated the object " + newObj + " of type " + desc + " whose size is " + size);
            if (count != -1) {
                System.err.println("It's an array of size " + count);
            }

            // show a stack trace to see which line caused allocation
            new RuntimeException().printStackTrace();
        };
        Thread.sleep(500);
        AllocationRecorder.addSampler(sampler);

        // now do some steady-state logging

        ThreadContext.put("aKey", "value1");
        ThreadContext.put("key2", "value2");

        final int ITERATIONS = 5;
        for (int i = 0; i < ITERATIONS; i++) {
            singleLoggingIteration(logger, myCharSeq, mapMessage);
            ThreadContext.remove("aKey");
            ThreadContext.put("aKey", "value1");
        }
        Thread.sleep(50);
        samplingEnabled.set(false); // reliably ignore all allocations from now on
        AllocationRecorder.removeSampler(sampler);
        Thread.sleep(100);
    }

    private static void singleLoggingIteration(
            final org.apache.logging.log4j.Logger logger,
            final MyCharSeq myCharSeq,
            final StringMapMessage mapMessage) {
        logger.isEnabled(Level.TRACE);
        logger.isEnabled(Level.TRACE, MarkerManager.getMarker("test"));
        logger.isTraceEnabled();
        logger.isTraceEnabled(MarkerManager.getMarker("test"));
        logger.trace(myCharSeq);
        logger.trace(MarkerManager.getMarker("test"), myCharSeq);
        logger.trace("Test message");
        logger.trace("Test parameterized message {}", "param");
        logger.trace("Test parameterized message {}{}", "param", "param2");
        logger.trace("Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.trace(MarkerManager.getMarker("test"), "Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.trace(mapMessage); // LOG4J2-1683

        logger.isEnabled(Level.DEBUG);
        logger.isEnabled(Level.DEBUG, MarkerManager.getMarker("test"));
        logger.isDebugEnabled();
        logger.isDebugEnabled(MarkerManager.getMarker("test"));
        logger.debug(myCharSeq);
        logger.debug(MarkerManager.getMarker("test"), myCharSeq);
        logger.debug("Test message");
        logger.debug("Test parameterized message {}", "param");
        logger.debug("Test parameterized message {}{}", "param", "param2");
        logger.debug("Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.debug(MarkerManager.getMarker("test"), "Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.debug(mapMessage); // LOG4J2-1683

        logger.isEnabled(Level.INFO);
        logger.isEnabled(Level.INFO, MarkerManager.getMarker("test"));
        logger.isInfoEnabled();
        logger.isInfoEnabled(MarkerManager.getMarker("test"));
        logger.info(myCharSeq);
        logger.info(MarkerManager.getMarker("test"), myCharSeq);
        logger.info("Test message");
        logger.info("Test parameterized message {}", "param");
        logger.info("Test parameterized message {}{}", "param", "param2");
        logger.info("Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.info(MarkerManager.getMarker("test"), "Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.info(mapMessage); // LOG4J2-1683

        logger.isEnabled(Level.WARN);
        logger.isEnabled(Level.WARN, MarkerManager.getMarker("test"));
        logger.isWarnEnabled();
        logger.isWarnEnabled(MarkerManager.getMarker("test"));
        logger.warn(myCharSeq);
        logger.warn(MarkerManager.getMarker("test"), myCharSeq);
        logger.warn("Test message");
        logger.warn("Test parameterized message {}", "param");
        logger.warn("Test parameterized message {}{}", "param", "param2");
        logger.warn("Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.warn(MarkerManager.getMarker("test"), "Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.warn(mapMessage); // LOG4J2-1683

        logger.isEnabled(Level.ERROR);
        logger.isEnabled(Level.ERROR, MarkerManager.getMarker("test"));
        logger.isErrorEnabled();
        logger.isErrorEnabled(MarkerManager.getMarker("test"));
        logger.error(myCharSeq);
        logger.error(MarkerManager.getMarker("test"), myCharSeq);
        logger.error("Test message");
        logger.error("Test parameterized message {}", "param");
        logger.error("Test parameterized message {}{}", "param", "param2");
        logger.error("Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.error(MarkerManager.getMarker("test"), "Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.error(mapMessage); // LOG4J2-1683

        logger.isEnabled(Level.FATAL);
        logger.isEnabled(Level.FATAL, MarkerManager.getMarker("test"));
        logger.isFatalEnabled();
        logger.isFatalEnabled(MarkerManager.getMarker("test"));
        logger.fatal(myCharSeq);
        logger.fatal(MarkerManager.getMarker("test"), myCharSeq);
        logger.fatal("Test message");
        logger.fatal("Test parameterized message {}", "param");
        logger.fatal("Test parameterized message {}{}", "param", "param2");
        logger.fatal("Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.fatal(MarkerManager.getMarker("test"), "Test parameterized message {}{}{}", "param", "param2", "abc");
        logger.fatal(mapMessage); // LOG4J2-1683
    }

    public static void runTest(final Class<?> cls) throws Exception {
        final String javaHome = getProperty("java.home");
        final String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        final String classpath = getProperty("java.class.path");
        final String javaagent = "-javaagent:" + agentJar();
        final boolean usePreciseClock = PropertyEnvironment.getGlobal()
                .getProperty(ConfigurationProperties.class)
                .usePreciseClock();

        final File tempFile = File.createTempFile("allocations", ".txt");
        // tempFile.deleteOnExit();
        final List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add(javaagent);
        if (usePreciseClock) {
            command.add("-D" + TestConstants.CONFIGURATION_USE_PRECISE_CLOCK + "=" + usePreciseClock);
        }
        command.add("-cp");
        command.add(classpath);
        command.add(cls.getName());
        final ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.to(tempFile));
        builder.redirectOutput(ProcessBuilder.Redirect.to(tempFile));
        final Process process = builder.start();
        process.waitFor();
        process.exitValue();

        final AtomicInteger lineCounter = new AtomicInteger(0);
        try (final Stream<String> lines = Files.lines(tempFile.toPath(), Charset.defaultCharset())) {
            final Pattern pattern =
                    Pattern.compile(String.format("^FATAL .*\\.%s [main].*", Pattern.quote(cls.getSimpleName())));
            assertThat(lines.flatMap(l -> {
                        final int lineNumber = lineCounter.incrementAndGet();
                        final String line = l.trim();
                        return pattern.matcher(line).matches() ? Stream.of(lineNumber + ": " + line) : Stream.empty();
                    }))
                    .isEmpty();
        }
    }

    private static File agentJar() throws Exception {
        final String name = AllocationRecorder.class.getName();
        final URL url = AllocationRecorder.class.getResource(
                "/" + name.replace('.', '/').concat(".class"));
        if (url == null) {
            throw new IllegalStateException("Could not find url for " + name);
        }
        final String temp = url.toString();
        final URL jarurl = new URL(temp.substring("jar:".length(), temp.indexOf('!')));
        return new File(jarurl.toURI());
    }

    public static class MyCharSeq implements CharSequence {
        final String seq = GcFreeLoggingTestUtil.class.toString();

        @Override
        public int length() {
            return seq.length();
        }

        @Override
        public char charAt(final int index) {
            return seq.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return seq.subSequence(start, end);
        }

        @Override
        public String toString() {
            System.err.println("TEMP OBJECT CREATED!");
            throw new IllegalStateException("TEMP OBJECT CREATED!");
        }
    }
}
