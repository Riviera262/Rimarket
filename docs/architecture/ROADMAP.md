# RiMarket Roadmap

## Completed

### Identity Foundation

- User/Role foundation
- email verification
- JWT authentication
- Redis refresh-token storage

### Catalog — Category

- Category hierarchy
- maximum depth
- cycle protection
- sibling uniqueness
- delete guards
- tree mapping
- fetch strategy

### Catalog — Product

- Product aggregate root
- slug generation
- lifecycle
- public/storefront reads
- management writes
- Product aggregate optimistic locking

### Catalog — ProductVariant

- SKU generation
- VND price validation
- aggregate-controlled mutation
- activate/deactivate behavior
- explicit variant reordering

### Infrastructure

- Cloudinary configuration
- ImageStorage port
- CloudinaryImageStorage adapter
- test upload
- test delete

---

## In Progress

### VariantImage

- [x] architecture
- [x] entity design
- [x] sortOrder design
- [x] first image by sortOrder is the cover
- [x] finalize service
- [x] management controller
- [x] replace image while preserving image ID/order
- [x] batch upload with all-or-nothing DB attachment and best-effort storage compensation
- [x] one list-based upload endpoint for both one and multiple files
- [x] Hibernate UUIDv7 generation for new Catalog entity IDs, preserving legacy IDs
- [ ] upload test through real Variant
- [x] delete unit test
- [x] cover selection through reorder unit test
- [x] reorder unit test
- [x] replace/compensation unit test
- [x] Product detail mapping
- [ ] apply manual migrations and run PostgreSQL concurrency/Cloudinary HTTP checks
- [x] prepare Catalog-owned outbox table migration
- [x] retire VariantCreated writer example in favor of one synchronous create workflow
- [ ] implement Catalog event integration only when a concrete consumer needs it
- [ ] define remaining Catalog integration events when their consumers need them

---

## Next

### Inventory

Implemented: item/movement/outbox entity mappings, Catalog public lookup, management
create/read/receive/adjust/history APIs, operation deduplication, optimistic locking,
and transactional outgoing stock-change event writes. Manual table-creation SQL is
prepared; existing stock backfill, reservations, Catalog consumers and publisher remain pending.
See [Inventory schema](INVENTORY_SCHEMA.md) for quantity semantics and constraints.

The admin form uses the single POST /products/{productId}/variants for synchronous,
transactional variant creation plus initial stock; no alternate Catalog-only create route.
See [workflow](VARIANT_STOCK_WORKFLOW.md).
Request-wide creation idempotency now uses (actor_id, operationId), with a saved response
and atomic claim in the workflow transaction. Keys expire after 24 hours; an hourly
bounded cleanup removes expired rows. Receipt operationId still deduplicates within a
variant. Apply the manual migration and verify the full workflow on a disposable
application DB before rollout; see the workflow document for test scope.

Goals:

- quantity
- reserved quantity
- optimistic locking
- concurrency handling
- stock adjustment history

Important rule:

Inventory references variantId.

Do not create cross-module JPA relationships to Catalog.

---

## Later

Cart

Order / Sales

Payment

After real cross-module reliability requirements appear:

- integration events
- transactional outbox writers/publisher (Catalog table schema is already prepared)
- idempotency
- payment webhooks
- Saga only if justified
