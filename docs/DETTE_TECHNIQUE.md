# Dette technique

Suivi des problèmes connus, non bloquants pour l'avancement des tickets en
cours, mais à traiter à un moment donné : vulnérabilités de dépendances,
raccourcis pris sous contrainte de temps, limitations techniques
identifiées pendant le développement. Chaque entrée reste jusqu'à sa
résolution (ou jusqu'à une décision explicite de l'ignorer, justifiée).

---

## Frontend — vulnérabilité `nanoid` (sévérité haute)

* **Détecté** : lors de la vérification de LL-4011 (13 août 2026).
* **Où** : `frontend/package-lock.json`, dépendance transitive (`nanoid`
  < 3.3.18), remontée par `npm audit`.
* **Nature** : générateurs personnalisés pouvant boucler indéfiniment
  quand `size` vaut zéro — [GHSA-2v37-7h3g-55p8](https://github.com/advisories/GHSA-2v37-7h3g-55p8).
* **Impact réel** : nul en pratique — chaîne de dépendance confirmée via
  `npm ls nanoid` : `vite@8.2.1 → postcss@8.5.26 → nanoid@3.3.17`.
  `nanoid` n'est utilisé que par PostCSS pendant le build (outillage de
  développement), jamais exécuté côté navigateur dans le code livré aux
  utilisateurs. Pas de chemin d'exploitation identifié côté application.
* **Correctif disponible** : `npm audit fix` (dans `frontend/`).
* **Pourquoi pas corrigé immédiatement** : hors périmètre du ticket en
  cours (LL-4011) au moment de la détection ; à traiter séparément pour
  ne pas mélanger une mise à jour de dépendances avec un changement
  fonctionnel dans le même commit/diff.
