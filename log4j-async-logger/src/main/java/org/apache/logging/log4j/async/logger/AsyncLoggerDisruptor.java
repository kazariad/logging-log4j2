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

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.async.logger.internal.AsyncLoggerDefaultExceptionHandler;
import org.apache.logging.log4j.core.AbstractLifeCycle;
import org.apache.logging.log4j.core.async.AsyncQueueFullPolicy;
import org.apache.logging.log4j.core.async.DiscardingAsyncQueueFullPolicy;
import org.apache.logging.log4j.core.async.EventRoute;
import org.apache.logging.log4j.core.util.Log4jThread;
import org.apache.logging.log4j.core.util.Log4jThreadFactory;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;

/**
 * Helper class for async loggers: AsyncLoggerDisruptor handles the mechanics of working with the LMAX Disruptor, and
 * works with its associated AsyncLoggerContext to synchronize the life cycle of the Disruptor and its thread with the
 * life cycle of the context. The AsyncLoggerDisruptor of the context is shared by all AsyncLogger objects created by
 * that AsyncLoggerContext.
 */
public class AsyncLoggerDisruptor extends AbstractLifeCycle {
    private static final int SLEEP_MILLIS_BETWEEN_DRAIN_ATTEMPTS = 50;
    private static final int MAX_DRAIN_ATTEMPTS_BEFORE_SHUTDOWN = 200;

    private final Lock startLock = new ReentrantLock();
    private final Lock queueFullEnqueueLock = new ReentrantLock();

    private volatile Disruptor<RingBufferLogEvent> disruptor;
    private String contextName;
    private final AsyncLoggerProperties properties;

    private long backgroundThreadId;
    private final AsyncQueueFullPolicy asyncQueueFullPolicy;
    private final WaitStrategy waitStrategy;

    public AsyncLoggerDisruptor(
            final String contextName,
            final AsyncQueueFullPolicy asyncQueueFullPolicy,
            final WaitStrategy waitStrategy,
            final AsyncLoggerProperties properties) {
        this.contextName = contextName;
        this.asyncQueueFullPolicy = asyncQueueFullPolicy;
        this.waitStrategy = waitStrategy;
        this.properties = properties;
    }

    // package-protected for testing

    WaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    public String getContextName() {
        return contextName;
    }

    public void setContextName(final String name) {
        contextName = name;
    }

    /**
     * Creates and starts a new Disruptor and associated thread if none currently exists.
     *
     * @see #stop()
     */
    @Override
    public void start() {
        startLock.lock();
        try {
            if (disruptor != null) {
                LOGGER.trace(
                        "[{}] AsyncLoggerDisruptor not starting new disruptor for this context, using existing object.",
                        contextName);
                return;
            }
            if (isStarting()) {
                LOGGER.trace("[{}] AsyncLoggerDisruptor is already starting.", contextName);
                return;
            }
            setStarting();
            LOGGER.trace("[{}] AsyncLoggerDisruptor creating new disruptor for this context.", contextName);
            final int ringBufferSize = properties.ringBuffer().size();

            final ThreadFactory threadFactory =
                    new Log4jThreadFactory("AsyncLogger[" + contextName + "]", true, Thread.NORM_PRIORITY) {
                        @Override
                        public Thread newThread(final Runnable r) {
                            final Thread result = super.newThread(r);
                            backgroundThreadId = result.getId();
                            return result;
                        }
                    };

            disruptor = new Disruptor<>(
                    RingBufferLogEvent.FACTORY, ringBufferSize, threadFactory, ProducerType.MULTI, waitStrategy);

            final AsyncLoggerExceptionHandler errorHandler = getExceptionHandler();
            disruptor.setDefaultExceptionHandler(errorHandler);

            final RingBufferLogEventHandler[] handlers = {new RingBufferLogEventHandler()};
            disruptor.handleEventsWith(handlers);

            LOGGER.debug(
                    "[{}] Starting AsyncLogger disruptor for this context with ringbufferSize={}, waitStrategy={}, "
                            + "exceptionHandler={}...",
                    contextName,
                    disruptor.getRingBuffer().getBufferSize(),
                    waitStrategy.getClass().getSimpleName(),
                    errorHandler);
            disruptor.start();

            super.start();
        } finally {
            startLock.unlock();
        }
    }

    /**
     * Decreases the reference count. If the reference count reached zero, the Disruptor and its associated thread are
     * shut down and their references set to {@code null}.
     */
    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        final Disruptor<RingBufferLogEvent> temp = this.disruptor;
        if (temp == null) {
            LOGGER.trace("[{}] AsyncLoggerDisruptor: disruptor for this context already shut down.", contextName);
            return true; // disruptor was already shut down by another thread
        }
        setStopping();
        LOGGER.debug("[{}] AsyncLoggerDisruptor: shutting down disruptor for this context.", contextName);

        // We must guarantee that publishing to the RingBuffer has stopped before we call disruptor.shutdown().
        disruptor = null; // client code fails with NPE if log after stop. This is by design.

        // Calling Disruptor.shutdown() will wait until all enqueued events are fully processed,
        // but this waiting happens in a busy-spin. To avoid (postpone) wasting CPU,
        // we sleep in short chunks, up to 10 seconds, waiting for the ringbuffer to drain.
        for (int i = 0; hasBacklog(temp) && i < MAX_DRAIN_ATTEMPTS_BEFORE_SHUTDOWN; i++) {
            try {
                Thread.sleep(SLEEP_MILLIS_BETWEEN_DRAIN_ATTEMPTS); // give up the CPU for a while
            } catch (final InterruptedException e) { // ignored
            }
        }
        try {
            // busy-spins until all events currently in the disruptor have been processed, or timeout
            temp.shutdown(timeout, timeUnit);
        } catch (final TimeoutException e) {
            LOGGER.warn("[{}] AsyncLoggerDisruptor: shutdown timed out after {} {}", contextName, timeout, timeUnit);
            temp.halt(); // give up on remaining log events, if any
        }

