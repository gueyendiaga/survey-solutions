-- Suivi des opérations de terrain : validation, tablettes, mode de collecte, rendez-vous.

ALTER TABLE interview
    ADD COLUMN last_completed_at          TIMESTAMPTZ,
    ADD COLUMN supervisor_approved_at     TIMESTAMPTZ,
    ADD COLUMN hq_approved_at             TIMESTAMPTZ,
    ADD COLUMN received_by_tablet_at      TIMESTAMPTZ,
    ADD COLUMN interview_mode             TEXT,
    ADD COLUMN received_by_interviewer_at TIMESTAMPTZ;

ALTER TABLE assignment
    ADD COLUMN created_at            TIMESTAMPTZ,
    ADD COLUMN received_by_tablet_at TIMESTAMPTZ,
    ADD COLUMN web_mode              BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN target_area           TEXT;

-- Compte enquêteur : verrouillé dans HQ, tablette liée (DeviceId renseigné).
ALTER TABLE team_member
    ADD COLUMN locked        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN device_linked BOOLEAN;

-- Rendez-vous planifiés dans HQ (calendrier des interviews et des affectations).
CREATE TABLE appointment (
    id               BIGSERIAL PRIMARY KEY,
    questionnaire_id UUID        NOT NULL,
    version          BIGINT      NOT NULL,
    interview_key    TEXT,
    assignment_id    INTEGER,
    responsible_name TEXT,
    start_at         TIMESTAMPTZ NOT NULL,
    completed        BOOLEAN     NOT NULL DEFAULT FALSE,
    comment          TEXT
);

CREATE INDEX appointment_version_idx ON appointment (questionnaire_id, version, start_at);
CREATE INDEX interview_status_idx ON interview (questionnaire_id, version, status);

-- Les nouvelles colonnes se remplissent à la prochaine synchronisation complète.
UPDATE sync_run SET hq_fingerprint = NULL;
