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
 * Generic SQL statement splitter for standard databases.
 * Handles strings, comments, and parentheses depth to avoid splitting inside blocks.
 *
 * <p>Features implemented:
 * <ul>
 *   <li>Double-quoted identifiers (ANSI SQL): Allows semicolons inside "column;name"</li>
 *   <li>Single-quoted string literals with doubled-quote escaping ('')</li>
 *   <li>Nested BEGIN/END blocks with proper depth tracking</li>
 *   <li>CASE/END blocks with proper depth tracking</li>
 *   <li>Compound END keywords (END IF, END LOOP, END WHILE, etc.)</li>
 *   <li>Block comments with nested support</li>
 *   <li>Line comments (--) with proper detection</li>
 *   <li>JSON path operators not treated as comments</li>
 * </ul>
 *
 * <p>Extensible quote styles (opt-in via subclasses):
 * <ul>
 *   <li>Backticks (MySQL, MariaDB): Override supportsBacktickIdentifiers()</li>
 *   <li>Square brackets (SQL Server, SQLite): Override supportsSquareBracketIdentifiers()</li>
 *   <li>Curly brace comments (Informix): Override supportsCurlyBraceComments()</li>
 * </ul>
 */
public abstract class AbstractSqlSplitter implements SqlSplitter {

    /**
     * Override to enable backtick identifiers (MySQL-style).
     * @return true if backticks should be treated as identifier quotes
     */
    protected boolean supportsBacktickIdentifiers() {
        return false;
    }

    /**
     * Override to enable square bracket identifiers (SQL Server-style).
     * @return true if square brackets should be treated as identifier quotes
     */
    protected boolean supportsSquareBracketIdentifiers() {
        return false;
    }

    /**
     * Override to enable curly brace comments (Informix-style).
     * @return true if curly braces should be treated as comments
     */
    protected boolean supportsCurlyBraceComments() {
        return false;
    }

    /**
     * Override to enable backslash escape sequences in strings (MySQL-style).
     * When enabled, backslash acts as escape character: \' \" \n \\ etc.
     * @return true if backslash should escape the next character in strings
     */
    protected boolean supportsBackslashEscapes() {
        return false;
    }

    /**
     * Override to enable double-quoted strings as string literals (MySQL default mode).
     * When false (default), double quotes are treated as identifier quotes (ANSI SQL).
     * When true, double quotes behave like single quotes for string literals.
     * @return true if double quotes should be treated as string delimiters
     */
    protected boolean supportsDoubleQuotedStringsAsStrings() {
        return false;
    }

    /**
     * Override to enable dollar-quoted strings (PostgreSQL-style).
     * Supports both $$ ... $$ and $tag$ ... $tag$ syntax.
     * @return true if dollar-quoted strings should be recognized
     */
    protected boolean supportsDollarQuotedStrings() {
        return false;
    }

    /**
     * Override to enable E-strings (PostgreSQL-style).
     * E-strings use C-style escape sequences: E'text\n\t'
     * @return true if E-strings should be recognized
     */
    protected boolean supportsEStrings() {
        return false;
    }

    /**
     * Override to enable hash (#) line comments (MySQL/MariaDB-style).
     * When false (default), '#' is treated as a regular character.
     * When true, '#' (not followed by '&gt;') starts a line comment.
     * @return true if hash should be treated as a line comment starter
     */
    protected boolean supportsHashComments() {
        return false;
    }

    @Override
    public List<SqlStatement> split(String sql) {
        return splitWithDelimiter(sql, ";");
    }

    public List<SqlStatement> splitWithDelimiter(String sql, String delimiter) {
        // Hook for dialect-specific SQL preprocessing (e.g., Firebird SET TERM)
        String preprocessedSql = preprocessSql(sql);

        List<String> rawStatements = parseStatements(preprocessedSql, delimiter);
        // Apply normalization like the old parser and wrap in SqlStatement
        List<SqlStatement> result = new ArrayList<>();
        for (String stmt : rawStatements) {
            String normalized = normalizeSpaces(stmt);
            if (!normalized.trim().isEmpty()) {
                result.add(new SqlStatement(normalized));
            }
        }
        return result;
    }

