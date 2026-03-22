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

/**
 * H2 Database-specific SQL statement splitter.
 *
 * <p>H2 is highly compatible with both PostgreSQL and MySQL modes.
 * This splitter enables features from both databases for maximum compatibility.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>Backtick identifiers (MySQL compatibility): {@code `table`} ✅ IMPLEMENTED</li>
 *   <li>Dollar-quoted strings (PostgreSQL compatibility): {@code $$text$$} ✅ IMPLEMENTED</li>
 *   <li>E-strings (PostgreSQL compatibility): {@code E'text\n'} ✅ IMPLEMENTED</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 *   <li>JSON operators: {@code ->}, {@code ->>}, {@code #>}, {@code #>>} ✅ IMPLEMENTED (inherited)</li>
 *   <li>Standard SQL features (double quotes, semicolons, etc.) ✅ IMPLEMENTED (inherited)</li>
 *   <li>BEGIN/END blocks in procedures and triggers ✅ IMPLEMENTED (inherited)</li>
 *   <li>{@code CREATE ALIAS} for Java function definitions ✅ IMPLEMENTED
 *     <ul>
 *       <li>Java method references: {@code CREATE ALIAS FOR "com.example.Class.method"}</li>
 *       <li>Inline Java code with $$ delimiters</li>
 *       <li>DETERMINISTIC flag support</li>
 *     </ul>
 *   </li>
 *   <li>H2-specific statements:
 *     <ul>
 *       <li>{@code MERGE INTO} statement ✅ IMPLEMENTED</li>
 *       <li>{@code RUNSCRIPT} command ✅ IMPLEMENTED</li>
 *       <li>{@code SCRIPT} command ✅ IMPLEMENTED</li>
 *     </ul>
 *   </li>
 *   <li>Mixed mode support: Backticks and dollar quotes in same script ✅ IMPLEMENTED</li>
 *   <li>Nested dollar quotes with tags: {@code $outer$ ... $inner$ ... $inner$ ... $outer$} ✅ IMPLEMENTED</li>
 *   <li>{@code CREATE TRIGGER} with {@code BEGIN ATOMIC ... END} ✅ IMPLEMENTED</li>
 * </ul>
 *
 * <p><b>Note:</b> This splitter enables both MySQL and PostgreSQL features simultaneously,
 * providing maximum compatibility. Users should write SQL compatible with the H2 mode they
 * configured in their application (e.g., MODE=PostgreSQL, MODE=MySQL, etc.).
 */
public class H2Splitter extends AbstractSqlSplitter {

    @Override
    protected boolean supportsBacktickIdentifiers() {
        // H2 supports backticks in MySQL compatibility mode
        return true;
    }

    @Override
    protected boolean supportsDollarQuotedStrings() {
        // H2 supports dollar quotes in PostgreSQL compatibility mode
        return true;
    }

    @Override
    protected boolean supportsEStrings() {
        // H2 supports E-strings in PostgreSQL compatibility mode
        return true;
    }

}
