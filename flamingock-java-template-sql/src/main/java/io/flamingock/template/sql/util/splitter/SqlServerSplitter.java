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

import java.util.ArrayList;
import java.util.List;

/**
 * SQL Server-specific SQL statement splitter.
 *
 * <p>Handles SQL Server-specific syntax:
 * <ul>
 *   <li><b>GO keyword:</b> Batch separator (case-insensitive, must be on its own line) ✅ IMPLEMENTED</li>
 *   <li><b>GO with count:</b> {@code GO 5} executes the batch 5 times ✅ IMPLEMENTED</li>
 *   <li><b>Square brackets:</b> {@code [identifier]} for names with spaces/special chars ✅ IMPLEMENTED</li>
 *   <li><b>Mixed delimiters:</b> Both semicolons and GO can be used in same script ✅ IMPLEMENTED</li>
 *   <li><b>GO inside comments/strings:</b> Properly ignored ✅ IMPLEMENTED</li>
 * </ul>
 *
 * <p>GO keyword rules:
 * <ul>
 *   <li>Must appear on its own line (whitespace before/after is allowed)</li>
 *   <li>Case-insensitive: GO, go, Go, gO all recognized</li>
 *   <li>Optional count: {@code GO} (default 1) or {@code GO 5} (repeat 5 times)</li>
 *   <li>Not recognized inside strings, comments, or square bracket identifiers</li>
 * </ul>
 */
public class SqlServerSplitter extends AbstractSqlSplitter {

    @Override
    protected boolean supportsSquareBracketIdentifiers() {
        return true;
    }

    @Override
    public List<SqlStatement> split(String sql) {
        // Split by GO first, then by semicolons within batches
        return splitByGO(sql);
    }

    /**
     * Splits SQL by GO keyword (batch separator) with support for GO count.
     *
     * <p>This implementation parses character-by-character to properly handle:
     * <ul>
     *   <li>GO inside strings (should be ignored)</li>
     *   <li>GO inside comments (should be ignored)</li>
     *   <li>GO inside square bracket identifiers (should be ignored)</li>
     *   <li>GO must be on its own line (only whitespace before/after)</li>
     * </ul>
     */
    private List<SqlStatement> splitByGO(String sql) {
        List<SqlStatement> statements = new ArrayList<>();
        StringBuilder currentBatch = new StringBuilder();
        ParserState state = new ParserState();
        List<CharacterHandler> handlers = buildHandlers();

        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            // Try handlers in order (Chain of Responsibility pattern)
            boolean handled = false;
            for (CharacterHandler handler : handlers) {
                if (handler.canHandle(c, next, state)) {
                    i = handler.handle(sql, i, state, currentBatch);
                    handled = true;
                    break;
                }
            }

            // If no handler processed it, check for GO keyword
            if (!handled) {
                // Only check for GO when NOT inside any quote/comment
                if (!state.isInComment() && !state.inLineComment && !state.isInAnyQuote()) {
                    // Check if we're at the start of a potential GO keyword
                    String remaining = sql.substring(i);
                    GOMatch goMatch = tryMatchGO(remaining);

                    if (goMatch != null) {
                        // Found GO keyword, process current batch
                        if (currentBatch.length() > 0) {
                            List<SqlStatement> batchStatements = splitBatchBySemicolons(currentBatch.toString(), goMatch.repeatCount);
                            statements.addAll(batchStatements);
                            currentBatch.setLength(0);
                        }

                        // Skip past the GO keyword and optional count
                        i += goMatch.length;
                        continue;
                    }
                }

                // Default: append character
                currentBatch.append(c);
                i++;
            }
        }

        // Process remaining batch
        if (currentBatch.length() > 0) {
            List<SqlStatement> batchStatements = splitBatchBySemicolons(currentBatch.toString(), 1);
            statements.addAll(batchStatements);
        }

