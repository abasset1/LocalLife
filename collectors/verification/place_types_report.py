"""Types de lieux DATAtourisme d'un département : volumes, qualité, et impact d'une liste blanche (ADR-0011, phase B).

Usage :
    python3 -I place_types_report.py export.csv DEPARTEMENT [--whitelist T1,T2,...] [--exclude T1,T2,...]

export.csv : export CSV DATAtourisme publié sur data.gouv.fr (fichier régional ou national).
Lecture seule, sans réseau : le script ne lit que le fichier donné et n'écrit rien.

Un lieu est une ligne dont les catégories contiennent PlaceOfInterest, sans EntertainmentAndEvent (événement),
Product ni Tour. Chaque ligne porte tous ses types, parents compris. Faute d'ontologie lisible, la hiérarchie
est déduite du fichier entier : A est parent de B si toute ligne de type B est aussi de type A. Les types
« précis » d'un lieu sont ceux qui ne sont parents d'aucun autre de ses types.

Liste blanche : un lieu est retenu s'il a au moins un type précis de la liste ET aucun type de la liste
d'exclusion (tous types, parents compris). Un type parent de la liste blanche ne retient que les lieux dont il est
le type précis : aucun sous-type n'est retenu implicitement.
"""
import csv
import re
import sys
from collections import Counter, defaultdict
from datetime import date

CORE = "https://www.datatourisme.fr/ontology/core#"
# Itinéraires présentés comme des lieux (« Circuit piéton à la découverte de… ») : à relire en modération.
ROUTE_NAME = re.compile(r"^\W*(circuit|balade|parcours|sentier|randonn|itin[ée]raire|boucle)", re.IGNORECASE)
NOT_PLACES = {"EntertainmentAndEvent", "Product", "Tour"}
RECENT_DAYS = 730


def types_of(row):
    """Types DATAtourisme (préfixe core retiré) ; les types schema.org sont ignorés."""
    return {uri[len(CORE):] for uri in row["Categories_de_POI"].split("|") if uri.startswith(CORE)}


def parents_by_type(rows):
    """Parents déduits : A parent de B si toute ligne de type B porte aussi A."""
    common = {}
    for row in rows:
        types = types_of(row)
        for t in types:
            common[t] = (common[t] & types) if t in common else set(types)
    return {t: shared - {t} for t, shared in common.items()}


def precise_types(types, parents):
    implied = set().union(*(parents.get(t, set()) for t in types)) if types else set()
    return types - implied


def is_recent(value, today):
    try:
        return (today - date.fromisoformat(value.strip()[:10])).days <= RECENT_DAYS
    except ValueError:
        return False


def option(name):
    if name in sys.argv:
        return {t for t in sys.argv[sys.argv.index(name) + 1].split(",") if t}
    return None


def main(path, department):
    csv.field_size_limit(10 ** 9)
    with open(path, encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    parents = parents_by_type(rows)
    today = date.today()
    places = [r for r in rows if r["Code_postal_et_commune"].startswith(department)
              and "PlaceOfInterest" in types_of(r) and not (types_of(r) & NOT_PLACES)]
    stats = defaultdict(Counter)
    examples = defaultdict(list)
    for row in places:
        for t in precise_types(types_of(row), parents):
            s = stats[t]
            s["lieux"] += 1
            s["adresse"] += bool(row["Adresse_postale"].strip())
            s["description"] += bool(row["Description"].strip())
            s["site"] += "http" in row["Contacts_du_POI"]
            s["maj_2_ans"] += is_recent(row["Date_de_mise_a_jour"], today)
            if len(examples[t]) < 3:
                examples[t].append(row["Nom_du_POI"].strip())
    print(f"Fichier : {path} ({len(rows)} lignes) — département {department} : {len(places)} lieux")
    print("type précis;parents (déduits);lieux;% adresse;% description;% site web;% mis à jour < 2 ans;exemples")
    for t, s in sorted(stats.items(), key=lambda item: -item[1]["lieux"]):
        n = s["lieux"]
        family = ",".join(sorted(parents.get(t, set()) - {"PointOfInterest", "PlaceOfInterest"}))
        print(f"{t};{family};{n};{100 * s['adresse'] // n};{100 * s['description'] // n};"
              f"{100 * s['site'] // n};{100 * s['maj_2_ans'] // n};{' / '.join(examples[t])}")
    whitelist, exclude = option("--whitelist"), option("--exclude") or set()
    if whitelist is not None:
        kept = [r for r in places if precise_types(types_of(r), parents) & whitelist and not types_of(r) & exclude]
        excluded = [r for r in places if precise_types(types_of(r), parents) & whitelist and types_of(r) & exclude]
        print(f"\nListe blanche ({len(whitelist)} types) : {len(kept)} lieux retenus ; "
              f"{len(excluded)} écartés par l'exclusion ({', '.join(sorted(exclude)) or 'aucune'})")
        unknown = whitelist - set(parents)
        if unknown:
            print(f"Types de la liste absents du fichier : {', '.join(sorted(unknown))}")
        by_type = Counter(t for r in kept for t in precise_types(types_of(r), parents) & whitelist)
        print("; ".join(f"{t} {n}" for t, n in by_type.most_common()))
        print("Exemples écartés par l'exclusion : " + " / ".join(r["Nom_du_POI"].strip() for r in excluded[:8]))
        no_address = [r for r in kept if not r["Adresse_postale"].strip()]
        routes = [r for r in kept if ROUTE_NAME.search(r["Nom_du_POI"])]
        names = Counter((r["Nom_du_POI"].strip().lower(), r["Code_postal_et_commune"]) for r in kept)
        duplicates = sum(n - 1 for n in names.values() if n > 1)
        print(f"Qualité des lieux retenus : {len(no_address)} sans adresse ; {len(routes)} itinéraires nommés comme "
              f"tels ; {duplicates} doublons de nom dans la même commune")
        print("Itinéraires : " + " / ".join(r["Nom_du_POI"].strip() for r in routes[:6]))
        print("Sans adresse : " + " / ".join(r["Nom_du_POI"].strip() for r in no_address[:8]))


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
