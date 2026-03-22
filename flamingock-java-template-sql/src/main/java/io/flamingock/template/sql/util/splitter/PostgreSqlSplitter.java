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
 * PostgreSQL-specific SQL statement splitter.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>Dollar-quoted strings: {@code $$...$$} and {@code $tag$...$tag$} ✅ IMPLEMENTED</li>
 *   <li>{@code DO $$} anonymous code blocks ✅ IMPLEMENTED (via dollar-quoted strings)</li>
 *   <li>{@code CREATE FUNCTION} with {@code $$} or {@code $function$} body delimiters ✅ IMPLEMENTED</li>
 *   <li>{@code LANGUAGE plpgsql} procedures and functions ✅ IMPLEMENTED (via dollar-quoted strings)</li>
 *   <li>E-strings with C-style escapes: {@code E'text\n'} ✅ IMPLEMENTED</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 *   <li>JSON operators: {@code ->}, {@code ->>} ✅ IMPLEMENTED (inherited)</li>
 *   <li>JSON path operators: {@code #>}, {@code #>>} ✅ IMPLEMENTED (inherited)</li>
 *   <li>Double-quoted identifiers (strict SQL standard) ✅ IMPLEMENTED (inherited)</li>
 *   <li>Array constructors: {@code ARRAY[1,2,3]} ✅ IMPLEMENTED (inherited)</li>
 * </ul>
 *
 * <p><b>Note:</b> All PostgreSQL tests currently pass. No additional features needed at this time.
 */
public class PostgreSqlSplitter extends AbstractSqlSplitter {

    @Override
    protected boolean supportsDollarQuotedStrings() {
        return true;
    }

    @Override
    protected boolean supportsEStrings() {
        return true;
    }

}
