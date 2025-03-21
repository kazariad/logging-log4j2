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
package org.apache.logging.log4j.core.config.plugins.util;

import java.util.function.Supplier;
import org.apache.logging.log4j.core.config.plugins.PluginNode;
import org.apache.logging.log4j.plugins.Node;
import org.apache.logging.log4j.plugins.di.InstanceFactory;
import org.apache.logging.log4j.plugins.di.Key;
import org.apache.logging.log4j.plugins.di.spi.FactoryResolver;
import org.apache.logging.log4j.plugins.di.spi.ResolvableKey;

@Deprecated(since = "3.0", forRemoval = true)
@SuppressWarnings("removal")
public class LegacyPluginNodeFactoryResolver implements FactoryResolver<Node> {
    @Override
    public boolean supportsKey(final Key<?> key) {
        return key.getQualifierType() == PluginNode.class;
    }

    @Override
    public Supplier<Node> getFactory(final ResolvableKey<Node> resolvableKey, final InstanceFactory instanceFactory) {
        return instanceFactory.getFactory(Node.CURRENT_NODE);
    }
}
