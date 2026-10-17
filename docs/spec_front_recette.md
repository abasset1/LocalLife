# Lot P0 mobile — recette

Résultats des tests du plan de `docs/spec_front.md` (section 8), pour les PR P0-A à P0-E.

Légende :

- **Auto** : test automatisé (Vitest + Testing Library, `frontend/src/*.test.tsx`), au vert.
- **Visuel** : vérifié dans Chromium en émulation mobile (tactile, `isMobile`), avec une API simulée par Playwright et sans tuiles de carte.
- **Non vérifié** : pas testé ; à faire sur un appareil Android réel avant de le considérer comme validé.

Aucun test n'a été fait sur un appareil Android physique. L'émulation Chromium ne reproduit ni la fenêtre d'autorisation de localisation, ni le vrai clavier virtuel, ni la barre d'adresse rétractable.

## Tests fonctionnels

| # | Scénario | Résultat | Où |
|---|----------|----------|----|
| 1 | Première ouverture sans compte et sans permission | Auto + Visuel : aucune demande de position, aucun appel `nearby`, recherche sur la zone affichée, invitation « Choisir ma ville » / « Autour de moi » | `App.location.test.tsx` |
| 2 | Choix manuel d'une ville | Auto : la carte et la recherche utilisent la ville | `App.location.test.tsx`, `App.search.test.tsx` |
| 3 | « Autour de moi », permission accordée | Auto (géolocalisation simulée) : recentrage, bouton actif | `App.location.test.tsx` |
| 4 | « Autour de moi », permission refusée | Auto : message, choix de la ville proposé, carte utilisable | `App.location.test.tsx` |
| 5 | Position indisponible ou délai dépassé | Auto : message, « Réessayer » ou ville | `App.location.test.tsx` |
| 6 | Première connexion, préférences non configurées | Auto + Visuel (360×640) : l'accueil s'ouvre et défile | `App.onboarding.test.tsx` |
| 7 | Report de l'onboarding | Auto : « Plus tard », ✕ et Échap ; pas de réouverture dans la même session | `App.onboarding.test.tsx` |
| 8 | Enregistrement des préférences | Auto : chemin d'erreur (503 affichée, fenêtre ouverte). Chemin de succès : **non vérifié** par un nouveau test (code inchangé) | `App.onboarding.test.tsx` |
| 9 | Erreur serveur au chargement des activités | Auto + Visuel : message distinct et « Réessayer » | `App.states.test.tsx` |
| 10 | Perte de réseau et nouvelle tentative | Auto : relance avec les mêmes filtres | `App.states.test.tsx` |
| 11 | Recherche valide sans résultat | Auto : actions selon les critères (catégories, dates, rayon, ville) ; recherche texte inchangée | `App.states.test.tsx`, `App.search.test.tsx` |
| 12 | Recherche textuelle trop courte | Auto (existant, inchangé) | `App.search.test.tsx` |
| 13 | Passage carte/liste avec recherche et filtres | Auto (existant, inchangé) | `App.search.test.tsx` |
| 14 | Pas de centrage silencieux sur Marseille | Auto : aucune requête `nearby` sans lieu choisi, aucune mention de Marseille | `App.location.test.tsx` |

## Tests visuels (émulation Chromium)

| Cas | Résultat |
|-----|----------|
| Petit smartphone (360×640) | Visuel : en-tête compact, commandes visibles, cibles tactiles de 40 px au moins (44 px pour le menu) |
| Smartphone courant (412×915) | Visuel |
| Clavier virtuel ouvert (simulé par une fenêtre de 360×330) | Visuel : la liste des suggestions tient dans la hauteur visible et défile. **Non vérifié** avec un vrai clavier Android |
| Suggestions de villes ouvertes | Visuel |
| Erreur affichée | Visuel : en bas de la carte sur téléphone, en haut sur desktop |
| Aucun résultat | Visuel |
| Panneaux de filtres (Catégories, Rayon, Dates) | Visuel : le panneau « Rayon » ne sort plus de l'écran |
| Aperçu (bottom sheet) et fiche détaillée | Visuel : la fiche tient à l'écran et défile ; les messages sont masqués pendant l'aperçu |
| Grand écran (1366×768) | Visuel : aucune différence de mise en page avec `main`, hormis le texte d'aide de la recherche (P0-B) et les nouveaux messages |

## Non-régression

La suite Vitest complète est au vert : 100 tests. `tsc --noEmit` et `vite build` passent. Elle couvre :

- la recherche textuelle et la pagination ;
- les suggestions et la sélection de ville ;
- les filtres de catégories ;
- la couche « Lieux » ;
- la liste des activités.

Couverture **indirecte** seulement (code inchangé, sans nouveau test) :

- les filtres de dates ;
- le filtre de rayon hors cas « aucun résultat » ;
- l'ouverture d'un aperçu et d'une fiche, au-delà de la capture visuelle ;
- les favoris et les fonctions de compte.

## Reste à vérifier sur un appareil Android réel

- La fenêtre d'autorisation de localisation : accord, refus, refus définitif.
- La reprise silencieuse quand la permission est déjà accordée.
- Le clavier virtuel avec les suggestions de villes.
- La hauteur visible (`100dvh`) avec la barre d'adresse qui se rétracte.
