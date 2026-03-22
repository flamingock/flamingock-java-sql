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
package io.flamingock.template.sql.util.splitter;

import io.flamingock.template.sql.util.SqlStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Sybase ASE-specific SQL splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in Sybase. Additionally, it tests Sybase-specific
 * features:
 * <ul>
 *   <li><b>GO keyword:</b> Batch separator (like SQL Server) - must be on its own line</li>
 *   <li><b>GO case-insensitivity:</b> {@code GO}, {@code go}, {@code Go} all work</li>
 *   <li><b>AS keyword in procedures:</b> {@code CREATE PROCEDURE name AS} (inherited from T-SQL)</li>
 *   <li><b>Sybase-specific syntax:</b> Some differences from SQL Server in procedure/function syntax</li>
 * </ul>
 *
 * <p>Note: Sybase ASE is the ancestor of SQL Server (Microsoft SQL Server forked from Sybase in 1994),
 * so they share much syntax, but Sybase has evolved separately since then.
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("Sybase Splitter")
class SybaseSplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new SybaseSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new SybaseSplitter();
    }

    // ============================================================
    // SYBASE-SPECIFIC TESTS
    // ============================================================

    @Test
    @DisplayName("Sybase: Should split on GO keyword")
    void shouldSplitOnGO() {
        String sql = "CREATE TABLE users (id INT)\nGO\nINSERT INTO users VALUES (1)\nGO";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TABLE"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT"));
    }

    @Test
    @DisplayName("Sybase: GO should be case-insensitive")
    void shouldSplitOnGOCaseInsensitive() {
        String sql = "SELECT 1\ngo\nSELECT 2\nGo\nSELECT 3\nGO";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(3, statements.size());
    }

    @Test
    @DisplayName("Sybase: GO must be on its own line")
    void shouldNotSplitOnGOInsideLine() {
        String sql = "SELECT 'GO' AS command; SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Should split on semicolon, not on GO inside string
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("GO"));
    }

    @Test
    @DisplayName("Sybase: Should handle CREATE PROCEDURE with AS keyword and validate delimiter")
    void shouldHandleCreateProcedureWithAS() {
        String sql = "CREATE PROCEDURE add_user\n" +
            "AS\n" +
            "BEGIN\n" +
            "  INSERT INTO users VALUES (1, 'Admin');\n" +
            "END;\n" +
            "SELECT 1";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split procedure and simple SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("AS"));
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Sybase: Should handle multiple procedures separated by GO")
    void shouldHandleMultipleProcedures() {
        String sql = "CREATE PROCEDURE proc1\n" +
            "AS\n" +
            "  SELECT 1\n" +
            "GO\n" +
            "CREATE PROCEDURE proc2\n" +
            "AS\n" +
            "  SELECT 2\n" +
            "GO";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("proc1"));
        assertTrue(statements.get(1).getSql().contains("proc2"));
    }

    @Test
    @DisplayName("Sybase: Should handle CREATE FUNCTION with RETURNS and validate delimiter")
    void shouldHandleCreateFunction() {
        String sql = "CREATE FUNCTION get_total()\n" +
            "RETURNS INT\n" +
            "AS\n" +
            "BEGIN\n" +
            "  RETURN 100\n" +
            "END\n" +
            "GO\n" +
            "SELECT * FROM users";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split function and simple SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("RETURNS"));
        assertEquals("SELECT * FROM users", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Sybase: Should handle mixed semicolon and GO delimiters")
    void shouldHandleMixedDelimiters() {
        String sql = "CREATE TABLE t1 (id INT); CREATE TABLE t2 (id INT)\n" +
            "GO\n" +
            "INSERT INTO t1 VALUES (1); INSERT INTO t2 VALUES (2)\n" +
            "GO";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // First batch: 2 CREATE statements (split by semicolon)
        // Second batch: 2 INSERT statements (split by semicolon)
        assertEquals(4, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TABLE T1"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("CREATE TABLE T2"));
        assertTrue(statements.get(2).getSql().toUpperCase().contains("INSERT INTO T1"));
        assertTrue(statements.get(3).getSql().toUpperCase().contains("INSERT INTO T2"));
    }

    @Test
    @DisplayName("Sybase: Should handle triggers and validate delimiter")
    void shouldHandleTriggers() {
        String sql = "CREATE TRIGGER update_timestamp\n" +
            "ON users\n" +
            "FOR UPDATE\n" +
            "AS\n" +
            "BEGIN\n" +
            "  UPDATE users SET modified = getdate()\n" +
            "END\n" +
            "GO\n" +
            "INSERT INTO users VALUES (1, 'test')";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split trigger and INSERT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
        assertEquals("INSERT INTO users VALUES (1, 'test')", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Sybase: Should handle RAISERROR statements and validate delimiter")
    void shouldHandleRaiseError() {
        String sql = "IF @count = 0\n" +
            "BEGIN\n" +
            "  RAISERROR 20001 'No records found'\n" +
            "END;\n" +
            "SELECT 1";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split IF block and SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("RAISERROR"));
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Sybase: Should handle multiple GOs on separate lines")
    void shouldHandleMultipleGOs() {
        String sql = "SELECT 1\nGO\nGO\nSELECT 2\nGO";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Empty batches between GOs should be filtered out
        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    // ============================================================
    // EDGE CASES - GO INSIDE COMMENTS/STRINGS
    // ============================================================

    @Test
    @DisplayName("Sybase: Should NOT split on GO inside block comment")
    void shouldNotSplitOnGOInBlockComment() {
        String sql = "SELECT 1\n" +
            "/*\n" +
            "GO\n" +
            "*/\n" +
            "SELECT 2";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // GO inside comment should be ignored - should NOT split
        assertEquals(1, statements.size(), "GO inside block comment should NOT cause split");

        // The single statement should contain both SELECTs
        String stmt = statements.get(0).getSql();
        assertTrue(stmt.contains("SELECT 1"), "Should contain first SELECT");
        assertTrue(stmt.contains("SELECT 2"), "Should contain second SELECT");

        // Comment should be replaced with space (not left as-is)
        assertFalse(stmt.contains("*/"), "Comment markers should be replaced with space");
    }

    @Test
    @DisplayName("Sybase: Should NOT split on GO inside string literal")
    void shouldNotSplitOnGOInString() {
        String sql = "INSERT INTO cmds (cmd) VALUES ('GO');\n" +
            "SELECT 1";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // GO inside string should not split - split by semicolon instead
        assertEquals(2, statements.size(), "GO inside string should not split");
        assertTrue(statements.get(0).getSql().contains("'GO'"));
        assertEquals("SELECT 1", statements.get(1).getSql());
    }
}
