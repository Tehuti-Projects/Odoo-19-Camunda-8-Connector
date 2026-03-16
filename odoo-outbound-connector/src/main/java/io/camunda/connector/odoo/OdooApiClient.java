package io.camunda.connector.odoo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.odoo.dto.OdooAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Direct read-only HTTP client for Odoo 19's External JSON-2 API.
 * 
 * <p>
 * Uses the new Odoo 19 /json/2 endpoint format:
 * <ul>
 * <li>URL: POST /json/2/{model}/{method}</li>
 * <li>Auth: Authorization: bearer {api_key}</li>
 * <li>Database: X-Odoo-Database header</li>
 * <li>Body: JSON with ids, context, fields, domain, etc.</li>
 * </ul>
 * 
 * <p>
 * This implementation uses Java's built-in HttpClient (java.net.http) to avoid
 * classloader conflicts with the Camunda 8 Connector Runtime.
 *
 * <p>
 * MAU uses this client only for controlled direct reads. Critical business writes
 * must go through the MAU bridge endpoints instead of generic JSON-2 mutation.
 * 
 * @see <a href=
 *      "https://www.odoo.com/documentation/19.0/developer/reference/external_api.html">Odoo
 *      External JSON-2 API</a>
 */
public class OdooApiClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OdooApiClient.class);

    // Configuration
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 60;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OdooAuthentication auth;
    private final String baseUrl;
    private final String userAgent;

    /**
     * Create a new client with the given authentication.
     */
    public OdooApiClient(OdooAuthentication auth) {
        this(auth, "Camunda-Odoo-Connector/1.0");
    }

    /**
     * Create a new client with custom user agent.
     */
    public OdooApiClient(OdooAuthentication auth, String userAgent) {
        this.auth = auth;
        this.baseUrl = normalizeUrl(auth.url()) + "/json/2";
        this.userAgent = userAgent;
        this.objectMapper = new ObjectMapper();
        this.httpClient = buildHttpClient();

        LOG.info("Odoo API client initialized for {}", auth.url());
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    private String normalizeUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ==================== Core API Methods ====================

    /**
     * Execute a method on an Odoo model using the JSON-2 API.
     * 
     * @param model  The model name (e.g., "res.partner")
     * @param method The method name (e.g., "search", "read", "create")
     * @param body   The request body parameters
     * @return The response from Odoo (type depends on the method)
     * @throws OdooApiException If the request fails
     */
    private Object execute(String model, String method, Map<String, Object> body)
            throws OdooApiException {
        return executeWithRetry(model, method, body, MAX_RETRIES);
    }

    private Object executeWithRetry(String model, String method, Map<String, Object> body, int retriesLeft)
            throws OdooApiException {
        String url = String.format("%s/%s/%s", baseUrl, model, method);

        try {
            // Add context if not present
            Map<String, Object> requestBody = new LinkedHashMap<>();
            if (body != null) {
                requestBody.putAll(body);
            }
            if (!requestBody.containsKey("context")) {
                requestBody.put("context", Map.of("lang", "en_US"));
            }

            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            LOG.debug("Odoo API request: {} {} - {}", method.toUpperCase(), url, jsonPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", "bearer " + auth.apiKey())
                    .header("X-Odoo-Database", auth.database())
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body() != null ? response.body() : "";

            LOG.debug("Odoo API response [{}]: {}", response.statusCode(),
                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

            // Handle HTTP errors
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                OdooError error = parseErrorResponse(responseBody, response.statusCode());

                // Retry on transient errors
                if (isRetryableError(response.statusCode()) && retriesLeft > 0) {
                    LOG.warn("Retrying after transient error ({}): {}", response.statusCode(), error.message());
                    sleep(RETRY_DELAY_MS);
                    return executeWithRetry(model, method, body, retriesLeft - 1);
                }

                throw new OdooApiException(error);
            }

            // Parse successful response
            return objectMapper.readValue(responseBody, Object.class);

        } catch (JsonProcessingException e) {
            throw new OdooApiException("Failed to serialize/deserialize JSON: " + e.getMessage(), e);
        } catch (IOException e) {
            // Retry on network errors
            if (retriesLeft > 0) {
                LOG.warn("Retrying after network error: {}", e.getMessage());
                sleep(RETRY_DELAY_MS);
                return executeWithRetry(model, method, body, retriesLeft - 1);
            }
            throw new OdooApiException("Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OdooApiException("Request interrupted: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private OdooError parseErrorResponse(String responseBody, int statusCode) {
        try {
            Map<String, Object> error = objectMapper.readValue(responseBody, Map.class);
            String name = (String) error.getOrDefault("name", "Unknown");
            String message = (String) error.getOrDefault("message", "HTTP " + statusCode);
            List<Object> arguments = (List<Object>) error.getOrDefault("arguments", List.of());
            String debug = (String) error.getOrDefault("debug", "");
            return new OdooError(statusCode, name, message, arguments, debug);
        } catch (Exception e) {
            return new OdooError(statusCode, "HTTP Error",
                    "HTTP " + statusCode + ": " + responseBody.substring(0, Math.min(200, responseBody.length())),
                    List.of(), "");
        }
    }

    private boolean isRetryableError(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Read-only Operations ====================

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> read(String model, List<Integer> ids, List<String> fields)
            throws OdooApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", ids);
        if (fields != null && !fields.isEmpty()) {
            body.put("fields", fields);
        }

        Object result = execute(model, "read", body);
        if (result instanceof List) {
            return (List<Map<String, Object>>) result;
        }
        throw new OdooApiException("Unexpected read result type: " + result);
    }

    public List<Integer> search(String model, List<Object> domain, Integer limit, Integer offset)
            throws OdooApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("domain", domain != null ? domain : List.of());
        if (limit != null)
            body.put("limit", limit);
        if (offset != null)
            body.put("offset", offset);

        Object result = execute(model, "search", body);
        if (result instanceof List) {
            return ((List<?>) result).stream()
                    .map(item -> ((Number) item).intValue())
                    .toList();
        }
        throw new OdooApiException("Unexpected search result type: " + result);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchRead(String model, List<Object> domain,
            List<String> fields, Integer limit, Integer offset) throws OdooApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("domain", domain != null ? domain : List.of());
        if (fields != null && !fields.isEmpty())
            body.put("fields", fields);
        if (limit != null)
            body.put("limit", limit);
        if (offset != null)
            body.put("offset", offset);

        Object result = execute(model, "search_read", body);
        if (result instanceof List) {
            return (List<Map<String, Object>>) result;
        }
        throw new OdooApiException("Unexpected search_read result type: " + result);
    }

    public Integer searchCount(String model, List<Object> domain) throws OdooApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("domain", domain != null ? domain : List.of());

        Object result = execute(model, "search_count", body);
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        throw new OdooApiException("Unexpected search_count result type: " + result);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fieldsGet(String model, List<String> fields) throws OdooApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        if (fields != null && !fields.isEmpty()) {
            body.put("allfields", fields);
        }
        body.put("attributes", List.of("string", "type", "required", "readonly", "selection"));

        Object result = execute(model, "fields_get", body);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        throw new OdooApiException("Unexpected fields_get result type: " + result);
    }

    /**
     * Check if connection to Odoo is valid.
     */
    public boolean testConnection() {
        try {
            // Try to read res.users fields - should work if authenticated
            fieldsGet("res.users", List.of("name"));
            return true;
        } catch (OdooApiException e) {
            LOG.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        // HttpClient doesn't require explicit cleanup
        LOG.debug("Odoo API client closed");
    }

    // ==================== Error Handling ====================

    /**
     * Structured error from Odoo API.
     */
    public record OdooError(
            int statusCode,
            String name,
            String message,
            List<Object> arguments,
            String debug) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(statusCode).append("] ");
            sb.append(name).append(": ").append(message);
            return sb.toString();
        }
    }

    /**
     * Exception for Odoo API errors.
     */
    public static class OdooApiException extends Exception {
        private final OdooError error;

        public OdooApiException(String message) {
            super(message);
            this.error = null;
        }

        public OdooApiException(String message, Throwable cause) {
            super(message, cause);
            this.error = null;
        }

        public OdooApiException(OdooError error) {
            super(error.toString());
            this.error = error;
        }

        public OdooError getError() {
            return error;
        }

        public int getStatusCode() {
            return error != null ? error.statusCode() : 0;
        }

        public boolean isAuthenticationError() {
            return error != null && error.statusCode() == 401;
        }

        public boolean isPermissionError() {
            return error != null && error.statusCode() == 403;
        }

        public boolean isNotFoundError() {
            return error != null && error.statusCode() == 404;
        }

        public boolean isValidationError() {
            return error != null && (error.statusCode() == 400 || error.statusCode() == 422);
        }
    }
}
