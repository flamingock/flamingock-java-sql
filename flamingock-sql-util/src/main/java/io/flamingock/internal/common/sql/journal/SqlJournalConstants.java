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

/**
 * SQL names used by the relational Journal Event store.
 */
public final class SqlJournalConstants {

    public static final String EVENT_ID = "event_id";
    public static final String EVENT_TYPE = "event_type";
    public static final String EVENT_VERSION = "event_version";
    public static final String STREAM_ID = "stream_id";
    public static final String STREAM_SEQUENCE = "stream_sequence";
    public static final String OCCURRED_AT = "occurred_at";
    public static final String ACKNOWLEDGED = "acknowledged";

    public static final String PENDING_EVENTS_INDEX = "pending_events";
    public static final String EVENT_ID_INDEX = "event_id";

    private SqlJournalConstants() {
    }

    /**
     * Validates a configured SQL identifier before it is interpolated into DDL or DML.
     *
     * @param value     identifier to validate
     * @param fieldName configuration field containing the identifier
     */
    public static void validateIdentifier(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!value.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(fieldName + " must be a simple SQL identifier");
        }
    }

    /**
     * Ensures that two configured SQL resources cannot address the same table.
     */
    public static void validateDistinct(String firstName,
                                        String firstField,
                                        String secondName,
                                        String secondField) {
        if (firstName.trim().equalsIgnoreCase(secondName.trim())) {
            throw new IllegalArgumentException(firstField + " and " + secondField + " must not be the same");
        }
    }
}
