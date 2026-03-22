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
 * IBM Informix-specific SQL statement splitter.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>Curly brace comments: {@code { comment }} ✅ IMPLEMENTED</li>
 *   <li>Nested curly brace comments: {@code { outer { inner } outer }} ✅ IMPLEMENTED</li>
 *   <li>{@code CREATE PROCEDURE ... END PROCEDURE} keyword detection ✅ IMPLEMENTED</li>
 *   <li>{@code CREATE FUNCTION ... RETURNING ... END FUNCTION} syntax ✅ IMPLEMENTED</li>
 *   <li>{@code ON EXCEPTION ... END EXCEPTION} error handling blocks ✅ IMPLEMENTED</li>
 *   <li>{@code FOREACH ... END FOREACH} loops ✅ IMPLEMENTED</li>
 *   <li>Standard SQL semicolon delimiter ✅ IMPLEMENTED (inherited)</li>
 *   <li>BEGIN/END blocks (basic support) ✅ IMPLEMENTED (inherited)</li>
 * </ul>
 *
 * <p><b>Key differences from standard SQL:</b>
 * <ul>
 *   <li>Procedures/functions can omit BEGIN, starting directly after CREATE PROCEDURE/FUNCTION</li>
 *   <li>END PROCEDURE/FUNCTION/FOREACH/EXCEPTION are compound keywords that close blocks</li>
 * </ul>
 */
public class InformixSplitter extends AbstractSqlSplitter {

    private boolean inInformixProcedure = false;

    @Override
    protected String preprocessSql(String sql) {
        inInformixProcedure = false; // Reset per-parse state
        return sql;
    }

    @Override
    protected boolean supportsCurlyBraceComments() {
        return true;
    }

