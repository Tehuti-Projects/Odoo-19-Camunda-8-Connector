package io.camunda.connector.odoo.dto;

import jakarta.validation.constraints.NotEmpty;

/**
 * Authentication configuration for Odoo 19 External JSON-2 API.
 */
public record OdooAuthentication(
        @NotEmpty String url,
        @NotEmpty String database,
        @NotEmpty String apiKey) {
    public void validate() {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Odoo URL is required");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("Database name is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
    }

    public String normalizedUrl() {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String authorizationHeader() {
        return "bearer " + apiKey;
    }
}
