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
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.plugins.Configurable;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.plugins.PluginElement;
import org.apache.logging.log4j.plugins.PluginFactory;

/**
 * When rolling over, <code>DefaultRolloverStrategy</code> renames files according to an algorithm as described below.
 *
 * <p>
 * The DefaultRolloverStrategy is a combination of a time-based policy and a fixed-window policy. When the file name
 * pattern contains a date format then the rollover time interval will be used to calculate the time to use in the file
 * pattern. When the file pattern contains an integer replacement token one of the counting techniques will be used.
 * </p>
 * <p>
 * When the ascending attribute is set to true (the default) then the counter will be incremented and the current log
 * file will be renamed to include the counter value. If the counter hits the maximum value then the oldest file, which
 * will have the smallest counter, will be deleted, all other files will be renamed to have their counter decremented
 * and then the current file will be renamed to have the maximum counter value. Note that with this counting strategy
 * specifying a large maximum value may entirely avoid renaming files.
 * </p>
 * <p>
 * When the ascending attribute is false, then the "normal" fixed-window strategy will be used.
 * </p>
 * <p>
 * Let <em>max</em> and <em>min</em> represent the values of respectively the <b>MaxIndex</b> and <b>MinIndex</b>
 * options. Let "foo.log" be the value of the <b>ActiveFile</b> option and "foo.%i.log" the value of
 * <b>FileNamePattern</b>. Then, when rolling over, the file <code>foo.<em>max</em>.log</code> will be deleted, the file
 * <code>foo.<em>max-1</em>.log</code> will be renamed as <code>foo.<em>max</em>.log</code>, the file
 * <code>foo.<em>max-2</em>.log</code> renamed as <code>foo.<em>max-1</em>.log</code>, and so on, the file
 * <code>foo.<em>min+1</em>.log</code> renamed as <code>foo.<em>min+2</em>.log</code>. Lastly, the active file
 * <code>foo.log</code> will be renamed as <code>foo.<em>min</em>.log</code> and a new active file name
 * <code>foo.log</code> will be created.
 * </p>
 * <p>
 * Given that this rollover algorithm requires as many file renaming operations as the window size, large window sizes
 * are discouraged.
 * </p>
 */
@Configurable(printObject = true)
@Plugin
public class DefaultRolloverStrategy extends AbstractRolloverStrategy {

    private static final int MIN_WINDOW_SIZE = 1;
    private static final int DEFAULT_WINDOW_SIZE = 7;

    /**
     * Builds DefaultRolloverStrategy instances.
     */
    public static class Builder implements org.apache.logging.log4j.plugins.util.Builder<DefaultRolloverStrategy> {
        @PluginBuilderAttribute("max")
        private String max;

        @PluginBuilderAttribute("min")
        private String min;

        @PluginBuilderAttribute("fileIndex")
        private String fileIndex;

        @PluginElement("Actions")
        private Action[] customActions;

        @PluginBuilderAttribute(value = "stopCustomActionsOnError")
        private boolean stopCustomActionsOnError = true;

        @PluginBuilderAttribute(value = "tempCompressedFilePattern")
        private String tempCompressedFilePattern;

        @PluginConfiguration
        private Configuration config;

