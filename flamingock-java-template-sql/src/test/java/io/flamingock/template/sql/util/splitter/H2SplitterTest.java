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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for H2 Database-specific SQL splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in H2. Additionally, it tests H2-specific
 * features:
 * <ul>
 *   <li>CREATE ALIAS for Java functions (H2-specific)</li>
 *   <li>Dollar-quoted strings $$ (PostgreSQL compatibility mode)</li>
 *   <li>Backticks ` (MySQL compatibility mode)</li>
 *   <li>Multi-mode support (can emulate PostgreSQL, MySQL, etc.)</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("H2 Database Splitter")
class H2SplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new H2Splitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new H2Splitter();
    }

    // ============================================================
    // H2-SPECIFIC TESTS
    // ============================================================

    // ============================================================
    // H2-SPECIFIC: CREATE ALIAS for Java functions
    // ============================================================

    @Test
    @DisplayName("H2: Should handle CREATE ALIAS for Java functions")
    void shouldHandleCreateAlias() {
        String sql = "CREATE ALIAS MY_FUNCTION FOR \"com.example.MyClass.myMethod\"; SELECT MY_FUNCTION(1);";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE ALIAS"));
        assertTrue(statements.get(0).getSql().contains("com.example.MyClass.myMethod"));
    }

    @Test
    @DisplayName("H2: Should handle CREATE ALIAS with deterministic flag")
    void shouldHandleCreateAliasDeterministic() {
        String sql = "CREATE ALIAS IF NOT EXISTS HASH FOR \"org.h2.util.Utils.hash\" DETERMINISTIC;\n" +
            "SELECT HASH('test');";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        // TDD rule: Test both "don't split inside alias" AND "do split after alias"
        assertEquals(2, statements.size(), "Should have alias and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().contains("DETERMINISTIC"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    // ============================================================
    // H2-SPECIFIC: PostgreSQL compatibility mode ($$)
    // ============================================================

    @Test
    @DisplayName("H2: Should handle dollar-quoted strings in PostgreSQL compatibility mode")
    void shouldHandleDollarQuotedStringsPostgreSQLMode() {
        String sql = "INSERT INTO test (code) VALUES ($$SELECT 1;$$); SELECT 2;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("$$SELECT 1;$$"));
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    @Test
    @DisplayName("H2: Should handle CREATE FUNCTION with $$ body in PostgreSQL mode")
    void shouldHandleCreateFunctionWithDollarQuotes() {
        String sql = "CREATE FUNCTION add(a INT, b INT) RETURNS INT AS $$\n" +
            "  RETURN a + b;\n" +
            "$$ LANGUAGE SQL;\n" +
            "SELECT add(1, 2);";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // TDD rule: Test both "don't split inside $$" AND "do split after function"
        assertEquals(2, statements.size(), "Should have function and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().contains("$$"));
        assertTrue(statements.get(0).getSql().contains("RETURN a + b"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    // ============================================================
    // H2-SPECIFIC: MySQL compatibility mode (backticks)
    // ============================================================

    @Test
    @DisplayName("H2: Should handle backticks in MySQL compatibility mode")
    void shouldHandleBackticksInMySQLMode() {
        String sql = "CREATE TABLE `test table` (id INT); INSERT INTO `test table` VALUES (1)";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("`test table`"));
        assertTrue(statements.get(1).getSql().contains("`test table`"));
    }

    @Test
    @DisplayName("H2: Should ignore semicolons inside backticks in MySQL mode")
    void shouldIgnoreSemicolonInBackticks() {
        String sql = "SELECT `col;name` FROM test; SELECT id FROM test;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("`col;name`"));
    }

    // ============================================================
    // H2-SPECIFIC: Embedded database features
    // ============================================================

    @Test
    @DisplayName("H2: Should handle RUNSCRIPT command")
    void shouldHandleRunScript() {
        String sql = "RUNSCRIPT FROM 'schema.sql'; SELECT * FROM users;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("RUNSCRIPT"));
    }

    @Test
    @DisplayName("H2: Should handle SCRIPT command")
    void shouldHandleScript() {
        String sql = "SCRIPT TO 'backup.sql'; SELECT COUNT(*) FROM users;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("SCRIPT"));
    }

    // ============================================================
    // H2 EDGE CASES: Mixed modes and advanced features
    // ============================================================

    @Test
    @DisplayName("H2: Should handle mixed mode (backticks + dollar quotes in same script)")
    void shouldHandleMixedMode() {
        String sql = "CREATE TABLE `users` (id INT);\n" +
            "CREATE FUNCTION get_user(id INT) RETURNS VARCHAR AS $$\n" +
            "  SELECT name FROM users WHERE id = $1;\n" +
            "$$ LANGUAGE SQL;\n" +
            "SELECT * FROM `users`;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(3, statements.size(), "Should handle MySQL backticks and PostgreSQL $$ in same script");
        assertTrue(statements.get(0).getSql().contains("`users`"));
        assertTrue(statements.get(1).getSql().contains("$$"));
        assertTrue(statements.get(2).getSql().contains("`users`"));
    }

    @Test
    @DisplayName("H2: Should handle E-strings (PostgreSQL compatibility)")
    void shouldHandleEStrings() {
        String sql = "INSERT INTO logs (message) VALUES (E'Line 1\\nLine 2\\tTabbed');\n" +
            "SELECT * FROM logs;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should handle E-strings without splitting on escaped chars");
        assertTrue(statements.get(0).getSql().contains("E'Line 1\\nLine 2\\tTabbed'"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("H2: Should handle BEGIN/END blocks in procedures")
    void shouldHandleBeginEndBlocks() {
        String sql = "CREATE PROCEDURE update_user(user_id INT, new_name VARCHAR)\n" +
            "BEGIN\n" +
            "  UPDATE users SET name = new_name WHERE id = user_id;\n" +
            "  INSERT INTO audit_log VALUES (user_id, 'updated');\n" +
            "END;\n" +
            "CALL update_user(1, 'John');";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should not split on semicolons inside BEGIN/END");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().contains("UPDATE users"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("CALL"));
    }

    @Test
    @DisplayName("H2: Should handle CREATE TRIGGER with BEGIN/END")
    void shouldHandleCreateTrigger() {
        String sql = "CREATE TRIGGER audit_trigger AFTER INSERT ON users\n" +
            "FOR EACH ROW\n" +
            "BEGIN ATOMIC\n" +
            "  INSERT INTO audit_log VALUES (NEW.id, CURRENT_TIMESTAMP);\n" +
            "END;\n" +
            "INSERT INTO users VALUES (1, 'test');";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should not split inside trigger body");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
        assertTrue(statements.get(0).getSql().contains("BEGIN ATOMIC"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT INTO USERS"));
    }

    @Test
    @DisplayName("H2: Should handle MERGE statement (H2-specific)")
    void shouldHandleMergeStatement() {
        String sql = "MERGE INTO users (id, name) KEY(id) VALUES (1, 'John');\n" +
            "SELECT * FROM users;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split MERGE and SELECT correctly");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("MERGE INTO"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("H2: Should handle nested dollar quotes with tags")
    void shouldHandleNestedDollarQuotesWithTags() {
        String sql = "CREATE FUNCTION complex_func() RETURNS VARCHAR AS $outer$\n" +
            "  DECLARE code VARCHAR = $inner$SELECT 1;$inner$;\n" +
            "  RETURN code;\n" +
            "$outer$ LANGUAGE SQL;\n" +
            "SELECT complex_func();";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should handle nested dollar quotes with different tags");
        assertTrue(statements.get(0).getSql().contains("$outer$"));
        assertTrue(statements.get(0).getSql().contains("$inner$"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("H2: Should handle CREATE ALIAS with inline Java code")
    void shouldHandleCreateAliasWithJavaCode() {
        String sql = "CREATE ALIAS REVERSE AS $$\n" +
            "String reverse(String s) {\n" +
            "  return new StringBuilder(s).reverse().toString();\n" +
            "}\n" +
            "$$;\n" +
            "SELECT REVERSE('hello');";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should handle CREATE ALIAS with Java code in $$");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE ALIAS"));
        assertTrue(statements.get(0).getSql().contains("StringBuilder"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }
}
