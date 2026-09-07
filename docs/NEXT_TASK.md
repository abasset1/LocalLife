# NEXT_TASK.md

## État actuel

Sprint 7 (MVP validé bout en bout) et Sprint 8 (préparation de la
bêta, GO conditionnel confirmé) terminés. Sprint 9 en cours : bêta
ouverte, backend déployé et sécurisé (LL-9001 → LL-9004), déploiement
du frontend en cours (LL-9005). Détail complet : `docs/PROJECT_STATUS.md`,
sections Sprint 7 à 9.

Phase actuelle : **Phase 2 — Validation et préparation de la bêta**.

**Sprint 8 — Préparation de la bêta.** Les neuf tickets ont été
traités ; LL-8009 (dernier ticket) a rendu une décision **GO bêta
conditionnel** — trois conditions restent à confirmer par Alex avant
l'ouverture effective (voir plus bas).

`LL-8001` terminé : parcours MVP rejoué après les corrections du
Sprint 7, aucune régression, baseline figée.

`LL-8002` terminé : premier compte `ADMIN` bootstrapé automatiquement
au premier démarrage (`AdminBootstrapRunner`,
`LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL`/`LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD`),
remplace la promotion SQL manuelle utilisée jusque-là.

`LL-8003` terminé : exceptions serveur non gérées (réponse `500`)
journalisées au niveau `ERROR` par `GlobalExceptionHandler`, sans mot
de passe ni JWT dans les logs ; contrat HTTP existant inchangé.

`LL-8004` : agendas OpenAgenda configurés pour Avignon ; pagination
complète de l'API OpenAgenda (`size=300` + curseur `after`), l'API ne
renvoyant que 20 résultats par défaut. **Écart trouvé pendant LL-8009**
(voir plus bas) : les propriétés `openagenda.avignon-*-uid` n'étaient
en réalité jamais lues par aucun bean — corrigé par LL-8009.

`LL-8005` terminé : import automatique planifié (`ImportScheduler`,
toutes les heures), en plus du déclenchement manuel existant
(`POST /api/v1/admin/import`, LL-7002).

`LL-8006` terminé : affichage de bout en bout des activités vérifié sur
la carte. Écart trouvé et corrigé : le popup n'affichait ni le lieu ni
la source lisible (seul `sourceId`, un identifiant technique, était
exposé) — `ActivityResponse` résout désormais `sourceId` en
`sourceName`, popup complété. Corrections complémentaires signalées par
Alex au fil de `mvn verify` : test d'intégration dépendant d'un id
d'activité fixe (fragile sur base persistante), dépassements de la
limite Checkstyle de 120 caractères.

`LL-8007` terminé : dette technique pertinente pour une bêta traitée —
vulnérabilité `nanoid` (déjà résolue, preuve formalisée), défaut de
formatage de `ActivityController` (corrigé), duplication des deux
`ROADMAP.md` (déjà résolue avant ce ticket, preuve formalisée). Aucune
nouvelle dette bloquante découverte pendant LL-8001. Une nouvelle
entrée a été ajoutée depuis, par LL-8009 (voir plus bas).

`LL-8008` terminé : documentation consolidée —
`README.md`, `backend/README.md`, `frontend/README.md`,
`docs/PROJECT_STATUS.md`, `docs/04_Project/ROADMAP.md`,
`docs/01_Product/BACKLOG.md`, `docs/NEXT_TASK.md` (ce fichier),
`docs/DETTE_TECHNIQUE.md`, `CHANGELOG.md` — plus aucune ne désigne le
Sprint 7 comme sprint courant ; le guide de démonstration du `README.md`
racine reflète la baseline actuelle (import automatique, popup
lieu/source, clustering).

