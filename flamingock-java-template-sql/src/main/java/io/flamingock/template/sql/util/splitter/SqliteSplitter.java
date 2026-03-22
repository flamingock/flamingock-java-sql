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
 * SQLite-specific SQL statement splitter.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>Square bracket identifiers: {@code [column name]} ✅ IMPLEMENTED</li>
 *   <li>Backtick identifiers (MySQL compatibility mode): {@code `column name`} ✅ IMPLEMENTED (inherited)</li>
 *   <li>Double-quoted identifiers (standard SQL) ✅ IMPLEMENTED (inherited)</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 *   <li>BEGIN/END blocks (basic support) ✅ IMPLEMENTED (inherited)</li>
 *   <li>CREATE TRIGGER context detection: Properly handles {@code CREATE TRIGGER ... BEGIN ... END;} ✅ IMPLEMENTED (inherited)</li>
 *   <li>Trigger-specific syntax:
 *     <ul>
 *       <li>WHEN clause: {@code CREATE TRIGGER ... WHEN condition BEGIN ... END} ✅ IMPLEMENTED</li>
 *       <li>FOR EACH ROW clause ✅ IMPLEMENTED</li>
 *       <li>Single-statement triggers without BEGIN/END ✅ IMPLEMENTED</li>
 *       <li>Multiple statements inside trigger body (semicolons preserved) ✅ IMPLEMENTED</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Note:</b> SQLite supports backticks for MySQL compatibility. This is enabled
 * by default via inheritance. Square brackets {@code [identifier]} are the SQLite-native
 * identifier quoting style and are also fully supported.
 *
 * <p><b>SQLite-specific features tested:</b>
 * <ul>
 *   <li>ATTACH DATABASE / DETACH DATABASE statements</li>
 *   <li>PRAGMA statements</li>
 *   <li>Trigger types: BEFORE, AFTER, INSTEAD OF</li>
 *   <li>NEW and OLD row references in triggers</li>
 *   <li>Mixed identifier quote styles in same script</li>
 * </ul>
 */
public class SqliteSplitter extends AbstractSqlSplitter {

    @Override
    protected boolean supportsSquareBracketIdentifiers() {
        return true;
    }

    @Override
    protected boolean supportsBacktickIdentifiers() {
        // SQLite supports backticks for MySQL compatibility
        return true;
    }

}
