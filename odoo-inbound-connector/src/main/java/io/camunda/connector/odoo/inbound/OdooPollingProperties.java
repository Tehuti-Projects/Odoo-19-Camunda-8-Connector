package io.camunda.connector.odoo.inbound;

import jakarta.validation.constraints.NotEmpty;

public record OdooPollingProperties(
        @NotEmpty String url,
        @NotEmpty String database,
        @NotEmpty String apiKey,
        @NotEmpty String model,
        Integer pollingInterval,
        String triggerField,
        String triggerCondition,
        String filterDomain,
        String fields,
        Integer batchSize,
        Boolean compatibilityModeEnabled,
        String compatibilityReason) {

    public int getEffectivePollingInterval() {
        return pollingInterval != null && pollingInterval >= 10 ? pollingInterval : 30;
    }

    public int getEffectiveBatchSize() {
        if (batchSize == null) {
            return 50;
        }
        return Math.max(1, Math.min(100, batchSize));
    }

    public boolean triggerOnNew() {
        return "NEW".equals(triggerCondition) || "BOTH".equals(triggerCondition) || triggerCondition == null;
    }

    public boolean triggerOnModified() {
        return "MODIFIED".equals(triggerCondition) || "BOTH".equals(triggerCondition) || triggerCondition == null;
    }

    public String getEffectiveTriggerField() {
        return triggerField != null && !triggerField.isBlank() ? triggerField : "write_date";
    }

    public void validateForMau() {
        if (!Boolean.TRUE.equals(compatibilityModeEnabled)) {
            throw new IllegalArgumentException(
                    "Generic Odoo model polling is disabled by default in MAU. Use Odoo outbound event webhooks or approved recovery tooling instead.");
        }
        if (compatibilityReason == null || compatibilityReason.isBlank()) {
            throw new IllegalArgumentException(
                    "compatibilityReason is required when enabling legacy polling in MAU.");
        }
    }
}
