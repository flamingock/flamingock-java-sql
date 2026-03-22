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
 * Tests for Oracle Database-specific SQL statement splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in Oracle. Additionally, it tests Oracle-specific
 * features:
 * <ul>
 *   <li><b>Forward slash delimiter:</b> Oracle uses {@code /} instead of {@code ;} for PL/SQL blocks</li>
 *   <li><b>PL/SQL blocks:</b> BEGIN/END blocks terminated with {@code /}</li>
 *   <li><b>Stored procedures/functions/packages:</b> CREATE statements with PL/SQL bodies</li>
 *   <li><b>Anonymous blocks:</b> DECLARE ... BEGIN ... END; blocks</li>
 *   <li><b>Q-strings:</b> Alternative quoting mechanism {@code q'[text]'}</li>
 *   <li><b>Semicolons in PL/SQL:</b> Should NOT cause splits (only {@code /} splits)</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("Oracle Splitter")
class OracleSplitterTest extends AbstractSqlSplitterTest {

    private final OracleSplitter sqlSplitter = new OracleSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new OracleSplitter();
    }

    // ============================================================
    // ORACLE-SPECIFIC TESTS
    // ============================================================

    // ============================================================
    // BASIC DELIMITER TESTS
    // ============================================================

    @Test
    @DisplayName("Oracle: Should NOT split on semicolons (uses / as delimiter)")
    void shouldNotSplitOnSemicolons() {
        // Oracle uses "/" as delimiter, so ";" should not split
        String sql = "SELECT 1; SELECT 2;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Since it uses "/", and sql has no "/", should return single statement
        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().contains("SELECT 1"));
        assertTrue(statements.get(0).getSql().contains("SELECT 2"));
    }

    @Test
    @DisplayName("Oracle: Should split on forward slash /")
    void shouldSplitOnSlash() {
        // Oracle should split on "/"
        String sql = "SELECT 1;\n/\nSELECT 2;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("SELECT 1"));
        assertTrue(statements.get(1).getSql().contains("SELECT 2"));
    }

    // ============================================================
    // PL/SQL BLOCKS
    // ============================================================

    @Test
    @DisplayName("Oracle: Should handle anonymous PL/SQL block with BEGIN/END")
    void shouldHandleAnonymousBlock() {
        String sql = "BEGIN\n" +
            "  DBMS_OUTPUT.PUT_LINE('Hello');\n" +
            "  INSERT INTO logs VALUES ('test');\n" +
            "END;\n" +
            "/\n" +
            "SELECT * FROM logs;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // TDD: Test both "don't split on ; inside block" AND "do split on /"
        assertEquals(2, statements.size(), "Should have PL/SQL block and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().contains("BEGIN"));
        assertTrue(statements.get(0).getSql().contains("DBMS_OUTPUT"));
        assertTrue(statements.get(0).getSql().contains("INSERT INTO logs"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("Oracle: Should handle DECLARE block with variables")
    void shouldHandleDeclareBlock() {
        String sql = "DECLARE\n" +
            "  v_count NUMBER;\n" +
            "BEGIN\n" +
            "  SELECT COUNT(*) INTO v_count FROM users;\n" +
            "  DBMS_OUTPUT.PUT_LINE('Count: ' || v_count);\n" +
            "END;\n" +
            "/\n" +
            "CREATE TABLE test (id NUMBER);";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have DECLARE block and CREATE TABLE as separate statements");
        assertTrue(statements.get(0).getSql().contains("DECLARE"));
        assertTrue(statements.get(0).getSql().contains("v_count"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("CREATE TABLE"));
    }

    // ============================================================
    // STORED PROCEDURES AND FUNCTIONS
    // ============================================================

    @Test
    @DisplayName("Oracle: Should handle CREATE PROCEDURE with PL/SQL body")
    void shouldHandleCreateProcedure() {
        String sql = "CREATE OR REPLACE PROCEDURE update_user(p_id IN NUMBER, p_name IN VARCHAR2) IS\n" +
            "BEGIN\n" +
            "  UPDATE users SET name = p_name WHERE id = p_id;\n" +
            "  COMMIT;\n" +
            "END update_user;\n" +
            "/\n" +
            "GRANT EXECUTE ON update_user TO public;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have procedure and GRANT as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE OR REPLACE PROCEDURE"));
        assertTrue(statements.get(0).getSql().contains("UPDATE users"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("GRANT"));
    }

    @Test
    @DisplayName("Oracle: Should handle CREATE FUNCTION with RETURN")
    void shouldHandleCreateFunction() {
        String sql = "CREATE OR REPLACE FUNCTION calculate_tax(p_amount IN NUMBER) RETURN NUMBER IS\n" +
            "  v_rate CONSTANT NUMBER := 0.15;\n" +
            "BEGIN\n" +
            "  RETURN p_amount * v_rate;\n" +
            "END;\n" +
            "/\n" +
            "SELECT calculate_tax(1000) FROM dual;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have function and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE OR REPLACE FUNCTION"));
        assertTrue(statements.get(0).getSql().contains("RETURN"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    // ============================================================
    // PACKAGES
    // ============================================================

    @Test
    @DisplayName("Oracle: Should handle CREATE PACKAGE specification")
    void shouldHandleCreatePackage() {
        String sql = "CREATE OR REPLACE PACKAGE pkg_utils AS\n" +
            "  FUNCTION get_version RETURN VARCHAR2;\n" +
            "  PROCEDURE log_message(p_msg IN VARCHAR2);\n" +
            "END pkg_utils;\n" +
            "/\n" +
            "SELECT * FROM user_packages;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have package spec and SELECT as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE OR REPLACE PACKAGE"));
        assertTrue(statements.get(0).getSql().contains("FUNCTION get_version"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("Oracle: Should handle CREATE PACKAGE BODY")
    void shouldHandleCreatePackageBody() {
        String sql = "CREATE OR REPLACE PACKAGE BODY pkg_utils AS\n" +
            "  FUNCTION get_version RETURN VARCHAR2 IS\n" +
            "  BEGIN\n" +
            "    RETURN '1.0.0';\n" +
            "  END;\n" +
            "  PROCEDURE log_message(p_msg IN VARCHAR2) IS\n" +
            "  BEGIN\n" +
            "    INSERT INTO log_table VALUES (p_msg, SYSDATE);\n" +
            "  END;\n" +
            "END pkg_utils;\n" +
            "/\n" +
            "EXEC pkg_utils.log_message('test');";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have package body and EXEC as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE OR REPLACE PACKAGE BODY"));
        assertTrue(statements.get(0).getSql().contains("get_version"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("EXEC"));
    }

    // ============================================================
    // TRIGGERS
    // ============================================================

    @Test
    @DisplayName("Oracle: Should handle CREATE TRIGGER with PL/SQL body")
    void shouldHandleCreateTrigger() {
        String sql = "CREATE OR REPLACE TRIGGER trg_audit_users\n" +
            "AFTER UPDATE ON users\n" +
            "FOR EACH ROW\n" +
            "BEGIN\n" +
            "  INSERT INTO audit_log VALUES (:OLD.id, :NEW.name, SYSDATE);\n" +
            "  COMMIT;\n" +
            "END;\n" +
            "/\n" +
            "UPDATE users SET name = 'test' WHERE id = 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have trigger and UPDATE as separate statements");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE OR REPLACE TRIGGER"));
        assertTrue(statements.get(0).getSql().contains(":OLD"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("UPDATE USERS"));
    }

    // ============================================================
    // NESTED BLOCKS
    // ============================================================

    @Test
    @DisplayName("Oracle: Should handle nested BEGIN/END blocks")
    void shouldHandleNestedBlocks() {
        String sql = "BEGIN\n" +
            "  DECLARE\n" +
            "    v_temp NUMBER := 0;\n" +
            "  BEGIN\n" +
            "    v_temp := 10;\n" +
            "    DBMS_OUTPUT.PUT_LINE('Inner: ' || v_temp);\n" +
            "  END;\n" +
            "  DBMS_OUTPUT.PUT_LINE('Outer block');\n" +
            "END;\n" +
            "/\n" +
            "SELECT 1 FROM dual;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should handle nested blocks correctly");
        assertTrue(statements.get(0).getSql().contains("DECLARE"));
        assertTrue(statements.get(0).getSql().contains("Inner:"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    // ============================================================
    // EDGE CASES: / in strings and comments
    // ============================================================

    @Test
    @DisplayName("Oracle: Should NOT split on / inside single-quoted strings")
    void shouldNotSplitOnSlashInStrings() {
        String sql = "INSERT INTO paths (url) VALUES ('http://example.com/path');\n" +
            "/\n" +
            "SELECT * FROM paths;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should preserve / inside strings");
        assertTrue(statements.get(0).getSql().contains("http://example.com/path"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("Oracle: Should NOT split on / inside comments")
    void shouldNotSplitOnSlashInComments() {
        String sql = "/* This is a comment with / inside */\n" +
            "SELECT 1 FROM dual;\n" +
            "/\n" +
            "-- Another comment with /path/to/file\n" +
            "SELECT 2 FROM dual;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should preserve / inside comments");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("SELECT 1"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT 2"));
    }

    // ============================================================
    // Q-STRINGS (Oracle alternative quoting)
    // ============================================================

    @Test
    @DisplayName("Oracle: Should handle q-strings with square brackets q'[...]'")
    void shouldHandleQStringsWithBrackets() {
        String sql = "INSERT INTO messages (text) VALUES (q'[It's a quote with / and ; inside]');\n" +
            "/\n" +
            "SELECT * FROM messages;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should handle q-strings correctly");
        assertTrue(statements.get(0).getSql().contains("q'["));
        assertTrue(statements.get(0).getSql().contains("It's a quote"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("Oracle: Should handle q-strings with curly braces q'{...}'")
    void shouldHandleQStringsWithBraces() {
        String sql = "SELECT q'{This is a q-string with 'quotes' and / slash}' FROM dual;\n" +
            "/\n" +
            "SELECT 1 FROM dual;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should handle q-strings with braces");
        assertTrue(statements.get(0).getSql().contains("q'{"));
        assertTrue(statements.get(1).getSql().contains("SELECT 1"));
    }

    @Test
    @DisplayName("Oracle: Should handle q-strings with custom delimiter q'<...>'")
    void shouldHandleQStringsWithCustomDelimiter() {
        String sql = "SELECT q'<SELECT * FROM table;>' FROM dual;\n" +
            "/\n" +
            "DELETE FROM temp;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should handle q-strings with custom delimiters");
        assertTrue(statements.get(0).getSql().contains("q'<"));
        assertTrue(statements.get(0).getSql().contains("SELECT * FROM table"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("DELETE"));
    }

    @Test
    @DisplayName("Oracle: Should NOT treat 'q' inside identifier as q-string (e.g., column_nameq'...')")
    void shouldNotTreatIdentifierEndingInQAsQString() {
        // Problematic case: "iraqt" is identifier, then string 'value/' with slash inside
        // If incorrectly parsed as q't...t', it might consume past the intended boundary
        String sql = "SELECT iraqt'value/t'FROM dual;\n" +
            "/\n" +
            "SELECT 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Correct parse should recognize this as 2 statements split by /
        assertEquals(2, statements.size(), "Should correctly split even with identifier ending in 'q'");
        assertTrue(statements.get(0).getSql().contains("value"));
        assertTrue(statements.get(1).getSql().contains("SELECT 1"));
    }

    @Test
    @DisplayName("Oracle: Should NOT treat 'q' at end of string as q-string (e.g., 'manq')")
    void shouldNotTreatStringEndingInQAsQString() {
        String sql = "INSERT INTO test VALUES ('iraq', 'test');\n" +
            "/\n" +
            "SELECT * FROM test;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // 'iraq' should be parsed as a normal string, NOT as q-string
        assertEquals(2, statements.size(), "Should NOT treat string ending in 'q' as q-string");
        assertTrue(statements.get(0).getSql().contains("'iraq'"));
        assertTrue(statements.get(0).getSql().contains("'test'"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    // ============================================================
    // EXCEPTION HANDLING
    // ============================================================

    @Test
    @DisplayName("Oracle: Should handle EXCEPTION blocks in PL/SQL")
    void shouldHandleExceptionBlocks() {
        String sql = "BEGIN\n" +
            "  INSERT INTO users VALUES (1, 'test');\n" +
            "  COMMIT;\n" +
            "EXCEPTION\n" +
            "  WHEN DUP_VAL_ON_INDEX THEN\n" +
            "    DBMS_OUTPUT.PUT_LINE('Duplicate key');\n" +
            "    ROLLBACK;\n" +
            "  WHEN OTHERS THEN\n" +
            "    DBMS_OUTPUT.PUT_LINE('Error: ' || SQLERRM);\n" +
            "END;\n" +
            "/\n" +
            "SELECT COUNT(*) FROM users;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should handle EXCEPTION blocks");
        assertTrue(statements.get(0).getSql().contains("EXCEPTION"));
        assertTrue(statements.get(0).getSql().contains("DUP_VAL_ON_INDEX"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }
}
