# FRONT-REV-03 — Refonte de l'écran Carte (issue #226)

- **Ticket** : issue GitHub #226, « Refonte de l'écran Carte : accès Lieux, Food Trucks et Night ».
- **Maquette validée** : [`docs/front-rev-03/maquette-carte.html`](front-rev-03/maquette-carte.html) (audit, maquettes, états, interactions).
- **Décisions validées** : 1A, 2A, 3A, 4A (détail ci-dessous).
- **Date** : 8 novembre 2026.
- **Périmètre** : application mobile (`mobile/`). Le site web n'est pas modifié. Aucun changement backend.

## Décisions

| N° | Question | Choix |
|---|---|---|
| 1 | Que montre « Tous » ? | **A** : activités, lieux et food trucks ensemble. La valeur enregistrée reste `activities` (appareil et `defaultMapLayer`), donc les choix déjà mémorisés et l'API restent valables. |
| 2 | Navigation en bas | **A** : la barre d'onglets revient avec les entrées principales d'avant : Carte, Liste, Contribuer (si une offre est ouverte), Compte. Le menu ☰ reste en haut pour le reste. |
| 3 | Recherche « une activité, un lieu » | **A** : la recherche reste une recherche de ville. La recherche par nom fera l'objet d'un ticket séparé (route serveur à créer). |
| 4 | Bouton « Affichage » | **A** : Plan / Satellite, et Liste. Satellite n'est pas proposé en Night ni avec les tuiles OpenStreetMap de développement. |

## Charte conservée

Le ticket évoquait une palette « vert / crème ». L'application n'en a pas : sa charte est celle de Neiri (bleu nuit `#1C2150`, rose `#D6295F`, lavande, dégradé orange → violet). Sur le site, `--ll-green` est un nom historique qui contient le bleu nuit. Conformément à la règle « conserver les couleurs existantes », la refonte reprend la composition de la maquette mais pas son vert. Toutes les couleurs viennent de `constants/theme.ts`.

Night est conservé tel quel : thème violet nuit, fond de carte sombre, marqueurs néon, logo du bouc (`neiri-night.webp`). Le raccourci Night garde son logo et son fond nuit dans tous les thèmes ; choisi, il s'allume en magenta.

## Ce qui change

### Écran Carte (`features/map/MapScreen.tsx`)
- **En-tête** : ☰ · logo Neiri (le bouc en Night) · bouton compte (ouvre l'onglet Compte). La recherche de ville passe en pleine largeur dessous.
- **Raccourcis** : Tous, Lieux, Food trucks, Night, avec pictogrammes système (`expo-symbols`) au lieu des emoji. Lieux et Food trucks restent soumis au lancement progressif. Un appui change les marqueurs sur place.
- **« Tous »** : activités (pastilles regroupées), lieux et food trucks sur la même carte. Les marqueurs gardent leurs formes et couleurs.
- **Filtres** (Tous et Night) : période, catégories, rayon, gratuit, avec leur valeur. « Réinitialiser » apparaît dès qu'un filtre est posé. Le filtre de cuisine reste propre à Food trucks.
- **Boutons flottants** à droite, au-dessus de l'aperçu : se recentrer (remplace « ◎ Autour de moi », mêmes messages de refus et d'indisponibilité) et Affichage (Plan, Satellite, Liste).
- **« Voir la liste (n) »** en bas : n = activités, lieux et food trucks visibles. Ouvre l'onglet Liste dans le même univers, avec la même ville et les mêmes filtres. Il remplace les listes « Liste (37) » et « Liste des lieux (5) » posées sur la carte (`ZoneActivityList`, `PlaceListPanel`, supprimés).
- **Aucun résultat** : le message propose « Modifier les filtres » et « Réinitialiser » quand des filtres sont posés.

