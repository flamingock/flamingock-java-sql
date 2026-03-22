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
 * Tests for PostgreSQL-specific SQL statement splitting behavior.
 *
 * <p>This class extends {@link AbstractSqlSplitterTest} to inherit all generic SQL tests,
 * ensuring that standard SQL features work correctly in PostgreSQL. Additionally, it tests PostgreSQL-specific
 * features:
 * <ul>
 *   <li><b>Dollar-quoted strings:</b> {@code $$text$$} and {@code $tag$text$tag$} for embedding SQL without escaping</li>
 *   <li><b>DO $$ blocks:</b> Anonymous code blocks with {@code DO $$ BEGIN ... END $$}</li>
 *   <li><b>E-prefixed escape strings:</b> {@code E'\\n'} for C-style escape sequences</li>
 *   <li><b>Nested block comments:</b> {@code /* outer /* inner *&#47; *&#47;} - PostgreSQL allows nesting</li>
 *   <li><b>Array constructors:</b> {@code ARRAY[1,2,3]} syntax</li>
 *   <li><b>JSON operators:</b> {@code ->}, {@code ->>}, {@code #>}, {@code #>>} for JSON navigation</li>
 *   <li><b>Double-quoted identifiers:</b> Standard SQL behavior (PostgreSQL is strict about this)</li>
 * </ul>
 *
 * @see AbstractSqlSplitterTest for inherited standard SQL behavior tests
 */
@DisplayName("PostgreSQL Splitter")
class PostgreSqlSplitterTest extends AbstractSqlSplitterTest {

    private final SqlSplitter sqlSplitter = new PostgreSqlSplitter();

    @Override
    protected AbstractSqlSplitter getSplitter() {
        return new PostgreSqlSplitter();
    }

    // ============================================================
    // POSTGRESQL-SPECIFIC TESTS
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: Should handle dollar-quoted strings with $$")
    void shouldHandleDollarQuotedStrings() {
        String sql = "INSERT INTO test (code) VALUES ($$SELECT * FROM users;$$); SELECT 1;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO test (code) VALUES ($$SELECT * FROM users;$$)", statements.get(0).getSql());
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle dollar-quoted strings with custom tags")
    void shouldHandleDollarQuotedStringsWithTags() {
        String sql = "INSERT INTO test (code) VALUES ($func$SELECT * FROM users;$func$); SELECT 1;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO test (code) VALUES ($func$SELECT * FROM users;$func$)", statements.get(0).getSql());
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle dollar-quoted strings with different tag names")
    void shouldHandleDifferentDollarTags() {
        String sql = "SELECT $tag1$text1$tag1$, $tag2$text2$tag2$ FROM dual; SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("$tag1$"));
        assertTrue(statements.get(0).getSql().contains("$tag2$"));
    }

    @Test
    @DisplayName("PostgreSQL: Should not split inside DO $$ blocks and split after")
    void shouldNotSplitInsideDOBlocks() {
        String sql = "DO $$\n" +
            "BEGIN\n" +
            "  INSERT INTO users (id, name) VALUES (1, 'Admin');\n" +
            "  COMMIT;\n" +
            "END;\n" +
            "$$;\n" +
            "SELECT 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have DO block and SELECT");
        assertTrue(statements.get(0).getSql().contains("DO $$"));
        assertTrue(statements.get(0).getSql().contains("INSERT INTO users"));
        assertTrue(statements.get(0).getSql().contains("COMMIT"));
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle DO block with multiple semicolons inside")
    void shouldHandleDOBlockWithMultipleSemicolons() {
        String sql = "DO $$\n" +
            "DECLARE\n" +
            "  v_count INTEGER;\n" +
            "BEGIN\n" +
            "  SELECT COUNT(*) INTO v_count FROM users;\n" +
            "  INSERT INTO audit VALUES (v_count);\n" +
            "  RAISE NOTICE 'Count: %', v_count;\n" +
            "END;\n" +
            "$$;\n" +
            "SELECT 99;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should not split on semicolons inside DO block");
        assertTrue(statements.get(0).getSql().contains("SELECT COUNT(*)"));
        assertTrue(statements.get(0).getSql().contains("INSERT INTO audit"));
        assertTrue(statements.get(0).getSql().contains("RAISE NOTICE"));
        assertEquals("SELECT 99", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle multiple DO blocks in sequence")
    void shouldHandleMultipleDOBlocks() {
        String sql = "DO $$ BEGIN INSERT INTO t1 VALUES (1); END $$;\n" +
            "DO $$ BEGIN INSERT INTO t2 VALUES (2); END $$;\n" +
            "SELECT 100;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(3, statements.size(), "Should split each DO block separately");
        assertTrue(statements.get(0).getSql().contains("t1"));
        assertTrue(statements.get(1).getSql().contains("t2"));
        assertEquals("SELECT 100", statements.get(2).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle CREATE FUNCTION with $$ body and split after")
    void shouldHandleCreateFunctionWithDollarQuotes() {
        String sql = "CREATE FUNCTION add_user() RETURNS void AS $$\n" +
            "BEGIN\n" +
            "  INSERT INTO users VALUES (1, 'test');\n" +
            "END;\n" +
            "$$ LANGUAGE plpgsql;\n" +
            "SELECT 42;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have function and SELECT");
        assertTrue(statements.get(0).getSql().toUpperCase().contains("CREATE FUNCTION"));
        assertTrue(statements.get(0).getSql().contains("INSERT INTO users"));
        assertEquals("SELECT 42", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle semicolons inside dollar-quoted strings and split after")
    void shouldIgnoreSemicolonInDollarQuotes() {
        String sql = "CREATE FUNCTION test() RETURNS void AS $$\n" +
            "  SELECT 1;\n" +
            "  SELECT 2;\n" +
            "$$ LANGUAGE sql;\n" +
            "SELECT 999;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have function and SELECT");
        assertTrue(statements.get(0).getSql().contains("SELECT 1"));
        assertTrue(statements.get(0).getSql().contains("SELECT 2"));
        assertEquals("SELECT 999", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle multiple dollar-quoted blocks in same statement")
    void shouldHandleMultipleDollarQuotedBlocks() {
        String sql = "INSERT INTO test VALUES ($$value1$$, $$value2;$$); SELECT 1;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO test VALUES ($$value1$$, $$value2;$$)", statements.get(0).getSql());
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle nested block comments")
    void shouldHandleNestedBlockComments() {
        String sql = "/* outer /* inner */ outer */ SELECT 1; SELECT 2;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
        assertFalse(statements.get(0).getSql().contains("/*"));
    }

    @Test
    @DisplayName("PostgreSQL: Should handle deeply nested block comments and split after")
    void shouldHandleDeeplyNestedBlockComments() {
        String sql = "/* level1 /* level2 /* level3 */ level2 */ level1 */ SELECT 1; SELECT 2";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should strip comment and split statements");
        assertEquals("SELECT 1", statements.get(0).getSql());
        assertEquals("SELECT 2", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle E-prefixed escape strings")
    void shouldHandleEscapeStrings() {
        String sql = "INSERT INTO test VALUES (E'Line 1\\nLine 2'); SELECT 1;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO test VALUES (E'Line 1\\nLine 2')", statements.get(0).getSql());
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle E-strings with various escapes")
    void shouldHandleEStringWithVariousEscapes() {
        String sql = "INSERT INTO log VALUES (E'Tab:\\tNewline:\\nQuote:\\''); SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("\\t"));
        assertTrue(statements.get(0).getSql().contains("\\n"));
        assertTrue(statements.get(0).getSql().contains("\\'"));
    }

    @Test
    @DisplayName("PostgreSQL: Should handle array constructors")
    void shouldHandleArrayConstructors() {
        String sql = "INSERT INTO test VALUES (ARRAY[1,2,3]); SELECT 1;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO test VALUES (ARRAY[1,2,3])", statements.get(0).getSql());
        assertEquals("SELECT 1", statements.get(1).getSql());
    }

    @Test
    @DisplayName("PostgreSQL: Should handle nested array constructors")
    void shouldHandleNestedArrays() {
        String sql = "INSERT INTO test VALUES (ARRAY[ARRAY[1,2],ARRAY[3,4]]); SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("ARRAY[ARRAY[1,2],ARRAY[3,4]]"));
    }

    @Test
    @DisplayName("PostgreSQL: Should handle JSON operators -> and ->>")
    void shouldHandleJSONOperators() {
        String sql = "SELECT data->>'name' FROM test; SELECT data->'id' FROM test;";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("->>'name'"));
        assertTrue(statements.get(1).getSql().contains("->'id'"));
    }

    @Test
    @DisplayName("PostgreSQL: Should handle JSON path operators #> and #>>")
    void shouldHandleJSONPathOperators() {
        String sql = "SELECT data#>>'{users,0,name}' FROM test; SELECT data#>'{id}' FROM test";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("#>>"));
        assertTrue(statements.get(1).getSql().contains("#>"));
    }

    @Test
    @DisplayName("PostgreSQL: Should handle JSONB operators @> and <@")
    void shouldHandleJSONBContainmentOperators() {
        String sql = "SELECT * FROM test WHERE data @> '{\"key\":\"value\"}'; SELECT 1";
        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).getSql().contains("@>"));
    }

    @Test
    @DisplayName("PostgreSQL: Should handle DO block with custom dollar tag and split after")
    void shouldHandleDOBlockWithCustomTag() {
        String sql = "DO $body$\n" +
            "BEGIN\n" +
            "  RAISE NOTICE 'Test';\n" +
            "END;\n" +
            "$body$;\n" +
            "SELECT 777;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should have DO block and SELECT");
        assertTrue(statements.get(0).getSql().contains("DO $body$"));
        assertTrue(statements.get(0).getSql().contains("RAISE NOTICE"));
        assertEquals("SELECT 777", statements.get(1).getSql());
    }

    // ============================================================
    // EDGE CASES - E-STRING VALIDATION
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: Should NOT treat 'E' inside identifier as E-string (e.g., column_nameE'...')")
    void shouldNotTreatIdentifierEndingInEAsEString() {
        // Edge case: identifier ending in 'E' followed by string
        String sql = "SELECT tableE'value', column2 FROM test;\n" +
            "SELECT 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // Should parse as: tableE (identifier), 'value' (string), NOT as E-string
        assertEquals(2, statements.size(), "Should NOT treat identifier+E as E-string");
        assertTrue(statements.get(0).getSql().contains("tableE"));
        assertTrue(statements.get(0).getSql().contains("'value'"));
        assertTrue(statements.get(1).getSql().contains("SELECT 1"));
    }

    @Test
    @DisplayName("PostgreSQL: Should NOT treat 'E' at end of string as E-string (e.g., 'nameE')")
    void shouldNotTreatStringEndingInEAsEString() {
        // Edge case: string ending in 'E' should NOT trigger E-string detection
        String sql = "INSERT INTO test VALUES ('employeeE', 'test');\n" +
            "SELECT * FROM test;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        // 'employeeE' should be parsed as a normal string, NOT as E-string
        assertEquals(2, statements.size(), "Should NOT treat string ending in 'E' as E-string");
        assertTrue(statements.get(0).getSql().contains("'employeeE'"));
        assertTrue(statements.get(0).getSql().contains("'test'"));
        assertTrue(statements.get(1).getSql().toUpperCase().contains("SELECT"));
    }

    // ============================================================
    // DOLLAR-SIGN PRESERVATION TESTS (bug documentation)
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: Should preserve plain $$ dollar-quotes nested inside tagged dollar-quotes")
    void shouldPreservePlainDollarQuotesNestedInsideTaggedDollarQuotes() {
        String sql = "DO $batch$\n" +
            "DECLARE v_msg TEXT;\n" +
            "BEGIN\n" +
            "    v_msg := $$This string contains; semicolons and 'quotes'$$;\n" +
            "END;\n" +
            "$batch$;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(1, statements.size(), "Entire DO block should be one statement");
        String result = statements.get(0).getSql();
        assertTrue(result.contains("$$This string contains; semicolons and 'quotes'$$"),
            "Plain $$ dollar-quotes must be preserved intact, got: " + result);
    }

    @Test
    @DisplayName("PostgreSQL: Should preserve multiple plain $$ dollar-quote blocks inside tagged outer")
    void shouldPreserveMultiplePlainDollarQuoteBlocks() {
        // Outer uses tagged $fn$ so inner plain $$ blocks are unambiguous
        String sql = "CREATE OR REPLACE FUNCTION test_multi() RETURNS void AS $fn$\n" +
            "DECLARE\n" +
            "    v1 TEXT := $$first value$$;\n" +
            "    v2 TEXT := $$second value$$;\n" +
            "BEGIN\n" +
            "    RAISE NOTICE '%', v1;\n" +
            "END;\n" +
            "$fn$ LANGUAGE plpgsql;\n" +
            "SELECT 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split into function and SELECT");
        String funcSql = statements.get(0).getSql();
        assertTrue(funcSql.contains("$$first value$$"),
            "First inner $$ block must be preserved, got: " + funcSql);
        assertTrue(funcSql.contains("$$second value$$"),
            "Second inner $$ block must be preserved, got: " + funcSql);
    }

    @Test
    @DisplayName("PostgreSQL: Should preserve dollar-sign in positional parameters ($1, $2)")
    void shouldPreserveDollarSignInPositionalParameters() {
        String sql = "PREPARE my_stmt AS SELECT $1, $2; EXECUTE my_stmt(1, 2);";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size());
        String prepareSql = statements.get(0).getSql();
        assertTrue(prepareSql.contains("$1"), "Positional parameter $1 must be preserved, got: " + prepareSql);
        assertTrue(prepareSql.contains("$2"), "Positional parameter $2 must be preserved, got: " + prepareSql);
    }

    @Test
    @DisplayName("PostgreSQL: Should preserve nested tagged dollar-quotes ($outer$...$inner$...$inner$...$outer$)")
    void shouldPreserveNestedTaggedDollarQuotes() {
        String sql = "CREATE OR REPLACE FUNCTION outer_func() RETURNS void AS $outer$\n" +
            "DECLARE\n" +
            "    v_sql TEXT := $inner$SELECT 'hello; world'$inner$;\n" +
            "BEGIN\n" +
            "    EXECUTE v_sql;\n" +
            "END;\n" +
            "$outer$ LANGUAGE plpgsql;\n" +
            "SELECT 1;";

        List<SqlStatement> statements = sqlSplitter.split(sql);

        assertEquals(2, statements.size(), "Should split into function and SELECT");
        String funcSql = statements.get(0).getSql();
        assertTrue(funcSql.contains("$inner$SELECT 'hello; world'$inner$"),
            "Nested tagged dollar-quotes must be preserved, got: " + funcSql);
        assertTrue(funcSql.contains("$outer$"),
            "Outer dollar-quote tags must be preserved, got: " + funcSql);
    }

    // ============================================================
    // UNCLOSED DOLLAR-QUOTE VALIDATION TESTS
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: Should throw when dollar-quote tag appears in comment inside block")
    void shouldThrowWhenDollarQuoteTagAppearsInCommentInsideBlock() {
        String sql = "DO $batch$\n" +
            "BEGIN\n" +
            "    -- comment mentioning $batch$ here\n" +
            "    v_msg := $$text$$;\n" +
            "END;\n" +
            "$batch$;";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> sqlSplitter.split(sql));
        assertTrue(ex.getMessage().contains("$batch$"), "Message should mention the unclosed tag");
        assertTrue(ex.getMessage().contains("Unclosed dollar-quoted block"), "Message should describe the problem");
    }

    @Test
    @DisplayName("PostgreSQL: Should throw when dollar-quote block is never closed")
    void shouldThrowWhenDollarQuoteBlockIsNeverClosed() {
        String sql = "SELECT $$unclosed block";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> sqlSplitter.split(sql));
        assertTrue(ex.getMessage().contains("$$"), "Message should mention the unclosed tag");
        assertTrue(ex.getMessage().contains("Unclosed dollar-quoted block"), "Message should describe the problem");
    }

    @Test
    @DisplayName("PostgreSQL: Internal spaces inside dollar-quoted strings should be preserved")
    void dollarQuotedSpacesPreserved() {
        List<SqlStatement> result = sqlSplitter.split("INSERT INTO t VALUES ($$  hello  world  $$);");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getSql().contains("$$  hello  world  $$"),
            "Multiple spaces inside dollar-quoted strings must be preserved as-is");
    }
}
