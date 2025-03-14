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
package org.apache.logging.log4j.core.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.TriConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the JdkMapAdapterStringMap class.
 */
public class JdkMapAdapterStringMapTest {

    @Test
    public void testConstructorDisallowsNull() {
        assertThrows(NullPointerException.class, () -> new JdkMapAdapterStringMap(null, false));
    }

    @Test
    public void testToString() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("a2", "bvalue");
        original.putValue("B", "Bvalue");
        original.putValue("C", "Cvalue");
        original.putValue("3", "3value");
        assertEquals("{3=3value, B=Bvalue, C=Cvalue, a=avalue, a2=bvalue}", original.toString());
    }

    @Test
    public void testPutAll() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putAll(original);
        assertEquals(original, other);

        other.putValue("3", "otherValue");
        assertNotEquals(original, other);

        other.putValue("3", null);
        assertNotEquals(original, other);

        other.putValue("3", "3value");
        assertEquals(original, other);
    }

    @Test
    public void testPutAll_overwritesSameKeys2() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aORIG");
        original.putValue("b", "bORIG");
        original.putValue("c", "cORIG");
        original.putValue("d", "dORIG");
        original.putValue("e", "eORIG");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue("1", "11");
        other.putValue("2", "22");
        other.putValue("a", "aa");
        other.putValue("c", "cc");
        original.putAll(other);

        assertEquals(7, original.size(), "size after put other");
        assertEquals("aa", original.getValue("a"));
        assertEquals("bORIG", original.getValue("b"));
        assertEquals("cc", original.getValue("c"));
        assertEquals("dORIG", original.getValue("d"));
        assertEquals("eORIG", original.getValue("e"));
        assertEquals("11", original.getValue("1"));
        assertEquals("22", original.getValue("2"));
    }

    @Test
    public void testPutAll_nullKeyInLargeOriginal() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue(null, "nullORIG");
        original.putValue("a", "aORIG");
        original.putValue("b", "bORIG");
        original.putValue("c", "cORIG");
        original.putValue("d", "dORIG");
        original.putValue("e", "eORIG");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue("1", "11");
        other.putValue("a", "aa");
        original.putAll(other);

        assertEquals(7, original.size(), "size after put other");
        assertEquals("aa", original.getValue("a"));
        assertEquals("bORIG", original.getValue("b"));
        assertEquals("cORIG", original.getValue("c"));
        assertEquals("dORIG", original.getValue("d"));
        assertEquals("eORIG", original.getValue("e"));
        assertEquals("11", original.getValue("1"));
        assertEquals("nullORIG", original.getValue(null));
    }

    @Test
    public void testPutAll_nullKeyInSmallOriginal() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue(null, "nullORIG");
        original.putValue("a", "aORIG");
        original.putValue("b", "bORIG");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue("1", "11");
        other.putValue("2", "22");
        other.putValue("3", "33");
        other.putValue("a", "aa");
        original.putAll(other);

        assertEquals(6, original.size(), "size after put other");
        assertEquals("aa", original.getValue("a"));
        assertEquals("bORIG", original.getValue("b"));
        assertEquals("11", original.getValue("1"));
        assertEquals("22", original.getValue("2"));
        assertEquals("33", original.getValue("3"));
        assertEquals("nullORIG", original.getValue(null));
    }

    @Test
    public void testPutAll_nullKeyInSmallAdditional() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aORIG");
        original.putValue("b", "bORIG");
        original.putValue("c", "cORIG");
        original.putValue("d", "dORIG");
        original.putValue("e", "eORIG");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue(null, "nullNEW");
        other.putValue("1", "11");
        other.putValue("a", "aa");
        original.putAll(other);

        assertEquals(7, original.size(), "size after put other");
        assertEquals("aa", original.getValue("a"));
        assertEquals("bORIG", original.getValue("b"));
        assertEquals("cORIG", original.getValue("c"));
        assertEquals("dORIG", original.getValue("d"));
        assertEquals("eORIG", original.getValue("e"));
        assertEquals("11", original.getValue("1"));
        assertEquals("nullNEW", original.getValue(null));
    }

    @Test
    public void testPutAll_nullKeyInLargeAdditional() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aORIG");
        original.putValue("b", "bORIG");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue(null, "nullNEW");
        other.putValue("1", "11");
        other.putValue("2", "22");
        other.putValue("3", "33");
        other.putValue("a", "aa");
        original.putAll(other);

        assertEquals(6, original.size(), "size after put other");
        assertEquals("aa", original.getValue("a"));
        assertEquals("bORIG", original.getValue("b"));
        assertEquals("11", original.getValue("1"));
        assertEquals("22", original.getValue("2"));
        assertEquals("33", original.getValue("3"));
        assertEquals("nullNEW", original.getValue(null));
    }

    @Test
    public void testPutAll_nullKeyInBoth_LargeOriginal() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue(null, "nullORIG");
        original.putValue("a", "aORIG");
        original.putValue("b", "bORIG");
        original.putValue("c", "cORIG");
        original.putValue("d", "dORIG");
        original.putValue("e", "eORIG");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue(null, "nullNEW");
        other.putValue("1", "11");
        other.putValue("a", "aa");
        original.putAll(other);

        assertEquals(7, original.size(), "size after put other");
        assertEquals("aa", original.getValue("a"));
        assertEquals("bORIG", original.getValue("b"));
        assertEquals("cORIG", original.getValue("c"));
        assertEquals("dORIG", original.getValue("d"));
        assertEquals("eORIG", original.getValue("e"));
        assertEquals("11", original.getValue("1"));
        assertEquals("nullNEW", original.getValue(null));
    }

    @Test
    public void testPutAll_nullKeyInBoth_SmallOriginal() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue(null, "nullORIG");
        original.putValue("a", "aORIG");
        original.putValue("b", "bORIG");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue(null, "nullNEW");
        other.putValue("1", "11");
        other.putValue("2", "22");
        other.putValue("3", "33");
        other.putValue("a", "aa");
        original.putAll(other);

        assertEquals(6, original.size(), "size after put other");
        assertEquals("aa", original.getValue("a"));
        assertEquals("bORIG", original.getValue("b"));
        assertEquals("11", original.getValue("1"));
        assertEquals("22", original.getValue("2"));
        assertEquals("33", original.getValue("3"));
        assertEquals("nullNEW", original.getValue(null));
    }

    @Test
    public void testPutAll_overwritesSameKeys1() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aORIG");
        original.putValue("b", "bORIG");
        original.putValue("c", "cORIG");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue("1", "11");
        other.putValue("2", "22");
        other.putValue("a", "aa");
        other.putValue("c", "cc");
        original.putAll(other);

        assertEquals(5, original.size(), "size after put other");
        assertEquals("aa", original.getValue("a"));
        assertEquals("bORIG", original.getValue("b"));
        assertEquals("cc", original.getValue("c"));
        assertEquals("11", original.getValue("1"));
        assertEquals("22", original.getValue("2"));
    }

    @Test
    public void testEquals() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");
        assertEquals(original, original); // equal to itself

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue("a", "avalue");
        assertNotEquals(original, other);

        other.putValue("B", "Bvalue");
        assertNotEquals(original, other);

        other.putValue("3", "3value");
        assertEquals(original, other);

        other.putValue("3", "otherValue");
        assertNotEquals(original, other);

        other.putValue("3", null);
        assertNotEquals(original, other);

        other.putValue("3", "3value");
        assertEquals(original, other);
    }

    @Test
    public void testToMap() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");

        final Map<String, Object> expected = new HashMap<>();
        expected.put("a", "avalue");
        expected.put("B", "Bvalue");
        expected.put("3", "3value");

        assertEquals(expected, original.toMap());

        try {
            original.toMap().put("abc", "xyz");
        } catch (final UnsupportedOperationException ex) {
            fail("Expected map to be mutable, but " + ex);
        }
    }

    @Test
    public void testPutAll_KeepsExistingValues() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "bbb");
        original.putValue("c", "ccc");
        assertEquals(3, original.size(), "size");

        // add empty context data
        original.putAll(new JdkMapAdapterStringMap());
        assertEquals(3, original.size(), "size after put empty");
        assertEquals("aaa", original.getValue("a"));
        assertEquals("bbb", original.getValue("b"));
        assertEquals("ccc", original.getValue("c"));

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue("1", "111");
        other.putValue("2", "222");
        other.putValue("3", "333");
        original.putAll(other);

        assertEquals(6, original.size(), "size after put other");
        assertEquals("aaa", original.getValue("a"));
        assertEquals("bbb", original.getValue("b"));
        assertEquals("ccc", original.getValue("c"));
        assertEquals("111", original.getValue("1"));
        assertEquals("222", original.getValue("2"));
        assertEquals("333", original.getValue("3"));
    }

    @Test
    public void testPutAll_sizePowerOfTwo() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "bbb");
        original.putValue("c", "ccc");
        original.putValue("d", "ddd");
        assertEquals(4, original.size(), "size");

        // add empty context data
        original.putAll(new JdkMapAdapterStringMap());
        assertEquals(4, original.size(), "size after put empty");
        assertEquals("aaa", original.getValue("a"));
        assertEquals("bbb", original.getValue("b"));
        assertEquals("ccc", original.getValue("c"));
        assertEquals("ddd", original.getValue("d"));

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        other.putValue("1", "111");
        other.putValue("2", "222");
        other.putValue("3", "333");
        other.putValue("4", "444");
        original.putAll(other);

        assertEquals(8, original.size(), "size after put other");
        assertEquals("aaa", original.getValue("a"));
        assertEquals("bbb", original.getValue("b"));
        assertEquals("ccc", original.getValue("c"));
        assertEquals("ddd", original.getValue("d"));
        assertEquals("111", original.getValue("1"));
        assertEquals("222", original.getValue("2"));
        assertEquals("333", original.getValue("3"));
        assertEquals("444", original.getValue("4"));
    }

    @Test
    public void testPutAll_largeAddition() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue(null, "nullVal");
        original.putValue("a", "aaa");
        original.putValue("b", "bbb");
        original.putValue("c", "ccc");
        original.putValue("d", "ddd");
        assertEquals(5, original.size(), "size");

        final JdkMapAdapterStringMap other = new JdkMapAdapterStringMap();
        for (int i = 0; i < 500; i++) {
            other.putValue(String.valueOf(i), String.valueOf(i));
        }
        other.putValue(null, "otherVal");
        original.putAll(other);

        assertEquals(505, original.size(), "size after put other");
        assertEquals("otherVal", original.getValue(null));
        assertEquals("aaa", original.getValue("a"));
        assertEquals("bbb", original.getValue("b"));
        assertEquals("ccc", original.getValue("c"));
        assertEquals("ddd", original.getValue("d"));
        for (int i = 0; i < 500; i++) {
            assertEquals(String.valueOf(i), original.getValue(String.valueOf(i)));
        }
    }

    @Test
    public void testPutAllSelfDoesNotModify() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "bbb");
        original.putValue("c", "ccc");
        assertEquals(3, original.size(), "size");

        // putAll with self
        original.putAll(original);
        assertEquals(3, original.size(), "size after put empty");
        assertEquals("aaa", original.getValue("a"));
        assertEquals("bbb", original.getValue("b"));
        assertEquals("ccc", original.getValue("c"));
    }

    @Test
    public void testNoConcurrentModificationBiConsumerPut() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "aaa");
        original.putValue("c", "aaa");
        original.putValue("d", "aaa");
        original.putValue("e", "aaa");
        original.forEach((s, o) -> original.putValue("c" + s, "other"));
    }

    @Test
    public void testNoConcurrentModificationBiConsumerPutValue() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "aaa");
        original.putValue("c", "aaa");
        original.putValue("d", "aaa");
        original.putValue("e", "aaa");
        original.forEach((s, o) -> original.putValue("c" + s, "other"));
    }

    @Test
    public void testNoConcurrentModificationBiConsumerRemove() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "aaa");
        original.putValue("c", "aaa");
        original.forEach((s, o) -> original.remove("a"));
    }

    @Test
    public void testNoConcurrentModificationBiConsumerClear() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "aaa");
        original.putValue("c", "aaa");
        original.putValue("d", "aaa");
        original.putValue("e", "aaa");
        original.forEach((s, o) -> original.clear());
    }

    @Test
    public void testNoConcurrentModificationTriConsumerPut() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "aaa");
        original.putValue("d", "aaa");
        original.putValue("e", "aaa");
        original.forEach((s, o, o2) -> original.putValue("c", "other"), null);
    }

    @Test
    public void testNoConcurrentModificationTriConsumerPutValue() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "aaa");
        original.putValue("c", "aaa");
        original.putValue("d", "aaa");
        original.putValue("e", "aaa");
        original.forEach((s, o, o2) -> original.putValue("c" + s, "other"), null);
    }

    @Test
    public void testNoConcurrentModificationTriConsumerRemove() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "aaa");
        original.putValue("c", "aaa");
        original.forEach((s, o, o2) -> original.remove("a"), null);
    }

    @Test
    public void testNoConcurrentModificationTriConsumerClear() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.putValue("b", "aaa");
        original.putValue("c", "aaa");
        original.putValue("d", "aaa");
        original.forEach((s, o, o2) -> original.clear(), null);
    }

    @Test
    public void testInitiallyNotFrozen() {
        assertFalse(new JdkMapAdapterStringMap().isFrozen());
    }

    @Test
    public void testIsFrozenAfterCallingFreeze() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        assertFalse(original.isFrozen(), "before freeze");
        original.freeze();
        assertTrue(original.isFrozen(), "after freeze");
    }

    @Test
    public void testFreezeProhibitsPutValue() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.freeze();
        assertThrows(UnsupportedOperationException.class, () -> original.putValue("a", "aaa"));
    }

    @Test
    public void testFreezeProhibitsRemove() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("b", "bbb");
        original.freeze();
        assertThrows(
                UnsupportedOperationException.class,
                () -> original.remove("b")); // existing key: modifies the collection
    }

    @Test
    public void testFreezeAllowsRemoveOfNonExistingKey() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("b", "bbb");
        original.freeze();
        original.remove("a"); // no actual modification
    }

    @Test
    public void testFreezeAllowsRemoveIfEmpty() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.freeze();
        original.remove("a"); // no exception
    }

    @Test
    public void testFreezeProhibitsClear() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "aaa");
        original.freeze();
        assertThrows(UnsupportedOperationException.class, original::clear);
    }

    @Test
    public void testFreezeAllowsClearIfEmpty() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.freeze();
        original.clear();
    }

    @Test
    public void testNullKeysAllowed() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");
        original.putValue("c", "cvalue");
        original.putValue("d", "dvalue");
        assertEquals(5, original.size());

        original.putValue(null, "nullvalue");
        assertEquals(6, original.size());
        assertEquals("nullvalue", original.getValue(null));

        original.putValue(null, "otherNullvalue");
        assertEquals("otherNullvalue", original.getValue(null));
        assertEquals(6, original.size());

        original.putValue(null, "nullvalue");
        assertEquals(6, original.size());
        assertEquals("nullvalue", original.getValue(null));

        original.putValue(null, "abc");
        assertEquals(6, original.size());
        assertEquals("abc", original.getValue(null));
    }

    @Test
    public void testNullKeysCopiedToAsMap() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");
        original.putValue("c", "cvalue");
        original.putValue("d", "dvalue");
        assertEquals(5, original.size());

        final HashMap<String, String> expected = new HashMap<>();
        expected.put("a", "avalue");
        expected.put("B", "Bvalue");
        expected.put("3", "3value");
        expected.put("c", "cvalue");
        expected.put("d", "dvalue");
        assertEquals(expected, original.toMap(), "initial");

        original.putValue(null, "nullvalue");
        expected.put(null, "nullvalue");
        assertEquals(6, original.size());
        assertEquals(expected, original.toMap(), "with null key");

        original.putValue(null, "otherNullvalue");
        expected.put(null, "otherNullvalue");
        assertEquals(6, original.size());
        assertEquals(expected, original.toMap(), "with null key value2");

        original.putValue(null, "nullvalue");
        expected.put(null, "nullvalue");
        assertEquals(6, original.size());
        assertEquals(expected, original.toMap(), "with null key value1 again");

        original.putValue(null, "abc");
        expected.put(null, "abc");
        assertEquals(6, original.size());
        assertEquals(expected, original.toMap(), "with null key value3");
    }

    @Test
    public void testRemove() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        assertEquals(1, original.size());
        assertEquals("avalue", original.getValue("a"));

        original.remove("a");
        assertEquals(0, original.size());
        assertNull(original.getValue("a"), "no a val");

        original.remove("B");
        assertEquals(0, original.size());
        assertNull(original.getValue("B"), "no B val");
    }

    @Test
    public void testRemoveWhenFull() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("b", "bvalue");
        original.putValue("c", "cvalue");
        original.putValue("d", "dvalue"); // default capacity = 4
        original.remove("d");
    }

    @Test
    public void testNullValuesArePreserved() {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        assertEquals(1, original.size());
        assertEquals("avalue", original.getValue("a"));

        original.putValue("a", null);
        assertEquals(1, original.size());
        assertNull(original.getValue("a"), "no a val");

        original.putValue("B", null);
        assertEquals(2, original.size());
        assertNull(original.getValue("B"), "no B val");
    }

    @Test
    public void testGet() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");

        assertEquals("avalue", original.getValue("a"));
        assertEquals("Bvalue", original.getValue("B"));
        assertEquals("3value", original.getValue("3"));

        original.putValue("0", "0value");
        assertEquals("0value", original.getValue("0"));
        assertEquals("3value", original.getValue("3"));
        assertEquals("Bvalue", original.getValue("B"));
        assertEquals("avalue", original.getValue("a"));
    }

    @Test
    public void testClear() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");
        assertEquals(3, original.size());

        original.clear();
        assertEquals(0, original.size());
    }

    @Test
    public void testContainsKey() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        assertFalse(original.containsKey("a"), "a");
        assertFalse(original.containsKey("B"), "B");
        assertFalse(original.containsKey("3"), "3");
        assertFalse(original.containsKey("A"), "A");

        original.putValue("a", "avalue");
        assertTrue(original.containsKey("a"), "a");
        assertFalse(original.containsKey("B"), "B");
        assertFalse(original.containsKey("3"), "3");
        assertFalse(original.containsKey("A"), "A");

        original.putValue("B", "Bvalue");
        assertTrue(original.containsKey("a"), "a");
        assertTrue(original.containsKey("B"), "B");
        assertFalse(original.containsKey("3"), "3");
        assertFalse(original.containsKey("A"), "A");

        original.putValue("3", "3value");
        assertTrue(original.containsKey("a"), "a");
        assertTrue(original.containsKey("B"), "B");
        assertTrue(original.containsKey("3"), "3");
        assertFalse(original.containsKey("A"), "A");

        original.putValue("A", "AAA");
        assertTrue(original.containsKey("a"), "a");
        assertTrue(original.containsKey("B"), "B");
        assertTrue(original.containsKey("3"), "3");
        assertTrue(original.containsKey("A"), "A");
    }

    @Test
    public void testSizeAndIsEmpty() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        assertEquals(0, original.size());
        assertTrue(original.isEmpty(), "initial");

        original.putValue("a", "avalue");
        assertEquals(1, original.size());
        assertFalse(original.isEmpty(), "size=" + original.size());

        original.putValue("B", "Bvalue");
        assertEquals(2, original.size());
        assertFalse(original.isEmpty(), "size=" + original.size());

        original.putValue("3", "3value");
        assertEquals(3, original.size());
        assertFalse(original.isEmpty(), "size=" + original.size());

        original.remove("B");
        assertEquals(2, original.size());
        assertFalse(original.isEmpty(), "size=" + original.size());

        original.remove("3");
        assertEquals(1, original.size());
        assertFalse(original.isEmpty(), "size=" + original.size());

        original.remove("a");
        assertEquals(0, original.size());
        assertTrue(original.isEmpty(), "size=" + original.size());
    }

    @Test
    public void testForEachBiConsumer() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");

        original.forEach(new BiConsumer<String, String>() {
            int count = 0;

            @Override
            public void accept(final String key, final String value) {
                //                assertEquals("key", key, original.getKeyAt(count));
                //                assertEquals("val", value, original.getValueAt(count));
                count++;
                assertTrue(count <= original.size(), "count should not exceed size but was " + count);
            }
        });
    }

    static class State {
        JdkMapAdapterStringMap data;
        int count;
    }

    static TriConsumer<String, String, JdkMapAdapterStringMapTest.State> COUNTER = (key, value, state) -> {
        //            assertEquals("key", key, state.data.getKeyAt(state.count));
        //            assertEquals("val", value, state.data.getValueAt(state.count));
        state.count++;
        assertTrue(state.count <= state.data.size(), "count should not exceed size but was " + state.count);
    };

    @Test
    public void testForEachTriConsumer() throws Exception {
        final JdkMapAdapterStringMap original = new JdkMapAdapterStringMap();
        original.putValue("a", "avalue");
        original.putValue("B", "Bvalue");
        original.putValue("3", "3value");

        final JdkMapAdapterStringMapTest.State state = new JdkMapAdapterStringMapTest.State();
        state.data = original;
        original.forEach(COUNTER, state);
        assertEquals(state.count, original.size());
    }

    static Stream<Arguments> testImmutability() {
        return Stream.of(
                Arguments.of(new HashMap<>(), false),
                Arguments.of(Map.of(), true),
                Arguments.of(Collections.emptyMap(), true),
                Arguments.of(Collections.unmodifiableMap(new HashMap<>()), true));
    }

    @ParameterizedTest
    @MethodSource
    void testImmutability(final Map<String, String> map, final boolean frozen) {
        assertThat(new JdkMapAdapterStringMap(map).isFrozen()).as("Frozen").isEqualTo(frozen);
    }
}
