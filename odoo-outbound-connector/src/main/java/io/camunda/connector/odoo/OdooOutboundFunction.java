package io.camunda.connector.odoo;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.odoo.OdooApiClient.OdooApiException;
import io.camunda.connector.odoo.dto.MauConnectorResult;
import io.camunda.connector.odoo.dto.OdooRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(name = "Odoo 19", inputVariables = {
        "authentication",
        "operation",
        "payload",
        "actor_type",
        "external_subject",
        "external_ref",
        "correlation_id",
        "occurred_at",
        "camunda_business_key",
        "camunda_process_key",
        "camunda_process_instance_key",
        "reference_code",
        "component_external_ref",
        "external_offer_ref",
        "model",
        "recordId",
        "recordIds",
        "fields",
        "domain",
        "limit",
        "offset",
        "order",
        "direct_access_purpose"
}, type = "io.camunda:odoo-outbound:1")
public class OdooOutboundFunction implements OutboundConnectorFunction {

    private static final Logger LOG = LoggerFactory.getLogger(OdooOutboundFunction.class);

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
        OdooRequest request = context.bindVariables(OdooRequest.class);

        try {
            request.validate();
        } catch (IllegalArgumentException exception) {
            throw new ConnectorException("validation_error", exception.getMessage(), exception);
        }

        MauBridgeOperation bridgeOperation = MauBridgeOperation.fromOperation(request.normalizedOperation()).orElse(null);
        if (bridgeOperation != null) {
            try (MauBridgeClient bridgeClient = new MauBridgeClient(request.authentication())) {
                return bridgeClient.execute(bridgeOperation, request);
            }
        }

        try (OdooApiClient client = new OdooApiClient(request.authentication())) {
            return executeDirectRead(client, request);
        } catch (OdooApiException exception) {
            LOG.error(
                    "connector=mautourism-odoo mode=direct_read_only operation={} model={} error={}",
                    request.normalizedOperation(),
                    request.model(),
                    exception.getMessage());
            return mapDirectReadError(exception, request);
        }
    }

    private MauConnectorResult executeDirectRead(OdooApiClient client, OdooRequest request)
            throws OdooApiException {
        String model = request.model();
        String operation = request.normalizedOperation();

        LOG.info(
                "connector=mautourism-odoo mode=direct_read_only operation={} model={} purpose={}",
                operation,
                model,
                request.directAccessPurpose());

        return switch (operation) {
            case "READ" -> {
                List<Map<String, Object>> records = client.read(model, request.getEffectiveRecordIds(), request.fields());
                yield MauConnectorResult.directReadSuccess(operation, endpointForRead(model, "read"), Map.of(
                        "model", model,
                        "records", records,
                        "direct_access_purpose", request.directAccessPurpose()));
            }
            case "SEARCH" -> {
                List<Integer> ids = client.search(model, request.domain(), request.limit(), request.offset());
                yield MauConnectorResult.directReadSuccess(operation, endpointForRead(model, "search"), Map.of(
                        "model", model,
                        "search_ids", ids,
                        "direct_access_purpose", request.directAccessPurpose()));
            }
            case "SEARCH_READ" -> {
                List<Map<String, Object>> records = client.searchRead(
                        model, request.domain(), request.fields(), request.limit(), request.offset());
                yield MauConnectorResult.directReadSuccess(operation, endpointForRead(model, "search_read"), Map.of(
                        "model", model,
                        "records", records,
                        "direct_access_purpose", request.directAccessPurpose()));
            }
            case "SEARCH_COUNT" -> {
                Integer count = client.searchCount(model, request.domain());
                yield MauConnectorResult.directReadSuccess(operation, endpointForRead(model, "search_count"), Map.of(
                        "model", model,
                        "count", count,
                        "direct_access_purpose", request.directAccessPurpose()));
            }
            default -> throw new ConnectorException(
                    "validation_error",
                    "Unsupported direct read-only operation: " + operation);
        };
    }

    private MauConnectorResult mapDirectReadError(OdooApiException exception, OdooRequest request) {
        int statusCode = exception.getStatusCode();
        String errorClass = classifyDirectReadError(exception);
        boolean retryable = "downstream_unavailable".equals(errorClass)
                || "timeout".equals(errorClass)
                || "internal_error".equals(errorClass);
        return MauConnectorResult.directReadFailure(
                request.normalizedOperation(),
                endpointForRead(request.model(), request.normalizedOperation().toLowerCase()),
                statusCode > 0 ? statusCode : impliedStatus(errorClass),
                errorClass,
                exception.getMessage(),
                retryable);
    }

    private String classifyDirectReadError(OdooApiException exception) {
        if (exception.isAuthenticationError()) {
            return "unauthorized";
        }
        if (exception.isPermissionError()) {
            return "forbidden";
        }
        if (exception.isNotFoundError()) {
            return "not_found";
        }
        if (exception.isValidationError()) {
            return "validation_error";
        }
        String message = exception.getMessage();
        if (message != null && message.startsWith("Network error")) {
            return "downstream_unavailable";
        }
        if (message != null && message.toLowerCase().contains("timeout")) {
            return "timeout";
        }
        return "internal_error";
    }

    private int impliedStatus(String errorClass) {
        return switch (errorClass) {
            case "validation_error" -> 400;
            case "unauthorized" -> 401;
            case "forbidden" -> 403;
            case "not_found" -> 404;
            case "downstream_unavailable" -> 503;
            case "timeout" -> 504;
            default -> 500;
        };
    }

    private String endpointForRead(String model, String method) {
        return "/json/2/" + model + "/" + method;
    }
}
