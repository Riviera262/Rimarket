# Create a variant with initial stock

## Why an orchestrator?

The admin form needs variant and initial stock to succeed or fail together. Waiting
for an asynchronous Catalog event cannot guarantee Inventory is ready when the form
returns. `application.variant.CreateVariantWithStockService` is an application-level
coordinator, not another domain module, a broker or a Saga.

Read its `create` method first. It claims/replays a workflow request, then calls two public module interfaces:

1. `CatalogVariantCommands.addVariant`: mutate Product and flush to obtain the variant
   ID. Existing Catalog rules remain here; it no longer writes a Catalog event.
2. `InventoryItemCommands.createItemWithStock`: look up that variant through
   CatalogVariantLookup, create a zero-stock item, then receive the initial quantity
   if greater than zero. Inventory owns quantity rules, movement and stock-change outbox.

All calls are synchronous Java calls between Spring beans. No HTTP, scheduler,
Cloudinary operation, or event delivery is involved in completing this form.

## What changed in createItem?

The existing `InventoryService.createItem(variantId)` still initializes zero stock.
Its body now delegates to a private `initializeItem` helper, which validates the ID,
rejects duplicate items, reads the Catalog snapshot, creates the item and saves it.

The new `createItemWithStock` reuses that helper. For a positive quantity it calls
the existing `changeStock(RECEIVE, ...)`, which updates the managed item, saves an
audit movement and writes Inventory outbox. For zero it skips those writes. Negative
quantity is rejected. An existing item is never reset or silently topped up by create.

The returned Inventory DTO is mapped after the receipt flush, so it contains the final
quantity, version and timestamps, not the intermediate zero-stock response. The snapshot
lookup can see the new Catalog variant because both modules share the same transaction.
It adds one query but keeps the existing initialization logic and trust boundary intact.

## Transaction boundary

The orchestrator's `@Transactional(isolation = READ_COMMITTED)` opens the outer transaction. Catalog and Inventory
commands use REQUIRED, joining it through their Spring proxies and the same transaction
manager. The private initializeItem/changeStock
helpers do not need annotations; the public Inventory entry point already has a transaction.

An error propagates to the caller and rolls back variant creation, Product activation/
version changes, Inventory item, movement, Inventory outbox and creation request together.
Flush sends SQL and assigns IDs; it is not a separate commit. There are no catches that
hide failures and no REQUIRES_NEW boundaries. This design assumes the current single-DB
modular monolith; it is not a distributed transaction design.

## API for the combined form

`POST /api/management/products/{productId}/variants`

Requires ADMIN or STAFF, as do the existing management endpoints. Actor ID/label come
from the authenticated principal, never from form fields.

JWT requests use `AuthenticatedUser(userId, email)` as the principal. `SecurityUser`
remains the UserDetails used during login; it is not reconstructed from a JWT or
loaded from the database on every request. The JWT filter verifies the token and
reads its userId, subject and roles before creating authentication. A valid signature
alone is insufficient when userId is missing or invalid: protected requests must
remain unauthenticated rather than create a movement with a missing actor.

After this change, obtain a fresh access token by logging in again (or refreshing).
Older tokens without userId no longer authenticate. The existing filter prioritizes
the accessToken cookie over the Bearer header, so clear/update an old cookie when
testing a new Bearer token in Postman.

```json
{
  "name": "Black / M",
  "price": 150000,
  "initialQuantity": 10,
  "operationId": "019a0000-0000-7000-8000-000000000001"
}
```

The response is HTTP 201 with `variant` (the existing Catalog response) and `inventory`
(the final InventoryItemResponse). All request fields are required; initialQuantity
must be an integer >= 0. A positive quantity creates one RECEIVE with the authenticated
actor and the reason `Initial stock at variant creation`.