    /**
     * Hook for dialect-specific SQL preprocessing before parsing.
     * Override this to transform SQL before it's parsed (e.g., Firebird SET TERM directive).
     *
     * @param sql the original SQL
     * @return preprocessed SQL
     */
    protected String preprocessSql(String sql) {
        return sql;
    }

    /**
     * Hook for dialect-specific additional delimiters (e.g., DB2's @ delimiter).
     * Override this to support additional statement delimiters beyond the primary delimiter.
     *
     * @param remaining SQL from current position onward
     * @param state current parser state
     * @return delimiter string if found, null otherwise
     */
    protected String checkAdditionalDelimiter(String remaining, ParserState state) {
        return null;
    }

    /**
     * Build the list of character handlers in priority order.
     * Order matters: handlers are checked in sequence, first match wins.
     *
     * <p><b>Static vs non-static inner classes:</b> handlers that call dialect capability
     * hooks ({@code supportsXxx()}) are non-static inner classes because they need an
     * implicit reference to the enclosing {@code AbstractSqlSplitter} instance to invoke
     * those methods. Handlers with no such dependency are declared {@code static} to avoid
     * the unnecessary enclosing reference. This is an intentional design choice.
     *
     * @return ordered list of character handlers
     */
    protected List<CharacterHandler> buildHandlers() {
        List<CharacterHandler> handlers = new ArrayList<>();

        // Comments must be checked first (before quotes)
        handlers.add(new BlockCommentHandler());
        handlers.add(new CurlyBraceCommentHandler());
        handlers.add(new LineCommentHandler());
        handlers.add(new InCommentAppenderHandler());

        // Backslash escaping must come before quote handlers
        handlers.add(new BackslashEscapeHandler());

        // Quote handlers (order matters!)
        handlers.add(new DollarQuoteHandler());
        handlers.add(new EStringHandler()); // BEFORE SingleQuoteHandler (E' must be matched before ')
        handlers.add(new SingleQuoteHandler());
        handlers.add(new DoubleQuoteHandler());
        handlers.add(new BacktickHandler());
        handlers.add(new SquareBracketHandler());

        return handlers;
    }

    private List<String> parseStatements(String sql, String delimiter) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();
        ParserState state = new ParserState();
        List<CharacterHandler> handlers = buildHandlers();

        for (int i = 0; i < sql.length(); ) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            // Try handlers in order (Chain of Responsibility pattern)
            boolean handled = false;
            for (CharacterHandler handler : handlers) {
                if (handler.canHandle(c, next, state)) {
                    i = handler.handle(sql, i, state, currentStatement);
                    handled = true;
                    break;
                }
            }

