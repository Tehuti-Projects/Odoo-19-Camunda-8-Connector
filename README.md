# MAU-Curated Camunda Odoo Connector

This repository started as a generic Odoo 19 and Camunda 8 connector. It has now been refined for MAU Tourism architecture.

## MAU Position

- Camunda is orchestration authority.
- Odoo is synchronized ERP/commercial/ops truth.
- Critical Camunda -> Odoo writes must go through the MAU bridge, not arbitrary model CRUD.
- Odoo -> Camunda inbound events must use the frozen MAU event envelope.
- Generic Odoo model polling is not an authoritative MAU integration pattern.

## Current Safe Scope

### Outbound

The outbound connector now supports:

- MAU bridge commands only for critical writes:
  - `TRIP_UPSERT`
  - `VENDOR_OFFER_UPSERT`
  - `VENDOR_OFFER_STATUS`
  - `READINESS_UPDATE`
  - `TRIP_FINAL_PACKET`
- direct Odoo JSON-2 read-only operations for diagnostic or explicitly approved non-critical reads:
  - `READ`
  - `SEARCH`
  - `SEARCH_READ`
  - `SEARCH_COUNT`

The connector no longer permits direct `CREATE`, `UPDATE`, `DELETE`, or arbitrary `CALL_METHOD` operations in the MAU runtime path.

### Inbound

- webhook intake now expects the frozen MAU envelope for approved Odoo outbound events only
- generic raw Odoo model-change payloads are rejected by default
- polling remains in the repo only as legacy compatibility code and is disabled by default unless explicitly enabled with a documented compatibility reason

## Key Files

- `odoo-outbound-connector/src/main/java/io/camunda/connector/odoo/MauBridgeOperation.java`
- `odoo-outbound-connector/src/main/java/io/camunda/connector/odoo/MauBridgeClient.java`
- `odoo-outbound-connector/src/main/java/io/camunda/connector/odoo/dto/MauConnectorResult.java`
- `odoo-inbound-connector/src/main/java/io/camunda/connector/odoo/inbound/MauOdooEventEnvelope.java`
- `odoo-outbound-connector/element-templates/odoo-outbound-connector.json`
- `odoo-inbound-connector/element-templates/*`

## MAU Notes

- use the MAU Delivery Library as the contract source of truth
- do not re-open generic CRUD access for convenience
- do not treat legacy polling as a normal production path
- treat any remaining generic capabilities as legacy debt, not architecture
