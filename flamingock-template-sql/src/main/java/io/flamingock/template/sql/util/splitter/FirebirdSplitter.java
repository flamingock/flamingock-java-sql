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
 * Firebird-specific SQL statement splitter.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>Standard SQL semicolon delimiter ✅ IMPLEMENTED (inherited)</li>
 *   <li>{@code SET TERM} directive for dynamically changing statement delimiter ✅ IMPLEMENTED</li>
 *   <li>BEGIN/END blocks (basic support) ✅ IMPLEMENTED (inherited)</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 *   <li>{@code EXECUTE BLOCK} with variable declarations ✅ IMPLEMENTED</li>
 *   <li>{@code AS} keyword context awareness (prevents splits in DECLARE section) ✅ IMPLEMENTED</li>
 *   <li>{@code FOR SELECT} loops in PSQL procedures ✅ IMPLEMENTED</li>
 *   <li>{@code DECLARE VARIABLE} in PSQL procedures ✅ IMPLEMENTED</li>
 *   <li>Nested BEGIN/END blocks in PSQL ✅ IMPLEMENTED</li>
 * </ul>
 */
public class FirebirdSplitter extends AbstractSqlSplitter {

    private boolean inAsContext = false;

    /**
     * Preprocesses SQL to handle Firebird's SET TERM directive.
     *
     * <p>Firebird's SET TERM command changes the statement delimiter:
     * <pre>
     * SET TERM ^^;        -- Changes delimiter from ; to ^^, command terminated with ;
     * CREATE PROCEDURE test AS BEGIN SELECT 1; END^^
     * SET TERM ;^^        -- Changes delimiter from ^^ to ;, command terminated with ^^
     * SELECT 2;
     * </pre>
     *
     * <p>This method transforms the SQL to use a consistent delimiter (;)
     * by processing SET TERM directives and replacing custom delimiters.
     *
     * <p><b>Important:</b> The SET TERM command itself is terminated with the
     * <b>current</b> delimiter, not the new delimiter.
     */
    @Override
    protected String preprocessSql(String sql) {
        inAsContext = false; // Reset per-parse state
        StringBuilder result = new StringBuilder();
        int lastProcessed = 0;
        String currentDelimiter = ";";

        while (lastProcessed < sql.length()) {
            // Try to find next SET TERM command from current position
            SetTermMatch match = findNextSetTerm(sql, lastProcessed, currentDelimiter);

            if (match != null) {
                // Process the segment before SET TERM (replace old delimiter if needed)
                String segment = sql.substring(lastProcessed, match.setTermStart);
                if (!currentDelimiter.equals(";")) {
                    segment = replaceDelimiterInSegment(segment, currentDelimiter, ";");
                }
                result.append(segment);

                // Update delimiter and skip SET TERM command
                currentDelimiter = match.newDelimiter;
                lastProcessed = match.endIndex;

                // Skip trailing newline if present
                if (lastProcessed < sql.length() && sql.charAt(lastProcessed) == '\n') {
                    lastProcessed++;
                }
            } else {
                // No more SET TERM commands - process remaining SQL
                String finalSegment = sql.substring(lastProcessed);
                if (!currentDelimiter.equals(";")) {
                    finalSegment = replaceDelimiterInSegment(finalSegment, currentDelimiter, ";");
                }
                result.append(finalSegment);
                break;
            }
        }

        return result.toString();
    }

    /**
     * Firebird AS context: prevents splitting between AS and BEGIN in procedures/functions.
     *
     * <p>In Firebird PSQL, the AS keyword starts a procedure/function/trigger body
     * where DECLARE statements can appear BEFORE the BEGIN keyword. Semicolons
     * in this context should NOT cause statement splits.
     */
    @Override
    protected boolean canSplitHere(ParserState state) {
        return !inAsContext;
    }

