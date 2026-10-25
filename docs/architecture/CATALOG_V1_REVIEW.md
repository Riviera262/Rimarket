# Catalog V1 review — updated 2026-10-13

## Current contract

- Product is the aggregate root for ProductVariant and VariantImage.
- ProductRepository owns SKU checks and both fetch queries. No child CRUD repository.
- Mutations of children go through Product and package-private child methods.
- Products may belong only to level-2 or level-3 Categories; root Categories cannot
  contain Products directly, and a populated Category cannot be moved to root.
- New Product is inactive; adding its first active Variant activates it. Later additions
  or activation of an existing Variant do not republish an intentionally inactive Product.
- Each Variant may have zero to eight images. The first image by sortOrder is its cover.
- Image upload has one endpoint: POST /api/management/products/{productId}/variants/{variantId}/images.
  Send one or more multipart files under the repeated field `files`; response is always
  a JSON array. The former `file` upload contract and /batch route are removed.
  A request preserves file order and attaches all DB metadata in one transaction.
  Limits: 5 MiB per file, 45 MiB per HTTP request, eight images total per Variant.
- Image response: id, url, sortOrder. Storage publicId stays server-side.
- StoredImage(publicId, url) is the storage port result. VariantImageReplacementResult
  lives in service/result and carries internal cleanup information between services.
- New Category, Product, ProductVariant and VariantImage IDs use Hibernate's
  `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`. Existing UUIDs stay unchanged.
  UUIDv7 is still 128 bits / 36 characters in canonical text; it adds time ordering,
  not shorter identifiers. Image display order remains sortOrder.

## Transactions and concurrency

Product child commands fetch Product + variants without a query lock, then call
EntityManager.lock(product, OPTIMISTIC_FORCE_INCREMENT) on the managed root instance.
Image commands initialize images with one additional query. Keeping the lock off the
fetch query prevents Hibernate from trying to lock unversioned ProductVariant entities.
A separate lock on each Variant is not required while every child writer participates
in the Product version protocol. Conflicting transactions cannot both commit stale
aggregate decisions. Changes to different Variants of one Product can therefore
conflict as well; this is an intentional aggregate-level trade-off.

Product activate/deactivate also force a version check, including no-op requests.
Category create/move/delete use SERIALIZABLE: row versions alone do not protect
hierarchy decisions spanning different rows (e.g. concurrent A-under-B and B-under-A).
Serialization/optimistic conflicts return HTTP 409; retry the whole request with fresh
data. No automatic retry surrounds external storage calls. All future hierarchy
writers must follow the same isolation protocol.

VariantImageService uses propagation NEVER to reject an ambient transaction.
VariantImageCommandService owns short DB transactions. This guarantees, for the
current controller flow, that the command's commit completes before cloud cleanup.

Upload: precheck -> new cloud asset -> attach in DB -> compensate new asset on failure.
Batch upload validates every file first, prechecks the total capacity, uploads files
sequentially, then attaches all metadata in one DB transaction. The image limit is
rechecked under the Product root lock. If an upload or DB attach fails, every known
new asset is cleaned up in reverse order.
Replace: precheck -> new asset -> replace DB metadata -> cleanup previous asset.
Delete: remove in DB -> cleanup cloud asset. Reorder only changes DB state.

The cover follows order. Replace preserves image ID/order; deleting the cover promotes
the next image, and deleting the last image leaves an empty collection.

## Database rollout (manual)

There is no migration runner. Keep SQL scripts as history; do not delete after use.
Back up the database and pause Catalog writes while applying migrations in order:

1. 20260928_add_catalog_versions.sql (if not applied).
2. 20260930_add_variant_sort_order.sql (if not applied).
3. 20261003_add_variant_image_order_constraint.sql (if not applied).
4. 20261005_use_first_image_as_cover.sql.
5. 20261009_create_catalog_outbox.sql.
6. 20261013_catalog_application_generated_uuidv7.sql.

