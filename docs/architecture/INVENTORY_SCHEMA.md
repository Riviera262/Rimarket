# Inventory V1 schema — 2026-10-14

The Inventory section of `init.sql` prepares three tables for a fresh database.
Entity mappings, management APIs, receipt/adjustment commands and an outgoing event
writer are implemented. Item creation now uses synchronous application orchestration;
Inventory outbox publication remains pending.
V1 assumes one warehouse and integer units; multi-warehouse stock needs a later design.

## Table responsibilities

| Table | Responsibility |
| --- | --- |
| `inventory.items` | Current quantities and Catalog description snapshots, one row per Variant |
| `inventory.movements` | Audit of a quantity-changing operation, including actor and before/after values |
| `inventory.outbox_events` | Outgoing Inventory events awaiting publication |

`items.variant_id` reuses Catalog's existing ID. New movement, operation and event IDs
are supplied by the application using UUIDv7. There is no database UUID default.
Catalog images are read synchronously through its public API, not stored in Inventory.
A per-order reservation table and event-driven image replication remain future work.

## Quantities and effects

- `on_hand_qty`: units physically in stock.
- `reserved_qty`: units allocated but not yet shipped.
- Available units = `on_hand_qty - reserved_qty`, calculated rather than independently stored.
- Both quantities must be nonnegative, with `reserved_qty <= on_hand_qty`.

Each movement stores `on_hand_before/after/delta` and `reserved_before/after/delta`.
A positive delta adds to that quantity; a negative delta subtracts. Do not infer
the affected quantity from a single ambiguous `delta` column.

| Type | on_hand_delta | reserved_delta | Meaning |
| --- | --- | --- | --- |
| RECEIVE | Positive | Zero | Receive physical stock |
| ADJUSTMENT | Nonzero | Zero | Correct physical stock; a reason is mandatory |
| RESERVE | Zero | Positive | Allocate available stock |
| RELEASE | Zero | Negative | Release an existing allocation |
| SHIP | Negative | Equal to on_hand_delta | Ship previously reserved units |

For example, shipping 3 reserved units from `(on_hand=10, reserved=4)` produces
`(7, 1)` and deltas `(-3, -3)`. Available stock stays 6.
All types reject no-op movements. A physical loss that would put stock below existing
reservations must be handled by an explicit shortage/reallocation policy later;
an adjustment must not silently cancel reservations.

## Actor, reference and retry

`actor_type` is USER or SYSTEM. USER requires `actor_id` from the authenticated
principal; SYSTEM requires a null user ID. `actor_label` keeps a display-name or
job-name snapshot. Do not accept arbitrary actor identity from a request body.
No FK to Identity is used, so audit history survives account lifecycle changes.

`reference_type` and `reference_id` must either both be absent or both be present.
They can identify a receipt, order, or other business document; they are not a
substitute for a reservation record or for validating its ownership.

`UNIQUE(variant_id, operation_id)` rejects duplicate processing of the same item
operation. Reuse the operation ID on retries; a new ID defeats deduplication.
One operation may affect multiple items. Distinct steps such as reserve and ship
must have distinct operation IDs even if they refer to the same order.
The future service must compare the command to the prior result on duplicate keys;
uniqueness alone does not detect a reused key with a different requested quantity.

## Database versus application rules

Database constraints check row-local quantities, delta arithmetic, type-specific
effects, required actor/reference fields and uniqueness. Movement -> item uses a
local FK with DELETE RESTRICT to preserve audit records.

The application loads the item, enforces the domain operation, updates its quantities,
inserts a movement and an outgoing event in one transaction. InventoryItem maps `version`
to JPA `@Version` using a nullable Long so new entities with an assigned Variant ID
are recognized as new by Spring Data. Entity callbacks maintain `updated_at`.

CHECK constraints cannot prove that movement.before matched the item, enforce the
continuity of separate movement rows, require every item update to have a movement,
or prove that released stock belongs to the requested order. Those are command-flow
and reservation-model responsibilities. Movement rows are append-only by application
policy; this DDL does not prevent a privileged SQL client from changing them.

The outbox follows Catalog's current format: payload JSON, contract version, retry
count/time and publication timestamp. It does not replace audit history, ensure event
ordering, or deduplicate incoming Catalog events. Incoming event handling needs its
own idempotency mechanism when that consumer is implemented.

## Rollout and verification

The old fresh-install table `inventory.inventory(available_qty, reserved_qty, version)`
is replaced with `inventory.items`, plus movements and outbox. Its cross-module FK
to Catalog is removed in favor of event-populated UUID references. The consumer must
validate events and handle Catalog deactivation/deletion without deleting stock history.

Do not rerun `init.sql` on an existing database. A data migration must first establish
what old `available_qty` means: if it meant free-to-sell units, then
`on_hand_qty = available_qty + reserved_qty`; if it meant physical stock, use that
value directly. Product/SKU snapshots also need backfilling. No data migration was
applied as part of this change. `docs/database/20261014_create_inventory_foundation.sql`
creates the three new tables on an existing database if they are not already present.
It leaves the old table/data untouched and does not backfill them. Apply only once;
skip it if the tables were already created from the updated init.sql. The application
uses schema validation and requires these tables before startup. Migration files are
currently gitignored; preserve or deliberately track the script for deployment.

The exact Inventory DDL was executed on PostgreSQL 17.10 in a uniquely named scratch
schema and rolled back. Seven valid movements and an outbox event were accepted;
27 invalid writes were rejected by their expected named constraints. This verifies
DDL and selected integrity rules, not concurrent application transactions or delivery.

