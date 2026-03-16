# MAU Connector Alignment Notes

## What changed

- critical outbound writes are now routed through MAU bridge endpoints only
- direct generic mutation operations were removed from the default connector execution path
- direct Odoo JSON-2 access is limited to read-only operations
- inbound webhook handling now expects MAU event envelopes from Odoo
- generic Odoo model polling is disabled by default and marked compatibility-only

## Why

The older connector was production-grade in a generic sense, but not MAU-safe:

- it could mutate arbitrary Odoo models directly
- it exposed arbitrary method execution
- it accepted generic Odoo model change semantics instead of MAU business event contracts
- it used generic variable names like `odooModel` and `odooRecordId` as first-class process inputs
- its polling path could introduce hidden orchestration drift

## Remaining legacy debt

- legacy inbound polling classes remain for compatibility review scenarios only
- legacy polling still emits generic helper variables such as `odooModel` and `odooRecordId`, but only when compatibility mode is explicitly enabled
- direct JSON-2 access still exists for read-only diagnostics and must stay non-authoritative

## UNKNOWN

- final long-term Odoo -> Camunda transport for MAU outbound facts
- whether legacy polling will ever be approved for any production recovery scenario
- final connector deployment secret names in the future Camunda runtime repo
