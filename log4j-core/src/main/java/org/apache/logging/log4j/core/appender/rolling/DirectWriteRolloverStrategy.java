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
package org.apache.logging.log4j.core.appender.rolling;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.core.appender.rolling.action.Action;
import org.apache.logging.log4j.core.appender.rolling.action.CompositeAction;
import org.apache.logging.log4j.core.appender.rolling.action.CompressActionFactory;
import org.apache.logging.log4j.core.appender.rolling.action.CompressActionFactoryProvider;
import org.apache.logging.log4j.core.appender.rolling.action.FileRenameAction;
import org.apache.logging.log4j.core.appender.rolling.action.PathCondition;
import org.apache.logging.log4j.core.appender.rolling.action.PosixViewAttributeAction;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.plugins.Configurable;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.plugins.PluginElement;
import org.apache.logging.log4j.plugins.PluginFactory;

/**
 * When rolling over, <code>DirectWriteRolloverStrategy</code> writes directly to the file as resolved by the file
 * pattern. Files will be renamed files according to an algorithm as described below.
 *
 * <p>
 * The DirectWriteRolloverStrategy uses similar logic as DefaultRolloverStrategy to determine the file name based
 * on the file pattern, however the DirectWriteRolloverStrategy writes directly to a file and does not rename it
 * during rollover, except if it is compressed, in which case it will add the appropriate file extension.
 * </p>
 *
 * @since 2.8
 */
@Configurable(printObject = true)
@Plugin
public class DirectWriteRolloverStrategy extends AbstractRolloverStrategy implements DirectFileRolloverStrategy {

    private static final int DEFAULT_MAX_FILES = 7;

    /**
     * Builds DirectWriteRolloverStrategy instances.
     */
    public static class Builder implements org.apache.logging.log4j.plugins.util.Builder<DirectWriteRolloverStrategy> {
        @PluginBuilderAttribute("maxFiles")
        private String maxFiles;

        @PluginElement("Actions")
        private Action[] customActions;

        @PluginBuilderAttribute(value = "stopCustomActionsOnError")
        private boolean stopCustomActionsOnError = true;

        @PluginBuilderAttribute(value = "tempCompressedFilePattern")
        private String tempCompressedFilePattern;

        @PluginConfiguration
        private Configuration config;

        @Override
        public DirectWriteRolloverStrategy build() {
            int maxIndex = Integer.MAX_VALUE;
            if (maxFiles != null) {
                maxIndex = Integer.parseInt(maxFiles);
                if (maxIndex < 0) {
                    maxIndex = Integer.MAX_VALUE;
                } else if (maxIndex < 2) {
                    LOGGER.error("Maximum files too small. Limited to " + DEFAULT_MAX_FILES);
                    maxIndex = DEFAULT_MAX_FILES;
                }
            }
            return new DirectWriteRolloverStrategy(
                    maxIndex,
                    config.getStrSubstitutor(),
                    customActions,
                    stopCustomActionsOnError,
                    tempCompressedFilePattern,
                    config);
        }

        public String getMaxFiles() {
            return maxFiles;
        }

        /**
         * Defines the maximum number of files to keep.
         *
         * @param maxFiles The maximum number of files that match the date portion of the pattern to keep.
         * @return This builder for chaining convenience
         */
        public Builder setMaxFiles(final String maxFiles) {
            this.maxFiles = maxFiles;
            return this;
        }

        public Action[] getCustomActions() {
            return customActions;
        }

        /**
         * Defines custom actions.
         *
         * @param customActions custom actions to perform asynchronously after rollover
         * @return This builder for chaining convenience
         */
        public Builder setCustomActions(final Action... customActions) {
            this.customActions = customActions;
            return this;
        }

        public boolean isStopCustomActionsOnError() {
            return stopCustomActionsOnError;
        }

        /**
         * Defines whether to stop executing asynchronous actions if an error occurs.
         *
         * @param stopCustomActionsOnError whether to stop executing asynchronous actions if an error occurs
         * @return This builder for chaining convenience
         */
        public Builder setStopCustomActionsOnError(final boolean stopCustomActionsOnError) {
            this.stopCustomActionsOnError = stopCustomActionsOnError;
            return this;
        }

        public String getTempCompressedFilePattern() {
            return tempCompressedFilePattern;
        }

        /**
         * Defines temporary compression file pattern.
         *
         * @param tempCompressedFilePattern File pattern of the working file pattern used during compression, if null no temporary file are used
         * @return This builder for chaining convenience
         */
        public Builder setTempCompressedFilePattern(final String tempCompressedFilePattern) {
            this.tempCompressedFilePattern = tempCompressedFilePattern;
            return this;
        }

        public Configuration getConfig() {
            return config;
        }

        /**
         * Defines configuration.
         *
         * @param config The Configuration.
         * @return This builder for chaining convenience
         */
        public Builder setConfig(final Configuration config) {
            this.config = config;
            return this;
        }
    }

    @PluginFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Index for most recent log file.
     */
    private final int maxFiles;

    private final List<Action> customActions;
    private final boolean stopCustomActionsOnError;
    private volatile String currentFileName;
    private int nextIndex = -1;
    private final PatternProcessor tempCompressedFilePattern;