`LL-8009` terminé (ce ticket) : décision go/no-go de la bêta. Écart
trouvé en vérifiant le critère « plusieurs agendas Avignon » (LL-8004,
voir ci-dessus) et corrigé (`OpenAgendaSourcesConfig` enregistre
désormais un collecteur par agenda réellement configuré, au lieu d'un
unique `@Component` ne consommant qu'un seul agenda). Limite résiduelle
documentée dans `docs/DETTE_TECHNIQUE.md` : un seul agenda
Avignon-spécifique a un uid réel (Culture) ; les trois autres restent
à identifier par Alex. **Décision : GO bêta conditionnel**, trois
conditions à confirmer par Alex avant ouverture effective (voir
`docs/PROJECT_STATUS.md`, section LL-8009) :

1. `mvn verify` passe.
2. Vérification manuelle : un import réel affiche des activités des
   deux agendas actifs sur la carte.
3. Arbitrage produit : un seul agenda Avignon actif suffit-il pour
   cette première bêta restreinte, ou faut-il en identifier d'autres
   avant l'ouverture ?

## Sprint 8 — clôturé

Les trois conditions du GO bêta conditionnel ont été confirmées par
Alex le 26/08/2026 (`mvn verify` passe, import réel multi-agenda
vérifié, diversité d'agendas Avignon acceptée en l'état). Bêta ouverte.
Détail complet : `docs/PROJECT_STATUS.md`, section Sprint 8.

## Sprint 9 — en cours (post-bêta)

Objectif : traiter au fil de l'eau les correctifs identifiés
pendant/après la bêta, puis déployer et sécuriser l'accès public à
LocalLife. Voir `docs/05_Sprints/SPRINT_9.md` pour le détail des
tickets et `docs/PROJECT_STATUS.md`, section Sprint 9, pour le
narratif complet de chacun.

* `LL-9001` terminé : les recherches publiques n'affichent plus par
  défaut les activités hors période (filtre implicite sur la date du
  jour quand `date` n'est pas fourni).
* `LL-9002` terminé : architecture et procédure de l'environnement
  bêta documentées (`docs/02_Architecture/BETA_DEPLOYMENT.md`),
  infrastructure Docker/Caddy livrée.
* `LL-9003` terminé : backend et PostgreSQL/PostGIS déployés en bêta
  sur Hetzner CX22 (bascule depuis Oracle Cloud, capacité ARM
  indisponible à l'inscription), health check et authentification
  vérifiés.
* `LL-9004` terminé : Security Gate validé le 28/08/2026. Deux
  bloquants corrigés (clé API OpenAgenda externalisée, procédure de
  sauvegarde/restauration Backblaze B2 ajoutée). Cinq constats non
  bloquants documentés dans `docs/DETTE_TECHNIQUE.md`, en attente
  d'arbitrage d'Alex (endpoint utilisateur public exposant l'email,
  messages d'exception bruts sur les 500, utilisateur PostgreSQL
  unique, en-têtes de sécurité HTTP absents côté Caddy,
  `UnsupportedJwtException` non capturée).

## Prochaine tâche

**`LL-9005` — Déployer le frontend et rendre LocalLife accessible en
ligne**, en cours. Dépend de `LL-9004` (Security Gate), désormais
validé. Critères d'acceptation : frontend accessible depuis Internet en
HTTPS, utilisant l'API bêta (aucune URL `localhost`), inscription/
connexion/carte/recherches/détail d'activité/contribution
fonctionnels, aucun secret backend dans le build frontend.

En parallèle, le sprint mobile (`docs/05_Sprints/SPRINT_MOBILE.md`) a
démarré : `LL-MOB-0001` (socle Expo/TypeScript) et `LL-MOB-0002`
(navigation) livrés et poussés sur `main` ; `LL-MOB-0003` (client API)
en pause en attendant deux décisions techniques d'Alex.

Également en parallèle, un nouveau sprint évolutions/corrections
(`docs/05_Sprints/SPRINT_EVOL_FIX.md`) a démarré : `LL-EF-001`
(formulaire de saisie d'activité en fenêtre modale, réservé aux
utilisateurs connectés), `LL-EF-002` (géolocalisation automatique,
suppression du bandeau « Utiliser la localisation ») et `LL-EF-003`
(rechargement fluide de la carte lors des déplacements/zoom, plus de
coupure visuelle) terminés et **appliqués sur `main`**. `LL-EF-004`
(interface d'administration `/admin` pour valider/refuser les
activités proposées — le statut de modération PENDING/PUBLISHED/
REJECTED demandé par ce ticket existait déjà depuis le Sprint 6,
aucun changement backend) déjà appliqué sur `main`. `LL-EF-005`
(gestion des agendas OpenAgenda depuis l'interface d'administration —
configuration dynamique en base, remplace `OpenAgendaSourcesConfig`)
terminé, livré sous forme de patch depuis `origin/main`, pas encore
appliqué. ⚠️ `LL-EF-005` n'a pas pu être compilé/testé côté backend
dans la session qui l'a produit (pas d'accès à Maven Central) : lancer
`mvn test` avant de l'appliquer.

`LL-EF-008` (vue liste des activités, regroupées par ville/triées par
date) traité à la demande explicite d'Alex, hors ordre du sprint —
`LL-EF-006` et `LL-EF-007` restent non traités. Ce ticket a nécessité un
changement de modèle métier non anticipé par sa rédaction initiale
(aucune notion de ville n'existait en base) : voir
`docs/02_Architecture/ADR-0001-adresse-structuree-activites.md`. Alex a
également demandé, en complément, l'affichage d'une adresse lisible à
la place des coordonnées GPS brutes — traité dans le même changement
(même ADR). Livré sous forme de patch indépendant, pas encore appliqué.

Prochain ticket de ce sprint, sauf arbitrage contraire d'Alex :
`LL-EF-006` (interface utilisateur).

## Règles

- Ne pas élargir le MVP avant les premiers retours de bêta.
- Toute correction doit être reliée à un risque de mise en bêta, une dette technique ou un critère de baseline.
- Un ticket terminé doit être vérifié dans Git, les tests ou une validation documentée.