### Aperçu (`features/map/PreviewCard.tsx`, `previewSheet.ts`)
Panneau du bas à trois positions :
- **réduit** : vignette, nature, nom, date courte (« Aujourd’hui · 8 h – 13 h »), lieu et distance (seulement quand la recherche part de la position de l'utilisateur), favori, et deux actions (Itinéraire + Ajouter à mon agenda pour une activité ; Itinéraire + Voir la fiche pour un food truck ; Itinéraire + Site du lieu pour un lieu) ;
- **développé** : poignée tirée vers le haut ou touchée. Ajoute la description, Partager, Voir la fiche complète, et, pour un lieu, le suivi, les activités à venir et la demande de gestion ;
- **fermé** : poignée tirée vers le bas depuis la position réduite, bouton ✕, ou appui sur la carte.
Un appui sur la vignette ou le titre ouvre la fiche. Le défilement « 2 / 4 » des activités au même endroit est conservé.

### Liste (`features/list/ActivityListScreen.tsx`)
- Mêmes univers que la carte, partagés avec elle (`features/map/layerStore.ts`) : changer d'univers d'un côté le change de l'autre.
- **Tous** mêle activités, lieux et food trucks par distance ; **Lieux** liste les lieux du rayon (`usePlaceList`, nouvelle carte `PlaceCard` qui cadre la carte sur le lieu) ; **Night** charge les activités de 20 h à 6 h, dans le thème nuit.
- Le thème Night est désormais piloté par la navigation (`app/(tabs)/_layout.tsx`) : il s'applique aussi quand on choisit Night depuis la liste.

### Navigation (`app/(tabs)/_layout.tsx`)
Barre d'onglets du bas rétablie (Carte, Liste, Contribuer, Compte), aux couleurs du thème affiché, Night compris. Les écrans des onglets ne réservent plus la barre de gestes eux-mêmes.

### Textes
Guide de l'application et réglage « Carte à l'ouverture » (« Tous » au lieu de « 📅 Activités ») mis à jour. La recherche de ville affiche « Rechercher une ville ou un code postal ».

## États traités

| État | Traitement |
|---|---|
| Chargement | « Chargement… », marqueurs déjà affichés conservés |
| Aucun lieu choisi | Vue Provence, invitation à chercher une ville ou toucher ◎ |
| Aucun résultat | Message selon la période ou « Gratuit », avec « Modifier les filtres » et « Réinitialiser » |
| Erreur | Message et « Réessayer » |
| Position refusée, indisponible, en erreur | Messages existants, « Ouvrir les réglages » si le système ne peut plus redemander |
| Filtres actifs | Valeurs affichées, « Réinitialiser » |
| Zone trop large | « Zoomez pour voir les activités », regroupement des pastilles conservé |
| Retour depuis une fiche ou la liste | Carte, univers, ville, filtres et aperçu conservés (état partagé, onglets non démontés) |
| Night | Thème nuit sur toute l'application, satellite désactivé |

## Vérifications

- `tsc --noEmit` : sans erreur.
- `jest` : 51 suites, 264 tests, tous verts. Nouveaux tests : `layerStore.test.ts` (univers partagé, priorité des paramètres, repli sur « Tous »), `previewSheet.test.ts` (gestes, date courte, distance), `list/__tests__/placeList.test.ts` (lieux du rayon, mélange par distance, résumé), `mapControls.test.ts` mis à jour.
- `expo lint` : aucune erreur. Un avertissement de plus (`react-hooks/set-state-in-effect` dans `usePlaceList`), du même type que ceux déjà présents dans `useActivityList` et `useFoodTruckList`.
- `expo export --platform web` : construit. La liste, ses univers, le passage en Night et la barre du bas ont été vérifiés dans Chromium.

## Points restant à vérifier ou à traiter

- **Vérification sur appareil** : la carte native (`react-native-maps`) n'existe pas sur le web. L'écran Carte, l'aperçu glissant, le satellite et les marqueurs mélangés doivent être vérifiés sur un build Android et iOS (petit écran type iPhone SE, grand écran, clair, sombre et Night). Pas de nouvelle dépendance native : un build de développement existant suffit.
- **Recherche par nom d'activité ou de lieu** : ticket séparé (décision 3).
- **Carte « Tous » chargée** : les lieux ne sont demandés qu'en dessous de 2° d'étendue, comme avant ; en vue Provence, seules les activités et food trucks s'affichent.
- **Bouton « Carte » de la liste** : conservé en haut de la liste, il fait doublon avec l'onglet Carte et pourra être retiré.
