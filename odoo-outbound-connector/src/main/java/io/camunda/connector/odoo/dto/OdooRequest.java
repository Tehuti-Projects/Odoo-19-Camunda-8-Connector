package io.camunda.connector.odoo.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for Odoo 19 outbound connector operations.
 * 
 * Element template provides the UI configuration.
 * This class handles the runtime binding.
 * 
 * Note: fields and domain are parsed from JSON strings when provided as text
 * input.
 */
public class OdooRequest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Valid
    @NotNull
    private OdooAuthentication authentication;

    @NotEmpty
    private String operation;

    @NotEmpty
    private String model;

    private Integer recordId;
    private List<Integer> recordIds;
    private Map<String, Object> values;
    private List<String> fields;
    private List<Object> domain;
    private Integer limit;
    private Integer offset;
    private String order;
    private String methodName;
    private Map<String, Object> methodArgs;
    private Map<String, Object> context;

    public OdooRequest() {
    }

    // Getters
    public OdooAuthentication authentication() {
        return authentication;
    }

    public String operation() {
        return operation;
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

    public Map<String, Object> values() {
        return values;
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

    public String methodName() {
        return methodName;
    }

    public Map<String, Object> methodArgs() {
        return methodArgs;
    }

    public Map<String, Object> context() {
        return context;
    }

    // Setters with JSON string parsing support
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

    public void setRecordIds(List<Integer> recordIds) {
        this.recordIds = recordIds;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * Set fields - handles both List<String> and JSON string input.
     * Element template sends: "[\"name\", \"email\"]" as a string
     * This method parses it into a proper list.
     */
    @JsonSetter("fields")
    public void setFields(Object fieldsInput) {
        if (fieldsInput == null) {
            this.fields = null;
        } else if (fieldsInput instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> fieldList = (List<String>) fieldsInput;
            this.fields = fieldList;
        } else if (fieldsInput instanceof String) {
            String fieldsStr = ((String) fieldsInput).trim();
            if (fieldsStr.isEmpty()) {
                this.fields = null;
            } else if (fieldsStr.startsWith("[")) {
                try {
                    this.fields = MAPPER.readValue(fieldsStr, new TypeReference<List<String>>() {
                    });
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("Invalid fields JSON: " + fieldsStr, e);
                }
            } else {
                // Single field name
                this.fields = List.of(fieldsStr);
            }
        } else {
            throw new IllegalArgumentException("Fields must be a list or JSON array string");
        }
    }

    /**
     * Set domain - handles both List<Object> and JSON string input.
     * Element template sends: "[[\"active\", \"=\", true]]" as a string
     * This method parses it into a proper list.
     */
    @JsonSetter("domain")
    public void setDomain(Object domainInput) {
        if (domainInput == null) {
            this.domain = null;
        } else if (domainInput instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> domainList = (List<Object>) domainInput;
            this.domain = domainList;
        } else if (domainInput instanceof String) {
            String domainStr = ((String) domainInput).trim();
            if (domainStr.isEmpty()) {
                this.domain = null;
            } else if (domainStr.startsWith("[")) {
                try {
                    this.domain = MAPPER.readValue(domainStr, new TypeReference<List<Object>>() {
                    });
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("Invalid domain JSON: " + domainStr, e);
                }
            } else {
                throw new IllegalArgumentException("Domain must be a JSON array");
            }
        } else {
            throw new IllegalArgumentException("Domain must be a list or JSON array string");
        }
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

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setMethodArgs(Map<String, Object> methodArgs) {
        this.methodArgs = methodArgs;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
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

    public void validate() {
        switch (operation) {
            case "CREATE" -> {
                if (values == null || values.isEmpty()) {
                    throw new IllegalArgumentException("CREATE requires 'values'");
                }
            }
            case "READ" -> {
                if (getEffectiveRecordIds().isEmpty()) {
                    throw new IllegalArgumentException("READ requires record IDs");
                }
            }
            case "UPDATE" -> {
                if (getEffectiveRecordIds().isEmpty() || values == null) {
                    throw new IllegalArgumentException("UPDATE requires IDs and values");
                }
            }
            case "DELETE" -> {
                if (getEffectiveRecordIds().isEmpty()) {
                    throw new IllegalArgumentException("DELETE requires record IDs");
                }
            }
            case "CALL_METHOD" -> {
                if (methodName == null || methodName.isBlank()) {
                    throw new IllegalArgumentException("CALL_METHOD requires methodName");
                }
            }
        }
    }
}
