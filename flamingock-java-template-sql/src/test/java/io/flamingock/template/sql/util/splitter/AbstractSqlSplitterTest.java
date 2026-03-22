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
 * Base test class for SQL statement splitting behavior that should work across ALL SQL dialects.
 *
 * <p>This abstract test class verifies the base SQL parsing functionality that is common to
 * all standard SQL implementations:
 * <ul>
 *   <li><b>Semicolon delimiters:</b> Standard SQL statement separator</li>
 *   <li><b>-- line comments:</b> SQL-92 standard single-line comments</li>
 *   <li><b>/* *&#47; block comments:</b> SQL-92 standard multi-line comments</li>
 *   <li><b>Single-quoted strings:</b> Standard SQL string literals with {@code 'text'}</li>
 *   <li><b>Doubled single quotes:</b> Standard escape mechanism {@code ''} for quotes inside strings</li>
 *   <li><b>Double-quoted identifiers:</b> Standard SQL identifier quoting with {@code "identifier"}</li>
 *   <li><b>BEGIN/END blocks:</b> Standard SQL procedural blocks (procedures, functions, triggers)</li>
 * </ul>
 *
 * <p><b>Usage:</b> Each SQL dialect test class should extend this class and provide its own
 * {@code getSplitter()} implementation. This ensures that all generic SQL tests run against
 * every dialect, verifying that standard SQL features work correctly.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * @DisplayName("MySQL Splitter")
 * class MySqlSplitterTest extends AbstractSqlSplitterTest {
 *     @Override
 *     protected AbstractSqlSplitter getSplitter() {
 *         return new MySqlSplitter();
 *     }
 *
 *     // MySQL-specific tests...
 * }
 * }</pre>
 *
 * <p><b>IMPORTANT:</b> Dialect-specific features should be tested in the concrete subclass:
 * <ul>
 *   <li>MySQL/MariaDB: {@code #} comments, backticks, backslash escapes</li>
 *   <li>PostgreSQL: {@code $$} dollar quotes, nested comments</li>
 *   <li>SQL Server/Sybase: {@code GO} keyword, square brackets</li>
 *   <li>SQLite: Multiple quote styles, {@code []} brackets</li>
 *   <li>And so on for other dialects...</li>
 * </ul>
 *
 * @see AbstractSqlSplitter the base implementation being tested
 */
@DisplayName("Abstract Splitter")
abstract class AbstractSqlSplitterTest {

    /**
     * Provides the SQL statement splitter instance to test.
     * Each dialect test class must implement this to return its specific splitter.
     *
     * @return the SQL statement splitter instance for this dialect
     */
    protected abstract AbstractSqlSplitter getSplitter();

    /**
     * Helper method to get the splitter instance for tests.
     * Uses the abstract getSplitter() method.
     */
    private AbstractSqlSplitter splitter() {
        return getSplitter();
    }

    // ============================================================
    // BASIC STATEMENT SPLITTING
    // ============================================================

    @Test
    @DisplayName("Should handle single statement without semicolon")
    void splitStatements_singleStatement() {
        String sql = "CREATE TABLE users (id INT, name VARCHAR(100))";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertEquals("CREATE TABLE users (id INT, name VARCHAR(100))", statements.get(0).getSql());
    }

    @Test
    @DisplayName("Should split multiple statements by semicolon")
    void splitStatements_multipleStatements() {
        String sql = "CREATE TABLE users (id INT, name VARCHAR(100)); INSERT INTO users VALUES (1, 'john'); SELECT * FROM users";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(3, statements.size());
        assertEquals("CREATE TABLE users (id INT, name VARCHAR(100))", statements.get(0).getSql());
        assertEquals("INSERT INTO users VALUES (1, 'john')", statements.get(1).getSql());
        assertEquals("SELECT * FROM users", statements.get(2).getSql());
    }

    @Test
    @DisplayName("Should filter out empty statements and trim whitespace")
    void splitStatements_emptyAndWhitespace() {
        String sql = "   ;   CREATE TABLE test (id INT);   ;   ";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertEquals("CREATE TABLE test (id INT)", statements.get(0).getSql());
    }

