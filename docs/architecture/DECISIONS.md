# RiMarket Architecture Decisions

## ADR-001 — Modular Monolith First

Status: Accepted

### Decision

RiMarket is implemented as a modular monolith before considering microservices.

### Why

The current project size does not justify distributed-system complexity.

The goal is to establish strong module boundaries first.

### Revisit when

- independent deployment becomes necessary
- modules require independent scaling
- organizational boundaries justify separate services

---

## ADR-002 — Product Is the Catalog Aggregate Root

Status: Accepted

### Decision

Product owns ProductVariant.

Variant mutations go through Product.

### Why

Variants currently do not have an independent business lifecycle.

Product-level invariants may depend on Variant state.

### Revisit when

- variants require independent high-frequency writes
- Product aggregates become excessively large
- Variant develops an independent lifecycle

---

## ADR-003 — Variant Owns Images

Status: Accepted

### Decision

VariantImage belongs to ProductVariant.

Database ownership is represented by:

variant_images.variant_id

### Why

Images identify individual sellable SKUs more accurately than Product-level images for current requirements.

This is particularly useful for warehouse/SKU identification.

### Revisit when

Product-level galleries or non-variant media become independent requirements.

---

## ADR-004 — External Image Storage Behind a Port

Status: Accepted

### Decision

Catalog depends on ImageStorage.

CloudinaryImageStorage implements the port.

### Why

Cloudinary is infrastructure and should not become a Catalog domain dependency.

### Revisit when

- Cloudinary latency becomes unacceptable after measurement
- cost changes significantly
- S3/R2 becomes more suitable
- direct client upload is introduced

---

## ADR-005 — Images Are LAZY

Status: Accepted

### Decision

Variant images remain LAZY.

Use-case-specific repository queries fetch images when required.

### Why

Aggregate ownership does not imply every command must load the entire object graph.

### Revisit when

Profiling demonstrates a different fetch strategy is consistently superior.