        @Override
        public DefaultRolloverStrategy build() {
            int minIndex;
            int maxIndex;
            final boolean useMax;

            if (fileIndex != null && fileIndex.equalsIgnoreCase("nomax")) {
                minIndex = Integer.MIN_VALUE;
                maxIndex = Integer.MAX_VALUE;
                useMax = false;
            } else {
                useMax = fileIndex == null || fileIndex.equalsIgnoreCase("max");
                minIndex = MIN_WINDOW_SIZE;
                if (min != null) {
                    minIndex = Integer.parseInt(min.trim());
                    if (minIndex < 1) {
                        LOGGER.error("Minimum window size too small. Limited to " + MIN_WINDOW_SIZE);
                        minIndex = MIN_WINDOW_SIZE;
                    }
                }
                maxIndex = DEFAULT_WINDOW_SIZE;
                if (max != null) {
                    maxIndex = Integer.parseInt(max.trim());
                    if (maxIndex < minIndex) {
                        maxIndex = Math.max(minIndex, DEFAULT_WINDOW_SIZE);
                        LOGGER.error("Maximum window size must be greater than the minimum windows size. Set to "
                                + maxIndex);
                    }
                }
            }

            // The config object can be null only in tests
            final Configuration configuration = config != null ? config : new NullConfiguration();
            final StrSubstitutor nonNullStrSubstitutor = configuration.getStrSubstitutor();
            return new DefaultRolloverStrategy(
                    minIndex,
                    maxIndex,
                    useMax,
                    nonNullStrSubstitutor,
                    customActions,
                    stopCustomActionsOnError,
                    tempCompressedFilePattern,
                    configuration);
        }

        public String getMax() {
            return max;
        }

        /**
         * Defines the maximum number of files to keep.
         *
         * @param max The maximum number of files to keep.
         * @return This builder for chaining convenience
         */
        public Builder setMax(final String max) {
            this.max = max;
            return this;
        }

        public String getMin() {
            return min;
        }

        /**
         * Defines the minimum number of files to keep.
         *
         * @param min The minimum number of files to keep.
         * @return This builder for chaining convenience
         */
        public Builder setMin(final String min) {
            this.min = min;
            return this;
        }

        public String getFileIndex() {
            return fileIndex;
        }

