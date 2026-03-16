package io.camunda.connector.odoo.inbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OdooWebhookPropertiesTest {

    @Test
    void blankAllowListAcceptsApprovedEventTypes() {
        OdooWebhookProperties properties = new OdooWebhookProperties("secret", null, false);

        assertTrue(properties.isEventTypeAllowed("odoo.deposit_paid.v1"));
    }

    @Test
    void explicitAllowListFiltersEventTypes() {
        OdooWebhookProperties properties = new OdooWebhookProperties(
                "secret",
                "odoo.deposit_paid.v1,odoo.final_packet.generated.v1",
                false);

        assertTrue(properties.isEventTypeAllowed("odoo.deposit_paid.v1"));
        assertFalse(properties.isEventTypeAllowed("odoo.support_ticket.opened.v1"));
    }
}
