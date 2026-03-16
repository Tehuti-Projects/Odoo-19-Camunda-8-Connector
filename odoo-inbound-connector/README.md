# MAU Odoo Inbound Connector

## Scope

This module is MAU event-envelope-first.

### Approved production path

- Odoo sends approved MAU outbound fact events to the webhook connector.
- The webhook payload must use the frozen MAU envelope:
  - `event_name`
  - `event_type`
  - `source_system = odoo`
  - `external_ref`
  - `payload`
- The webhook only accepts the approved MAU Odoo outbound event set:
  - `odoo.commercial_approval.recorded.v1`
  - `odoo.deposit_paid.v1`
  - `odoo.vendor_publication.updated.v1`
  - `odoo.readiness_exception.resolved.v1`
  - `odoo.support_ticket.opened.v1`
  - `odoo.final_packet.generated.v1`

### Legacy compatibility path

- Polling remains in the repo only for controlled compatibility or recovery scenarios.
- Polling is disabled by default.
- Enabling polling requires:
  - `compatibilityModeEnabled = true`
  - a non-empty `compatibilityReason`
- Legacy polling must not be treated as normal MAU orchestration input.

## Webhook variables

Successful MAU webhook correlation exposes the canonical event spine:

- `source_system`
- `event_name`
- `event_type`
- `source_event_name`
- `external_ref`
- `occurred_at`
- `correlation_id`
- `actor_type`
- `external_subject`
- `camunda_business_key`
- `camunda_process_instance_key`
- `reference_code`
- `component_external_ref`
- `external_offer_ref`
- `payload`
- `raw_event`

## Authentication

### Webhook

- `secretToken`

### Legacy polling

- `url`
- `database`
- `apiKey`

## MAU notes

- use `external_ref` as the message-id spine for webhook deduplication
- do not promote raw Odoo model-change payloads into MAU production contracts
- do not treat polling as a substitute for Odoo outbound business events
- do not use inbound connector variables to move workflow authority out of Camunda
