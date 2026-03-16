package io.camunda.connector.odoo;

import java.util.Arrays;
import java.util.Optional;

public enum MauBridgeOperation {
    TRIP_UPSERT("TRIP_UPSERT", "/mau/api/v1/trips/upsert", "camunda.trip_upsert.requested.v1"),
    VENDOR_OFFER_UPSERT("VENDOR_OFFER_UPSERT", "/mau/api/v1/vendor-offers/upsert",
            "camunda.vendor_offer_upsert.requested.v1"),
    VENDOR_OFFER_STATUS("VENDOR_OFFER_STATUS", "/mau/api/v1/vendor-offers/status",
            "camunda.vendor_offer_status.requested.v1"),
    READINESS_UPDATE("READINESS_UPDATE", "/mau/api/v1/readiness/update",
            "camunda.readiness_update.requested.v1"),
    TRIP_FINAL_PACKET("TRIP_FINAL_PACKET", "/mau/api/v1/trips/final-packet",
            "camunda.trip_final_packet.requested.v1");

    private final String operation;
    private final String endpointPath;
    private final String eventType;

    MauBridgeOperation(String operation, String endpointPath, String eventType) {
        this.operation = operation;
        this.endpointPath = endpointPath;
        this.eventType = eventType;
    }

    public String operation() {
        return operation;
    }

    public String endpointPath() {
        return endpointPath;
    }

    public String eventType() {
        return eventType;
    }

    public static Optional<MauBridgeOperation> fromOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.operation.equalsIgnoreCase(operation.trim()))
                .findFirst();
    }
}