    /**
     * Handles Informix-specific compound keywords.
     *
     * <p>Informix uses compound END keywords like:
     * <ul>
     *   <li>END PROCEDURE - closes procedure (decrements depth)</li>
     *   <li>END FUNCTION - closes function (decrements depth)</li>
     *   <li>END EXCEPTION - closes exception block (decrements depth)</li>
     *   <li>END FOREACH - closes foreach loop (decrements depth)</li>
     * </ul>
     *
     * <p><b>Critical handling for CREATE PROCEDURE/FUNCTION:</b>
     * Informix procedures/functions can have TWO forms:
     * <ul>
     *   <li><b>With BEGIN/END:</b> {@code CREATE PROCEDURE p() BEGIN ... END;}
     *       - Generic handler increments depth on BEGIN, decrements on END</li>
     *   <li><b>Without BEGIN:</b> {@code CREATE PROCEDURE p() INSERT ...; END PROCEDURE;}
     *       - We increment depth on CREATE PROCEDURE, decrement on END PROCEDURE</li>
     * </ul>
     *
     * <p>Algorithm: Always increment depth on CREATE PROCEDURE/FUNCTION. If BEGIN appears,
     * it will increment again (depth=2), and END will decrement to 1, then END PROCEDURE
     * decrements to 0. If no BEGIN appears, depth=1 from CREATE, and END PROCEDURE decrements to 0.
     */
    @Override
    protected KeywordHandlingResult handleDialectKeywords(String sql, int index, ParserState state, StringBuilder output) {
        String remaining = sql.substring(index).toUpperCase();

        // CREATE PROCEDURE or CREATE FUNCTION - ALWAYS increment depth for Informix
        if (remaining.startsWith("CREATE") && (remaining.length() == 6 || !Character.isLetterOrDigit(remaining.charAt(6)))) {
            String afterCreate = remaining.length() > 6 ? remaining.substring(6).trim() : "";

            if (afterCreate.startsWith("PROCEDURE")) {
                state.blockDepth++;
                inInformixProcedure = true; // Mark that we're in an Informix procedure
                int keywordLength = findEndOfKeyword(remaining, "CREATE", "PROCEDURE");
                output.append(sql.substring(index, index + keywordLength));
                return KeywordHandlingResult.handled(index + keywordLength);
            } else if (afterCreate.startsWith("FUNCTION")) {
                state.blockDepth++;
                inInformixProcedure = true; // Mark that we're in an Informix function
                int keywordLength = findEndOfKeyword(remaining, "CREATE", "FUNCTION");
                output.append(sql.substring(index, index + keywordLength));
                return KeywordHandlingResult.handled(index + keywordLength);
            }
        }
        // BEGIN keyword - if we're in an Informix procedure, this means it HAS BEGIN/END
        // So we should decrement the depth we added for CREATE PROCEDURE and let BEGIN/END handle it
        else if (remaining.startsWith("BEGIN") && (remaining.length() == 5 || !Character.isLetterOrDigit(remaining.charAt(5)))) {
            if (inInformixProcedure) {
                // We incremented depth for CREATE PROCEDURE, but now BEGIN will also increment
                // So we decrement back to let BEGIN/END pair handle the depth
                if (state.blockDepth > 0) {
                    state.decrementBlockDepth();
                }
                inInformixProcedure = false; // No longer tracking as Informix-style (it's standard BEGIN/END now)
            }
            // Let generic handler process BEGIN
        }
        // ON EXCEPTION increments depth
        if (remaining.startsWith("ON") && (remaining.length() == 2 || !Character.isLetterOrDigit(remaining.charAt(2)))) {
            String afterOn = remaining.length() > 2 ? remaining.substring(2).trim() : "";
            if (afterOn.startsWith("EXCEPTION")) {
                state.blockDepth++;
                int keywordLength = findEndOfKeyword(remaining, "ON", "EXCEPTION");
                output.append(sql.substring(index, index + keywordLength));
                return KeywordHandlingResult.handled(index + keywordLength);
            }
        }
        // FOREACH increments depth
        else if (remaining.startsWith("FOREACH") && (remaining.length() == 7 || !Character.isLetterOrDigit(remaining.charAt(7)))) {
            state.blockDepth++;
            output.append(sql.substring(index, index + 7));
            return KeywordHandlingResult.handled(index + 7);
        }
        // END keyword - check for Informix-specific compound keywords BEFORE generic processing
        else if (remaining.startsWith("END") && (remaining.length() == 3 || !Character.isLetterOrDigit(remaining.charAt(3)))) {
            String afterEnd = remaining.length() > 3 ? remaining.substring(3).trim() : "";

            // Check for Informix-specific compound END keywords that CLOSE blocks
            if (afterEnd.startsWith("PROCEDURE")) {
                // END PROCEDURE closes the procedure
                inInformixProcedure = false; // Exit Informix procedure context
                if (state.blockDepth > 0) {
                    state.decrementBlockDepth();
                }
                int keywordLength = findEndOfKeyword(remaining, "END", "PROCEDURE");
                output.append(sql.substring(index, index + keywordLength));
                return KeywordHandlingResult.handled(index + keywordLength);
            } else if (afterEnd.startsWith("FUNCTION")) {
                // END FUNCTION closes the function
                inInformixProcedure = false; // Exit Informix function context
                if (state.blockDepth > 0) {
                    state.decrementBlockDepth();
                }
                int keywordLength = findEndOfKeyword(remaining, "END", "FUNCTION");
                output.append(sql.substring(index, index + keywordLength));
                return KeywordHandlingResult.handled(index + keywordLength);
            } else if (afterEnd.startsWith("EXCEPTION")) {
                // END EXCEPTION closes the exception block
                if (state.blockDepth > 0) {
                    state.decrementBlockDepth();
                }
                int keywordLength = findEndOfKeyword(remaining, "END", "EXCEPTION");
                output.append(sql.substring(index, index + keywordLength));
                return KeywordHandlingResult.handled(index + keywordLength);
            } else if (afterEnd.startsWith("FOREACH")) {
                // END FOREACH closes the foreach loop
                if (state.blockDepth > 0) {
                    state.decrementBlockDepth();
                }
                int keywordLength = findEndOfKeyword(remaining, "END", "FOREACH");
                output.append(sql.substring(index, index + keywordLength));
                return KeywordHandlingResult.handled(index + keywordLength);
            }
            // else: Let generic handler process standard END/CASE/BEGIN keywords
        }

        // Not handled - let generic keyword handler process
        return KeywordHandlingResult.notHandled();
    }

    /**
     * Find the end position of a compound keyword like "END PROCEDURE".
     * Returns the total length from start to end of the compound keyword.
     *
     * @param text The text starting with the first keyword
     * @param firstKeyword First part (e.g., "END")
     * @param secondKeyword Second part (e.g., "PROCEDURE")
     * @return Total length including both keywords and whitespace between them
     */
    private int findEndOfKeyword(String text, String firstKeyword, String secondKeyword) {
        int pos = firstKeyword.length();

        // Skip whitespace between keywords
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }

        // Add second keyword length
        pos += secondKeyword.length();

        return pos;
    }

}
