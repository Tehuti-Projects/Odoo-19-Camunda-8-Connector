package io.camunda.connector.odoo.inbound;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MauOdooEventEnvelopeTest {

    @Test
    void acceptsApprovedEnvelopeAndMapsCanonicalVariables() {
        MauOdooEventEnvelope envelope = assertDoesNotThrow(() -> MauOdooEventEnvelope.fromPayload(Map.of(
                "event_name", "odoo.deposit_paid.v1",
                "event_type", "odoo.deposit_paid.v1",
                "source_system", "odoo",
                "external_ref", "evt-odoo-001",
                "occurred_at", "2026-03-16T10:15:30Z",
                "correlation_id", "corr-odoo-001",
                "actor", Map.of("actor_type", "system"),
                "correlation", Map.of(
                        "camunda_business_key", "BK-300",
                        "camunda_process_instance_key", "PI-300",
                        "reference_code", "REF-300"),
                "payload", Map.of("deposit_status", "paid"))));

        Map<String, Object> variables = envelope.toVariables();
        assertEquals("evt-odoo-001", variables.get("external_ref"));
        assertEquals("BK-300", variables.get("camunda_business_key"));
        assertEquals("PI-300", variables.get("camunda_process_instance_key"));
        assertEquals("REF-300", variables.get("reference_code"));
        assertTrue(variables.containsKey("payload"));
    }

    @Test
    void rejectsMissingPayloadObject() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MauOdooEventEnvelope.fromPayload(Map.of(
                        "event_name", "odoo.deposit_paid.v1",
                        "event_type", "odoo.deposit_paid.v1",
                        "source_system", "odoo",
                        "external_ref", "evt-odoo-002")));

        assertTrue(exception.getMessage().contains("payload must be a JSON object"));
    }

    @Test
    void rejectsWrongSourceSystem() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MauOdooEventEnvelope.fromPayload(Map.of(
                        "event_name", "odoo.deposit_paid.v1",
                        "event_type", "odoo.deposit_paid.v1",
                        "source_system", "appwrite",
                        "external_ref", "evt-odoo-003",
                        "payload", Map.of())));

        assertTrue(exception.getMessage().contains("source_system must be odoo"));
    }

    @Test
    void rejectsUnapprovedEventType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MauOdooEventEnvelope.fromPayload(Map.of(
                        "event_name", "odoo.unknown_fact.v1",
                        "event_type", "odoo.unknown_fact.v1",
                        "source_system", "odoo",
                        "external_ref", "evt-odoo-004",
                        "payload", Map.of())));

        assertTrue(exception.getMessage().contains("approved MAU Odoo outbound event set"));
    }
}
