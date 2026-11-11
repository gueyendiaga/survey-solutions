-- Suivi terrain : enquêteur, dates clés, affectations (objectifs) et composition des équipes.

ALTER TABLE interview
    ADD COLUMN interviewer_name TEXT,
    ADD COLUMN created_at       TIMESTAMPTZ,
    ADD COLUMN completed_at     TIMESTAMPTZ,
    ADD COLUMN last_action_at   TIMESTAMPTZ;

CREATE INDEX interview_interviewer_idx ON interview (questionnaire_id, version, interviewer_name);

-- Affectations HQ actives d'une version. quantity NULL ou négative : affectation sans quota.
CREATE TABLE assignment (
    questionnaire_id UUID    NOT NULL,
    version          BIGINT  NOT NULL,
    assignment_id    INTEGER NOT NULL,
    responsible_name TEXT,
    quantity         INTEGER,
    interviews_count INTEGER NOT NULL DEFAULT 0,
    archived         BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (questionnaire_id, version, assignment_id)
);

-- Rattachement enquêteur → superviseur, rafraîchi à chaque synchronisation.
CREATE TABLE team_member (
    interviewer_name TEXT PRIMARY KEY,
    supervisor_name  TEXT NOT NULL
);

-- Les versions déjà importées n'ont pas ces colonnes : effacer l'empreinte force leur
-- réimport à la prochaine synchronisation planifiée.
UPDATE sync_run SET hq_fingerprint = NULL;
