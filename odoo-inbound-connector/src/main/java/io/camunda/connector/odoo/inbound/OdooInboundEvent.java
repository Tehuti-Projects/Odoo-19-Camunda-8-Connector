package io.camunda.connector.odoo.inbound;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Event data received from Odoo webhook.
 * Contains information about the record change in Odoo.
 */
public record OdooInboundEvent(
        /** The Odoo model name (e.g., res.partner, sale.order) */
        String model,

        /** The type of operation: create, write, or unlink */
        String operation,

        /** The ID(s) of the affected record(s) */
        List<Integer> recordIds,

        /** The user ID who performed the operation in Odoo */
        Integer userId,

        /** The database name */
        String database,

        /** Timestamp of when the event occurred */
        Instant timestamp,

        /** Field values for create/write operations (may be partial for write) */
        Map<String, Object> values,

        /** Changed field names for write operations */
        List<String> changedFields,

        /** The full raw webhook payload for custom processing */
        Map<String, Object> rawPayload) {
    /**
     * Create an event from a webhook payload with improved null safety.
     */
    @SuppressWarnings("unchecked")
    public static OdooInboundEvent fromPayload(Map<String, Object> payload) {
        String model = (String) payload.get("model");
        String operation = (String) payload.get("operation");

        List<Integer> recordIds;
        Object idsObj = payload.get("record_ids");
        if (idsObj instanceof List) {
            recordIds = ((List<?>) idsObj).stream()
                    .map(id -> ((Number) id).intValue())
                    .toList();
        } else if (idsObj instanceof Number) {
            recordIds = List.of(((Number) idsObj).intValue());
        } else {
            recordIds = List.of();
        }

        // Safe extraction using Optional
        Integer userId = Optional.ofNullable(payload.get("user_id"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(null);

        String database = (String) payload.get("database");

        Instant timestamp;
        Object tsObj = payload.get("timestamp");
        if (tsObj instanceof String) {
            timestamp = Instant.parse((String) tsObj);
        } else if (tsObj instanceof Number) {
            timestamp = Instant.ofEpochMilli(((Number) tsObj).longValue());
        } else {
            timestamp = Instant.now();
        }

        Map<String, Object> values = payload.get("values") != null
                ? (Map<String, Object>) payload.get("values")
                : Map.of();

        List<String> changedFields = payload.get("changed_fields") != null
                ? ((List<?>) payload.get("changed_fields")).stream()
                        .map(Object::toString)
                        .toList()
                : List.of();

        return new OdooInboundEvent(
                model, operation, recordIds, userId, database,
                timestamp, values, changedFields, payload);
    }

    /**
     * Get a single record ID (for single-record operations).
     */
    public Integer getRecordId() {
        return recordIds != null && !recordIds.isEmpty() ? recordIds.get(0) : null;
    }
}
