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
 * Tests for SQLite-specific SQL splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in SQLite. Additionally, it tests SQLite-specific
 * features:
 * <ul>
 *   <li><b>Multiple identifier quote styles:</b> SQLite accepts backticks {@code `id`}, double quotes {@code "id"},
 *       and square brackets {@code [id]} - all are valid for identifiers</li>
 *   <li><b>ATTACH DATABASE:</b> {@code ATTACH DATABASE 'file.db' AS alias} for multiple database files</li>
 *   <li><b>SQLite-specific trigger syntax:</b> Triggers with {@code NEW} and {@code OLD} references</li>
 *   <li><b>Flexible quoting:</b> Very permissive about what can be used for identifiers</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("SQLite Splitter")
class SqliteSplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new SqliteSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new SqliteSplitter();
    }

    // ============================================================
    // SQLITE-SPECIFIC TESTS
    // ============================================================

    @Test
    @DisplayName("SQLite: Should handle backtick identifiers")
    void shouldHandleBacktickIdentifiers() {
        String sql = "CREATE TABLE `test table` (id INTEGER); INSERT INTO `test table` VALUES (1)";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("`test table`"));
        assertTrue(statements.get(1).getSql().contains("`test table`"));
    }

    @Test
    @DisplayName("SQLite: Should handle square bracket identifiers")
    void shouldHandleSquareBracketIdentifiers() {
        String sql = "CREATE TABLE [test table] (id INTEGER); INSERT INTO [test table] VALUES (1)";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("[test table]"));
        assertTrue(statements.get(1).getSql().contains("[test table]"));
    }

    @Test
    @DisplayName("SQLite: Should handle semicolons inside square brackets")
    void shouldIgnoreSemicolonInSquareBrackets() {
        String sql = "SELECT [column;name] FROM test; SELECT id FROM test";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("[column;name]"));
    }

    @Test
    @DisplayName("SQLite: Should handle mixed identifier quote styles in same script")
    void shouldHandleMixedIdentifierQuoteStyles() {
        String sql = "CREATE TABLE `table1` (id INT); CREATE TABLE \"table2\" (id INT); CREATE TABLE [table3] (id INT)";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(3, statements.size());
        assertTrue(statements.get(0).getSql().contains("`table1`"));
        assertTrue(statements.get(1).getSql().contains("\"table2\""));
        assertTrue(statements.get(2).getSql().contains("[table3]"));
    }

    @Test
    @DisplayName("SQLite: Should handle ATTACH DATABASE statements")
    void shouldHandleAttachDatabase() {
        String sql = "ATTACH DATABASE 'test.db' AS test; SELECT * FROM test.users;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("ATTACH"));
        assertTrue(statements.get(0).getSql().contains("test.db"));
    }

    @Test
    @DisplayName("SQLite: Should handle DETACH DATABASE statements")
    void shouldHandleDetachDatabase() {
        String sql = "ATTACH DATABASE 'test.db' AS test; DETACH DATABASE test;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(1).getSql().toUpperCase().contains("DETACH"));
    }

    @Test
    @DisplayName("SQLite: Should handle triggers with NEW and OLD references")
    void shouldHandleTriggersWithNewOld() {
        String sql = "CREATE TRIGGER update_timestamp\n" +
            "AFTER UPDATE ON users\n" +
            "BEGIN\n" +
            "  UPDATE users SET modified = datetime('now') WHERE id = NEW.id;\n" +
            "END;\n" +
            "SELECT * FROM users;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // TDD rule: Test both "don't split inside trigger" AND "do split after trigger"
        assertEquals(2, statements.size(), "Should have trigger and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
        assertTrue(statements.get(0).getSql().contains("NEW.id"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("SQLite: Should handle BEFORE INSERT trigger")
    void shouldHandleBeforeInsertTrigger() {
        String sql = "CREATE TRIGGER validate_data\n" +
            "BEFORE INSERT ON users\n" +
            "BEGIN\n" +
            "  SELECT CASE WHEN NEW.age < 0 THEN RAISE(ABORT, 'Invalid age') END;\n" +
            "END;\n" +
            "INSERT INTO users VALUES (1, 'test', 25);";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // TDD rule: Test both "don't split inside trigger" AND "do split after trigger"
        assertEquals(2, statements.size(), "Should have trigger and INSERT as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("BEFORE INSERT"));
        assertTrue(statements.get(0).getSql().contains("RAISE"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT INTO USERS"));
    }

    @Test
    @DisplayName("SQLite: Should handle INSTEAD OF trigger on views")
    void shouldHandleInsteadOfTrigger() {
        String sql = "CREATE TRIGGER view_insert\n" +
            "INSTEAD OF INSERT ON user_view\n" +
            "BEGIN\n" +
            "  INSERT INTO users VALUES (NEW.id, NEW.name);\n" +
            "END;\n" +
            "SELECT COUNT(*) FROM user_view;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // TDD rule: Test both "don't split inside trigger" AND "do split after trigger"
        assertEquals(2, statements.size(), "Should have trigger and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("INSTEAD OF"));
        assertTrue(statements.get(0).getSql().contains("NEW.id"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("SQLite: Should handle PRAGMA statements")
    void shouldHandlePragmaStatements() {
        String sql = "PRAGMA foreign_keys = ON; CREATE TABLE test (id INTEGER);";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("PRAGMA"));
    }

    @Test
    @DisplayName("SQLite: Should handle escaped square brackets inside identifiers")
    void shouldHandleEscapedSquareBrackets() {
        String sql = "CREATE TABLE [test]]table] (id INTEGER); SELECT * FROM [test]]table]";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("[test]]table]"));
    }

    @Test
    @DisplayName("SQLite: Should handle trigger with multiple statements inside BEGIN/END")
    void shouldHandleTriggerWithMultipleInternalStatements() {
        String sql = "CREATE TRIGGER audit_log\n" +
            "AFTER DELETE ON users\n" +
            "BEGIN\n" +
            "  INSERT INTO audit_log VALUES (OLD.id, 'deleted');\n" +
            "  UPDATE stats SET user_count = user_count - 1;\n" +
            "  DELETE FROM user_sessions WHERE user_id = OLD.id;\n" +
            "END;\n" +
            "SELECT COUNT(*) FROM audit_log;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Should NOT split on semicolons inside BEGIN/END block
        assertEquals(2, statements.size(), "Should have trigger and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
        assertTrue(statements.get(0).getSql().contains("INSERT INTO audit_log"));
        assertTrue(statements.get(0).getSql().contains("UPDATE stats"));
        assertTrue(statements.get(0).getSql().contains("DELETE FROM user_sessions"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("SQLite: Should handle trigger with WHEN clause")
    void shouldHandleTriggerWithWhenClause() {
        String sql = "CREATE TRIGGER conditional_log\n" +
            "AFTER UPDATE ON users\n" +
            "WHEN NEW.status = 'active'\n" +
            "BEGIN\n" +
            "  INSERT INTO active_users_log VALUES (NEW.id, datetime('now'));\n" +
            "END;\n" +
            "UPDATE users SET status = 'active' WHERE id = 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have trigger with WHEN clause and UPDATE as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("WHEN"));
        assertTrue(statements.get(0).getSql().contains("NEW.status"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("UPDATE USERS"));
    }

    @Test
    @DisplayName("SQLite: Should handle single-statement trigger without BEGIN/END")
    void shouldHandleSingleStatementTriggerWithoutBlock() {
        String sql = "CREATE TRIGGER simple_log\n" +
            "AFTER INSERT ON users\n" +
            "INSERT INTO log VALUES (NEW.id);\n" +
            "SELECT * FROM log;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // SQLite allows triggers without BEGIN/END for single statements
        assertEquals(2, statements.size(), "Should have trigger and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("AFTER INSERT"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("SQLite: Should handle FOR EACH ROW clause in triggers")
    void shouldHandleForEachRowInTrigger() {
        String sql = "CREATE TRIGGER row_trigger\n" +
            "BEFORE UPDATE OF name ON users\n" +
            "FOR EACH ROW\n" +
            "BEGIN\n" +
            "  SELECT RAISE(FAIL, 'Cannot change name') WHERE OLD.name IS NOT NULL;\n" +
            "END;\n" +
            "CREATE TABLE audit (id INTEGER);";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have trigger and CREATE TABLE as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("FOR EACH ROW"));
        assertTrue(statements.get(0).getSql().contains("RAISE"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("CREATE TABLE"));
    }
}
