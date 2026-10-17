# FRONT-REV-01 — Compte rendu d'audit du frontend

- **Date** : 17 octobre 2026.
- **Référence** : `docs/Front-rev-01.md`.
- **Code audité** : deux versions, parce que le lot P0 n'est arrivé qu'en partie sur `main`.
  - `main` : `5c1633a` (« Create Front-rev-01.md »).
  - `main` + PR #135 : `f942bc0`, branche `ccr-6235e9ce-xq52nh-p0-rattrapage`. C'est le reste du lot P0 (P0-B à P0-E), déjà relu dans les PR #131 à #134 mais **pas encore fusionné dans `main`**.

## Mise à jour du 17 octobre 2026 : #135 fusionnée

Depuis la rédaction de ce compte rendu, la PR #135 a été fusionnée dans `main` (`106ebdb`, puis `a402e21`). Les quatre commits P0-B à P0-E sont désormais dans `main`. Contrôles relancés sur `main` (`a402e21`) :

- `npm ci` : OK, même vulnérabilité `source-map-js` dans l'outillage ;
- `npm run test` : 23 fichiers, **100 tests réussis** ;
- `npm run build` : OK ;
- scénarios de bout en bout : **35 PASS / 0 FAIL**.

**Décision mise à jour : `main` VALIDÉ AVEC RÉSERVES.** Les anomalies 1 à 6 de la section D sont corrigées. Les réserves restent les mêmes :

- aucun appareil Android physique ;
- aucun backend réel ;
- la vulnérabilité `source-map-js` de l'outillage.

La prochaine étape recommandée devient la recette sur téléphone Android réel (G.2). La suite du document décrit l'état au moment de l'audit initial.

## A. Résumé

