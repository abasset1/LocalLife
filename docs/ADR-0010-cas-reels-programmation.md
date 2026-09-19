# ADR-0010 — Cas réels de programmation, correction du filtrage des occurrences (LL-11016)

## Contexte

LL-11016 (`SPRINT_11.md`, section 19) demande de vérifier que cinq
scénarios concrets de programmation sont « représentables et testés » :
événement ponctuel, événement récurrent, food truck multi-lieux,
exception (occurrence annulée ou déplacée), fiche riche complète.

En construisant effectivement le **Cas 4 — Exception** (voir
`RealSchedulingScenariosIntegrationTest`), ce ticket a révélé qu'il
n'était PAS représentable avec le code existant : LL-11013 avait
interprété « occurrences pertinentes » comme « non annulées »
(`OccurrenceService#findEffectiveByScheduleId`, voir ADR-0007), ce qui
excluait purement et simplement les occurrences `CANCELLED` de
`ActivityDetailResponse` — impossible d'afficher « 15/09 → annulé »
pour une occurrence qui n'apparaît jamais dans la réponse API.

## Décision

### `ActivityController#toDetailResponse` renvoie désormais toutes les occurrences

`OccurrenceService#findByScheduleId` (déjà existant, sans filtre de
statut) remplace `findEffectiveByScheduleId` (non annulées uniquement)
dans l'assemblage de la fiche détaillée. `OccurrenceSummary#status`
(déjà exposé depuis LL-11013) permet de distinguer une occurrence
normale d'une annulée — c'est désormais au consommateur de l'API
(frontend, ou tout autre client) de décider comment la représenter,
pas au backend de la lui cacher.

`OccurrenceService#findEffectiveByScheduleId` elle-même n'est pas
supprimée : la javadoc de LL-11005 la présentait comme utile pour « les
recherches » (LL-11012, filtrage des activités par ville/date/position —
une occurrence annulée ne doit effectivement jamais faire apparaître
une activité dans une recherche) — cette utilisation reste correcte et
n'est pas remise en cause par ce ticket. Seule la fiche détaillée d'une
activité *déjà* identifiée (LL-11013) doit montrer l'intégralité de son
historique, y compris ses exceptions.

### Frontend : les exceptions s'affichent, pas chaque occurrence individuelle

Afficher l'intégralité des occurrences d'une série récurrente (potentiellement
des dizaines pour un événement hebdomadaire sur plusieurs mois) serait
impraticable dans la fiche détaillée. Seules les occurrences qui
**dévient** du planning normal du schedule (annulée, ou déplacée vers un
autre lieu que celui du schedule) sont listées, sous chaque ligne
d'horaire concernée — exactement ce que montre l'exemple du ticket
(« 15/09 → annulé », « 22/09 → Marseille »), sans lister aussi « 29/09 →
normal » (une occurrence qui se déroule comme prévu n'apporte aucune
information supplémentaire par rapport à la ligne de récurrence déjà
affichée).

## Conséquences

- `RealSchedulingScenariosIntegrationTest` (nouveau) : cinq tests
  d'intégration, un par scénario du ticket, construisant directement les
  entités du modèle riche (comme `ActivityRepositoryIntegrationTest`
  depuis LL-11012) puis vérifiant la réponse HTTP réelle de `GET
  /api/v1/activities/{id}`.
- `ActivityDetailResponse.OccurrenceSummary` (javadoc) et
  `ActivityController#toDetailResponse` (javadoc) mis à jour pour
  refléter cette correction.
- Frontend (`App.tsx`, `frontend/src/utils/recurrence.ts`) : nouveau
  champ `occurrences` sur l'interface `ActivityDetail`, nouvelle fonction
  `formatDateOfDay`, rendu des exceptions sous chaque ligne d'horaire —
  vérifié par une compilation et un build réels (`npx tsc --noEmit`,
  `npm run build`).
- ⚠️ Le backend reste non vérifiable dans cette session (pas d'accès à
  Maven Central ni à une base PostgreSQL réelle) : les cinq scénarios
  ont été construits et vérifiés par relecture manuelle attentive de la
  correspondance entre les champs de chaque entité et les assertions
  JSON, mais **`mvn verify` reste à exécuter par Alex avant tout merge**
  pour confirmer qu'ils passent réellement.

## Addendum — bug de validation révélé par le premier vrai run CI

Le premier run CI réel de la PR de ce ticket (GitHub Actions,
`mvn verify` contre une base PostgreSQL fraîche) a révélé un deuxième
bug, sans rapport avec les occurrences annulées : `source.type =
'MANUAL'` comptait 6 lignes au lieu d'une, faisant échouer
`SourceRepository#findByType("MANUAL")`
(`IncorrectResultSizeDataAccessException`, cette méthode attend
exactement une ligne) dans des dizaines de tests par ricochet
(`ActivityService#createActivity`, `SourceService#deleteSource`, et
tout test appelant `manualSourceId()`).

Aucune migration ni aucun code applicatif n'insère jamais de seconde
ligne `MANUAL` intentionnellement — seule `V8__create_source_table.sql`
le fait, une fois. Mais `SourceService#createSource`/`updateSource`
(LL-EF-005) ne rejetaient `type` que s'il était vide, jamais
spécifiquement `MANUAL` : n'importe quel appel à `POST`/`PUT
/api/v1/sources` avec ce type était silencieusement accepté, créant ou
transformant une source en une seconde source « réservée ». Le chemin de
code exact qui a produit les 6 lignes lors de ce run CI précis n'a pas
pu être identifié avec certitude par relecture statique seule (aucun
test du dépôt ne le fait explicitement avec la chaîne littérale
`"MANUAL"`) — corrigé à la source (validation rejetant ce type dans les
deux méthodes) et en défense supplémentaire au niveau base
(`V30__enforce_single_manual_source.sql`, index unique partiel
`WHERE type = 'MANUAL'`), qui transformera toute prochaine tentative en
une erreur explicite au point d'insertion plutôt qu'en une pollution
silencieuse.

## Addendum 2 — `ImportServiceIntegrationTest` : compteur `created`/`updated` corrigé

`ImportService#importFrom` incrémentait `created`/`updated` **après**
l'appel à `enrichActivity(...)` — si cette étape (construction de
`Location`/`Schedule`/`Occurrence`/...) levait une exception, capturée
par le `catch` englobant, l'élément entier était compté en erreur alors
que l'`Activity` elle-même venait d'être sauvegardée avec succès juste
avant. Corrigé : le compteur s'incrémente désormais immédiatement après
`activityRepository.save(...)`, et l'enrichissement est isolé dans sa
propre étape (`enrichActivitySafely`) dont l'échec est journalisé en
avertissement, sans incrémenter `errors` — l'activité reste alors « nue »
(sans ses entités liées) jusqu'au prochain import réussi de ce même
élément, qui retente l'enrichissement en entier.

La cause exacte de l'échec initial d'`enrichActivity` (pourquoi elle
levait une exception en CI) n'a pas pu être identifiée avec certitude
par relecture statique seule — ce correctif rend le système robuste à
n'importe quelle cause sous-jacente plutôt que de la cibler
spécifiquement.
