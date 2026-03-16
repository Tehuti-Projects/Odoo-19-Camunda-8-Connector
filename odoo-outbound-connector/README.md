# Odoo 19 Outbound Connector

Production-grade Camunda 8 Outbound Connector for Odoo 19 External JSON-2 API.

## Features

- **Full CRUD Support** - Create, Read, Update, Delete operations
- **Search Operations** - SEARCH, SEARCH_READ, SEARCH_COUNT with domain filters
- **Custom Methods** - Call any model method via CALL_METHOD
- **Production Ready** - Retry logic, connection pooling, comprehensive error handling
- **BPMN Error Codes** - Proper error codes for error boundary events

## API Format

Uses Odoo 19's new External JSON-2 API:
```
POST /json/2/{model}/{method}
Authorization: bearer {api_key}
X-Odoo-Database: {database}
Content-Type: application/json; charset=utf-8or

{
  "context": {"lang": "en_US"},
  "domain": [["field", "=", "value"]],
  "fields": ["name", "email"]
}
```

## Build

```bash
mvn clean package
```

Output: `target/odoo-outbound-connector-1.0.0-SNAPSHOT.jar`

## Configuration

### Authentication
| Parameter | Description |
|-----------|-------------|
| **URL** | Base URL of Odoo instance (e.g., `https://mycompany.odoo.com`) |
| **Database** | Database name (sent in `X-Odoo-Database` header) |
| **API Key** | Generated from Odoo: Settings → Users & Companies → API Keys |

### Using Secrets
Reference secrets in element template:
```
{{secrets.ODOO_URL}}
{{secrets.ODOO_DATABASE}}
{{secrets.ODOO_API_KEY}}
```

## Operations

### CREATE
Create a new record.
```json
{
  "operation": "CREATE",
  "model": "res.partner",
  "values": {"name": "John Doe", "email": "john@example.com"}
}
```
Returns: `{success: true, createdId: 123}`

### READ
Read records by ID.
```json
{
  "operation": "READ",
  "model": "res.partner",
  "recordIds": [1, 2, 3],
  "fields": ["name", "email"]
}
```
Returns: `{success: true, records: [{id: 1, name: "...", email: "..."}]}`

### UPDATE
Update existing records.
```json
{
  "operation": "UPDATE",
  "model": "res.partner",
  "recordId": 123,
  "values": {"phone": "+1234567890"}
}
```
Returns: `{success: true, affectedIds: [123]}`

### DELETE
Delete records.
```json
{
  "operation": "DELETE",
  "model": "res.partner",
  "recordIds": [123, 124]
}
```
Returns: `{success: true, affectedIds: [123, 124]}`

### SEARCH
Find record IDs matching domain.
```json
{
  "operation": "SEARCH",
  "model": "res.partner",
  "domain": [["is_company", "=", true]],
  "limit": 100,
  "offset": 0
}
```
Returns: `{success: true, searchIds: [1, 2, 3, ...]}`

### SEARCH_READ
Search and read in one call.
```json
{
  "operation": "SEARCH_READ",
  "model": "sale.order",
  "domain": [["state", "=", "sale"]],
  "fields": ["name", "amount_total", "partner_id"],
  "limit": 10,
  "order": "create_date DESC"
}
```
Returns: `{success: true, records: [...]}`

### SEARCH_COUNT
Count matching records.
```json
{
  "operation": "SEARCH_COUNT",
  "model": "sale.order",
  "domain": [["state", "=", "draft"]]
}
```
Returns: `{success: true, count: 42}`

### CALL_METHOD
Call any model method.
```json
{
  "operation": "CALL_METHOD",
  "model": "sale.order",
  "methodName": "action_confirm",
  "recordIds": [123]
}
```
Returns: `{success: true, methodResult: ...}`

## Domain Filter Syntax

Odoo domains use Polish notation: `[["field", "operator", value], ...]`

**Operators:**
- `=`, `!=`, `>`, `<`, `>=`, `<=`
- `like`, `ilike` (case-insensitive like)
- `in`, `not in`
- `child_of`, `parent_of`

**Examples:**
```json
[["is_company", "=", true]]
[["name", "ilike", "%deco%"]]
[["state", "in", ["draft", "sent"]]]
[["create_date", ">", "2024-01-01"]]
["&", ["is_company", "=", true], ["country_id.code", "=", "US"]]
```

## Error Handling

BPMN Error Codes for error boundary events:
- `ODOO_AUTH_ERROR` - Invalid API key (401)
- `ODOO_PERMISSION_ERROR` - Access denied (403)
- `ODOO_NOT_FOUND` - Record not found (404)
- `ODOO_VALIDATION_ERROR` - Invalid input (400/422)
- `ODOO_CONNECTION_ERROR` - Network error
- `ODOO_ERROR` - Other errors

## Deployment

1. Copy JAR to Connectors runtime classpath
2. Upload `element-templates/odoo-outbound-connector.json` to Web Modeler
3. Configure secrets in Camunda Console or environment variables

## Notes

- **API Access**: Requires Odoo Custom pricing plan
- **Rate Limiting**: Built-in retry for 429/5xx responses
- **Timeouts**: 30s connect, 60s read/write
