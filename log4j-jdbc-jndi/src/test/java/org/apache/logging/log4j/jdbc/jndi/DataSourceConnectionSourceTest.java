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

import static org.apache.logging.log4j.core.test.TestConstants.JNDI_ENABLE_JDBC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.logging.log4j.core.test.junit.LoggerContextRule;
import org.apache.logging.log4j.jndi.test.junit.JndiRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DataSourceConnectionSourceTest {

    @Parameterized.Parameters(name = "{0}")
    public static Object[][] data() {
        System.setProperty(JNDI_ENABLE_JDBC, "true");
        return new Object[][] {{"java:/comp/env/jdbc/Logging01"}, {"java:/comp/env/jdbc/Logging02"}};
    }

    private static final String CONFIG = "DataSourceConnectionSourceTest.xml";

    @Rule
    public final RuleChain rules;

    private final DataSource dataSource = mock(DataSource.class);
    private final String jndiURL;

    public DataSourceConnectionSourceTest(final String jndiURL) {
        this.rules = RuleChain.outerRule(
                        new JndiRule(DataSourceConnectionSource.JNDI_MANAGER_NAME, jndiURL, dataSource))
                .around(new LoggerContextRule(CONFIG));
        this.jndiURL = jndiURL;
    }

    @Test
    public void testNullJndiName() {
        final DataSourceConnectionSource source = DataSourceConnectionSource.createConnectionSource(null);

        assertNull("The connection source should be null.", source);
    }

    @Test
    public void testEmptyJndiName() {
        final DataSourceConnectionSource source = DataSourceConnectionSource.createConnectionSource("");

        assertNull("The connection source should be null.", source);
    }

    @Test
    public void testNoDataSource() {
        final DataSourceConnectionSource source = DataSourceConnectionSource.createConnectionSource(jndiURL + "123");

        assertNull("The connection source should be null.", source);
    }

    @Test
    public void testDataSource() throws SQLException {
        try (final Connection connection1 = mock(Connection.class);
                final Connection connection2 = mock(Connection.class)) {

            given(dataSource.getConnection()).willReturn(connection1, connection2);

            DataSourceConnectionSource source = DataSourceConnectionSource.createConnectionSource(jndiURL);

            assertNotNull("The connection source should not be null.", source);
            assertEquals(
                    "The toString value is not correct.",
                    "dataSource{ name=" + jndiURL + ", value=" + dataSource + " }",
                    source.toString());
            assertSame("The connection is not correct (1).", connection1, source.getConnection());
            assertSame("The connection is not correct (2).", connection2, source.getConnection());

            source = DataSourceConnectionSource.createConnectionSource(jndiURL.substring(0, jndiURL.length() - 1));

            assertNull("The connection source should be null now.", source);
        }
    }
}
