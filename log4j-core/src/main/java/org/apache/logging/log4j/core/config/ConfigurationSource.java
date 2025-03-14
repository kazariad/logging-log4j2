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
package org.apache.logging.log4j.core.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.impl.CoreProperties.AuthenticationProperties;
import org.apache.logging.log4j.core.net.ssl.LaxHostnameVerifier;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfigurationFactory;
import org.apache.logging.log4j.core.util.AuthorizationProvider;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.core.util.Source;
import org.apache.logging.log4j.kit.env.PropertyEnvironment;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the source for the logging configuration.
 */
@NullMarked
public class ConfigurationSource {

    private static final Logger LOGGER = StatusLogger.getLogger();

    /**
     * ConfigurationSource to use with Configurations that do not require a "real" configuration source.
     */
    public static final ConfigurationSource NULL_SOURCE = new ConfigurationSource(new byte[0], null, 0);
    /**
     * ConfigurationSource to use with {@link org.apache.logging.log4j.core.config.composite.CompositeConfiguration}.
     */
    public static final ConfigurationSource COMPOSITE_SOURCE = new ConfigurationSource(new byte[0], null, 0);

    private static final String HTTPS = "https";

    private final InputStream stream;
    private volatile byte @Nullable [] data;
    private final @Nullable Source source;
    // The initial modification time when the `ConfigurationSource` is created
    private final long initialLastModified;
    // Set when the configuration has been updated so reset can use it for the next lastModified timestamp.
    private volatile long currentLastModified;

    /**
     * Constructs a new {@code ConfigurationSource} with the specified input stream that originated from the specified
     * file.
     *
     * @param stream the input stream, the caller is responsible for closing this resource.
     * @param file the file where the input stream originated
     */
    public ConfigurationSource(final InputStream stream, final File file) {
        this.stream = Objects.requireNonNull(stream, "stream is null");
        this.data = null;
        this.source = new Source(file);
        long modified = 0;
        try {
            modified = file.lastModified();
        } catch (final Exception ex) {
            // There is a problem with the file. It will be handled somewhere else.
        }
        this.currentLastModified = this.initialLastModified = modified;
    }

