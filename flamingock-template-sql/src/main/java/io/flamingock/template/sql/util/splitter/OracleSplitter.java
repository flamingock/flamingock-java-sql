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
 * Oracle Database-specific SQL statement splitter.
 *
 * <p>Oracle uses {@code /} (forward slash) as the primary delimiter for PL/SQL blocks,
 * procedures, functions, and packages instead of semicolon.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>{@code /} delimiter for PL/SQL blocks ✅ IMPLEMENTED</li>
 *   <li>Q-strings (alternative quoting): {@code q'[...]'}, {@code q'{...}'}, {@code q'|...|'} ✅ IMPLEMENTED</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 *   <li>Double-quoted identifiers (strict SQL standard) ✅ IMPLEMENTED (inherited)</li>
 *   <li>Single-quoted strings with {@code ''} escaping ✅ IMPLEMENTED (inherited)</li>
 *   <li>BEGIN/END blocks (basic support) ✅ IMPLEMENTED (inherited)</li>
 * </ul>
 */
public class OracleSplitter extends AbstractSqlSplitter {

    // Override to use "/" as delimiter for Oracle PL/SQL
    @Override
    public List<SqlStatement> split(String sql) {
        return splitWithDelimiter(sql, "/");
    }

    /**
     * Override to inject Oracle-specific Q-string handler before single-quote handler.
     */
    @Override
    protected List<CharacterHandler> buildHandlers() {
        List<CharacterHandler> handlers = new ArrayList<>();

        // Comments must be checked first (before quotes)
        handlers.add(new BlockCommentHandler());
        handlers.add(new CurlyBraceCommentHandler());
        handlers.add(new LineCommentHandler());
        handlers.add(new InCommentAppenderHandler());

        // Backslash escaping must come before quote handlers
        handlers.add(new BackslashEscapeHandler());

        // Oracle Q-string MUST come before SingleQuoteHandler
        handlers.add(new QStringHandler());

        // Quote handlers (order matters!)
        handlers.add(new DollarQuoteHandler());
        handlers.add(new EStringHandler()); // BEFORE SingleQuoteHandler (E' must be matched before ')
        handlers.add(new SingleQuoteHandler());
        handlers.add(new DoubleQuoteHandler());
        handlers.add(new BacktickHandler());
        handlers.add(new SquareBracketHandler());

        return handlers;
    }

    /**
     * Handler for Oracle Q-strings (alternative quoting mechanism).
     *
     * <p>Syntax: {@code q'<delimiter>text<delimiter>'}
     * <ul>
     *   <li>Bracket pairs: {@code []}, {@code {}}, {@code ()}, {@code <>}</li>
     *   <li>Custom delimiters: any single char (e.g., {@code |}, {@code !}, {@code #})</li>
     * </ul>
     *
     * <p>Example: {@code q'[It's a quote with / and ; inside]'}
     *
     * <p><b>Important:</b> Q-strings are only recognized when 'q' or 'Q' is NOT preceded by
     * an alphanumeric character (to avoid false positives like {@code iraqt'value'}).
     */
    protected static class QStringHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            // Only trigger if not inside comment/quote and current char is 'q' or 'Q'
            if (state.isInComment() || state.inLineComment || state.isInAnyQuote()) {
                return false;
            }

            // Check for q' or Q'
            return (c == 'q' || c == 'Q') && next == '\'';
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            // CRITICAL: Verify that 'q' is NOT preceded by alphanumeric char
            // This prevents false positives like: iraqt'value' being parsed as q-string
            if (index > 0) {
                char prev = sql.charAt(index - 1);
                if (Character.isLetterOrDigit(prev) || prev == '_') {
                    // Not a q-string, just a regular identifier followed by string
                    // Let the SingleQuoteHandler process the quote
                    output.append(sql.charAt(index)); // Append 'q'
                    return index + 1;
                }
            }

            // We're at 'q' or 'Q', next is '
            if (index + 2 >= sql.length()) {
                // Incomplete q-string, just append as-is
                output.append(sql.charAt(index));
                return index + 1;
            }

            char delimiterChar = sql.charAt(index + 2);

            // Determine closing delimiter based on opening character
            char closingDelimiter = getClosingDelimiter(delimiterChar);

            // Find the end of the q-string: closingDelimiter + '
            int endPos = findQStringEnd(sql, index + 3, closingDelimiter);

            if (endPos == -1) {
                // Malformed q-string, append as-is
                output.append(sql.charAt(index));
                return index + 1;
            }

            // Append entire q-string including q'...'
            output.append(sql, index, endPos);

            return endPos;
        }

        /**
         * Get the closing delimiter character for bracket pairs.
         */
        private char getClosingDelimiter(char opening) {
            switch (opening) {
                case '[': return ']';
                case '{': return '}';
                case '(': return ')';
                case '<': return '>';
                default: return opening; // Same char for custom delimiters
            }
        }

        /**
         * Find the end position of a q-string (closing delimiter + ').
         *
         * @param sql the full SQL string
         * @param startPos position after the opening delimiter
         * @param closingDelimiter the delimiter to look for
         * @return index just after the closing ', or -1 if not found
         */
        private int findQStringEnd(String sql, int startPos, char closingDelimiter) {
            for (int i = startPos; i < sql.length(); i++) {
                if (sql.charAt(i) == closingDelimiter) {
                    // Check if next char is '
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        return i + 2; // Position after the closing '
                    }
                }
            }
            return -1; // Not found
        }
    }

}
