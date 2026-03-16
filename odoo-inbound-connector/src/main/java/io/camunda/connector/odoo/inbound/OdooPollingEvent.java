package io.camunda.connector.odoo.inbound;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event data from Odoo polling.
 * Contains the record data and metadata about the change.
 */
public record OdooPollingEvent(
        /** The Odoo model name */
        String model,

        /** Record ID */
        Integer recordId,

        /** Event type: "create" or "write" */
        String eventType,

        /** The complete record data */
        Map<String, Object> record,

        /** Timestamp when the record was last modified */
        Instant timestamp,

        /** Fields that were requested */
        List<String> fields) {
    /**
     * Create an event from a record map.
     */
    public static OdooPollingEvent fromRecord(String model, Map<String, Object> record,
            String eventType, String timestampField) {
        Integer id = record.get("id") != null
                ? ((Number) record.get("id")).intValue()
                : null;

        Instant timestamp = Instant.now();
        Object tsValue = record.get(timestampField);
        if (tsValue instanceof String) {
            try {
                // Odoo datetime format: "2024-01-15 10:30:00"
                String odooDate = (String) tsValue;
                if (odooDate.contains(" ")) {
                    odooDate = odooDate.replace(" ", "T") + "Z";
                }
                timestamp = Instant.parse(odooDate);
            } catch (Exception e) {
                // Use current time if parsing fails
            }
        }

        List<String> fields = record.keySet().stream().toList();

        return new OdooPollingEvent(model, id, eventType, record, timestamp, fields);
    }

    /**
     * Convert to a map for correlation.
     */
    public Map<String, Object> toVariables() {
        return Map.of(
                "odooModel", model,
                "odooRecordId", recordId != null ? recordId : 0,
                "odooEventType", eventType,
                "odooRecord", record,
                "odooTimestamp", timestamp.toString(),
                "odooFields", fields);
    }
}
