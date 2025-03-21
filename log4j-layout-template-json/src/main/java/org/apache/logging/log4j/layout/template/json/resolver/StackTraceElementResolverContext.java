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
package org.apache.logging.log4j.layout.template.json.resolver;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.layout.template.json.JsonTemplateLayoutProperties;
import org.apache.logging.log4j.layout.template.json.util.JsonWriter;

/**
 * {@link TemplateResolverContext} specialized for {@link StackTraceElement}s.
 *
 * @see StackTraceElementResolver
 * @see StackTraceElementResolverFactory
 */
final class StackTraceElementResolverContext
        implements TemplateResolverContext<StackTraceElement, StackTraceElementResolverContext> {

    private final Map<String, StackTraceElementResolverFactory> resolverFactoryByName;

    private final StackTraceElementResolverStringSubstitutor substitutor;

    private final JsonWriter jsonWriter;

    private final JsonTemplateLayoutProperties defaults;

    private StackTraceElementResolverContext(final Builder builder) {
        this.resolverFactoryByName = builder.resolverFactoryByName;
        this.substitutor = builder.substitutor;
        this.jsonWriter = builder.jsonWriter;
        this.defaults = builder.defaults;
    }

    @Override
    public final Class<StackTraceElementResolverContext> getContextClass() {
        return StackTraceElementResolverContext.class;
    }

    @Override
    public Map<String, StackTraceElementResolverFactory> getResolverFactoryByName() {
        return resolverFactoryByName;
    }

    @Override
    public List<? extends TemplateResolverInterceptor<StackTraceElement, StackTraceElementResolverContext>>
            getResolverInterceptors() {
        return Collections.emptyList();
    }

    @Override
    public StackTraceElementResolverStringSubstitutor getSubstitutor() {
        return substitutor;
    }

    @Override
    public JsonWriter getJsonWriter() {
        return jsonWriter;
    }

    @Override
    public JsonTemplateLayoutProperties getDefaults() {
        return defaults;
    }

    static Builder newBuilder() {
        return new Builder();
    }

    static final class Builder {

        private Map<String, StackTraceElementResolverFactory> resolverFactoryByName;

        private StackTraceElementResolverStringSubstitutor substitutor;

        private JsonWriter jsonWriter;

        private JsonTemplateLayoutProperties defaults;

        private Builder() {
            // Do nothing.
        }

        Builder setResolverFactoryByName(final Map<String, StackTraceElementResolverFactory> resolverFactoryByName) {
            this.resolverFactoryByName = resolverFactoryByName;
            return this;
        }

        Builder setSubstitutor(final StackTraceElementResolverStringSubstitutor substitutor) {
            this.substitutor = substitutor;
            return this;
        }

        Builder setJsonWriter(final JsonWriter jsonWriter) {
            this.jsonWriter = jsonWriter;
            return this;
        }

        Builder setDefaults(final JsonTemplateLayoutProperties defaults) {
            this.defaults = defaults;
            return this;
        }

        StackTraceElementResolverContext build() {
            validate();
            return new StackTraceElementResolverContext(this);
        }

        private void validate() {
            Objects.requireNonNull(resolverFactoryByName, "resolverFactoryByName");
            if (resolverFactoryByName.isEmpty()) {
                throw new IllegalArgumentException("empty resolverFactoryByName");
            }
            Objects.requireNonNull(substitutor, "substitutor");
            Objects.requireNonNull(jsonWriter, "jsonWriter");
            Objects.requireNonNull(defaults, "defaults");
        }
    }
}
