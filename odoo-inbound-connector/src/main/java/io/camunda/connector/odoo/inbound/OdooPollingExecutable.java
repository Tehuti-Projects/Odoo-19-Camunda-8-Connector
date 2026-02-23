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

import java.time.Duration;
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
 * 
 * Thread-safe with memory-bounded deduplication cache.
 */
@InboundConnector(name = "Odoo 19 Polling", type = "io.camunda:odoo-inbound-polling:1")
public class OdooPollingExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

    private static final Logger LOG = LoggerFactory.getLogger(OdooPollingExecutable.class);
    private static final DateTimeFormatter ODOO_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Memory-bounded cache to prevent OutOfMemoryError
    private static final int MAX_CACHE_SIZE = 10_000;
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private InboundConnectorContext context;
    private OdooPollingProperties properties;
    private OdooPollingClient client;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean active = new AtomicBoolean(false);

    private volatile Instant lastPollTime;

    // Thread-safe, memory-bounded cache for deduplication
    private final Cache<Integer, Boolean> processedRecords = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .build();

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

        // Create scheduler with named, daemon threads and error handling
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "odoo-polling-" + properties.model());
            t.setDaemon(true); // Allow JVM to exit
            t.setUncaughtExceptionHandler(
                    (thread, ex) -> LOG.error("Uncaught exception in polling thread for {}", properties.model(), ex));
            return t;
        });

        int interval = properties.getEffectivePollingInterval();
        scheduler.scheduleAtFixedRate(this::poll, interval, interval, TimeUnit.SECONDS);

        LOG.info("Odoo polling connector activated: model={}, interval={}s, cacheSize={}, cacheTTL={}h",
                properties.model(), interval, MAX_CACHE_SIZE, CACHE_TTL.toHours());
    }

    @Override
    public void deactivate() throws Exception {
        active.set(false);

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOG.warn("Scheduler did not terminate gracefully, forcing shutdown");
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

        processedRecords.invalidateAll();
        LOG.info("Odoo polling connector deactivated: model={}", properties.model());
    }

    private void poll() {
        if (!active.get()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int processedCount = 0;

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
                if (!active.get()) {
                    break;
                }
                if (processRecord(record)) {
                    processedCount++;
                }
            }

            lastPollTime = Instant.now();

        } catch (Exception e) {
            LOG.error("Error polling Odoo for {}: {}", properties.model(), e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("Polling completed: model={}, recordsFound={}, recordsProcessed={}, durationMs={}, cacheSize={}",
                    properties.model(), processedCount, processedCount, duration, processedRecords.estimatedSize());
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

    /**
     * Process a single record. Returns true if processed, false if skipped.
     */
    private boolean processRecord(Map<String, Object> record) {
        Integer recordId = extractRecordId(record);
        if (recordId == null) {
            LOG.warn("Skipping record with null ID");
            return false;
        }

        // Check cache for deduplication
        if (processedRecords.getIfPresent(recordId) != null) {
            LOG.debug("Skipping already processed record {}", recordId);
            return false;
        }

        String eventType = determineEventType(record);
        if (eventType == null) {
            LOG.debug("Skipping record {} - event type not configured", recordId);
            return false;
        }

        OdooPollingEvent event = OdooPollingEvent.fromRecord(
                properties.model(),
                record,
                eventType,
                properties.getEffectiveTriggerField());

        try {
            // Construct unique message ID for deduplication
            String messageId = String.format("odoo-%s-%d-%s",
                    properties.model(),
                    recordId,
                    eventType);

            CorrelationRequest request = CorrelationRequest.builder()
                    .messageId(messageId)
                    .variables(event.toVariables())
                    .build();

            CorrelationResult result = context.correlate(request);
            handleCorrelationResult(result, event);

            // Mark as processed
            processedRecords.put(recordId, Boolean.TRUE);
            return true;

        } catch (Exception e) {
            LOG.error("Failed to correlate event for record {}: {}", recordId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Safely extract record ID with null safety.
     */
    private Integer extractRecordId(Map<String, Object> record) {
        return Optional.ofNullable(record.get("id"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(null);
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
            case CorrelationResult.Success ignored ->
                LOG.info("Event correlated successfully: model={}, id={}, type={}",
                        event.model(), event.recordId(), event.eventType());
            case CorrelationResult.Failure failure -> {
                if (failure.handlingStrategy() instanceof CorrelationFailureHandlingStrategy.ForwardErrorToUpstream) {
                    LOG.error("Correlation failed for model={}, id={}: {}",
                            event.model(), event.recordId(), failure.message());
                } else {
                    LOG.debug("No waiting process for event: model={}, id={}",
                            event.model(), event.recordId());
                }
            }
        }
    }
}
