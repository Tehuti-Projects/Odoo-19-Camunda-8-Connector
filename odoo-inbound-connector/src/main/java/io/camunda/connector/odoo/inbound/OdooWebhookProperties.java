package io.camunda.connector.odoo.inbound;

import jakarta.validation.constraints.NotEmpty;

/**
 * Configuration properties for Odoo inbound webhook connector.
 * Defines the webhook endpoint settings and filtering options.
 * 
 * Note: Element template is handcrafted in
 * element-templates/odoo-inbound-connector.json
 */
public record OdooWebhookProperties(
        /** Secret token to validate webhook requests from Odoo */
        @NotEmpty String secretToken,

        /**
         * Comma-separated list of Odoo model names to accept (e.g.,
         * res.partner,sale.order). Leave empty for all.
         */
        String allowedModels,

        /**
         * Type of Odoo event to listen for: all, create, write, unlink, create_write
         */
        String eventType) {

    /**
     * Check if the given model is allowed by this configuration.
     */
    public boolean isModelAllowed(String model) {
        if (allowedModels == null || allowedModels.isBlank()) {
            return true;
        }
        String[] models = allowedModels.split(",");
        for (String allowed : models) {
            if (allowed.trim().equals(model)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the given event type is accepted by this configuration.
     */
    public boolean isEventTypeAllowed(String event) {
        if (eventType == null || eventType.equals("all")) {
            return true;
        }
        if (eventType.equals("create_write")) {
            return event.equals("create") || event.equals("write");
        }
        return eventType.equals(event);
    }
}
