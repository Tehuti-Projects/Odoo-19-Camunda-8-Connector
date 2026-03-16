package io.camunda.connector.odoo.inbound;

import jakarta.validation.constraints.NotEmpty;

/**
 * Configuration properties for Odoo inbound polling connector.
 */
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
        Integer batchSize) {
    public int getEffectivePollingInterval() {
        return pollingInterval != null && pollingInterval >= 10 ? pollingInterval : 30;
    }

    public int getEffectiveBatchSize() {
        if (batchSize == null)
            return 50;
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
}
