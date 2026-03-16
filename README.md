# Camunda Odoo Connector

[![Compatible with: Camunda 8](https://img.shields.io/badge/Compatible%20with-Camunda%208-26d07c)](https://docs.camunda.io/)
[![Odoo Version](https://img.shields.io/badge/Odoo-19.0-714B67)](https://www.odoo.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A **production-ready** Camunda 8 Connector for integrating with **Odoo 19's External JSON-2 API**. This connector enables seamless bi-directional communication between Camunda workflows and Odoo ERP systems.

![Odoo Connector Banner](Resources/Banner.png)

## Features

### Outbound Connector (Camunda → Odoo)
- **CRUD Operations**: Create, Read, Update, Delete records in any Odoo model
- **Search & Filter**: Query records with domain filters, pagination, and sorting
- **Search & Read**: Combined search and read in a single operation
- **Custom Methods**: Execute any custom Odoo model method
- **Robust Error Handling**: Automatic retries with exponential backoff
- **Full JSON-2 API Support**: Uses Odoo 19's modern REST-like API

### Inbound Connector (Odoo → Camunda)
- **Polling Mode (Primary)**: Periodically poll Odoo 19 for new/modified records with robust deduplication.
- **Broker-Level Deduplication**: Uses unique `messageId` to ensure "Exactly-Once" process execution.
- **Flexible BPMN Support**: Includes templates for Start Events (Message/Simple), Intermediate Catch, and Boundary Events.
- **Custom Filters**: Leverage Odoo domain syntax to monitor specific record states.
- **Multi-model Support**: Monitor any Odoo model (e.g., `sale.order`, `res.partner`).

## Requirements

- **Camunda 8** (Self-Managed or SaaS) - Runtime 8.9.0+
- **Odoo 19** with External API enabled
- **Java 21** or higher (for building from source)
- **Maven 3.8+** (for building from source)

## Installation

### Option 1: Download Pre-built JARs

Download the latest releases from the [Releases](https://github.com/Tehuti-Projects/Odoo-19-Camunda-8-Connector/releases) page.

### Option 2: Build from Source

```bash
# Clone the repository
git clone https://github.com/Tehuti-Projects/Odoo-19-Camunda-8-Connector.git
cd Odoo-19-Camunda-8-Connector

# Build outbound connector
cd odoo-outbound-connector
mvn clean package -DskipTests

# Build inbound connector
cd ../odoo-inbound-connector
mvn clean package -DskipTests
```

### Deployment

#### Self-Managed (Helm)
Add the connector JARs to your Connector Runtime using `LOADER_PATH`:

```yaml
connectors:
  extraEnv:
    - name: LOADER_PATH
      value: "/opt/custom-connectors/"
  extraVolumeMounts:
    - name: custom-connectors
      mountPath: /opt/custom-connectors
  extraVolumes:
    - name: custom-connectors
      hostPath:
        path: /path/to/connector-jars
```

#### Docker
```bash
docker run -d \
  -v /path/to/connectors:/opt/custom-connectors \
  -e LOADER_PATH=/opt/custom-connectors \
  camunda/connectors-bundle:8.9.0
```

## Configuration

### Odoo API Key Setup

1. Log in to Odoo as an administrator
2. Go to **Settings → General Settings → Integrations**
3. Enable **External API**
4. Navigate to **Settings → Users → [Your User] → API Keys**
5. Generate a new API key

### Element Templates

Copy the element templates from `element-templates/` to your Camunda Modeler:
- **Windows**: `%APPDATA%\camunda-modeler\resources\element-templates\`
- **macOS**: `~/Library/Application Support/camunda-modeler/resources/element-templates/`
- **Linux**: `~/.config/camunda-modeler/resources/element-templates/`

## Usage

### Outbound Connector

#### Authentication
| Property | Description |
|----------|-------------|
| `url` | Odoo instance URL (e.g., `https://mycompany.odoo.com`) |
| `database` | Odoo database name |
| `apiKey` | API key for authentication |

#### Operations

##### CREATE - Create a new record
```json
{
  "operation": "CREATE",
  "model": "res.partner",
  "values": {
    "name": "Acme Corporation",
    "email": "info@acme.com",
    "is_company": true
  }
}
```

##### READ - Read records by IDs
```json
{
  "operation": "READ",
  "model": "res.partner",
  "recordIds": [1, 2, 3],
  "fields": ["name", "email", "phone"]
}
```

##### SEARCH - Find records matching criteria
```json
{
  "operation": "SEARCH",
  "model": "res.partner",
  "domain": [["is_company", "=", true]],
  "limit": 10,
  "offset": 0,
  "order": "name ASC"
}
```

##### SEARCH_READ - Search and read in one call
```json
{
  "operation": "SEARCH_READ",
  "model": "res.partner",
  "domain": [["customer_rank", ">", 0]],
  "fields": ["name", "email"],
  "limit": 50
}
```

##### UPDATE - Update existing records
```json
{
  "operation": "UPDATE",
  "model": "res.partner",
  "recordIds": [42],
  "values": {
    "phone": "+1-555-0123"
  }
}
```

##### DELETE - Delete records
```json
{
  "operation": "DELETE",
  "model": "res.partner",
  "recordIds": [42]
}
```

##### EXECUTE - Call custom methods
```json
{
  "operation": "EXECUTE",
  "model": "sale.order",
  "methodName": "action_confirm",
  "recordId": 100
}
```

#### Response Format
```json
{
  "operation": "CREATE",
  "model": "res.partner",
  "success": true,
  "createdId": 42,
  "primaryResult": 42
}
```

### Inbound Connector

#### Polling Mode
The connector monitors Odoo models at regular intervals. It generates a unique `messageId` in the format `odoo-{model}-{recordId}-{eventType}` to prevent duplicates.

| Property | Description |
|----------|-------------|
| `model` | Odoo model to monitor (e.g., `sale.order`) |
| `pollingInterval` | Interval in seconds (default: 30) |
| `domain` | Optional filter domain (Polish notation) |
| `trackingField` | Field to track changes (default: `write_date`) |
| `messageName` | Unique name for the message (required for Message Start/Catch) |

## Element Templates

| Template | Type | Description |
|----------|------|-------------|
| `odoo-outbound-connector.json` | Service Task | Outbound operations (CRUD, Search, Execute) |
| `odoo-polling-connector-message-start-event.json` | Message Start | **Recommended.** Starts process with Exactly-Once guarantees. |
| `odoo-polling-connector-start-event.json` | Start Event | Simple start event (No message required). |
| `odoo-polling-connector-intermediate.json` | Intermediate Catch | Pauses process until a specific Odoo event occurs. |
| `odoo-polling-connector-boundary.json` | Boundary Event | Interrupts activity when an Odoo event occurs. |

## Domain Filter Syntax

Odoo uses a Polish notation (prefix) domain syntax:

```json
// Simple condition
[["field", "operator", "value"]]

// AND (implicit)
[["field1", "=", "value1"], ["field2", ">", 10]]

// OR
["|", ["field1", "=", "A"], ["field1", "=", "B"]]

// NOT
["!", ["active", "=", false]]

// Complex
["&", "|", ["state", "=", "draft"], ["state", "=", "sent"], ["amount", ">", 1000]]
```

### Common Operators
| Operator | Description |
|----------|-------------|
| `=` | Equals |
| `!=` | Not equals |
| `>`, `<`, `>=`, `<=` | Comparisons |
| `like` | Pattern match (% wildcards) |
| `ilike` | Case-insensitive pattern match |
| `in` | Value in list |
| `not in` | Value not in list |
| `child_of` | Hierarchical child |

## Error Handling

The connector provides structured error responses:

```json
{
  "success": false,
  "error": "[422] werkzeug.exceptions.UnprocessableEntity: ...",
  "errorName": "werkzeug.exceptions.UnprocessableEntity",
  "statusCode": 422
}
```

### Common Error Codes
| Code | Description |
|------|-------------|
| 401 | Invalid API key or authentication failed |
| 403 | Access denied (check user permissions) |
| 404 | Model or record not found |
| 422 | Validation error (check required fields) |
| 500 | Odoo server error |

## Secrets Management

Use Camunda secrets for sensitive values:

```
{{secrets.ODOO_API_KEY}}
```

Configure in Connector Runtime:
```yaml
env:
  - name: CAMUNDA_CONNECTORS_SECRETPROVIDER_ENVIRONMENT_PREFIX
    value: "CONNECTOR_"
  - name: CONNECTOR_ODOO_API_KEY
    value: "your-api-key"
```

## Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- 📖 [Odoo External API Documentation](https://www.odoo.com/documentation/19.0/developer/reference/external_api.html)
- 📖 [Camunda Connectors Documentation](https://docs.camunda.io/docs/components/connectors/introduction/)
- 🐛 [Issue Tracker](https://github.com/Tehuti-Projects/Odoo-19-Camunda-8-Connector/issues)

## Acknowledgments

- Built with [Camunda Connectors SDK](https://github.com/camunda/connectors)
- Powered by [Odoo](https://www.odoo.com/) ERP

---

Made with ❤️ by [Tehuti Projects](https://tehutiprojects.com)
