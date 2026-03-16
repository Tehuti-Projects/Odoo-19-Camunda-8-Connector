package io.camunda.connector.odoo.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.odoo.MauBridgeOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OdooRequest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Integer>> INTEGER_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> OBJECT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    @Valid
    @NotNull
    private OdooAuthentication authentication;

    @NotEmpty
    private String operation;

    private String model;
    private Integer recordId;
    private List<Integer> recordIds;
    private List<String> fields;
    private List<Object> domain;
    private Integer limit;
    private Integer offset;
    private String order;
    private String directAccessPurpose;

    private Map<String, Object> payload;
    private String actorType;
    private String externalSubject;
    private String externalRef;
    private String correlationId;
    private String occurredAt;
    private String camundaBusinessKey;
    private String camundaProcessKey;
    private String camundaProcessInstanceKey;
    private String referenceCode;
    private String componentExternalRef;
    private String externalOfferRef;

    public OdooRequest() {
    }

    public OdooAuthentication authentication() {
        return authentication;
    }

    public String operation() {
        return operation;
    }

    public String normalizedOperation() {
        return operation == null ? null : operation.trim().toUpperCase(Locale.ROOT);
    }

    public String model() {
        return model;
    }

    public Integer recordId() {
        return recordId;
    }

    public List<Integer> recordIds() {
        return recordIds;
    }

    public List<String> fields() {
        return fields;
    }

    public List<Object> domain() {
        return domain;
    }

    public Integer limit() {
        return limit;
    }

    public Integer offset() {
        return offset;
    }

    public String order() {
        return order;
    }

    public String directAccessPurpose() {
        return directAccessPurpose;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public String actorType() {
        return actorType;
    }

    public String externalSubject() {
        return externalSubject;
    }

    public String externalRef() {
        return externalRef;
    }

    public String correlationId() {
        return correlationId;
    }

    public String occurredAt() {
        return occurredAt;
    }

    public String camundaBusinessKey() {
        return camundaBusinessKey;
    }

    public String camundaProcessKey() {
        return camundaProcessKey;
    }

    public String camundaProcessInstanceKey() {
        return camundaProcessInstanceKey;
    }

    public String referenceCode() {
        return referenceCode;
    }

    public String componentExternalRef() {
        return componentExternalRef;
    }

    public String externalOfferRef() {
        return externalOfferRef;
    }

    public void setAuthentication(OdooAuthentication authentication) {
        this.authentication = authentication;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setRecordId(Integer recordId) {
        this.recordId = recordId;
    }

    @JsonSetter("recordIds")
    public void setRecordIds(Object recordIdsInput) {
        this.recordIds = parseList(recordIdsInput, INTEGER_LIST, "recordIds");
    }

    @JsonSetter("fields")
    public void setFields(Object fieldsInput) {
        this.fields = parseList(fieldsInput, STRING_LIST, "fields");
    }

    @JsonSetter("domain")
    public void setDomain(Object domainInput) {
        this.domain = parseList(domainInput, OBJECT_LIST, "domain");
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    @JsonSetter("direct_access_purpose")
    public void setDirectAccessPurpose(String directAccessPurpose) {
        this.directAccessPurpose = directAccessPurpose;
    }

    @JsonSetter("payload")
    public void setPayload(Object payloadInput) {
        if (payloadInput == null) {
            this.payload = null;
            return;
        }
        if (payloadInput instanceof Map<?, ?> payloadMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) payloadMap;
            this.payload = casted;
            return;
        }
        if (payloadInput instanceof String payloadString) {
            String trimmed = payloadString.trim();
            if (trimmed.isEmpty()) {
                this.payload = null;
                return;
            }
            try {
                this.payload = MAPPER.readValue(trimmed, OBJECT_MAP);
                return;
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Invalid payload JSON object", exception);
            }
        }
        throw new IllegalArgumentException("payload must be a JSON object");
    }

    @JsonSetter("actor_type")
    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    @JsonSetter("external_subject")
    public void setExternalSubject(String externalSubject) {
        this.externalSubject = externalSubject;
    }

    @JsonSetter("external_ref")
    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    @JsonSetter("correlation_id")
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @JsonSetter("occurred_at")
    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    @JsonSetter("camunda_business_key")
    public void setCamundaBusinessKey(String camundaBusinessKey) {
        this.camundaBusinessKey = camundaBusinessKey;
    }

    @JsonSetter("camunda_process_key")
    public void setCamundaProcessKey(String camundaProcessKey) {
        this.camundaProcessKey = camundaProcessKey;
    }

    @JsonSetter("camunda_process_instance_key")
    public void setCamundaProcessInstanceKey(String camundaProcessInstanceKey) {
        this.camundaProcessInstanceKey = camundaProcessInstanceKey;
    }

    @JsonSetter("reference_code")
    public void setReferenceCode(String referenceCode) {
        this.referenceCode = referenceCode;
    }

    @JsonSetter("component_external_ref")
    public void setComponentExternalRef(String componentExternalRef) {
        this.componentExternalRef = componentExternalRef;
    }

    @JsonSetter("external_offer_ref")
    public void setExternalOfferRef(String externalOfferRef) {
        this.externalOfferRef = externalOfferRef;
    }

    public List<Integer> getEffectiveRecordIds() {
        if (recordIds != null && !recordIds.isEmpty()) {
            return recordIds;
        }
        if (recordId != null) {
            return List.of(recordId);
        }
        return List.of();
    }

    public String occurredAtOrNow() {
        return occurredAt != null && !occurredAt.isBlank()
                ? occurredAt
                : Instant.now().toString();
    }

    public Map<String, Object> buildActor() {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("actor_type", actorType != null && !actorType.isBlank() ? actorType : "system");
        actor.put("external_subject", externalSubject != null && !externalSubject.isBlank() ? externalSubject : null);
        return actor;
    }

    public Map<String, Object> buildCorrelation() {
        Map<String, Object> correlation = new LinkedHashMap<>();
        putIfNotBlank(correlation, "camunda_business_key", camundaBusinessKey);
        putIfNotBlank(correlation, "camunda_process_instance_key", camundaProcessInstanceKey);
        putIfNotBlank(correlation, "reference_code", referenceCode);
        putIfNotBlank(correlation, "component_external_ref", componentExternalRef);
        putIfNotBlank(correlation, "external_offer_ref", externalOfferRef);
        return correlation;
    }

    public Map<String, Object> buildBridgePayload(MauBridgeOperation operation) {
        Map<String, Object> normalizedPayload = new LinkedHashMap<>();
        if (payload != null) {
            normalizedPayload.putAll(payload);
        }
        switch (operation) {
            case TRIP_UPSERT -> putIfNotBlank(normalizedPayload, "reference_code", referenceCode);
            case VENDOR_OFFER_UPSERT, VENDOR_OFFER_STATUS -> {
                putIfNotBlank(normalizedPayload, "reference_code", referenceCode);
                putIfNotBlank(normalizedPayload, "component_external_ref", componentExternalRef);
                putIfNotBlank(normalizedPayload, "external_offer_ref", externalOfferRef);
            }
            case READINESS_UPDATE, TRIP_FINAL_PACKET -> putIfNotBlank(normalizedPayload, "reference_code",
                    referenceCode);
            default -> {
            }
        }
        return normalizedPayload;
    }

    public boolean isBridgeOperation() {
        return MauBridgeOperation.fromOperation(normalizedOperation()).isPresent();
    }

    public boolean isDirectReadOnlyOperation() {
        return switch (normalizedOperation()) {
            case "READ", "SEARCH", "SEARCH_READ", "SEARCH_COUNT" -> true;
            default -> false;
        };
    }

    public void validate() {
        if (authentication == null) {
            throw new IllegalArgumentException("authentication is required");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }

        MauBridgeOperation bridgeOperation = MauBridgeOperation.fromOperation(normalizedOperation()).orElse(null);
        if (bridgeOperation != null) {
            validateBridgeOperation(bridgeOperation);
            return;
        }

        validateDirectReadOperation();
    }

    private void validateBridgeOperation(MauBridgeOperation bridgeOperation) {
        authentication.validateForBridge();
        requireNonBlank(externalRef, "external_ref");
        if (actorType != null && !actorType.isBlank() && !"system".equals(actorType)
                && (externalSubject == null || externalSubject.isBlank())) {
            throw new IllegalArgumentException("external_subject is required for non-system actors");
        }
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("payload is required for bridge commands");
        }
        switch (bridgeOperation) {
            case TRIP_UPSERT -> {
                requireNonBlank(camundaBusinessKey, "camunda_business_key");
                requireNonBlank(camundaProcessKey, "camunda_process_key");
                requireNonBlank(camundaProcessInstanceKey, "camunda_process_instance_key");
            }
            case VENDOR_OFFER_UPSERT, VENDOR_OFFER_STATUS -> {
                requireNonBlank(referenceCode, "reference_code");
                requireNonBlank(componentExternalRef, "component_external_ref");
                requireNonBlank(externalOfferRef, "external_offer_ref");
            }
            case READINESS_UPDATE, TRIP_FINAL_PACKET -> requireNonBlank(referenceCode, "reference_code");
            default -> {
            }
        }
    }

    private void validateDirectReadOperation() {
        authentication.validateForDirectRead();
        requireNonBlank(directAccessPurpose, "direct_access_purpose");
        requireNonBlank(model, "model");

        switch (normalizedOperation()) {
            case "READ" -> {
                if (getEffectiveRecordIds().isEmpty()) {
                    throw new IllegalArgumentException("READ requires record IDs");
                }
            }
            case "SEARCH", "SEARCH_READ", "SEARCH_COUNT" -> {
                // domain is optional
            }
            case "CREATE", "UPDATE", "DELETE", "CALL_METHOD" -> throw new IllegalArgumentException(
                    "MAU forbids direct Odoo mutations through the generic connector. Use approved bridge operations.");
            default -> throw new IllegalArgumentException(
                    "Unsupported operation. Use MAU bridge operations or direct read-only operations.");
        }
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private <T> List<T> parseList(Object input, TypeReference<List<T>> type, String fieldName) {
        if (input == null) {
            return null;
        }
        if (input instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) list;
            return casted;
        }
        if (input instanceof String inputString) {
            String trimmed = inputString.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return MAPPER.readValue(trimmed, type);
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Invalid " + fieldName + " JSON", exception);
            }
        }
        throw new IllegalArgumentException(fieldName + " must be a JSON array");
    }
}
