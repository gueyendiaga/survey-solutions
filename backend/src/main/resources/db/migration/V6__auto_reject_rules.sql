-- Règles de rejet automatique, paramétrées par version de questionnaire.
CREATE TABLE auto_reject_settings (
    questionnaire_id   UUID        NOT NULL,
    version            BIGINT      NOT NULL,
    mode               TEXT        NOT NULL DEFAULT 'OFF' CHECK (mode IN ('OFF', 'SIMULATION', 'AUTO')),
    max_hq_rejections  INTEGER     NOT NULL DEFAULT 2,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by         TEXT,
    PRIMARY KEY (questionnaire_id, version)
);

CREATE TABLE auto_reject_rule (
    id               BIGSERIAL PRIMARY KEY,
    questionnaire_id UUID    NOT NULL,
    version          BIGINT  NOT NULL,
    position         INTEGER NOT NULL,
    type             TEXT    NOT NULL CHECK (type IN
                         ('VALIDATION_ERRORS', 'MIN_DURATION', 'MAX_UNANSWERED', 'REQUIRED_ANSWER', 'VALUE_RANGE')),
    variable         TEXT,
    min_value        NUMERIC,
    max_value        NUMERIC,
    message          TEXT,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX auto_reject_rule_version_idx ON auto_reject_rule (questionnaire_id, version);

-- Journal des rejets automatiques : une interview n'est rejetée qu'une fois par soumission.
CREATE TABLE auto_reject_event (
    id               BIGSERIAL PRIMARY KEY,
    questionnaire_id UUID        NOT NULL,
    version          BIGINT      NOT NULL,
    interview_id     UUID        NOT NULL,
    interview_key    TEXT,
    completed_at     TIMESTAMPTZ,
    evaluated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    outcome          TEXT        NOT NULL CHECK (outcome IN ('REJECTED', 'FAILED', 'SKIPPED_LIMIT')),
    reasons          TEXT        NOT NULL,
    error            TEXT
);

CREATE INDEX auto_reject_event_version_idx ON auto_reject_event (questionnaire_id, version, evaluated_at DESC);
CREATE INDEX auto_reject_event_interview_idx ON auto_reject_event (interview_id);