* **Résolu par** : mise à jour de dépendance déjà présente dans le
  lockfile actuel (vérifié le 25 août 2026, LL-8007) —
  `npm ls nanoid` donne désormais `vite@8.2.2 → postcss@8.5.26 →
  nanoid@3.3.18`, exactement la version corrigeant
  [GHSA-2v37-7h3g-55p8](https://github.com/advisories/GHSA-2v37-7h3g-55p8)
  (« all versions before 3.3.18 »). `npm audit` confirme
  `0 vulnerabilities` (`"total": 0` dans le rapport JSON) ;
  `npm audit fix --dry-run` ne propose plus aucune mise à jour de
  sécurité, seulement des binaires optionnels de plateformes (non liés
  à cette vulnérabilité). Aucune action supplémentaire nécessaire.
* **Statut** : résolu.

---

## Backend — ligne manquante entre deux méthodes (`ActivityController`)

* **Détecté** : lors de la préparation de LL-4015 (mise à jour de la
  documentation), en relisant `ActivityController.java`.
* **Où** : `backend/src/main/java/com/locallife/backend/activity/api/ActivityController.java`,
  entre la fin de `getActivityById` et le début de `createActivity`
  (`}    @PostMapping` sur une seule ligne, sans saut de ligne).
* **Nature** : défaut de formatage préexistant (pas introduit par les
  tickets du Sprint 4), probablement issu d'une fusion de diff
  antérieure. Aucun impact fonctionnel — le code compile et se comporte
  normalement — mais viole probablement la règle Checkstyle habituelle
  d'une ligne vide entre deux méthodes, et nuit à la lisibilité.
* **Impact réel** : nul fonctionnellement ; purement cosmétique.
* **Correctif disponible** : ajouter un saut de ligne entre les deux
  méthodes.
* **Pourquoi pas corrigé immédiatement** : repéré en marge d'un ticket
  de documentation (LL-4015), pas de ticket dédié pour une modification
  de code, même triviale — évite de mélanger un changement de code
  (même cosmétique) avec un diff purement documentaire.
* **Résolu par** : LL-8007 (25 août 2026) — saut de ligne ajouté entre
  `getActivityById` et `createActivity`. Pas de nouvelle violation
  Checkstyle sur ce fichier depuis (voir aussi le correctif de longueur
  de ligne livré juste avant ce ticket sur le même fichier, LL-8006).
* **Statut** : résolu.

---

## Backend — activités `ARCHIVED` visibles par défaut (recherche/carte)

* **Détecté** : lors de LL-5008 (persistance des imports, Sprint 5),
  formalisé en LL-5012.
* **Où** : `ActivityService#findNearby`/`#findWithinBounds`
  (`com.locallife.backend.activity.application`).
* **Nature** : le pipeline d'import (LL-5008) archive (`status =
  "ARCHIVED"`) une activité déjà importée mais absente d'une collecte
  plus récente, plutôt que de la supprimer physiquement. Or ni la
  recherche géographique ni la recherche par zone ne filtraient `status`
  par défaut (paramètre optionnel) — une activité `ARCHIVED` continuait
  donc d'apparaître sur la carte comme n'importe quelle autre.
* **Impact réel** : une activité qui n'existe plus réellement (source
  supprimée) restait visible aux utilisateurs jusqu'à ce qu'un filtre
  explicite soit ajouté.
* **Correctif disponible** : exclure `ARCHIVED` par défaut côté
  requête (`ActivityRepository`) quand `status` n'est pas fourni
  explicitement, ou filtrer côté frontend.
* **Pourquoi pas corrigé immédiatement** : modifier le comportement par
  défaut d'un endpoint utilisé depuis le Sprint 4 est un changement de
  comportement qui dépasse le périmètre d'un ticket d'import (LL-5008)
  ou de documentation (LL-5012) — nécessite une décision produit/un
  ticket dédié.
* **Statut** : résolu par LL-6004 (Sprint 6, 16 août 2026). Solution
  finalement plus stricte que le correctif envisagé ci-dessus (qui ne
  visait qu'`ARCHIVED`) : avec l'introduction de la modération en
  LL-6003 (`PENDING`/`PUBLISHED`/`REJECTED`), le paramètre `status` a
  été retiré des deux endpoints publics plutôt que simplement doté d'un
  défaut — ils ne retournent désormais que les activités `PUBLISHED`,
  sans exception possible côté appelant. Couvre `ARCHIVED` au passage
  (jamais `PUBLISHED`), donc résout ce problème sans ticket dédié
  supplémentaire. Voir `GEO_SEARCH_CONTRACT.md`/
  `BOUNDING_BOX_SEARCH_CONTRACT.md` (mise à jour LL-6004) et
  `PROJECT_STATUS.md`.

---

## Backend — aucun déclencheur pour le pipeline d'import ✅ Résolu

* **Détecté** : LL-5008/LL-5009, formalisé en LL-5012.
* **Où** : `ImportService#importAll()`
  (`com.locallife.backend.collector.application`).
* **Nature** : aucun ticket du Sprint 5 ne demandait explicitement de
  déclencheur (tâche planifiée, endpoint) pour exécuter l'import. La
  méthode existe, fonctionne (tests LL-5010/LL-5011), mais rien ne
  l'appelle dans l'application en cours d'exécution.
* **Impact réel** : le pipeline est fonctionnel mais inerte tant qu'un
  déclencheur n'est pas ajouté — aucune donnée OpenAgenda ne sera
  importée en usage réel sans intervention.
* **Correctif disponible** : `@Scheduled` (tâche planifiée) ou endpoint
  d'administration protégé, selon la préférence d'Alex.
* **Pourquoi pas corrigé immédiatement** : décision produit (fréquence
  souhaitée, méthode de déclenchement) plutôt que choix technique
  unilatéral — à trancher avec Alex avant implémentation.
* **Résolu par** : `LL-7002` (Sprint 7) — endpoint d'administration
  protégé (`POST /api/v1/admin/import`, rôle `ADMIN`), pas de
  scheduler, conformément à la décision du sprint. Voir
  `docs/02_Architecture/COLLECTOR_OPERATIONS.md`.

---

## Documentation — deux fichiers `ROADMAP.md` distincts

* **Détecté** : lors de LL-6011 (documentation de fin de Sprint 6, 17
  août 2026), en cherchant où mettre à jour la roadmap.
* **Où** : `docs/04_Project/ROADMAP.md` (fichier d'origine, présent
  depuis le commit initial, structure détaillée et alignée sur les
  autres documents du dossier `04_Project`) et `docs/ROADMAP.md`
  (fichier distinct, créé séparément — voir son historique Git,
  `Create ROADMAP.md` puis `Update ROADMAP.md` — contenu plus terse,
  sans les mêmes sous-sections).
* **Nature** : duplication non intentionnelle, déjà repérée sans être
  résolue lors de LL-5012 (qui avait mis à jour les deux fichiers en
  parallèle, corrigeant au passage une information obsolète dans
  `docs/ROADMAP.md`, sans consolider). LL-6011 a de nouveau mis à jour
  les deux fichiers en parallèle pour éviter de les faire diverger
  davantage, sans trancher laquelle des deux copies doit devenir la
  seule source de vérité — décision produit/documentaire plutôt que
  choix technique unilatéral.
* **Impact réel** : risque de divergence progressive entre les deux
  fichiers si l'un des deux est oublié lors d'une future mise à jour
  (déjà arrivé une fois avant LL-5012, selon son propre journal) ;
  aucun impact fonctionnel (documentation uniquement).
* **Correctif disponible** : choisir l'un des deux fichiers comme
  source unique (probablement `docs/04_Project/ROADMAP.md`, plus
  complet et cohérent avec le rangement du dossier `04_Project`),
  supprimer l'autre, et rediriger toute référence externe éventuelle.
* **Pourquoi pas corrigé immédiatement** : supprimer un fichier de
  documentation est une décision structurante qui dépasse le périmètre
  d'un ticket de mise à jour de documentation (LL-6011) — à confirmer
  avec Alex avant suppression.
* **Résolu par** : commit `b1c29d9` (« Mise à jour de la doc suite
  cloture sprint 7 + depot du sprint 8 »), sans ticket dédié, entre
  LL-6011 et l'ouverture du Sprint 8 — `docs/ROADMAP.md` a été
  transformé en simple point d'entrée historique (8 lignes) qui
  redirige explicitement vers `docs/04_Project/ROADMAP.md` comme
  « source de vérité », au lieu d'être supprimé (conserve un lien
  historique, sans dupliquer le contenu détaillé — voir la différence
  entre les deux fichiers, vérifiée le 25 août 2026 pour LL-8007). Plus
  aucun risque de divergence : `docs/ROADMAP.md` ne contient plus de
  section susceptible d'être oubliée lors d'une mise à jour, seulement
  un renvoi. Cette solution (redirection plutôt que suppression) diverge
  légèrement du correctif initialement envisagé ci-dessus, mais
  satisfait le même objectif (une seule source de vérité) sans perdre
  le point d'entrée historique évoqué dans `docs/ROADMAP.md` lui-même.
