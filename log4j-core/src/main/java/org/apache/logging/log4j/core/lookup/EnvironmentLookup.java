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

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.plugins.Plugin;

/**
 * Looks up keys from environment variables.
 */
@Lookup
@Plugin("env")
public class EnvironmentLookup extends AbstractLookup {

    /**
     * Looks up the value of the environment variable.
     *
     * @param key  the key to be looked up, may be null
     * @return The value of the environment variable.
     */
    @Override
    public String lookup(final LogEvent ignored, final String key) {
        // getenv throws NullPointerException if <code>name</code> is <code>null</code>
        return key != null ? System.getenv(key) : null;
    }
}