    /**
     * Constructs a new {@code ConfigurationSource} with the specified input stream that originated from the specified
     * path.
     *
     * @param stream the input stream, the caller is responsible for closing this resource.
     * @param path the path where the input stream originated.
     */
    public ConfigurationSource(final InputStream stream, final Path path) {
        this.stream = Objects.requireNonNull(stream, "stream is null");
        this.data = null;
        this.source = new Source(path);
        long modified = 0;
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ex) {
            // There is a problem with the file. It will be handled somewhere else.
        }
        this.currentLastModified = this.initialLastModified = modified;
    }

    /**
     * Constructs a new {@code ConfigurationSource} with the specified input stream that originated from the specified
     * url.
     *
     * @param stream the input stream
     * @param url the URL where the input stream originated
     */
    public ConfigurationSource(final InputStream stream, final URL url) {
        this(stream, url, 0L);
    }

    /**
     * Constructs a new {@code ConfigurationSource} with the specified input stream that originated from the specified
     * url.
     *
     * @param stream the input stream, the caller is responsible for closing this resource.
     * @param url the URL where the input stream originated
     * @param lastModified when the source was last modified.
     */
    public ConfigurationSource(final InputStream stream, final URL url, final long lastModified) {
        this.stream = Objects.requireNonNull(stream, "stream is null");
        this.data = null;
        this.currentLastModified = this.initialLastModified = lastModified;
        this.source = new Source(url);
    }

    /**
     * Constructs a new {@code ConfigurationSource} with the specified input stream. Since the stream is the only source
     * of data, this constructor makes a copy of the stream contents.
     *
     * @param stream the input stream, the caller is responsible for closing this resource.
     * @throws IOException if an exception occurred reading from the specified stream
     */
    public ConfigurationSource(final InputStream stream) throws IOException {
        this(stream.readAllBytes(), null, 0);
    }

    public ConfigurationSource(final Source source, final byte[] data, final long lastModified) {
        Objects.requireNonNull(source, "source is null");
        this.data = Objects.requireNonNull(data, "data is null");
        this.stream = new ByteArrayInputStream(data);
        this.currentLastModified = this.initialLastModified = lastModified;
        this.source = source;
    }

    private ConfigurationSource(byte[] data, @Nullable Source source, long lastModified) {
        this(data, source, new ByteArrayInputStream(data), lastModified);
    }

    /**
     * @throws NullPointerException if both {@code stream} and {@code data} are {@code null}.
     */
    private ConfigurationSource(
            byte @Nullable [] data, @Nullable Source source, InputStream stream, long lastModified) {
        if (data == null && source == null) {
            throw new NullPointerException("both `data` and `source` are null");
        }
        this.stream = stream;
        this.data = data;
        this.source = source;
        this.currentLastModified = this.initialLastModified = lastModified;
    }

    /**
     * Returns the file configuration source, or {@code null} if this configuration source is based on an URL or has
     * neither a file nor an URL.
     *
     * @return the configuration source file, or {@code null}
     */
    public @Nullable File getFile() {
        return source == null ? null : source.getFile();
    }

    /**
     * Returns the configuration source URL, or {@code null} if this configuration source is based on a file or has
     * neither a file nor an URL.
     *
     * @return the configuration source URL, or {@code null}
     */
    public @Nullable URL getURL() {
        return source == null ? null : source.getURL();
    }

    public void setData(final byte[] data) {
        this.data = data;
    }

    /**
     * Updates the last known modification time of the resource.
     *
     * @param currentLastModified The modification time of the resource in millis.
     */
    public void setModifiedMillis(final long currentLastModified) {
        this.currentLastModified = currentLastModified;
    }

    /**
     * Returns a URI representing the configuration resource or null if it cannot be determined.
     * @return The URI.
     */
    public @Nullable URI getURI() {
        return source == null ? null : source.getURI();
    }

    /**
     * Returns the last modification time known when the {@code ConfigurationSource} was created.
     *
     * @return the last modified time of the resource.
     */
    public long getLastModified() {
        return initialLastModified;
    }

    /**
     * Returns a string describing the configuration source file or URL, or {@code null} if this configuration source
     * has neither a file nor an URL.
     *
     * @return a string describing the configuration source file or URL, or {@code null}
     */
    public @Nullable String getLocation() {
        return source == null ? null : source.getLocation();
    }

    /**
     * Returns the input stream that this configuration source was constructed with.
     *
     * @return the input stream that this configuration source was constructed with.
     */
    public InputStream getInputStream() {
        return stream;
    }

    /**
     * Returns a new {@code ConfigurationSource} whose input stream is reset to the beginning.
     *
     * @return a new {@code ConfigurationSource}
     * @throws IOException if a problem occurred while opening the new input stream
     */
    public @Nullable ConfigurationSource resetInputStream() throws IOException {
        byte[] data = this.data;
        if (source != null && data != null) {
            return new ConfigurationSource(source, data, currentLastModified);
        }
        File file = getFile();
        if (file != null) {
            return new ConfigurationSource(Files.newInputStream(file.toPath()), getFile());
        }
        URL url = getURL();
        if (url != null && data != null) {
            // Creates a ConfigurationSource without accessing the URL since the data was provided.
            return new ConfigurationSource(data, new Source(url), currentLastModified);
        }
        URI uri = getURI();
        if (uri != null) {
            return fromUri(uri);
        }
        return data == null ? null : new ConfigurationSource(data, null, currentLastModified);
    }

    @Override
    public String toString() {
        if (source != null) {
            return source.getLocation();
        }
        if (this == NULL_SOURCE) {
            return "NULL_SOURCE";
        }
        if (this == COMPOSITE_SOURCE) {
            return "COMPOSITE_SOURCE";
        }
        byte[] data = this.data;
        final int length = data == null ? -1 : data.length;
        return "stream (" + length + " bytes, unknown location)";
    }

    /**
     * Loads the configuration from a URI.
     * @param configLocation A URI representing the location of the configuration.
     * @return The ConfigurationSource for the configuration or {@code null}.
     */
    public static @Nullable ConfigurationSource fromUri(final URI configLocation) {
        final File configFile = FileUtils.fileFromUri(configLocation);
        if (configFile != null && configFile.exists() && configFile.canRead()) {
            try {
                return new ConfigurationSource(new FileInputStream(configFile), configFile);
            } catch (final FileNotFoundException ex) {
                ConfigurationFactory.LOGGER.error("Cannot locate file {}", configLocation.getPath(), ex);
            }
        }
        if (ConfigurationFactory.isClassLoaderUri(configLocation)) {
            final ClassLoader loader = LoaderUtil.getThreadContextClassLoader();
            final String path = ConfigurationFactory.extractClassLoaderUriPath(configLocation);
            return fromResource(path, loader);
        }
        if (!configLocation.isAbsolute()) { // LOG4J2-704 avoid confusing error message thrown by uri.toURL()
            ConfigurationFactory.LOGGER.error(
                    "File not found in file system or classpath: {}", configLocation.toString());
            return null;
        }
        try {
            return getConfigurationSource(configLocation.toURL());
        } catch (final MalformedURLException ex) {
            ConfigurationFactory.LOGGER.error("Invalid URL {}", configLocation.toString(), ex);
        }
        return null;
    }

    /**
     * Retrieves the configuration via the ClassLoader.
     * @param resource The resource to load.
     * @param loader The default ClassLoader to use.
     * @return The ConfigurationSource for the configuration.
     */
    public static @Nullable ConfigurationSource fromResource(String resource, @Nullable ClassLoader loader) {
        final URL url = Loader.getResource(resource, loader);
        return url == null ? null : getConfigurationSource(url);
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The name of the accessed files is based on a configuration value.")
    private static @Nullable ConfigurationSource getConfigurationSource(final URL url) {
        try {
            final URLConnection urlConnection = url.openConnection();
            // A "jar:" URL file remains open after the stream is closed, so do not cache it.
            urlConnection.setUseCaches(false);
            final PropertyEnvironment env = PropertyEnvironment.getGlobal();
            final AuthorizationProvider provider =
                    AuthorizationProvider.getAuthorizationProvider(env.getProperty(AuthenticationProperties.class));
            provider.addAuthorization(urlConnection);
            if (url.getProtocol().equals(HTTPS)) {
                final SslConfiguration sslConfiguration = SslConfigurationFactory.getSslConfiguration(env);
                if (sslConfiguration != null) {
                    ((HttpsURLConnection) urlConnection)
                            .setSSLSocketFactory(
                                    sslConfiguration.getSslContext().getSocketFactory());
                    if (!sslConfiguration.isVerifyHostName()) {
                        ((HttpsURLConnection) urlConnection).setHostnameVerifier(LaxHostnameVerifier.INSTANCE);
                    }
                }
            }
            final File file = FileUtils.fileFromUri(url.toURI());
            try {
                if (file != null) {
                    return new ConfigurationSource(urlConnection.getInputStream(), FileUtils.fileFromUri(url.toURI()));
                } else if (urlConnection instanceof JarURLConnection) {
                    // Work around https://bugs.openjdk.java.net/browse/JDK-6956385.
                    URL jarFileUrl = ((JarURLConnection) urlConnection).getJarFileURL();
                    File jarFile = new File(jarFileUrl.getFile());
                    long lastModified = jarFile.lastModified();
                    return new ConfigurationSource(urlConnection.getInputStream(), url, lastModified);
                } else {
                    return new ConfigurationSource(
                            urlConnection.getInputStream(), url, urlConnection.getLastModified());
                }
            } catch (final FileNotFoundException ex) {
                ConfigurationFactory.LOGGER.info("Unable to locate file {}, ignoring.", url.toString());
                return null;
            }
        } catch (final IOException | URISyntaxException ex) {
            ConfigurationFactory.LOGGER.warn(
                    "Error accessing {} due to {}, ignoring.", url.toString(), ex.getMessage());
            return null;
        }
    }
}