* **Statut** : résolu.

---

## Backend — aucun mécanisme de création du premier compte administrateur

* **Détecté** : LL-7008 (Sprint 7), en rédigeant le guide de
  démonstration.
* **Où** : `POST /api/v1/users`
  (`com.locallife.backend.user.api.UserController`), seule route
  capable d'assigner explicitement un rôle — mais réservée au rôle
  `ADMIN` (`SecurityConfig`). `POST /api/v1/auth/register` crée
  toujours un compte `USER` (`AuthService`).
* **Nature** : sur une base de données neuve, aucun compte `ADMIN`
  n'existe et aucune route ne permet d'en créer un — la seule route
  qui le pourrait exige déjà d'être authentifié en `ADMIN`.
* **Impact réel** : aucun blocage fonctionnel (contournement documenté
  dans le `README.md`, section « Sprint 7 — Démonstration du MVP » :
  inscription via `/auth/register` puis promotion par requête SQL
  directe), mais aucun chemin applicatif ne couvre ce besoin.
* **Correctif disponible** : à trancher avec Alex — ex. compte
  `ADMIN` seedé par une migration Flyway dédiée (données de
  démonstration uniquement, jamais en prod), ou commande
  d'administration hors API.
* **Pourquoi pas corrigé immédiatement** : hors périmètre de LL-7008
  (documentation uniquement) ; décision produit/sécurité (seeding en
  base vs commande dédiée) plutôt que choix technique unilatéral.
* **Résolu par** : `LL-8002` (Sprint 8) — `AdminBootstrapRunner`
  (`com.locallife.backend.auth.application`), exécuté une seule fois
  au démarrage : crée le premier compte `ADMIN` uniquement si aucun
  n'existe déjà en base, à partir des variables d'environnement
  `LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL` / `LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD`
  (aucun secret par défaut, aucune élévation d'un compte `USER`
  existant). Procédure documentée dans `backend/README.md`.
* **Statut** : résolu.

## Trois agendas Avignon sur quatre restent non identifiés

* **Détecté** : lors de LL-8009 (décision go/no-go de la bêta, 25 août
  2026), en vérifiant le critère d'acceptation de LL-8004 (« plusieurs
  agendas Avignon »).
* **Où** : `backend/src/main/resources/application.properties`
  (`openagenda.avignon-spectacles-uid`, `-patrimoine-uid`,
  `-loisirs-uid`), toutes vides.
