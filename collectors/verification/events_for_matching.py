"""Événements DATAtourisme → CSV d'entrée de DuplicateReport.java (ADR-0011, phase D). Lecture seule, sans réseau.

Usage :
    python3 -I events_for_matching.py export.csv DEPARTEMENT sortie.csv [--source-par-producteur]

Sortie : id,source,title,periods,latitude,longitude,address,city,urls.
- source : « DATAtourisme », ou, avec --source-par-producteur, le producteur de la donnée (office de tourisme,
  ville…) : deux producteurs d'un même fichier servent d'étalonnage quand une seconde source n'est pas disponible.
- periods : mêmes règles que le collecteur (DataTourismeCollector) : une période de plus de 31 jours n'est gardée
  que pour les types continus (Exhibition, Festival, FairOrShow) ; les autres en sont écartées.
- adresse générique : laissée telle quelle ; DuplicateReport.java la reconnaît avec la règle de l'application.
"""
import csv
import re
import sys
from datetime import date

CORE = "https://www.datatourisme.fr/ontology/core#"
CONTINUOUS = {"Exhibition", "Festival", "FairOrShow"}
MAX_DAYS = 31
URL = re.compile(r"https?://[^\s<>#|]+")


def periods(value, types):
    kept = []
    for period in (value or "").split("|"):
        bounds = period.split("<->")
        if len(bounds) != 2:
            continue
        try:
            start, end = date.fromisoformat(bounds[0].strip()[:10]), date.fromisoformat(bounds[1].strip()[:10])
        except ValueError:
            continue
        if (end - start).days + 1 > MAX_DAYS and not (types & CONTINUOUS):
            continue
        kept.append(f"{start}/{end}")
    return ";".join(kept)


def main(path, department, output, by_producer):
    csv.field_size_limit(10 ** 9)
    written = skipped = 0
    with open(path, encoding="utf-8", newline="") as handle, open(output, "w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out, lineterminator="\n")
        writer.writerow(["id", "source", "title", "periods", "latitude", "longitude", "address", "city", "urls"])
        for row in csv.DictReader(handle):
            postal_city = row["Code_postal_et_commune"].split("#")
            types = {u[len(CORE):] for u in row["Categories_de_POI"].split("|") if u.startswith(CORE)}
            if "EntertainmentAndEvent" not in types or not postal_city[0].startswith(department):
                continue
            days = periods(row["Periodes_regroupees"], types)
            if not days:
                skipped += 1
                continue
            source = row["Createur_de_la_donnee"].split("#")[0].strip() if by_producer else "DATAtourisme"
            writer.writerow([row["URI_ID_du_POI"], source, row["Nom_du_POI"].strip(), days, row["Latitude"],
                             row["Longitude"], row["Adresse_postale"].strip(),
                             postal_city[1].strip() if len(postal_city) > 1 else "",
                             " ".join(sorted(set(URL.findall(row["Contacts_du_POI"]))))])
            written += 1
    print(f"{written} événements écrits dans {output} ; {skipped} sans période retenue (règle des 31 jours).")


if __name__ == "__main__":
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3], "--source-par-producteur" in sys.argv[4:])
