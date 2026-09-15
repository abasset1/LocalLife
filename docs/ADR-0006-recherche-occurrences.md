# ADR-0006 — Recherche occurrence-aware avec repli sur les colonnes plates (LL-11012)

## Contexte

Le Sprint 11 a introduit un modèle événementiel riche (`Location`,
`Schedule`, `Occurrence`) en parallèle des colonnes plates historiques
d'`Activity` (`latitude`/`longitude`/`city`/`start_date`/`end_date`).
LL-11011 a rendu le pipeline d'import capable de peupler ce modèle riche,
mais **seulement pour les activités importées** : une contribution
manuelle (`ActivityService#createActivity`, LL-2012/LL-3012) continue de
n'écrire que les colonnes plates d'`Activity` — elle ne crée aucune
`Location` ni aucun `Schedule`.

LL-11012 (`docs/05_Sprints/SPRINT_11.md`, section 15) demande de faire
évoluer la recherche (`GET /api/v1/activities`, `/nearby`,
`/within-bounds`) pour qu'elle prenne en compte ce modèle riche —
notamment plusieurs horaires, plusieurs schedules/villes pour une même
activité, les occurrences annulées (« exceptions »), et un changement
exceptionnel de lieu pour une occurrence donnée.

## Décision

### Double condition : occurrence-aware si `schedule` existe, repli sinon

Chacune des trois requêtes de recherche (`findAllFiltered`,
`findWithinRadius`, `findWithinBounds`) évalue désormais, pour chaque
activité candidate, l'une des deux conditions suivantes (mutuellement
exclusives) :

1. **L'activité a au moins un `schedule`** (`EXISTS (SELECT 1 FROM
   schedule WHERE activity_id = a.id)`) : le filtre (ville, position
   géographique, date) est évalué sur ses `occurrence` **non annulées**
   (`status <> 'CANCELLED'`), en résolvant pour chacune son lieu effectif
   via `COALESCE(occurrence.location_id, schedule.location_id)`.
2. **Sinon** (aucun `schedule` — contribution manuelle, ou toute activité
   dont l'enrichissement LL-11011 aurait échoué) : repli intégral sur le
   comportement précédent (`activity.city`/`activity.location`/
   `activity.start_date`/`activity.end_date`), strictement inchangé.

Ce choix binaire (jamais un mélange des deux pour une même activité) est
volontaire : une activité enrichie par LL-11011 doit être jugée
uniquement sur des données à jour (ses occurrences), pas sur les colonnes
plates d'`Activity`, qui ne reflètent plus qu'un instantané pris au
premier import (voir la javadoc de `NormalizationService#normalize`) —
même logique de « la donnée la plus riche disponible fait autorité » que
la stratégie de remplacement décrite dans ADR-0005.

Cette double condition couvre nativement tous les cas listés par le
ticket (« le système doit gérer ») :

| Cas du ticket | Couvert par |
| --- | --- |
| Activité ponctuelle | Une activité avec un seul `schedule` et une seule `occurrence`. |
| Activité récurrente | Plusieurs `occurrence` pour le même `schedule` (matérialisées par LL-11004, pas de recalcul de RRULE à la recherche). |
| Plusieurs schedules | La condition `EXISTS` porte sur **toutes** les occurrences de **tous** les schedules de l'activité, sans distinction. |
| Plusieurs villes | Conséquence directe du point précédent : chaque occurrence peut résoudre une ville différente. |
| Exceptions | `occurrence.status <> 'CANCELLED'` exclut une occurrence annulée d'une série par ailleurs récurrente. |
| Changement exceptionnel de lieu | `COALESCE(occurrence.location_id, schedule.location_id)` : le lieu de l'occurrence prévaut sur celui du schedule quand il est renseigné. |
| Horaires | Non filtré explicitement par ce ticket (seuls ville/date/géographie le sont) — la donnée existe déjà sur `Schedule#startTime`/`endTime`, disponible pour un futur ticket. |

### Fuseau horaire : `COALESCE(location.timezone, 'Europe/Paris')`

