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
package org.apache.logging.log4j.jdbc.jndi;

import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.jdbc.appender.AbstractConnectionSource;
import org.apache.logging.log4j.jndi.JndiManager;
import org.apache.logging.log4j.plugins.Configurable;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginAttribute;
import org.apache.logging.log4j.plugins.PluginFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

/**
 * A {@link org.apache.logging.log4j.jdbc.appender.JdbcAppender} connection source that uses a {@link DataSource} to connect to the database.
 */
@Configurable(elementType = "connectionSource", printObject = true)
@Plugin("DataSource")
public final class DataSourceConnectionSource extends AbstractConnectionSource {
    private static final String DOC_URL =
            "https://logging.staged.apache.org/log4j/3.x/manual/systemproperties.html#properties-jndi-support";
    static final String JNDI_MANAGER_NAME = "org.apache.logging.log4j.jdbc.jndi.DataSourceConnectionSource";
    private static final Logger LOGGER = StatusLogger.getLogger();

    private final DataSource dataSource;
    private final String description;

    private DataSourceConnectionSource(final String dataSourceName, final DataSource dataSource) {
        this.dataSource = dataSource;
        this.description = "dataSource{ name=" + dataSourceName + ", value=" + dataSource + " }";
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }

    @Override
    public String toString() {
        return this.description;
    }

    /**
     * Factory method for creating a connection source within the plugin manager.
     *
     * @param jndiName The full JNDI path where the data source is bound. Must start with java:/comp/env or environment-equivalent.
     * @return the created connection source.
     */
    @PluginFactory
    public static DataSourceConnectionSource createConnectionSource(@PluginAttribute final String jndiName) {
        if (!JndiManager.isJndiJdbcEnabled()) {
            LOGGER.error(
                    "JNDI must be enabled by setting `log4j.jndi.enableJdbc=\"true\"`\nSee {} for more details.",
                    DOC_URL);
            return null;
        }
        if (Strings.isEmpty(jndiName)) {
            LOGGER.error("No JNDI name provided.");
            return null;
        }

        try {
            final DataSource dataSource =
                    JndiManager.getDefaultManager(JNDI_MANAGER_NAME).lookup(jndiName);
            if (dataSource != null) {
                return new DataSourceConnectionSource(jndiName, dataSource);
            }
            LOGGER.error("Failed to retrieve JNDI data source with name {}.", jndiName);
        } catch (final NamingException e) {
            LOGGER.error("Failed to retrieve JNDI data source with name {}.", jndiName, e);
        }
        return null;
    }
}
