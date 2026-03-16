package io.camunda.connector.odoo.inbound;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record MauOdooEventEnvelope(
        String eventName,
        String eventType,
        String sourceSystem,
        String externalRef,
        String occurredAt,
        String correlationId,
        Map<String, Object> actor,
        Map<String, Object> correlation,
        Map<String, Object> payload,
        Map<String, Object> rawPayload) {

    public static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
            "odoo.commercial_approval.recorded.v1",
            "odoo.deposit_paid.v1",
            "odoo.vendor_publication.updated.v1",
            "odoo.readiness_exception.resolved.v1",
            "odoo.support_ticket.opened.v1",
            "odoo.final_packet.generated.v1");

    @SuppressWarnings("unchecked")
    public static MauOdooEventEnvelope fromPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Webhook payload is required");
        }
        String eventType = stringValue(payload.get("event_type"));
        String eventName = stringValue(payload.get("event_name"));
        String sourceSystem = stringValue(payload.get("source_system"));
        String externalRef = stringValue(payload.get("external_ref"));
        if (eventType == null || eventName == null || externalRef == null || sourceSystem == null) {
            throw new IllegalArgumentException(
                    "MAU Odoo webhook payload must use the frozen event envelope with event_name, event_type, source_system, and external_ref.");
        }
        if (!eventType.equals(eventName)) {
            throw new IllegalArgumentException("event_name must equal event_type");
        }
        if (!"odoo".equals(sourceSystem)) {
            throw new IllegalArgumentException("source_system must be odoo");
        }
        if (!ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("event_type is not in the approved MAU Odoo outbound event set");
        }
        Map<String, Object> actor = payload.get("actor") instanceof Map<?, ?> actorMap
                ? (Map<String, Object>) actorMap
                : Map.of();
        Map<String, Object> correlation = payload.get("correlation") instanceof Map<?, ?> correlationMap
                ? (Map<String, Object>) correlationMap
                : Map.of();
        if (!(payload.get("payload") instanceof Map<?, ?> eventPayloadMap)) {
            throw new IllegalArgumentException("payload must be a JSON object in the frozen MAU event envelope");
        }
        Map<String, Object> eventPayload = (Map<String, Object>) eventPayloadMap;

        return new MauOdooEventEnvelope(
                eventName,
                eventType,
                sourceSystem,
                externalRef,
                stringValue(payload.get("occurred_at")),
                stringValue(payload.get("correlation_id")),
                actor,
                correlation,
                eventPayload,
                payload);
    }

    public Map<String, Object> toVariables() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("source_system", sourceSystem);
        variables.put("event_name", eventName);
        variables.put("event_type", eventType);
        variables.put("source_event_name", eventName);
        variables.put("external_ref", externalRef);
        variables.put("occurred_at", occurredAt != null ? occurredAt : Instant.now().toString());
        variables.put("correlation_id", correlationId);
        variables.put("actor_type", stringValue(actor.get("actor_type")));
        variables.put("external_subject", stringValue(actor.get("external_subject")));
        variables.put("camunda_business_key", stringValue(correlation.get("camunda_business_key")));
        variables.put("camunda_process_instance_key", stringValue(correlation.get("camunda_process_instance_key")));
        variables.put("reference_code", stringValue(correlation.get("reference_code")));
        variables.put("component_external_ref", stringValue(correlation.get("component_external_ref")));
        variables.put("external_offer_ref", stringValue(correlation.get("external_offer_ref")));
        variables.put("payload", payload);
        variables.put("raw_event", rawPayload);
        return variables;
    }

    private static String stringValue(Object candidate) {
        if (candidate == null) {
            return null;
        }
        String normalized = String.valueOf(candidate).trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
