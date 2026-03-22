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
 * IBM DB2-specific SQL statement splitter.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>Standard SQL semicolon delimiter ✅ IMPLEMENTED (inherited)</li>
 *   <li>{@code @} delimiter support for DB2 CLP (Command Line Processor) scripts ✅ IMPLEMENTED</li>
 *   <li>BEGIN/END blocks (basic support) ✅ IMPLEMENTED (inherited)</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 *   <li>{@code BEGIN ATOMIC} keyword detection for compound statements ✅ IMPLEMENTED</li>
 *   <li>Advanced nested BEGIN/END block tracking in procedures/functions ✅ IMPLEMENTED</li>
 *   <li>{@code LANGUAGE SQL} context awareness for procedure/function bodies ✅ IMPLEMENTED</li>
 *   <li>{@code CREATE TRIGGER} with {@code REFERENCING} clause ✅ IMPLEMENTED</li>
 * </ul>
 */
public class Db2Splitter extends AbstractSqlSplitter {

    /**
     * DB2 CLP supports @ as an additional statement delimiter.
     * This hook allows the generic parser to recognize @ as a valid delimiter.
     */
    @Override
    protected String checkAdditionalDelimiter(String remaining, ParserState state) {
        // DB2 CLP supports @ as a delimiter in addition to semicolon
        if (remaining.startsWith("@")) {
            return "@";
        }
        return null;
    }

}
