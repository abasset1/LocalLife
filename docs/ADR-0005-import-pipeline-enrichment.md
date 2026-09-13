# ADR-0005 — Persistance du modèle événementiel riche par le pipeline d'import (LL-11011)

## Contexte

Le Sprint 11 (LL-11001 à LL-11010) a ajouté au modèle métier un jeu
d'entités riches autour d'une activité — `Location`, `Schedule`,
`Occurrence`, `Media`, `ActivityLink`, `Accessibility`, `ActivityTag`,
`ActivityMetadata` — chacune avec son repository et son service, mais
jusqu'ici jamais alimentées par le pipeline d'import (`ImportService`,
LL-5008) : celui-ci ne persistait toujours que l'`Activity` elle-même,
avec les champs plats hérités de LL-5004/LL-EF-008/LL-11006.

LL-11011 (`docs/05_Sprints/SPRINT_11.md`, section 14) demande
explicitement d'« adapter le pipeline d'import » pour que ces données
riches, quand elles sont disponibles côté source, soient effectivement
collectées et persistées — avec pour critère d'acceptation transversal
« aucune perte volontaire des données déjà disponibles ».

## Décision

### `CollectedActivity` restructuré en sous-modèles

`CollectedActivity` (`collector.domain`) portait jusqu'ici des champs
plats (`latitude`/`longitude`/`address`/`city`/`postalCode`,
`startDate`/`endDate`). Plutôt que d'empiler encore des champs plats
pour les nouvelles données (risque déjà identifié pour `Activity`
elle-même, voir ADR-0004), ils sont regroupés en cinq sous-modèles
dédiés : `CollectedLocation`, `CollectedTiming` (en liste, un par
horaire), `CollectedMedia` (en liste), `CollectedLink` (en liste),
`CollectedAccessibility`, plus `tags` (liste de `String`) et
`registrationInfo` (texte libre). Une source peut donc désormais
fournir plusieurs horaires pour un même événement (critère d'acceptation
explicite du ticket) là où un seul couple `startDate`/`endDate` était
possible avant.

### `NormalizationService` : une méthode `normalizeXxx` par entité cible

`normalize()` reste la conversion vers `Activity` (avec sa validation
existante, étendue aux nouveaux critères — voir la javadoc de la
classe). Sept méthodes supplémentaires (`normalizeLocation`,
`normalizeSchedule`, `normalizeOccurrences`, `normalizeMedia`,
`normalizeLinks`, `normalizeAccessibility`, `normalizeTags`,
`normalizeMetadata`) convertissent la même `CollectedActivity` vers
chacune des autres entités. Volontairement séparées plutôt que
regroupées dans une seule méthode retournant un agrégat : la validité
d'une donnée annexe (ex. un lien mal formé) ne doit jamais faire rejeter
l'`Activity` elle-même — seul `normalize()` détermine si l'élément
collecté est accepté ou non. `NormalizationService` continue de ne rien
persister (principe déjà posé par LL-5005) : ces méthodes restent de
simples convertisseurs en mémoire.

### `ImportService` : persistance en deux temps, remplacement à chaque réimport

`ImportService` appelle désormais, pour chaque élément accepté, la
persistance de l'`Activity` (comme avant) **puis**, une fois son `id`
connu, la construction et la persistance des entités liées
(`enrichActivity`) :

1. **`Location`/`Media` sont explicitement partageables** (voir leur
   javadoc) : réutilisées plutôt que dupliquées, via `findOrCreate`
   (nouvelles méthodes sur `LocationService`/`MediaService`, dédiées par
   adresse complète/coordonnées pour `Location`, par url pour `Media`).
2. **Toutes les autres entités liées à une activité sont remplacées** à
   chaque réimport : supprimées (`clearEnrichment`, nouvelles méthodes
   `deleteByActivityId`/`deleteByScheduleId` sur les repositories et
   services concernés) puis reconstruites à partir de la donnée
   collectée courante, plutôt que fusionnées avec ce qui existait déjà
   en base.

### Aucun `RRULE` produit par `OpenAgendaCollector`

`CollectedActivity` porte un champ `recurrenceRule` (RRULE RFC 5545,
comme `Schedule.recurrenceRule`), mais `OpenAgendaCollector` ne le
renseigne jamais : OpenAgenda expose ses événements récurrents déjà
« à plat » (un horaire par répétition dans `timings`), sans règle de
récurrence explicite dans la réponse de son API de lecture des
événements, à notre connaissance. Le champ existe malgré tout sur
`CollectedActivity` pour une source future qui en fournirait un
réellement (ex. import iCal).

## Pourquoi remplacer plutôt que fusionner à chaque réimport

| Option | Rejetée car |
| --- | --- |
| Comparer chaque entité liée existante à la donnée collectée courante, ne modifier que ce qui a changé | Complexité et risque d'erreur bien plus élevés qu'un remplacement complet (correspondance à établir entre occurrences existantes et nouvelles, par exemple), pour un bénéfice principalement en performance — non prioritaire vu le volume d'un import (quelques centaines d'événements par agenda, voir la javadoc d'`OpenAgendaCollector` sur la pagination). |
| Ne jamais supprimer, seulement ajouter | Une occurrence disparue de la source (événement annulé) resterait indéfiniment en base — contredit directement le critère d'acceptation « aucune perte volontaire des données déjà disponibles » lu dans l'autre sens : une donnée qui n'est plus disponible ne doit pas persister comme si elle l'était encore. |

## Conséquences

- `ImportService` passe de 5 à 14 dépendances constructeur — cohérent
  avec le style d'injection du projet (constructeur simple, pas de
  builder), mais point de complexité à surveiller si un futur ticket
  ajoute encore des entités liées.
- `Location`/`Media` : la déduplication par `findOrCreate` reste
  volontairement simple (égalité exacte sur adresse complète ou
  coordonnées/url) — un même lieu orthographié différemment d'une source
  à l'autre créera des doublons ; non traité par ce ticket.
- Une activité archivée (source où elle a disparu, voir
  `ImportService`) conserve ses entités liées telles que laissées par le
  dernier import réussi : cohérent avec la suppression douce déjà
  choisie pour `Activity` elle-même.
- ⚠️ Cette session n'a pas d'accès réseau à Maven Central : le pipeline
  n'a pas pu être compilé ni testé automatiquement (voir aussi
  ADR-0004). Relecture manuelle attentive de la correspondance entre
  l'ordre des champs de chaque record de domaine et les appels de
  construction dans `NormalizationService`/`ImportService`, mais
  `mvn verify` reste à exécuter par Alex avant tout merge.
