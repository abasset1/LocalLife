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
