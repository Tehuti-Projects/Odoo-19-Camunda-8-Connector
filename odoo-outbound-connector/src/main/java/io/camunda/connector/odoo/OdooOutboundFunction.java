package io.camunda.connector.odoo;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.odoo.OdooApiClient.OdooApiException;
import io.camunda.connector.odoo.dto.OdooRequest;
import io.camunda.connector.odoo.dto.OdooResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Camunda 8 Outbound Connector for Odoo 19 External JSON-2 API.
 * 
 * <p>
 * Provides bidirectional integration between Camunda workflows and Odoo ERP,
 * supporting CRUD operations and custom method calls on any Odoo model.
 * 
 * <p>
 * Features:
 * <ul>
 * <li>Full CRUD support (Create, Read, Update, Delete)</li>
 * <li>Search and Search-Read operations with domain filters</li>
 * <li>Search-Count for record counting</li>
 * <li>Custom method calls for advanced operations</li>
 * <li>Automatic retry on transient failures</li>
 * <li>Comprehensive error handling with BPMN error codes</li>
 * </ul>
 * 
 * @see <a href=
 *      "https://www.odoo.com/documentation/19.0/developer/reference/external_api.html">Odoo
 *      External JSON-2 API</a>
 */
@OutboundConnector(name = "Odoo 19", inputVariables = {
        "authentication",
        "operation",
        "model",
        "recordId",
        "recordIds",
        "values",
        "fields",
        "domain",
        "limit",
        "offset",
        "order",
        "methodName",
        "methodArgs",
        "context"
}, type = "io.camunda:odoo-outbound:1")
// Note: Element template is handcrafted in
// element-templates/odoo-outbound-connector.json
public class OdooOutboundFunction implements OutboundConnectorFunction {

    private static final Logger LOG = LoggerFactory.getLogger(OdooOutboundFunction.class);

    // BPMN Error Codes for error handling in processes
    public static final String ERROR_AUTHENTICATION = "ODOO_AUTH_ERROR";
    public static final String ERROR_PERMISSION = "ODOO_PERMISSION_ERROR";
    public static final String ERROR_NOT_FOUND = "ODOO_NOT_FOUND";
    public static final String ERROR_VALIDATION = "ODOO_VALIDATION_ERROR";
    public static final String ERROR_CONNECTION = "ODOO_CONNECTION_ERROR";
    public static final String ERROR_UNKNOWN = "ODOO_ERROR";

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
        OdooRequest request = context.bindVariables(OdooRequest.class);

        LOG.info("Executing Odoo operation: {} on model {}", request.operation(), request.model());

        // Validate the request
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            throw new ConnectorException(ERROR_VALIDATION, "Invalid request: " + e.getMessage(), e);
        }

        // Execute the operation
        try (OdooApiClient client = new OdooApiClient(request.authentication())) {
            return executeOperation(client, request);
        } catch (OdooApiException e) {
            LOG.error("Odoo API error: {}", e.getMessage());
            return handleApiException(e, request);
        }
    }

    private OdooResult executeOperation(OdooApiClient client, OdooRequest request)
            throws OdooApiException {
        String model = request.model();
        String operation = request.operation();

        return switch (operation) {
            case "CREATE" -> {
                Integer id = client.create(model, request.values());
                LOG.info("Created {} record with ID: {}", model, id);
                yield OdooResult.created(model, id);
            }

            case "READ" -> {
                List<Integer> ids = request.getEffectiveRecordIds();
                List<Map<String, Object>> records = client.read(model, ids, request.fields());
                LOG.info("Read {} records from {}", records.size(), model);
                yield OdooResult.read(model, records);
            }

            case "UPDATE" -> {
                List<Integer> ids = request.getEffectiveRecordIds();
                boolean success = client.write(model, ids, request.values());
                if (!success) {
                    throw new OdooApiException("Update operation returned false");
                }
                LOG.info("Updated {} records in {}", ids.size(), model);
                yield OdooResult.updated(model, ids);
            }

            case "DELETE" -> {
                List<Integer> ids = request.getEffectiveRecordIds();
                boolean success = client.unlink(model, ids);
                if (!success) {
                    throw new OdooApiException("Delete operation returned false");
                }
                LOG.info("Deleted {} records from {}", ids.size(), model);
                yield OdooResult.deleted(model, ids);
            }

            case "SEARCH" -> {
                List<Integer> ids = client.search(model, request.domain(), request.limit(), request.offset());
                LOG.info("Found {} record IDs in {}", ids.size(), model);
                yield OdooResult.searched(model, ids);
            }

            case "SEARCH_READ" -> {
                List<Map<String, Object>> records = client.searchRead(
                        model, request.domain(), request.fields(), request.limit(), request.offset());
                LOG.info("Search-Read found {} records in {}", records.size(), model);
                yield OdooResult.searchedAndRead(model, records);
            }

            case "SEARCH_COUNT" -> {
                Integer count = client.searchCount(model, request.domain());
                LOG.info("Counted {} records in {} matching domain", count, model);
                yield OdooResult.counted(model, count);
            }

            case "CALL_METHOD" -> {
                String methodName = request.methodName();
                Object result = client.callMethod(model, methodName,
                        request.getEffectiveRecordIds(), request.methodArgs());
                LOG.info("Called method {} on {} with result type: {}",
                        methodName, model, result != null ? result.getClass().getSimpleName() : "null");
                yield OdooResult.methodCalled(model, methodName, result);
            }

            default -> throw new ConnectorException(ERROR_VALIDATION,
                    "Unsupported operation: " + operation);
        };
    }

    /**
     * Handle API exceptions and convert to appropriate BPMN errors.
     */
    private OdooResult handleApiException(OdooApiException e, OdooRequest request) {
        String errorCode = determineErrorCode(e);
        String operation = request.operation();
        String model = request.model();

        OdooApiClient.OdooError error = e.getError();
        if (error != null) {
            LOG.error("Odoo API error: {} - {} (errorCode: {})", error.name(), error.message(), errorCode);
            return OdooResult.error(
                    operation,
                    model,
                    error.message() + " [" + errorCode + "]",
                    error.name(),
                    error.statusCode());
        }

        LOG.error("Odoo error: {} (errorCode: {})", e.getMessage(), errorCode);
        return OdooResult.error(operation, model, e.getMessage() + " [" + errorCode + "]");
    }

    /**
     * Determine appropriate BPMN error code from exception.
     */
    private String determineErrorCode(OdooApiException e) {
        if (e.isAuthenticationError()) {
            return ERROR_AUTHENTICATION;
        }
        if (e.isPermissionError()) {
            return ERROR_PERMISSION;
        }
        if (e.isNotFoundError()) {
            return ERROR_NOT_FOUND;
        }
        if (e.isValidationError()) {
            return ERROR_VALIDATION;
        }
        if (e.getMessage() != null && e.getMessage().startsWith("Network error")) {
            return ERROR_CONNECTION;
        }
        return ERROR_UNKNOWN;
    }
}
