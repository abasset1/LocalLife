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
* **Statut** : ouvert.

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
* **Statut** : ouvert.

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
* **Statut** : ouvert.

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
