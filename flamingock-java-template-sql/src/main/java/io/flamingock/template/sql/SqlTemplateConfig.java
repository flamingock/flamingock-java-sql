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

import io.flamingock.api.template.TemplateField;
import io.flamingock.api.template.TemplatePayloadValidationError;
import io.flamingock.api.template.TemplateValidationContext;

import java.util.Collections;
import java.util.List;

/**
 * Configuration class for SqlTemplate.
 * Allows customization of SQL splitting behavior.
 */
public class SqlTemplateConfig implements TemplateField {

    /**
     * Whether to split the SQL string into multiple statements according to its dialect.
     * Default is true for backward compatibility.
     */
    private boolean splitStatements = true;

    public boolean isSplitStatements() {
        return splitStatements;
    }

    public void setSplitStatements(boolean splitStatements) {
        this.splitStatements = splitStatements;
    }

    @Override
    public List<TemplatePayloadValidationError> validate(TemplateValidationContext context) {
        return Collections.emptyList();
    }

}
