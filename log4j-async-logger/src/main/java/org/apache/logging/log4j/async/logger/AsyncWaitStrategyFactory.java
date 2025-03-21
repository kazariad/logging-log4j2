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

import com.lmax.disruptor.WaitStrategy;
import java.util.function.Supplier;

/**
 * This interface allows users to configure a custom Disruptor WaitStrategy used for
 * Async Loggers and Async LoggerConfigs.
 *
 * @since 2.17.3
 */
@FunctionalInterface
public interface AsyncWaitStrategyFactory extends Supplier<WaitStrategy> {
    /**
     * Creates and returns a non-null implementation of the LMAX Disruptor's WaitStrategy interface.
     * This WaitStrategy will be used by Log4j Async Loggers and Async LoggerConfigs.
     *
     * @return the WaitStrategy instance to be used by Async Loggers and Async LoggerConfigs
     */
    WaitStrategy createWaitStrategy();

    @Override
    default WaitStrategy get() {
        return createWaitStrategy();
    }
}