        /**
         * Defines the file index for rolling strategy.
         *
         * @param fileIndex If set to "max" (the default), files with a higher index will be newer than files with a smaller
         *            index. If set to "min", file renaming and the counter will follow the Fixed Window strategy.
         * @return This builder for chaining convenience
         */
        public Builder setFileIndex(final String fileIndex) {
            this.fileIndex = fileIndex;
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
     * Index for oldest retained log file.
     */
    private final int maxIndex;

    /**
     * Index for most recent log file.
     */
    private final int minIndex;

    private final boolean useMax;
    private final List<Action> customActions;
    private final boolean stopCustomActionsOnError;
    private final PatternProcessor tempCompressedFilePattern;

    /**
     * Constructs a new instance.
     *
     * @param minIndex The minimum index.
     * @param maxIndex The maximum index.
     * @param customActions custom actions to perform asynchronously after rollover
     * @param stopCustomActionsOnError whether to stop executing asynchronous actions if an error occurs
     * @param tempCompressedFilePatternString File pattern of the working file
     *                                     used during compression, if null no temporary file are used
     */
    private DefaultRolloverStrategy(
            final int minIndex,
            final int maxIndex,
            final boolean useMax,
            final StrSubstitutor strSubstitutor,
            final Action[] customActions,
            final boolean stopCustomActionsOnError,
            final String tempCompressedFilePatternString,
            final Configuration configuration) {
        super(CompressActionFactoryProvider.newInstance(configuration), strSubstitutor);
        this.minIndex = minIndex;
        this.maxIndex = maxIndex;
        this.useMax = useMax;
        this.stopCustomActionsOnError = stopCustomActionsOnError;
        this.customActions = customActions == null ? Collections.emptyList() : Arrays.asList(customActions);
        this.tempCompressedFilePattern = tempCompressedFilePatternString != null
                ? new PatternProcessor(configuration, tempCompressedFilePatternString)
                : null;
    }

    public List<Action> getCustomActions() {
        return customActions;
    }

    public int getMaxIndex() {
        return this.maxIndex;
    }

    public int getMinIndex() {
        return this.minIndex;
    }

    public boolean isStopCustomActionsOnError() {
        return stopCustomActionsOnError;
    }

    public boolean isUseMax() {
        return useMax;
    }

    public PatternProcessor getTempCompressedFilePattern() {
        return tempCompressedFilePattern;
    }

    private int purge(final int lowIndex, final int highIndex, final RollingFileManager manager) {
        return useMax ? purgeAscending(lowIndex, highIndex, manager) : purgeDescending(lowIndex, highIndex, manager);
    }

    /**
     * Purges and renames old log files in preparation for rollover. The oldest file will have the smallest index, the
     * newest the highest.
     *
     * @param lowIndex low index. Log file associated with low index will be deleted if needed.
     * @param highIndex high index.
     * @param manager The RollingFileManager
     * @return true if purge was successful and rollover should be attempted.
     */
    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The name of the accessed files is based on a configuration value.")
    private int purgeAscending(final int lowIndex, final int highIndex, final RollingFileManager manager) {
        final SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager);
        final int maxFiles = highIndex - lowIndex + 1;

        LOGGER.debug("Eligible files: {}", eligibleFiles);
        boolean renameFiles = !eligibleFiles.isEmpty() && eligibleFiles.lastKey() >= maxIndex;
        while (eligibleFiles.size() >= maxFiles) {
            try {
                LOGGER.debug("Eligible files: {}", eligibleFiles);
                final Integer key = eligibleFiles.firstKey();
                LOGGER.debug("Deleting {}", eligibleFiles.get(key).toFile().getAbsolutePath());
                Files.delete(eligibleFiles.get(key));
                eligibleFiles.remove(key);
                renameFiles = true;
            } catch (final IOException ioe) {
                LOGGER.error("Unable to delete {}, {}", eligibleFiles.firstKey(), ioe.getMessage(), ioe);
                break;
            }
        }
        final StringBuilder buf = new StringBuilder();
        if (renameFiles) {
            for (final Map.Entry<Integer, Path> entry : eligibleFiles.entrySet()) {
                buf.setLength(0);
                // LOG4J2-531: directory scan & rollover must use same format
                manager.getPatternProcessor().formatFileName(strSubstitutor, buf, entry.getKey() - 1);
                final String currentName = entry.getValue().toFile().getName();
                String renameTo = buf.toString();
                final int suffixLength = suffixLength(renameTo);
                if (suffixLength > 0 && suffixLength(currentName) == 0) {
                    renameTo = renameTo.substring(0, renameTo.length() - suffixLength);
                }
                final Action action = new FileRenameAction(entry.getValue().toFile(), new File(renameTo), true);
                try {
                    LOGGER.debug("DefaultRolloverStrategy.purgeAscending executing {}", action);
                    if (!action.execute()) {
                        return -1;
                    }
                } catch (final Exception ex) {
                    LOGGER.warn("Exception during purge in RollingFileAppender", ex);
                    return -1;
                }
            }
        }

        return eligibleFiles.isEmpty()
                ? lowIndex
                : (eligibleFiles.lastKey() < highIndex ? eligibleFiles.lastKey() + 1 : highIndex);
    }

    /**
     * Purges and renames old log files in preparation for rollover. The newest file will have the smallest index, the
     * oldest will have the highest.
     *
     * @param lowIndex low index
     * @param highIndex high index. Log file associated with high index will be deleted if needed.
     * @param manager The RollingFileManager
     * @return true if purge was successful and rollover should be attempted.
     */
    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The name of the accessed files is based on a configuration value.")
    private int purgeDescending(final int lowIndex, final int highIndex, final RollingFileManager manager) {
        // Retrieve the files in descending order, so the highest key will be first.
        final SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager, false);
        final int maxFiles = highIndex - lowIndex + 1;
        LOGGER.debug("Eligible files: {}", eligibleFiles);

