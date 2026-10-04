-- Catalogue of ingested policy documents. The chunks + embeddings themselves live in the
-- Spring AI managed `vector_store` table and reference this row via metadata->>'document_id'.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE policy_document (
    id              UUID PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    audience        VARCHAR(32)  NOT NULL,
    source_name     VARCHAR(255) NOT NULL,
    content_type    VARCHAR(128) NOT NULL,
    content_sha256  CHAR(64)     NOT NULL,
    chunk_count     INTEGER      NOT NULL,
    created_by      VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT policy_document_audience_chk
        CHECK (audience IN ('PUBLIC', 'EMPLOYEE', 'HR', 'FINANCE', 'LEGAL')),
    CONSTRAINT policy_document_sha_unique UNIQUE (content_sha256)
);

CREATE INDEX policy_document_audience_idx ON policy_document (audience);
