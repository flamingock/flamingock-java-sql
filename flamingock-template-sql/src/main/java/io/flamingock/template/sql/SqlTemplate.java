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
package io.flamingock.template.sql;

import io.flamingock.api.annotations.ApplyTemplate;
import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.api.annotations.RollbackTemplate;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.wrappers.TemplateString;
import io.flamingock.template.sql.util.SqlSplitterFactory;
import io.flamingock.template.sql.util.SqlStatement;
import io.flamingock.template.sql.util.splitter.SqlSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

/**
 * Flamingock template that executes raw SQL statements from YAML change files.
 *
 * <p>YAML schema:
 * <pre>
 * id: create-users-table
 * template: sql-template
 * apply: |
 *   CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100));
 *   INSERT INTO users VALUES (1, 'admin');
 * rollback: "DROP TABLE users;"   # optional
 * config:
 *   splitStatements: true         # default; set to false for drivers that accept multi-statement execute
 * </pre>
 *
 * <p>The {@code apply} payload is split into individual statements by
 * {@link io.flamingock.template.sql.util.SqlSplitterFactory}, which selects a
 * dialect-aware splitter based on the JDBC connection metadata. Each statement is
 * executed via {@link java.sql.Statement#execute(String)}.
 *
 * <p>The {@code rollback} payload is optional ({@code rollbackPayloadRequired = false}).
 * If absent or blank, {@link #rollback(Connection)} is a no-op.
 *
 * @see SqlTemplateConfig for configuration options
 */
@ChangeTemplate(name = "sql-template", rollbackPayloadRequired = false)
public class SqlTemplate extends AbstractChangeTemplate<SqlTemplateConfig, TemplateString, TemplateString> {

    private static final Logger logger = LoggerFactory.getLogger(SqlTemplate.class);

    public SqlTemplate() {
        super();
    }

    @ApplyTemplate
    public void apply(Connection connection) {
        execute(connection, applyPayload.getValue());
    }

    @RollbackTemplate
    public void rollback(Connection connection) {
        if (rollbackPayload != null && rollbackPayload.getValue() != null
                && !rollbackPayload.getValue().trim().isEmpty()) {
            execute(connection, rollbackPayload.getValue());
        }
    }

    private void execute(Connection connection, String sql) {

        boolean splitStatements = configuration == null || configuration.isSplitStatements();

        List<SqlStatement> statements;
        if (splitStatements) {
            SqlSplitter sqlSplitter = SqlSplitterFactory.createForDialect(connection);
            statements = sqlSplitter.split(sql);
        } else {
            statements = Collections.singletonList(new SqlStatement(sql.trim()));
        }

        for (SqlStatement stmt : statements) {
            String sqlText = stmt.getSql().trim();
            if (sqlText.isEmpty()) continue;

            int repeatCount = stmt.getRepeatCount();
            for (int i = 0; i < repeatCount; i++) {
                executeSingle(connection, sqlText);
            }
        }
    }

    private void executeSingle(Connection connection, String stmtSql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(stmtSql);
        } catch (SQLException e) {
            logger.debug("Failed SQL (full text): {}", stmtSql);
            String truncated = stmtSql.length() > 200 ? stmtSql.substring(0, 200) + "..." : stmtSql;
            String errorMsg = "SQL execution failed: " + truncated;
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
}
