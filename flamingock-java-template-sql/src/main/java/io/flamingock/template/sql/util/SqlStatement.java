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
package io.flamingock.template.sql.util;

/**
 * Represents a SQL statement with optional execution metadata.
 *
 * <p>This class wraps a SQL statement string with additional metadata that affects
 * how the statement should be executed. Currently supports:
 * <ul>
 *   <li><b>repeatCount:</b> Number of times to execute the statement (used by SQL Server GO N)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Simple statement (executed once)
 * SqlStatement stmt1 = new SqlStatement("INSERT INTO users VALUES (1)");
 *
 * // Statement with repeat count (SQL Server "GO 5")
 * SqlStatement stmt2 = new SqlStatement("INSERT INTO test VALUES (1)", 5);
 * }</pre>
 */
public class SqlStatement {

    private final String sql;
    private final int repeatCount;

    /**
     * Creates a SQL statement with default repeat count of 1.
     *
     * @param sql the SQL statement text
     */
    public SqlStatement(String sql) {
        this(sql, 1);
    }

    /**
     * Creates a SQL statement with specified repeat count.
     *
     * @param sql the SQL statement text
     * @param repeatCount number of times to execute (must be &gt;= 1)
     * @throws IllegalArgumentException if repeatCount is less than 1
     */
    public SqlStatement(String sql, int repeatCount) {
        if (repeatCount < 1) {
            throw new IllegalArgumentException("repeatCount must be >= 1, got: " + repeatCount);
        }
        this.sql = sql;
        this.repeatCount = repeatCount;
    }

    /**
     * Returns the SQL statement text.
     *
     * @return the SQL statement
     */
    public String getSql() {
        return sql;
    }

    /**
     * Returns the number of times this statement should be executed.
     * Default is 1 for normal statements.
     *
     * @return the repeat count (&gt;= 1)
     */
    public int getRepeatCount() {
        return repeatCount;
    }

    @Override
    public String toString() {
        if (repeatCount == 1) {
            return sql;
        }
        return sql + " (repeat " + repeatCount + "x)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SqlStatement that = (SqlStatement) o;

        if (repeatCount != that.repeatCount) return false;
        return sql != null ? sql.equals(that.sql) : that.sql == null;
    }

    @Override
    public int hashCode() {
        int result = sql != null ? sql.hashCode() : 0;
        result = 31 * result + repeatCount;
        return result;
    }
}
