-- ====================================================================================
-- RIMARKET MODULAR MONOLITH SCHEMA V4
-- ====================================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS application;
CREATE SCHEMA IF NOT EXISTS cart;
CREATE SCHEMA IF NOT EXISTS sales;
CREATE SCHEMA IF NOT EXISTS payment;


-- ====================================================================================
-- 1. IDENTITY MODULE
-- ====================================================================================

CREATE TABLE identity.users (
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),

    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    is_deleted BOOLEAN NOT NULL DEFAULT 'FALSE',
    is_locked BOOLEAN NOT NULL DEFAULT 'FALSE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- PostgreSQL UNIQUE(email) is case-sensitive.
-- We normally do not want:
-- ri@gmail.com
-- RI@gmail.com
-- to become two different accounts.
CREATE UNIQUE INDEX uq_users_email_ci ON identity.users (LOWER(email));


CREATE TABLE identity.roles (
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),

    name VARCHAR(50) UNIQUE NOT NULL
);


CREATE TABLE identity.user_roles (
     user_id UUID NOT NULL
         REFERENCES identity.users(id) ON DELETE CASCADE,

     role_id UUID NOT NULL
         REFERENCES identity.roles(id) ON DELETE CASCADE,

     PRIMARY KEY (user_id, role_id)
);


CREATE TABLE identity.verification_tokens (
      id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),

      token VARCHAR(255) UNIQUE NOT NULL,

      user_id UUID NOT NULL
          REFERENCES identity.users(id) ON DELETE CASCADE,

      expiry_date TIMESTAMPTZ NOT NULL,

      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ====================================================================================
-- 2. CATALOG MODULE
-- ====================================================================================

