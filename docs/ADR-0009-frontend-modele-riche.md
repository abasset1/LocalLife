# ADR-0009 — Exploiter le modèle événementiel riche dans le frontend (LL-11015)

## Contexte

LL-11013 a exposé le modèle événementiel riche du Sprint 11 via
`GET /api/v1/activities/{id}` (`ActivityDetailResponse`), mais le
frontend (React/TypeScript, `frontend/src/App.tsx`) continuait d'afficher
uniquement les champs plats déjà connus (titre, date, ville, adresse) —
sans image, sans horaires détaillés, sans description longue, sans
médias/liens/accessibilité/tags. LL-11015 (`SPRINT_11.md`, section 18)
demande d'adapter l'interface pour exploiter ce modèle.

## Décision

### `mainImageUrl` ajouté à `ActivityResponse` (petite extension backend)

Le frontend affiche déjà une carte/liste alimentée par `/nearby`/
`/within-bounds` (`ActivityResponse`, LL-4002/LL-4007/LS-002) — un
DTO que LL-11012/LL-11013/LL-11014 ont délibérément laissé inchangé
(« aucun endpoint existant cassé »). Pour satisfaire le critère
d'acceptation « images visibles » sur cette carte/liste (pas seulement
sur la fiche détaillée), `ActivityResponse` gagne un champ
`mainImageUrl`, résolu par une nouvelle variante à 5 arguments de
`from(...)` — les variantes existantes (2/3/4 arguments) sont préservées
à l'identique (`mainImageUrl` y vaut toujours `null`), pour ne rien
changer au comportement déjà couvert par leurs tests. `LikeController`
(favoris, LS-002) gagne les mêmes dépendances (`MediaService`/
`ActivityMediaService`) qu'`ActivityController` pour la même résolution
— sa propre javadoc annonçait déjà « image éventuelle » parmi les champs
attendus, jamais effectivement implémentée avant ce ticket.

### Fiche détaillée chargée à la demande, pas en amont

Le clic sur une activité (carte ou liste) ouvre toujours immédiatement
le panneau de détail avec les données déjà en mémoire (résumé), comme
avant ce ticket — un `useEffect` déclenche alors `GET
/api/v1/activities/{id}` en arrière-plan et affiche les sections
enrichies une fois la réponse arrivée (état de chargement explicite,
`AbortController` pour annuler une requête devenue obsolète si
l'utilisateur enchaîne plusieurs ouvertures). Pas de préchargement de
toutes les fiches détaillées en même temps que la liste : coût réseau
inutile pour des activités jamais ouvertes.

Puisque LL-11014 permet à cet endpoint de renvoyer un résumé plutôt
qu'une fiche détaillée (accès Premium non accordé), le frontend
distingue les deux uniquement par la présence du champ `schedules` dans
la réponse — si absent, aucune section enrichie n'est affichée (le
résumé déjà connu reste seul visible), sans erreur ni distinction
visuelle dédiée à ce cas (hors périmètre de ce ticket, la politique
d'accès actuelle — `StubPremiumAccessPolicy` — accorde de toute façon
l'accès à tout le monde aujourd'hui).

### Récurrence traduite en français, jamais la chaîne RRULE brute

`frontend/src/utils/recurrence.ts` traduit `Schedule.recurrenceRule`
(RFC 5545) en phrase française (« Tous les mardis »). Volontairement
limité au sous-ensemble réellement produit par le backend aujourd'hui
(`FREQ=WEEKLY;BYDAY=...`, voir `RecurrenceRuleService`/
`OpenAgendaCollector` côté backend — aucune source actuelle n'émet
d'autre fréquence) plutôt qu'un parseur RFC 5545 complet, avec un repli
générique (mais toujours en français) pour toute autre fréquence.

### « Food truck multi-lieux » : itération naturelle sur `schedules`

Aucun traitement spécial : la section « Horaires » de la fiche détaillée
affiche une ligne par `Schedule` de l'activité (récurrence + horaire +
lieu résolu). Une activité avec plusieurs schedules pointant vers des
lieux différents (LL-11012, LL-11016 « Cas 3 ») affiche donc
naturellement plusieurs lignes, une par lieu — pas de composant ni de cas
particulier dédié au « food truck » (qui n'est, dans ce modèle, qu'une
activité comme une autre avec plusieurs schedules).

## Hors périmètre assumé (à revisiter, notamment pour LL-11016)

- **Occurrences annulées** : `ActivityDetailResponse.occurrences`
  (LL-11013) n'inclut que les occurrences non annulées (voir ADR-0007,
  « occurrences pertinentes » = non annulées) — le frontend n'affiche
  donc aucune exception/annulation explicite (LL-11016, « Cas 4 »),
  puisque le backend ne les transmet pas. Non traité par ce ticket (ses
  critères d'acceptation ne mentionnent pas les exceptions) ; à
  reconsidérer lors de LL-11016 si le cas doit être visible.
- **Changement exceptionnel de lieu par occurrence** (une seule
  occurrence déplacée, pas tout le schedule) : la fiche détaillée
  affiche les lieux résolus par schedule, pas occurrence par occurrence
  — un déplacement ponctuel n'apparaît donc pas distinctement
  aujourd'hui. Même remarque que ci-dessus.
- **Résolution de `mainImageUrl` sans mise en cache** (une requête par
  activité affichée) : même limitation déjà assumée côté backend depuis
  LL-11013 (voir ADR-0007) — désormais aussi vraie pour `/nearby`/
  `/within-bounds`/favoris, potentiellement plus coûteuse ici (listes
  plus longues qu'une fiche unique). À optimiser si cela devient un
  problème réel de performance.

## Conséquences

- `frontend/src/utils/recurrence.ts` (nouveau fichier, sans dépendance
  externe) et de nouvelles interfaces TypeScript dans `App.tsx`
  (`ActivityDetail` et ses types imbriqués), miroir manuel des DTO
  backend — aucune génération de types automatique dans ce projet, à
  maintenir en cohérence manuellement si les DTO backend évoluent
  encore.
- Vérifié par une compilation TypeScript et un build réels
  (`npx tsc --noEmit`, `npm run build`) dans cette session — contrairement
  au backend, npm/Node sont accessibles depuis ce sandbox. Le backend,
  lui, reste non vérifiable ici (pas d'accès à Maven Central ni à une
  base PostgreSQL réelle) : **`mvn verify` reste à exécuter par Alex**
  pour les changements `ActivityResponse`/`ActivityController`/
  `LikeController` de ce ticket.