As of 2026-10-18 this is the only HTTP variant creation route. Its handler is the
application controller, not the Catalog controller. There is no /with-stock alias and
no POST /api/management/inventory/items. The internal createItem method remains for
controlled initialization/testing, not as a second HTTP form. Catalog's controller
still owns update, activate, deactivate and reorder. Its create request DTO is used
only behind the module command interface, not by a Catalog-only POST handler.

Clients must now send initialQuantity (0 is valid) and operationId, and consume the
nested variant/inventory response instead of the old flat Catalog response. On 2026-10-19,
workflow idempotency storage and a unique variant-name constraint were added. Apply
`docs/database/2026-10-19-variant-creation-idempotency.sql` manually to an existing DB
before using the new code. `init.sql` has the definitions for a fresh DB; editing it
does not migrate an existing DB. Catalog outbox storage/data remain untouched.

## Request-level idempotency — 2026-10-19

Previously `(variant_id, operation_id)` only protected an Inventory movement. A repeated
create could allocate a NEW variant ID, making that movement constraint irrelevant.
Zero-stock creation did not even create a movement. The same `operationId` in the
existing request now ALSO protects the whole form; no new header/FE field is required.

`application.variant_creation_requests` belongs to the application workflow, not either
domain aggregate. Its primary key is `(actor_id, operation_id)`; this table itself
scopes keys to the create-variant operation. Product ID is part of the fingerprint,
so reusing a key for a different product by the same user conflicts. Different users
have separate keys and never receive each other's stored responses. Business name
uniqueness still applies across users. There are no cross-module entity relationships.

Read these three files in order:

1. `CreateVariantWithStockService`: claim/replay, call Catalog + Inventory, store result.
2. `VariantCreationFingerprint`: SHA-256 of version + productId + trimmed name + numeric
   price + initial quantity. Length-prefixed UTF-8 fields avoid delimiter ambiguity.
   10 and 10.00 are equivalent; actor email is not hashed because it can change.
3. `VariantCreationRequestStore`: PostgreSQL claim and response persistence. MANDATORY
   prevents accidental commits outside the orchestration transaction. JdbcTemplate
   must use the SAME DataSource as the existing JpaTransactionManager/JPA repositories.

The database interaction is intentionally not `find -> if absent -> save`:

1. `INSERT ... ON CONFLICT DO NOTHING` claims a new key before business writes.
2. If inserted: create variant/item/optional movement+outbox, save response JSON, commit all.
3. If duplicate: compare hash and return the saved response, or throw COMMON_008 / 409.
4. Concurrent duplicates wait on PostgreSQL's unique-index conflict. If the winner
   commits, the next SELECT at READ COMMITTED sees its result. If it rolls back, the
   waiting INSERT can claim the key and execute. No REQUIRES_NEW, distributed lock
   or independent IN_PROGRESS transaction is needed to coordinate concurrent calls.

The claim row has a null response only INSIDE the uncommitted owning transaction.
Failure, including failure to serialize the response or a constraint error at commit,
rolls back that row as well. No permanent failed response is cached. Do not catch a
business exception and commit the claim. A manually corrupted committed null response
fails closed; it must never trigger another create.

| Same authenticated user | Result |
| --- | --- |
| New operationId, valid new variant name | Create and return 201 |
| Same operationId + same effective payload | Original response and 201; no business writes |
| Same operationId + changed product/name/price/quantity | 409 COMMON_008 |
| New operationId + existing variant name in same product | 409 Catalog duplicate-name error |
| First attempt rolled back, retry same operationId | Fresh attempt; no committed receipt existed |

The response is the ORIGINAL creation snapshot, not current stock. If stock later goes
from 10 to 8, retry still returns the original creation response of 10; GET detail shows 8.
This is intentional and avoids treating a retry as another receipt. Replays still pass
current authentication/role checks at the HTTP boundary.