        return statements;
    }

    /**
     * Attempts to match a GO keyword at the current position.
     * GO must be on its own line (only whitespace before/after on the line).
     *
     * @param sql SQL starting at potential GO position
     * @return GOMatch if GO found, null otherwise
     */
    private GOMatch tryMatchGO(String sql) {
        // GO must be preceded by newline or start of string (with optional whitespace)
        // and followed by whitespace, optional count, and newline or end of string

        String upper = sql.toUpperCase();

        // Check if starts with "GO"
        if (!upper.startsWith("GO")) {
            return null;
        }

        // Check what comes after "GO"
        if (sql.length() == 2) {
            // Just "GO" at end of string
            return new GOMatch(2, 1);
        }

        char afterGO = sql.charAt(2);

        // GO must be followed by whitespace, newline, or end
        if (!Character.isWhitespace(afterGO)) {
            // e.g., "GOING" - not a GO keyword
            return null;
        }

        // Parse optional count: "GO 5", "GO\n", "GO ", etc.
        int pos = 2;

        // Skip whitespace (but not newlines yet)
        while (pos < sql.length() && (sql.charAt(pos) == ' ' || sql.charAt(pos) == '\t')) {
            pos++;
        }

        int repeatCount = 1;

        // Check if there's a negative number (GO -5 is invalid)
        if (pos < sql.length() && sql.charAt(pos) == '-') {
            if (pos + 1 < sql.length() && Character.isDigit(sql.charAt(pos + 1))) {
                StringBuilder negStr = new StringBuilder("-");
                pos++; // skip '-'
                while (pos < sql.length() && Character.isDigit(sql.charAt(pos))) {
                    negStr.append(sql.charAt(pos));
                    pos++;
                }
                throw new IllegalArgumentException("GO count must be >= 1, got: " + negStr);
            }
        }

        // Check if there's a number
        if (pos < sql.length() && Character.isDigit(sql.charAt(pos))) {
            StringBuilder countStr = new StringBuilder();
            while (pos < sql.length() && Character.isDigit(sql.charAt(pos))) {
                countStr.append(sql.charAt(pos));
                pos++;
            }

            try {
                repeatCount = Integer.parseInt(countStr.toString());
                if (repeatCount < 1) {
                    throw new IllegalArgumentException("GO count must be >= 1, got: " + repeatCount);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid GO count value: '" + countStr + "'", e);
            }
        }

        // Skip remaining whitespace to newline or end
        while (pos < sql.length() && (sql.charAt(pos) == ' ' || sql.charAt(pos) == '\t')) {
            pos++;
        }

        // Must end with newline or end of string
        if (pos < sql.length() && sql.charAt(pos) != '\n' && sql.charAt(pos) != '\r') {
            // GO has non-whitespace after count on same line
            return null;
        }

        // Consume the newline if present
        if (pos < sql.length() && sql.charAt(pos) == '\r') {
            pos++;
        }
        if (pos < sql.length() && sql.charAt(pos) == '\n') {
            pos++;
        }

        return new GOMatch(pos, repeatCount);
    }

    /**
     * Result of GO keyword matching.
     */
    private static class GOMatch {
        final int length;      // Total characters consumed (including GO, count, newline)
        final int repeatCount; // Repeat count from "GO n"

        GOMatch(int length, int repeatCount) {
            this.length = length;
            this.repeatCount = repeatCount;
        }
    }

    /**
     * Splits a batch (between GO statements) by semicolons, applying repeat count to all statements.
     */
    private List<SqlStatement> splitBatchBySemicolons(String batch, int repeatCount) {
        // Use parent's semicolon splitting
        List<SqlStatement> semicolonSplit = super.splitWithDelimiter(batch, ";");

        // Apply repeat count metadata
        List<SqlStatement> result = new ArrayList<>();
        for (SqlStatement stmt : semicolonSplit) {
            if (repeatCount > 1) {
                result.add(new SqlStatement(stmt.getSql(), repeatCount));
            } else {
                result.add(stmt);
            }
        }

        return result;
    }
}
