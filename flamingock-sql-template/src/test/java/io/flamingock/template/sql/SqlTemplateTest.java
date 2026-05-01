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
package io.flamingock.template.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.api.StageType;
import io.flamingock.api.template.wrappers.TemplateString;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.common.core.util.Deserializer;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.store.sql.SqlAuditStore;
import io.flamingock.targetsystem.sql.SqlTargetSystem;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Disabled
class SqlTemplateTest {

    private static final String TEST_TABLE = "test_users";

    private static DataSource dataSource;

    @BeforeAll
    static void beforeAll() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        dataSource = new HikariDataSource(config);
    }

    @AfterEach
    void tearDown() {
        // Cleanup test table if exists
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TEST_TABLE);
                // Also clean Flamingock audit tables
                stmt.execute("DROP TABLE IF EXISTS flamingockAuditLog");
                stmt.execute("DROP TABLE IF EXISTS flamingockLock");
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    @DisplayName("WHEN run a Flamingock's template THEN it runs fine")
    void happyPath() throws Exception {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {

            mocked.when(Deserializer::readMetadataFromFile).thenReturn(new FlamingockMetadata(createPipeline(true, true), null, null));

            SqlTargetSystem sqlTargetSystem = new SqlTargetSystem("sql", dataSource);
            FlamingockFactory.getCommunityBuilder()
                .setAuditStore(SqlAuditStore.from(sqlTargetSystem))
                .addTargetSystem(sqlTargetSystem)
                .build()
                .run();

            // Verify table was created and data inserted
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE)) {

                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }

            // Verify specific data
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM " + TEST_TABLE + " ORDER BY id")) {

                assertTrue(rs.next());
                assertEquals("Admin", rs.getString(1));
                assertTrue(rs.next());
                assertEquals("backup", rs.getString(1));
                assertTrue(rs.next());
                assertEquals("text;with;semi", rs.getString(1));
            }
        }
    }

    @Test
    @DisplayName("WHEN run a Flamingock's template without apply THEN throws FlamingockException")
    void withoutApply() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {

            mocked.when(Deserializer::readMetadataFromFile).thenReturn(new FlamingockMetadata(createPipeline(false, true), null, null));

            assertThrows(FlamingockException.class, () -> {
                SqlTargetSystem sqlTargetSystem = new SqlTargetSystem("sql", dataSource);
                FlamingockFactory.getCommunityBuilder()
                    .setAuditStore(SqlAuditStore.from(sqlTargetSystem))
                    .addTargetSystem(sqlTargetSystem)
                    .build()
                    .run();
            });
        }
    }

    @Test
    @DisplayName("P0-B: WHEN rollbackPayload is set THEN rollback() executes the rollback SQL")
    void rollbackExecution() throws Exception {
        String rollbackSql = "DROP TABLE " + TEST_TABLE + ";";

        SqlTemplate template = new SqlTemplate();
        // splitStatements=false avoids dialect detection (getMetaData) on the mocked connection
        SqlTemplateConfig config = new SqlTemplateConfig();
        config.setSplitStatements(false);
        template.setConfiguration(config);
        template.setRollbackPayload(new TemplateString(rollbackSql));

        Connection mockConnection = Mockito.mock(Connection.class);
        Statement mockStatement = Mockito.mock(Statement.class);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(anyString())).thenReturn(false);

        template.rollback(mockConnection);

        verify(mockStatement, times(1)).execute(rollbackSql.trim());
    }

    @Test
    @DisplayName("P0-C: WHEN rollbackPayload is null THEN rollback() does not execute any SQL")
    void rollbackNullPayloadGuard() throws Exception {
        SqlTemplate template = new SqlTemplate();
        // rollbackPayload is null by default — no setRollbackPayload() call

        Connection mockConnection = Mockito.mock(Connection.class);
        Statement mockStatement = Mockito.mock(Statement.class);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        template.rollback(mockConnection);

        verify(mockStatement, never()).execute(anyString());
    }

    @Test
    @DisplayName("P0-C: WHEN rollbackPayload value is empty string THEN rollback() does not execute any SQL")
    void rollbackEmptyPayloadGuard() throws Exception {
        SqlTemplate template = new SqlTemplate();
        template.setRollbackPayload(new TemplateString(""));

        Connection mockConnection = Mockito.mock(Connection.class);
        Statement mockStatement = Mockito.mock(Statement.class);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        template.rollback(mockConnection);

        verify(mockStatement, never()).execute(anyString());
    }

    @Test
    @DisplayName("WHEN rollback SQL is executed against a real database THEN the database reflects the rollback")
    void rollbackExecutesAgainstRealDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // Apply: create the table via SqlTemplate against a real H2 connection
            SqlTemplate applyTemplate = new SqlTemplate();
            SqlTemplateConfig config = new SqlTemplateConfig();
            config.setSplitStatements(true);
            applyTemplate.setConfiguration(config);
            applyTemplate.setApplyPayload(new TemplateString(
                    "CREATE TABLE " + TEST_TABLE + " (id INT PRIMARY KEY, name VARCHAR(50))"));
            applyTemplate.apply(conn);

            assertTrue(tableExists(conn, TEST_TABLE), "Table should exist after apply");

            // Rollback: drop the table
            SqlTemplate rollbackTemplate = new SqlTemplate();
            rollbackTemplate.setConfiguration(config);
            rollbackTemplate.setRollbackPayload(new TemplateString("DROP TABLE " + TEST_TABLE));
            rollbackTemplate.rollback(conn);

            assertFalse(tableExists(conn, TEST_TABLE), "Table should not exist after rollback");
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE upper(table_name) = '"
                             + tableName.toUpperCase() + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    @Test
    @DisplayName("WHEN run a Flamingock's template without rollback THEN runs fine (rollback is optional)")
    void withoutRollback() throws Exception {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {

            mocked.when(Deserializer::readMetadataFromFile).thenReturn(new FlamingockMetadata(createPipeline(true, false), null, null));

            SqlTargetSystem sqlTargetSystem = new SqlTargetSystem("sql", dataSource);
            FlamingockFactory.getCommunityBuilder()
                .setAuditStore(SqlAuditStore.from(sqlTargetSystem))
                .addTargetSystem(sqlTargetSystem)
                .build()
                .run();

            // Verify table was created and data inserted
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE)) {

                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }

            // Verify specific data
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM " + TEST_TABLE + " ORDER BY id")) {

                assertTrue(rs.next());
                assertEquals("Admin", rs.getString(1));
                assertTrue(rs.next());
                assertEquals("backup", rs.getString(1));
                assertTrue(rs.next());
                assertEquals("text;with;semi", rs.getString(1));
            }
        }
    }

    private PreviewPipeline createPipeline(Boolean apply, Boolean rollback) {

        String applyString = null;
        String rollbackString = null;

        if (apply != null && apply) {
            applyString = "INSERT INTO test_users (id, name, role) VALUES (1, 'Admin', 'superuser');\n" +
                "INSERT INTO test_users (id, name, role) VALUES (2, 'backup', 'readonly');\n" +
                "INSERT INTO test_users (id, name, role) VALUES (3, 'text;with;semi', 'user');";
        }

        if (rollback != null && rollback) {
            rollbackString = "DELETE FROM test_users WHERE id IN (1, 2, 3);";
        }


        TemplatePreviewChange change1 = new TemplatePreviewChange(
            "create-table.yaml",
            "create-test-users-table",
            "0001",
            "test-author",
            "sql-template",
            Collections.emptyList(),
            true,
            false,
            false,
            null,
            "CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100), role VARCHAR(50));",
            "DROP TABLE test_users;",
            null,
            TargetSystemDescriptor.fromId("sql"),
            RecoveryDescriptor.getDefault()
        );

        TemplatePreviewChange change2 = new TemplatePreviewChange(
            "insert-users.yaml",
            "insert-test-users",
            "0002",
            "test-author",
            "sql-template",
            Collections.emptyList(),
            true,
            false,
            false,
            null,
            applyString,
            rollbackString,
            null,
            TargetSystemDescriptor.fromId("sql"),
            RecoveryDescriptor.getDefault()
        );

        PreviewStage stage = new PreviewStage(
            "test-stage",
            StageType.DEFAULT,
            "Test stage",
            null,
            null,
            Arrays.asList(change1, change2)
        );

        return new PreviewPipeline(Collections.singletonList(stage));
    }
}
