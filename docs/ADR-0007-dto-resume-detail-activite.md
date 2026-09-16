# ADR-0007 — DTO résumé vs DTO détail pour les activités (LL-11013)

## Contexte

Jusqu'à ce ticket, `GET /api/v1/activities` et `GET
/api/v1/activities/{id}` exposaient directement l'entité de domaine
{@code Activity} en réponse JSON — un simple record miroir des colonnes
de la table `activity`, sans distinction entre ce qu'une carte/liste a
besoin d'afficher (position, titre, catégorie, date) et ce qu'une fiche
détaillée a besoin d'afficher (description longue, programmation,
médias, accessibilité, etc.). Le Sprint 11 a par ailleurs introduit un
modèle événementiel riche (`Schedule`/`Occurrence`/`Location`/`Media`/
`ActivityLink`/`Accessibility`/`ActivityTag`/`ActivityMetadata`,
LL-11001 à LL-11010) qui n'était encore exposé par aucun endpoint public.

LL-11013 (`SPRINT_11.md`, section 16) demande de séparer ces deux
besoins : un DTO résumé (léger, pour la carte/liste) et un DTO détail
(riche, pour la fiche), avec pour critère transversal « aucune donnée
sensible exposée ».

## Décision

### Deux nouveaux DTO, un par endpoint

- **`ActivitySummaryResponse`** (`GET /api/v1/activities`) : exactement
  les champs listés par le ticket (`id`, `title`, `category`, `date`,
  `city`, `address`, `latitude`, `longitude`, `mainImageUrl`).
- **`ActivityDetailResponse`** (`GET /api/v1/activities/{id}`) : les
  champs « sûrs » d'`Activity` (ceux déjà exposés par `ActivityResponse`,
  voir plus bas) complétés par l'arborescence explicitement listée par le
  ticket — `schedules`, occurrences pertinentes, `locations`, `media`,
  `links`, `accessibility`, `tags`, informations complémentaires — sous
  forme de records imbriqués dédiés (`ScheduleSummary`,
  `OccurrenceSummary`, `LocationSummary`, `MediaSummary`, `LinkSummary`,
  `AccessibilitySummary`) plutôt que d'exposer directement les entités de
  domaine (`Schedule`, `Occurrence`, etc.) : même principe que le DTO
  résumé, aucune colonne technique (ex. `Schedule.activityId`, redondant
  puisque déjà l'id de la ressource parente) ne fuite dans la réponse.

Aucun des deux ne réutilise/n'étend `ActivityResponse` (déjà utilisé par
`/nearby`/`/within-bounds`/les activités likées) : ce dernier porte des
champs ajoutés pour des besoins concrets propres à ces trois endpoints
(`likedByCurrentUser`, `createdByUsername`, etc.), ni listés par ce
ticket ni pertinents pour `GET /api/v1/activities`/`GET
/api/v1/activities/{id}`. Les dupliquer aurait recréé le problème que ce
ticket cherche à résoudre (payload plus lourd que nécessaire) ; les
endpoints `/nearby`/`/within-bounds`/likes ne sont pas concernés par ce
ticket et continuent d'utiliser `ActivityResponse` sans changement
(critère d'acceptation « aucun endpoint existant cassé »).

### « Occurrences pertinentes » = non annulées, sans fenêtre temporelle

Le terme du ticket (« occurrences pertinentes ») n'est pas davantage
précisé. Interprété comme « non annulées »
(`OccurrenceService#findEffectiveByScheduleId`, déjà utilisé par le
reste du Sprint 11), sans restriction de fenêtre temporelle (pas
seulement les occurrences à venir) — ⚠️ décision à valider : une fiche
détaillée affichant l'historique complet des occurrences passées non
annulées d'une activité récurrente ancienne pourrait devenir volumineuse.
Aucun ticket ne demande explicitement de borner cette liste ; à revisiter
si cela devient un problème réel (pagination, ou restriction aux
occurrences futures uniquement).

### Locations dédoublonnées

Une même activité peut référencer plusieurs fois le même lieu (schedule
et occurrences par défaut au même endroit). `locations` est construit à
partir d'un `LinkedHashMap<Long, Location>` (clé = id du lieu) plutôt que
d'une simple concaténation, pour ne jamais renvoyer deux fois le même
lieu.

### `mainImageUrl` résolu individuellement, sans mise en cache

Comme `withSourceNames`/la résolution des noms d'utilisateur déjà en
place dans `ActivityController` (résolution un par un, sans cache), la
résolution de l'image principale d'une activité pour le DTO résumé fait
une requête par activité de la liste. Accepté pour les mêmes raisons déjà
documentées ailleurs dans ce contrôleur : suffisant à l'échelle d'une
bêta, une optimisation par lot pourrait être introduite plus tard sans
changer le contrat public.

## Conséquences

- `ActivityController` gagne 9 dépendances supplémentaires
  (`ScheduleService`, `OccurrenceService`, `LocationService`,
  `MediaService`, `ActivityMediaService`, `ActivityLinkService`,
  `AccessibilityService`, `ActivityTagService`,
  `ActivityMetadataService`) — cohérent avec le style d'injection déjà en
  place (constructeur simple), mais point de complexité à surveiller.
- `GET /api/v1/activities/{id}` fait potentiellement plusieurs requêtes
  supplémentaires par activité consultée (une par sous-ensemble du modèle
  riche) — acceptable pour un endpoint de détail (une seule ressource à
  la fois, contrairement à la liste), pas optimisé davantage par ce
  ticket.
- Tous les tests référençant l'ancien contrat (`Activity` renvoyé
  directement) ont été mis à jour : `ActivityControllerTest` (mocks des 9
  nouvelles dépendances ajoutés, assertions adaptées),
  `ActivityControllerIntegrationTest` (désérialisation
  `ActivitySummaryResponse[]` au lieu d'`Activity[]`, nouveaux tests
  vérifiant l'absence de champs sensibles et la présence de
  l'arborescence riche).
- OpenAPI (Springdoc, généré depuis les annotations `@Operation`/
  `@ApiResponse` déjà en place, pas de fichier statique dans ce projet)
  reflète automatiquement les nouveaux types de retour.
- ⚠️ Cette session n'a pas d'accès réseau à Maven Central ni à une base
  PostgreSQL réelle : vérifié par relecture manuelle attentive
  uniquement (correspondance des champs entre chaque record de domaine
  et les `Summary` correspondants). **`mvn verify` reste à exécuter par
  Alex avant tout merge.**
