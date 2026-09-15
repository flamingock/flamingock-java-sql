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
package io.flamingock.internal.common.sql.journal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SQL column names used to persist the flattened, typed representation of an audit entry
 * embedded in a Journal Event row.
 */
public final class SqlAuditColumnNames {

    private static final List<String> COLUMN_NAMES = Collections.unmodifiableList(Arrays.asList(
            "execution_id", "stage_id", "change_id", "author", "created_at", "state", "invoked_class",
            "invoked_method", "source_file", "metadata", "execution_millis", "execution_hostname",
            "error_trace", "type", "tx_strategy", "target_system_id", "change_order", "recovery_strategy",
            "transaction_flag", "system_change"));

    private SqlAuditColumnNames() {
    }

    public static List<String> columnNames() {
        return COLUMN_NAMES;
    }
}
