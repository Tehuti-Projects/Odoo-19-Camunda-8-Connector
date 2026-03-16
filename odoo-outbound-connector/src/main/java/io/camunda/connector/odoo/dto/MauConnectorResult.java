package io.camunda.connector.odoo.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MauConnectorResult(
        String mode,
        String operation,
        boolean success,
        String status,
        String endpoint,
        String eventType,
        String externalRef,
        Map<String, Object> correlation,
        Map<String, Object> result,
        List<Map<String, Object>> errors,
        String errorClass,
        boolean retryable,
        boolean requiresReconciliation,
        Integer httpStatus) {

    public static MauConnectorResult bridgeSuccess(
            String operation,
            String endpoint,
            String eventType,
            String externalRef,
            Map<String, Object> correlation,
            Integer httpStatus,
            String status,
            String idempotencyStatus,
            Map<String, Object> result) {
        Map<String, Object> normalizedResult = new LinkedHashMap<>(result != null ? result : Map.of());
        if (idempotencyStatus != null) {
            normalizedResult.put("idempotency_status", idempotencyStatus);
        }
        return new MauConnectorResult(
                "bridge_command",
                operation,
                true,
                status != null ? status : "accepted",
                endpoint,
                eventType,
                externalRef,
                correlation != null ? correlation : Map.of(),
                normalizedResult,
                List.of(),
                null,
                false,
                false,
                httpStatus);
    }

    public static MauConnectorResult bridgeFailure(
            String operation,
            String endpoint,
            String eventType,
            String externalRef,
            Map<String, Object> correlation,
            Integer httpStatus,
            String errorClass,
            String message,
            boolean retryable,
            boolean requiresReconciliation) {
        return bridgeFailure(
                operation,
                endpoint,
                eventType,
                externalRef,
                correlation,
                httpStatus,
                errorClass,
                message,
                retryable,
                requiresReconciliation,
                errorClass,
                null,
                Map.of(),
                List.of());
    }

    public static MauConnectorResult bridgeFailure(
            String operation,
            String endpoint,
            String eventType,
            String externalRef,
            Map<String, Object> correlation,
            Integer httpStatus,
            String errorClass,
            String message,
            boolean retryable,
            boolean requiresReconciliation,
            String status,
            String idempotencyStatus,
            Map<String, Object> result,
            List<Map<String, Object>> errors) {
        List<Map<String, Object>> normalizedErrors = new ArrayList<>();
        if (errors != null && !errors.isEmpty()) {
            normalizedErrors.addAll(errors);
        } else {
            normalizedErrors.add(Map.of(
                    "error_class", errorClass,
                    "message", message,
                    "retryable", retryable));
        }
        Map<String, Object> normalizedResult = new LinkedHashMap<>(result != null ? result : Map.of());
        if (idempotencyStatus != null) {
            normalizedResult.put("idempotency_status", idempotencyStatus);
        }
        return new MauConnectorResult(
                "bridge_command",
                operation,
                false,
                status != null ? status : errorClass,
                endpoint,
                eventType,
                externalRef,
                correlation != null ? correlation : Map.of(),
                normalizedResult,
                normalizedErrors,
                errorClass,
                retryable,
                requiresReconciliation,
                httpStatus);
    }

    public static MauConnectorResult directReadSuccess(
            String operation,
            String endpoint,
            Map<String, Object> result) {
        return new MauConnectorResult(
                "direct_read_only",
                operation,
                true,
                "accepted",
                endpoint,
                null,
                null,
                Map.of(),
                result != null ? result : Map.of(),
                List.of(),
                null,
                false,
                false,
                200);
    }

    public static MauConnectorResult directReadFailure(
            String operation,
            String endpoint,
            Integer httpStatus,
            String errorClass,
            String message,
            boolean retryable) {
        return new MauConnectorResult(
                "direct_read_only",
                operation,
                false,
                errorClass,
                endpoint,
                null,
                null,
                Map.of(),
                Map.of(),
                List.of(Map.of(
                        "error_class", errorClass,
                        "message", message,
                        "retryable", retryable)),
                errorClass,
                retryable,
                false,
                httpStatus);
    }
}
