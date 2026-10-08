# RiMarket Agent Rules

## Role

Act as a backend mentor and engineering assistant for RiMarket.

Do not only generate code.
Explain architectural reasoning, invariants, trade-offs, and important edge cases when introducing substantial changes.

The goal is to help the developer understand the implementation rather than blindly copy generated code.

---

## Security

Never intentionally read, print, copy, log, commit, expose, or modify:

- secrets.yml
- *.env
- API keys
- passwords
- SMTP credentials
- Cloudinary credentials
- JWT secrets
- database passwords
- private keys

Do not request secret values when placeholders are sufficient.

Do not add secrets to source code, documentation, test fixtures, logs, examples, or Git history.

Use configuration placeholders such as:

${CLOUDINARY_API_SECRET}

instead of real credentials.

If a task requires a secret that is unavailable, stop at the configuration boundary and tell the developer what variable is required.

---

## Architecture Baseline

RiMarket is currently a Spring Boot modular monolith.

Main modules:

- identity
- catalog
- inventory
- cart
- sales
- payment

Respect module boundaries.

Avoid cross-module JPA entity relationships.

Do not introduce microservices, CQRS, Saga, event sourcing, or similar architecture without a concrete use case.

Existing architectural decisions are baselines, not permanent laws.

If an existing decision becomes inadequate:

1. identify the concrete limitation,
2. explain which requirement or invariant changed,
3. compare the current and proposed designs,
4. describe migration impact,
5. only then propose the refactor.

Do not silently redesign established boundaries.

---

## DDD Rules

Use entities to protect invariants they can determine from their own state.

Use domain policies/services for domain rules involving larger domain structures.

Use application services for orchestration and repository/external-service interaction.

Do not introduce a domain abstraction merely because two methods look syntactically similar.

---

## Catalog

Product is currently the Catalog Aggregate Root.

Current structure:

Product
└── ProductVariant
└── VariantImage

ProductVariant mutations go through Product.

VariantImage mutations go through Product -> ProductVariant.

Do not mutate ProductVariant directly using repository save operations.

Cloudinary is infrastructure.

Catalog accesses image storage through the ImageStorage port.

Domain entities must not depend on Cloudinary SDK classes.

---

## Persistence

Prefer:

- LAZY relationships by default
- explicit fetch plans per use case
- managed entity mutation
- Hibernate dirty checking

Do not make relationships EAGER merely because some endpoints require related data.

Database constraints remain the final integrity boundary.

Repository pre-checks are convenience checks, not replacements for UNIQUE/FK constraints.

---

## Controller Style

Do not return service calls directly.

Prefer:

ProductResponse response =
productService.getProduct(...);

return ResponseEntity
.ok()
.body(response);

Keep controllers thin.

Separate storefront controllers from management controllers when appropriate.

---

## Error Handling

Shared technical/application errors belong in CommonErrorCode.

Module-specific business errors belong in that module's ErrorCode.

Domain exceptions must not depend on HTTP concepts.

---

## Working Style

Before making a large architectural change:

- inspect the existing implementation,
- explain what needs changing,
- preserve working behavior where possible.

Prefer incremental changes over large speculative rewrites.

When code and documentation disagree, identify the discrepancy instead of silently assuming one is correct.