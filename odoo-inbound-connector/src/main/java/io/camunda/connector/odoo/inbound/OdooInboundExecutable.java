package io.camunda.connector.odoo.inbound;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Camunda Inbound Connector for Odoo 19 Webhook Events.
 * 
 * Receives HTTP webhook calls from Odoo with security features:
 * - Constant-time secret token validation (timing attack resistant)
 * - Webhook replay protection
 * - Event filtering by model and operation type
 */
@SuppressWarnings("unused")
@InboundConnector(name = "Odoo Inbound Webhook", type = "io.camunda:odoo-inbound-webhook:1")
public class OdooInboundExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

    private static final Logger LOG = LoggerFactory.getLogger(OdooInboundExecutable.class);

    // Webhook replay protection settings
    private static final Duration WEBHOOK_MAX_AGE = Duration.ofMinutes(5);
    private static final int MAX_WEBHOOK_CACHE = 10_000;

    private InboundConnectorContext context;
    private OdooWebhookProperties properties;
    private ScheduledExecutorService scheduler;
    private volatile boolean active = false;

    // Cache for webhook replay protection
    private final Cache<String, Boolean> processedWebhooks = Caffeine.newBuilder()
            .maximumSize(MAX_WEBHOOK_CACHE)
            .expireAfterWrite(WEBHOOK_MAX_AGE)
            .build();

    @Override
    public void activate(InboundConnectorContext connectorContext) throws Exception {
        this.context = connectorContext;
        this.properties = connectorContext.bindProperties(OdooWebhookProperties.class);
        this.active = true;

        LOG.info("Odoo webhook connector activated: allowedModels={}, eventType={}, replayProtection=enabled",
                properties.allowedModels(), properties.eventType());
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
        processedWebhooks.invalidateAll();
        LOG.info("Odoo webhook connector deactivated");
    }

    /**
     * Process an incoming Odoo webhook event.
     * 
     * @param payload     The webhook payload from Odoo
     * @param secretToken The secret token from the X-Odoo-Webhook-Secret header
     * @return Processing result with HTTP status code
     */
    public WebhookProcessingResult processWebhook(Map<String, Object> payload, String secretToken) {
        if (!active) {
            return new WebhookProcessingResult(false, "Connector not active", 503);
        }

        // Validate secret token using constant-time comparison (timing attack
        // resistant)
        if (properties.secretToken() != null && !properties.secretToken().isEmpty()) {
            if (!constantTimeEquals(properties.secretToken(), secretToken)) {
                LOG.warn("Invalid webhook secret token received");
                return new WebhookProcessingResult(false, "Invalid secret token", 401);
            }
        }

        try {
            // Parse the event
            OdooInboundEvent event = OdooInboundEvent.fromPayload(payload);

            // Webhook replay protection
            String webhookId = generateWebhookId(event);
            if (processedWebhooks.getIfPresent(webhookId) != null) {
                LOG.warn("Duplicate webhook detected: model={}, recordIds={}",
                        event.model(), event.recordIds());
                return new WebhookProcessingResult(true, "Duplicate webhook ignored", 200);
            }

            // Validate timestamp (reject old webhooks)
            if (event.timestamp().isBefore(Instant.now().minus(WEBHOOK_MAX_AGE))) {
                LOG.warn("Webhook too old: timestamp={}, maxAge={}min",
                        event.timestamp(), WEBHOOK_MAX_AGE.toMinutes());
                return new WebhookProcessingResult(false, "Webhook expired", 400);
            }

            // Validate model
            if (!properties.isModelAllowed(event.model())) {
                LOG.debug("Ignoring webhook for model {} (not in allowed list)", event.model());
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

            // Mark as processed for replay protection
            processedWebhooks.put(webhookId, Boolean.TRUE);

            return handleCorrelationResult(result, event);

        } catch (Exception e) {
            LOG.error("Error processing Odoo webhook event: {}", e.getMessage(), e);
            return new WebhookProcessingResult(false, e.getMessage(), 500);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * Uses MessageDigest.isEqual() which is guaranteed to be constant-time.
     */
    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * Generate unique webhook ID for replay protection.
     * Based on model + operation + recordIds + timestamp.
     */
    private String generateWebhookId(OdooInboundEvent event) {
        return String.format("%s-%s-%s-%s",
                event.model(),
                event.operation(),
                event.recordIds(),
                event.timestamp().toEpochMilli());
    }

    private WebhookProcessingResult handleCorrelationResult(CorrelationResult result, OdooInboundEvent event) {
        return switch (result) {
            case CorrelationResult.Success ignored -> {
                LOG.info("Odoo webhook correlated successfully: model={}, operation={}, recordId={}",
                        event.model(), event.operation(), event.getRecordId());
                yield new WebhookProcessingResult(true, "Event correlated", 200);
            }
            case CorrelationResult.Failure failure -> {
                if (failure.handlingStrategy() instanceof CorrelationFailureHandlingStrategy.Ignore) {
                    LOG.debug("Correlation not required, webhook acknowledged: {}", failure.message());
                    yield new WebhookProcessingResult(true, "Event acknowledged", 200);
                } else {
                    LOG.error("Correlation failed: {}", failure.message());
                    yield new WebhookProcessingResult(false, failure.message(), 422);
                }
            }
        };
    }

    /**
     * Result of webhook processing.
     */
    public record WebhookProcessingResult(boolean success, String message, int statusCode) {
    }
}
