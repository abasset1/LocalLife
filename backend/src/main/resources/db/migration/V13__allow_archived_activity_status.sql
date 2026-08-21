-- LL-7007 : corrige le blocage trouvé en LL-7003. ImportService
-- (LL-5008) affecte le statut ARCHIVED aux activités disparues de la
-- source lors d'un ré-import, mais chk_activity_status (V11,
-- LL-6003) n'autorisait que PENDING/PUBLISHED/REJECTED. Un second
-- import déclenchait donc systématiquement une
-- DataIntegrityViolationException dès qu'une activité devait être
-- archivée.
ALTER TABLE activity DROP CONSTRAINT chk_activity_status;
ALTER TABLE activity ADD CONSTRAINT chk_activity_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED'));
