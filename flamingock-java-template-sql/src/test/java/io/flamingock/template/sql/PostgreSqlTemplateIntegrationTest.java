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
import io.flamingock.api.template.wrappers.TemplateString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that execute {@link SqlTemplate} against a real PostgreSQL instance
 * (via Testcontainers). Verifies dialect detection, multi-statement splitting, dollar-quoted
 * PL/pgSQL function bodies, and rollback execution.
 */
@Testcontainers
class PostgreSqlTemplateIntegrationTest {

    private static final String TEST_TABLE = "pg_test";

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static DataSource dataSource;

    @BeforeAll
    static void setUpDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        dataSource = new HikariDataSource(config);
    }

    @AfterEach
    void cleanUp() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TEST_TABLE);
            stmt.execute("DROP FUNCTION IF EXISTS greet(TEXT)");
            stmt.execute("DROP FUNCTION IF EXISTS dollar_test()");
            stmt.execute("DROP FUNCTION IF EXISTS nested_tag_test()");
            stmt.execute("DROP FUNCTION IF EXISTS outer_func()");
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @AfterAll
    static void tearDownDataSource() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    @DisplayName("WHEN multiple SQL statements applied against PostgreSQL THEN all execute correctly")
    void applyMultipleStatements() throws Exception {
        SqlTemplate template = buildTemplate(
                "CREATE TABLE " + TEST_TABLE + " (id INT PRIMARY KEY, name VARCHAR(100));\n" +
                "INSERT INTO " + TEST_TABLE + " VALUES (1, 'Alice');\n" +
                "INSERT INTO " + TEST_TABLE + " VALUES (2, 'Bob');",
                null);

        try (Connection conn = dataSource.getConnection()) {
            template.apply(conn);
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE)) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("WHEN a dollar-quoted PL/pgSQL function is applied THEN it is created intact and callable")
    void dollarQuotedFunctionBody() throws Exception {
        SqlTemplate template = buildTemplate(
                "CREATE OR REPLACE FUNCTION greet(name TEXT) RETURNS TEXT AS $$\n" +
                "BEGIN\n" +
                "  RETURN 'Hello, ' || name;\n" +
                "END;\n" +
                "$$ LANGUAGE plpgsql;",
                null);

        try (Connection conn = dataSource.getConnection()) {
            template.apply(conn);
        }

        // Verify the function was created and returns the expected result
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT greet('World')")) {
            assertTrue(rs.next());
            assertEquals("Hello, World", rs.getString(1));
        }
    }

    @Test
    @DisplayName("WHEN a multi-statement apply includes a dollar-quoted block THEN non-block statements are split correctly")
    void dollarQuotedFunctionWithSurroundingStatements() throws Exception {
        SqlTemplate template = buildTemplate(
                "CREATE TABLE " + TEST_TABLE + " (id INT PRIMARY KEY);\n" +
                "CREATE OR REPLACE FUNCTION greet(name TEXT) RETURNS TEXT AS $$\n" +
                "BEGIN\n" +
                "  RETURN 'Hello, ' || name;\n" +
                "END;\n" +
                "$$ LANGUAGE plpgsql;\n" +
                "INSERT INTO " + TEST_TABLE + " VALUES (1);",
                null);

        try (Connection conn = dataSource.getConnection()) {
            template.apply(conn);
        }

        // Both table and function should have been created
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT greet('Test')")) {
            assertTrue(rs.next());
            assertEquals("Hello, Test", rs.getString(1));
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE)) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("WHEN rollback SQL is executed against PostgreSQL THEN the database state is reverted")
    void rollbackExecutesAgainstRealPostgresDatabase() throws Exception {
        SqlTemplate template = buildTemplate(
                "CREATE TABLE " + TEST_TABLE + " (id INT PRIMARY KEY)",
                "DROP TABLE " + TEST_TABLE);

        try (Connection conn = dataSource.getConnection()) {
            template.apply(conn);
            assertTrue(tableExists(conn, TEST_TABLE), "Table should exist after apply");

            template.rollback(conn);
            assertFalse(tableExists(conn, TEST_TABLE), "Table should not exist after rollback");
        }
    }

    // ============================================================
    // DOLLAR-SIGN CORRUPTION BUG TESTS
    // ============================================================

    @Test
    @DisplayName("WHEN DO block has nested plain $$ dollar-quotes (no splitting) THEN $$ is preserved and executes")
    void shouldExecuteDOBlockWithNestedPlainDollarQuotes() throws Exception {
        String sql = "DO $batch$\n" +
            "DECLARE v_msg TEXT;\n" +
            "BEGIN\n" +
            "    v_msg := $$This string contains; semicolons and 'quotes'$$;\n" +
            "    CREATE TABLE " + TEST_TABLE + " (id INT PRIMARY KEY, msg TEXT);\n" +
            "    INSERT INTO " + TEST_TABLE + " VALUES (1, v_msg);\n" +
            "END;\n" +
            "$batch$;";

        SqlTemplate template = buildTemplate(sql, null, false);

        try (Connection conn = dataSource.getConnection()) {
            template.apply(conn);
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT msg FROM " + TEST_TABLE + " WHERE id = 1")) {
            assertTrue(rs.next(), "Row should exist");
            assertEquals("This string contains; semicolons and 'quotes'", rs.getString(1));
        }
    }

    @Test
    @DisplayName("WHEN DO block has nested plain $$ dollar-quotes (with splitting) THEN $$ is preserved and executes")
    void shouldExecuteDOBlockWithNestedPlainDollarQuotesAndSplitting() throws Exception {
        String sql = "DO $batch$\n" +
            "DECLARE v_msg TEXT;\n" +
            "BEGIN\n" +
            "    v_msg := $$This string contains; semicolons and 'quotes'$$;\n" +
            "    CREATE TABLE " + TEST_TABLE + " (id INT PRIMARY KEY, msg TEXT);\n" +
            "    INSERT INTO " + TEST_TABLE + " VALUES (1, v_msg);\n" +
            "END;\n" +
            "$batch$;";

        SqlTemplate template = buildTemplate(sql, null, true);

        try (Connection conn = dataSource.getConnection()) {
            template.apply(conn);
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT msg FROM " + TEST_TABLE + " WHERE id = 1")) {
            assertTrue(rs.next(), "Row should exist");
            assertEquals("This string contains; semicolons and 'quotes'", rs.getString(1));
        }
    }

    @Test
    @DisplayName("WHEN function body uses multiple plain $$ blocks inside tagged outer THEN all are preserved and function works")
    void shouldExecuteMultiplePlainDollarQuoteBlocks() throws Exception {
        // Outer uses tagged $fn$ so inner plain $$ blocks are unambiguous
        String sql = "CREATE OR REPLACE FUNCTION dollar_test() RETURNS TEXT AS $fn$\n" +
            "DECLARE\n" +
            "    v1 TEXT := $$first; value$$;\n" +
            "    v2 TEXT := $$second; value$$;\n" +
            "BEGIN\n" +
            "    RETURN v1 || ' and ' || v2;\n" +
            "END;\n" +
            "$fn$ LANGUAGE plpgsql;\n" +
            "CREATE TABLE " + TEST_TABLE + " (id INT PRIMARY KEY, result TEXT);\n" +
            "INSERT INTO " + TEST_TABLE + " VALUES (1, dollar_test());";

        SqlTemplate template = buildTemplate(sql, null, true);

        try (Connection conn = dataSource.getConnection()) {
            template.apply(conn);
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT result FROM " + TEST_TABLE + " WHERE id = 1")) {
            assertTrue(rs.next(), "Row should exist");
            assertEquals("first; value and second; value", rs.getString(1));
        }
    }

    @Test
    @DisplayName("WHEN function uses nested tagged dollar-quotes THEN they are preserved and function works")
    void shouldExecuteNestedTaggedDollarQuotes() throws Exception {
        String sql = "CREATE OR REPLACE FUNCTION nested_tag_test() RETURNS TEXT AS $outer$\n" +
            "DECLARE\n" +
            "    v_sql TEXT := $inner$hello; world$inner$;\n" +
            "BEGIN\n" +
            "    RETURN v_sql;\n" +
            "END;\n" +
            "$outer$ LANGUAGE plpgsql;\n" +
            "CREATE TABLE " + TEST_TABLE + " (id INT PRIMARY KEY, result TEXT);\n" +
            "INSERT INTO " + TEST_TABLE + " VALUES (1, nested_tag_test());";

        SqlTemplate template = buildTemplate(sql, null, true);

        try (Connection conn = dataSource.getConnection()) {
            template.apply(conn);
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT result FROM " + TEST_TABLE + " WHERE id = 1")) {
            assertTrue(rs.next(), "Row should exist");
            assertEquals("hello; world", rs.getString(1));
        }
    }

    @Test
    @DisplayName("WHEN DO block comment contains the same dollar-quote tag (with splitting) THEN descriptive error is thrown")
    void shouldThrowDescriptiveErrorWhenDollarQuoteTagInComment() {
        String sql = "DO $batch$\n" +
            "DECLARE\n" +
            "    v_count INTEGER;\n" +
            "    v_msg   TEXT;\n" +
            "BEGIN\n" +
            "    -- Create a table inside a DO block\n" +
            "    CREATE TABLE IF NOT EXISTS " + TEST_TABLE + " (\n" +
            "        id    SERIAL PRIMARY KEY,\n" +
            "        label TEXT NOT NULL\n" +
            "    );\n" +
            "\n" +
            "    -- Nested dollar-quote inside $batch$ using $$\n" +
            "    v_msg := $$This string contains; semicolons and 'quotes'$$;\n" +
            "\n" +
            "    -- Insert rows in a loop with exception handling\n" +
            "    FOR i IN 1..5 LOOP\n" +
            "        BEGIN\n" +
            "            INSERT INTO " + TEST_TABLE + " (label)\n" +
            "            VALUES ('item-' || i::TEXT || E'; special\\nchar');\n" +
            "        EXCEPTION WHEN unique_violation THEN\n" +
            "            RAISE NOTICE 'Skipping duplicate for item %', i;\n" +
            "        END;\n" +
            "    END LOOP;\n" +
            "\n" +
            "    SELECT count(*) INTO v_count FROM " + TEST_TABLE + ";\n" +
            "    RAISE NOTICE 'Inserted % rows; message = %', v_count, v_msg;\n" +
            "END;\n" +
            "$batch$;";

        SqlTemplate template = buildTemplate(sql, null, true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            try (Connection conn = dataSource.getConnection()) {
                template.apply(conn);
            }
        });
        assertTrue(ex.getMessage().contains("Unclosed dollar-quoted block"),
            "Should provide descriptive error about unclosed dollar-quote, got: " + ex.getMessage());
    }

    // ---- helpers ----

    private SqlTemplate buildTemplate(String applySql, String rollbackSql, boolean splitStatements) {
        SqlTemplate template = new SqlTemplate();
        SqlTemplateConfig config = new SqlTemplateConfig();
        config.setSplitStatements(splitStatements);
        template.setConfiguration(config);
        template.setApplyPayload(new TemplateString(applySql));
        if (rollbackSql != null) {
            template.setRollbackPayload(new TemplateString(rollbackSql));
        }
        return template;
    }

    private SqlTemplate buildTemplate(String applySql, String rollbackSql) {
        SqlTemplate template = new SqlTemplate();
        SqlTemplateConfig config = new SqlTemplateConfig();
        config.setSplitStatements(true);
        template.setConfiguration(config);
        template.setApplyPayload(new TemplateString(applySql));
        if (rollbackSql != null) {
            template.setRollbackPayload(new TemplateString(rollbackSql));
        }
        return template;
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables " +
                     "WHERE table_name = '" + tableName + "' AND table_schema = 'public'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