The subsequent entity/API implementation passed all 97 project tests on a disposable
PostgreSQL 17 database initialized from init.sql. Five new domain tests cover audit
snapshots, invalid quantities, overflow, reserved-stock protection and audit context.
Five new integration tests cover Catalog lookup, assigned item IDs, UUIDv7 movement/event
IDs, JSON payload persistence, idempotent replay/conflict, atomic rollback and two
overlapping transactions with a stale writer. No real Cloudinary call was made.
Test JVM timezone was UTC because this container rejected the host's Asia/Saigon alias;
the application/user timezone configuration was not changed. Apply the schema before
running these Spring integration tests against any other database.

## Implemented management flow

Catalog exposes `CatalogVariantLookup` returning a `CatalogVariantSnapshot` record.
Inventory imports only this public module API, never Catalog entities/repositories.
The lookup includes inactive Variants because stock may be received before publication.
The initial SKU/product/variant names are snapshots, not automatically refreshed yet.

All routes below use `/api/management/inventory/items` and require ADMIN or STAFF:

| Method/path | Purpose |
| --- | --- |
| GET /?page=0&size=20 | Page of item summaries with one nullable thumbnailUrl; size is limited to 100 |
| GET /{variantId} | Current stock, version and an ordered images list (empty if no images) |
| POST /{variantId}/receipts | Receive a positive quantity |
| POST /{variantId}/adjustments | Apply nonzero signed delta with a reason |
| GET /{variantId}/movements?page=0&size=20 | Audit history, newest first |

Receipt request example (replace operationId with a new UUID for a new operation):

```json
{
  "operationId": "019a0000-0000-7000-8000-000000000001",
  "quantity": 10,
  "reason": "Initial receipt"
}
```

Adjustment replaces `quantity` with `delta` (e.g. -2) and requires `reason`.
Both accept optional `referenceType` and `referenceId` together. Actor UUID and label
come from the authenticated SecurityUser (currently its username/email is the label),
not the request body. The internal domain also supports SYSTEM actors.

Receipt/adjustment responses contain the movement, including the resulting quantities.
Retry with the same operationId AND same type/delta/reference/reason/actor returns
the original movement and does not write another event. Reusing a key with different
content returns 409. Concurrent first attempts may receive a unique/version conflict;
retry the whole request with the same key after the winning transaction commits.

The InventoryItem aggregate validates the quantity change and creates its movement.
The service persists the movement, creates `inventory.stock-changed` event version 1,
then flushes the transaction. Any failure rolls back item, movement and event together.
Outbox payload includes variantId, movementId, operationId, movementType and the six
before/after/delta quantities. Event ID is stable for future delivery retries. No worker
publishes these rows yet; they remain pending. Ordering/consumer contracts will be
reviewed before a publisher is enabled; UUID/time ordering alone is not that contract.

There is no generic quantity setter, movement editor or item delete endpoint. Reserve,
release and ship enum values mirror the SQL vocabulary but have no command API yet.
POST /api/management/products/{productId}/variants is the only variant creation route,
creating the Inventory item in the same transaction with optional initial receipt.
There is no standalone HTTP Inventory item-creation route; the internal method remains.
The temporary Catalog VariantCreated writer was removed on 2026-10-18.
See VARIANT_STOCK_WORKFLOW.md. Request-level creation idempotency was added on 2026-10-19
in application.variant_creation_requests, separate from movement deduplication. Apply
its manual migration and verify full workflow rollback before rollout. Image events
and reservations remain later tasks.

## Item reads and Catalog images — 2026-10-16

This is a synchronous Java call between Spring beans in the same application, not HTTP,
an event, or a Cloudinary SDK call. Inventory imports only `catalog.api` contracts.

- Create: the orchestrator calls `createItemWithStock`, which looks up Catalog and
  initializes zero stock, then receives a positive initial quantity. InventoryItemResponse
  is nested under inventory in the combined POST response. Creation does not fetch images.
- Detail: `getItem` -> read Inventory item -> `CatalogVariantLookup.findImages`
  for its variant ID -> `InventoryItemMapper.toDetailResponse`.
- Page: `getItems` -> page Inventory items -> one `findImages` call for all IDs in
  that page -> choose the first image per variant -> `Page.map` with
  `InventoryItemMapper.toSummaryResponse`. An empty page skips the image call.

Catalog projects imageId, variantId, URL and sortOrder, ordered by variantId,
sortOrder and imageId. No entity or Cloudinary publicId crosses the module boundary.
The smallest sortOrder supplies the thumbnail, including when there is no zero.
Detail returns all images; page summaries return only a thumbnail URL. With Catalog's
current limit of eight images per variant, the page lookup fetches all images in one
bounded batch before choosing thumbnails, not one query per item. There are no
collection fetch joins in this read path and item pagination/counts are unaffected.

Names/SKU remain the snapshot stored at item creation, while URLs reflect Catalog
at read time. This is not automatic metadata synchronization or an atomic snapshot
across multiple queries. Inventory reads currently depend on Catalog's image lookup
succeeding; errors are not silently treated as "no images". Future event-driven image
replication can replace this lookup if independent reads become a requirement.

The two GET DTOs are `InventoryItemSummaryResponse` and `InventoryItemDetailResponse`.
Their mapper methods only transform data; services perform all reads. Item timestamps
retain ZonedDateTime to match the current Catalog style; movement/outbox timestamps
are unchanged. No schema migration is needed for this read refactor.