* **Nature** : à l'origine, un écart plus grave existait — ces
  propriétés étaient définies mais **jamais lues par aucun bean**
  (`OpenAgendaCollector` ne consommait que `openagenda.agenda-uid`,
  un seul agenda au total, même pas Avignon-spécifique). Corrigé par
  LL-8009 : `OpenAgendaSourcesConfig` enregistre désormais
  automatiquement un collecteur par agenda dont l'uid est renseigné.
  Il ne reste donc plus qu'un écart de **donnée** (identifiants réels
  manquants), plus un écart de **code**.
* **Impact réel** : un seul agenda spécifiquement Avignonnais est
  actif (Culture, uid `79839448`), en plus de l'agenda de démonstration
  historique (Ministère de la culture, non Avignon-spécifique). La
  diversité de contenu (spectacles, patrimoine, loisirs) visée par
  LL-8004 n'est donc que partiellement au rendez-vous pour une bêta.
* **Correctif disponible** : aucune modification de code nécessaire —
  définir `OPENAGENDA_AVIGNON_SPECTACLES_UID`/`_PATRIMOINE_UID`/
  `_LOISIRS_UID` (ou les propriétés `openagenda.avignon-*-uid`
  correspondantes) avec de vrais identifiants d'agendas OpenAgenda
  suffit ; `OpenAgendaSourcesConfig` les enregistrera automatiquement
  au prochain démarrage.
* **Pourquoi pas corrigé immédiatement** : identifier des agendas
  OpenAgenda réels et pertinents pour Avignon (spectacles, patrimoine,
  loisirs) est une recherche métier qu'Alex doit faire (accès à
  openagenda.com, connaissance du terrain), pas quelque chose que
  Claude peut déterminer depuis cette sandbox (pas d'accès réseau à
  openagenda.com, domaine non autorisé).
* **Statut** : ouvert — signalé explicitement dans la décision LL-8009
  (`docs/PROJECT_STATUS.md`) comme condition à évaluer avant, ou tôt
  après, l'ouverture de la bêta.

---

## Backend — `GET /api/v1/users/{id}` public, expose l'email

* **Détecté** : audit sécurité LL-9004 (28 août 2026), catégorie
  Autorisation.
* **Où** : `UserController` (`com.locallife.backend.user.api`),
  `SecurityConfig` (`anyRequest().permitAll()` couvre cette route par
  défaut, aucune règle explicite ne la restreint).
* **Nature** : la route retourne `UserResponse` (id, username,
  **email**, role, createdAt) sans authentification requise. Permet
  l'énumération de tous les comptes par incrémentation d'id, avec
  fuite de l'email associé à chacun.
* **Impact réel** : pas de fuite de `passwordHash` (déjà exclu de
  `UserResponse` depuis LL-3010), mais fuite d'une donnée personnelle
  (email) et vecteur d'énumération de comptes, sans authentification.