    /**
     * Constructs a new instance.
     *
     * @param maxFiles The maximum number of files that match the date portion of the pattern to keep.
     * @param customActions custom actions to perform asynchronously after rollover
     * @param stopCustomActionsOnError whether to stop executing asynchronous actions if an error occurs
     * @param tempCompressedFilePatternString File pattern of the working file
     *                                     used during compression, if null no temporary file are used
     */
    private DirectWriteRolloverStrategy(
            final int maxFiles,
            final StrSubstitutor strSubstitutor,
            final Action[] customActions,
            final boolean stopCustomActionsOnError,
            final String tempCompressedFilePatternString,
            final Configuration configuration) {
        super(CompressActionFactoryProvider.newInstance(configuration), strSubstitutor);
        this.maxFiles = maxFiles;
        this.stopCustomActionsOnError = stopCustomActionsOnError;
        this.customActions = customActions == null ? Collections.emptyList() : Arrays.asList(customActions);
        this.tempCompressedFilePattern = tempCompressedFilePatternString != null
                ? new PatternProcessor(configuration, tempCompressedFilePatternString)
                : null;
    }

    public List<Action> getCustomActions() {
        return customActions;
    }

    public int getMaxFiles() {
        return this.maxFiles;
    }

    public boolean isStopCustomActionsOnError() {
        return stopCustomActionsOnError;
    }

    public PatternProcessor getTempCompressedFilePattern() {
        return tempCompressedFilePattern;
    }

    private int purge(final RollingFileManager manager) {
        final SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager);
        LOGGER.debug("Found {} eligible files, max is  {}", eligibleFiles.size(), maxFiles);
        while (eligibleFiles.size() >= maxFiles) {
            try {
                final Integer key = eligibleFiles.firstKey();
                Files.delete(eligibleFiles.get(key));
                eligibleFiles.remove(key);
            } catch (final IOException ioe) {
                LOGGER.error("Unable to delete {}", eligibleFiles.firstKey(), ioe);
                break;
            }
        }
        return eligibleFiles.isEmpty() ? 1 : eligibleFiles.lastKey();
    }

    @Override
    public String getCurrentFileName(final RollingFileManager manager) {
        if (currentFileName == null) {
            final SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager);
            final int fileIndex = eligibleFiles.isEmpty() ? 1 : (nextIndex > 0 ? nextIndex : eligibleFiles.lastKey());
            final StringBuilder buf = new StringBuilder(255);
            // LOG4J2-3339 - Always use the current time for new direct write files.
            manager.getPatternProcessor().setCurrentFileTime(System.currentTimeMillis());
            manager.getPatternProcessor().formatFileName(strSubstitutor, buf, true, fileIndex);
            final int suffixLength = suffixLength(buf.toString());
            currentFileName = suffixLength > 0 ? buf.substring(0, buf.length() - suffixLength) : buf.toString();
        }
        return currentFileName;
    }

    @Override
    public void clearCurrentFileName() {
        currentFileName = null;
    }

    /**
     * Performs the rollover.
     *
     * @param manager The RollingFileManager name for current active log file.
     * @return A RolloverDescription.
     * @throws SecurityException if an error occurs.
     */
    @Override
    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The name of the accessed files is based on a configuration value.")
    public RolloverDescription rollover(final RollingFileManager manager) throws SecurityException {
        LOGGER.debug("Rolling " + currentFileName);
        if (maxFiles < 0) {
            return null;
        }
        final long startNanos = System.nanoTime();
        final int fileIndex = purge(manager);
        if (LOGGER.isTraceEnabled()) {
            final double durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            LOGGER.trace("DirectWriteRolloverStrategy.purge() took {} milliseconds", durationMillis);
        }
        Action compressAction = null;
        final String sourceName = getCurrentFileName(manager);
        String compressedName = sourceName;
        currentFileName = null;
        nextIndex = fileIndex + 1;
        final CompressActionFactory compressActionFactory = manager.getCompressActionFactory();
        if (compressActionFactory != null) {
            compressedName += compressActionFactory.getExtension();
            if (tempCompressedFilePattern != null) {
                final StringBuilder buf = new StringBuilder();
                tempCompressedFilePattern.formatFileName(strSubstitutor, buf, fileIndex);
                final String tmpCompressedName = buf.toString();
                final File tmpCompressedNameFile = new File(tmpCompressedName);
                final File parentFile = tmpCompressedNameFile.getParentFile();
                if (parentFile != null) {
                    parentFile.mkdirs();
                }
                compressAction = new CompositeAction(
                        Arrays.asList(
                                compressActionFactory.createCompressAction(sourceName, tmpCompressedName, Map.of()),
                                new FileRenameAction(tmpCompressedNameFile, new File(compressedName), true)),
                        true);
            } else {
                compressAction = compressActionFactory.createCompressAction(sourceName, compressedName, Map.of());
            }
        }

        if (compressAction != null && manager.isAttributeViewEnabled()) {
            // Propagate POSIX attribute view to compressed file
            // @formatter:off
            final Action posixAttributeViewAction = PosixViewAttributeAction.newBuilder()
                    .setBasePath(compressedName)
                    .setFollowLinks(false)
                    .setMaxDepth(1)
                    .setPathConditions(PathCondition.EMPTY_ARRAY)
                    .setSubst(getStrSubstitutor())
                    .setFilePermissions(manager.getFilePermissions())
                    .setFileOwner(manager.getFileOwner())
                    .setFileGroup(manager.getFileGroup())
                    .build();
            // @formatter:on
            compressAction = new CompositeAction(Arrays.asList(compressAction, posixAttributeViewAction), false);
        }

        final Action asyncAction = merge(compressAction, customActions, stopCustomActionsOnError);
        return new RolloverDescriptionImpl(sourceName, false, null, asyncAction);
    }

    @Override
    public String toString() {
        return "DirectWriteRolloverStrategy(maxFiles=" + maxFiles + ')';
    }
}
