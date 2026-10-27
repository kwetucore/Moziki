-- ============================================================================
--  Moziki · daily cash & mobile money reconciliation
--
--  Leaders declare what they physically counted at the end of the business
--  day. The service freezes the journal-derived book balance next to the
--  declared count, so history can never be rewritten by later postings.
--  Corrections are new revisions that point at the report they supersede.
-- ============================================================================

CREATE TABLE cash_reconciliations (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cooperative_id           UUID        NOT NULL REFERENCES cooperatives (id) ON DELETE RESTRICT,
    business_date            DATE        NOT NULL,
    revision                 INTEGER     NOT NULL DEFAULT 1 CHECK (revision >= 1),
    supersedes_id            UUID        REFERENCES cash_reconciliations (id) ON DELETE RESTRICT,
    reported_by              UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'CDF',

    counted_cash_minor       BIGINT      NOT NULL CHECK (counted_cash_minor >= 0),
    counted_airtel_minor     BIGINT      NOT NULL CHECK (counted_airtel_minor >= 0),
    counted_orange_minor     BIGINT      NOT NULL CHECK (counted_orange_minor >= 0),
    counted_mpesa_minor      BIGINT      NOT NULL CHECK (counted_mpesa_minor >= 0),

    book_cash_minor          BIGINT      NOT NULL,
    book_airtel_minor        BIGINT      NOT NULL,
    book_orange_minor        BIGINT      NOT NULL,
    book_mpesa_minor         BIGINT      NOT NULL,

    cash_difference_minor    BIGINT      NOT NULL,
    airtel_difference_minor  BIGINT      NOT NULL,
    orange_difference_minor  BIGINT      NOT NULL,
    mpesa_difference_minor   BIGINT      NOT NULL,

    total_counted_minor      BIGINT      NOT NULL,
    total_book_minor         BIGINT      NOT NULL,
    total_difference_minor   BIGINT      NOT NULL,

    status                   VARCHAR(16) NOT NULL
                             CHECK (status IN ('BALANCED', 'DISCREPANCY')),
    notes                    VARCHAR(500),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_cash_reconciliations_revision
        UNIQUE (cooperative_id, business_date, revision),
    CONSTRAINT chk_cash_reconciliations_supersedes
        CHECK (supersedes_id IS NULL OR revision > 1)
);

CREATE INDEX idx_cash_reconciliations_coop_date
    ON cash_reconciliations (cooperative_id, business_date DESC, revision DESC);

CREATE INDEX idx_cash_reconciliations_coop_created
    ON cash_reconciliations (cooperative_id, created_at DESC);

-- A report is evidence: it may be superseded by a newer revision, never edited
-- or deleted. This is deliberately independent of the application layer.
CREATE OR REPLACE FUNCTION moziki_block_reconciliation_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'cash reconciliations are append-only; % is forbidden', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_cash_reconciliations_immutable
    BEFORE UPDATE OR DELETE ON cash_reconciliations
    FOR EACH ROW EXECUTE FUNCTION moziki_block_reconciliation_mutation();