**`main` ne contient que P0-A (#130).** Les PR #131 à #134 étaient empilées. Elles ont été fusionnées chacune dans la branche de la PR précédente, et non dans `main`. Sur `main`, il manque donc :

- l'onboarding facultatif ;
- le bouton « Réessayer » de la carte ;
- les messages visibles sur téléphone ;
- les correctifs Android.

Les 7 échecs constatés sur `main` sont tous corrigés par #135, qui est fusionnable sans conflit.

Avec #135, les 35 scénarios de bout en bout et les 100 tests automatisés passent. Le frontend est alors conforme à `docs/spec_front.md`, sous les réserves suivantes :

- aucun test sur un téléphone Android physique ;
- aucun test contre le backend réel (API simulée) ;
- une vulnérabilité npm de gravité élevée dans une dépendance de développement.

**Principaux risques :**

1. Fusion de #135 oubliée : le lot P0 resterait incomplet en production.
2. Comportements natifs Android non observés : fenêtre de permission, clavier, barre d'adresse.
3. Contrat d'API vérifié seulement par des réponses simulées.

## B. Tableau de validation

**Méthode.** Chromium (Playwright) en émulation mobile (`isMobile`, tactile) et en desktop, sur les builds de production (`vite preview`). L'API est simulée réseau par réseau et la géolocalisation est simulée. Le script et les captures sont hors dépôt (bac à sable de la session).

**Lecture des colonnes.** « main » et « main + #135 » donnent le résultat sur chaque version.

| Scénario | main | main + #135 | Preuve | Action |
|---|---|---|---|---|
| A1 Arrivée sans ville ni permission | PASS | PASS | 0 appel de géolocalisation, aucun `nearby` centré sur Marseille, invitation « Choisir ma ville » | Aucune |
| A2 Ville choisie puis recherche | PASS | PASS | Bandeau « 📍 Lyon », `latitude=45.758` transmis à la recherche | Aucune |
| A3 « Autour de moi », permission accordée | PASS | PASS | Position transmise à la recherche. Permission déjà accordée : la position est reprise sans fenêtre (comportement P0-A voulu) | Aucune |
| A4 « Autour de moi », permission refusée | PASS | PASS | « Localisation refusée… », carte chargée, choix de ville proposé | Aucune |
| A5 Position indisponible ou délai dépassé | PASS | PASS | « Position introuvable… » et « Réessayer » | Aucune |
| A6 Ville déjà enregistrée | PASS | PASS | `nearby` sur Avignon, pas de géolocalisation, pas d'invitation | Aucune |
| B1 Explorer sans configuration (« Plus tard ») | **FAIL** | PASS | main : pas de « Plus tard », seule sortie = déconnexion, l'accueil revient au rechargement. #135 : fermé sans `PUT`, toujours connecté, pas de réouverture | Fusionner #135 |
| B2 Enregistrement des préférences | PASS | PASS | `PUT` avec ville et catégorie, puis étape « alerte » | Aucune |
| B3 Erreur d'enregistrement visible | PASS | PASS | 503 affichée dans la fenêtre, qui reste ouverte | Aucune |
| C1 Recherche textuelle et pagination | PASS | PASS | « 25 résultats · 1–20 », puis « 21–25 sur 25 » (`offset=20`) | Aucune |
| C2 Requête trop courte | PASS | PASS | Message immédiat, aucun appel serveur | Aucune |
| C3 Filtres catégories et dates | **FAIL** | PASS | main (360 px) : le panneau Catégories ouvert recouvre le bouton « Dates ». #135 : `category=CULTURE&date=…&dateTo=…` | Fusionner #135 |
| C4 Filtre de rayon | PASS | PASS | `nearby … radius=10` | Aucune |
| C5 Carte ↔ liste, critères conservés | PASS | PASS | 20 résultats en liste, saisie et filtre conservés, aucun nouvel appel | Aucune |
| C6 Aperçu depuis une ligne, puis fiche | PASS | PASS | Aperçu puis fiche ouverts, aucune erreur JS | Aucune |
| C7 Aperçu depuis un marqueur | PASS | PASS | Aperçu ouvert (desktop) | Aucune |
| D1 Chargement normal | PASS | PASS | Indicateur pendant la requête, absent après | Aucune |
| D2 Erreur serveur et nouvelle tentative | **FAIL** | PASS | main : message brut du serveur, aucun « Réessayer ». #135 : « Le chargement des activités a échoué. », relance de la même URL, erreur effacée | Fusionner #135 |
| D3 Erreur réseau | PASS | PASS | Message de connexion, pas de « aucune activité » | Aucune |
| D4 Aucun résultat (carte) | PASS | PASS | main : message seul. #135 : message et actions utiles | Aucune |
| D5 Recherche texte vide ; page vide ≠ zéro résultat | PASS | PASS | « Aucun résultat pour « zzz » ». Page vide couverte par `App.search.test.tsx` | Aucune |
| D6 Messages visibles sur téléphone | **FAIL** | PASS | main : le message d'erreur (y = 12) est caché sous le bandeau flottant (jusqu'à y = 147). #135 : au premier plan, en bas de la carte | Fusionner #135 |
| E1 Inscription (erreur et succès) | PASS | PASS | « Cet email est déjà utilisé. », puis « Vérifie ta boîte mail » | Aucune |
| E2 Connexion, puis déconnexion | PASS | PASS | Erreur affichée, retour sur `/`, e-mail dans le menu, jeton effacé | Aucune |
| E3 Profil et favoris | PASS | PASS | Favori listé, « Mon compte » affiché, aucune erreur JS | Aucune |
| E4 Favoris sans compte | PASS | PASS | Redirection vers `/login` | Aucune |
| E5 Contribution : validation, puis envoi | PASS | PASS | « Choisis au moins une catégorie », puis `POST` avec `["CULTURE"]` | Aucune |
| E6 Contribution sans compte | PASS | PASS | Redirection vers `/login` | Aucune |
| R Petit écran 360×640 | **FAIL** | PASS | Aucun débordement horizontal. main : chips, Carte/Liste, « Lieux », × de 30 à 32 px. #135 : tout ≥ 40 px | Fusionner #135 |
| R Écran courant 412×915 | **FAIL** | PASS | Mêmes constats | Fusionner #135 |
| R Desktop 1366×768 | PASS | PASS | Aucun débordement ; cibles tactiles sans objet | Aucune |
| R Panneau « Rayon » à 360 px | **FAIL** | PASS | main : x = 125→445, sort de l'écran. #135 : x = 12→348 | Fusionner #135 |
| R Suggestions, clavier ouvert (360×330) | PASS | PASS | Suggestion visible et sélectionnable | Aucune |
| R Menu mobile | PASS | PASS | Lien visible, fermeture par Échap | Aucune |
| R Onboarding sur écran bas (360×560) | PASS | PASS | Fenêtre dans l'écran, « Continuer » atteignable | Aucune |
| Téléphone Android physique | BLOCKED | BLOCKED | Aucun appareil ni émulateur Android dans l'environnement : fenêtre de permission native, vrai clavier et barre d'adresse non observés | À faire sur appareil |
| Backend réel | NOT TESTED | NOT TESTED | API simulée ; contrats lus dans le code (`UserPreferencesController`, `ActivityController`) | À faire en recette bêta |

**Total** : main 28 PASS / 7 FAIL ; main + #135 35 PASS / 0 FAIL ; 2 contrôles BLOCKED ou NOT TESTED.

## C. Tests automatisés

