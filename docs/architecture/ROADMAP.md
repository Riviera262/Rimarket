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
- [ ] upload test through real Variant
- [x] delete unit test
- [x] cover selection through reorder unit test
- [x] reorder unit test
- [x] replace/compensation unit test
- [x] Product detail mapping
- [ ] apply manual migrations and run PostgreSQL concurrency/Cloudinary HTTP checks
- [x] prepare Catalog-owned outbox table migration
- [ ] define and emit Catalog integration events after Inventory's consumer contract exists

---

## Next

### Inventory

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
