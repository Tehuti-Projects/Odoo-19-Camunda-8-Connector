# Odoo 19 Inbound Connector

Production-grade Camunda 8 Inbound Connector that polls Odoo 19 for record changes. This connector has been transitioned from a Webhook-based architecture to a Polling-based architecture to ensure compatibility with Odoo 19 and provide robust reliability.

## How It Works

This connector **polls** Odoo at regular intervals. It uses a state-based tracking mechanism to identify new or modified records since the last poll.

```
[Odoo Database] <--poll every N seconds-- [Camunda Connector]
                  (new/modified records)        |
                                                v
                                    [Broker-Level Deduplication]
                                                |
                                                v
                                    [Process Instance Correlation]
```

## Deduplication Logic

To ensure "Exactly-Once" execution, the connector generates a unique `messageId` for every event found. Zeebe uses this ID to reject duplicate process instances if the connector or server restarts during processing.

**Format:** `odoo-{model}-{recordId}-{eventType}` (e.g., `odoo-sale.order-123-create`)

## Features

- **Polling-based** - Native Odoo 19 compatibility without complex webhook setup.
- **Broker-Level Deduplication** - Guaranteed reliability via unique `messageId`.
- **Configurable interval** - 10 seconds minimum to balance freshness and server load.
- **Trigger conditions** - NEW, MODIFIED, or BOTH.
- **Custom filters** - Full Odoo domain filter support.
- **Fields Selection** - Specify exactly which fields to include in the payload.

## Build

```bash
mvn clean package -DskipTests
```

## Configuration

### Authentication
| Parameter | Description |
|-----------|-------------|
| **URL** | Odoo instance URL |
| **Database** | Database name |
| **API Key** | API key from Odoo user profile |

### Polling
| Parameter | Description | Default |
|-----------|-------------|---------|
| **Model** | Odoo model to monitor (e.g., `sale.order`) | Required |
| **Polling Interval** | Seconds between polls | 30 |
| **Trigger Condition** | NEW, MODIFIED, or BOTH | BOTH |
| **Trigger Field** | Field to track changes | `write_date` |
| **Filter Domain** | Additional domain filter (Polish notation) | None |
| **Fields** | Fields to include in event payload | All |
| **Batch Size** | Max records per poll | 50 |

## Event Variables

On correlation, these variables are available in the process:
- `odooModel` - Model name (e.g., "sale.order")
- `odooRecordId` - Record ID
- `odooEventType` - "create" or "write"
- `odooRecord` - Full record data (map of fields)
- `odooTimestamp` - When the record was modified in Odoo
- `odooFields` - List of fields included

## Element Templates & Usage Scenarios

We provide 4 distinct templates to cover all BPMN use cases:

### A. Message Start Event (Recommended)
**File:** `odoo-polling-connector-message-start-event.json`
**Scenario:** A customer places an order (`sale.order`). You want to start a fulfillment process.
**Why:** Guaranteed "Exactly-Once" execution. If the server crashes, deduplication prevents shipping the same order twice.

### B. Simple Start Event
**File:** `odoo-polling-connector-start-event.json`
**Scenario:** A new lead (`res.partner`) is added. You want to send a generic "Hello" email.
**Why:** Lighter configuration. Rare glitches resulting in duplicate emails are non-critical.

### C. Intermediate Catch Event
**File:** `odoo-polling-connector-intermediate.json`
**Scenario:** A process is running and waiting for a specific invoice (`account.move`) to be marked as "paid".
**Why:** Pauses the process until a specific Odoo event occurs, correlated by a business ID.

### D. Boundary Event
**File:** `odoo-polling-connector-boundary.json`
**Scenario:** A worker is packing an order (User Task), but the order is cancelled in Odoo.
**Why:** Interrupts the manual task immediately to move to a "Restock" or "Cancellation" flow.

## Deployment

1. Copy the built JAR to your Connectors runtime classpath.
2. Upload the desired element templates from the `element-templates/` directory to Camunda Modeler.
3. Configure Odoo credentials and model monitoring in the properties panel.

## Notes

- **Polling Interval**: Don't set too low (< 10s) to avoid overloading Odoo.
- **Write Date**: The connector uses the `write_date` field (UTC) for timestamp comparison. Ensure your Odoo user has sufficient permissions to read this field.
