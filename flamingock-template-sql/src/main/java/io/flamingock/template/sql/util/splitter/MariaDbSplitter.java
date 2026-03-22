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
 * MariaDB-specific SQL statement splitter.
 *
 * <p>MariaDB is a fork of MySQL and shares nearly identical syntax.
 * This splitter inherits all MySQL features.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>{@code #} line comments ✅ IMPLEMENTED (inherited from MySQL)</li>
 *   <li>Backtick identifiers: {@code `table name`} ✅ IMPLEMENTED (inherited from MySQL)</li>
 *   <li>Backslash escape sequences: {@code \'}, {@code \"}, {@code \n} ✅ IMPLEMENTED (inherited from MySQL)</li>
 *   <li>Double-quoted strings (ANSI_QUOTES off mode) ✅ IMPLEMENTED (inherited from MySQL)</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 *   <li>{@code DELIMITER} command for changing statement delimiter ✅ IMPLEMENTED (inherited from MySQL)</li>
 *   <li>Stored procedures/functions with DECLARE, IF/THEN/ELSE, nested blocks ✅ IMPLEMENTED (inherited from MySQL)</li>
 * </ul>
 */
public class MariaDbSplitter extends MySqlSplitter {
     // MariaDB is a fork of MySQL and shares the same syntax for the features we care about.
     // Therefore, we can simply inherit all behavior from MySqlSplitter without any modifications.
     // If MariaDB introduces any specific syntax differences in the future, we can override methods here as needed.
}
