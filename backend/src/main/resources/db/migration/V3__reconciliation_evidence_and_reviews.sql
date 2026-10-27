-- ============================================================================
--  Moziki · daily cash count evidence and correction approval
--
--  A correction (revision 2+) is not authoritative until an auditor reviews
--  it. Decisions and supporting photos/receipts live in their own append-only
--  tables: the original report stays untouched and the review trail cannot be
--  rewritten.
-- ============================================================================

CREATE TABLE reconciliation_reviews (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cooperative_id    UUID        NOT NULL REFERENCES cooperatives (id) ON DELETE RESTRICT,
    reconciliation_id UUID        NOT NULL REFERENCES cash_reconciliations (id) ON DELETE RESTRICT,
    decision          VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    reviewer_id       UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    note              VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_reconciliation_reviews_report UNIQUE (reconciliation_id)
);

CREATE INDEX idx_reconciliation_reviews_coop_created
    ON reconciliation_reviews (cooperative_id, created_at DESC);

CREATE TABLE reconciliation_evidence (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cooperative_id    UUID         NOT NULL REFERENCES cooperatives (id) ON DELETE RESTRICT,
    reconciliation_id UUID         NOT NULL REFERENCES cash_reconciliations (id) ON DELETE RESTRICT,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),
    sha256            VARCHAR(64)  NOT NULL,
    storage_key       VARCHAR(255) NOT NULL,
    uploaded_by       UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_reconciliation_evidence_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_reconciliation_evidence_report
    ON reconciliation_evidence (reconciliation_id, created_at);

CREATE OR REPLACE FUNCTION moziki_block_append_only_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is forbidden', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_reconciliation_reviews_immutable
    BEFORE UPDATE OR DELETE ON reconciliation_reviews
    FOR EACH ROW EXECUTE FUNCTION moziki_block_append_only_mutation();

CREATE TRIGGER trg_reconciliation_evidence_immutable
    BEFORE UPDATE OR DELETE ON reconciliation_evidence
    FOR EACH ROW EXECUTE FUNCTION moziki_block_append_only_mutation();
