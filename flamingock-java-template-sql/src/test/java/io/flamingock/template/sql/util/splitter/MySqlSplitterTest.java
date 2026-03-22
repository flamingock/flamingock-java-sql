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
 * Tests for MySQL-specific SQL statement splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in MySQL. Additionally, it tests MySQL-specific
 * features:
 * <ul>
 *   <li><b>Hash comments:</b> {@code #} starts a line comment (MySQL extension)</li>
 *   <li><b>Backtick identifiers:</b> {@code `table name`} allows spaces and special chars in identifiers</li>
 *   <li><b>Backslash escapes:</b> {@code \'} for escaping quotes within strings (not standard SQL)</li>
 *   <li><b>Mixed comment styles:</b> Supports {@code --}, {@code #}, and {@code /* *&#47;} all in same script</li>
 *   <li><b>DELIMITER command:</b> Change statement delimiter for stored procedures</li>
 *   <li><b>Double-quoted strings:</b> When {@code ANSI_QUOTES} mode is disabled (default)</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("MySQL Splitter")
class MySqlSplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new MySqlSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new MySqlSplitter();
    }

    // ============================================================
    // MYSQL-SPECIFIC TESTS
    // ============================================================

    @Test
    @DisplayName("MySQL: Should strip line comments starting with #")
    void shouldStripHashComments() {
        String sql = "SELECT 1; # This is a MySQL comment\nSELECT 2;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
        assertFalse(statements.stream().map(SqlStatement::getSql).anyMatch(s -> s.contains("#")));
    }

    @Test
    @DisplayName("MySQL: Should handle # comment at end of line without newline and split correctly")
    void shouldHandleHashCommentAtEndOfFile() {
        String sql = "SELECT 1; # comment at end\nSELECT 2";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql().trim());
        assertEquals("SELECT 2", statements.get(1).getSql().trim());
    }

    @Test
    @DisplayName("MySQL: Should handle backtick-quoted identifiers")
    void shouldHandleBacktickIdentifiers() {
        String sql = "CREATE TABLE `test table` (id INT); INSERT INTO `test table` VALUES (1)";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE `test table` (id INT)", statements.get(0).getSql());
        assertEquals("INSERT INTO `test table` VALUES (1)", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Should ignore semicolons inside backtick-quoted identifiers")
    void shouldIgnoreSemicolonInBackticks() {
        String sql = "SELECT `column;name` FROM test; SELECT id FROM test";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("SELECT `column;name` FROM test", statements.get(0).getSql());
        assertEquals("SELECT id FROM test", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Should handle escaped backticks inside identifiers")
    void shouldHandleEscapedBackticks() {
        String sql = "CREATE TABLE `user``name` (id INT); SELECT * FROM `user``name`";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("`user``name`"));
        assertTrue(statements.get(1).getSql().contains("`user``name`"));
    }

    @Test
    @DisplayName("MySQL: Should handle backslash escapes in strings")
    void shouldHandleBackslashEscapes() {
        String sql = "INSERT INTO users (name) VALUES ('It\\'s fine'); SELECT * FROM users";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO users (name) VALUES ('It\\'s fine')", statements.get(0).getSql());
        assertEquals("SELECT * FROM users", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Should preserve semicolons in string with escaped quotes")
    void shouldPreserveSemicolonInStringWithEscapedQuotes() {
        String sql = "INSERT INTO t(txt) VALUES ('a;b\\'c'); SELECT 1;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t(txt) VALUES ('a;b\\'c')", statements.get(0).getSql());
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Should handle double-quoted strings (when ANSI_QUOTES is disabled)")
    void shouldHandleDoubleQuotedStrings() {
        String sql = "INSERT INTO users (name) VALUES (\"John\"); SELECT * FROM users";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO users (name) VALUES (\"John\")", statements.get(0).getSql());
        assertEquals("SELECT * FROM users", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Should handle mixed comment styles (all three types)")
    void shouldHandleMixedCommentStyles() {
        String sql = "SELECT 1; -- dash comment\n" +
                     "SELECT 2; # hash comment\n" +
                     "/* block comment */ SELECT 3;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(3, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
        assertEquals("SELECT 3", statements.get(2).getSql());
    }

    @Test
    @DisplayName("MySQL: Should handle # inside strings (not a comment)")
    void shouldHandleHashInsideStrings() {
        String sql = "INSERT INTO tags (tag) VALUES ('#hashtag'); SELECT * FROM tags";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("#hashtag"));
    }

    @Test
    @DisplayName("MySQL: Should handle DELIMITER command for stored procedures and validate final delimiter")
    void shouldHandleDelimiterCommand() {
        String sql = "DELIMITER //\n" +
            "CREATE PROCEDURE test_proc()\n" +
            "BEGIN\n" +
            "  SELECT 1;\n" +
            "  SELECT 2;\n" +
            "END//\n" +
            "DELIMITER ;\n" +
            "SELECT 100";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // After DELIMITER //, semicolons should not split, only // should
        // After DELIMITER ;, back to normal semicolon splitting
        // Expected: 2 statements (procedure + SELECT 100)
        assertEquals(2, statements.size(), "Should split procedure and final SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertEquals("SELECT 100", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Should handle newline escape \\n in strings")
    void shouldHandleNewlineEscape() {
        String sql = "INSERT INTO log (msg) VALUES ('line1\\nline2'); SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("\\n"));
    }

    @Test
    @DisplayName("MySQL: Should handle tab escape \\t in strings")
    void shouldHandleTabEscape() {
        String sql = "INSERT INTO data (val) VALUES ('col1\\tcol2'); SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("\\t"));
    }

    @Test
    @DisplayName("MySQL: Should NOT split on semicolons after DELIMITER change")
    void shouldNotSplitOnSemicolonAfterDelimiterChange() {
        String sql = "DELIMITER $$\n" +
            "CREATE FUNCTION add_numbers(a INT, b INT) RETURNS INT\n" +
            "BEGIN\n" +
            "  DECLARE result INT;\n" +
            "  SET result = a + b;\n" +
            "  RETURN result;\n" +
            "END$$\n" +
            "DELIMITER ;\n" +
            "SELECT add_numbers(1, 2);";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Semicolons inside function should NOT cause split when delimiter is $$
        // Expected: 2 statements (function + SELECT)
        assertEquals(2, statements.size(), "Should have function and SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"));
        assertTrue(statements.get(0).getSql().contains("SET result = a + b"));
        assertEquals("SELECT add_numbers(1, 2)", statements.get(1).getSql().trim());
    }

    @Test
    @DisplayName("MySQL: Should handle multiple DELIMITER changes in same script")
    void shouldHandleMultipleDelimiterChanges() {
        String sql = "SELECT 1;\n" +
            "DELIMITER //\n" +
            "CREATE PROCEDURE proc1() BEGIN SELECT 2; END//\n" +
            "DELIMITER $$\n" +
            "CREATE PROCEDURE proc2() BEGIN SELECT 3; END$$\n" +
            "DELIMITER ;\n" +
            "SELECT 4;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Expected: 4 statements (SELECT 1, proc1, proc2, SELECT 4)
        assertEquals(4, statements.size(), "Should split on each delimiter correctly");
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertTrue(statements.get(1).getSql().toUpperCase().contains("CREATE PROCEDURE PROC1"));
        assertTrue(statements.get(2).getSql().toUpperCase().contains("CREATE PROCEDURE PROC2"));
        assertEquals("SELECT 4", statements.get(3).getSql());
    }

    @Test
    @DisplayName("MySQL: DELIMITER should be case-insensitive")
    void delimiterShouldBeCaseInsensitive() {
        String sql = "delimiter //\n" +
            "CREATE PROCEDURE test() BEGIN SELECT 1; END//\n" +
            "DeLiMiTeR ;\n" +
            "SELECT 2;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Should not split inside CREATE PROCEDURE with BEGIN/END block (basic context detection)")
    void shouldNotSplitInsideCreateProcedureWithBeginEnd() {
        String sql = "CREATE PROCEDURE update_stats()\n" +
            "BEGIN\n" +
            "  DECLARE count INT;\n" +
            "  SELECT COUNT(*) INTO count FROM users;\n" +
            "  UPDATE statistics SET total = count;\n" +
            "END;\n" +
            "SELECT 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Without DELIMITER, this relies on BEGIN/END block detection
        assertEquals(2, statements.size(), "Should have procedure and SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().contains("SELECT COUNT(*)"));
        assertTrue(statements.get(0).getSql().contains("UPDATE statistics"));
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Should not split inside CREATE FUNCTION with BEGIN/END block")
    void shouldNotSplitInsideCreateFunctionWithBeginEnd() {
        String sql = "CREATE FUNCTION get_user_count() RETURNS INT\n" +
            "BEGIN\n" +
            "  DECLARE result INT;\n" +
            "  SELECT COUNT(*) INTO result FROM users;\n" +
            "  RETURN result;\n" +
            "END;\n" +
            "SELECT get_user_count();";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have function and SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"),
            "Statement 0 should contain CREATE FUNCTION. Got: " + statements.get(0).getSql());
        assertTrue(statements.get(0).getSql().contains("DECLARE result"),
            "Statement 0 should contain DECLARE result. Got: " + statements.get(0).getSql());
        assertTrue(statements.get(0).getSql().contains("RETURN result"),
            "Statement 0 should contain RETURN result. Got: " + statements.get(0).getSql());
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT GET_USER_COUNT"),
            "Statement 1 should contain SELECT GET_USER_COUNT. Got: " + statements.get(1).getSql());
    }

    @Test
    @DisplayName("MySQL: Internal spaces inside backslash-escaped strings should be preserved")
    void backslashEscapedStringPreserved() {
        List<SqlStatement> result = sqlSplitter.split("INSERT INTO t VALUES ('It\\'s   fine');");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getSql().contains("'It\\'s   fine'"),
            "Multiple spaces inside backslash-escaped strings must be preserved as-is");
    }

    @Test
    @DisplayName("MySQL: Should handle nested BEGIN/END blocks in stored procedures")
    void shouldHandleNestedBeginEndInProcedures() {
        String sql = "CREATE PROCEDURE complex_proc()\n" +
            "BEGIN\n" +
            "  IF (SELECT COUNT(*) FROM users) > 0 THEN\n" +
            "    BEGIN\n" +
            "      INSERT INTO log VALUES ('Users exist');\n" +
            "      UPDATE stats SET checked = 1;\n" +
            "    END;\n" +
            "  END IF;\n" +
            "END;\n" +
            "INSERT INTO audit VALUES ('done');";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have exactly 2 statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"),
            "First statement should contain CREATE PROCEDURE");
        assertTrue(statements.get(0).getSql().contains("IF (SELECT COUNT(*)"),
            "First statement should contain nested IF block");
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT INTO AUDIT"),
            "Second statement should contain INSERT INTO AUDIT");
    }
}
