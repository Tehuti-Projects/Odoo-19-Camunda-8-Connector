package io.camunda.connector.odoo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.odoo.dto.OdooAuthentication;
import io.camunda.connector.odoo.dto.OdooRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OdooRequestTest {

    @Test
    void rejectsDirectMutationOperations() {
        OdooRequest request = new OdooRequest();
        request.setAuthentication(new OdooAuthentication("https://odoo.example.com", "mau", "json2-key", "bridge-key"));
        request.setOperation("CALL_METHOD");
        request.setModel("res.partner");
        request.setDirectAccessPurpose("diagnostic_read");

        assertThatThrownBy(request::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAU forbids direct Odoo mutations");
    }

    @Test
    void tripUpsertRequiresCanonicalBridgeFields() {
        OdooRequest request = new OdooRequest();
        request.setAuthentication(new OdooAuthentication("https://odoo.example.com", null, null, "bridge-key"));
        request.setOperation("TRIP_UPSERT");
        request.setPayload(Map.of("workflow_state", "trip_draft"));
        request.setExternalRef("evt-trip-001");

        assertThatThrownBy(request::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("camunda_business_key is required");
    }

    @Test
    void bridgeCommandsRequireExternalSubjectForNonSystemActors() {
        OdooRequest request = validTripUpsertRequest();
        request.setActorType("tourist");
        request.setExternalSubject(null);

        assertThatThrownBy(request::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("external_subject is required");
    }

    @Test
    void directReadRequiresExplicitPurpose() {
        OdooRequest request = new OdooRequest();
        request.setAuthentication(new OdooAuthentication("https://odoo.example.com", "mau", "json2-key", null));
        request.setOperation("SEARCH_READ");
        request.setModel("res.partner");

        assertThatThrownBy(request::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direct_access_purpose is required");
    }

    @Test
    void validTripUpsertPreservesCanonicalCorrelationAndPayload() {
        OdooRequest request = validTripUpsertRequest();

        assertThatCode(request::validate).doesNotThrowAnyException();
        assertThat(request.buildCorrelation())
                .containsEntry("camunda_business_key", "BK-100")
                .containsEntry("camunda_process_instance_key", "PI-100")
                .containsEntry("reference_code", "REF-100");
        assertThat(request.buildActor())
                .containsEntry("actor_type", "system")
                .containsKey("external_subject");
        assertThat(request.buildActor().get("external_subject")).isNull();
        assertThat(request.buildBridgePayload(MauBridgeOperation.TRIP_UPSERT))
                .containsEntry("workflow_state", "trip_draft")
                .containsEntry("reference_code", "REF-100");
    }

    private OdooRequest validTripUpsertRequest() {
        OdooRequest request = new OdooRequest();
        request.setAuthentication(new OdooAuthentication("https://odoo.example.com", null, null, "bridge-key"));
        request.setOperation("TRIP_UPSERT");
        request.setPayload(Map.of("workflow_state", "trip_draft"));
        request.setExternalRef("evt-trip-001");
        request.setActorType("system");
        request.setCorrelationId("corr-001");
        request.setCamundaBusinessKey("BK-100");
        request.setCamundaProcessKey("tailored_trip_generation");
        request.setCamundaProcessInstanceKey("PI-100");
        request.setReferenceCode("REF-100");
        return request;
    }
}
