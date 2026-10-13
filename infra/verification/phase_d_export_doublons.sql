-- Phase D (ADR-0011, § 7.4) : export des activités pour le rapport de doublons — LECTURE SEULE.
-- Usage (VPS, depuis infra/, après « set -a; . ./.env.beta; set +a » comme backup.sh) :
--   docker compose -f docker-compose.beta.yml exec -T postgres \
--     psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
--     < verification/phase_d_export_doublons.sql > activites-doublons-84.csv
-- Une seule commande \copy … TO STDOUT : aucune écriture en base, le fichier est écrit côté client.
-- Contenu : activités publiées, à venir, du Vaucluse, hors contributions (source MANUAL : adresses saisies par
-- des utilisateurs) ; titre, jours (occurrences, sinon dates de l'activité), position, adresse, commune, URL.
-- Format attendu par collectors/verification/DuplicateReport.java. Testé sur une base jetable le 13/10/2026.
\copy (SELECT a.id, s.name AS source, a.title, COALESCE(string_agg(DISTINCT to_char((o.start_at AT TIME ZONE 'Europe/Paris')::date, 'YYYY-MM-DD') || '/' || to_char((COALESCE(o.end_at, o.start_at) AT TIME ZONE 'Europe/Paris')::date, 'YYYY-MM-DD'), ';'), to_char(a.start_date::date, 'YYYY-MM-DD') || '/' || to_char(COALESCE(a.end_date, a.start_date)::date, 'YYYY-MM-DD')) AS periods, a.latitude, a.longitude, a.address, a.city, COALESCE(a.url, '') AS urls FROM activity a JOIN source s ON s.id = a.source_id LEFT JOIN schedule sc ON sc.activity_id = a.id LEFT JOIN occurrence o ON o.schedule_id = sc.id WHERE a.status = 'PUBLISHED' AND s.type <> 'MANUAL' AND a.postal_code LIKE '84%' AND COALESCE(a.end_date, a.start_date) >= CURRENT_DATE GROUP BY a.id, s.name) TO STDOUT WITH CSV HEADER
