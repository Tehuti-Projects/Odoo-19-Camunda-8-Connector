package io.camunda.connector.odoo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.odoo.dto.MauConnectorResult;
import io.camunda.connector.odoo.dto.OdooAuthentication;
import io.camunda.connector.odoo.dto.OdooRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MauBridgeClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MauBridgeClient.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final int REQUEST_TIMEOUT_SECONDS = 60;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OdooAuthentication authentication;

    public MauBridgeClient(OdooAuthentication authentication) {
        this.authentication = authentication;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    public MauConnectorResult execute(MauBridgeOperation operation, OdooRequest request) {
        Map<String, Object> command = buildCommand(operation, request);
        String endpointUrl = authentication.normalizedUrl() + operation.endpointPath();

        try {
            String jsonPayload = objectMapper.writeValueAsString(command);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .header("X-MAU-API-Key", authentication.bridgeApiKey())
                    .header("X-Source-System", "camunda")
                    .header("X-External-Ref", request.externalRef())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            LOG.info(
                    "connector=mautourism-odoo mode=bridge_command operation={} endpoint={} event_type={} external_ref={} camunda_business_key={} reference_code={}",
                    operation.operation(),
                    operation.endpointPath(),
                    operation.eventType(),
                    request.externalRef(),
                    request.camundaBusinessKey(),
                    request.referenceCode());

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> responseBody = parseResponseBody(response.body());
            return classifyBridgeResponse(operation, request, response.statusCode(), responseBody);
        } catch (HttpTimeoutException timeoutException) {
            return MauConnectorResult.bridgeFailure(
                    operation.operation(),
                    operation.endpointPath(),
                    operation.eventType(),
                    request.externalRef(),
                    request.buildCorrelation(),
                    504,
                    "timeout",
                    timeoutException.getMessage(),
                    true,
                    true);
        } catch (IOException ioException) {
            return MauConnectorResult.bridgeFailure(
                    operation.operation(),
                    operation.endpointPath(),
                    operation.eventType(),
                    request.externalRef(),
                    request.buildCorrelation(),
                    503,
                    "downstream_unavailable",
                    ioException.getMessage(),
                    true,
                    false);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return MauConnectorResult.bridgeFailure(
                    operation.operation(),
                    operation.endpointPath(),
                    operation.eventType(),
                    request.externalRef(),
                    request.buildCorrelation(),
                    504,
                    "timeout",
                    interruptedException.getMessage(),
                    true,
                    true);
        } catch (Exception exception) {
            return MauConnectorResult.bridgeFailure(
                    operation.operation(),
                    operation.endpointPath(),
                    operation.eventType(),
                    request.externalRef(),
                    request.buildCorrelation(),
                    500,
                    "internal_error",
                    exception.getMessage(),
                    true,
                    false);
        }
    }

    Map<String, Object> buildCommand(MauBridgeOperation operation, OdooRequest request) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("event_name", operation.eventType());
        command.put("event_type", operation.eventType());
        command.put("source_system", "camunda");
        command.put("external_ref", request.externalRef());
        command.put("occurred_at", request.occurredAtOrNow());
        command.put("correlation_id", request.correlationId());
        command.put("actor", request.buildActor());
        command.put("correlation", request.buildCorrelation());
        command.put("payload", request.buildBridgePayload(operation));
        if (operation == MauBridgeOperation.TRIP_UPSERT) {
            command.put("camunda_process_key", request.camundaProcessKey());
        }
        return command;
    }

    private Map<String, Object> parseResponseBody(String responseBody) throws IOException {
        if (responseBody == null || responseBody.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(responseBody, MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    MauConnectorResult classifyBridgeResponse(
            MauBridgeOperation operation,
            OdooRequest request,
            int httpStatus,
            Map<String, Object> responseBody) {
        boolean success = Boolean.TRUE.equals(responseBody.get("success"));
        String status = stringValue(responseBody.get("status"));
        String idempotencyStatus = stringValue(responseBody.get("idempotency_status"));
        Map<String, Object> responseCorrelation = responseBody.get("correlation") instanceof Map
                ? (Map<String, Object>) responseBody.get("correlation")
                : request.buildCorrelation();
        Map<String, Object> result = responseBody.get("result") instanceof Map
                ? (Map<String, Object>) responseBody.get("result")
                : Map.of();
        List<Map<String, Object>> errors = responseBody.get("errors") instanceof List
                ? (List<Map<String, Object>>) responseBody.get("errors")
                : List.of();

        if (success) {
            return MauConnectorResult.bridgeSuccess(
                    operation.operation(),
                    operation.endpointPath(),
                    operation.eventType(),
                    stringValue(responseBody.getOrDefault("external_ref", request.externalRef())),
                    responseCorrelation,
                    httpStatus,
                    status,
                    idempotencyStatus,
                    result);
        }

        String errorClass = extractErrorClass(errors, status);
        return MauConnectorResult.bridgeFailure(
                operation.operation(),
                operation.endpointPath(),
                operation.eventType(),
                stringValue(responseBody.getOrDefault("external_ref", request.externalRef())),
                responseCorrelation,
                httpStatus,
                errorClass,
                extractErrorMessage(errors, responseBody),
                isRetryable(errorClass),
                requiresReconciliation(errorClass),
                status,
                idempotencyStatus,
                result,
                errors);
    }

    private String extractErrorClass(List<Map<String, Object>> errors, String fallbackStatus) {
        if (!errors.isEmpty()) {
            Object candidate = errors.get(0).get("error_class");
            if (candidate instanceof String errorClass && !errorClass.isBlank()) {
                return errorClass;
            }
        }
        return fallbackStatus == null || fallbackStatus.isBlank() ? "internal_error" : fallbackStatus;
    }

    private String extractErrorMessage(List<Map<String, Object>> errors, Map<String, Object> responseBody) {
        if (!errors.isEmpty()) {
            Object candidate = errors.get(0).get("message");
            if (candidate instanceof String message && !message.isBlank()) {
                return message;
            }
        }
        Object resultMessage = responseBody.get("message");
        if (resultMessage instanceof String message && !message.isBlank()) {
            return message;
        }
        return "Downstream bridge call failed.";
    }

    private boolean isRetryable(String errorClass) {
        return "downstream_unavailable".equals(errorClass)
                || "timeout".equals(errorClass)
                || "internal_error".equals(errorClass);
    }

    private boolean requiresReconciliation(String errorClass) {
        return "timeout".equals(errorClass);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Override
    public void close() {
        LOG.debug("MauBridgeClient closed at {}", Instant.now());
    }
}
