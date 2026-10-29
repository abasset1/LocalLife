#!/usr/bin/env bash
# Vérifie, sur la vraie API OpenAgenda, que le filtre relative[]=current&relative[]=upcoming ne fait perdre aucun
# événement encore à venir — LECTURE SEULE (requêtes GET vers OpenAgenda, rien n'est écrit en base).
# Contexte : sans filtre, l'import télécharge tout l'historique des agendas (1,9 million d'événements pour ~100 000
# retenus, 28/10/2026). Le collecteur ne garde qu'un événement dont au moins un horaire finit maintenant ou plus tard
# (OpenAgendaCollector.hasUpcomingTiming) ; le filtre de l'API est sûr si aucun événement « passed » n'a un tel horaire.
#
# Usage (VPS, depuis infra/, la clé est lue dans .env.beta et jamais affichée) :
#   bash verification/openagenda_filtre_verif.sh 312 421 4 2857
# Arguments : ids d'agendas LocalLife (source.id) ; l'uid OpenAgenda est lu en base.
# Pour chaque agenda : totaux annoncés par l'API (tout, en cours + à venir, passé), puis examen des événements
# « passed » (jusqu'à 5 pages de 300) : aucun ne doit avoir d'horaire qui finit après maintenant.
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.beta}"
KEY="$(grep -E '^OPENAGENDA_API_KEY=' "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '"' | tr -d "'")"
if [ -z "$KEY" ]; then
    echo "OPENAGENDA_API_KEY introuvable dans $ENV_FILE" >&2
    exit 1
fi

api() {
    curl -sS -g --max-time 60 "https://api.openagenda.com/v2/agendas/$1/events?key=$KEY&$2"
}

total() {
    python3 -c 'import json,sys; print(json.load(sys.stdin).get("total"))'
}

for SOURCE_ID in "$@"; do
    AGENDA_UID="$(docker exec -i locallife-postgres-beta sh -c \
        "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -tAc \"SELECT agenda_uid FROM source WHERE id = $SOURCE_ID\"" \
        | tr -d '[:space:]')"
    if [ -z "$AGENDA_UID" ]; then
        echo "== Agenda $SOURCE_ID : pas d'uid OpenAgenda en base, ignoré"
        continue
    fi
    echo "== Agenda $SOURCE_ID (uid OpenAgenda $AGENDA_UID)"
    TOUT="$(api "$AGENDA_UID" "size=1" | total)"
    A_VENIR="$(api "$AGENDA_UID" "size=1&relative[]=current&relative[]=upcoming" | total)"
    PASSE="$(api "$AGENDA_UID" "size=1&relative[]=passed" | total)"
    echo "   total annoncé : tout = $TOUT ; en cours + à venir = $A_VENIR ; passé = $PASSE"

    # Examen des événements « passed » : un horaire qui finit maintenant ou plus tard serait perdu par le filtre.
    AFTER=""
    for PAGE in 1 2 3 4 5; do
        REPONSE="$(api "$AGENDA_UID" "size=300&detailed=1&relative[]=passed$AFTER")"
        RESULTAT="$(printf '%s' "$REPONSE" | python3 -c '
import json, re, sys
from datetime import datetime, timezone
data = json.load(sys.stdin)
now = datetime.now(timezone.utc)
events = data.get("events") or []
encore_a_venir = []
for event in events:
    timings = event.get("timings") or []
    if not timings and event.get("lastTiming"):
        timings = [event["lastTiming"]]
    for timing in timings:
        value = timing.get("end") or timing.get("begin")
        if not value:
            continue
        # Décalage « +0100 » (format OpenAgenda) réécrit en « +01:00 » pour Python 3.10.
        value = re.sub(r"([+-]\d{2})(\d{2})$", r"\1:\2", value.replace("Z", "+00:00"))
        moment = datetime.fromisoformat(value)
        if moment.tzinfo is None:
            moment = moment.replace(tzinfo=timezone.utc)
        if moment >= now:
            encore_a_venir.append(event.get("slug") or event.get("uid"))
            break
after = data.get("after")
print(len(events), len(encore_a_venir), ";".join(map(str, encore_a_venir[:5])), json.dumps(after), sep="|")
')"
        IFS="|" read -r NB PERDUS EXEMPLES SUITE <<< "$RESULTAT"
        echo "   page passé $PAGE : $NB événements examinés, $PERDUS encore à venir ${EXEMPLES:+(ex. $EXEMPLES)}"
        if [ "$NB" = "0" ] || [ "$SUITE" = "null" ] || [ "$SUITE" = "[]" ]; then
            break
        fi
        AFTER="$(printf '%s' "$SUITE" | python3 -c '
import json, sys
print("".join("&after[]=" + ("null" if v is None else str(v)) for v in json.load(sys.stdin)))')"
    done
done
echo "Attendu : « 0 encore à venir » sur chaque page examinée, et en cours + à venir + passé ≈ tout."
