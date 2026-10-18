# FRONT-REV-02 — Compte rendu

- **Ticket** : `docs/Front-rev-02.md` (fluidité de l'explorateur, catégories et recherche par ville).
- **Base** : `main` `9821cf6`.
- **Date** : 18 octobre 2026.

## Périmètre retenu : l'application mobile (`mobile/`)

Les constats du ticket décrivent l'application mobile Expo, testée sur Android :

- elle a une barre de quatre onglets (Découvrir, Liste, Contribuer, Compte) ;
- elle n'offre aucun choix de catégorie ;
- elle n'a pas de recherche de ville.

Le site web (`frontend/`) n'est pas concerné. Il a déjà un menu rétractable, des catégories à cocher et une recherche de ville avec suggestions, validés par FRONT-REV-01 (35/35 scénarios). Il n'est pas modifié ici.

## 1. Diagnostic avant modification

Les problèmes ont été reproduits sur l'export web de l'application (`expo export --platform web`, Chromium, API simulée) et par lecture du code.

### A. La recherche par ville ne fonctionne pas

| Cause | Preuve |
|---|---|
| Aucun champ de ville sur la carte. Seule la ville des paramètres (compte requis) recentre la carte | `src/features/map/MapScreen.tsx` : pas de saisie ; cadrage seulement depuis `useSearchPreferences` |
| Le champ de la liste annonce « Concert, marché, commune… » mais ne filtre que les activités déjà chargées autour du centre courant. Taper « Lyon » depuis Marseille donne « Rien trouvé ici » | `src/features/list/ActivityListScreen.tsx` (`matchesQuery` en local) |
| La vérification de ville existante (`/users/me/preferences/city`) exige un compte : un visiteur ne peut pas changer de ville | `src/api/preferences.ts` (`authenticated: true`) |
| Ville par défaut silencieuse : la liste affiche « Près de Marseille » sans choix de l'utilisateur | `ActivityListScreen.tsx` (`DEFAULT_CENTER`) |

**Solution retenue, sans changement backend** : le backend expose déjà `GET /api/v1/geocoding/city-suggestions`. Cet endpoint est public : le site l'utilise sans jeton, et `SecurityConfig` ne le protège pas.

### B. Pas de sélection de catégories opérationnelle

| Cause | Preuve |
|---|---|
| Aucun contrôle de choix de catégorie, ni sur la carte ni dans la liste. Seules les catégories par défaut des paramètres s'appliquent (« Tout afficher » / « Toutes les catégories ») | `MapScreen.tsx` (`clearedFilterKey`), `ActivityListScreen.tsx` (`showAllCategories`) |
| Carte et liste ont chacune leur propre levée du filtre et leur propre rayon. Un choix fait sur l'une n'existe pas sur l'autre ; seule la période était partagée (`periodStore`) | Mêmes fichiers |

### C. Onglets encombrants

La barre de quatre onglets occupe en permanence 66 px plus la zone de gestes, sous la carte et la liste (`src/app/(tabs)/_layout.tsx`). Il n'y a pas de menu.

### Fichiers concernés

- `src/app/(tabs)/_layout.tsx`, `compte.tsx`, `contribution.tsx`
- `src/features/map/MapScreen.tsx`, `MapScreen.web.tsx`
- `src/features/list/ActivityListScreen.tsx`, `useActivityList.ts`
- Nouveaux : `src/features/explorer/`, `src/features/navigation/`, `src/api/geocoding.ts`

## 2. Modifications

| Fichier | Changement |
|---|---|
| `src/api/geocoding.ts` (nouveau), `api/types.ts`, `api/index.ts` | `suggestCities` : `GET /geocoding/city-suggestions`, sans jeton |
| `src/features/explorer/explorerStore.ts` (nouveau) | Critères partagés carte/liste : lieu, catégories, rayon. Paramètres de l'utilisateur appliqués à leur changement seulement, sans écraser un choix fait entre-temps ; coordonnées de la ville enregistrée vérifiées ; critères des paramètres retirés à la déconnexion |
| `src/features/explorer/citySearch.ts`, `useCitySuggestions.ts`, `CitySearch.tsx` (nouveaux) | Recherche de ville : suggestions après une pause de frappe, états chargement / panne avec « Réessayer » / aucune commune, « ✕ » pour effacer la saisie ou retirer le lieu |
| `src/features/explorer/CategoryPanel.tsx` (nouveau) | Choix d'une ou plusieurs catégories du référentiel, « Toutes les catégories » |
| `src/features/navigation/menuEntries.ts`, `AppMenu.tsx` (nouveaux) | Menu rétractable : bouton « ☰ », panneau latéral (`Modal`, sans nouvelle dépendance), rubrique courante mise en évidence, fermeture par « ✕ », appui à côté ou retour Android |
| `src/app/(tabs)/_layout.tsx` | Barre d'onglets masquée. Le navigateur est conservé pour garder l'état de chaque vue |
| `src/features/map/MapScreen.tsx` | Recherche de ville et menu en haut ; boutons « Liste » et « Catégories » ; critères lus dans `explorerStore` ; cadrage sur le lieu choisi ; message « Vue d'ensemble » sans lieu ; zone de sécurité du bas |
| `src/features/list/ActivityListScreen.tsx`, `useActivityList.ts` | Recherche de ville, menu, bouton « Carte », catégories ; plus de `DEFAULT_CENTER` : sans lieu, pas de requête et invitation à choisir ; filtre texte renommé « Filtrer : concert, marché… » ; vide avec catégories → « Toutes les catégories » |
| `src/app/(tabs)/compte.tsx`, `contribution.tsx`, `MapScreen.web.tsx` | Bouton « ☰ » ; zone de sécurité du bas |
| `alertes.tsx`, `parametres.tsx`, `favoris.tsx`, `contributions/index.tsx`, `bienvenue.tsx`, `_layout.tsx` | Textes : « l'onglet Compte » devient « la rubrique Compte du menu » |
| `mobile/README.md`, `CHANGELOG.md` | Documentation |

**Anomalie trouvée et corrigée en cours de test** : toucher une commune proposée retirait d'abord le focus du champ, ce qui refermait la liste et annulait le choix. La liste dépend maintenant de la saisie, plus du focus.

Aucun changement de backend, de contrat d'API ou de modèle. Aucune nouvelle dépendance.

## 3. Tests exécutés (résultats réels)

| Commande (`mobile/`) | Avant | Après |
|---|---|---|
| `npm ci` | — | OK (une coupure réseau ECONNRESET au premier essai, OK au second) |
| `npx jest` | 20 suites, 127 tests OK | **24 suites, 142 tests OK** : `explorerStore`, `citySearch`, `geocoding`, `menuEntries` |
| `npm run typecheck` | OK | OK |
| `npm run lint` | 0 erreur, 9 avertissements | 0 erreur, 9 avertissements (aucun nouveau) |
| `npx expo export --platform web` | — | OK |

**Scénarios de bout en bout** sur l'export web (Chromium, émulation mobile 360×640, API simulée) : **10/10 PASS**.

| Critère du ticket | Scénario | Résultat |
|---|---|---|
| 1. Ville recherchée, sélectionnée, résultats actualisés | « lyo » → Lyon : « Près de Lyon (69) », `nearby` lat 45.758 ; puis Avignon : lat 43.949 | PASS |
| 2. Erreurs explicites et récupérables | Coupure réseau sur les suggestions : « Impossible de joindre le serveur… », « Réessayer » propose Lyon ; « zzzz » → « Aucune commune ne correspond » ; rien sous 2 caractères | PASS |
| 3. Catégories sélectionnées et désélectionnées | `CULTURE` → `CULTURE,SPORT` → `SPORT` → aucun filtre (« Toutes les catégories ») | PASS |
| 4. Mêmes filtres carte et liste | État unique (`explorerStore`, testé) ; la requête de la liste porte catégorie et rayon. La carte native n'a pas été observée (voir section 4) | PASS (liste, état partagé) / **NON VÉRIFIÉ** (carte native) |
| 5. Filtres conservés au changement de vue | 3 allers-retours liste → carte → liste : ville, « Catégories (1) », 10 km et dernière requête conservés | PASS |
| 6. Aucun résultat ≠ erreur | Vide avec catégorie : « Rien trouvé ici » et « Toutes les catégories ». Erreur 503 : « Erreur : Service indisponible », puis « Réessayer » | PASS |
| 7. Menu replié et déployé | Aucun onglet ; ouverture ; rubriques présentes ; fermeture par « ✕ » et par appui à côté | PASS (web) / **NON VÉRIFIÉ** sur Android |
| 8. Utilisable sur petit écran | 360×640 : pas de débordement horizontal, aucune cible tactile sous 40 px | PASS |
| 9. Pas de régression | Compte, Contribuer et retour Liste par le menu sans erreur JS ; 127 tests existants toujours au vert | PASS (hors carte native) |

**Captures vérifiées** : liste sans lieu, liste sur Lyon, panneau Catégories, menu ouvert, erreur. Le titre de la liste, tronqué à 360 px dans une première version, a été corrigé.

## 4. Points restant à tester manuellement (appareil Android)

L'environnement n'a ni appareil ni émulateur Android, et `react-native-maps` ne fonctionne pas sur le web. **Non vérifié, à tester avant de clore le ticket** :

1. **Carte native** :
   - champ de ville et suggestions au-dessus de la carte, clavier ouvert ;
   - cadrage sur la ville choisie ;
   - bouton « Catégories » et son panneau ;
   - filtre appliqué aux marqueurs ;
   - « Ma position » (point bleu, cadrage) ;
   - rayon autour de la ville choisie ;
   - bouton « Liste ».
2. **Menu sur Android** : panneau latéral, fermeture par le bouton retour, zone de sécurité (encoche, barre de gestes).
3. **Toucher une commune** proposée avec le clavier ouvert, sur la carte comme dans la liste.
4. **Utilisateur connecté** : ville, rayon et catégories des paramètres appliqués au démarrage, puis non écrasés par un choix fait ensuite.
5. **Parcours existants** : favoris, fiche d'activité, « Voir sur la carte » depuis une fiche, contribution, alertes.

Commande : `npx expo run:android --variant release`, après `npm ci`.

## 5. Décision

**Non clos.** Les critères 1, 2, 3, 5, 6 et 8 sont vérifiés. Les critères 4, 7 et 9 ne le sont qu'en partie : la carte native et le menu sur Android restent à tester sur un appareil (section 4).
