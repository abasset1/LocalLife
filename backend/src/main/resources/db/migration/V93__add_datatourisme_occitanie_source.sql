-- Source DATAtourisme Occitanie (data.gouv.fr), pendant de la source PACA (V60) pour la zone de lancement.
--
-- Constat (19/11/2026) : peu d'activités en Occitanie comparé à PACA, alors que le fichier régional Occitanie
-- (datatourisme-reg-occ.csv, ~41 Mo) compte 7 381 événements à venir contre 2 645 pour PACA :
-- 34 : 2 289, 31 : 1 046, 65 : 856, 12 : 831, 46 : 664, 66 : 582, 48 : 498, 11 : 235, 81 : 115, 30 : 94,
-- 32 : 87, 82 : 44, 09 : 40. Aucune migration ne créait de source pour cette région.
--
-- url : URL stable de la ressource data.gouv.fr « datatourisme-reg-occ.csv », qui redirige vers le fichier du
-- jour. region_filter NULL : toute la région (13 départements). L'enrichissement par l'API DATAtourisme reste
-- sous le garde-fou de DataTourismeApiEnricher (7 381 événements → 148 requêtes sur 400).
--
-- Créée INACTIVE, comme toute nouvelle source DATAtourisme : elle s'active après un essai à blanc réussi
-- (Administration → Imports → « Activer après vérification »). Les événements déjà publiés par une autre source
-- (fichier France, source « Languedoc » créée depuis l'administration, agendas OpenAgenda) ne sont pas publiés
-- deux fois : l'import écarte les doublons vivants d'une autre source (ImportService.isDuplicateOfAnotherSource).
INSERT INTO source (name, type, url, status, last_sync_at, agenda_uid, region_filter)
VALUES (
    'DATAtourisme Occitanie (data.gouv.fr)',
    'DATATOURISME',
    'https://www.data.gouv.fr/api/1/datasets/r/0c463ef6-c00a-48e2-b50a-d17cfe998b84',
    'INACTIVE',
    NULL,
    NULL,
    NULL);
