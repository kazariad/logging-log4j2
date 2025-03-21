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
package org.apache.logging.log4j.core.lookup;

import static org.apache.logging.log4j.core.lookup.StrSubstitutorTest.LOOKUP_PLUGINS;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.StringMapMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class InterpolatorTest {

    private static final String TESTKEY = "TestKey";
    private static final String TESTKEY2 = "TestKey2";
    private static final String TESTVAL = "TestValue";

    private static final String TEST_CONTEXT_RESOURCE_NAME = "logging/context-name";
    private static final String TEST_CONTEXT_NAME = "app-1";

    @BeforeAll
    public static void beforeClass() {
        System.setProperty(TESTKEY, TESTVAL);
        System.setProperty(TESTKEY2, TESTVAL);
    }

    @AfterAll
    public static void afterClass() {
        System.clearProperty(TESTKEY);
        System.clearProperty(TESTKEY2);
    }

    @Test
    public void testGetDefaultLookup() {
        final Map<String, String> map = new HashMap<>();
        map.put(TESTKEY, TESTVAL);
        final MapLookup defaultLookup = new MapLookup(map);
        final Interpolator interpolator = new Interpolator(defaultLookup, LOOKUP_PLUGINS);
        assertEquals(defaultLookup.getMap(), ((MapLookup) interpolator.getDefaultLookup()).getMap());
        assertSame(defaultLookup, interpolator.getDefaultLookup());
    }

    @Test
    public void testLookup() {
        final Map<String, String> map = new HashMap<>();
        map.put(TESTKEY, TESTVAL);
        final StrLookup lookup = new Interpolator(new MapLookup(map), LOOKUP_PLUGINS);
        ThreadContext.put(TESTKEY, TESTVAL);
        String value = lookup.lookup(TESTKEY);
        assertEquals(TESTVAL, value);
        value = lookup.lookup("ctx:" + TESTKEY);
        assertEquals(TESTVAL, value);
        value = lookup.lookup("sys:" + TESTKEY);
        assertEquals(TESTVAL, value);
        value = lookup.lookup("SYS:" + TESTKEY2);
        assertEquals(TESTVAL, value);
        value = lookup.lookup("BadKey");
        assertNull(value);
        ThreadContext.clearMap();
        value = lookup.lookup("ctx:" + TESTKEY);
        assertEquals(TESTVAL, value);
    }

    private void assertLookupNotEmpty(final StrLookup lookup, final String key) {
        final String value = lookup.lookup(key);
        assertNotNull(value);
        assertFalse(value.isEmpty());
        System.out.println(key + " = " + value);
    }

    @Test
    public void testLookupWithDefaultInterpolator() {
        final StrLookup lookup = new Interpolator(new PropertiesLookup(Map.of()), LOOKUP_PLUGINS);
        String value = lookup.lookup("sys:" + TESTKEY);
        assertEquals(TESTVAL, value);
        value = lookup.lookup("env:PATH");
        assertNotNull(value);
        value = lookup.lookup("date:yyyy-MM-dd");
        assertNotNull("No Date", value);
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        final String today = format.format(new Date());
        assertEquals(value, today);
        assertLookupNotEmpty(lookup, "java:version");
        assertLookupNotEmpty(lookup, "java:runtime");
        assertLookupNotEmpty(lookup, "java:vm");
        assertLookupNotEmpty(lookup, "java:os");
        assertLookupNotEmpty(lookup, "java:locale");
        assertLookupNotEmpty(lookup, "java:hw");
    }

    @Test
    public void testInterpolatorMapMessageWithNoPrefix() {
        final HashMap<String, String> configProperties = new HashMap<>();
        configProperties.put("key", "configProperties");
        final Interpolator interpolator = new Interpolator(new PropertiesLookup(configProperties), LOOKUP_PLUGINS);
        final HashMap<String, String> map = new HashMap<>();
        map.put("key", "mapMessage");
        final LogEvent event = Log4jLogEvent.newBuilder()
                .setLoggerName(getClass().getName())
                .setLoggerFqcn(Logger.class.getName())
                .setLevel(Level.INFO)
                .setMessage(new StringMapMessage(map))
                .build();
        assertEquals("configProperties", interpolator.lookup(event, "key"));
    }

    @Test
    public void testInterpolatorMapMessageWithNoPrefixConfigDoesntMatch() {
        final Interpolator interpolator =
                new Interpolator(new PropertiesLookup(Collections.emptyMap()), LOOKUP_PLUGINS);
        final HashMap<String, String> map = new HashMap<>();
        map.put("key", "mapMessage");
        final LogEvent event = Log4jLogEvent.newBuilder()
                .setLoggerName(getClass().getName())
                .setLoggerFqcn(Logger.class.getName())
                .setLevel(Level.INFO)
                .setMessage(new StringMapMessage(map))
                .build();
        assertNull(interpolator.lookup(event, "key"), "Values without a map prefix should not match MapMessages");
    }

    @Test
    public void testInterpolatorMapMessageWithMapPrefix() {
        final HashMap<String, String> configProperties = new HashMap<>();
        configProperties.put("key", "configProperties");
        final Interpolator interpolator = new Interpolator(new PropertiesLookup(configProperties), LOOKUP_PLUGINS);
        final HashMap<String, String> map = new HashMap<>();
        map.put("key", "mapMessage");
        final LogEvent event = Log4jLogEvent.newBuilder()
                .setLoggerName(getClass().getName())
                .setLoggerFqcn(Logger.class.getName())
                .setLevel(Level.INFO)
                .setMessage(new StringMapMessage(map))
                .build();
        assertEquals("mapMessage", interpolator.lookup(event, "map:key"));
    }
}
