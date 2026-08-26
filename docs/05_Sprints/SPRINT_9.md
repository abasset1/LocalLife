# Sprint 9 — Corrections post-bêta

**Statut :** ⏳ À faire

---

# Objectif

Sprint 8 clôturé avec une décision **GO bêta conditionnel** (LL-8009,
voir `docs/PROJECT_STATUS.md`). Ce sprint traite, un par un, les
correctifs identifiés par Alex pendant/après la bêta — chaque ticket
est autonome et testable indépendamment, dans la continuité de la
méthode adoptée en Sprint 8.

---

# Périmètre

## Inclus

- corrections de comportement identifiées sur la baseline bêta ;
- chaque correctif traité comme un ticket isolé, avec ses propres
  critères d'acceptation et sa propre vérification.

## Exclus

- nouveau domaine métier ;
- refonte graphique importante ;
- optimisation d'architecture non justifiée par un problème réel.

(Liste susceptible d'être complétée à mesure que Alex identifie
d'autres correctifs.)

---

# Tickets

---

## LL-9001 — Ne plus afficher les activités hors période sur les recherches publiques

**Priorité : Haute**

**Statut : ✅ traité (en attente de confirmation `mvn verify` par Alex) — voir `docs/PROJECT_STATUS.md` pour le détail de l'implémentation et des décisions retenues.**

**Dépendance :** aucune (indépendant des tickets Sprint 8).

### Constat

Signalé par Alex le 26/08/2026, confirmé par relecture de
`ActivityRepository`/`ActivityService` : les recherches publiques
(`GET /api/v1/activities/nearby`, `GET /api/v1/activities/within-bounds`)
ne filtrent aujourd'hui que sur le statut (`PUBLISHED` uniquement,
LL-6004). Aucun filtre n'exclut par défaut :

- une activité dont `end_date` est déjà passée (activité terminée) ;
- une activité dont `start_date` est dans le futur (annonce trop en
  avance par rapport à la date du jour).

Un paramètre `date` existe déjà (LL-4005) mais est **optionnel et à la
charge de l'appelant** — rien ne l'applique par défaut à la date du
jour. Une activité terminée depuis plusieurs mois ou prévue dans
plusieurs mois s'affiche donc aujourd'hui exactement comme une
activité en cours.

### Objectif

Ne plus retourner, par défaut, sur les recherches publiques, une
activité dont la période `[start_date, end_date]` ne couvre pas la
date du jour — sans supprimer ni archiver la donnée elle-même, et sans
retirer le paramètre `date` existant (LL-4005), qui doit continuer à
fonctionner pour filtrer sur une date différente d'aujourd'hui.

### Points à trancher avec Alex avant/pendant l'implémentation

- **Portée** : uniquement `findNearby`/`findWithinBounds` (recherches
  publiques, LL-6004), ou aussi `findAll`/`findByStatus`
  (consultation administrative, LL-6005) ? Proposition par défaut :
  uniquement les deux endpoints publics, cohérent avec la logique déjà
  utilisée pour le filtre de statut `PUBLISHED` (LL-6004) — la file de
  modération doit rester consultable sans restriction de date.
- **Bornes** : une activité est-elle "en cours" quand
  `start_date <= aujourd'hui <= end_date` (bornes incluses, comme le
  filtre `date` existant), ou faut-il aussi retenir les activités dont
  `start_date` est dans le futur proche (ex. J+7, pour ne pas cacher
  un événement qui vient d'être publié) ? Proposition par défaut :
  strictement "en cours aujourd'hui", conforme à la formulation d'Alex
  (« date de début > date du jour, pas affichée »).
- **`end_date` absente** (activités créées via le formulaire de
  contribution, LL-2012, qui ne renseigne pas de date de fin) : déjà
  traité comme une activité d'une seule journée par le filtre `date`
  existant (`COALESCE(end_date, start_date)`), même logique à
  réutiliser ici.
- **Interaction avec le paramètre `date` existant** : si le client
  fournit explicitement `date`, ce filtre explicite doit-il continuer
  à primer (comportement actuel inchangé), le filtre "aujourd'hui"
  n'intervenant que si `date` est absent ? Proposition par défaut :
  oui, pas de changement de comportement quand `date` est fourni
  explicitement.

### Piste d'implémentation (à confirmer)

Réutiliser le filtre SQL déjà en place pour le paramètre `date`
(`ActivityRepository#findWithinRadius`/`findWithinBounds`,
`:date::date BETWEEN start_date::date AND COALESCE(end_date, start_date)::date`)
en le rendant systématique côté service (`ActivityService#findNearby`/
`findWithinBounds`) : si `dateRaw` n'est pas fourni par l'appelant,
passer la date du jour au lieu de `null`, plutôt que de dupliquer la
logique de comparaison de dates. Limite couverte plus haut
(« portée ») : cette évolution ne toucherait que ces deux méthodes,
pas `findByStatus`/`findAll`.

### Critères d'acceptation

- une activité dont `end_date < aujourd'hui` n'apparaît plus dans
  `/nearby`/`/within-bounds` sans paramètre `date` explicite ;
- une activité dont `start_date > aujourd'hui` n'apparaît plus dans
  ces mêmes conditions ;
- une activité en cours (`start_date <= aujourd'hui <= end_date`,
  `end_date` traitée comme `start_date` si absente) continue de
  s'afficher normalement ;
- le paramètre `date` existant (LL-4005) continue de fonctionner à
  l'identique quand il est fourni explicitement (aucune régression) ;
- la consultation administrative par statut (LL-6005) n'est pas
  affectée par ce changement, sauf décision contraire d'Alex ;
- tests couvrant les quatre cas ci-dessus (terminée, future, en
  cours, `end_date` absente) ajoutés à
  `ActivityServiceTest`/`ActivityRepositoryIntegrationTest`.

---
