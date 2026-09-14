-- LL-EF-010 : ajoute CANCELLED aux statuts autorisés (formalisés en
-- LL-6003 / V11__enforce_activity_status.sql, étendus une première fois
-- en V13__allow_archived_activity_status.sql pour ARCHIVED), pour que le
-- créateur d'une activité manuelle (ou un administrateur) puisse
-- l'annuler tout en la conservant en base (critère d'acceptation
-- explicite du ticket).
--
-- Une contrainte CHECK ne peut pas être modifiée directement en
-- PostgreSQL : on la supprime puis on la recrée avec la valeur
-- supplémentaire, même nom (chk_activity_status) pour rester la même
-- contrainte logique plutôt qu'en ajouter une seconde. Reprend
-- explicitement ARCHIVED (V13) en plus des trois statuts d'origine (V11)
-- : recréer cette contrainte sans lui aurait empêché toute activité
-- existante ou future d'être marquée ARCHIVED (voir ImportService,
-- LL-5008) — un oubli initialement fait ici, repéré avant merge.
ALTER TABLE activity DROP CONSTRAINT chk_activity_status;
ALTER TABLE activity ADD CONSTRAINT chk_activity_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED', 'CANCELLED'));
