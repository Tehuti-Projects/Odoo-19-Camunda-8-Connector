package io.camunda.connector.odoo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.odoo.dto.OdooAuthentication;
import org.junit.jupiter.api.Test;

class OdooAuthenticationTest {

    @Test
    void bridgeCommandsRequireBridgeApiKey() {
        OdooAuthentication authentication = new OdooAuthentication("https://odoo.example.com", null, null, null);

        assertThatThrownBy(authentication::validateForBridge)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bridgeApiKey is required");
    }

    @Test
    void directReadRequiresDatabaseAndApiKey() {
        OdooAuthentication authentication = new OdooAuthentication("https://odoo.example.com", null, null, null);

        assertThatThrownBy(authentication::validateForDirectRead)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database is required");
    }

    @Test
    void validDirectReadAuthPasses() {
        OdooAuthentication authentication = new OdooAuthentication(
                "https://odoo.example.com", "mau", "json2-key", null);

        assertThatCode(authentication::validateForDirectRead).doesNotThrowAnyException();
    }
}
