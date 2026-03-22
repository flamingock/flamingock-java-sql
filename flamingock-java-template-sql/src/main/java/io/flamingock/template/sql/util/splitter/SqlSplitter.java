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

import java.util.List;

/**
 * Interface for SQL statement splitting logic.
 * Allows different splitting behaviors for various database types.
 */
public interface SqlSplitter {

    /**
     * Splits the given SQL string into individual statements using the delimiter.
     * Must handle strings, comments, and nested blocks properly.
     *
     * <p>The returned {@link SqlStatement} objects may include metadata such as
     * repeat counts (e.g., SQL Server "GO 5" means execute the batch 5 times).
     *
     * @param sql the SQL string to split
     * @return list of SQL statements with execution metadata
     */
    List<SqlStatement> split(String sql);

}