Node v22.22.0 (le projet demande ≥ 20.19.0) et npm 10.9.4. Commandes lancées depuis `frontend/`.

| Commande | main (`5c1633a`) | main + #135 (`f942bc0`) |
|---|---|---|
| `npm ci` | OK. `npm audit` : 1 vulnérabilité élevée | OK. Même constat |
| `npm run test` | 21 fichiers, **92 tests réussis** | 23 fichiers, **100 tests réussis** |
| `npm run build` (`tsc --noEmit` + Vite) | OK, 121 modules | OK, 122 modules. Avertissement Vite sur la taille du bundle (> 500 kB), sans nouveauté |

**Vulnérabilité** : `source-map-js` (déni de service via des décalages de source map), dépendance indirecte de l'outillage de build. Elle n'est pas incluse dans le bundle livré : c'est un risque d'outillage, pas d'exécution.

## D. Anomalies

1. **Lot P0 incomplet sur `main`.** Gravité : **critique** pour la conformité à `spec_front.md`.
   - Reproduction : `git merge-base --is-ancestor 32c6a3d origin/main` échoue, et de même pour `4162748`, `6fceaa2` et `014aacd`.
   - Attendu : P0-A à P0-E sur `main`.
   - Constaté : seul P0-A y est.
   - Conséquences sur `main` : les anomalies 2 à 6 ci-dessous.
   - Fichiers : ceux de #135. Correction : fusionner #135.
2. **Onboarding obligatoire** (B1). Gravité : majeure.
   - Reproduction : compte avec `configured: false`.
   - Attendu : « Plus tard ». Constaté : seule sortie = déconnexion, et l'accueil revient au rechargement.
   - Fichier : `OnboardingModal.tsx`. Corrigé par #135.
3. **Erreur de carte sans relance, message brut** (D2). Gravité : majeure.
   - Reproduction : `within-bounds` répond 503.
   - Attendu : « Réessayer ». Constaté : « Erreur interne » sans action.
   - Fichiers : `App.tsx`, `ActivityList.tsx`. Corrigé par #135.
4. **Messages cachés sur téléphone** (D6). Gravité : majeure.
   - Reproduction : 360×640, vue Carte, erreur serveur.
   - Attendu : message visible. Constaté : il est sous le bandeau de recherche.
   - Fichier : `styles.css`. Corrigé par #135.
5. **Panneaux de filtres hors écran ou masquant d'autres commandes** (C3, panneau « Rayon »). Gravité : mineure à majeure.
   - Reproduction : 360×640, ouvrir « Rayon » ou « Catégories ».
   - Constaté : le panneau « Rayon » sort à droite (« 30 km » coupé), et le panneau Catégories recouvre « Dates ».
   - Fichier : `styles.css`. Corrigé par #135.
6. **Cibles tactiles de 30 à 32 px** (R). Gravité : mineure.
   - Fichier : `styles.css`. Corrigé par #135 (≥ 40 px).

**Observation non reproductible avec le backend réel.** Une réponse de préférences sans `defaultCategoryCodes` fait planter l'explorateur (« a is not iterable »). Le backend renvoie toujours une liste : ce n'est pas une anomalie actuelle. À garder en tête si l'API change.

## E. Modifications

**Aucune modification de code.** Toutes les anomalies constatées sont déjà corrigées par #135, déjà relu. Le seul fichier ajouté est ce compte rendu (`docs/front-rev-01-compte-rendu.md`). Aucun changement d'API, de modèle, de base de données ni de droits.

## F. Décision finale

- **`main` en l'état : NON VALIDÉ.** Les anomalies 1 à 4 sont critiques ou majeures.
- **`main` + #135 : VALIDÉ AVEC RÉSERVES.**
  - Aucun test sur un appareil Android physique.
  - Aucun test contre le backend réel.
  - Vulnérabilité `source-map-js` dans l'outillage.

## G. Prochaine étape recommandée

1. ~~Fusionner #135 dans `main`~~ : **fait** (`106ebdb`). `main` compte désormais 100 tests réussis.
2. **Recette sur un téléphone Android réel**, contre le backend de bêta. Vérifier :
   - la fenêtre de permission de localisation (accord, refus, refus définitif) ;
   - le clavier ouvert sur les suggestions ;
   - la barre d'adresse (`100dvh`) ;
   - l'onboarding sur un compte neuf.
   - Consigner le résultat dans `docs/spec_front_recette.md`.
3. **Ticket d'outillage** : mettre à jour la dépendance qui embarque `source-map-js`, via `npm audit fix` et une PR dédiée validée par `npm run build`. La vulnérabilité n'affecte pas le bundle livré.
