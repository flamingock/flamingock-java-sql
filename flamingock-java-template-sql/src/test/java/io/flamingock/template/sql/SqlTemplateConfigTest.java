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

import io.flamingock.api.template.wrappers.TemplateString;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SqlTemplateConfigTest {

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
    @DisplayName("WHEN splitStatements=false THEN Statement.execute() is called exactly once with full unsplit SQL")
    void splitStatementsFalseCallsExecuteOnce() throws Exception {
        String multiStatementSql = "INSERT INTO t VALUES (1);\nINSERT INTO t VALUES (2);\nINSERT INTO t VALUES (3);";

        SqlTemplate template = new SqlTemplate();
        SqlTemplateConfig config = new SqlTemplateConfig();
        config.setSplitStatements(false);
        template.setConfiguration(config);
        template.setApplyPayload(new TemplateString(multiStatementSql));

        Connection mockConnection = Mockito.mock(Connection.class);
        Statement mockStatement = Mockito.mock(Statement.class);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(anyString())).thenReturn(false);

        template.apply(mockConnection);

        // With splitStatements=false, the full SQL (trimmed) is passed to execute() in one call
        verify(mockStatement, times(1)).execute(multiStatementSql.trim());
        verify(mockStatement, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("WHEN run with splitStatements=false and multiple statements THEN succeeds (H2 supports multi-statement execute)")
    void noSplitting() throws Exception {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {

            SqlTemplateConfig config = new SqlTemplateConfig();
            config.setSplitStatements(false);

            mocked.when(Deserializer::readMetadataFromFile).thenReturn(new FlamingockMetadata(createPipeline(config), null, null));

            SqlTargetSystem sqlTargetSystem = new SqlTargetSystem("sql", dataSource);
            FlamingockFactory.getCommunityBuilder()
                .setAuditStore(SqlAuditStore.from(sqlTargetSystem))
                .addTargetSystem(sqlTargetSystem)
                .build()
                .run();

            // Verify table was created and data inserted (H2 2.x supports multi-statement execute())
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE)) {

                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }
        }
    }

    private PreviewPipeline createPipeline(SqlTemplateConfig config) {

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
            config,
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
            config,
            "INSERT INTO test_users (id, name, role) VALUES (1, 'Admin', 'superuser');\n" +
                "INSERT INTO test_users (id, name, role) VALUES (2, 'backup', 'readonly');\n" +
                "INSERT INTO test_users (id, name, role) VALUES (3, 'text;with;semi', 'user');",
            "DELETE FROM test_users WHERE id IN (1, 2, 3);",
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
