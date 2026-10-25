# Catalog outbox status — 2026-10-18

The VariantCreated writer example was retired when variant creation became a single
synchronous workflow. The former event record, writer, repository and entity mapping
were removed from Java code; addVariant no longer writes Catalog outbox.

The only HTTP create endpoint is now
POST /api/management/products/{productId}/variants, handled by the application orchestrator.
It creates the Catalog variant and Inventory item together; initial quantity > 0 also
creates a RECEIVE movement and an Inventory stock-changed outbox event.

See [the current workflow](VARIANT_STOCK_WORKFLOW.md). There is no Catalog publisher,
consumer or scheduler to enable for Inventory initialization.

## Preserved storage and historical events

The catalog.outbox_events table remains in init.sql and the manual migration. No table
or existing event row was dropped, deleted, marked published or replayed by this refactor.
The Java application no longer maps or writes that table. Inventory outbox is unchanged.

If earlier runs created pending catalog.variant-created rows, leave them untouched until
there is an explicit archival or migration decision. Do not later enable a consumer that
blindly recreates items or repeats receipts from these historical notifications.

A Catalog outbox can be reintroduced when a concrete integration consumer needs it;
synchronous initialization does not prohibit events, but it does not require a duplicate
asynchronous creation path. Request-level idempotency is still separate work.