CREATE TABLE catalog.categories (
    -- Hibernate supplies UUIDv7; direct SQL inserts must supply an id explicitly.
    id UUID PRIMARY KEY,

    version BIGINT NOT NULL DEFAULT 0,

    name VARCHAR(100) NOT NULL,

    parent_id UUID
        REFERENCES catalog.categories(id)
            ON DELETE RESTRICT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_categories_parent_id
    ON catalog.categories(parent_id);


-- Category names are unique among siblings.
--
-- Valid:
-- Electronics -> Accessories
-- Phones      -> Accessories
--
-- Invalid:
-- Electronics -> Accessories
-- Electronics -> Accessories
--
-- COALESCE makes root categories participate in the same uniqueness rule.
CREATE UNIQUE INDEX uq_categories_parent_name_ci
    ON catalog.categories (
                           COALESCE(
            parent_id,
            '00000000-0000-0000-0000-000000000000'::UUID
        ),
                           LOWER(name)
        );


CREATE TABLE catalog.products (
  id UUID PRIMARY KEY,

  version BIGINT NOT NULL DEFAULT 0,

  category_id UUID NOT NULL
      REFERENCES catalog.categories(id)
          ON DELETE RESTRICT,

  name VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL,

  description TEXT,

  is_active BOOLEAN NOT NULL DEFAULT FALSE,

  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE UNIQUE INDEX uq_products_slug_ci
    ON catalog.products(LOWER(slug));

CREATE INDEX idx_products_category_id
    ON catalog.products(category_id);


CREATE TABLE catalog.product_variants (
  id UUID PRIMARY KEY,

  product_id UUID NOT NULL
      REFERENCES catalog.products(id)
          ON DELETE CASCADE,

  sku VARCHAR(100) NOT NULL,

  name VARCHAR(255) NOT NULL,

  price NUMERIC(19,0) NOT NULL
      CHECK (price >= 0),

  sort_order INTEGER NOT NULL DEFAULT 0,

  is_active BOOLEAN NOT NULL DEFAULT TRUE,

  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT chk_product_variants_sort_order
      CHECK (sort_order >= 0),

  CONSTRAINT uq_product_variants_product_name
      UNIQUE (product_id, name),

  CONSTRAINT uq_product_variants_product_sort_order
      UNIQUE (product_id, sort_order)
      DEFERRABLE INITIALLY DEFERRED
);


CREATE UNIQUE INDEX uq_product_variants_sku_ci
    ON catalog.product_variants(UPPER(sku));

CREATE INDEX idx_product_variants_product_id
    ON catalog.product_variants(product_id);

-- One workflow-level key per authenticated user, across all products.
-- Claim, business writes and response are committed/rolled back in ONE transaction.
-- No cross-module FK: a creation receipt survives later business changes.
CREATE TABLE application.variant_creation_requests (
    actor_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (actor_id, operation_id),
    CONSTRAINT chk_variant_creation_completion CHECK (
        (response_json IS NULL AND completed_at IS NULL) OR
        (response_json IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_variant_creation_requests_expires_at
    ON application.variant_creation_requests(expires_at);

CREATE TABLE catalog.variant_images (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,

    public_id VARCHAR(255) NOT NULL,
    url VARCHAR(500) NOT NULL,

    sort_order INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_variant_images_variant
        FOREIGN KEY (variant_id)
            REFERENCES catalog.product_variants(id)
            ON DELETE CASCADE,

    CONSTRAINT uq_variant_images_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_variant_images_variant_sort_order
        UNIQUE (variant_id, sort_order)
        DEFERRABLE INITIALLY DEFERRED,

    CONSTRAINT chk_variant_images_sort_order
        CHECK (sort_order >= 0)
);

CREATE INDEX idx_variant_images_variant_id
    ON catalog.variant_images(variant_id);


CREATE TABLE catalog.outbox_events (
    -- Reserved for future Catalog integrations; variant creation currently uses synchronous orchestration.
    -- A future writer should supply a UUIDv7 event id.
    id UUID PRIMARY KEY,

    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,

    CONSTRAINT chk_catalog_outbox_event_version
        CHECK (event_version > 0),

    CONSTRAINT chk_catalog_outbox_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_catalog_outbox_pending
    ON catalog.outbox_events(next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL;

CREATE INDEX idx_catalog_outbox_aggregate
    ON catalog.outbox_events(aggregate_type, aggregate_id, occurred_at, id);

COMMENT ON TABLE catalog.outbox_events IS
    'Catalog integration events awaiting reliable publication';

COMMENT ON COLUMN catalog.outbox_events.id IS
    'Stable event identifier and downstream idempotency key';

COMMENT ON COLUMN catalog.outbox_events.event_version IS
    'Version of the event payload contract, not the JPA aggregate version';

COMMENT ON COLUMN catalog.outbox_events.next_attempt_at IS
    'Earliest time at which a failed or new event may be published';

-- No aggregate_id foreign key: events survive deletion of their source aggregate.

-- ====================================================================================
-- 3. INVENTORY MODULE
-- ====================================================================================

-- V1: one warehouse, one item per Catalog Variant.
-- variant_id is the existing Catalog ID, NOT a newly generated Inventory ID.
-- No cross-module FK: Catalog's public lookup supplies the initial snapshots.
-- Future Catalog events will create/update snapshots automatically.
-- available_qty is derived as on_hand_qty - reserved_qty; never write it separately.
CREATE TABLE inventory.items (
    variant_id UUID PRIMARY KEY,

    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    variant_name VARCHAR(255) NOT NULL,

    on_hand_qty INTEGER NOT NULL DEFAULT 0,
    reserved_qty INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_inventory_item_names
        CHECK (BTRIM(sku) <> '' AND BTRIM(product_name) <> '' AND BTRIM(variant_name) <> ''),

    CONSTRAINT chk_inventory_item_on_hand
        CHECK (on_hand_qty >= 0),

    CONSTRAINT chk_inventory_item_reserved
        CHECK (reserved_qty >= 0 AND reserved_qty <= on_hand_qty),

    CONSTRAINT chk_inventory_item_version
        CHECK (version >= 0)
);

CREATE INDEX idx_inventory_items_sku
    ON inventory.items(sku);


-- Audit history, written in the SAME transaction as the item quantity change.
-- IDs (including operation_id) are supplied by the application; new IDs use UUIDv7.
-- A retry must reuse operation_id. One operation may affect multiple items, but
-- may produce only one movement per item; separate business steps need separate IDs.
CREATE TABLE inventory.movements (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    operation_id UUID NOT NULL,

    movement_type VARCHAR(20) NOT NULL,

    on_hand_before INTEGER NOT NULL,
    on_hand_after INTEGER NOT NULL,
    on_hand_delta INTEGER NOT NULL,

    reserved_before INTEGER NOT NULL,
    reserved_after INTEGER NOT NULL,
    reserved_delta INTEGER NOT NULL,

    actor_type VARCHAR(10) NOT NULL,
    actor_id UUID,
    actor_label VARCHAR(255) NOT NULL,

    -- Optional business reference, e.g. ORDER + order UUID or RECEIPT + receipt UUID.
    -- These are identifiers only, with no FK to Sales/Identity or other modules.
    reference_type VARCHAR(50),
    reference_id UUID,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_movement_item
        FOREIGN KEY (variant_id) REFERENCES inventory.items(variant_id) ON DELETE RESTRICT,

    CONSTRAINT uq_inventory_movement_operation
        UNIQUE (variant_id, operation_id),

    CONSTRAINT chk_inventory_movement_type
        CHECK (movement_type IN ('RECEIVE', 'ADJUSTMENT', 'RESERVE', 'RELEASE', 'SHIP')),

    CONSTRAINT chk_inventory_movement_before
        CHECK (on_hand_before >= 0 AND reserved_before >= 0 AND reserved_before <= on_hand_before),

    CONSTRAINT chk_inventory_movement_after
        CHECK (on_hand_after >= 0 AND reserved_after >= 0 AND reserved_after <= on_hand_after),

    -- Cast before subtracting to avoid INTEGER arithmetic overflow on bad input.
    CONSTRAINT chk_inventory_movement_on_hand_delta
        CHECK (on_hand_delta::BIGINT = on_hand_after::BIGINT - on_hand_before::BIGINT),

    CONSTRAINT chk_inventory_movement_reserved_delta
        CHECK (reserved_delta::BIGINT = reserved_after::BIGINT - reserved_before::BIGINT),

    -- SHIP in V1 always consumes previously reserved stock.
    -- Physical corrections use ADJUSTMENT and never silently change reservations.
    CONSTRAINT chk_inventory_movement_effect
        CHECK (
            (movement_type = 'RECEIVE' AND on_hand_delta > 0 AND reserved_delta = 0)
            OR (movement_type = 'ADJUSTMENT' AND on_hand_delta <> 0 AND reserved_delta = 0)
            OR (movement_type = 'RESERVE' AND on_hand_delta = 0 AND reserved_delta > 0)
            OR (movement_type = 'RELEASE' AND on_hand_delta = 0 AND reserved_delta < 0)
            OR (movement_type = 'SHIP' AND on_hand_delta < 0 AND reserved_delta = on_hand_delta)
        ),

    -- A human actor is a trusted authenticated user UUID; SYSTEM has no user UUID.
    -- actor_label is an audit snapshot (display name or system job name).
    CONSTRAINT chk_inventory_movement_actor
        CHECK (
            ((actor_type = 'USER' AND actor_id IS NOT NULL)
             OR (actor_type = 'SYSTEM' AND actor_id IS NULL))
            AND BTRIM(actor_label) <> ''
        ),

    CONSTRAINT chk_inventory_movement_reference
        CHECK (
            (reference_type IS NULL AND reference_id IS NULL)
            OR (reference_type IS NOT NULL AND BTRIM(reference_type) <> '' AND reference_id IS NOT NULL)
        ),

    CONSTRAINT chk_inventory_movement_reason
        CHECK (
            (reason IS NULL OR BTRIM(reason) <> '')
            AND (movement_type <> 'ADJUSTMENT' OR reason IS NOT NULL)
        )
);

CREATE INDEX idx_inventory_movements_item_history
    ON inventory.movements(variant_id, created_at, id);

CREATE INDEX idx_inventory_movements_actor_history
    ON inventory.movements(actor_id, created_at, id)
    WHERE actor_id IS NOT NULL;

CREATE INDEX idx_inventory_movements_reference
    ON inventory.movements(reference_type, reference_id)
    WHERE reference_id IS NOT NULL;

COMMENT ON TABLE inventory.movements IS
    'Quantity audit history; application treats rows as append-only and records corrections as new movements';

-- CHECK constraints validate one row; they cannot ensure that before values match
-- the live item, that audit rows cannot be changed, or that a reservation belongs
-- to a specific order. Implement those rules in the transactional command flow.
-- A future reservation table tracks per-order allocations; this log is not a substitute.


-- Inventory's OUTGOING events. Incoming Catalog event deduplication is separate.
-- Receive/adjust commands write stock-changed events; publication is not implemented yet.
CREATE TABLE inventory.outbox_events (
    id UUID PRIMARY KEY,

    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,

    CONSTRAINT chk_inventory_outbox_event_version
        CHECK (event_version > 0),

    CONSTRAINT chk_inventory_outbox_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_inventory_outbox_pending
    ON inventory.outbox_events(next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL;

CREATE INDEX idx_inventory_outbox_aggregate
    ON inventory.outbox_events(aggregate_type, aggregate_id, occurred_at, id);

COMMENT ON TABLE inventory.outbox_events IS
    'Inventory integration events awaiting reliable publication; write with item/movement changes in one transaction';

COMMENT ON COLUMN inventory.outbox_events.id IS
    'Application-generated UUIDv7 event identifier; downstream consumers deduplicate by this stable ID';

COMMENT ON COLUMN inventory.outbox_events.event_version IS
    'Payload contract version, not the optimistic-lock version of the Inventory item';

-- No aggregate_id FK: event history must survive aggregate lifecycle changes.


-- ====================================================================================
-- 4. CART MODULE
-- ====================================================================================

CREATE TABLE cart.carts (
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),

    user_id UUID UNIQUE
        REFERENCES identity.users(id)
            ON DELETE CASCADE,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE cart.cart_items (
     id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),

     cart_id UUID NOT NULL
         REFERENCES cart.carts(id)
             ON DELETE CASCADE,

     variant_id UUID NOT NULL
         REFERENCES catalog.product_variants(id)
             ON DELETE RESTRICT,

     quantity INTEGER NOT NULL
         CHECK (quantity > 0),

     UNIQUE(cart_id, variant_id)
);


CREATE INDEX idx_cart_items_cart_id
    ON cart.cart_items(cart_id);

CREATE INDEX idx_cart_items_variant_id
    ON cart.cart_items(variant_id);


-- ====================================================================================
-- 5. SALES MODULE
-- ====================================================================================

CREATE TABLE sales.orders (
      id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),

      user_id UUID NOT NULL
          REFERENCES identity.users(id),

      status VARCHAR(50) NOT NULL DEFAULT 'PENDING',

      total_amount NUMERIC(19,2) NOT NULL DEFAULT 0
          CHECK (total_amount >= 0),

      shipping_address TEXT NOT NULL,

      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE sales.order_items (
   id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),

   order_id UUID NOT NULL
       REFERENCES sales.orders(id)
           ON DELETE CASCADE,

   variant_id UUID NOT NULL
       REFERENCES catalog.product_variants(id)
           ON DELETE RESTRICT,

-- Snapshot fields:
-- Order history must not change when Product/Variant changes later.
   product_name_snapshot VARCHAR(255) NOT NULL,
   variant_name_snapshot VARCHAR(255) NOT NULL,
   sku_snapshot VARCHAR(100) NOT NULL,

   quantity INTEGER NOT NULL
       CHECK (quantity > 0),

   unit_price NUMERIC(19,2) NOT NULL
       CHECK (unit_price >= 0),

   UNIQUE(order_id, variant_id)
);


CREATE INDEX idx_orders_user_id
    ON sales.orders(user_id);

CREATE INDEX idx_order_items_order_id
    ON sales.order_items(order_id);

CREATE INDEX idx_order_items_variant_id
    ON sales.order_items(variant_id);


-- ====================================================================================
-- 6. PAYMENT MODULE
-- ====================================================================================

CREATE TABLE payment.payments (
      id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),

      order_id UUID NOT NULL
          REFERENCES sales.orders(id)
              ON DELETE RESTRICT,

      amount NUMERIC(19,2) NOT NULL
          CHECK (amount > 0),

      status VARCHAR(50) NOT NULL DEFAULT 'PENDING_GATEWAY',

      provider_tx_id VARCHAR(255),

      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_payments_order_id
    ON payment.payments(order_id);

CREATE UNIQUE INDEX uq_payments_provider_tx_id
    ON payment.payments(provider_tx_id)
    WHERE provider_tx_id IS NOT NULL;
