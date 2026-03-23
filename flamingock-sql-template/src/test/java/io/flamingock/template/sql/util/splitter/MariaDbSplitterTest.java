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
 * Tests for MariaDB-specific SQL splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in MariaDB. Additionally, it tests MariaDB-specific
 * features:
 * <ul>
 *   <li><b>Hash comments:</b> {@code #} starts a line comment (like MySQL)</li>
 *   <li><b>Backtick identifiers:</b> {@code `table name`} allows spaces in identifiers</li>
 *   <li><b>Backslash escapes:</b> {@code \'} for escaping quotes within strings (not standard SQL)</li>
 *   <li><b>Mixed comment styles:</b> Supports {@code --}, {@code #}, and {@code /* *&#47;} all in same script</li>
 *   <li><b>DELIMITER command:</b> (Optional) Change statement delimiter for stored procedures</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("MariaDB Splitter")
class MariaDbSplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new MariaDbSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new MariaDbSplitter();
    }

    // ============================================================
    // MARIADB-SPECIFIC TESTS
    // ============================================================

    @Test
    @DisplayName("MariaDB: Should strip line comments starting with #")
    void shouldStripHashComments() {
        String sql = "SELECT 1; # This is a MariaDB comment\nSELECT 2;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertFalse(statements.stream().map(SqlStatement::getSql).anyMatch(s -> s.contains("#")));
    }

    @Test
    @DisplayName("MariaDB: Should handle # comment at end of line without newline")
    void shouldHandleHashCommentAtEndOfLine() {
        String sql = "SELECT 1; # comment without newline";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql().trim());
    }

    @Test
    @DisplayName("MariaDB: Should handle backtick-quoted identifiers")
    void shouldHandleBacktickIdentifiers() {
        String sql = "CREATE TABLE `test table` (id INT); INSERT INTO `test table` VALUES (1)";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("`test table`"));
        assertTrue(statements.get(1).getSql().contains("`test table`"));
    }

    @Test
    @DisplayName("MariaDB: Should handle backticks with semicolons inside")
    void shouldHandleBackticksWithSemicolonsInside() {
        String sql = "SELECT `column;name` FROM users; SELECT * FROM orders";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("`column;name`"));
    }

    @Test
    @DisplayName("MariaDB: Should handle backslash escapes in strings")
    void shouldHandleBackslashEscapes() {
        String sql = "INSERT INTO users (name) VALUES ('It\\'s fine'); SELECT * FROM users";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("\\'"));
    }

    @Test
    @DisplayName("MariaDB: Should handle backslash escape for quotes")
    void shouldHandleBackslashEscapeQuotes() {
        String sql = "INSERT INTO log (msg) VALUES ('User said: \\\"hello\\\"')";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().contains("\\\""));
    }

    @Test
    @DisplayName("MariaDB: Should handle mixed comment styles (all three types)")
    void shouldHandleMixedCommentStyles() {
        String sql = "SELECT 1; -- dash comment\n" +
                     "SELECT 2; # hash comment\n" +
                     "/* block comment */ SELECT 3;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(3, statements.size());
        assertFalse(statements.stream().map(SqlStatement::getSql).anyMatch(s -> s.contains("--") || s.contains("#") || s.contains("/*")));
    }

    @Test
    @DisplayName("MariaDB: Should handle # inside strings (not a comment)")
    void shouldHandleHashInsideStrings() {
        String sql = "INSERT INTO tags (tag) VALUES ('#hashtag'); SELECT * FROM tags";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("#hashtag"));
    }

    @Test
    @DisplayName("MariaDB: Should handle backtick inside strings")
    void shouldHandleBacktickInsideStrings() {
        String sql = "INSERT INTO code (snippet) VALUES ('SELECT `id` FROM users'); SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("`id`"));
    }

    @Test
    @DisplayName("MariaDB: Should handle DELIMITER command for stored procedures and validate final delimiter")
    void shouldHandleDelimiterCommand() {
        String sql = "DELIMITER //\n" +
            "CREATE PROCEDURE test()\n" +
            "BEGIN\n" +
            "  SELECT 1;\n" +
            "END//\n" +
            "DELIMITER ;\n" +
            "SELECT 42";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // After DELIMITER //, semicolons should not split, only // should
        // After DELIMITER ;, back to normal semicolon splitting
        // Expected: 2 statements (procedure + SELECT 42)
        assertEquals(2, statements.size(), "Should split procedure and final SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertEquals("SELECT 42", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MariaDB: DELIMITER should be case-insensitive")
    void delimiterShouldBeCaseInsensitive() {
        String sql = "delimiter $$\n" +
            "CREATE FUNCTION f() RETURNS INT BEGIN RETURN 1; END$$\n" +
            "DeLiMiTeR ;\n" +
            "SELECT 99;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"));
        assertEquals("SELECT 99", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MariaDB: Should NOT split on semicolons when delimiter is changed")
    void shouldNotSplitOnSemicolonsWhenDelimiterChanged() {
        String sql = "DELIMITER ||\n" +
            "CREATE TRIGGER t1 BEFORE INSERT ON users\n" +
            "FOR EACH ROW\n" +
            "BEGIN\n" +
            "  SET NEW.created = NOW();\n" +
            "  INSERT INTO audit VALUES (NEW.id);\n" +
            "END||\n" +
            "DELIMITER ;\n" +
            "INSERT INTO users VALUES (1, 'test');";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Semicolons inside trigger should NOT split when delimiter is ||
        assertEquals(2, statements.size(), "Should have 2 statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
        assertTrue(statements.get(0).getSql().contains("SET NEW.created"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT INTO USERS"));
    }
}
