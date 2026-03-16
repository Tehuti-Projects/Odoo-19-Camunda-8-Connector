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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production-grade Camunda Inbound Connector for Odoo 19.
 * Polls Odoo for new or modified records at regular intervals.
 */
@InboundConnector(name = "Odoo 19 Polling", type = "io.camunda:odoo-inbound-polling:1")
public class OdooPollingExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

    private static final Logger LOG = LoggerFactory.getLogger(OdooPollingExecutable.class);
    private static final DateTimeFormatter ODOO_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private InboundConnectorContext context;
    private OdooPollingProperties properties;
    private OdooPollingClient client;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean active = new AtomicBoolean(false);

    private volatile Instant lastPollTime;
    private final Set<Integer> processedIds = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void activate(InboundConnectorContext connectorContext) throws Exception {
        this.context = connectorContext;
        this.properties = connectorContext.bindProperties(OdooPollingProperties.class);

        this.client = new OdooPollingClient(
                properties.url(),
                properties.database(),
                properties.apiKey());

        if (!client.testConnection()) {
            throw new RuntimeException("Failed to connect to Odoo at " + properties.url());
        }

        this.lastPollTime = Instant.now();
        this.active.set(true);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "odoo-polling-" + properties.model());
            t.setDaemon(true);
            return t;
        });

        int interval = properties.getEffectivePollingInterval();
        scheduler.scheduleAtFixedRate(this::poll, interval, interval, TimeUnit.SECONDS);

        LOG.info("Odoo polling connector activated: model={}, interval={}s",
                properties.model(), interval);
    }

    @Override
    public void deactivate() throws Exception {
        active.set(false);

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

        if (client != null) {
            client.close();
        }

        processedIds.clear();
        LOG.info("Odoo polling connector deactivated: model={}", properties.model());
    }

    private void poll() {
        if (!active.get())
            return;

        try {
            List<Object> domain = buildDomain();
            List<String> fields = buildFieldsList();

            List<Map<String, Object>> records = client.searchRead(
                    properties.model(),
                    domain,
                    fields,
                    properties.getEffectiveBatchSize());

            if (records.isEmpty()) {
                LOG.debug("No new records found for {}", properties.model());
                lastPollTime = Instant.now();
                return;
            }

            LOG.info("Found {} records to process for {}", records.size(), properties.model());

            for (Map<String, Object> record : records) {
                if (!active.get())
                    break;
                processRecord(record);
            }

            lastPollTime = Instant.now();

            if (processedIds.size() > 1000) {
                synchronized (processedIds) {
                    Set<Integer> newSet = new HashSet<>();
                    int count = 0;
                    for (Integer id : processedIds) {
                        if (count++ > 500)
                            newSet.add(id);
                    }
                    processedIds.clear();
                    processedIds.addAll(newSet);
                }
            }

        } catch (Exception e) {
            LOG.error("Error polling Odoo for {}: {}", properties.model(), e.getMessage());
        }
    }

    private List<Object> buildDomain() {
        List<Object> domain = new ArrayList<>();

        String triggerField = properties.getEffectiveTriggerField();
        String lastPollStr = ODOO_DATETIME.format(lastPollTime.atZone(java.time.ZoneOffset.UTC));
        domain.add(List.of(triggerField, ">", lastPollStr));

        List<Object> customDomain = client.parseDomain(properties.filterDomain());
        domain.addAll(customDomain);

        return domain;
    }

    private List<String> buildFieldsList() {
        List<String> fields = new ArrayList<>();
        fields.add("id");
        fields.add("create_date");
        fields.add("write_date");

        String triggerField = properties.getEffectiveTriggerField();
        if (!fields.contains(triggerField)) {
            fields.add(triggerField);
        }

        List<String> customFields = client.parseFields(properties.fields());
        if (customFields != null) {
            for (String f : customFields) {
                if (!fields.contains(f)) {
                    fields.add(f);
                }
            }
        }

        return fields;
    }

    private void processRecord(Map<String, Object> record) {
        Integer recordId = record.get("id") != null
                ? ((Number) record.get("id")).intValue()
                : null;

        if (recordId == null) {
            return;
        }

        // We still keep the local cache to save unnecessary network calls
        if (processedIds.contains(recordId)) {
            return;
        }

        String eventType = determineEventType(record);
        if (eventType == null) {
            return;
        }

        OdooPollingEvent event = OdooPollingEvent.fromRecord(
                properties.model(),
                record,
                eventType,
                properties.getEffectiveTriggerField());

        try {
            // Construct a standardized, unique Message ID for deduplication
            // Format: odoo-{model}-{id}-{eventType}
            // Example: odoo-res.partner-123-create
            String messageId = String.format("odoo-%s-%d-%s",
                    properties.model(),
                    recordId,
                    eventType);

            CorrelationRequest request = CorrelationRequest.builder()
                    .messageId(messageId) // Critical for Deduplication
                    .variables(event.toVariables())
                    .build();

            CorrelationResult result = context.correlate(request);
            handleCorrelationResult(result, event);

            processedIds.add(recordId);

        } catch (Exception e) {
            LOG.error("Failed to correlate event for record {}: {}", recordId, e.getMessage());
        }
    }

    private String determineEventType(Map<String, Object> record) {
        Object createDate = record.get("create_date");
        Object writeDate = record.get("write_date");

        boolean isNew = createDate != null && writeDate != null && createDate.equals(writeDate);

        if (isNew) {
            if (properties.triggerOnNew()) {
                return "create";
            }
        } else {
            if (properties.triggerOnModified()) {
                return "write";
            }
        }

        return null;
    }

    private void handleCorrelationResult(CorrelationResult result, OdooPollingEvent event) {
        switch (result) {
            case CorrelationResult.Success ignored -> LOG.info("Event correlated: model={}, id={}, type={}",
                    event.model(), event.recordId(), event.eventType());
            case CorrelationResult.Failure failure -> {
                switch (failure.handlingStrategy()) {
                    case CorrelationFailureHandlingStrategy.ForwardErrorToUpstream ignored ->
                        LOG.error("Correlation failed: {}", failure.message());
                    case CorrelationFailureHandlingStrategy.Ignore ignored ->
                        LOG.debug("No waiting process for event: {}", failure.message());
                }
            }
        }
    }
}