`occurrence.start_at`/`end_at` sont des instants (`TIMESTAMPTZ`, voir la
javadoc d'`Occurrence`), tandis qu'une date de recherche (`date`) est un
jour civil sans fuseau. Les comparer exige de choisir un fuseau ; celui
du lieu résolu (`location.timezone`) est repris, avec le même repli
`'Europe/Paris'` que `NormalizationService#resolveZone` à l'écriture —
cohérence entre écriture et lecture plutôt qu'une dépendance implicite au
fuseau du serveur PostgreSQL (potentiellement UTC).

### Tri par ville/date (`findAllFiltered`) : toujours sur les colonnes propres d'`Activity`

Contrairement au filtre (qui doit être occurrence-aware pour être
correct), le **tri** de `GET /api/v1/activities?sort=city,date` continue
d'utiliser `activity.city`/`activity.start_date` pour toute activité,
qu'elle ait ou non des occurrences. ⚠️ Décision à valider : une activité
ne correspondant à la recherche que via une occurrence dans une ville
différente de sa `city` par défaut sera triée selon cette dernière, pas
selon la ville effectivement trouvée. Justification : `activity.city`/
`start_date` restent renseignées à l'import (première/unique localisation
connue à ce moment, voir `NormalizationService#normalize`) et donnent une
valeur de tri pertinente dans l'immense majorité des cas ; trier par « la
ville/date de l'occurrence qui a fait matcher l'activité » demanderait de
désigner cette occurrence précise dans le résultat retourné (LL-11013,
pas ce ticket).

### Tri par distance (`findWithinRadius`) : `LEAST` de deux sous-requêtes

Le tri par distance ne peut pas se contenter des colonnes propres
d'`Activity` (pas de notion de « distance » stockée). La distance
retenue pour une activité est le plus petit des deux nombres suivants
(`COALESCE(..., 'Infinity'::float8)` pour neutraliser la branche qui ne
s'applique pas) :

- la plus petite distance parmi ses occurrences correspondantes (au cas
  où plusieurs, dans des lieux différents, seraient toutes dans le
  rayon demandé) ;
- la distance de sa localisation historique propre (`activity.location`),
  pour la branche de repli.

Implémentée par une sous-requête corrélée dans la clause `ORDER BY`
plutôt que par un `UNION`/colonne calculée ajoutée au `SELECT` : évite
tout risque de perturber le mapping Spring Data JDBC de `Activity`
(colonnes du `SELECT` inchangées, `a.*` comme avant ce ticket) — voir
« Alternative écartée » ci-dessous.

## Alternative écartée : `UNION ALL` avec colonne `distance` calculée

Une première approche envisagée consistait à construire le résultat par
`UNION ALL` de deux `SELECT a.*, <distance> AS distance FROM ...`
(branche occurrence / branche repli), avec un `ORDER BY distance` global
après l'union. Écartée : bien que standard en SQL, cette forme ajoute une
colonne `distance` au jeu de résultats, dont le comportement face au
mapping automatique de Spring Data JDBC vers l'entité `Activity` (20
colonnes exactement) n'est pas vérifiable dans cet environnement (pas
d'accès à une vraie base PostgreSQL, voir plus bas) — la sous-requête
corrélée en `ORDER BY` obtient le même résultat sans toucher à la forme
du `SELECT` principal, donc sans introduire ce risque.

## Conséquences

- `ActivityRepository#findAllFiltered` gagne un paramètre `date`
  (`LocalDate`, nullable) — signature élargie, tous les appelants
  (`ActivityService#findAll`, `ActivityController#getAllActivities`,
  leurs tests) mis à jour en conséquence.
- `GET /api/v1/activities` accepte désormais un paramètre `date` (voir
  `docs/02_Architecture/LOCATION_CONTRACT.md`), sans fenêtre par défaut
  contrairement à `/nearby`/`/within-bounds` (reste une liste brute).
- Les trois requêtes deviennent sensiblement plus complexes (sous-requêtes
  `EXISTS`/corrélées répétées textuellement entre `findWithinRadius` et
  `findWithinBounds`, comme le reste de ce repository le faisait déjà
  pour le filtre de date avant ce ticket) — accepté au nom de la
  simplicité de chaque requête individuelle plutôt que d'une factorisation
  SQL plus risquée (voir alternative écartée).
- ⚠️ Cette session n'a pas d'accès réseau à Maven Central ni à une base
  PostgreSQL/PostGIS réelle : ces requêtes SQL n'ont pu être vérifiées que
  par relecture manuelle attentive (cohérence des parenthèses, alias,
  types castés), pas exécutées. Des tests d'intégration couvrant chaque
  cas du tableau ci-dessus ont été ajoutés à
  `ActivityRepositoryIntegrationTest`, mais **`mvn verify` reste à
  exécuter par Alex avant tout merge** (même limitation déjà documentée
  dans ADR-0004/ADR-0005).
