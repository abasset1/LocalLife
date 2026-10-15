# Proposition de design — direction « Garrigue »

Statut : **proposition, à valider par Alex** (aucun code applicatif modifié).
Maquettes interactives : canvas Claude « LocalLife — nouveau design » (lien privé, partagé à la demande).

## Constat

- Le frontend web actuel (`frontend/src/styles.css`) repose sur Arial et un bleu générique (`#2563eb`) ; l'app mobile (`mobile/src/constants/theme.ts`) sur un autre bleu (`#1A5FC8`) et les polices système. Les deux surfaces n'ont pas d'identité commune.
- La maquette d'accueil existante (`LocalLife_home_mockup.html`) pose déjà une piste vert / corail / sable, mais pour une page vitrine, pas pour l'explorateur carte + liste qui est le cœur du produit.
- Les 9 catégories V1 (CAT-001) n'ont aucune couleur attitrée : sur la carte, rien ne distingue un marché d'un concert.

## Principe

Fond calme (pierre calcaire), une couleur d'action (vert pin), un accent de contribution (terre cuite). **La couleur vive est réservée aux catégories** : elle sert à reconnaître une activité d'un coup d'œil, sur la carte (pin) comme dans la liste (vignette horaire).

## Tokens

| Token | Valeur | Usage |
|---|---|---|
| `--ll-pine` | `#1F4A3D` | action principale, filtre actif, onglet actif |
| `--ll-terracotta` | `#B2482A` | « Proposer une activité », favoris |
| `--ll-ochre` | `#E9B44C` | accent, marqueur food truck, avatar |
| `--ll-limestone` | `#F4F2ED` | fond d'application |
| `--ll-surface` | `#FFFFFF` | panneaux, cartes |
| `--ll-line` | `#E2DED4` | bordures, séparateurs |
| `--ll-ink` | `#1B2420` | texte |
| `--ll-ink-muted` | `#5A635E` | texte secondaire (≥ 4,5:1 sur le fond) |

### Couleurs des catégories V1

Texte blanc lisible sur toutes (≥ 4,5:1).

| Code | Libellé fr-FR | Couleur |
|---|---|---|
| CULTURE | Culture | `#6E3A86` |
| GASTRONOMY | Gastronomie | `#B2482A` |
| MARKETS | Marchés | `#8F5E12` |
| COMMERCE | Commerce | `#7A5C44` |
| SPORT | Sport | `#1F5C9E` |
| LEISURE | Loisirs | `#B83568` |
| NATURE | Nature | `#2F6B42` |
| TOURISM | Tourisme | `#136F73` |
| CITIZEN | Citoyen | `#4D5663` |

La correspondance code → couleur vit côté frontend/mobile (présentation), pas dans le référentiel : le référentiel V1 n'est pas modifié.

### Typographie

- Titres, heures, chiffres : **Bricolage Grotesque** 700/800 (Google Fonts).
- Texte courant, filtres, formulaires : **Instrument Sans** 400/600/700, 13–17 px.

### Formes

Rayons : 12 px (vignettes, boutons carrés), 16–24 px (cartes, panneaux), pilule pour les filtres et le CTA. Cibles tactiles ≥ 44 px.

## Écrans proposés

1. **Web — Explorer** : en-tête (logo, recherche + ville, « Proposer une activité », favoris, alertes, profil) ; barre de filtres (Quand : Aujourd'hui / Ce week-end / Ce mois-ci / Dates… ; 9 puces catégories ; rayon) ; liste groupée par jour à gauche avec vignette horaire colorée ; carte à droite avec pins par catégorie, marqueurs food truck carrés, cercle de rayon, « Rechercher dans cette zone », bouton Lieux, aperçu flottant de l'activité sélectionnée.
2. **Web — Fiche activité** : visuel, catégorie + type + public, titre, organisateur, description, prochaines dates ; colonne latérale date/lieu, Itinéraire, Favori, Agenda, mini-carte, source et signalement.
3. **Mobile — Découvrir** : carte plein écran, recherche flottante, puces horizontales, feuille de liste en bas, barre d'onglets Découvrir / Contribution / Compte (onglets actuels conservés).
4. **Mobile — Fiche activité** : visuel, puces, bloc date/lieu, description repliée, barre d'action fixe « Y aller » + agenda.

Les fonctionnalités représentées sont celles qui existent déjà (filtres date/catégories/rayon, onglets Activités/Lieux, food trucks, favoris, alertes, contribution, itinéraire, ajout Google Agenda) ; aucune nouvelle fonctionnalité n'est supposée.

## Mise en œuvre envisagée (après validation)

1. Tokens CSS dans `frontend/src/styles.css` (`:root`) et remplacement des couleurs en dur.
2. Mêmes valeurs dans `mobile/src/constants/theme.ts` + table `CategoryColors`.
3. Pins Leaflet colorés par `primaryCategoryCode`.
4. Vignette horaire dans `ActivityList`, puis fiche activité.

Chaque étape fera l'objet d'une PR séparée.
