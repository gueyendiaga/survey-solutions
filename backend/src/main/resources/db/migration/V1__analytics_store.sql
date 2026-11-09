-- Entrepôt Analytics alimenté par l'Export API de Survey Solutions (format tabulaire).
-- Une synchronisation remplace intégralement les données d'une version de questionnaire.

CREATE TABLE sync_run (
    id                 BIGSERIAL PRIMARY KEY,
    questionnaire_id   UUID        NOT NULL,
    version            BIGINT      NOT NULL,
    status             TEXT        NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    trigger            TEXT        NOT NULL CHECK (trigger IN ('SCHEDULED', 'MANUAL', 'ON_DEMAND')),
    started_at         TIMESTAMPTZ NOT NULL,
    finished_at        TIMESTAMPTZ,
    export_job_id      BIGINT,
    interview_count    INTEGER,
    unit_count         INTEGER,
    hq_fingerprint TEXT,
    error              TEXT
);

CREATE INDEX sync_run_questionnaire_idx ON sync_run (questionnaire_id, version, started_at DESC);

-- Une ligne par interview exportée (fichier principal + interview__diagnostics).
CREATE TABLE interview (
    interview_id          UUID PRIMARY KEY,
    questionnaire_id      UUID    NOT NULL,
    version               BIGINT  NOT NULL,
    interview_key         TEXT    NOT NULL,
    status                TEXT    NOT NULL,
    assignment_id         INTEGER,
    has_errors            BOOLEAN,
    errors_count          INTEGER,
    rejections_supervisor INTEGER,
    rejections_hq         INTEGER,
    interviewer_count     INTEGER,
    duration_seconds      BIGINT,
    unanswered_count      INTEGER
);

CREATE INDEX interview_questionnaire_idx ON interview (questionnaire_id, version, status);

-- Une ligne par unité d'observation : l'interview elle-même (niveau principal) ou une ligne de
-- roster (membre du ménage, parcelle…). answers associe chaque variable à son état et sa valeur :
--   {"age": {"s": "A", "v": "34"}, "sexe": {"s": "D"}}
--   s = A (répondu), M (active mais sans réponse), D (désactivée / non applicable).
--   v = valeur normalisée (code de modalité, codes cochés séparés par ", ", nombre, texte…).
CREATE TABLE answer_unit (
    id               BIGSERIAL PRIMARY KEY,
    questionnaire_id UUID   NOT NULL,
    version          BIGINT NOT NULL,
    interview_id     UUID   NOT NULL REFERENCES interview (interview_id) ON DELETE CASCADE,
    level            TEXT   NOT NULL,
    row_key          TEXT   NOT NULL DEFAULT '',
    answers          JSONB  NOT NULL
);

CREATE INDEX answer_unit_questionnaire_idx ON answer_unit (questionnaire_id, version);
CREATE INDEX answer_unit_interview_idx ON answer_unit (interview_id);
CREATE INDEX answer_unit_answers_idx ON answer_unit USING GIN (answers);
