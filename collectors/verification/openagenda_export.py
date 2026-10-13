"""Export des événements d'un agenda OpenAgenda au format de coverage_check.py.

Usage :
    OPENAGENDA_API_KEY=... python3 openagenda_export.py AGENDA_UID DEPARTEMENT DU AU sortie.csv
    python3 openagenda_export.py --self-test

Exemple (Journées européennes du patrimoine, agenda national, Vaucluse, édition 2025) :
    OPENAGENDA_API_KEY=... python3 openagenda_export.py 2883956 84 2025-09-19 2025-09-21 jep-openagenda.csv

Sortie : colonnes title, start_date, end_date, postal_code, source (source = « OpenAgenda <uid> »).

Sécurité : la clé est lue dans la variable d'environnement OPENAGENDA_API_KEY, jamais en argument (elle
apparaîtrait dans l'historique du shell) ; l'URL d'appel, qui la contient, n'est jamais affichée ni écrite.
Lecture seule : le script n'appelle que la lecture des événements.

Non vérifié contre l'API réelle (pas de clé dans l'environnement de développement) : la pagination
(paramètre after) et les champs lus reprennent ceux du collecteur Java (OpenAgendaCollector).
"""
import csv
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.openagenda.com/v2/agendas/{uid}/events"
PAGE_SIZE = 300
MAX_PAGES = 500


def rows_from_events(events, department, uid):
    """Événements OpenAgenda → lignes de comparaison, filtrées sur le département (préfixe de code postal)."""
    rows = []
    for event in events:
        location = event.get("location") or {}
        postal_code = str(location.get("postalCode") or "")
        if not postal_code.startswith(department):
            continue
        timings = event.get("timings") or [t for t in (event.get("firstTiming"), event.get("lastTiming")) if t]
        begins = sorted(t["begin"][:10] for t in timings if t.get("begin"))
        ends = sorted((t.get("end") or t["begin"])[:10] for t in timings if t.get("begin"))
        if not begins:
            continue
        title = (event.get("title") or {}).get("fr") or next(iter((event.get("title") or {}).values()), "")
        rows.append([title, begins[0], ends[-1], postal_code, f"OpenAgenda {uid}"])
    return rows


def fetch_page(uid, key, date_from, date_to, after):
    params = [("key", key), ("size", PAGE_SIZE), ("detailed", 1),
              ("timings[gte]", f"{date_from}T00:00:00"), ("timings[lte]", f"{date_to}T23:59:59")]
    for value in after or []:
        params.append(("after[]", "null" if value is None else value))
    url = API.format(uid=uid) + "?" + urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # Jamais l'URL : elle contient la clé.
        sys.exit(f"OpenAgenda a répondu HTTP {error.code} pour l'agenda {uid}.")
    except urllib.error.URLError as error:
        sys.exit(f"OpenAgenda injoignable : {error.reason}")


def export(uid, department, date_from, date_to, output):
    key = os.environ.get("OPENAGENDA_API_KEY", "").strip()
    if not key:
        sys.exit("Variable d'environnement OPENAGENDA_API_KEY absente.")
    rows, after, pages = [], None, 0
    while pages < MAX_PAGES:
        body = fetch_page(uid, key, date_from, date_to, after)
        events = body.get("events") or []
        if not events:
            break
        rows.extend(rows_from_events(events, department, uid))
        after = body.get("after")
        pages += 1
        if not after:
            break
    with open(output, "w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["title", "start_date", "end_date", "postal_code", "source"])
        writer.writerows(rows)
    print(f"{len(rows)} événements du département {department} écrits dans {output} ({pages} pages lues).")


def self_test():
    events = [
        {"title": {"fr": "Visite du cloître"}, "location": {"postalCode": "84000"},
         "timings": [{"begin": "2025-09-20T10:00:00+0200", "end": "2025-09-20T11:00:00+0200"},
                     {"begin": "2025-09-21T10:00:00+0200", "end": "2025-09-21T11:00:00+0200"}]},
        {"title": {"fr": "Hors département"}, "location": {"postalCode": "13001"},
         "timings": [{"begin": "2025-09-20T10:00:00+0200"}]},
        {"title": {"en": "Sans titre français"}, "location": {"postalCode": "84800"},
         "firstTiming": {"begin": "2025-09-19T18:00:00+0200"}},
        {"title": {"fr": "Sans horaire"}, "location": {"postalCode": "84000"}, "timings": []},
    ]
    rows = rows_from_events(events, "84", "2883956")
    assert rows == [
        ["Visite du cloître", "2025-09-20", "2025-09-21", "84000", "OpenAgenda 2883956"],
        ["Sans titre français", "2025-09-19", "2025-09-19", "84800", "OpenAgenda 2883956"],
    ], rows
    print("Autotest réussi : filtre de département, dates extrêmes, titre de repli, événement sans horaire ignoré.")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 6:
        export(*sys.argv[1:6])
    else:
        sys.exit(__doc__)
