-- MANUAL migration for an existing DB. init.sql is for a fresh DB only.
-- Back up first; run during a maintenance window (ALTER TABLE takes a lock).
-- No automatic execution, deletion, renaming or deduplication is performed here.
-- If preflight fails, inspect and resolve the names deliberately, then rerun.
-- Diagnostic: SELECT product_id, name, count(*) FROM catalog.product_variants
--             GROUP BY product_id, name HAVING count(*) > 1;

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM catalog.product_variants
        GROUP BY product_id, name HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate variant names exist. Resolve them before adding the constraint.';
    END IF;
    IF EXISTS (SELECT 1 FROM catalog.product_variants WHERE name <> btrim(name)) THEN
        RAISE EXCEPTION 'Untrimmed variant names exist. Normalize and check duplicates before continuing.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'catalog.product_variants'::regclass
          AND conname = 'uq_product_variants_product_name'
    ) THEN
        ALTER TABLE catalog.product_variants
            ADD CONSTRAINT uq_product_variants_product_name UNIQUE (product_id, name);
    END IF;
END $$;

CREATE SCHEMA IF NOT EXISTS application;
CREATE TABLE IF NOT EXISTS application.variant_creation_requests (
    actor_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    PRIMARY KEY (actor_id, operation_id),
    CONSTRAINT chk_variant_creation_completion CHECK (
        (response_json IS NULL AND completed_at IS NULL) OR
        (response_json IS NOT NULL AND completed_at IS NOT NULL)
    )
);

-- Preserve the original claim time for existing entries. Already-expired keys
-- become eligible for the cleanup job after deployment.
ALTER TABLE application.variant_creation_requests
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

UPDATE application.variant_creation_requests
SET expires_at = created_at + INTERVAL '24 hours'
WHERE expires_at IS NULL;

ALTER TABLE application.variant_creation_requests
    ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_variant_creation_requests_expires_at
    ON application.variant_creation_requests(expires_at);

COMMIT;
