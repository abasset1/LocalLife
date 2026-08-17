# Contrat du modèle Source (LL-5001)

Définit le modèle minimal permettant d'identifier l'origine d'une
activité importée (`SPRINT_5.md` : « identifier la source d'une
activité »). Sert de base à l'implémentation du module `Source`
(LL-5002) et au contrat du collecteur (LL-5003), comme
`BOUNDING_BOX_SEARCH_CONTRACT.md` l'a fait pour LL-4007 — pas de code à
ce stade, uniquement le contrat.

Ce document ne décrit ni collecteur, ni pipeline de collecte : ces
sujets relèvent de LL-5003 et suivants.

---

## Modèle `Source`

| Champ           | Type            | Obligatoire | Contraintes                          | Description                                                        |
| ---------------- | --------------- | ------------ | -------------------------------------- | --------------------------------------------------------------------- |
| `id`             | Long            | généré       | —                                       | Identifiant technique, cohérent avec `Activity.id` (généré en base). |
| `name`           | String          | oui          | non vide                               | Nom lisible de la source (ex. `"OpenAgenda Marseille"`).             |
| `type`           | String          | oui          | doit correspondre à une valeur connue  | Nature technique de la source (voir « Valeurs de `type` » ci-dessous). |
| `url`            | String          | non          | URL valide si fournie                  | Point d'accès de la source (endpoint API, flux, page). Peut être absent pour une source sans accès réseau direct. |
| `status`         | String          | oui          | doit correspondre à une valeur connue  | État courant de la source (voir « Valeurs de `status` » ci-dessous). |
| `lastSyncAt`     | LocalDateTime   | non          | —                                       | Date/heure du dernier import réussi. `null` tant qu'aucun import n'a eu lieu. |

### Valeurs de `type`

Convention identique à `Activity.status` (chaîne libre plutôt qu'enum
Java, pour rester extensible sans migration de code) :

- `API` — source exposant une API structurée (JSON/XML) ;
- `RSS` — flux RSS/Atom ;
- `MANUAL` — réservée à la compatibilité avec les activités créées
  manuellement (voir section dédiée ci-dessous).

⚠️ Décision à valider : liste volontairement restreinte à ce que le
Sprint 5 nécessite (un seul collecteur réel, cf. `SPRINT_5.md`). Pas de
valeur `CSV`/`SCRAPING` tant qu'aucun collecteur de ce type n'existe —
à étendre lors d'un futur ticket plutôt qu'anticipé ici.

### Valeurs de `status`

- `ACTIVE` — source utilisée pour les imports courants ;
- `INACTIVE` — source désactivée volontairement, aucun import déclenché ;
- `ERROR` — dernier import en échec (à titre indicatif ; le détail de
  l'erreur relève de la journalisation, LL-5009, pas de ce champ).

⚠️ Décision à valider : `status` reflète l'état de la *source*, pas
celui d'un import individuel. Le journal détaillé par import (compteurs
créé/mis à jour/ignoré/erreur) est hors périmètre de ce contrat — voir
LL-5009.

---

## Compatibilité avec les activités créées manuellement

`SPRINT_5.md` exige que le modèle reste « compatible avec les activités
créées manuellement » (critère d'acceptation LL-5001).

⚠️ Décision à valider (à trancher avant LL-5002) : une activité créée
manuellement (hors import) est associée à une source réservée de type
`MANUAL` plutôt qu'à une valeur `null`. Deux options ont été envisagées :

1. **Source `null` autorisée** sur `Activity` — plus simple, mais
   introduit un cas particulier (« pas de source ») à gérer dans tout
   code qui lit `Activity.source`, y compris hors périmètre du Sprint 5.
2. **Source réservée `MANUAL`** (une seule ligne en base, créée par
   migration) — toute activité a systématiquement une source non
   nulle, ce qui simplifie LL-5008 (mise à jour d'une activité « si
   elle appartient à la même source ») et LL-5011 (affichage sur la
   carte) : aucune branche `null` à traiter.

Option retenue : **2 (source réservée `MANUAL`)**, pour éviter la
prolifération de vérifications `null` dans le domaine métier — mais
cela reste à valider avant l'implémentation de LL-5002, qui créera
effectivement cette ligne (migration Flyway).

Ce contrat ne modifie pas `Activity` : l'ajout du champ de liaison
(`sourceId` ou équivalent) sur `Activity` est un changement de modèle
métier et relève d'un ticket dédié (LL-5002 ou LL-5008 selon la
séquence retenue), pas de LL-5001.

---

> **Mise à jour LL-6007 (Sprint 6)** : l'exclusion « aucun endpoint REST »
> ci-dessous concernait le périmètre de LL-5001 (ce contrat), pas une
> décision définitive. LL-6007 a ajouté `SourceController` en lecture
> seule (`GET /api/v1/sources`, `GET /api/v1/sources/{id}`), non
> protégé, pour rendre le `sourceId` porté par `Activity` (depuis
> LL-5008) identifiable côté API — voir PROJECT_STATUS.md pour le
> détail. Aucune écriture (création/modification/suppression de source
> via l'API) n'a été ajoutée : toujours hors périmètre.

## Hors périmètre de LL-5001

Conformément à `SPRINT_5.md` et `AI_RULES.md` (un ticket = une seule
responsabilité) :

- aucune entité Java, aucun repository, aucune migration Flyway
  (LL-5002) ;
- aucun mécanisme de collecte (LL-5003) ;
- aucune modification du modèle `Activity` ;
- aucun endpoint REST.