The fourth script moves legacy primary images to the front before removing is_primary.
It uses a single transaction and a table lock. The fifth script creates only the
Catalog-owned outbox table and indexes; it does not create event writers or a publisher.
The new init.sql is for fresh databases; never rerun the full initialization against
a populated database.

The sixth script removes Catalog's legacy UUIDv4 defaults. Hibernate supplies UUIDv7
for mapped Catalog entities; direct SQL inserts must supply IDs explicitly, including
the future outbox writer. PostgreSQL 17 can store UUIDv7 without a schema type change.
No existing rows or foreign keys are rewritten. Identity and other modules retain
their current ID policy. Migration files currently reside in a gitignored directory
(`docs/database/`); retain them separately or deliberately track them for rollout.

The scripts were prepared, not applied to the user's database by this review.

## Verification scope

`mvnw.cmd test` passed on 2026-10-13: 87 tests, zero failures/errors/skips, including
the application-context smoke test, standalone multipart MVC binding tests for one
and multiple files, and transactional PostgreSQL repository tests. These verify the
Product root-lock sequence and generated UUIDv7 IDs for all four Catalog entity types,
including cascading image persistence and ID preservation on reload. No real
Cloudinary operation was executed; the new SQL migration was not applied.

Unit tests cover domain invariants, auto-activation, reorder validation, ownership,
file validation, batch request ordering, atomic batch metadata attachment, image limits
rechecked at attach time, and cleanup/compensation.
Command tests verify use of the root locking query; annotation contract tests verify
the intended lock/isolation configuration. Spring-proxy tests use a fake transaction
manager to simulate commit failure and to reject an ambient transaction.
Cloudinary adapter tests use mocks only.

This does not yet include two truly concurrent PostgreSQL transactions. The repository
test proves the Hibernate root-lock/fetch sequence, but does not prove racing transaction
behavior, SQL migration correctness, or end-to-end Cloudinary operation.

## Remaining operational checks / limitations

- Run the migrations manually, then test single/batch upload, replace, reorder and delete through HTTP
  with a disposable Variant and real Cloudinary assets.
- Test two transactions concurrently: last-active-Variant deactivation, image limit,
  reorder/replace conflict, and opposing Category moves. Expect at least one conflict
  when stale decisions would break invariants; reload and retry.
- External storage and PostgreSQL are not one atomic transaction. Cleanup failure,
  process crash, or ambiguous network commit outcomes can leave inconsistency.
  Durable cleanup/reconciliation remains future work, not solved by unit tests.
- Delete cleanup failure currently returns FILE_DELETE_FAILED after the DB deletion
  has committed. Retrying by imageId cannot recover the removed publicId; inspect logs
  and reconcile storage until durable cleanup is implemented.
- Version fields protect overlapping DB transactions, not stale browser forms submitted
  later. A future version/If-Match request contract would be needed for that case.
- MIME validation currently trusts the multipart content type; it is not byte-level
  image verification. Add a documented file-type policy before accepting arbitrary
  untrusted uploads beyond the current staff/admin workflow.
- Two-query reads at READ_COMMITTED need not represent a single snapshot under writes;
  the version check protects commands, not a historical snapshot for storefront reads.

## Next learning task

Start InventoryItem's domain model and quantity invariants. Keep UUID variantId,
without a cross-module JPA relationship. Decide quantity semantics before choosing
reservation APIs. Update 2026-10-18: variant creation now has one HTTP endpoint through
the application orchestrator, creating its Inventory item synchronously. The temporary
VariantCreated writer is removed, while Catalog outbox storage is preserved.
See [workflow](VARIANT_STOCK_WORKFLOW.md) and [outbox status](CATALOG_OUTBOX.md).

References: [PostgreSQL transaction isolation](https://www.postgresql.org/docs/17/transaction-iso.html),
[Cloudinary Upload API](https://cloudinary.com/documentation/image_upload_api_reference).
