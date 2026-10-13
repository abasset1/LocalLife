# Étude ponctuelle (ADR-0011, § 2.8). Usage : python3 ntriples_market_study.py OCTETS_COMPRESSES_A_LIRE
# Exemple du 13/10/2026 : 503316480 (480 Mo, 26 % du fichier). Ne pas lancer sur le fichier entier sans décision.
"""Étude limitée du fichier DATAtourisme N-Triples : lecture en flux d'un préfixe de l'archive (requêtes HTTP
partielles), décompression à la volée, aucune écriture sur disque. Mesure : marchés, périodes, horaires
d'ouverture, chaînes marché -> période -> horaires résolues dans le préfixe, débit."""
import json, subprocess, sys, time, zlib, struct, collections
URL = "https://static.data.gouv.fr/resources/datatourisme-la-plateforme-nationale-des-donnees-touristiques/20261013-024517/datatourisme.nt.zip"
TOTAL = 1930194831
LIMIT = int(sys.argv[1])          # octets compressés à lire
CHUNK = 8 * 1024 * 1024
CORE = "https://www.datatourisme.fr/ontology/core#"
def fetch(start, end):
    for attempt in range(20):
        r = subprocess.run(["curl", "-sS", "-m", "300", "-r", f"{start}-{end}", URL], capture_output=True)
        if r.returncode == 0 and len(r.stdout) == end - start + 1:
            return r.stdout
        time.sleep(min(30, 2 * (attempt + 1)))
    return None
markets = {}                       # uri -> {label, periods:[], start, end}
periods = {}                       # uri -> list of (pred, obj) ; noeuds ayant startDate/endDate
ohs = {}                           # uri -> {day:[], opens, closes}
pred_counter = collections.Counter()
triples = 0; t0 = time.time(); done = 0
dec = None; buf = b""; header_skipped = False
cur_subject = None; cur = []
def flush(subject, po):
    if subject is None: return
    types = {o for p, o in po if p.endswith("22-rdf-syntax-ns#type>")}
    if f"<{CORE}Market>" in types:
        markets[subject] = {
            "label": next((o for p, o in po if p.endswith("rdf-schema#label>") and o.endswith("@fr")), None),
            "periods": [o for p, o in po if p == f"<{CORE}takesPlaceAt>"],
        }
    if "<http://schema.org/OpeningHoursSpecification>" in types:
        ohs[subject] = {
            "days": [o for p, o in po if p.endswith("dayOfWeek>")],
            "opens": next((o for p, o in po if p.endswith("opens>")), None),
            "closes": next((o for p, o in po if p.endswith("closes>")), None),
        }
    if any(p.endswith("startDate>") or p.endswith("endDate>") for p, o in po) and f"<{CORE}Market>" not in types:
        periods[subject] = po
pos = 0
while pos < LIMIT:
    end = min(pos + CHUNK, LIMIT) - 1
    data = fetch(pos, end)
    if data is None:
        print(json.dumps({"arret": f"échec de téléchargement {pos}-{end}", "lu_Mo": round(pos / 2**20)}), flush=True)
        LIMIT = pos
        break
    pos = end + 1
    if not header_skipped:
        nlen, elen = struct.unpack("<HH", data[26:30]); data = data[30 + nlen + elen:]
        dec = zlib.decompressobj(-15); header_skipped = True
    out = buf + dec.decompress(data)
    lines = out.split(b"\n"); buf = lines.pop()
    for raw in lines:
        parts = raw.decode("utf-8", "replace").split(" ", 2)
        if len(parts) < 3: continue
        s, p, o = parts[0], parts[1], parts[2].rstrip(" .")
        triples += 1
        if s != cur_subject:
            flush(cur_subject, cur); cur_subject, cur = s, []
        cur.append((p, o))
    done = pos
    el = time.time() - t0
    print(json.dumps({"lu_Mo": round(done / 2**20), "triplets": triples, "s": round(el), "marches": len(markets),
                      "periodes": len(periods), "horaires": len(ohs)}), flush=True)
flush(cur_subject, cur)
# prédicats des noeuds de période (pour trouver le lien vers les horaires)
for po in periods.values():
    for p, o in po: pred_counter[p] += 1
period_links_to_ohs = collections.Counter(p for po in periods.values() for p, o in po if o in ohs)
resolved = with_hours = 0; days_dist = collections.Counter(); examples = []
for m, info in markets.items():
    ps = [periods[x] for x in info["periods"] if x in periods]
    if ps: resolved += 1
    hours = [ohs[o] for po in ps for p, o in po if o in ohs]
    if hours:
        with_hours += 1
        days = sorted({d.split("/")[-1].rstrip(">") for h in hours for d in h["days"]})
        days_dist[len(days)] += 1
        if len(examples) < 5:
            examples.append({"marche": info["label"], "jours": days,
                             "horaires": sorted({(h["opens"] or "")[1:9] + "-" + (h["closes"] or "")[1:9] for h in hours})})
el = time.time() - t0
print(json.dumps({
    "prefixe_compresse_Mo": round(LIMIT / 2**20), "part_du_fichier": round(LIMIT / TOTAL, 3), "duree_s": round(el),
    "debit_Mo_compresses_par_s": round(LIMIT / 2**20 / el, 2), "triplets": triples,
    "marches": len(markets), "marches_avec_periode_resolue": resolved, "marches_avec_horaires": with_hours,
    "nb_jours_distincts_par_marche": dict(days_dist), "noeuds_periode": len(periods), "noeuds_horaires": len(ohs),
    "liens_periode_vers_horaires": dict(period_links_to_ohs.most_common(5)),
    "predicats_des_periodes": dict(pred_counter.most_common(12)), "exemples": examples}, ensure_ascii=False, indent=1))
