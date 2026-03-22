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

import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.common.sql.SqlDialectFactory;
import io.flamingock.template.sql.util.splitter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Factory for creating database-specific SQL statement splitters.
 *
 * <p>This factory analyzes the database connection to determine the SQL dialect
 * and returns an appropriate {@link SqlSplitter} implementation optimized for
 * that database's specific syntax and features.
 *
 * <p>Supported databases include:
 * <ul>
 *   <li>MySQL and MariaDB - with support for backtick identifiers, DELIMITER command, and hash comments</li>
 *   <li>PostgreSQL - with support for dollar-quoted strings, E-strings, and DO blocks</li>
 *   <li>Oracle - with support for forward slash delimiter and PL/SQL blocks</li>
 *   <li>SQL Server and Sybase - with support for GO batch separator</li>
 *   <li>SQLite - with support for multiple identifier quote styles</li>
 *   <li>H2 - with support for PostgreSQL and MySQL compatibility modes</li>
 *   <li>Firebird - with support for SET TERM directive</li>
 *   <li>DB2 - with support for @ delimiter in CLP scripts</li>
 *   <li>Informix - with support for curly brace comments and SPL syntax</li>
 * </ul>
 *
 * <p>Each splitter handles dialect-specific features such as:
 * <ul>
 *   <li>Statement delimiters (semicolon, GO, forward slash, etc.)</li>
 *   <li>String and identifier quoting (single quotes, double quotes, backticks)</li>
 *   <li>Comments (line comments, block comments, nested comments)</li>
 *   <li>Stored procedure/function blocks that should not be split internally</li>
 * </ul>
 *
 * @see SqlSplitter
 * @see AbstractSqlSplitter
 */
public class SqlSplitterFactory {

    private static final Logger logger = LoggerFactory.getLogger(SqlSplitterFactory.class);

    private static final Map<SqlDialect, Supplier<SqlSplitter>> customSplitters = new ConcurrentHashMap<>();

    /**
     * Registers a custom splitter for a given dialect, overriding the built-in one.
     * Useful for extending support to new dialects without modifying this factory.
     *
     * @param dialect the SQL dialect to register
     * @param supplier the supplier that creates the splitter instance
     */
    public static void registerSplitter(SqlDialect dialect, Supplier<SqlSplitter> supplier) {
        customSplitters.put(dialect, supplier);
    }

    /**
     * Removes a previously registered custom splitter for the given dialect,
     * restoring the built-in behaviour. Intended for use in tests.
     *
     * @param dialect the SQL dialect to deregister
     */
    public static void deregisterSplitter(SqlDialect dialect) {
        customSplitters.remove(dialect);
    }

    public static SqlSplitter createForDialect(Connection connection) {

        SqlDialect dialect = SqlDialectFactory.getSqlDialect(connection);

        Supplier<SqlSplitter> custom = customSplitters.get(dialect);
        if (custom != null) {
            return custom.get();
        }

        switch (dialect) {

            case MYSQL:
                return new MySqlSplitter();
            case MARIADB:
                return new MariaDbSplitter();
            case POSTGRESQL:
                return new PostgreSqlSplitter();
            case SQLITE:
                return new SqliteSplitter();
            case H2:
                return new H2Splitter();
            case SQLSERVER:
                return new SqlServerSplitter();
            case SYBASE:
                return new SybaseSplitter();
            case FIREBIRD:
                return new FirebirdSplitter();
            case INFORMIX:
                return new InformixSplitter();
            case ORACLE:
                return new OracleSplitter();
            case DB2:
                return new Db2Splitter();
            default:
                logger.warn("SQL dialect '{}' has no dedicated splitter; falling back to standard semicolon splitting. " +
                            "Dialect-specific features may not work.", dialect);
                return new AbstractSqlSplitter() {};

        }
    }
}
