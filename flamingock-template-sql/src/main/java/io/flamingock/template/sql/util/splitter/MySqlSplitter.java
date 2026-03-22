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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MySQL-specific SQL statement splitter.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>{@code #} line comments in addition to {@code --} ✅ IMPLEMENTED (inherited)</li>
 *   <li>Backtick identifiers: {@code `table name`} ✅ IMPLEMENTED</li>
 *   <li>Backslash escape sequences: {@code \'}, {@code \"}, {@code \n}, {@code \t} ✅ IMPLEMENTED</li>
 *   <li>Double-quoted strings when ANSI_QUOTES mode is disabled (default) ✅ IMPLEMENTED</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 *   <li>BEGIN/END blocks (basic support) ✅ IMPLEMENTED (inherited)</li>
 *   <li>{@code DELIMITER} command: {@code DELIMITER //} changes delimiter until {@code DELIMITER ;} ✅ IMPLEMENTED</li>
 *   <li>Stored procedures/functions with DECLARE, IF/THEN/ELSE, nested blocks ✅ IMPLEMENTED</li>
 * </ul>
 */
public class MySqlSplitter extends AbstractSqlSplitter {

    @Override
    protected boolean supportsBacktickIdentifiers() {
        return true;
    }

    @Override
    protected boolean supportsBackslashEscapes() {
        return true;
    }

    @Override
    protected boolean supportsDoubleQuotedStringsAsStrings() {
        return true;
    }

    @Override
    protected boolean supportsHashComments() {
        return true;
    }

    /**
     * Preprocesses SQL to handle MySQL's DELIMITER command before the main parse loop.
     * Transforms dynamic delimiter changes into a consistent semicolon-delimited form,
     * aligning with how Firebird handles SET TERM via the same hook.
     */
    @Override
    protected String preprocessSql(String sql) {
        return preprocessDelimiterCommands(sql);
    }

    /**
     * Preprocesses SQL to replace DELIMITER commands with special markers
     * and translate delimiters to a consistent format.
     * <p>
     * MySQL's DELIMITER command changes the statement delimiter:
     * <pre>
     * DELIMITER //
     * CREATE PROCEDURE test() BEGIN SELECT 1; END//
     * DELIMITER ;
     * SELECT 2;
     * </pre>
     * <p>
     * This method transforms the SQL to use a consistent delimiter (;)
     * by processing DELIMITER directives and replacing custom delimiters.
     */
    private String preprocessDelimiterCommands(String sql) {
        Pattern delimiterPattern = Pattern.compile(
            "^\\s*DELIMITER\\s+(\\S+)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );

        Matcher matcher = delimiterPattern.matcher(sql);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        String currentDelimiter = ";";

        while (matcher.find()) {
            // Append SQL between last match and this DELIMITER command
            String sqlSegment = sql.substring(lastEnd, matcher.start());

            // Replace current delimiter with semicolon in this segment
            if (!currentDelimiter.equals(";")) {
                sqlSegment = replaceDelimiterInSegment(sqlSegment, currentDelimiter, ";");
            }

            result.append(sqlSegment);

            // Update current delimiter to new value from DELIMITER command
            currentDelimiter = matcher.group(1);

            // Skip the DELIMITER command itself (don't include in output)
            lastEnd = matcher.end();

            // Skip trailing newline if present
            if (lastEnd < sql.length() && sql.charAt(lastEnd) == '\n') {
                lastEnd++;
            }
        }

        // Append remaining SQL after last DELIMITER command
        String finalSegment = sql.substring(lastEnd);
        if (!currentDelimiter.equals(";")) {
            finalSegment = replaceDelimiterInSegment(finalSegment, currentDelimiter, ";");
        }
        result.append(finalSegment);

        return result.toString();
    }

}

