# Validation du parcours carte / liste / détail (LL-10009)

**Ticket :** LL-10009 — Valider le parcours carte / liste / détail
(`docs/05_Sprints/SPRINT_10.md`)
**Dépendances :** LL-10007 (vue liste), LL-10008 (filtre/tri par ville) —
toutes deux déjà livrées.

Ticket de validation, sur le modèle de
`docs/02_Architecture/MVP_VALIDATION_PROTOCOL.md` (LL-7001) : pas de
nouveau code fonctionnel attendu, seulement une vérification que la vue
liste (LL-10007/LL-10008) complète réellement la carte plutôt que de
créer un second parcours indépendant.

## 1. Limite d'environnement (même contrainte que LL-7001)

Comme documenté dans `MVP_VALIDATION_PROTOCOL.md` section 3, cette
session sandbox n'a pas accès à une base PostgreSQL réelle ni, cette
fois, à un navigateur pour cliquer réellement dans l'interface (Maven
Central reste hors des domaines réseau autorisés pour le backend ; npm
est accessible et a permis de compiler/typer le frontend pour
LL-10006/10007/10008, mais aucun outil de rendu/clic navigateur n'est
disponible ici — voir `AI_RULES.md`, pas d'introduction d'un outil
E2E comme Playwright/Cypress sans décision explicite, ce projet n'en a
aucun).

**Ce que ce document fournit donc :** une **revue de code exhaustive**
du parcours décrit par le ticket, tracée jusqu'aux lignes de
`frontend/src/App.tsx` concernées, plutôt qu'une exécution réelle.
Chaque étape est validée par le code source, pas observée à l'écran.
**Une exécution réelle (`npm run dev` + backend + données variées,
plusieurs villes/adresses) reste nécessaire avant de considérer LL-10009
définitivement clos** — à faire par Alex, comme pour le protocole MVP.

## 2. Parcours (10 étapes du ticket), tracé dans le code

1. **Ouvrir LocalLife** → `App()` s'affiche, l'effet de récupération des
   activités (dépendances `[selectedCategory, selectedDate, refreshKey,
   userPosition, mapBounds]`, `App.tsx:569`) se déclenche automatiquement.
