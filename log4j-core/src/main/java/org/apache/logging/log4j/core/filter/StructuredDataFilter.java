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
package org.apache.logging.log4j.core.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.kit.recycler.Recycler;
import org.apache.logging.log4j.kit.recycler.RecyclerFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.apache.logging.log4j.plugins.Configurable;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginAttribute;
import org.apache.logging.log4j.plugins.PluginElement;
import org.apache.logging.log4j.plugins.PluginFactory;
import org.apache.logging.log4j.util.IndexedReadOnlyStringMap;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.StringBuilders;

/**
 * Filter based on data in a StructuredDataMessage.
 */
@Configurable(elementType = Filter.ELEMENT_TYPE, printObject = true)
@Plugin
@PerformanceSensitive("allocation")
public final class StructuredDataFilter extends MapFilter {

    private static final int MAX_BUFFER_SIZE = 2048;

    private final Recycler<StringBuilder> stringBuilderRecycler;

    private StructuredDataFilter(
            final RecyclerFactory recyclerFactory,
            final Map<String, List<String>> map,
            final boolean oper,
            final Result onMatch,
            final Result onMismatch) {
        super(map, oper, onMatch, onMismatch);
        this.stringBuilderRecycler = recyclerFactory.create(StringBuilder::new);
    }

    @Override
    public Result filter(
            final Logger logger, final Level level, final Marker marker, final Message msg, final Throwable t) {
        if (msg instanceof StructuredDataMessage) {
            return filter((StructuredDataMessage) msg);
        }
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(final LogEvent event) {
        final Message msg = event.getMessage();
        if (msg instanceof StructuredDataMessage) {
            return filter((StructuredDataMessage) msg);
        }
        return super.filter(event);
    }

    protected Result filter(final StructuredDataMessage message) {
        boolean match = false;
        final IndexedReadOnlyStringMap map = getStringMap();
        final StringBuilder sb = stringBuilderRecycler.acquire();
        try {
            for (int i = 0; i < map.size(); i++) {
                final StringBuilder toMatch = getValue(sb, message, map.getKeyAt(i));
                if (toMatch != null) {
                    final List<String> candidates = map.getValueAt(i);
                    match = listContainsValue(candidates, toMatch);
                } else {
                    match = false;
                }
                if ((!isAnd() && match) || (isAnd() && !match)) {
                    break;
                }
                StringBuilders.trimToMaxSize(sb, MAX_BUFFER_SIZE);
                sb.setLength(0);
            }
        } finally {
            stringBuilderRecycler.release(sb);
        }
        return match ? onMatch : onMismatch;
    }

    private StringBuilder getValue(final StringBuilder sb, final StructuredDataMessage data, final String key) {
        if (key.equalsIgnoreCase("id")) {
            data.getId().formatTo(sb);
            return sb;
        } else if (key.equalsIgnoreCase("id.name")) {
            return appendOrNull(data.getId().getName(), sb);
        } else if (key.equalsIgnoreCase("type")) {
            return appendOrNull(data.getType(), sb);
        } else if (key.equalsIgnoreCase("message")) {
            data.formatTo(sb);
            return sb;
        } else {
            return appendOrNull(data.get(key), sb);
        }
    }

    private StringBuilder appendOrNull(final String value, final StringBuilder sb) {
        if (value == null) {
            return null;
        }
        sb.append(value);
        return sb;
    }

    private boolean listContainsValue(final List<String> candidates, final StringBuilder toMatch) {
        if (toMatch == null) {
            for (int i = 0; i < candidates.size(); i++) {
                final String candidate = candidates.get(i);
                if (candidate == null) {
                    return true;
                }
            }
        } else {
            for (int i = 0; i < candidates.size(); i++) {
                final String candidate = candidates.get(i);
                if (candidate == null) {
                    return false;
                }
                if (StringBuilders.equals(candidate, 0, candidate.length(), toMatch, 0, toMatch.length())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates the StructuredDataFilter.
     * @param pairs Key and value pairs.
     * @param operator The operator to perform. If not "or" the operation will be an "and".
     * @param onMatch The action to perform on a match.
     * @param onMismatch The action to perform on a mismatch.
     * @return The StructuredDataFilter.
     */
    // TODO Consider refactoring to use AbstractFilter.AbstractFilterBuilder
    @PluginFactory
    public static StructuredDataFilter createFilter(
            @PluginConfiguration final Configuration configuration,
            @PluginElement final KeyValuePair[] pairs,
            @PluginAttribute final String operator,
            @PluginAttribute final Result onMatch,
            @PluginAttribute final Result onMismatch) {
        if (pairs == null || pairs.length == 0) {
            LOGGER.error("keys and values must be specified for the StructuredDataFilter");
            return null;
        }
        final Map<String, List<String>> map = new HashMap<>();
        for (final KeyValuePair pair : pairs) {
            final String key = pair.getKey();
            if (key == null) {
                LOGGER.error("A null key is not valid in MapFilter");
                continue;
            }
            final String value = pair.getValue();
            if (value == null) {
                LOGGER.error("A null value for key " + key + " is not allowed in MapFilter");
                continue;
            }
            List<String> list = map.get(pair.getKey());
            if (list != null) {
                list.add(value);
            } else {
                list = new ArrayList<>();
                list.add(value);
                map.put(pair.getKey(), list);
            }
        }
        if (map.isEmpty()) {
            LOGGER.error("StructuredDataFilter is not configured with any valid key value pairs");
            return null;
        }
        final boolean isAnd = operator == null || !operator.equalsIgnoreCase("or");
        final RecyclerFactory recyclerFactory = configuration.getRecyclerFactory();
        return new StructuredDataFilter(recyclerFactory, map, isAnd, onMatch, onMismatch);
    }
}
