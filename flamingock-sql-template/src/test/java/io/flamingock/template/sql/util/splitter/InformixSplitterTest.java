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
 * Tests for IBM Informix-specific SQL splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests
 * (semicolon splitting, comments, strings, etc.) and adds Informix-specific tests.
 *
 * <p>Informix SQL dialect has several unique features:
 * <ul>
 *   <li><b>Curly brace comments:</b> {@code { comment }} - Informix-specific comment style</li>
 *   <li><b>END PROCEDURE keyword:</b> Procedures end with {@code END PROCEDURE}, not just {@code END}</li>
 *   <li><b>RETURNING keyword:</b> Functions declare return type with {@code RETURNING} instead of {@code RETURNS}</li>
 *   <li><b>ON EXCEPTION blocks:</b> SPL (Stored Procedure Language) exception handling with {@code ON EXCEPTION END EXCEPTION}</li>
 *   <li><b>SPL blocks:</b> Informix Stored Procedure Language with specific BEGIN/END syntax</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for standard SQL behavior tests
 */
@DisplayName("Informix Splitter")
class InformixSplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new InformixSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new InformixSplitter();
    }

    // ============================================================
    // INFORMIX-SPECIFIC TESTS
    // ============================================================

    @Test
    @DisplayName("Informix: Should strip curly brace comments { }")
    void shouldStripCurlyBraceComments() {
        String sql = "SELECT 1; { This is an Informix comment } SELECT 2;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
        assertFalse(statements.stream().map(SqlStatement::getSql).anyMatch(s -> s.contains("{")));
    }

    @Test
    @DisplayName("Informix: Should handle nested curly brace comments")
    void shouldHandleNestedCurlyBraceComments() {
        String sql = "SELECT 1; { Outer { nested } comment } SELECT 2;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Informix: Should not split inside CREATE PROCEDURE ... END PROCEDURE")
    void shouldNotSplitInsideProcedures() {
        String sql = "CREATE PROCEDURE add_user()\n" +
            "  INSERT INTO users VALUES (1, 'Admin');\n" +
            "  COMMIT WORK;\n" +
            "END PROCEDURE;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("END PROCEDURE"));
    }

    @Test
    @DisplayName("Informix: Should handle CREATE FUNCTION with RETURNING keyword")
    void shouldHandleCreateFunctionWithReturning() {
        String sql = "CREATE FUNCTION get_total()\n" +
            "RETURNING INTEGER;\n" +
            "  DEFINE total INTEGER;\n" +
            "  SELECT COUNT(*) INTO total FROM users;\n" +
            "  RETURN total;\n" +
            "END FUNCTION;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("RETURNING"));
    }

    @Test
    @DisplayName("Informix: Should handle ON EXCEPTION blocks in SPL")
    void shouldHandleOnExceptionBlocks() {
        String sql = "BEGIN\n" +
            "  ON EXCEPTION END EXCEPTION;\n" +
            "  INSERT INTO test VALUES (1);\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("ON EXCEPTION"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("END EXCEPTION"));
    }

    @Test
    @DisplayName("Informix: Should handle ON EXCEPTION with custom error handling")
    void shouldHandleOnExceptionWithErrorHandling() {
        String sql = "BEGIN\n" +
            "  ON EXCEPTION\n" +
            "    LET error_msg = 'An error occurred';\n" +
            "    RAISE EXCEPTION -746, 0, error_msg;\n" +
            "  END EXCEPTION;\n" +
            "  INSERT INTO test VALUES (1);\n" +
            "END;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("ON EXCEPTION"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("RAISE EXCEPTION"));
    }

    @Test
    @DisplayName("Informix: Should handle multiple procedures with END PROCEDURE")
    void shouldHandleMultipleProcedures() {
        String sql = "CREATE PROCEDURE proc1()\n" +
            "  INSERT INTO t1 VALUES (1);\n" +
            "END PROCEDURE;\n" +
            "CREATE PROCEDURE proc2()\n" +
            "  INSERT INTO t2 VALUES (2);\n" +
            "END PROCEDURE;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("PROC1"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("PROC2"));
    }

    @Test
    @DisplayName("Informix: Should handle DEFINE statements in SPL")
    void shouldHandleDefineStatements() {
        String sql = "CREATE FUNCTION calculate()\n" +
            "RETURNING INTEGER;\n" +
            "  DEFINE result INTEGER;\n" +
            "  DEFINE temp INTEGER;\n" +
            "  LET result = 100;\n" +
            "  RETURN result;\n" +
            "END FUNCTION;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("DEFINE RESULT"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("DEFINE TEMP"));
    }

    @Test
    @DisplayName("Informix: Should handle FOREACH loops in SPL and not split prematurely")
    void shouldHandleForEachLoops() {
        String sql = "CREATE PROCEDURE process_users()\n" +
            "  DEFINE user_id INTEGER;\n" +
            "  FOREACH cursor1 FOR SELECT id INTO user_id FROM users\n" +
            "    UPDATE audit SET processed = 1 WHERE id = user_id;\n" +
            "  END FOREACH;\n" +
            "END PROCEDURE;\n" +
            "SELECT 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("FOREACH"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("END FOREACH"));
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("Informix: Should handle nested control structures (FOREACH inside procedure)")
    void shouldHandleNestedControlStructures() {
        String sql = "CREATE PROCEDURE nested_example()\n" +
            "  DEFINE counter INTEGER;\n" +
            "  LET counter = 0;\n" +
            "  FOREACH SELECT * FROM data\n" +
            "    LET counter = counter + 1;\n" +
            "  END FOREACH;\n" +
            "  INSERT INTO summary VALUES (counter);\n" +
            "END PROCEDURE;\n" +
            "INSERT INTO log VALUES ('done');";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue(statements.get(0).getSql().toUpperCase().contains("END PROCEDURE"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("INSERT INTO LOG"));
    }
}
