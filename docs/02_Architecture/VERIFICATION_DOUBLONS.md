# Doublons entre sources — mesure sans écriture (ADR-0011, phase D)

Décision d'Alex (13/10/2026) : **mesure sans écriture** autorisée. Aucune
modification des données, aucune fusion automatique, aucun changement de
visibilité. Critères et résultats reproductibles ; rapport classant les
correspondances en **certaines, probables et ambiguës**, avec mesure des
faux positifs.

**Statut : outil implémenté et testé ; étalonné sur des données publiques
DATAtourisme ; mesure OpenAgenda ↔ DATAtourisme sur les données de
l'application non faite** (voir « Ce qui reste à mesurer »).

## Outils (lecture seule, sans réseau)

| Fichier | Rôle |
|---|---|
| `collectors/verification/DuplicateReport.java` | calcule et classe les paires ; appelle **la règle de l'application telle quelle** (`CrossSourceDuplicateMatcher`, `CoordinatePrecisionPolicy`) ; autotest de 10 vérifications |
| `collectors/verification/events_for_matching.py` | convertit un export DATAtourisme en fichier d'entrée (mêmes règles de périodes que le collecteur) |
| `infra/verification/phase_d_export_doublons.sql` | export des activités de l'application (lecture seule, contributions exclues) |
| `collectors/verification/reference/etalonnage-doublons-datatourisme-13-2026-10-13.csv` | paires de l'étalonnage avec verdict et justification |

```sh
(cd backend && mvn -q compile)
java --class-path backend/target/classes collectors/verification/DuplicateReport.java --self-test
java --class-path backend/target/classes collectors/verification/DuplicateReport.java entree.csv sortie
```

Sorties : un résumé, `sortie-paires.csv` (toutes les paires classées) et
`sortie-echantillon.csv` (20 paires par classe au plus, tirées avec une
graine fixe, colonne de verdict à remplir).

## Critères

Seules sont comparées deux activités de **sources différentes** ayant **au
moins un jour commun**. La règle (`CrossSourceDuplicateMatcher`) donne
`PROBABLE` (titre normalisé identique et non générique, lieux précis à
300 m au plus), `POSSIBLE` (titre proche ou générique, ou position
approximative dans la même commune) ou `NONE`.

| Classe | Définition | Usage prévu |
|---|---|---|
| **Certaine** | les deux activités partagent un **lien distinctif** : même URL complète (avec un chemin, hors page d'accueil), portée par une seule activité dans chaque source. Preuve **indépendante** du titre et de la position | vérité de terrain : mesure du rappel de la règle et des faux positifs sûrs |
| **Probable** | règle `PROBABLE`, sans lien distinctif, et aucune des deux activités n'a d'autre candidat | candidate à un futur lien automatique, après mesure |
| **Ambiguë** | règle `POSSIBLE`, ou `PROBABLE` avec plusieurs candidats pour une même activité | relecture humaine uniquement |

Faux positifs : (1) **sûrs**, calculés : paire probable alors qu'une des
deux activités a une paire certaine avec une autre ; (2) **relus** : verdict
humain `MEME` / `DIFFERENT` / `INCERTAIN` sur l'échantillon. Les faux
négatifs (doublons que la règle ne propose pas du tout) ne sont mesurés que
sur les paires certaines.

## Étalonnage sur données publiques (13/10/2026)

Aucune donnée OpenAgenda n'est accessible sans clé depuis l'environnement
de développement (les exports publics du Loiret et d'Opendatasoft sont
bloqués par la politique réseau). L'étalonnage utilise donc l'export
DATAtourisme PACA du 13/10/2026, en traitant **chaque producteur** (office
de tourisme, mairie) comme une source distincte : deux producteurs
indépendants qui décrivent les mêmes événements, situation proche
d'OpenAgenda ↔ DATAtourisme.

| Département | Événements | Certaines | Probables | Ambiguës |
|---|---|---|---|---|
| 13 | 2 207 | 0 | 1 | 14 |
| 04, 05, 06, 83, 84 | — | 0 | 0 | 0 |

Relecture des 15 paires du département 13 (toutes, moins de 20 par
classe ; relecture faite par l'assistant, **à confirmer par un humain**) :

| Classe | Paires | Mêmes événements | Différents | Faux positifs |
|---|---|---|---|---|
| Probable | 1 | 1 | 0 | 0 sur 1 |
| Ambiguë | 14 | 11 | 3 | 3 sur 14 (21 %) |

Les 3 faux positifs : animation autour d'une exposition contre
l'exposition ; soirée d'ouverture contre biennale entière ; performance
contre exposition de même titre. Les 11 vrais doublons ambigus diffèrent
surtout par un **préfixe de type** (« Exposition - Futurs solaires » /
« Futurs solaires »).

Conclusions de l'étalonnage :

1. **Aucune paire certaine** : les producteurs ne partagent aucun lien
   distinctif (8 liens communs, tous des pages d'accueil). Côté
   DATAtourisme, 2 événements sur 3 565 citent OpenAgenda, et ce sont des
   pages d'agenda : la preuve par lien a peu de chances de fonctionner pour
   OpenAgenda ↔ DATAtourisme. La certitude devra venir d'une confirmation
   humaine.
2. **Règle « probable » prudente** : 0 faux positif, mais elle ne propose
   qu'1 des 12 vrais doublons relus.
3. **Les ambiguës ne doivent jamais être liées automatiquement** : 21 % de
   faux positifs.
4. Piste, **non implémentée** : retirer un préfixe de type en tête de titre
   (« Exposition - », « Spectacle - », « Spectacle BD : », « Performance - »)
   rend 5 paires identiques ; l'une garde deux candidats (« La
   responsabilité des rêves » : exposition et performance) et reste
   ambiguë. Effet calculé : **4 des 11 doublons ambigus deviendraient
   probables, sans nouveau faux positif** sur ces 15 paires. Échantillon
   trop petit pour décider : à remesurer sur les données de l'application.

Limites : un seul département exploitable, 15 paires ; faux négatifs hors
paires proposées non mesurés ; étalonnage intra-DATAtourisme, pas
OpenAgenda ↔ DATAtourisme.

## Ce qui reste à mesurer : données de l'application

1. L'administrateur exécute `infra/verification/phase_d_export_doublons.sql`
   (lecture seule) et transmet `activites-doublons-84.csv` (titres,
   jours, positions, adresses et URL d'activités publiques ; pas de
   contribution d'utilisateur ; fichier ignoré par Git, supprimé après le
   rapport).
2. `DuplicateReport.java` sur ce fichier ; relecture humaine de
   l'échantillon (20 paires par classe).
3. Rapport : nombres par classe, faux positifs relus et sûrs, rappel sur les
   paires certaines s'il y en a, puis décision sur l'étape 2 du § 7.4 de
   l'ADR (liens réversibles).

Aujourd'hui, la seule source DATAtourisme est inactive ou récente : si la
base ne contient pas encore d'activités DATAtourisme, la mesure portera sur
les recouvrements entre agendas OpenAgenda (Ville d'Avignon, Ministère de la
Culture), utile aussi.