    /**
     * Handles Firebird-specific keywords: AS context tracking.
     *
     * <p>Pattern:
     * <pre>
     * CREATE PROCEDURE name
     * AS
     *   DECLARE VARIABLE v INT;  ← This ; should NOT split
     * BEGIN
     *   ...
     * END;
     * </pre>
     */
    @Override
    protected KeywordHandlingResult handleDialectKeywords(String sql, int index, ParserState state, StringBuilder output) {
        String remaining = sql.substring(index).toUpperCase();

        // Detect AS keyword after CREATE PROCEDURE/FUNCTION/TRIGGER or EXECUTE BLOCK
        if (remaining.startsWith("AS") && (remaining.length() == 2 || !Character.isLetterOrDigit(remaining.charAt(2)))) {
            // Check if this AS is part of CREATE PROCEDURE/FUNCTION/TRIGGER/EXECUTE BLOCK
            String stmtSoFar = output.toString().toUpperCase();
            if (stmtSoFar.contains("CREATE PROCEDURE") ||
                stmtSoFar.contains("CREATE FUNCTION") ||
                stmtSoFar.contains("CREATE TRIGGER") ||
                stmtSoFar.contains("EXECUTE BLOCK")) {
                // Entering AS context - prevent splits until BEGIN
                inAsContext = true;
            }
        }
        // BEGIN exits AS context (and is handled by generic keyword handler)
        else if (remaining.startsWith("BEGIN") && (remaining.length() == 5 || !Character.isLetterOrDigit(remaining.charAt(5)))) {
            inAsContext = false; // Exiting AS context, now inside BEGIN block
            // Let generic handler process BEGIN
        }

        // Not handled - let generic keyword handler process
        return KeywordHandlingResult.notHandled();
    }

    // ============================================================
    // SET TERM PREPROCESSING HELPERS
    // ============================================================

    /**
     * Helper class to hold SET TERM match results.
     */
    private static class SetTermMatch {
        int setTermStart;
        String newDelimiter;
        int endIndex;

        SetTermMatch(int setTermStart, String newDelimiter, int endIndex) {
            this.setTermStart = setTermStart;
            this.newDelimiter = newDelimiter;
            this.endIndex = endIndex;
        }
    }

    /**
     * Finds the next SET TERM command starting from the given position.
     * Returns null if no SET TERM found, otherwise returns match information.
     */
    private SetTermMatch findNextSetTerm(String sql, int startPos, String currentDelimiter) {
        int i = startPos;

        while (i < sql.length()) {
            // Skip to start of line (SET TERM must be at line start)
            if (i > startPos && sql.charAt(i - 1) != '\n') {
                i++;
                continue;
            }

            // Skip leading whitespace on the line
            int lineStart = i;
            while (i < sql.length() && Character.isWhitespace(sql.charAt(i)) && sql.charAt(i) != '\n') {
                i++;
            }

            // Check for "SET"
            String remaining = i < sql.length() ? sql.substring(i).toUpperCase() : "";
            if (!remaining.startsWith("SET") ||
                (remaining.length() > 3 && Character.isLetterOrDigit(remaining.charAt(3)))) {
                // Not a SET keyword, skip this line
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
                if (i < sql.length()) i++; // Skip newline
                continue;
            }
            i += 3;

            // Skip whitespace after SET
            if (i >= sql.length() || !Character.isWhitespace(sql.charAt(i))) {
                continue;
            }
            while (i < sql.length() && Character.isWhitespace(sql.charAt(i)) && sql.charAt(i) != '\n') {
                i++;
            }

            // Check for "TERM"
            remaining = i < sql.length() ? sql.substring(i).toUpperCase() : "";
            if (!remaining.startsWith("TERM") ||
                (remaining.length() > 4 && Character.isLetterOrDigit(remaining.charAt(4)))) {
                continue;
            }
            i += 4;

            // Skip whitespace after TERM
            if (i >= sql.length() || !Character.isWhitespace(sql.charAt(i))) {
                continue;
            }
            while (i < sql.length() && Character.isWhitespace(sql.charAt(i)) && sql.charAt(i) != '\n') {
                i++;
            }

            // Extract new delimiter (until current delimiter)
            int delimiterStart = i;
            int delimiterEnd = sql.indexOf(currentDelimiter, delimiterStart);

            if (delimiterEnd == -1) {
                return null; // Malformed SET TERM
            }

            String newDelimiter = sql.substring(delimiterStart, delimiterEnd).trim();

            if (newDelimiter.isEmpty()) {
                return null; // Invalid empty delimiter
            }

            // Found valid SET TERM - return match
            return new SetTermMatch(lineStart, newDelimiter, delimiterEnd + currentDelimiter.length());
        }

        return null; // No SET TERM found
    }

}

