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
package org.apache.logging.log4j.async.logger.internal;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * However, it will periodically wake up if it has been idle for specified period by throwing a
 * {@link TimeoutException}.  To make use of this, the event handler class should override
 * {@link EventHandler#onTimeout(long)}, which the {@link BatchEventProcessor} will call if the timeout occurs.
 *
 * <p>This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
// IMPLEMENTATION NOTE:
// This is a copy of the com.lmax.disruptor.TimeoutBlockingWaitStrategy class in disruptor-4.0.0-RC1.
// The difference between this code and the implementation of this class in disruptor-3.4.4
// is that this class is garbage-free, because it uses a synchronized block instead of a ReentrantLock.
// This class is package-protected, so that it can be used internally as the default WaitStrategy
// by Log4j Async Loggers, but can be removed in a future Log4j release without impacting binary compatibility.
// (disruptor-4.0.0-RC1 requires Java 11 and has other incompatible changes so cannot be used in Log4j 2.x.)
//
// Log4j 3.0.0 NOTE:
// Implementation was updated to use Lock/Condition API for https://github.com/apache/logging-log4j2/issues/1532
public class TimeoutBlockingWaitStrategy implements WaitStrategy {
    private final Lock mutex = new ReentrantLock();
    private final Condition condition = mutex.newCondition();
    private final long timeoutInNanos;

    /**
     * @param timeout how long to wait before waking up
     * @param units   the unit in which timeout is specified
     */
    public TimeoutBlockingWaitStrategy(final long timeout, final TimeUnit units) {
        timeoutInNanos = units.toNanos(timeout);
    }

    @Override
    public long waitFor(
            final long sequence,
            final Sequence cursorSequence,
            final Sequence dependentSequence,
            final SequenceBarrier barrier)
            throws AlertException, InterruptedException, TimeoutException {
        long timeoutNanos = timeoutInNanos;

        long availableSequence;
        if (cursorSequence.get() < sequence) {
            mutex.lock();
            try {
                while (cursorSequence.get() < sequence) {
                    barrier.checkAlert();
                    timeoutNanos = condition.awaitNanos(timeoutNanos);
                    if (timeoutNanos <= 0) {
                        throw TimeoutException.INSTANCE;
                    }
                }
            } finally {
                mutex.unlock();
            }
        }

        while ((availableSequence = dependentSequence.get()) < sequence) {
            barrier.checkAlert();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
        mutex.lock();
        try {
            condition.signalAll();
        } finally {
            mutex.unlock();
        }
    }

    @Override
    public String toString() {
        return "TimeoutBlockingWaitStrategy{" + "mutex=" + mutex + ", timeoutInNanos=" + timeoutInNanos + '}';
    }
}
