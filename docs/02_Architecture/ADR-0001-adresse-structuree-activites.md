# ADR-0001 — Adresse structurée des activités (LL-EF-008)

## Contexte

Le ticket LL-EF-008 (`docs/05_Sprints/SPRINT_EVOL_FIX.md`) demande une
vue liste des activités « regroupées ou triées par ville ». Or le modèle
métier (`Activity`) ne stockait jusqu'ici que `latitude`/`longitude` :
aucune notion de ville n'existait nulle part dans l'application (ni en
base, ni dans les DTOs, ni côté collecteurs).

En complément, Alex a explicitement demandé qu'une adresse lisible soit
affichée à l'utilisateur final à la place des coordonnées GPS brutes,
« avec la méthode la plus professionnelle, qui aurait pu être utilisée
pour une grosse application ».

Ce document trace la décision retenue, conformément à `AI_RULES.md`
(« aucun changement d'architecture sans ADR »).

## Décision

Ajouter trois colonnes nullables à `activity` — `address`, `city`,
`postal_code` (migration `V15__add_address_to_activity.sql`) —
résolues **une seule fois, à l'écriture**, jamais recalculées à la
lecture :

- **Contribution manuelle** (`ActivityService#createActivity`) :
  `address` est le texte déjà saisi par le contributeur (jusqu'ici
  géocodé via `GeocodingService`/Nominatim puis jeté après usage — il
  suffit de le conserver). `city`/`postalCode` proviennent de la **même**
  réponse Nominatim, en demandant `addressdetails=1` : aucun appel
  réseau supplémentaire n'est nécessaire.
- **Import** (`NormalizationService`) : les trois champs proviennent de
  `CollectedActivity`, elle-même alimentée par l'objet `location` déjà
  renvoyé par l'API de la source (ex. OpenAgenda, qui documente
  `address`/`city`/`postalCode` sur son objet `location` — voir
  developers.openagenda.com/en/lieux/). Même principe que `url`
  (LL-6002) : une donnée déjà présente dans la réponse de la source,
  reprise plutôt que perdue.

`latitude`/`longitude` sont conservées inchangées pour le seul
positionnement du marqueur sur la carte ; ce sont désormais `address`
(avec repli sur `city`, puis sur les coordonnées) qui servent de « lieu »
affiché à l'utilisateur (popup carte, vue liste), et `city` seule qui
sert de clé de regroupement dans la vue liste.

## Alternatives envisagées

| Option | Rejetée car |
| --- | --- |
| Géocodage inverse (coordonnées → adresse) à chaque affichage, côté frontend | Appel réseau répété à chaque chargement de la liste, pas de persistance, dépendance à la disponibilité d'un service tiers au moment de l'affichage — non professionnel pour un volume d'activités qui croît avec les imports. |
| Utiliser `sourceName` (ex. « OpenAgenda — Avignon ») comme proxy de ville | Trompeur dès qu'une source couvre plusieurs villes ; absent pour les contributions manuelles. |
| Ne rien stocker, résoudre uniquement à la lecture via un service tiers | Mêmes inconvénients que la première option, en pire (à chaque rendu de chaque marqueur). |

## Conséquences

- Aucune activité existante n'est rétroactivement enrichie (pas de
  backfill dans ce ticket) : les trois champs restent `null` pour les
  activités déjà en base tant qu'elles ne sont pas re-géocodées/
  ré-importées. Le frontend prévoit un repli (`formatActivityLocation`,
  regroupement sous « Ville non renseignée »).
- Aucune nouvelle dépendance externe : réutilisation de Nominatim (déjà
  utilisé, LL-3012) et de l'API OpenAgenda (déjà appelée).
- `ActivityResponse` expose désormais `address`/`city` en plus de
  `latitude`/`longitude`.
