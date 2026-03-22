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
 * Tests for IBM DB2-specific SQL splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in DB2. Additionally, it tests DB2-specific
 * features:
 * <ul>
 *   <li>BEGIN ATOMIC blocks (DB2-specific keyword combination)</li>
 *   <li>LANGUAGE SQL clause in procedures/functions</li>
 *   <li>REFERENCING clause in triggers</li>
 *   <li>@ delimiter in CLP (Command Line Processor)</li>
 *   <li>DB2-specific procedure/function/trigger syntax</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("DB2 Splitter")
class Db2SplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new Db2Splitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new Db2Splitter();
    }

    // ============================================================
    // DB2-SPECIFIC TESTS
    // ============================================================

    // ============================================================
    // DB2-SPECIFIC: CREATE PROCEDURE with LANGUAGE SQL
    // ============================================================

    @Test
    @DisplayName("DB2: Should not split inside CREATE PROCEDURE with LANGUAGE SQL")
    void shouldNotSplitInsideCreateProcedure() {
        String sql = "CREATE PROCEDURE add_user()\n" +
            "LANGUAGE SQL\n" +
            "BEGIN\n" +
            "  INSERT INTO users (id, name) VALUES (1, 'Admin');\n" +
            "  COMMIT;\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("LANGUAGE SQL"));
    }

    // ============================================================
    // DB2-SPECIFIC: CREATE FUNCTION with BEGIN ATOMIC
    // ============================================================

    @Test
    @DisplayName("DB2: Should not split inside CREATE FUNCTION with BEGIN ATOMIC")
    void shouldNotSplitInsideCreateFunction() {
        String sql = "CREATE FUNCTION get_total()\n" +
            "RETURNS INTEGER\n" +
            "LANGUAGE SQL\n" +
            "BEGIN ATOMIC\n" +
            "  DECLARE total INTEGER;\n" +
            "  SELECT COUNT(*) INTO total FROM users;\n" +
            "  RETURN total;\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("BEGIN ATOMIC"));
    }

    @Test
    @DisplayName("DB2: Should handle CREATE FUNCTION followed by another statement")
    void shouldHandleCreateFunctionFollowedByStatement() {
        String sql = "CREATE FUNCTION f() RETURNS INT LANGUAGE SQL BEGIN ATOMIC RETURN 1; END;\n" +
                     "SELECT 2;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT 2"));
    }

    // ============================================================
    // DB2-SPECIFIC: CREATE TRIGGER with REFERENCING and BEGIN ATOMIC
    // ============================================================

    @Test
    @DisplayName("DB2: Should not split inside CREATE TRIGGER with REFERENCING")
    void shouldNotSplitInsideCreateTrigger() {
        String sql = "CREATE TRIGGER update_timestamp\n" +
            "AFTER UPDATE ON users\n" +
            "REFERENCING NEW AS n\n" +
            "FOR EACH ROW\n" +
            "BEGIN ATOMIC\n" +
            "  SET n.modified = CURRENT TIMESTAMP;\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE TRIGGER"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("REFERENCING"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("BEGIN ATOMIC"));
    }

    @Test
    @DisplayName("DB2: Should handle trigger with multiple statements in body")
    void shouldHandleTriggerWithMultipleStatements() {
        String sql = "CREATE TRIGGER log_changes\n" +
            "AFTER UPDATE ON users\n" +
            "REFERENCING NEW AS n OLD AS o\n" +
            "FOR EACH ROW\n" +
            "BEGIN ATOMIC\n" +
            "  INSERT INTO audit_log VALUES (n.id, 'UPDATE');\n" +
            "  UPDATE statistics SET count = count + 1;\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().contains("INSERT INTO audit_log"));
        assertTrue(statements.get(0).getSql().contains("UPDATE statistics"));
    }

    // ============================================================
    // DB2-SPECIFIC: BEGIN ATOMIC blocks (standalone)
    // ============================================================

    @Test
    @DisplayName("DB2: Should not split inside standalone BEGIN ATOMIC block")
    void shouldNotSplitInsideBeginAtomicBlock() {
        String sql = "BEGIN ATOMIC\n" +
            "  DECLARE v_count INTEGER;\n" +
            "  SELECT COUNT(*) INTO v_count FROM users;\n" +
            "  INSERT INTO audit VALUES (v_count);\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("BEGIN ATOMIC"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("DECLARE"));
    }

    @Test
    @DisplayName("DB2: Should handle BEGIN ATOMIC with multiple semicolons inside")
    void shouldHandleBeginAtomicWithMultipleSemicolons() {
        String sql = "BEGIN ATOMIC\n" +
            "  DECLARE x INT;\n" +
            "  SET x = 1;\n" +
            "  INSERT INTO test VALUES (x);\n" +
            "  COMMIT;\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().contains("SET x = 1"));
        assertTrue(statements.get(0).getSql().contains("INSERT"));
        assertTrue(statements.get(0).getSql().contains("COMMIT"));
    }

    // ============================================================
    // DB2-SPECIFIC: CLP @ delimiter
    // ============================================================

    @Test
    @DisplayName("DB2 CLP: Should split on @ delimiter when used in CLP scripts")
    void shouldSplitOnAtDelimiterInCLP() {
        // In DB2 CLP (Command Line Processor), @ is often used as delimiter
        String sql = "CREATE PROCEDURE test()\n" +
            "BEGIN\n" +
            "  INSERT INTO users VALUES (1);\n" +
            "END@\n" +
            "SELECT * FROM users@";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Expected: 2 statements (procedure + SELECT)
        assertEquals(2, statements.size(), "Should split on @ delimiter");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT * FROM USERS"));
    }

    @Test
    @DisplayName("DB2 CLP: Should not split on @ inside string literals")
    void shouldNotSplitOnAtInsideStrings() {
        String sql = "INSERT INTO emails (address) VALUES ('user@domain.com')@\n" +
                     "SELECT * FROM emails@";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // @ inside 'user@domain.com' should NOT cause split
        // Only the @ after closing quote should be delimiter
        assertEquals(2, statements.size(), "Should split on @ delimiter but not inside strings");
        assertTrue(statements.get(0).getSql().contains("user@domain.com"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT * FROM EMAILS"));
    }

    @Test
    @DisplayName("DB2 CLP: Should not split on @ inside block comments")
    void shouldNotSplitOnAtInsideBlockComments() {
        String sql = "SELECT 1@\n" +
                     "/* Email: admin@company.com\n" +
                     "Contact: support@help.org @ */\n" +
                     "SELECT 2@";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // @ inside block comment should NOT cause split
        assertEquals(2, statements.size(), "Should not split on @ inside block comments");
        assertTrue(statements.get(0).getSql().trim().startsWith("SELECT 1"));
        assertTrue(statements.get(1).getSql().trim().startsWith("SELECT 2"));
    }

    @Test
    @DisplayName("DB2 CLP: Should not split on @ inside line comments")
    void shouldNotSplitOnAtInsideLineComments() {
        String sql = "SELECT 1@\n" +
                     "-- Email: admin@company.com @\n" +
                     "SELECT 2@";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // @ inside line comment should NOT cause split
        assertEquals(2, statements.size(), "Should not split on @ inside line comments");
        assertTrue(statements.get(0).getSql().trim().startsWith("SELECT 1"));
        assertTrue(statements.get(1).getSql().trim().startsWith("SELECT 2"));
    }

    @Test
    @DisplayName("DB2 CLP: Should support mixing @ and ; delimiters")
    void shouldSupportMixingAtAndSemicolonDelimiters() {
        String sql = "INSERT INTO users VALUES (1);\n" +
                     "INSERT INTO users VALUES (2)@\n" +
                     "SELECT * FROM users;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Both ; and @ should work as delimiters
        assertEquals(3, statements.size(), "Should support both ; and @ as delimiters");
        assertTrue(statements.get(0).getSql().contains("VALUES (1)"));
        assertTrue(statements.get(1).getSql().contains("VALUES (2)"));
        assertTrue(statements.get(2).getSql().toUpperCase().contains("SELECT * FROM USERS"));
    }

    // ============================================================
    // DB2-SPECIFIC: Complex combinations
    // ============================================================

    @Test
    @DisplayName("DB2: Should handle SELECT INTO inside BEGIN ATOMIC")
    void shouldHandleSelectIntoInsideBeginAtomic() {
        String sql = "BEGIN ATOMIC\n" +
            "  DECLARE total INT;\n" +
            "  SELECT COUNT(*) INTO total FROM users WHERE active = 1;\n" +
            "  UPDATE summary SET user_count = total;\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().contains("SELECT COUNT(*)"));
        assertTrue(statements.get(0).getSql().contains("INTO total"));
    }

    @Test
    @DisplayName("DB2: Should handle nested BEGIN/END blocks if supported")
    void shouldHandleNestedBeginEndBlocks() {
        String sql = "BEGIN ATOMIC\n" +
            "  IF (SELECT COUNT(*) FROM users) > 0 THEN\n" +
            "    BEGIN\n" +
            "      INSERT INTO log VALUES ('Users exist');\n" +
            "    END;\n" +
            "  END IF;\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("BEGIN ATOMIC"));
        assertTrue(statements.get(0).getSql().contains("INSERT INTO log"));
    }
}
