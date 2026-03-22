/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.template.sql.util;

import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.template.sql.util.splitter.AbstractSqlSplitter;
import io.flamingock.template.sql.util.splitter.SqlSplitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SqlSplitterFactory")
class SqlSplitterFactoryTest {

    @AfterEach
    void deregister() {
        SqlSplitterFactory.deregisterSplitter(SqlDialect.MYSQL);
    }

    @Test
    @DisplayName("WHEN a custom splitter is registered for a dialect THEN createForDialect returns that splitter")
    void registeredSplitterOverridesBuiltIn() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        SqlSplitter custom = sql -> Collections.emptyList();
        SqlSplitterFactory.registerSplitter(SqlDialect.MYSQL, () -> custom);

        SqlSplitter result = SqlSplitterFactory.createForDialect(connection);

        assertSame(custom, result, "Custom registered splitter should be returned instead of built-in MySqlSplitter");
    }

    @Test
    @DisplayName("WHEN a custom splitter is deregistered THEN createForDialect reverts to the built-in splitter")
    void deregisteredSplitterReverts() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        SqlSplitter custom = sql -> Collections.emptyList();
        SqlSplitterFactory.registerSplitter(SqlDialect.MYSQL, () -> custom);
        SqlSplitterFactory.deregisterSplitter(SqlDialect.MYSQL);

        SqlSplitter result = SqlSplitterFactory.createForDialect(connection);

        assertNotSame(custom, result, "Built-in splitter should be returned after deregistration");
        assertInstanceOf(AbstractSqlSplitter.class, result, "Reverted splitter should be an AbstractSqlSplitter");
    }
}