    @Test
    @DisplayName("Should handle trailing semicolon")
    void splitStatements_trailingSemicolon() {
        String sql = "SELECT 1; SELECT 2;";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Should handle multiple consecutive semicolons")
    void splitStatements_consecutiveSemicolons() {
        String sql = "SELECT 1;;; SELECT 2";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    // ============================================================
    // COMMENT HANDLING
    // ============================================================

    @Test
    @DisplayName("Should strip block comments /* */")
    void splitStatements_blockComments() {
        String sql = "/* comment */ CREATE TABLE test (id INT); /* another */ INSERT INTO test VALUES (1)";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE test (id INT)", statements.get(0).getSql());
        assertEquals("INSERT INTO test VALUES (1)", statements.get(1).getSql());
        assertFalse(statements.get(0).getSql().contains("/*"));
        assertFalse(statements.get(1).getSql().contains("/*"));
    }

    @Test
    @DisplayName("Should strip line comments --")
    void splitStatements_lineComments() {
        String sql = "CREATE TABLE test (id INT); -- comment\nINSERT INTO test VALUES (1); -- another";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE test (id INT)", statements.get(0).getSql());
        assertEquals("INSERT INTO test VALUES (1)", statements.get(1).getSql());
        assertFalse(statements.get(0).getSql().contains("--"));
        assertFalse(statements.get(1).getSql().contains("--"));
    }

    @Test
    @DisplayName("Should handle mixed block and line comments")
    void splitStatements_mixedComments() {
        String sql = "/* block */ CREATE TABLE test (id INT); -- line\nINSERT INTO test VALUES (1); /* block */";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE test (id INT)", statements.get(0).getSql());
        assertEquals("INSERT INTO test VALUES (1)", statements.get(1).getSql());
        assertFalse(statements.stream().map(SqlStatement::getSql).anyMatch(s -> s.contains("/*") || s.contains("--")));
    }

    @Test
    @DisplayName("Should handle multi-line block comments")
    void splitStatements_multiLineBlockComments() {
        String sql = "/* Multi-line comment\n" +
                     "   spanning several lines\n" +
                     "   with content */\n" +
                     "CREATE TABLE users (id INT);";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertFalse(statements.get(0).getSql().contains("/*"));
        assertFalse(statements.get(0).getSql().contains("Multi-line"));
    }

    @Test
    @DisplayName("Should replace block comments with space to avoid token concatenation")
    void splitStatements_blockCommentDoesNotConcatenateTokens() {
        String sql = "SELECT/*comment*/1; SELECT 2;";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        // Comment is replaced with whitespace, so tokens are not concatenated (no "SELECT1")
        String stmt0 = statements.get(0).getSql().trim();
        assertTrue(stmt0.matches("SELECT\\s+1"), "SELECT and 1 should be separated by whitespace, got: " + stmt0);
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Should handle -- comment at end of file without newline")
    void splitStatements_dashCommentAtEnd() {
        String sql = "SELECT 1; -- comment at end";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
    }

    @Test
    @DisplayName("Should not treat -- inside strings as comment")
    void splitStatements_dashDashInsideStrings() {
        String sql = "INSERT INTO t (txt) VALUES ('--not a comment'); SELECT 1";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("--not a comment"));
    }

    @Test
    @DisplayName("Should not treat /* */ inside strings as comment")
    void splitStatements_blockCommentInsideStrings() {
        String sql = "INSERT INTO t (txt) VALUES ('/* not a comment */'); SELECT 1";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("/* not a comment */"));
    }

    // ============================================================
    // STRING LITERAL HANDLING
    // ============================================================

    @Test
    @DisplayName("Should not split on semicolons inside single-quoted strings")
    void splitStatements_simpleStrings() {
        String sql = "INSERT INTO users (name) VALUES ('john;doe'); INSERT INTO users (name) VALUES ('jane')";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO users (name) VALUES ('john;doe')", statements.get(0).getSql());
        assertEquals("INSERT INTO users (name) VALUES ('jane')", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Should handle doubled single quotes (SQL standard escape)")
    void splitStatements_escapedQuotes() {
        String sql = "INSERT INTO users (name) VALUES ('O''Brien'); INSERT INTO users (quote) VALUES ('can''t do it')";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO users (name) VALUES ('O''Brien')", statements.get(0).getSql());
        assertEquals("INSERT INTO users (quote) VALUES ('can''t do it')", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Should handle doubled quotes with semicolon inside string")
    void splitStatements_escapedQuotesWithSemicolon() {
        String sql = "INSERT INTO t(txt) VALUES ('x''y;z'); SELECT 1;";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t(txt) VALUES ('x''y;z')", statements.get(0).getSql());
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Should preserve newlines inside string literals")
    void splitStatements_preservesNewlinesInsideStringLiteral() {
        String sql = "INSERT INTO t(txt) VALUES ('a\nb'); SELECT 1;";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t(txt) VALUES ('a\nb')", statements.get(0).getSql());
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Should preserve multiple spaces inside string literals")
    void splitStatements_preservesMultipleSpacesInsideStringLiteral() {
        String sql = "INSERT INTO t(txt) VALUES ('a  b');";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertEquals("INSERT INTO t(txt) VALUES ('a  b')", statements.get(0).getSql());
    }

    @Test
    @DisplayName("Should handle empty strings")
    void splitStatements_emptyStrings() {
        String sql = "INSERT INTO t (txt) VALUES (''); SELECT 1";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("''"));
    }

    @Test
    @DisplayName("Should handle multiple strings in same statement")
    void splitStatements_multipleStringsInStatement() {
        String sql = "INSERT INTO t (a, b) VALUES ('val1', 'val2'); SELECT 1";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("'val1'"));
        assertTrue(statements.get(0).getSql().contains("'val2'"));
    }

    // ============================================================
    // DOUBLE-QUOTED IDENTIFIERS (Standard SQL)
    // ============================================================

    @Test
    @DisplayName("Should handle double-quoted identifiers")
    void splitStatements_doubleQuotes() {
        String sql = "CREATE TABLE \"test table\" (id INT); INSERT INTO \"test table\" VALUES (1)";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE \"test table\" (id INT)", statements.get(0).getSql());
        assertEquals("INSERT INTO \"test table\" VALUES (1)", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Should not split on semicolons inside double-quoted identifiers")
    void splitStatements_semicolonInDoubleQuotes() {
        String sql = "SELECT \"column;name\" FROM test; SELECT id FROM test";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("\"column;name\""));
    }

    @Test
    @DisplayName("Should handle escaped double quotes inside identifiers")
    void splitStatements_escapedDoubleQuotes() {
        String sql = "CREATE TABLE \"test\"\"table\" (id INT); SELECT * FROM \"test\"\"table\"";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("\"test\"\"table\""));
    }

    // ============================================================
    // BEGIN/END BLOCKS (Procedures, Functions, Triggers)
    // ============================================================

    @Test
    @DisplayName("Should not split inside CREATE PROCEDURE with BEGIN/END")
    void procedureDefinitionIsNotSplit() {
        String sql = "CREATE OR REPLACE PROCEDURE add_admin_user()\n" +
            "BEGIN\n" +
            "  INSERT INTO test_users (id, name, role) VALUES (99, 'Admin', 'superuser');\n" +
            "  COMMIT;\n" +
            "END;";

        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE OR REPLACE PROCEDURE ADD_ADMIN_USER"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("BEGIN"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("END"));
    }

    @Test
    @DisplayName("Should not split inside CREATE FUNCTION with BEGIN/END")
    void functionDefinitionIsNotSplit() {
        String sql = "CREATE FUNCTION get_total() RETURNS INT\n" +
            "BEGIN\n" +
            "  DECLARE total INT;\n" +
            "  SELECT COUNT(*) INTO total FROM users;\n" +
            "  RETURN total;\n" +
            "END;";

        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"));
    }

    @Test
    @DisplayName("Should not split inside CREATE TRIGGER with BEGIN/END")
    void triggerDefinitionIsNotSplit() {
        String sql = "CREATE TRIGGER update_timestamp\n" +
            "AFTER UPDATE ON users\n" +
            "FOR EACH ROW\n" +
            "BEGIN\n" +
            "  UPDATE users SET modified = NOW() WHERE id = NEW.id;\n" +
            "END;";

        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
    }

    @Test
    @DisplayName("Should handle nested BEGIN/END blocks")
    void nestedBeginEndBlocks() {
        String sql = "CREATE PROCEDURE complex_proc()\n" +
            "BEGIN\n" +
            "  IF condition THEN\n" +
            "    BEGIN\n" +
            "      INSERT INTO t1 VALUES (1);\n" +
            "      INSERT INTO t2 VALUES (2);\n" +
            "    END;\n" +
            "  END IF;\n" +
            "END;";

        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
    }

    @Test
    @DisplayName("Should handle multiple procedures separated by semicolons")
    void multipleProcedures() {
        String sql = "CREATE PROCEDURE proc1()\n" +
            "BEGIN\n" +
            "  SELECT 1;\n" +
            "END;\n" +
            "CREATE PROCEDURE proc2()\n" +
            "BEGIN\n" +
            "  SELECT 2;\n" +
            "END;";

        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("PROC1"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("PROC2"));
    }

    @Test
    @DisplayName("Should not treat BEGIN/END inside strings as block delimiters")
    void beginEndInsideStrings() {
        String sql = "INSERT INTO cmds (cmd) VALUES ('BEGIN transaction'); SELECT 1";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("BEGIN transaction"));
    }

    // ============================================================
    // COMPLEX MULTI-LINE SCENARIOS
    // ============================================================

    @Test
    @DisplayName("Should handle complex multi-line SQL with comments and procedures")
    void splitStatements_complexMultiLine() {
        String sql = "/* Multi-line comment\n" +
                      "   with content */\n" +
                      "CREATE TABLE users (\n" +
                      "    id INT,\n" +
                      "    name VARCHAR(100)\n" +
                      ");\n" +
                      "\n" +
                      "-- Line comment\n" +
                      "INSERT INTO users (id, name) VALUES (1, 'john');\n" +
                      "\n" +
                      "SELECT * FROM users";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(3, statements.size());
        // Comments are stripped; leading/trailing whitespace trimmed; internal structure preserved
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TABLE USERS"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("ID INT"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("NAME VARCHAR(100)"));
        assertEquals("INSERT INTO users (id, name) VALUES (1, 'john')", statements.get(1).getSql());
        assertEquals("SELECT * FROM users", statements.get(2).getSql());
        assertFalse(statements.stream().map(SqlStatement::getSql).anyMatch(s -> s.contains("/*") || s.contains("--")));
    }

    @Test
    @DisplayName("Should handle statements with parentheses")
    void splitStatements_parentheses() {
        String sql = "SELECT * FROM users WHERE id IN (1, 2, 3); SELECT COUNT(*) FROM (SELECT * FROM orders) AS subq";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("(1, 2, 3)"));
        assertTrue(statements.get(1).getSql().contains("FROM (SELECT"));
    }

    @Test
    @DisplayName("Should handle CASE statements with semicolons in THEN clauses")
    void splitStatements_caseStatements() {
        String sql = "SELECT CASE WHEN x = 1 THEN 'one' WHEN x = 2 THEN 'two' END FROM t; SELECT 1";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("CASE WHEN"));
    }

    @Test
    @DisplayName("Should split statements even when extra whitespace is present between tokens")
    void splitStatements_whitespaceNormalization() {
        String sql = "SELECT   1   FROM    dual;    SELECT     2";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        // Both statements are present; leading/trailing whitespace is trimmed
        assertTrue(statements.get(0).getSql().trim().toUpperCase().startsWith("SELECT"));
        assertTrue(statements.get(1).getSql().trim().toUpperCase().startsWith("SELECT"));
    }

    // ============================================================
    // EDGE CASES
    // ============================================================

    @Test
    @DisplayName("Should handle empty input")
    void splitStatements_emptyInput() {
        String sql = "";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(0, statements.size());
    }

    @Test
    @DisplayName("Should handle only whitespace")
    void splitStatements_onlyWhitespace() {
        String sql = "   \n\t  ";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(0, statements.size());
    }

    @Test
    @DisplayName("Should handle only comments")
    void splitStatements_onlyComments() {
        String sql = "-- comment 1\n/* comment 2 */";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(0, statements.size());
    }

    @Test
    @DisplayName("Should handle only semicolons")
    void splitStatements_onlySemicolons() {
        String sql = ";;;";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(0, statements.size());
    }

    @Test
    @DisplayName("Should handle very long strings")
    void splitStatements_longStrings() {
        StringBuilder sb = new StringBuilder(10000);
        for (int i = 0; i < 10000; i++) {
            sb.append('a');
        }
        String longValue = sb.toString();
        String sql = "INSERT INTO t (txt) VALUES ('" + longValue + "'); SELECT 1";
        List<SqlStatement> statements = splitter().splitWithDelimiter(sql, ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains(longValue));
    }
}