2. **Afficher les activités sur la carte** → `viewMode` vaut `"map"` par
   défaut (`App.tsx:313` d'origine LL-EF-008) ; les marqueurs sont
   rendus depuis `visibleActivities` (`App.tsx:753` et son usage dans
   `<MarkerClusterGroup>`).
3. **Passer en liste** → bouton « Liste » (`App.tsx`, bandeau
   `view-mode-toggle`) change uniquement `viewMode` (état local), sans
   dépendance dans l'effet de récupération (§1) : **aucun nouvel appel
   réseau**, la liste consomme le même `visibleActivities` que la carte.
4. **Filtrer par ville** → `<select id="city-filter">` met à jour
   `selectedCity` ; `visibleActivities = filterAndSortActivities(activities,
   selectedCity, sortOrder)` recalcule à chaque rendu (pas de fetch,
   décision LL-10008 documentée dans `filterAndSortActivities`).
5. **Trier par date** → `<select id="sort-order">`, valeur `"date"`,
   même fonction `filterAndSortActivities`, tri
   `a.startDate.localeCompare(b.startDate)` — flat list, ville rappelée
   par ligne (`renderActivityListItem(activity, true)`).
6. **Ouvrir une activité** → chaque ligne est un `<button>`
   (`onClick={() => setSelectedActivity(activity)}`), ouvre la modale
   `modal-overlay`/`modal-dialog` (héritée de LL-EF-001/LL-EF-008).
7. **Vérifier son adresse** → modale : `<strong>Lieu :</strong>
   {formatActivityLocation(selectedActivity)}` (`App.tsx:1077`) —
   `formatActivityLocation` retourne `activity.address` tel quel s'il
   est renseigné (`App.tsx:233-241`, aucune transformation), donc
   strictement la valeur reçue de l'API.
8. **Revenir à la liste** → bouton « Liste » de nouveau, `viewMode`
   change seul ; `activities`/`selectedActivity` ne sont pas
   réinitialisés (pas de dépendance de l'effet §1 sur `viewMode`).
9. **Revenir à la carte** → idem, symétrique.
10. **Vérifier que l'activité correspond toujours aux mêmes données** →
    puisque `viewMode` n'est pas une dépendance de l'effet de
    récupération (§1) et que `selectedActivity` n'est réinitialisé que
    par sa propre fermeture (`setSelectedActivity(null)`), l'objet
    référencé est **littéralement le même objet JS** d'un aller-retour
    à l'autre (pas seulement une valeur égale recalculée) — garantie
    plus forte que ce que demande le critère.

## 3. Critères d'acceptation

| # | Critère | Validation (code) |
| - | ------- | ------------------ |
| 1 | Les données affichées dans la carte et la liste sont cohérentes | Les deux vues consomment exclusivement `visibleActivities` (`App.tsx:753`), une seule valeur dérivée de `activities` — pas deux chemins de données séparés. Confirmé par recherche exhaustive : aucun usage résiduel de `activities` brut dans le rendu (marqueurs, items de liste, états vide/erreur utilisent tous `visibleActivities`). |
| 2 | L'adresse affichée correspond à celle de l'API | `formatActivityLocation`/l'affichage direct de `activity.address` dans la ligne de liste (`App.tsx`, `activity-list-item-address`) n'appliquent aucune transformation à une valeur non nulle — seul un repli (« Adresse non renseignée » / ville / coordonnées) s'applique quand la donnée est absente, jamais une valeur inventée. |
| 3 | La ville affichée correspond à celle de l'API | En-tête de groupe (`groupActivitiesByCity`) et ligne de liste en mode tri explicite (`activity.city ?? UNKNOWN_CITY_LABEL`) utilisent la valeur brute. Le filtre ville lui-même compare par égalité stricte (`item.city === city`) sur cette même valeur brute, sans normalisation qui pourrait diverger du contenu API. |
| 4 | Les filtres ne produisent pas de divergence entre les vues | Catégorie/date : filtrés côté serveur, donc déjà identiques dans `activities` pour les deux vues. Ville/tri : appliqués une seule fois par `filterAndSortActivities`, dont le résultat (`visibleActivities`) est la seule source pour la carte et pour la liste — aucune divergence possible par construction (pas deux calculs séparés). |
| 5 | Aucune régression sur la recherche géographique | L'effet de récupération (`App.tsx:569`) ne dépend ni de `selectedCity`, ni de `sortOrder`, ni de `viewMode` — seuls `selectedCategory`/`selectedDate`/`refreshKey`/`userPosition`/`mapBounds` (inchangés depuis LL-EF-003/LL-4012) déclenchent un appel à `/nearby` ou `/within-bounds`. LL-10007/LL-10008 n'ont ajouté aucune dépendance à cet effet. |

## 4. Point d'attention (non bloquant, à surveiller)

Le libellé de la modale de détail est « Lieu » et non « Adresse »
(hérité de LL-EF-008/LL-8006 — `formatActivityLocation` couvre
adresse/ville/coordonnées selon disponibilité). Le critère d'acceptation
porte sur l'exactitude de la donnée affichée, pas sur l'intitulé du
champ : pas un défaut fonctionnel, mais à garder en tête si un testeur
suit le scénario du ticket à la lettre (« vérifier son adresse ») et
cherche un champ littéralement nommé « Adresse » dans la modale — il
la trouvera sous « Lieu ». Non corrigé ici (renommer un champ établi
depuis deux tickets serait un changement hors du périmètre strict de
LL-10009, une validation).

## 5. Conclusion

Les 5 critères d'acceptation et les 10 étapes du scénario sont
cohérents avec le code source à ce jour (revue exhaustive, `git log`
jusqu'à `LL-10008` inclus). **Validation par code uniquement** :
aucune régression identifiée sur la recherche géographique, aucune
divergence identifiée entre carte et liste, adresse/ville tracées
jusqu'à l'API sans transformation.

**Reste à faire avant clôture définitive de LL-10009** (hors capacité
de cette session sandbox) : exécution réelle du parcours par Alex
(`npm run dev` + backend + données couvrant plusieurs villes, avec et
sans adresse résolue) pour confirmer visuellement ce que cette revue
de code établit logiquement — même limite déjà documentée pour le
protocole MVP (LL-7001) et pour LL-10006 (`mvn verify` non exécutable
en sandbox).
