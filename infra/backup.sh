#!/usr/bin/env bash
#
# Sauvegarde de la base bêta LocalLife (LL-9004, critère « sauvegarde
# minimale disponible »).
#
# - dump PostgreSQL compressé, écrit localement dans infra/backups/ ;
# - copie du dump vers Backblaze B2 (hors-site) via rclone, remote "b2"
#   préconfiguré sur le VPS (voir docs/02_Architecture/BACKUP_RESTORE.md) ;
# - rotation locale et distante.
#
# Destiné à être appelé par cron depuis ce dossier (infra/). Peut aussi
# être lancé manuellement pour une sauvegarde ponctuelle.
#
# Non testé en conditions réelles depuis cette sandbox (pas de VPS, pas
# de compte B2 accessible) : vérifié par relecture et par exécution
# partielle en local (génération du dump), voir BACKUP_RESTORE.md.

set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f .env.beta ]; then
    echo "Erreur : .env.beta introuvable dans $(pwd)." >&2
    exit 1
fi

set -a
# shellcheck source=/dev/null
source .env.beta
set +a

BACKUP_DIR="./backups"
LOCAL_RETENTION_DAYS=7
REMOTE_RETENTION_DAYS=30
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
DUMP_FILE="${BACKUP_DIR}/locallife-beta-${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "[$(date -Iseconds)] Démarrage du dump..."

# --clean --if-exists : le dump contient les DROP nécessaires avant
# chaque CREATE, pour permettre une restauration directe sur une base
# non vide (voir procédure de restauration).
docker compose -f docker-compose.beta.yml exec -T postgres \
    pg_dump --clean --if-exists -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
    | gzip > "${DUMP_FILE}"

echo "[$(date -Iseconds)] Dump local créé : ${DUMP_FILE} ($(du -h "${DUMP_FILE}" | cut -f1))"

if [ -z "${B2_BUCKET_NAME:-}" ] || [ "${B2_BUCKET_NAME}" = "changez_moi" ]; then
    echo "Erreur : B2_BUCKET_NAME non configuré dans .env.beta." >&2
    exit 1
fi

echo "[$(date -Iseconds)] Copie vers Backblaze B2..."
rclone copy "${DUMP_FILE}" "b2:${B2_BUCKET_NAME}/"

echo "[$(date -Iseconds)] Rotation locale (> ${LOCAL_RETENTION_DAYS} j) et distante (> ${REMOTE_RETENTION_DAYS} j)..."
find "${BACKUP_DIR}" -name "locallife-beta-*.sql.gz" -mtime "+${LOCAL_RETENTION_DAYS}" -delete
rclone delete --min-age "${REMOTE_RETENTION_DAYS}d" "b2:${B2_BUCKET_NAME}/"

echo "[$(date -Iseconds)] Sauvegarde terminée."
