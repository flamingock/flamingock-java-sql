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
 * Tests for Firebird-specific SQL splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests
 * (semicolon splitting, comments, strings, etc.) and adds Firebird-specific tests.
 *
 * <p>Firebird-specific features tested:
 * <ul>
 *   <li>SET TERM directive for changing delimiters</li>
 *   <li>EXECUTE BLOCK statements</li>
 *   <li>CREATE PROCEDURE with AS keyword</li>
 *   <li>Firebird-specific PSQL syntax</li>
 * </ul>
 */
@DisplayName("Firebird Splitter")
class FirebirdSplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new FirebirdSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new FirebirdSplitter();
    }

    // ============================================================
    // FIREBIRD-SPECIFIC TESTS
    // ============================================================

    // ============================================================
    // FIREBIRD-SPECIFIC: CREATE PROCEDURE with AS
    // ============================================================

    @Test
    @DisplayName("Firebird: Should not split inside CREATE PROCEDURE with AS keyword")
    void shouldNotSplitInsideCreateProcedure() {
        String sql = "CREATE PROCEDURE add_user\n" +
            "AS\n" +
            "BEGIN\n" +
            "  INSERT INTO users (id, name) VALUES (1, 'Admin');\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("AS"));
    }

    // ============================================================
    // FIREBIRD-SPECIFIC: SET TERM directive
    // ============================================================

    @Test
    @DisplayName("Firebird: Should handle SET TERM directive for delimiter change and validate restoration")
    void shouldHandleSetTermDirective() {
        String sql = "SET TERM ^;\n" +
            "CREATE PROCEDURE test\n" +
            "AS\n" +
            "BEGIN\n" +
            "  SELECT 1 FROM RDB$DATABASE;\n" +
            "END^\n" +
            "SET TERM ;^\n" +
            "SELECT 2";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // SET TERM changes delimiter from ; to ^
        // Expected: 2 statements (procedure + SELECT 2)
        assertEquals(2, statements.size(), "Should split procedure and final SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Firebird: Should handle procedure with custom delimiter after SET TERM and validate restoration")
    void shouldHandleProcedureWithCustomDelimiter() {
        String sql = "SET TERM $$;\n" +
            "CREATE PROCEDURE update_stats\n" +
            "AS\n" +
            "BEGIN\n" +
            "  UPDATE statistics SET count = count + 1;\n" +
            "END$$\n" +
            "SET TERM ;$$\n" +
            "SELECT 99";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Expected: 2 statements (procedure + SELECT 99)
        assertEquals(2, statements.size(), "Should split procedure and final SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertEquals("SELECT 99", statements.get(1).getSql());
    }

    // ============================================================
    // FIREBIRD-SPECIFIC: EXECUTE BLOCK
    // ============================================================

    @Test
    @DisplayName("Firebird: Should not split inside EXECUTE BLOCK")
    void shouldNotSplitInsideExecuteBlock() {
        String sql = "EXECUTE BLOCK\n" +
            "AS\n" +
            "BEGIN\n" +
            "  INSERT INTO test VALUES (1);\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("EXECUTE BLOCK"));
    }

    @Test
    @DisplayName("Firebird: Should handle EXECUTE BLOCK with variables")
    void shouldHandleExecuteBlockWithVariables() {
        // DECLARE VARIABLE outside BEGIN (official Firebird PSQL syntax)
        String sql = "EXECUTE BLOCK\n" +
            "AS\n" +
            "  DECLARE VARIABLE cnt INTEGER;\n" +
            "BEGIN\n" +
            "  SELECT COUNT(*) FROM users INTO :cnt;\n" +
            "  INSERT INTO audit VALUES (:cnt);\n" +
            "END;\n" +
            "SELECT * FROM audit;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Should split into 2 statements: EXECUTE BLOCK + SELECT
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("DECLARE VARIABLE"));
        assertTrue(statements.get(0).getSql().contains("INTO :cnt"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT * FROM AUDIT"));
    }

    // ============================================================
    // FIREBIRD-SPECIFIC: System tables (RDB$)
    // ============================================================

    @Test
    @DisplayName("Firebird: Should handle queries on RDB$ system tables")
    void shouldHandleSystemTables() {
        String sql = "SELECT * FROM RDB$DATABASE; SELECT * FROM RDB$RELATIONS;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("RDB$DATABASE"));
        assertTrue(statements.get(1).getSql().contains("RDB$RELATIONS"));
    }

    // ============================================================
    // FIREBIRD-SPECIFIC: PSQL context awareness
    // ============================================================

    @Test
    @DisplayName("Firebird: Should handle nested BEGIN/END in PSQL procedures")
    void shouldHandleNestedBeginEndInPSQL() {
        String sql = "CREATE PROCEDURE complex_proc\n" +
            "AS\n" +
            "BEGIN\n" +
            "  IF (1=1) THEN\n" +
            "  BEGIN\n" +
            "    INSERT INTO test VALUES (1);\n" +
            "  END\n" +
            "  ELSE\n" +
            "  BEGIN\n" +
            "    INSERT INTO test VALUES (2);\n" +
            "  END\n" +
            "END;\n" +
            "SELECT 99;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().contains("IF (1=1)"));
        assertEquals("SELECT 99", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Firebird: Should handle FOR SELECT loops in PSQL")
    void shouldHandleForSelectLoopsInPSQL() {
        // DECLARE VARIABLE outside BEGIN (official Firebird PSQL syntax)
        String sql = "CREATE PROCEDURE loop_test\n" +
            "AS\n" +
            "DECLARE VARIABLE v INT;\n" +
            "BEGIN\n" +
            "  FOR SELECT id FROM users INTO :v DO\n" +
            "  BEGIN\n" +
            "    INSERT INTO audit VALUES (:v);\n" +
            "  END\n" +
            "END;\n" +
            "INSERT INTO log VALUES ('done');";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("FOR SELECT"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT INTO LOG"));
    }

    @Test
    @DisplayName("Firebird: SET TERM should not split on old delimiter after change")
    void setTermShouldNotSplitOnOldDelimiter() {
        String sql = "SET TERM ^^;\n" +
            "CREATE PROCEDURE p1 AS BEGIN SELECT 1; SELECT 2; END^^\n" +
            "SET TERM ;^^\n" +
            "SELECT 100;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // After SET TERM ^^, semicolons inside procedure should NOT split
        assertEquals(2, statements.size(), "Should have procedure and final SELECT");
        assertTrue(statements.get(0).getSql().contains("SELECT 1"));
        assertTrue(statements.get(0).getSql().contains("SELECT 2"));
        assertEquals("SELECT 100", statements.get(1).getSql());
    }

    // ============================================================
    // FIREBIRD-SPECIFIC: SET TERM edge cases
    // ============================================================

    @Test
    @DisplayName("Firebird: SET TERM should handle custom delimiter in strings without confusion")
    void setTermShouldHandleDelimiterInStrings() {
        String sql = "SET TERM ^^;\n" +
            "CREATE PROCEDURE test AS BEGIN INSERT INTO log VALUES ('Error^^Message'); END^^\n" +
            "SET TERM ;^^\n" +
            "SELECT * FROM log;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // ^^ inside string 'Error^^Message' should NOT cause split
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("Error^^Message"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT * FROM LOG"));
    }

    @Test
    @DisplayName("Firebird: SET TERM should support multi-character delimiters")
    void setTermShouldSupportMultiCharacterDelimiters() {
        String sql = "SET TERM $$$;\n" +
            "CREATE PROCEDURE test AS BEGIN SELECT 1; END$$$\n" +
            "SET TERM ;$$$\n" +
            "SELECT 2;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Firebird: Multiple SET TERM changes in same script")
    void setTermShouldHandleMultipleChanges() {
        String sql = "SET TERM ^^;\n" +
            "CREATE PROCEDURE p1 AS BEGIN SELECT 1; END^^\n" +
            "SET TERM ;^^\n" +
            "SELECT 2;\n" +
            "SET TERM $$;\n" +
            "CREATE PROCEDURE p2 AS BEGIN SELECT 3; END$$\n" +
            "SET TERM ;$$\n" +
            "SELECT 4;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(4, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE P1"));
        assertEquals("SELECT 2", statements.get(1).getSql());
        assertTrue(statements.get(2).getSql().toUpperCase().contains("CREATE PROCEDURE P2"));
        assertEquals("SELECT 4", statements.get(3).getSql());
    }
}
