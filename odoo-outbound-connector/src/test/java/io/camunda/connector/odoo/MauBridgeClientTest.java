package io.camunda.connector.odoo;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.odoo.dto.MauConnectorResult;
import io.camunda.connector.odoo.dto.OdooAuthentication;
import io.camunda.connector.odoo.dto.OdooRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MauBridgeClientTest {

    @Test
    void buildCommandUsesFrozenEnvelope() {
        OdooRequest request = validTripUpsertRequest();

        try (MauBridgeClient client = new MauBridgeClient(new OdooAuthentication(
                "https://odoo.example.com", null, null, "bridge-key"))) {
            Map<String, Object> command = client.buildCommand(MauBridgeOperation.TRIP_UPSERT, request);
            @SuppressWarnings("unchecked")
            Map<String, Object> actor = (Map<String, Object>) command.get("actor");

            assertThat(command)
                    .containsEntry("event_name", "camunda.trip_upsert.requested.v1")
                    .containsEntry("event_type", "camunda.trip_upsert.requested.v1")
                    .containsEntry("source_system", "camunda")
                    .containsEntry("external_ref", "evt-trip-200")
                    .containsEntry("camunda_process_key", "tailored_trip_generation");
            assertThat(actor)
                    .containsEntry("actor_type", "system")
                    .containsKey("external_subject");
            assertThat(actor.get("external_subject")).isNull();
            assertThat(command.get("correlation")).isEqualTo(Map.of(
                    "camunda_business_key", "BK-200",
                    "camunda_process_instance_key", "PI-200",
                    "reference_code", "REF-200"));
        }
    }

    @Test
    void classifyBridgeResponseTreatsDuplicateAsSuccessNoOp() {
        OdooRequest request = validTripUpsertRequest();

        try (MauBridgeClient client = new MauBridgeClient(new OdooAuthentication(
                "https://odoo.example.com", null, null, "bridge-key"))) {
            MauConnectorResult result = client.classifyBridgeResponse(
                    MauBridgeOperation.TRIP_UPSERT,
                    request,
                    200,
                    Map.of(
                            "success", true,
                            "status", "accepted",
                            "idempotency_status", "duplicate_noop",
                            "external_ref", "evt-trip-200",
                            "correlation", Map.of("camunda_business_key", "BK-200"),
                            "result", Map.of("event_log_id", 44)));

            assertThat(result.success()).isTrue();
            assertThat(result.status()).isEqualTo("accepted");
            assertThat(result.result()).containsEntry("idempotency_status", "duplicate_noop");
            assertThat(result.retryable()).isFalse();
        }
    }

    @Test
    void classifyBridgeResponseMarksTimeoutAsRetryableWithReconciliation() {
        OdooRequest request = validTripUpsertRequest();

        try (MauBridgeClient client = new MauBridgeClient(new OdooAuthentication(
                "https://odoo.example.com", null, null, "bridge-key"))) {
            MauConnectorResult result = client.classifyBridgeResponse(
                    MauBridgeOperation.TRIP_UPSERT,
                    request,
                    504,
                    Map.of(
                            "success", false,
                            "status", "timeout",
                            "errors", List.of(Map.of(
                                    "error_class", "timeout",
                                    "message", "Bridge timeout"))));

            assertThat(result.success()).isFalse();
            assertThat(result.errorClass()).isEqualTo("timeout");
            assertThat(result.retryable()).isTrue();
            assertThat(result.requiresReconciliation()).isTrue();
        }
    }

    @Test
    void classifyBridgeResponseMarksConflictAsTerminal() {
        OdooRequest request = validTripUpsertRequest();

        try (MauBridgeClient client = new MauBridgeClient(new OdooAuthentication(
                "https://odoo.example.com", null, null, "bridge-key"))) {
            MauConnectorResult result = client.classifyBridgeResponse(
                    MauBridgeOperation.TRIP_UPSERT,
                    request,
                    409,
                    Map.of(
                            "success", false,
                            "status", "conflict",
                            "errors", List.of(Map.of(
                                    "error_class", "conflict",
                                    "message", "Payload mismatch"))));

            assertThat(result.success()).isFalse();
            assertThat(result.errorClass()).isEqualTo("conflict");
            assertThat(result.retryable()).isFalse();
            assertThat(result.requiresReconciliation()).isFalse();
        }
    }

    private OdooRequest validTripUpsertRequest() {
        OdooRequest request = new OdooRequest();
        request.setAuthentication(new OdooAuthentication("https://odoo.example.com", null, null, "bridge-key"));
        request.setOperation("TRIP_UPSERT");
        request.setPayload(Map.of("workflow_state", "trip_draft"));
        request.setExternalRef("evt-trip-200");
        request.setActorType("system");
        request.setCorrelationId("corr-200");
        request.setCamundaBusinessKey("BK-200");
        request.setCamundaProcessKey("tailored_trip_generation");
        request.setCamundaProcessInstanceKey("PI-200");
        request.setReferenceCode("REF-200");
        return request;
    }
}