        while (eligibleFiles.size() >= maxFiles) {
            try {
                final Integer key = eligibleFiles.firstKey();
                LOGGER.debug("Deleting {}", eligibleFiles.get(key).toFile().getAbsolutePath());
                Files.delete(eligibleFiles.get(key));
                eligibleFiles.remove(key);
            } catch (final IOException ioe) {
                LOGGER.error("Unable to delete {}, {}", eligibleFiles.firstKey(), ioe.getMessage(), ioe);
                break;
            }
        }
        final StringBuilder buf = new StringBuilder();
        for (final Map.Entry<Integer, Path> entry : eligibleFiles.entrySet()) {
            buf.setLength(0);
            // LOG4J2-531: directory scan & rollover must use same format
            manager.getPatternProcessor().formatFileName(strSubstitutor, buf, entry.getKey() + 1);
            final String currentName = entry.getValue().toFile().getName();
            String renameTo = buf.toString();
            final int suffixLength = suffixLength(renameTo);
            if (suffixLength > 0 && suffixLength(currentName) == 0) {
                renameTo = renameTo.substring(0, renameTo.length() - suffixLength);
            }
            final Action action = new FileRenameAction(entry.getValue().toFile(), new File(renameTo), true);
            try {
                LOGGER.debug("DefaultRolloverStrategy.purgeDescending executing {}", action);
                if (!action.execute()) {
                    return -1;
                }
            } catch (final Exception ex) {
                LOGGER.warn("Exception during purge in RollingFileAppender", ex);
                return -1;
            }
        }

        return lowIndex;
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
        final int fileIndex;
        final StringBuilder buf = new StringBuilder(255);
        if (minIndex == Integer.MIN_VALUE) {
            final SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager);
            fileIndex = eligibleFiles.isEmpty() ? 1 : eligibleFiles.lastKey() + 1;
            manager.getPatternProcessor().formatFileName(strSubstitutor, buf, fileIndex);
        } else {
            if (maxIndex < 0) {
                return null;
            }
            final long startNanos = System.nanoTime();
            fileIndex = purge(minIndex, maxIndex, manager);
            if (fileIndex < 0) {
                return null;
            }
            manager.getPatternProcessor().formatFileName(strSubstitutor, buf, fileIndex);
            if (LOGGER.isTraceEnabled()) {
                final double durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                LOGGER.trace("DefaultRolloverStrategy.purge() took {} milliseconds", durationMillis);
            }
        }

        final String currentFileName = manager.getFileName();

        String renameTo = buf.toString();
        final String compressedName = renameTo;
        Action compressAction = null;

        final CompressActionFactory compressActionFactory = manager.getCompressActionFactory();
        if (compressActionFactory != null) {
            final File renameToFile = new File(renameTo);
            // Without dot + extension
            renameTo = renameTo.substring(
                    0, renameTo.length() - compressActionFactory.getExtension().length());
            if (tempCompressedFilePattern != null) {
                buf.delete(0, buf.length());
                tempCompressedFilePattern.formatFileName(strSubstitutor, buf, fileIndex);
                final String tmpCompressedName = buf.toString();
                final File tmpCompressedNameFile = new File(tmpCompressedName);
                final File parentFile = tmpCompressedNameFile.getParentFile();
                if (parentFile != null) {
                    parentFile.mkdirs();
                }
                compressAction = new CompositeAction(
                        Arrays.asList(
                                compressActionFactory.createCompressAction(renameTo, tmpCompressedName, Map.of()),
                                new FileRenameAction(tmpCompressedNameFile, renameToFile, true)),
                        true);
            } else {
                compressAction = compressActionFactory.createCompressAction(renameTo, compressedName, Map.of());
            }
        }

        if (currentFileName.equals(renameTo)) {
            LOGGER.warn("Attempt to rename file {} to itself will be ignored", currentFileName);
            return new RolloverDescriptionImpl(currentFileName, false, null, null);
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

        final FileRenameAction renameAction =
                new FileRenameAction(new File(currentFileName), new File(renameTo), manager.isRenameEmptyFiles());

        final Action asyncAction = merge(compressAction, customActions, stopCustomActionsOnError);
        return new RolloverDescriptionImpl(currentFileName, false, renameAction, asyncAction);
    }

    @Override
    public String toString() {
        return "DefaultRolloverStrategy(min=" + minIndex + ", max=" + maxIndex + ", useMax=" + useMax + ")";
    }
}
