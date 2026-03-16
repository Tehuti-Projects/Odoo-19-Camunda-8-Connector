package io.camunda.connector.odoo.inbound;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OdooPollingPropertiesTest {

    @Test
    void rejectsPollingWhenCompatibilityModeIsDisabled() {
        OdooPollingProperties properties = new OdooPollingProperties(
                "https://odoo.example.com",
                "mau",
                "json2-key",
                "sale.order",
                30,
                "write_date",
                "BOTH",
                null,
                null,
                20,
                false,
                null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, properties::validateForMau);
        assertTrue(exception.getMessage().contains("disabled by default"));
    }

    @Test
    void requiresCompatibilityReasonWhenLegacyPollingIsEnabled() {
        OdooPollingProperties properties = new OdooPollingProperties(
                "https://odoo.example.com",
                "mau",
                "json2-key",
                "sale.order",
                30,
                "write_date",
                "BOTH",
                null,
                null,
                20,
                true,
                null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, properties::validateForMau);
        assertTrue(exception.getMessage().contains("compatibilityReason is required"));
    }

    @Test
    void allowsPollingWhenExplicitCompatibilityReasonIsProvided() {
        OdooPollingProperties properties = new OdooPollingProperties(
                "https://odoo.example.com",
                "mau",
                "json2-key",
                "sale.order",
                30,
                "write_date",
                "BOTH",
                null,
                null,
                20,
                true,
                "Temporary recovery while outbound Odoo facts are being wired.");

        assertDoesNotThrow(properties::validateForMau);
    }
}
