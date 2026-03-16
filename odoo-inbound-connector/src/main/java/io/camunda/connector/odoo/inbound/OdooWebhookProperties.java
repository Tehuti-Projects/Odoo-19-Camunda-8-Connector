package io.camunda.connector.odoo.inbound;

import jakarta.validation.constraints.NotEmpty;

public record OdooWebhookProperties(
        @NotEmpty String secretToken,
        String allowedEventTypes,
        Boolean allowLegacyGenericWebhook) {

    public boolean isEventTypeAllowed(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return false;
        }
        if (allowedEventTypes == null || allowedEventTypes.isBlank()) {
            return true;
        }
        String[] allowed = allowedEventTypes.split(",");
        for (String candidate : allowed) {
            if (candidate.trim().equals(eventType)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsLegacyGenericWebhook() {
        return Boolean.TRUE.equals(allowLegacyGenericWebhook);
    }
}
