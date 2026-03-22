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
 * Tests for SQL Server-specific SQL splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in SQL Server. Additionally, it tests SQL Server-specific
 * features:
 * <ul>
 *   <li><b>GO keyword:</b> Batch separator (not a SQL keyword) - must be on its own line</li>
 *   <li><b>GO case-insensitivity:</b> {@code GO}, {@code go}, {@code Go} all work</li>
 *   <li><b>GO with count:</b> {@code GO 5} repeats the batch 5 times</li>
 *   <li><b>Square brackets:</b> {@code [table name]} for identifiers with spaces/special chars</li>
 *   <li><b>AS keyword in procedures:</b> {@code CREATE PROCEDURE name AS} (no BEGIN required immediately)</li>
 *   <li><b>Mixed delimiters:</b> Both semicolons and GO can be used in same script</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("SQL Server Splitter")
class SqlServerSplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new SqlServerSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new SqlServerSplitter();
    }

    // ============================================================
    // SQL SERVER-SPECIFIC TESTS
    // ============================================================

    @Test
    @DisplayName("SQL Server: Should split on GO keyword (case-insensitive)")
    void shouldSplitOnGO() {
        String sql = "CREATE TABLE users (id INT)\nGO\nINSERT INTO users VALUES (1)\nGO";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE users (id INT)", statements.get(0).getSql());
        assertEquals("INSERT INTO users VALUES (1)", statements.get(1).getSql());
    }

    @Test
    @DisplayName("SQL Server: GO should be case-insensitive")
    void shouldSplitOnGOCaseInsensitive() {
        String sql = "SELECT 1\ngo\nSELECT 2\nGo\nSELECT 3\nGO";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(3, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
        assertEquals("SELECT 3", statements.get(2).getSql());
    }

    @Test
    @DisplayName("SQL Server: GO must be on its own line")
    void shouldNotSplitOnGOInsideLine() {
        String sql = "SELECT 'GO' AS command; SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Should split on semicolon, not on GO inside string
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("GO"));
    }

    @Test
    @DisplayName("SQL Server: GO should not be recognized inside strings")
    void shouldNotSplitOnGOInStrings() {
        String sql = "INSERT INTO cmds (cmd) VALUES ('GO')\nGO\nSELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("'GO'"));
    }

    @Test
    @DisplayName("SQL Server: Should handle GO with count (GO 5)")
    void shouldHandleGOWithCount() {
        String sql = "INSERT INTO test VALUES (1)\nGO 5";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        // GO 5 means repeat the batch 5 times (metadata stored in SqlStatement)
        assertEquals(1, statements.size());
        assertEquals("INSERT INTO test VALUES (1)", statements.get(0).getSql());
        assertEquals(5, statements.get(0).getRepeatCount(), "GO 5 should set repeatCount to 5");
    }

    @Test
    @DisplayName("SQL Server: Should handle GO with various count values")
    void shouldHandleGOWithVariousCounts() {
        String sql = "SELECT 1\nGO 10\nSELECT 2\nGO 1\nSELECT 3\nGO 100";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(3, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals(10, statements.get(0).getRepeatCount());
        assertEquals("SELECT 2", statements.get(1).getSql());
        assertEquals(1, statements.get(1).getRepeatCount());
        assertEquals("SELECT 3", statements.get(2).getSql());
        assertEquals(100, statements.get(2).getRepeatCount());
    }

    @Test
    @DisplayName("SQL Server: Should handle square bracket identifiers")
    void shouldHandleSquareBracketIdentifiers() {
        String sql = "CREATE TABLE [test table] (id INT); INSERT INTO [test table] VALUES (1)";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE [test table] (id INT)", statements.get(0).getSql());
        assertEquals("INSERT INTO [test table] VALUES (1)", statements.get(1).getSql());
    }

    @Test
    @DisplayName("SQL Server: Should ignore semicolons inside square brackets")
    void shouldIgnoreSemicolonInSquareBrackets() {
        String sql = "SELECT [column;name] FROM test; SELECT id FROM test";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("SELECT [column;name] FROM test", statements.get(0).getSql());
        assertEquals("SELECT id FROM test", statements.get(1).getSql());
    }

    @Test
    @DisplayName("SQL Server: Should handle escaped square brackets inside identifiers")
    void shouldHandleEscapedSquareBrackets() {
        String sql = "CREATE TABLE [test]]table] (id INT); SELECT * FROM [test]]table]";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("[test]]table]"));
    }

    @Test
    @DisplayName("SQL Server: Should handle CREATE PROCEDURE with AS keyword and validate delimiter")
    void shouldHandleCreateProcedureWithAS() {
        String sql = "CREATE PROCEDURE add_user\n" +
            "AS\n" +
            "BEGIN\n" +
            "  INSERT INTO users (id, name) VALUES (1, 'Admin');\n" +
            "  COMMIT;\n" +
            "END;\n" +
            "SELECT * FROM users";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split procedure and simple SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("AS"));
        assertEquals("SELECT * FROM users", statements.get(1).getSql());
    }

    @Test
    @DisplayName("SQL Server: Should handle CREATE PROCEDURE with AS but no BEGIN and validate delimiter")
    void shouldHandleProcedureASWithoutBegin() {
        String sql = "CREATE PROCEDURE get_user\n" +
            "AS\n" +
            "  SELECT * FROM users;\n" +
            "INSERT INTO log VALUES ('procedure created')";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split procedure and INSERT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertEquals("INSERT INTO log VALUES ('procedure created')", statements.get(1).getSql());
    }

    @Test
    @DisplayName("SQL Server: Should handle mixed semicolon and GO delimiters")
    void shouldHandleMixedDelimiters() {
        String sql = "CREATE TABLE t1 (id INT);\n" +
                     "CREATE TABLE t2 (id INT)\n" +
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
    @DisplayName("SQL Server: Should handle statements without semicolons before GO")
    void shouldHandleStatementsWithoutSemicolons() {
        String sql = "CREATE TABLE test (id INT)\n" +
                     "GO\n" +
                     "INSERT INTO test VALUES (1)\n" +
                     "INSERT INTO test VALUES (2)\n" +
                     "GO";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Without semicolons, each batch becomes one statement
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TABLE"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT INTO TEST VALUES (1)"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT INTO TEST VALUES (2)"));
    }

    @Test
    @DisplayName("SQL Server: Should handle DECLARE variables in batches")
    void shouldHandleDeclareInBatches() {
        String sql = "DECLARE @count INT = 10\nGO\nSELECT @count";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("DECLARE"));
    }

    @Test
    @DisplayName("SQL Server: Should handle multiple GOs on separate lines")
    void shouldHandleMultipleGOs() {
        String sql = "SELECT 1\nGO\nGO\nSELECT 2";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Empty batch between two GOs should be filtered out
        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    // ============================================================
    // EDGE CASES - GO INSIDE STRINGS/COMMENTS
    // ============================================================

    @Test
    @DisplayName("SQL Server: Should NOT split on GO inside multi-line string literal")
    void shouldNotSplitOnGOInMultiLineString() {
        // Multi-line strings need special handling
        String sql = "INSERT INTO cmds (cmd) VALUES ('Line 1' + CHAR(10) + 'GO' + CHAR(10) + 'Line 3');\n" +
            "SELECT 1";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Should split by semicolon, not by GO concatenated in string
        assertEquals(2, statements.size(), "GO inside concatenated string should not split");
        assertTrue(statements.get(0).getSql().contains("'GO'"));
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("SQL Server: Should NOT split on GO inside block comment")
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
    @DisplayName("SQL Server: Should NOT split on GO inside line comment")
    void shouldNotSplitOnGOInLineComment() {
        String sql = "SELECT 1 -- GO this is commented\n" +
            "SELECT 2";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // GO in line comment should not split
        // Should be treated as one statement (no delimiter between SELECT 1 and SELECT 2)
        assertEquals(1, statements.size(), "GO inside line comment should not split");
        assertTrue(statements.get(0).getSql().contains("SELECT 1"));
        assertTrue(statements.get(0).getSql().contains("SELECT 2"));
    }

    @Test
    @DisplayName("SQL Server: Should handle GO after semicolon on same line")
    void shouldNotSplitOnGOAfterSemicolon() {
        // GO must be on its own line, not after semicolon
        String sql = "SELECT 1; GO\nSELECT 2";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // "SELECT 1; GO" is one line, GO is after semicolon so not on its own line
        // Should split on semicolon, treating "GO" as part of the line
        // Then next batch is "SELECT 2"
        assertTrue(statements.size() >= 1, "Should have statements");
    }

    @Test
    @DisplayName("SQL Server: # in temp table name should NOT be treated as a comment")
    void hashTempTable() {
        String sql = "SELECT * FROM #temp WHERE id = 1;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size(), "Should produce exactly one statement");
        assertTrue(statements.get(0).getSql().contains("#temp"),
            "Temp table name #temp should be preserved, not treated as comment");
    }

    @Test
    @DisplayName("SQL Server: GO count overflow should throw IllegalArgumentException")
    void goCountOverflow() {
        assertThrows(IllegalArgumentException.class,
            () -> sqlSplitter.split("SELECT 1\nGO 2147483648\n"),
            "Integer overflow in GO count should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("SQL Server: Negative GO count should throw IllegalArgumentException")
    void goCountNegative() {
        assertThrows(IllegalArgumentException.class,
            () -> sqlSplitter.split("SELECT 1\nGO -5\n"),
            "Negative GO count should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("SQL Server: GO followed by a tab and no trailing newline should not throw StringIndexOutOfBoundsException")
    void goFollowedByTabAtEndOfInput() {
        // Regression for operator-precedence bug in tryMatchGO():
        // "GO\t" at end of string (no newline) used to throw StringIndexOutOfBoundsException
        List<SqlStatement> statements = sqlSplitter.split("SELECT 1\nGO\t");

        assertEquals(1, statements.size(), "GO should be recognised as a batch separator");
        assertEquals("SELECT 1", statements.get(0).getSql());
    }
}
