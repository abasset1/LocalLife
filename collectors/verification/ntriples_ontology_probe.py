# Phase C (ADR-0011, § 7.3) : sonde ponctuelle de l'ontologie DATAtourisme telle qu'employée dans les données.
# Usage : python3 -I ntriples_ontology_probe.py OCTETS_COMPRESSES_A_LIRE [URL | début d'archive déjà téléchargé]
# Exécution du 13/10/2026 : 335544320 (320 Mo, 17 % de l'archive, 35 min) ; résultats dans ADR-0011, § 7.3.
# Lecture partielle seulement (requêtes HTTP partielles, ou fichier local), décompression à la volée, rien n'est
# écrit. Ne pas lancer sur le fichier entier sans décision.
"""L'ontologie (framagit, GitLab ADN Tourisme, datatourisme.fr) n'est pas joignable depuis l'environnement de
développement : la sonde reconstitue la partie utile à partir des données. Sur un préfixe de l'archive, elle mesure :
- l'inventaire des prédicats et des types (ontologie « employée ») et les éventuels triplets d'ontologie ;
- le profil des nœuds d'horaires (schema:OpeningHoursSpecification) et des nœuds datés (périodes) ;
- les prédicats qui mènent vers un nœud d'horaires, et le type du nœud de départ ;
- pour les marchés (core:Market) : chemins vers des horaires, et jours de la semaine déduits des dates de leurs
  périodes quand elles sont résolues.
Les nœuds liés étant dispersés dans le fichier, une chaîne n'est résolue que si tous ses nœuds sont dans le préfixe."""
import collections
import json
import struct
import subprocess
import sys
import time
import zlib
from datetime import date

URL = (sys.argv[2] if len(sys.argv) > 2 else "https://static.data.gouv.fr/resources/"
       "datatourisme-la-plateforme-nationale-des-donnees-touristiques/20261013-024517/datatourisme.nt.zip")
LIMIT = int(sys.argv[1])
CHUNK = 8 * 1024 * 1024
RDF_TYPE = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"
ONTOLOGY_PREDICATES = ("rdf-schema#domain>", "rdf-schema#range>", "rdf-schema#subPropertyOf>",
                       "rdf-schema#subClassOf>")
KEPT_TYPES = {"Market", "OpeningHoursSpecification", "Period"}
WEEKDAYS = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]


def local(uri):
    uri = uri.strip("<>")
    for sep in ("#", "/"):
        if sep in uri:
            uri = uri.rsplit(sep, 1)[1]
    return uri


def fetch(start, end):
    if not URL.startswith("http"):
        with open(URL, "rb") as handle:
            handle.seek(start)
            data = handle.read(end - start + 1)
        return data if len(data) == end - start + 1 else None
    for attempt in range(20):
        r = subprocess.run(["curl", "-sS", "-m", "300", "-r", f"{start}-{end}", URL], capture_output=True)
        if r.returncode == 0 and len(r.stdout) == end - start + 1:
            return r.stdout
        time.sleep(min(30, 2 * (attempt + 1)))
    return None


predicates = collections.Counter()
types = collections.Counter()
ontology_triples = collections.Counter()
interned, profiles = {}, []          # profil (types, prédicats) -> id
node_profile = {}                    # sujet -> id de profil
values = {}                          # sujet -> {prédicat local: [objets]} (marchés, horaires, nœuds datés)
incoming = {}                        # objet -> (sujet, prédicat local)
triples = 0


def flush(subject, po):
    if subject is None:
        return
    node_types = frozenset(local(o) for p, o in po if p == RDF_TYPE)
    preds = frozenset(local(p) for p, o in po)
    key = (node_types, preds)
    if key not in interned:
        interned[key] = len(profiles)
        profiles.append(key)
    node_profile[subject] = interned[key]
    dated = bool(preds & {"startDate", "endDate"}) and "PointOfInterest" not in node_types
    if node_types & KEPT_TYPES or dated:
        v = collections.defaultdict(list)
        for p, o in po:
            if p != RDF_TYPE:
                v[local(p)].append(o)
        values[subject] = dict(v)


t0 = time.time()
pos, dec, buf, cur_subject, cur = 0, None, b"", None, []
while pos < LIMIT:
    end = min(pos + CHUNK, LIMIT) - 1
    data = fetch(pos, end)
    if data is None:
        print(json.dumps({"arret": f"échec {pos}-{end}"}), flush=True)
        LIMIT = pos
        break
    if pos == 0:
        name_len, extra_len = struct.unpack("<HH", data[26:30])
        data = data[30 + name_len + extra_len:]
        dec = zlib.decompressobj(-15)
    pos = end + 1
    out = buf + dec.decompress(data)
    lines = out.split(b"\n")
    buf = lines.pop()
    for raw in lines:
        parts = raw.decode("utf-8", "replace").split(" ", 2)
        if len(parts) < 3:
            continue
        s, p, o = parts[0], parts[1], parts[2].rstrip(" .")
        triples += 1
        predicates[local(p)] += 1
        if p == RDF_TYPE:
            types[local(o)] += 1
        if p.endswith(ONTOLOGY_PREDICATES):
            ontology_triples[local(p)] += 1
        if o.startswith("<https://data.datatourisme.fr/") and p != RDF_TYPE:
            incoming[o] = (s, local(p))
        if s != cur_subject:
            flush(cur_subject, cur)
            cur_subject, cur = s, []
        cur.append((p, o))
    print(json.dumps({"lu_Mo": round(pos / 2**20), "triplets": triples, "s": round(time.time() - t0)}), flush=True)