            // If no handler processed it, check for delimiters, parens, keywords
            if (!handled) {
                // Parentheses and procedural block tracking
                if (!state.isInComment() && !state.inLineComment && !state.isInAnyQuote()) {
                    if (c == '(') {
                        state.parensCount++;
                    } else if (c == ')') {
                        state.parensCount--;
                    } else if (state.parensCount == 0 && state.blockDepth == 0 && canSplitHere(state)) {
                        // Support multi-character delimiters (e.g., MySQL DELIMITER //)
                        String remaining = sql.substring(i);
                        if (remaining.startsWith(delimiter)) {
                            statements.add(currentStatement.toString());
                            currentStatement.setLength(0);
                            i += delimiter.length();
                            continue;
                        }

                        // Hook: Check for additional dialect-specific delimiters (e.g., DB2's @)
                        String additionalDelim = checkAdditionalDelimiter(remaining, state);
                        if (additionalDelim != null && remaining.startsWith(additionalDelim)) {
                            statements.add(currentStatement.toString());
                            currentStatement.setLength(0);
                            i += additionalDelim.length();
                            continue;
                        }
                    }
                }

                // Detect BEGIN/END tokens to avoid splitting inside procedural blocks
                if (!state.isInComment() && !state.inLineComment && !state.isInAnyQuote()) {
                    // Hook for dialect-specific keyword handling
                    KeywordHandlingResult keywordResult = handleDialectKeywords(sql, i, state, currentStatement);
                    if (keywordResult.handled) {
                        i = keywordResult.newIndex;
                        continue;
                    }

                    // Default keyword handling
                    i = handleGenericKeywords(sql, i, state, currentStatement);
                    continue;
                }

                // Default: append character (if not in comment)
                if (!state.isInComment() && !state.inLineComment) {
                    currentStatement.append(c);
                }

                i++;
            }
        }

        validateEndState(state);

        if (currentStatement.length() > 0) {
            statements.add(currentStatement.toString());
        }

        return statements;
    }

    /**
     * Validates the parser state after parsing completes.
     * Detects unclosed constructs and throws descriptive errors.
     *
     * @param state the parser state after the parsing loop
     * @throws IllegalArgumentException if an unclosed construct is detected
     */
    private void validateEndState(ParserState state) {
        if (state.dollarQuoteTag != null) {
            throw new IllegalArgumentException(
                    "Unclosed dollar-quoted block: tag «" + state.dollarQuoteTag +
                    "» was opened but never closed. " +
                    "This often happens when the content between dollar-quote tags contains the tag itself " +
                    "(e.g., in a SQL comment like '-- ... " + state.dollarQuoteTag + " ...'). " +
                    "PostgreSQL's lexer matches dollar-quote tags as plain text — comments inside a " +
                    "dollar-quoted block do not prevent tag matching. " +
                    "Fix: use a different tag that does not appear in the block content."
            );
        }
    }

    /**
     * Hook for dialect-specific conditions that prevent splitting at a delimiter.
     * Override this to add custom split prevention logic (e.g., Firebird AS context).
     *
     * @param state current parser state
     * @return true if splitting is allowed at current position (default: true)
     */
    protected boolean canSplitHere(ParserState state) {
        return true;
    }

    /**
     * Hook for dialect-specific keyword handling.
     * Override this to handle custom keywords before generic keyword processing.
     *
     * @param sql full SQL string
     * @param index current position
     * @param state parser state
     * @param output current statement builder
     * @return result indicating if keyword was handled and new index position
     */
    protected KeywordHandlingResult handleDialectKeywords(String sql, int index, ParserState state, StringBuilder output) {
        return KeywordHandlingResult.notHandled();
    }

    /**
     * Generic keyword handling for BEGIN, CASE, END.
     * Subclasses should call this for standard keyword processing.
     *
     * @param sql the full SQL string being parsed
     * @param index the current position in the SQL string
     * @param state the current parser state
     * @param output the output buffer for the current statement
     * @return the updated index after processing
     */
    protected int handleGenericKeywords(String sql, int index, ParserState state, StringBuilder output) {
        String remaining = sql.substring(index).toUpperCase();

        // BEGIN increments block depth
        if (remaining.startsWith("BEGIN") && (remaining.length() == 5 || !Character.isLetterOrDigit(remaining.charAt(5)))) {
            state.blockDepth++;
        }
        // CASE also increments depth (CASE ... END pattern)
        else if (remaining.startsWith("CASE") && (remaining.length() == 4 || !Character.isLetterOrDigit(remaining.charAt(4)))) {
            state.blockDepth++;
        }
        // END decrements block depth (unless it's a compound keyword)
        else if (remaining.startsWith("END") && (remaining.length() == 3 || !Character.isLetterOrDigit(remaining.charAt(3)))) {
            // Check if this is END followed by a keyword like IF, LOOP, WHILE, etc.
            // These are compound keywords and should NOT decrement block depth
            String afterEnd = remaining.length() > 3 ? remaining.substring(3).trim() : "";
            boolean isCompoundEnd = afterEnd.startsWith("IF") ||
                                   afterEnd.startsWith("LOOP") ||
                                   afterEnd.startsWith("WHILE") ||
                                   afterEnd.startsWith("FOR") ||
                                   afterEnd.startsWith("REPEAT");

            if (!isCompoundEnd && state.blockDepth > 0) {
                state.decrementBlockDepth();
            }
        }

        // Append current character and advance
        output.append(sql.charAt(index));
        return index + 1;
    }

    /**
     * Result of dialect-specific keyword handling.
     */
    protected static class KeywordHandlingResult {
        final boolean handled;
        final int newIndex;

        private KeywordHandlingResult(boolean handled, int newIndex) {
            this.handled = handled;
            this.newIndex = newIndex;
        }

        static KeywordHandlingResult notHandled() {
            return new KeywordHandlingResult(false, -1);
        }

        static KeywordHandlingResult handled(int newIndex) {
            return new KeywordHandlingResult(true, newIndex);
        }
    }

    private String normalizeSpaces(String sql) {
        if (sql == null) return "";
        return sql.trim();
    }

    /**
     * Replaces all occurrences of {@code oldDelim} with {@code newDelim} in the given SQL
     * segment, but only when NOT inside strings, comments, or identifiers.
     *
     * <p>Uses the handler chain (via {@link #buildHandlers()}) to correctly parse the
     * segment so that delimiters appearing inside quoted strings or comments are not replaced.
     *
     * <p>This method is shared by dialect preprocessors that normalise a custom delimiter
     * back to a semicolon before the main parse loop runs — for example, Firebird
     * {@code SET TERM} and MySQL {@code DELIMITER} preprocessing.
     *
     * @param sql      the SQL segment to process
     * @param oldDelim the delimiter to replace
     * @param newDelim the replacement delimiter
     * @return the segment with every unquoted, uncommented {@code oldDelim} replaced by {@code newDelim}
     */
    protected String replaceDelimiterInSegment(String sql, String oldDelim, String newDelim) {
        StringBuilder result = new StringBuilder();
        ParserState state = new ParserState();
        List<CharacterHandler> handlers = buildHandlers();

        for (int i = 0; i < sql.length(); ) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            boolean handled = false;
            for (CharacterHandler handler : handlers) {
                if (handler.canHandle(c, next, state)) {
                    i = handler.handle(sql, i, state, result);
                    handled = true;
                    break;
                }
            }

            if (!handled) {
                if (!state.isInComment() && !state.inLineComment && !state.isInAnyQuote()) {
                    String remaining = sql.substring(i);
                    if (remaining.startsWith(oldDelim)) {
                        result.append(newDelim);
                        i += oldDelim.length();
                        continue;
                    }
                }

                if (!state.isInComment() && !state.inLineComment) {
                    result.append(c);
                } else {
                    result.append(' '); // Replace comment content with space
                }
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Internal state tracker for SQL parsing.
     * Encapsulates all flags and counters needed during parsing.
     */
    protected static class ParserState {
        boolean inSingleQuoteString = false;
        boolean inDoubleQuoteIdentifier = false;
        boolean inBacktickIdentifier = false;
        boolean inSquareBracketIdentifier = false;
        boolean inEString = false; // PostgreSQL E-strings: E'text\n'
        int commentDepth = 0; // Support nested block comments /* */
        int curlyCommentDepth = 0; // Support nested curly brace comments { } (Informix)
        boolean inLineComment = false;
        int parensCount = 0;
        int blockDepth = 0;
        String dollarQuoteTag = null; // PostgreSQL dollar-quoted strings: $$...$$, $tag$...$tag$
        String currentDelimiter = ";"; // Current statement delimiter (MySQL DELIMITER command support)

        /**
         * Safely decrements blockDepth, preventing it from going below zero on malformed SQL.
         */
        void decrementBlockDepth() {
            if (blockDepth > 0) blockDepth--;
        }

        /**
         * Returns true if currently inside any type of quote (string or identifier).
         */
        boolean isInAnyQuote() {
            return inSingleQuoteString || inDoubleQuoteIdentifier ||
                   inBacktickIdentifier || inSquareBracketIdentifier ||
                   dollarQuoteTag != null || inEString;
        }

        /**
         * Returns true if currently inside any type of comment.
         */
        boolean isInComment() {
            return commentDepth > 0 || curlyCommentDepth > 0;
        }

        /**
         * Returns true if currently inside a string (not identifier).
         */
        boolean isInString() {
            return inSingleQuoteString || inDoubleQuoteIdentifier ||
                   dollarQuoteTag != null || inEString;
        }
    }

    /**
     * Handler for processing specific character patterns during SQL parsing.
     * Implements Chain of Responsibility pattern for modular parsing logic.
     */
    protected interface CharacterHandler {
        /**
         * Check if this handler can process the current character(s).
         *
         * @param c current character
         * @param next next character (or '\0' if at end)
         * @param state current parser state
         * @return true if this handler should process these characters
         */
        boolean canHandle(char c, char next, ParserState state);

        /**
         * Process the current character(s) and update state.
         *
         * @param sql full SQL string
         * @param index current position in SQL
         * @param state parser state (will be modified)
         * @param output StringBuilder to append processed characters
         * @return new index position after processing (use index+1 if only consumed current char)
         */
        int handle(String sql, int index, ParserState state, StringBuilder output);
    }

    /**
     * Handles block comments with nested support.
     * Supports nested comments.
     */
    protected static class BlockCommentHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            // Opening comment: /* (when not inside quotes)
            if (!state.isInAnyQuote() && c == '/' && next == '*') {
                return true;
            }
            // Closing comment: */ (when inside comment)
            if (state.commentDepth > 0 && c == '*' && next == '/') {
                return true;
            }
            return false;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char c = sql.charAt(index);
            char next = (index + 1 < sql.length()) ? sql.charAt(index + 1) : '\0';

            if (c == '/' && next == '*') {
                // Opening /*
                state.commentDepth++;
                output.append(' '); // Replace comment with space
                return index + 2; // Skip both /* characters
            } else if (c == '*' && next == '/') {
                // Closing */
                state.commentDepth--;
                output.append(' '); // Replace comment with space
                return index + 2; // Skip both */ characters
            }

            // Should not reach here if canHandle is correct
            return index + 1;
        }
    }

    /**
     * Handles line comments: -- and #
     * Respects JSON operators like {@code #>}, {@code #>>}, {@code ->}, and {@code ->>} (not treated as comments).
     */
    protected class LineCommentHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (state.isInAnyQuote() || state.isInComment()) {
                return false;
            }

            // Dash dash comment: -- (but NOT if part of --> operator)
            if (c == '-' && next == '-') {
                // We need access to sql and index to check ahead, so we'll handle this in handle()
                return true;
            }

            // Hash comment: # (but NOT #> or #>> which are PostgreSQL JSON operators)
            if (supportsHashComments() && c == '#' && next != '>') {
                return true;
            }

            // Newline ends line comment
            if (state.inLineComment && c == '\n') {
                return true;
            }

            return false;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char c = sql.charAt(index);
            char next = (index + 1 < sql.length()) ? sql.charAt(index + 1) : '\0';

            if (c == '-' && next == '-') {
                // Check if this is part of --> operator (PostgreSQL JSON ->>)
                char afterNext = (index + 2 < sql.length()) ? sql.charAt(index + 2) : '\0';
                if (afterNext == '>') {
                    // This is -->, not a comment - don't consume it
                    // Just append the first - and let the loop continue
                    output.append(c);
                    return index + 1;
                }

                // It's a real comment
                state.inLineComment = true;
                output.append(' '); // Replace with space
                return index + 2; // Skip --
            } else if (supportsHashComments() && c == '#' && next != '>') {
                state.inLineComment = true;
                output.append(' '); // Replace with space
                return index + 1;
            } else if (c == '\n') {
                state.inLineComment = false;
                output.append(' '); // Replace newline with space
                return index + 1;
            }

            return index + 1;
        }
    }

    /**
     * Handles characters inside comments (replaces with spaces).
     * Must be checked after comment start/end handlers.
     */
    protected static class InCommentAppenderHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            return state.isInComment() || state.inLineComment;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            output.append(' '); // Replace comment content with space
            return index + 1;
        }
    }

    /**
     * Handles curly brace comments: { ... } (Informix-style).
     * Supports nested comments. Only active if supportsCurlyBraceComments() returns true.
     */
    protected class CurlyBraceCommentHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (!supportsCurlyBraceComments()) {
                return false;
            }
            // Opening {
            if (!state.isInAnyQuote() && c == '{') {
                return true;
            }
            // Closing }
            if (state.curlyCommentDepth > 0 && c == '}') {
                return true;
            }
            return false;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char c = sql.charAt(index);

            if (c == '{') {
                state.curlyCommentDepth++;
                output.append(' ');
                return index + 1;
            } else if (c == '}') {
                state.curlyCommentDepth--;
                output.append(' ');
                return index + 1;
            }

            return index + 1;
        }
    }

    /**
     * Handles backslash escaping in strings (MySQL-style and PostgreSQL E-strings).
     * When inside a string, backslash escapes the next character.
     */
    protected class BackslashEscapeHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (c != '\\' || next == '\0') {
                return false;
            }

            // MySQL mode: backslash escapes in regular strings
            if (supportsBackslashEscapes()) {
                boolean inString = state.inSingleQuoteString ||
                                 (state.inDoubleQuoteIdentifier && supportsDoubleQuotedStringsAsStrings());
                if (inString) {
                    return true;
                }
            }

            // PostgreSQL E-strings: backslash escapes enabled
            if (state.inEString) {
                return true;
            }

            return false;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char next = sql.charAt(index + 1);

            // Append backslash and escaped character
            output.append('\\');
            output.append(next);

            return index + 2; // Skip both backslash and next char
        }
    }

    /**
     * Handles E-strings (PostgreSQL): E'text\n'
     * E-prefix enables C-style escape sequences like \n, \t, \\, \', etc.
     * Only active if supportsEStrings() returns true.
     *
     * <p><b>Important:</b> E-strings are only recognized when 'E' or 'e' is NOT preceded by
     * an alphanumeric character (to avoid false positives like {@code column_nameE'value'}).
     */
    protected class EStringHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (!supportsEStrings() || state.isInComment() || state.inLineComment) {
                return false;
            }

            // Not inside other quotes (INCLUDING regular single-quoted strings!)
            if (state.inSingleQuoteString || state.inDoubleQuoteIdentifier || state.inBacktickIdentifier ||
                state.inSquareBracketIdentifier || state.dollarQuoteTag != null) {
                return false;
            }

            // Check for E' pattern (case-insensitive)
            if (!state.inEString && (c == 'E' || c == 'e') && next == '\'') {
                return true;
            }

            // Inside E-string, handle quotes
            if (state.inEString && c == '\'') {
                return true;
            }

            return false;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char c = sql.charAt(index);
            char next = (index + 1 < sql.length()) ? sql.charAt(index + 1) : '\0';

            if (!state.inEString && (c == 'E' || c == 'e') && next == '\'') {
                // CRITICAL: Verify that 'E' is NOT preceded by alphanumeric char
                // This prevents false positives like: column_nameE'value' being parsed as E-string
                if (index > 0) {
                    char prev = sql.charAt(index - 1);
                    if (Character.isLetterOrDigit(prev) || prev == '_') {
                        // Not an E-string, just a regular identifier followed by string
                        // Let the SingleQuoteHandler process the quote
                        output.append(c); // Append 'E' or 'e'
                        return index + 1;
                    }
                }

                // Opening E'
                state.inEString = true;
                output.append(c); // E or e
                output.append('\'');
                return index + 2;
            } else if (state.inEString && c == '\'') {
                // Check if this quote is escaped with backslash
                if (index > 0 && sql.charAt(index - 1) == '\\') {
                    // This quote was already appended by backslash handler
                    // Just continue
                    return index + 1;
                }

                // Check for doubled quote ''
                if (next == '\'') {
                    output.append("''");
                    return index + 2;
                }

                // Closing quote
                state.inEString = false;
                output.append('\'');
                return index + 1;
            }

            return index + 1;
        }
    }

    /**
     * Handles single-quoted strings: 'text'
     * Supports doubled quote escaping: ''
     */
    protected static class SingleQuoteHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (state.isInComment() || state.inLineComment || c != '\'') {
                return false;
            }

            // Not inside other quote types
            return !state.inDoubleQuoteIdentifier && !state.inBacktickIdentifier && !state.inSquareBracketIdentifier;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char next = (index + 1 < sql.length()) ? sql.charAt(index + 1) : '\0';

            if (!state.inSingleQuoteString) {
                // Opening quote
                state.inSingleQuoteString = true;
                output.append('\'');
                return index + 1;
            } else {
                // Inside string - check for escaped quote ''
                if (next == '\'') {
                    output.append("''");
                    return index + 2; // Skip both quotes
                } else {
                    // Closing quote
                    state.inSingleQuoteString = false;
                    output.append('\'');
                    return index + 1;
                }
            }
        }
    }

    /**
     * Handles double-quoted identifiers/strings: "text"
     * Behavior depends on supportsDoubleQuotedStringsAsStrings().
     * Supports doubled quote escaping: ""
     */
    protected static class DoubleQuoteHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (state.isInComment() || state.inLineComment || c != '"') {
                return false;
            }

            // Not inside other quote types
            return !state.inSingleQuoteString && !state.inBacktickIdentifier && !state.inSquareBracketIdentifier;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char next = (index + 1 < sql.length()) ? sql.charAt(index + 1) : '\0';

            if (!state.inDoubleQuoteIdentifier) {
                // Opening quote
                state.inDoubleQuoteIdentifier = true;
                output.append('"');
                return index + 1;
            } else {
                // Inside identifier/string - check for escaped quote ""
                if (next == '"') {
                    output.append("\"\"");
                    return index + 2; // Skip both quotes
                } else {
                    // Closing quote
                    state.inDoubleQuoteIdentifier = false;
                    output.append('"');
                    return index + 1;
                }
            }
        }
    }

    /**
     * Handles backtick identifiers: `text` (MySQL-style).
     * Only active if supportsBacktickIdentifiers() returns true.
     * Supports doubled backtick escaping: ``
     */
    protected class BacktickHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (!supportsBacktickIdentifiers() || state.isInComment() || state.inLineComment || c != '`') {
                return false;
            }

            // Not inside other quote types
            return !state.inSingleQuoteString && !state.inDoubleQuoteIdentifier && !state.inSquareBracketIdentifier;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char next = (index + 1 < sql.length()) ? sql.charAt(index + 1) : '\0';

            if (!state.inBacktickIdentifier) {
                // Opening backtick
                state.inBacktickIdentifier = true;
                output.append('`');
                return index + 1;
            } else {
                // Inside identifier - check for escaped backtick ``
                if (next == '`') {
                    output.append("``");
                    return index + 2; // Skip both backticks
                } else {
                    // Closing backtick
                    state.inBacktickIdentifier = false;
                    output.append('`');
                    return index + 1;
                }
            }
        }
    }

    /**
     * Handles square bracket identifiers: [text] (SQL Server-style).
     * Only active if supportsSquareBracketIdentifiers() returns true.
     * Supports doubled bracket escaping: ]]
     */
    protected class SquareBracketHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (!supportsSquareBracketIdentifiers() || state.isInComment() || state.inLineComment) {
                return false;
            }

            // Opening [
            if (c == '[' && !state.inSingleQuoteString && !state.inDoubleQuoteIdentifier &&
                !state.inBacktickIdentifier && !state.inSquareBracketIdentifier) {
                return true;
            }

            // Closing ]
            if (c == ']' && state.inSquareBracketIdentifier) {
                return true;
            }

            return false;
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            char c = sql.charAt(index);
            char next = (index + 1 < sql.length()) ? sql.charAt(index + 1) : '\0';

            if (c == '[') {
                state.inSquareBracketIdentifier = true;
                output.append('[');
                return index + 1;
            } else if (c == ']') {
                // Check for escaped bracket ]]
                if (next == ']') {
                    output.append("]]");
                    return index + 2;
                } else {
                    state.inSquareBracketIdentifier = false;
                    output.append(']');
                    return index + 1;
                }
            }

            return index + 1;
        }
    }

    /**
     * Handles dollar-quoted strings: $$text$$ or $tag$text$tag$ (PostgreSQL-style).
     * Only active if supportsDollarQuotedStrings() returns true.
     * Supports nested dollar quotes with different tags.
     */
    protected class DollarQuoteHandler implements CharacterHandler {
        @Override
        public boolean canHandle(char c, char next, ParserState state) {
            if (!supportsDollarQuotedStrings() || state.isInComment() || state.inLineComment) {
                return false;
            }

            // Not inside other quotes (but CAN be inside another dollar quote with different tag)
            if (state.inSingleQuoteString || state.inDoubleQuoteIdentifier ||
                state.inBacktickIdentifier || state.inSquareBracketIdentifier) {
                return false;
            }

            // Check if we're at a $ that could start/end a dollar quote
            return c == '$';
        }

        @Override
        public int handle(String sql, int index, ParserState state, StringBuilder output) {
            // Try to match a dollar tag: $[identifier]$
            String tag = extractDollarTag(sql, index);

            if (tag == null) {
                // Not a valid dollar quote, treat as regular character
                output.append('$');
                return index + 1;
            }

            // Check if we're opening or closing a dollar quote
            if (state.dollarQuoteTag == null) {
                // Opening a new dollar-quoted string
                state.dollarQuoteTag = tag;
                output.append(tag);
                return index + tag.length();
            } else if (tag.equals(state.dollarQuoteTag)) {
                // Closing the current dollar-quoted string
                state.dollarQuoteTag = null;
                output.append(tag);
                return index + tag.length();
            } else {
                // Different tag, just append and continue
                output.append(tag);
                return index + tag.length();
            }
        }

        /**
         * Extract dollar tag from current position.
         * Returns null if not a valid dollar tag.
         * Valid formats: $$ or $identifier$ where identifier is alphanumeric/underscore.
         */
        private String extractDollarTag(String sql, int index) {
            if (index >= sql.length() || sql.charAt(index) != '$') {
                return null;
            }

            StringBuilder tag = new StringBuilder("$");
            int i = index + 1;

            // Read identifier characters (letters, digits, underscore)
            while (i < sql.length()) {
                char c = sql.charAt(i);
                if (c == '$') {
                    // Found closing $
                    tag.append('$');
                    return tag.toString();
                } else if (Character.isLetterOrDigit(c) || c == '_') {
                    // Valid identifier character
                    tag.append(c);
                    i++;
                } else {
                    // Invalid character for dollar tag
                    return null;
                }
            }

            // Reached end of string without closing $
            return null;
        }
    }

}
