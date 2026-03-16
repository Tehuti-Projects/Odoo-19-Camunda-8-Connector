# MAU Odoo Outbound Connector

## Scope

This connector is now MAU bridge-first.

### Allowed critical write operations

- `TRIP_UPSERT`
- `VENDOR_OFFER_UPSERT`
- `VENDOR_OFFER_STATUS`
- `READINESS_UPDATE`
- `TRIP_FINAL_PACKET`

These operations:

- force `source_system = camunda`
- derive the frozen versioned `event_type`
- require `external_ref`
- build the MAU `correlation` block with canonical identifiers
- send requests to MAU-approved bridge endpoints using `X-MAU-API-Key`

### Allowed direct Odoo operations

Only read-only JSON-2 access remains available:

- `READ`
- `SEARCH`
- `SEARCH_READ`
- `SEARCH_COUNT`

Direct reads require:

- Odoo JSON-2 credentials
- an explicit `direct_access_purpose`

### Forbidden operations

The connector rejects:

- `CREATE`
- `UPDATE`
- `DELETE`
- `CALL_METHOD`

Those operations bypass the MAU bridge boundary and are not valid for production MAU workflows.

## Authentication

### Bridge commands

- `authentication.url`
- `authentication.bridgeApiKey`

### Direct read-only JSON-2 access

- `authentication.url`
- `authentication.database`
- `authentication.apiKey`

## Output

The connector returns a normalized result object with:

- `mode`
- `operation`
- `success`
- `status`
- `endpoint`
- `eventType`
- `externalRef`
- `correlation`
- `result`
- `errors`
- `errorClass`
- `retryable`
- `requiresReconciliation`
- `httpStatus`

## MAU notes

- duplicate bridge responses remain safe no-op outcomes
- timeout results are marked retryable and `requiresReconciliation=true`
- conflict, validation, unauthorized, forbidden, and unsupported-state-transition are terminal
