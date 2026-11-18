-- Entrée dans la file de HQ : approbation superviseur ou désapprobation HQ. Distincte de
-- supervisor_approved_at, qui sert au délai de validation du superviseur.
ALTER TABLE interview ADD COLUMN hq_queue_since TIMESTAMPTZ;

UPDATE sync_run SET hq_fingerprint = NULL;