flush(cur_subject, cur)


def has_type(node, name):
    return node in node_profile and name in profiles[node_profile[node]][0]


def type_of(node):
    return "/".join(sorted(profiles[node_profile[node]][0])) or "(sans type)" if node in node_profile else "(hors préfixe)"


ohs_nodes = [n for n in values if has_type(n, "OpeningHoursSpecification")]
market_nodes = [n for n in values if has_type(n, "Market")]
dated_nodes = [n for n in values if n not in set(ohs_nodes) and not has_type(n, "PointOfInterest")]
into_ohs = collections.Counter()
ohs_by_source = collections.defaultdict(list)
for n in ohs_nodes:
    if n in incoming:
        source, pred = incoming[n]
        into_ohs[f"{type_of(source)} --{pred}--> OpeningHoursSpecification"] += 1
        ohs_by_source[source].append(n)

paths = collections.Counter()
hours_examples = []
deduced = collections.Counter()
deduced_examples = []
for m in market_nodes:
    for pred in ("isLocatedAt", "takesPlaceAt"):
        for o in values[m].get(pred, []):
            paths[f"Market --{pred}--> {type_of(o)}"] += 1
    for place in values[m].get("isLocatedAt", []):
        for n in ohs_by_source.get(place, []):
            paths[f"Market --isLocatedAt--> lieu --{incoming[n][1]}--> horaires"] += 1
            if len(hours_examples) < 5:
                hours_examples.append({k: [local(x) if x.startswith("<") else x[:30] for x in v]
                                       for k, v in values[n].items()})
    resolved = [values[o] for o in values[m].get("takesPlaceAt", []) if o in values]
    if not resolved:
        continue
    single, long_, weekdays, day_props = 0, 0, set(), set()
    for period in resolved:
        day_props |= {k for k in period if "day" in k.lower() or "time" in k.lower()}
        try:
            start = date.fromisoformat(period.get("startDate", ['""'])[0].strip('"')[:10])
            end = date.fromisoformat(period.get("endDate", period.get("startDate", ['""']))[0].strip('"')[:10])
        except ValueError:
            continue
        if start == end:
            single += 1
            weekdays.add(start.weekday())
        elif (end - start).days > 31:
            long_ += 1
    if day_props:
        deduced["propriétés de jour ou d'heure sur la période : " + ",".join(sorted(day_props))] += 1
    if single and not long_:
        deduced[f"dates d'un jour seulement, {len(weekdays)} jour(s) de semaine"] += 1
        if len(deduced_examples) < 8:
            deduced_examples.append({"marche": values[m].get("label", ['""'])[0][:50], "dates": single,
                                     "jours": [WEEKDAYS[d] for d in sorted(weekdays)]})
    elif long_ and not single:
        deduced["périodes longues seulement"] += 1
    else:
        deduced["mixte ou illisible"] += 1

print(json.dumps({
    "prefixe_compresse_Mo": round(LIMIT / 2**20), "duree_s": round(time.time() - t0), "triplets": triples,
    "triplets_d_ontologie": dict(ontology_triples),
    "types_principaux": dict(types.most_common(25)),
    "predicats_principaux": dict(predicates.most_common(60)),
    "predicats_jour_heure": {k: v for k, v in predicates.items()
                             if any(w in k.lower() for w in ("day", "time", "opens", "closes", "valid", "hours",
                                                              "opening", "schedule"))},
    "noeuds_horaires": len(ohs_nodes),
    "profil_horaires": dict(collections.Counter(k for n in ohs_nodes for k in values[n])),
    "liens_vers_horaires": dict(into_ohs.most_common(10)),
    "noeuds_dates_hors_poi": len(dated_nodes),
    "types_des_noeuds_dates": dict(collections.Counter(type_of(n) for n in dated_nodes).most_common(8)),
    "profil_noeuds_dates": dict(collections.Counter(k for n in dated_nodes for k in values[n]).most_common(20)),
    "marches": len(market_nodes),
    "profil_marches": dict(collections.Counter(k for n in market_nodes for k in values[n]).most_common(30)),
    "chemins_marche": dict(paths.most_common(20)), "exemples_horaires_de_marche": hours_examples,
    "marches_avec_periodes_resolues": sum(v for k, v in deduced.items() if not k.startswith("propriétés")),
    "jours_deduits_des_dates": dict(deduced), "exemples_jours_deduits": deduced_examples,
}, ensure_ascii=False, indent=1))