* **Correctif disponible** : à trancher avec Alex — restreindre la
  route (authentifiée, ou limitée à l'utilisateur lui-même/`ADMIN`),
  ou réduire `UserResponse` pour cette route (retirer l'email) si un
  usage public partiel reste voulu côté frontend.
* **Pourquoi pas corrigé immédiatement** : je n'ai pas le contexte
  produit pour savoir si cette route a un usage prévu côté frontend
  nécessitant qu'elle reste publique — décision produit/sécurité,
  pas un choix technique unilatéral.
* **Statut** : ouvert.

---

## Backend — messages d'exception bruts renvoyés au client sur les 500

* **Détecté** : audit sécurité LL-9004 (28 août 2026), catégorie
  Données sensibles.
* **Où** : `GlobalExceptionHandler`
  (`com.locallife.backend.shared.api` ou équivalent — gestionnaire
  d'exceptions générique du projet).
* **Nature** : le corps de la réponse 500 inclut `exception.getMessage()`
  tel quel. Pas de stack trace (déjà correct), mais le message brut de
  certaines exceptions (ex. `DataIntegrityViolationException`) peut
  contenir des détails internes (nom de contrainte SQL, colonne,
  table).
* **Impact réel** : fuite d'information technique interne limitée
  (pas de données utilisateur), mais pas strictement conforme à
  l'esprit « aucune donnée technique interne exposée » du critère
  LL-9004.
* **Correctif disponible** : remplacer par un message générique côté
  client (« Une erreur interne est survenue »), conserver le détail
  dans les logs uniquement (déjà fait pour les logs actuellement).
* **Pourquoi pas corrigé immédiatement** : changement de comportement
  sur toutes les réponses 500 de l'API — à confirmer avec Alex que le
  détail actuel n'est utile à aucun consommateur (frontend, débogage
  bêta) avant de le retirer.
* **Statut** : ouvert.

---

## Infrastructure — un seul utilisateur PostgreSQL (migrations et runtime)

* **Détecté** : audit sécurité LL-9004 (28 août 2026), catégorie Base
  de données.
* **Où** : `infra/docker-compose.beta.yml` (`POSTGRES_USER`), utilisé
  à la fois par Flyway pour les migrations et par le backend pour
  toutes les requêtes applicatives.
* **Nature** : cet utilisateur est propriétaire de la base créée par
  l'image `postgis/postgis`, avec des privilèges complets sur cette
  base — plus large que nécessaire pour de simples opérations CRUD en
  exécution normale.
* **Impact réel** : limité pour une bêta à petite échelle (pas de
  surface d'attaque supplémentaire tant que l'accès réseau à
  PostgreSQL reste confiné, cf. catégorie 7 de l'audit, déjà
  conforme), mais ne respecte pas strictement le principe de
  privilèges minimaux.
* **Correctif disponible** : créer un second rôle PostgreSQL dédié au
  runtime applicatif (droits `SELECT`/`INSERT`/`UPDATE`/`DELETE` sur
  les tables applicatives uniquement, pas de droits DDL), garder
  l'utilisateur actuel réservé aux migrations Flyway.
* **Pourquoi pas corrigé immédiatement** : implique de modifier
  `docker-compose.beta.yml` et la procédure de déploiement
  (`BETA_DEPLOYMENT.md`) — décision d'architecture, à trancher avec
  Alex plutôt qu'un changement unilatéral, d'autant que ce n'est pas
  bloquant pour la validation de LL-9004.
* **Statut** : ouvert.

---

## Infrastructure — aucun header de sécurité HTTP explicite (Caddy)

* **Détecté** : audit sécurité LL-9004 (28 août 2026), catégorie CORS
  et sécurité HTTP.
* **Où** : `infra/Caddyfile.beta`.
* **Nature** : aucun header de sécurité explicite (HSTS,
  `X-Content-Type-Options`, `X-Frame-Options`, etc.) n'est configuré.
  Spring Security ajoute certains headers par défaut côté API, mais
  rien n'est garanti côté fichiers statiques servis par le conteneur
  frontend.
* **Impact réel** : faible pour une bêta fermée à un petit panel, mais
  à corriger avant une exposition plus large.
* **Correctif disponible** : ajouter un bloc `header` dans
  `Caddyfile.beta` (HSTS, `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY` au minimum).
* **Statut** : résolu par LL-9006 (Sprint 9, 01/09/2026). `infra/Caddyfile.beta`
  ajoute désormais un bloc `header` (`Strict-Transport-Security`,
  `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`) appliqué
  à toutes les réponses (API et fichiers statiques du frontend).

* **Détecté** : audit sécurité LL-9004 (28 août 2026), catégorie
  Authentification.
* **Où** : `JwtFilter` (`com.locallife.backend.auth` ou équivalent).
* **Nature** : `SignatureException`, `MalformedJwtException`,
  `ExpiredJwtException` et `IllegalArgumentException` sont capturées
  explicitement et renvoient 401, mais pas `UnsupportedJwtException`
  (jjwt) — un token JWT valide mais d'un type non supporté remonterait
  au `GlobalExceptionHandler` générique en 500 plutôt qu'en 401.
* **Impact réel** : nul en termes de sécurité (pas de fuite de droits,
  l'accès reste refusé), simple incohérence de code HTTP par rapport
  aux autres cas d'erreur JWT.
* **Correctif disponible** : ajouter `UnsupportedJwtException` au
  `catch` existant dans `JwtFilter`.
* **Pourquoi pas corrigé immédiatement** : correctif trivial et sans
  risque, mais périmètre de LL-9004 traité comme rapport d'audit
  d'abord — à inclure dans le prochain lot de correctifs validé par
  Alex plutôt que modifié isolément.
* **Statut** : résolu par LL-9006 (Sprint 9, 01/09/2026).
  `UnsupportedJwtException` ajoutée au bloc `catch` existant de
  `JwtFilter`, même comportement (401) que les autres cas d'erreur JWT.

---

<!--
Modèle pour une nouvelle entrée :

## <Titre court>

* **Détecté** : <date, contexte/ticket>.
* **Où** : <fichier(s) concerné(s)>.
* **Nature** : <description du problème>.
* **Impact réel** : <évaluation, même approximative>.
* **Correctif disponible** : <s'il y en a un>.
* **Pourquoi pas corrigé immédiatement** : <raison>.
* **Statut** : ouvert / en cours / résolu (avec date et commit si résolu).
-->
