-- Rapports PDF générés automatiquement (ou archivés à la demande), conservés en base.
CREATE TABLE report (
    id               BIGSERIAL PRIMARY KEY,
    questionnaire_id UUID        NOT NULL,
    version          BIGINT      NOT NULL,
    generated_at     TIMESTAMPTZ NOT NULL,
    trigger          TEXT        NOT NULL CHECK (trigger IN ('SCHEDULED', 'MANUAL')),
    filename         TEXT        NOT NULL,
    size_bytes       INTEGER     NOT NULL,
    content          BYTEA       NOT NULL
);

CREATE INDEX report_questionnaire_idx ON report (questionnaire_id, version, generated_at DESC);
