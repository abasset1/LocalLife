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
  recherche géographique ni la recherche par zone ne filtrent `status`
  par défaut (paramètre optionnel) — une activité `ARCHIVED` continue
  donc d'apparaître sur la carte comme n'importe quelle autre.
* **Impact réel** : une activité qui n'existe plus réellement (source
  supprimée) reste visible aux utilisateurs jusqu'à ce qu'un filtre
  explicite soit ajouté.
* **Correctif disponible** : exclure `ARCHIVED` par défaut côté
  requête (`ActivityRepository`) quand `status` n'est pas fourni
  explicitement, ou filtrer côté frontend.
* **Pourquoi pas corrigé immédiatement** : modifier le comportement par
  défaut d'un endpoint utilisé depuis le Sprint 4 est un changement de
  comportement qui dépasse le périmètre d'un ticket d'import (LL-5008)
  ou de documentation (LL-5012) — nécessite une décision produit/un
  ticket dédié.
* **Statut** : ouvert.

---

## Backend — aucun déclencheur pour le pipeline d'import

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
* **Statut** : ouvert.

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
