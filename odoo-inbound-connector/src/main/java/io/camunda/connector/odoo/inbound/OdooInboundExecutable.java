package io.camunda.connector.odoo.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Camunda Inbound Connector for Odoo 19 Events.
 * 
 * This connector provides two modes of operation:
 * 1. WEBHOOK mode - Receives HTTP webhook calls from Odoo (requires external
 * webhook setup)
 * 2. POLLING mode - Periodically checks Odoo for new/updated records
 * 
 * The connector correlates events to waiting process instances based on
 * configuration.
 */
@SuppressWarnings("unused")
@InboundConnector(name = "Odoo Inbound Webhook", type = "io.camunda:odoo-inbound-webhook:1")
public class OdooInboundExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

    private static final Logger LOG = LoggerFactory.getLogger(OdooInboundExecutable.class);

    private InboundConnectorContext context;
    private OdooWebhookProperties properties;
    private ScheduledExecutorService scheduler;
    private volatile boolean active = false;

    @Override
    public void activate(InboundConnectorContext connectorContext) throws Exception {
        this.context = connectorContext;
        this.properties = connectorContext.bindProperties(OdooWebhookProperties.class);
        this.active = true;

        // Start polling simulation (in production, this would integrate with Camunda's
        // webhook infrastructure)
        // For demonstration, we'll just mark the connector as ready
        LOG.info("Odoo inbound connector activated. Secret: [configured], Allowed models: {}, Event type: {}",
                properties.allowedModels(), properties.eventType());
        LOG.info("Connector is ready to receive webhook events at the Camunda Connectors webhook endpoint");
    }

    @Override
    public void deactivate() throws Exception {
        this.active = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("Odoo inbound connector deactivated");
    }

    /**
     * Process an incoming Odoo webhook event.
     * This method should be called by the Camunda webhook infrastructure when a
     * request is received.
     * 
     * @param payload     The webhook payload from Odoo
     * @param secretToken The secret token from the X-Odoo-Webhook-Secret header
     * @return Processing result
     */
    public WebhookProcessingResult processWebhook(Map<String, Object> payload, String secretToken) {
        if (!active) {
            return new WebhookProcessingResult(false, "Connector not active", 503);
        }

        // Validate secret token
        if (properties.secretToken() != null && !properties.secretToken().isEmpty()) {
            if (!properties.secretToken().equals(secretToken)) {
                LOG.warn("Invalid webhook secret token received");
                return new WebhookProcessingResult(false, "Invalid secret token", 401);
            }
        }

        try {
            // Parse the event
            OdooInboundEvent event = OdooInboundEvent.fromPayload(payload);

            // Validate model
            if (!properties.isModelAllowed(event.model())) {
                LOG.debug("Ignoring event for model {} (not in allowed list)", event.model());
                return new WebhookProcessingResult(true, "Model not in allowed list", 200);
            }

            // Validate event type
            if (!properties.isEventTypeAllowed(event.operation())) {
                LOG.debug("Ignoring event type {} (not matching filter)", event.operation());
                return new WebhookProcessingResult(true, "Event type not matching", 200);
            }

            // Build event variables for process correlation
            Map<String, Object> eventVariables = Map.of(
                    "odooModel", event.model(),
                    "odooOperation", event.operation(),
                    "odooRecordId", event.getRecordId() != null ? event.getRecordId() : 0,
                    "odooRecordIds", event.recordIds(),
                    "odooUserId", event.userId() != null ? event.userId() : 0,
                    "odooDatabase", event.database() != null ? event.database() : "",
                    "odooTimestamp", event.timestamp().toString(),
                    "odooValues", event.values(),
                    "odooChangedFields", event.changedFields(),
                    "odooEvent", payload);

            CorrelationRequest correlationRequest = CorrelationRequest.builder()
                    .variables(eventVariables)
                    .build();

            // Correlate the event with waiting process instances
            CorrelationResult result = context.correlate(correlationRequest);
            return handleCorrelationResult(result, event);

        } catch (Exception e) {
            LOG.error("Error processing Odoo webhook event: {}", e.getMessage(), e);
            return new WebhookProcessingResult(false, e.getMessage(), 500);
        }
    }

    private WebhookProcessingResult handleCorrelationResult(CorrelationResult result, OdooInboundEvent event) {
        return switch (result) {
            case CorrelationResult.Success ignored -> {
                LOG.info("Odoo event correlated successfully: model={}, operation={}, recordId={}",
                        event.model(), event.operation(), event.getRecordId());
                yield new WebhookProcessingResult(true, "Event correlated", 200);
            }
            case CorrelationResult.Failure failure -> {
                if (failure.handlingStrategy() instanceof CorrelationFailureHandlingStrategy.Ignore) {
                    LOG.debug("Correlation not required, event acknowledged: {}", failure.message());
                    yield new WebhookProcessingResult(true, "Event acknowledged", 200);
                } else {
                    LOG.error("Correlation failed: {}", failure.message());
                    yield new WebhookProcessingResult(false, failure.message(), 422);
                }
            }
        };
    }

    /**
     * Result of webhook processing
     */
    public record WebhookProcessingResult(boolean success, String message, int statusCode) {
    }
}
