"""Couverture d'une liste officielle d'événements (JEP, Fête de la science) par les activités d'une source.

Usage :
    python3 coverage_check.py reference.csv activites.csv [manquants.csv]

reference.csv : fichier de reference/ (colonnes identifiant, titre_fr, ville, code_postal, premiere_date,
derniere_date, lien_canonique).
activites.csv : export des activités à comparer (colonnes title, start_date, end_date, postal_code, source),
par exemple produit par la requête SQL de docs/02_Architecture/VERIFICATION_JEP_FDS.md.

Un événement de référence est couvert par une activité si :
- leurs titres normalisés (minuscules, sans accents ni ponctuation) sont identiques, ou si au moins 80 % des
  mots du plus court figurent dans l'autre (même règle que CrossSourceDuplicateMatcher) ;
- leurs périodes se chevauchent (dates de début et de fin, au jour près) ;
- ils sont dans le même département (deux premiers chiffres du code postal).

Le script n'écrit que le fichier des manquants, s'il est demandé. Aucune connexion réseau ni base.
"""
import csv
import sys
import unicodedata
from collections import Counter, defaultdict
from datetime import date

MIN_WORD_OVERLAP = 0.8


def normalize(text):
    text = unicodedata.normalize("NFD", text or "")
    text = "".join(c for c in text if not unicodedata.combining(c)).lower()
    return " ".join("".join(c if c.isalnum() else " " for c in text).split())


def words(normalized):
    return {w for w in normalized.split(" ") if len(w) >= 2}


def close_titles(first, second):
    if first and first == second:
        return True
    a, b = words(first), words(second)
    if not a or not b:
        return False
    shorter, longer = (a, b) if len(a) <= len(b) else (b, a)
    return sum(1 for w in shorter if w in longer) >= MIN_WORD_OVERLAP * len(shorter)


def day(value):
    """Date ISO (les 10 premiers caractères), ou None si absente."""
    value = (value or "").strip()[:10]
    return date.fromisoformat(value) if value else None


def load(path):
    with open(path, encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def main(reference_path, activities_path, missing_path=None):
    reference = load(reference_path)
    activities = load(activities_path)
    by_department = defaultdict(list)
    for activity in activities:
        start = day(activity["start_date"])
        if start is None:
            continue
        end = day(activity.get("end_date")) or start
        by_department[activity["postal_code"].strip()[:2]].append(
            (normalize(activity["title"]), start, end, activity.get("source", "")))
    covered = Counter()
    missing = []
    undated = 0
    for event in reference:
        title = normalize(event["titre_fr"])
        first = day(event["premiere_date"])
        last = day(event["derniere_date"]) or first
        if first is None:
            undated += 1
        sources = {source for (candidate, start, end, source) in by_department[event["code_postal"].strip()[:2]]
                   if first and start <= last and end >= first and close_titles(title, candidate)}
        if sources:
            covered["toutes sources"] += 1
            for source in sources:
                covered[source] += 1
        else:
            missing.append(event)
    total = len(reference)
    print(f"Référence : {total} événements ({reference_path})")
    for source, count in covered.most_common():
        print(f"  couverts par {source} : {count} ({100 * count / total:.0f} %)" if total else "")
    print(f"  non couverts : {len(missing)} (dont {undated} sans date dans la référence)")
    if missing_path:
        with open(missing_path, "w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(reference[0].keys()), lineterminator="\n")
            writer.writeheader()
            writer.writerows(missing)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(*sys.argv[1:4])
