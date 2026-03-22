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
 * Sybase ASE (Adaptive Server Enterprise) SQL statement splitter.
 *
 * <p>Sybase ASE is the ancestor of Microsoft SQL Server and shares very similar syntax.
 * This splitter inherits all SQL Server features.
 *
 * <p>Implemented features:
 * <ul>
 *   <li>{@code GO} keyword as batch separator ✅ IMPLEMENTED (inherited from SQL Server)</li>
 *   <li>{@code GO} with repeat count: {@code GO 5} ✅ IMPLEMENTED (inherited from SQL Server)</li>
 *   <li>Square bracket identifiers: {@code [column name]} ✅ IMPLEMENTED (inherited from SQL Server)</li>
 *   <li>Mixed semicolon and GO delimiters ✅ IMPLEMENTED (inherited from SQL Server)</li>
 *   <li>Nested block comments ✅ IMPLEMENTED (inherited)</li>
 * </ul>
 *
 * <p><b>Note:</b> Sybase ASE uses the same batch processing syntax as SQL Server.
 * All features are inherited from {@link SqlServerSplitter}.
 */
public class SybaseSplitter extends SqlServerSplitter {

    // Sybase uses same syntax as SQL Server, inherits from SqlServerSplitter
    // Square bracket support is automatically enabled via supportsSquareBracketIdentifiers()

}
