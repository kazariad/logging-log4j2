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
package org.apache.logging.log4j.core.appender.rolling.action;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.time.Clock;
import org.apache.logging.log4j.plugins.Configurable;
import org.apache.logging.log4j.plugins.Inject;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginAttribute;
import org.apache.logging.log4j.plugins.PluginElement;
import org.apache.logging.log4j.plugins.PluginFactory;
import org.apache.logging.log4j.plugins.validation.constraints.Required;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * PathCondition that accepts paths that are older than the specified duration.
 * @since 2.5
 */
@Configurable(printObject = true)
@Plugin
public final class IfLastModified implements PathCondition {
    private static final Logger LOGGER = StatusLogger.getLogger();
    private final Clock clock;

    private final Duration age;
    private final PathCondition[] nestedConditions;

    private IfLastModified(final Duration age, final PathCondition[] nestedConditions, final Clock clock) {
        this.age = Objects.requireNonNull(age, "age");
        this.nestedConditions = PathCondition.copy(nestedConditions);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @since 3.0.0
     */
    public Duration getAge() {
        return age;
    }

    public List<PathCondition> getNestedConditions() {
        return List.of(nestedConditions);
    }

    @Override
    public boolean accept(final Path basePath, final Path relativePath, final BasicFileAttributes attrs) {
        final FileTime fileTime = attrs.lastModifiedTime();
        final long millis = fileTime.toMillis();
        final long ageMillis = clock.currentTimeMillis() - millis;
        final boolean result = ageMillis >= age.toMillis();
        final String match = result ? ">=" : "<";
        final String accept = result ? "ACCEPTED" : "REJECTED";
        LOGGER.trace("IfLastModified {}: {} ageMillis '{}' {} '{}'", accept, relativePath, ageMillis, match, age);
        if (result) {
            return IfAll.accept(nestedConditions, basePath, relativePath, attrs);
        }
        return result;
    }

    @Override
    public void beforeFileTreeWalk() {
        IfAll.beforeFileTreeWalk(nestedConditions);
    }

    @Override
    public String toString() {
        final String nested = nestedConditions.length == 0 ? "" : " AND " + Arrays.toString(nestedConditions);
        return "IfLastModified(age=" + age + nested + ")";
    }

    /**
     * @since 2.24.0
     */
    @PluginFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * @since 2.24.0
     */
    public static final class Builder implements Supplier<IfLastModified> {
        private Duration age;
        private PathCondition[] nestedConditions;
        private Clock clock;

        public Builder setAge(
                @PluginAttribute @Required(message = "No age provided for IfLastModified") final Duration age) {
            this.age = age;
            return this;
        }

        public Builder setNestedConditions(@PluginElement final PathCondition... nestedConditions) {
            this.nestedConditions = nestedConditions;
            return this;
        }

        @Inject
        public Builder setClock(final Clock clock) {
            this.clock = clock;
            return this;
        }

        @Override
        public IfLastModified get() {
            return new IfLastModified(age, nestedConditions, clock);
        }
    }
}
