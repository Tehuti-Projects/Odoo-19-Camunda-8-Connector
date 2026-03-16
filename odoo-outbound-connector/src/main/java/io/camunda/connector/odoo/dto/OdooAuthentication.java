package io.camunda.connector.odoo.dto;

import jakarta.validation.constraints.NotEmpty;

/**
 * Authentication configuration for the MAU Odoo connector.
 *
 * <p>
 * Bridge commands and direct JSON-2 reads use different credentials on purpose:
 * bridge writes use the MAU bridge technical key, while optional direct reads use
 * Odoo's JSON-2 API key.
 */
public record OdooAuthentication(
        @NotEmpty String url,
        String database,
        String apiKey,
        String bridgeApiKey) {

    public void validateBaseUrl() {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Odoo URL is required");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
    }

    public void validateForBridge() {
        validateBaseUrl();
        if (bridgeApiKey == null || bridgeApiKey.isBlank()) {
            throw new IllegalArgumentException("bridgeApiKey is required for MAU bridge commands");
        }
    }

    public void validateForDirectRead() {
        validateBaseUrl();
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("database is required for direct Odoo JSON-2 reads");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required for direct Odoo JSON-2 reads");
        }
    }

    public String normalizedUrl() {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String authorizationHeader() {
        return "bearer " + apiKey;
    }
}
