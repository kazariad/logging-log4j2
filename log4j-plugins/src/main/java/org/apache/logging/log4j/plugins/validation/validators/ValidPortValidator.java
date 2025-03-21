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
package org.apache.logging.log4j.plugins.validation.validators;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.plugins.validation.ConstraintValidator;
import org.apache.logging.log4j.plugins.validation.constraints.ValidPort;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * Validator that checks an object to verify it is a valid port number (an integer between 0 and 65535).
 *
 * @since 2.8
 */
public class ValidPortValidator implements ConstraintValidator<ValidPort> {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private ValidPort annotation;

    @Override
    public void initialize(final ValidPort annotation) {
        this.annotation = annotation;
    }

    @Override
    public boolean isValid(final String name, final Object value) {
        final int port;
        if (value instanceof Integer i) {
            port = i;
        } else if (value instanceof CharSequence cs) {
            try {
                port = Integer.parseInt(cs, 0, cs.length(), 10);
            } catch (final NumberFormatException ignored) {
                LOGGER.error(annotation.message());
                return false;
            }
        } else {
            LOGGER.error(annotation.message());
            return false;
        }
        if (port < 0 || port > 65535) {
            LOGGER.error(annotation.message());
            return false;
        }
        return true;
    }
}
