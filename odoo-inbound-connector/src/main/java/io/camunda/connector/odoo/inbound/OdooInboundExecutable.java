package io.camunda.connector.odoo.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Odoo Inbound Webhook", type = "io.camunda:odoo-inbound-webhook:1")
public class OdooInboundExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

    private static final Logger LOG = LoggerFactory.getLogger(OdooInboundExecutable.class);

    private InboundConnectorContext context;
    private OdooWebhookProperties properties;
    private volatile boolean active = false;

    @Override
    public void activate(InboundConnectorContext connectorContext) throws Exception {
        this.context = connectorContext;
        this.properties = connectorContext.bindProperties(OdooWebhookProperties.class);
        this.active = true;

        LOG.info(
                "connector=mautourism-odoo mode=inbound_webhook allowed_event_types={} legacy_generic_webhook_enabled={}",
                properties.allowedEventTypes(),
                properties.allowsLegacyGenericWebhook());
    }

    @Override
    public void deactivate() {
        this.active = false;
        LOG.info("connector=mautourism-odoo mode=inbound_webhook deactivated=true");
    }

    public WebhookProcessingResult processWebhook(Map<String, Object> payload, String secretToken) {
        if (!active) {
            return new WebhookProcessingResult(false, "Connector not active", 503);
        }

        if (properties.secretToken() != null && !properties.secretToken().equals(secretToken)) {
            LOG.warn("connector=mautourism-odoo mode=inbound_webhook unauthorized=true");
            return new WebhookProcessingResult(false, "Invalid secret token", 401);
        }

        try {
            MauOdooEventEnvelope envelope = MauOdooEventEnvelope.fromPayload(payload);
            if (!properties.isEventTypeAllowed(envelope.eventType())) {
                LOG.debug(
                        "connector=mautourism-odoo mode=inbound_webhook ignored=true event_type={}",
                        envelope.eventType());
                return new WebhookProcessingResult(true, "Event type not in allowed list", 200);
            }

            CorrelationRequest correlationRequest = CorrelationRequest.builder()
                    .messageId(envelope.externalRef())
                    .variables(envelope.toVariables())
                    .build();

            CorrelationResult result = context.correlate(correlationRequest);
            return handleCorrelationResult(result, envelope);
        } catch (IllegalArgumentException illegalArgumentException) {
            if (properties.allowsLegacyGenericWebhook()) {
                LOG.warn(
                        "connector=mautourism-odoo mode=inbound_webhook legacy_generic_payload_rejected=true message={}",
                        illegalArgumentException.getMessage());
            }
            return new WebhookProcessingResult(false, illegalArgumentException.getMessage(), 400);
        } catch (Exception exception) {
            LOG.error(
                    "connector=mautourism-odoo mode=inbound_webhook internal_error=true message={}",
                    exception.getMessage(),
                    exception);
            return new WebhookProcessingResult(false, exception.getMessage(), 500);
        }
    }

    private WebhookProcessingResult handleCorrelationResult(CorrelationResult result, MauOdooEventEnvelope envelope) {
        return switch (result) {
            case CorrelationResult.Success ignored -> {
                LOG.info(
                        "connector=mautourism-odoo mode=inbound_webhook correlated=true event_type={} external_ref={} camunda_business_key={} reference_code={}",
                        envelope.eventType(),
                        envelope.externalRef(),
                        envelope.correlation().get("camunda_business_key"),
                        envelope.correlation().get("reference_code"));
                yield new WebhookProcessingResult(true, "Event correlated", 200);
            }
            case CorrelationResult.Failure failure -> {
                if (failure.handlingStrategy() instanceof CorrelationFailureHandlingStrategy.Ignore) {
                    LOG.debug(
                            "connector=mautourism-odoo mode=inbound_webhook ignored=true event_type={} external_ref={}",
                            envelope.eventType(),
                            envelope.externalRef());
                    yield new WebhookProcessingResult(true, "Event acknowledged", 200);
                }
                LOG.error(
                        "connector=mautourism-odoo mode=inbound_webhook correlation_failed=true event_type={} external_ref={} message={}",
                        envelope.eventType(),
                        envelope.externalRef(),
                        failure.message());
                yield new WebhookProcessingResult(false, failure.message(), 422);
            }
        };
    }

    public record WebhookProcessingResult(boolean success, String message, int statusCode) {
    }
}