        LOGGER.trace("[{}] AsyncLoggerDisruptor: disruptor has been shut down.", contextName);

        if (DiscardingAsyncQueueFullPolicy.getDiscardCount(asyncQueueFullPolicy) > 0) {
            LOGGER.trace(
                    "AsyncLoggerDisruptor: {} discarded {} events.",
                    asyncQueueFullPolicy,
                    DiscardingAsyncQueueFullPolicy.getDiscardCount(asyncQueueFullPolicy));
        }
        setStopped();
        return true;
    }

    /**
     * Returns {@code true} if the specified disruptor still has unprocessed events.
     */
    private static boolean hasBacklog(final Disruptor<?> theDisruptor) {
        final RingBuffer<?> ringBuffer = theDisruptor.getRingBuffer();
        return !ringBuffer.hasAvailableCapacity(ringBuffer.getBufferSize());
    }

    EventRoute getEventRoute(final Level logLevel) {
        final int remainingCapacity = remainingDisruptorCapacity();
        if (remainingCapacity < 0) {
            return EventRoute.DISCARD;
        }
        return asyncQueueFullPolicy.getRoute(backgroundThreadId, logLevel);
    }

    private AsyncLoggerExceptionHandler getExceptionHandler() {
        try {
            final Class<? extends AsyncLoggerExceptionHandler> handlerClass = properties.exceptionHandler();
            if (handlerClass != null) {
                return LoaderUtil.newInstanceOf(handlerClass);
            }
        } catch (final ReflectiveOperationException e) {
            StatusLogger.getLogger().debug("Invalid AsyncLogger.ExceptionHandler value: {}", e.getMessage(), e);
        }
        return new AsyncLoggerDefaultExceptionHandler();
    }

    private int remainingDisruptorCapacity() {
        final Disruptor<RingBufferLogEvent> temp = disruptor;
        if (hasLog4jBeenShutDown(temp)) {
            return -1;
        }
        return (int) temp.getRingBuffer().remainingCapacity();
    }
    /**
     * Returns {@code true} if the specified disruptor is null.
     */
    private boolean hasLog4jBeenShutDown(final Disruptor<RingBufferLogEvent> aDisruptor) {
        if (aDisruptor == null) { // LOG4J2-639
            LOGGER.warn("Ignoring log event after log4j was shut down");
            return true;
        }
        return false;
    }

    boolean tryPublish(final RingBufferLogEventTranslator translator) {
        try {
            // Note: we deliberately access the volatile disruptor field afresh here.
            // Avoiding this and using an older reference could result in adding a log event to the disruptor after it
            // was shut down, which could cause the publishEvent method to hang and never return.
            return disruptor.getRingBuffer().tryPublishEvent(translator);
        } catch (final NullPointerException npe) {
            // LOG4J2-639: catch NPE if disruptor field was set to null in stop()
            logWarningOnNpeFromDisruptorPublish(translator);
            return false;
        }
    }

    void enqueueLogMessageWhenQueueFull(final RingBufferLogEventTranslator translator) {
        try {
            // Note: we deliberately access the volatile disruptor field afresh here.
            // Avoiding this and using an older reference could result in adding a log event to the disruptor after it
            // was shut down, which could cause the publishEvent method to hang and never return.
            if (synchronizeEnqueueWhenQueueFull()) {
                queueFullEnqueueLock.lock();
                try {
                    disruptor.publishEvent(translator);
                } finally {
                    queueFullEnqueueLock.unlock();
                }
            } else {
                disruptor.publishEvent(translator);
            }
        } catch (final NullPointerException npe) {
            // LOG4J2-639: catch NPE if disruptor field was set to null in stop()
            logWarningOnNpeFromDisruptorPublish(translator);
        }
    }

    private boolean synchronizeEnqueueWhenQueueFull() {
        return properties.synchronizeEnqueueWhenQueueFull()
                // Background thread must never block
                && backgroundThreadId != Thread.currentThread().getId()
                // Threads owned by log4j are most likely to result in
                // deadlocks because they generally consume events.
                // This prevents deadlocks between AsyncLoggerContext
                // disruptors.
                && !(Thread.currentThread() instanceof Log4jThread);
    }

    private void logWarningOnNpeFromDisruptorPublish(final RingBufferLogEventTranslator translator) {
        logWarningOnNpeFromDisruptorPublish(
                translator.level, translator.loggerName, translator.message, translator.thrown);
    }

    private void logWarningOnNpeFromDisruptorPublish(
            final Level level, final String fqcn, final Message msg, final Throwable thrown) {
        LOGGER.warn(
                "[{}] Ignoring log event after log4j was shut down: {} [{}] {}{}",
                contextName,
                level,
                fqcn,
                msg.getFormattedMessage(),
                thrown == null ? "" : Throwables.toStringList(thrown));
    }

    // package-protected for tests
    RingBuffer<RingBufferLogEvent> getRingBuffer() {
        return disruptor.getRingBuffer();
    }

    @FunctionalInterface
    public interface Factory {
        AsyncLoggerDisruptor createAsyncLoggerDisruptor(String contextName);
    }
}