Client rule: generate one UUID per intended form submission and retain it for retries,
including timeout/reload recovery. Generate a new key for a new intended operation.
Never generate a fresh UUID on every network retry. A claimed key expires 24 hours after
its first claim; replay does not extend the expiry. After expiry, the same key can represent
a new request. The cleanup job starts after one hour and removes at most 1,000 expired
rows per run, so physical deletion normally follows logical expiry within about an hour;
it can take longer while cleanup catches up with a backlog.
Stored response schema changes need backward-compatible deserialization or a migration.
Existing variants created BEFORE rollout have no request receipts; this change cannot
retroactively replay them. Keep the name constraint as an independent safety net.

READ_COMMITTED is explicit for this entry point, not a global isolation change. If a
future caller wraps it in an existing transaction, that caller must use compatible
isolation: Spring's REQUIRED propagation joins the existing transaction. Database lock
timeouts/deadlocks still require retrying the SAME key after rollback; do not spin inside
an aborted transaction. This is scoped idempotency, not universal exactly-once delivery.

## Variant name rule

Product checks names on both add and rename. Names are trimmed using the existing
Catalog normalization and compared case-sensitively, including inactive variants.
Keeping your own name on update is allowed; another product can reuse the name.
`UNIQUE(product_id, name)` is the final concurrent-writer boundary; optimistic Product
locking remains in place. A race may surface the existing generic 409 conflict rather
than the friendly CATALOG_208 pre-check. The migration refuses existing duplicates;
it never deletes/merges/renames user data. Case-insensitive matching, accent folding
and whitespace collapsing are deliberately NOT introduced by this change.

## Important limits

- Request-level idempotency applies to this creation workflow, not every API. Inventory
  receipts/adjustments retain their separate `(variant_id, operation_id)` protection.
- Variant creation no longer emits Catalog events. Historical Catalog outbox rows must
  not be blindly replayed to recreate items or receive stock. See CATALOG_OUTBOX.md.
- Inventory stock-change events remain pending; its publisher is not implemented.
  Uploading images remains a separate follow-up API after this request returns variant.id.
  On upload failure, retry only upload; do not submit the create request again.

## Verification

Unit coverage checks the orchestration order, forwarding quantity/actor/IDs, zero quantity,
failure propagation and request validation. The existing Inventory integration suite now
includes positive/zero initial stock, Inventory outbox with no new Catalog events, complete
rollback and preservation of internal item creation. Routing tests load both controllers
to check the single creation route, rejection of the old payload/alias, and Catalog updates.
The controller tests also exercise real JWT signing/verification, the JWT filter and
Spring's principal resolver for create, receive and adjust. They cover cookie/Bearer
authentication and rejection of missing IDs, malformed IDs, expired tokens, invalid
signatures and absent authentication. Keys are generated only in memory. These are
standalone MVC tests, not a test of the full SecurityFilterChain or role enforcement.
The full SpringBoot Inventory integration suite must only run against a disposable DB
initialized from init.sql. Do not run it against the developer's normal DB/config.

`VariantCreationRequestStorePostgresTest` is a separate opt-in PostgreSQL test that
does not load application config or credentials. It verifies the manual migration,
response JSON round-trip, MANDATORY boundary, mismatch, actor scope, rollback and actual
concurrent lock waits on commit/rollback. It tests the store with a JDBC transaction
manager, not the complete JPA/application workflow. For a disposable localhost PostgreSQL
database named rimarket_idempotency_test, run the test with:

```powershell
.\mvnw.cmd -o org.apache.maven.plugins:maven-compiler-plugin:3.14.1:testCompile@default-testCompile org.apache.maven.plugins:maven-surefire-plugin:3.5.5:test "-Dtest=VariantCreationRequestStorePostgresTest" "-Drimarket.idempotency-test-url=jdbc:postgresql://127.0.0.1:<temporary-port>/rimarket_idempotency_test" "-DargLine=-Duser.timezone=UTC"
```

This test uses local throwaway trust authentication, never shared/production credentials.
UTC is scoped to the test JVM to avoid host timezone aliases unsupported by that DB image.
