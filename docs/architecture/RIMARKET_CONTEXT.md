# RiMarket Current Context

## Overview

RiMarket is an e-commerce backend implemented as a Spring Boot modular monolith.

Technology stack:

- Java
- Spring Boot
- Spring Data JPA / Hibernate
- PostgreSQL
- Redis
- Spring Security
- JWT authentication
- Cloudinary

---

## Modules

Current planned modules:

identity
catalog
inventory
cart
sales
payment

---

## Identity

Current foundation includes:

- User
- Role
- email verification
- JWT access token
- refresh token
- Redis-backed refresh-token storage

Remaining Identity work includes:

- logout refinement
- refresh flow refinement
- reset password
- role/permission refinement

---

## Catalog

Catalog currently contains:

Product
└── ProductVariant
└── VariantImage

Category is a separate hierarchy inside Catalog.

### Category

Rules:

- maximum depth = 3
- no circular hierarchy
- sibling names are unique
- level-1/root categories cannot contain Products directly
- categories with children cannot be deleted
- categories referenced by Products cannot be deleted

Category uses row-level optimistic locking to prevent concurrent admin updates
from silently overwriting one another. Structural write transactions also use
SERIALIZABLE to protect hierarchy decisions spanning multiple rows; conflicts
require reloading data and retrying the whole request. Database unique and
foreign-key constraints remain the final integrity boundary. Concurrent database
integration checks remain pending; see CATALOG_V1_REVIEW.md.

### Product

Product is the Aggregate Root.

Product owns ProductVariants.

Product lifecycle:

inactive/draft
→ add the first active Variant
→ automatically activate Product

Product requires at least one active Variant before activation.

Slug is generated automatically and remains stable after rename.

Products may be assigned only to level-2 or level-3 Categories. Product creation,
Product category changes, and Category moves use the same SERIALIZABLE protocol so a
concurrent Product assignment cannot race a Category move to level 1.

Product carries the optimistic-lock version for the whole aggregate.
Commands that only mutate a ProductVariant force-increment the Product version,
so concurrent variant commands cannot bypass aggregate-level invariants.

### ProductVariant

Variant represents the sellable SKU.

Fields include:

- sku
- name
- price
- sortOrder
- active
- images

SKU is immutable.

Price currently represents VND using BigDecimal / NUMERIC(19,0).

Variants are displayed by sortOrder. New Variants are appended to the end of
their Product; explicit reordering accepts the complete ordered list of Variant IDs.

Product creation, Variant creation, and image upload are separate application
operations. A Product may remain an inactive draft without Variants, and a
Variant may exist before any image is uploaded.

### VariantImage

A Variant owns a list of VariantImages.

VariantImage contains:

- id
- variant_id
- publicId
- url
- sortOrder
- createdAt

Images are ordered using sortOrder. The first image is the cover; there is no separate primary flag.

Image mutation operations include:

- add
- add multiple images in request order
- replace the stored file while preserving image identity and display state
- remove
- reorder

The single upload endpoint remains available. Batch upload validates every file before
external I/O, uploads sequentially, and attaches all image metadata in one Catalog DB
transaction. Known uploads are deleted as compensation if the batch cannot be attached.

Catalog owns `catalog.outbox_events`. The SQL table and polling indexes are prepared,
but no Catalog command writes events and no publisher runs yet. Event contracts will be
defined from Inventory's actual consumer requirements.

---

## Cloudinary

Cloudinary upload/delete integration has been tested successfully.

Catalog depends on:

ImageStorage

Infrastructure provides:

CloudinaryImageStorage implements ImageStorage

Cloudinary classes must not leak into Catalog entities.

---

## Fetch Strategy

Normal Product aggregate command:

Product + Variants

Image command/detail:

Product + Variants + Images

Relationships remain LAZY unless a query explicitly requires them.

---

## Controller Structure

controller/
├── storefront/
└── management/

Storefront exposes public Catalog reads.

Management endpoints are intended for STAFF and ADMIN.

---

## Current Development State

Catalog core is close to V1 completion.

Catalog services, controllers and unit tests have been reconciled with the current
first-image-as-cover model. See CATALOG_V1_REVIEW.md for transaction boundaries,
manual SQL rollout, verification evidence and remaining integration checks.
