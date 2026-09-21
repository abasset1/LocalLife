Sprint Évol-Fix

Objectif

Améliorer l'expérience utilisateur, la robustesse et les fonctionnalités générales de LocalLife sans introduire de fonctionnalités sociales.

Les fonctionnalités sociales sont regroupées dans "SPRINT_SOCIAL.md".

---

Tickets

LL-EF-001 — Revoir l'affichage de la saisie d'une activité

Priorité : Haute
Type : Évolution / UX

- La création d'une activité nécessite d'être connecté.
- Le formulaire s'ouvre dans une nouvelle fenêtre/vue.
- Le formulaire doit être clair et structuré.
- Un utilisateur non connecté ne peut pas créer d'activité.
- La fermeture du formulaire doit fonctionner correctement.
- Le comportement doit rester cohérent avec l'authentification existante.

---

LL-EF-002 — Utiliser automatiquement la localisation

Priorité : Haute
Type : Évolution / UX / Carte

- Supprimer le bandeau « Utiliser la localisation ».
- Utiliser automatiquement la position disponible de l'utilisateur.
- Centrer la carte automatiquement lorsque la position est disponible.
- Gérer proprement le refus ou l'indisponibilité de la localisation.
- Ne jamais bloquer l'utilisation de LocalLife si la localisation n'est pas disponible.

---

LL-EF-003 — Revoir le rechargement de la carte

Priorité : Haute
Type : Évolution / UX / Performance

Le déplacement actuel de la carte provoque un rechargement visuel désagréable.

Objectifs :

- conserver la carte affichée pendant les déplacements ;
- éviter les coupures visuelles ;
- mettre à jour les marqueurs indépendamment du rendu de la carte ;
- éviter les appels API inutiles lors des déplacements rapides ;
- gérer correctement déplacement et zoom ;
- charger les nouvelles données en arrière-plan lorsque nécessaire.

Critère principal : l'utilisateur doit pouvoir déplacer et zoomer la carte sans avoir l'impression que celle-ci est entièrement reconstruite.

---

LL-EF-004 — Créer une interface d'administration

Priorité : Haute
Type : Évolution / Administration

Créer une interface réservée aux administrateurs permettant notamment :

- consulter les activités soumises par les utilisateurs ;
- consulter leur détail ;
- approuver une activité ;
- rejeter une activité ;
- consulter les activités selon leur statut ;
- visualiser clairement leur état de modération.

Le contrôle des droits doit être effectué côté backend et ne doit pas reposer uniquement sur le frontend.

Statuts minimum recommandés :

- "PENDING"
- "APPROVED"
- "REJECTED"

---

LL-EF-005 — Gérer les agendas depuis l'interface d'administration

Priorité : Haute
Type : Évolution / Administration

Permettre à un administrateur de :

- consulter les agendas/sources ;
- ajouter un agenda ;
- modifier un agenda si nécessaire ;
- supprimer un agenda ;
- confirmer une suppression.

La suppression doit être cohérente avec les activités déjà importées depuis cet agenda.

Un agenda peut représenter notamment une source externe telle qu'un flux RSS.

Important : ne pas recréer un système d'import RSS. L'architecture d'import existante doit être réutilisée.

---

LL-EF-006 — Créer une interface utilisateur

Priorité : Haute
Type : Évolution / UX / Compte utilisateur

Créer une interface permettant à l'utilisateur connecté de :

- consulter son compte ;
- consulter ses informations personnelles ;
- modifier les informations autorisées ;
- se déconnecter ;
- accéder aux futures fonctionnalités liées au compte.

L'accès aux données personnelles doit être protégé côté backend.

---

LL-EF-007 — Réinitialisation du mot de passe

Priorité : Haute
Type : Évolution / Authentification / Sécurité

Implémenter un parcours « Mot de passe oublié ».

Le système doit :

- permettre de demander une réinitialisation ;
- envoyer un lien sécurisé ;
- utiliser un token temporaire ;
- rendre le token utilisable une seule fois ;
- permettre de définir un nouveau mot de passe ;
- ne pas révéler si une adresse email existe ;
- ne jamais exposer le token dans les logs ;
- conserver uniquement le mot de passe sous forme sécurisée.

---

LL-EF-008 — Ajouter une liste des activités

Priorité : Haute
Type : Évolution / UX / Affichage

Ajouter une vue liste alternative à la carte.

Accès :

- bouton en haut à droite ;
- dans la même zone que « Filtrer par catégorie ».

La liste doit :

- regrouper ou trier les activités par ville ;
- trier les activités par date ;
- afficher les informations essentielles ;
- permettre d'accéder au détail ;
- conserver les filtres existants ;
- rester utilisable sur mobile.

Informations minimales :

- nom ;
- ville ;
- date ;
- catégorie si disponible.

---

LL-EF-009 — Afficher l'adresse d'une activité

Priorité : Moyenne
Type : Évolution / UX / Géolocalisation

L'activité possède déjà des coordonnées GPS issues du géocodage de son adresse.

Ajouter un reverse geocoding permettant d'obtenir une adresse lisible à partir des coordonnées.

L'adresse doit être visible dans le détail d'une activité.

Les coordonnées GPS restent la source de vérité.

Prévoir une gestion propre des erreurs du service de reverse geocoding.

---

LL-EF-010 — Identifier et annuler les activités créées manuellement

Priorité : Haute
Type : Évolution / Activités / Droits utilisateur

Concerne uniquement les activités créées manuellement par les utilisateurs.

Ne concerne pas les activités issues des agendas/imports externes.

Objectifs :

- identifier le créateur d'une activité ;
- permettre au créateur d'annuler son activité ;
- permettre à un administrateur de l'annuler ;
- demander confirmation avant annulation ;
- conserver l'historique plutôt que supprimer physiquement l'activité.

Statut recommandé :

"CANCELLED"

 ### Droits
 
- [x] Seul le créateur peut annuler son activité.
- [x] Un administrateur peut également annuler une activité.
- [x] Un autre utilisateur ne peut pas annuler l'activité.
- [x] Les contrôles de permission sont effectués côté backend.

### Critères d'acceptation

- [x] Une activité saisie manuellement possède une référence vers son créateur.
- [x] Le créateur est identifiable depuis le détail de l'activité.
- [x] Le créateur peut annuler son activité.
- [x] Une confirmation est demandée avant l'annulation.
- [x] Une activité annulée n'est plus affichée comme activité active sur la carte.
- [x] Une activité annulée n'apparaît plus dans la liste des activités actives.
- [x] L'activité reste conservée en base.
- [x] Un administrateur peut annuler une activité.
- [x] Un utilisateur quelconque ne peut pas annuler l'activité d'un autre utilisateur.
- [x] Les activités provenant des collectors ne sont pas concernées par cette fonctionnalité.
---

LL-EF-011 — Créer un compte via Google

Priorité : Moyenne
Type : Évolution / Authentification / Sécurité

Ajouter une authentification Google basée sur OAuth2/OpenID Connect.

Le système doit :

- proposer « Continuer avec Google » ;
- créer automatiquement un compte LocalLife si nécessaire ;
- associer correctement un compte existant ;
- utiliser l'email vérifié fourni par Google ;
- ne jamais stocker de mot de passe Google ;
- conserver une architecture permettant d'ajouter d'autres fournisseurs plus tard.

La vérification de l'identité doit être réalisée côté backend.

---

LL-EF-013 — Ajouter un événement à Google Agenda

Priorité : Moyenne
Type : Évolution / UX / Intégration externe

Ajouter une action « Ajouter à Google Agenda » dans le détail d'un événement.

Préremplir autant que possible :

- titre ;
- description ;
- date ;
- heure ;
- heure de fin ;
- adresse ;
- coordonnées GPS si pertinentes.

L'utilisateur doit pouvoir modifier les informations avant l'ajout.

Ne pas stocker les identifiants Google de l'utilisateur.

---

LL-EF-015 — « Y aller » vers un événement

Priorité : Haute
Type : Évolution / UX / Géolocalisation / Navigation

Ajouter une action « Y aller » dans le détail d'un événement.

La destination doit utiliser :

1. les coordonnées GPS si disponibles ;
2. l'adresse en solution de secours.

Ouvrir l'application de navigation compatible de l'utilisateur.

LocalLife ne doit pas implémenter son propre système de navigation.

---

LL-EF-016 — Ajouter un lien web et un contact à un événement

Priorité : Moyenne
Type : Évolution / UX / Événements / Contact

Permettre à un événement de posséder :

- une URL ;
- un numéro de téléphone ;
- une adresse email.

Ces informations doivent être accessibles depuis le détail de l'événement.

Prévoir :

- validation des URLs ;
- validation des emails ;
- validation des téléphones ;
- liens sécurisés ;
- appel téléphonique sur mobile ;
- ouverture du client email.

Prévoir une structure suffisamment propre pour permettre plusieurs contacts ultérieurement.

---

Fonctionnalités déplacées vers Sprint Social

Les tickets suivants ne font plus partie de ce sprint :

- "LL-EF-012" — Liker et consulter ses événements favoris
- "LL-EF-014" — Partager un événement
- "LL-EF-017" — Rendre le nom utilisateur unique
- "LL-EF-018" — Rechercher et ajouter un utilisateur comme ami
- "LL-EF-019" — Notification lorsqu'un ami like un événement
- "LL-EF-020" — Participer à un événement et notifier la communauté
- "LL-EF-021" — Créer des alertes par catégorie et périmètre géographique

Ils sont regroupés dans "SPRINT_SOCIAL.md".

---

LL-EF-017 — Refonte de l'écran Explorer autour de la carte

Type : Evolution UX / Frontend
Priorité : Haute
Complexité : Élevée
Sprint : "evol-fix"
Statut : À faire

---

1. Objectif

Faire évoluer l'écran principal d'exploration de LocalLife afin qu'il soit plus lisible, plus responsable et plus cohérent avec l'identité du produit.

L'écran actuel présente trop d'informations et de fonctionnalités simultanément.

LocalLife étant avant tout une application de découverte géographique des activités locales, la carte doit rester le cœur de l'expérience.

L'objectif n'est donc pas de supprimer la carte ou de la rendre secondaire, mais au contraire de construire toute l'interface autour d'elle.

Principe UX fondamental

«LocalLife est une application de découverte géographique. La carte constitue le point d'entrée principal de l'interface et ne doit jamais être reléguée au second plan.»

Les fonctionnalités secondaires doivent être accessibles à la demande, via le menu ou des panneaux contextuels.

---

2. Nouvelle hiérarchie de l'écran

L'interface doit suivre cette hiérarchie :

1. Carte
2. Filtres
3. Recherche
4. Activités / informations contextuelles
5. Fonctionnalités secondaires via le menu

L'utilisateur doit pouvoir ouvrir LocalLife et comprendre immédiatement :

«"Qu'est-ce qui se passe autour de moi ?"»

sans avoir besoin de parcourir plusieurs blocs d'interface.

---

3. Desktop

La carte doit occuper la majeure partie de l'espace disponible.

Organisation cible :

┌────────────┬──────────────────────────────────────────────┐
│            │ Recherche                                    │
│  LocalLife ├──────────────────────────────────────────────┤
│            │ Toutes Culture Musique Sport ...             │
│  Explorer  ├───────────────────────────────┬──────────────┤
│  Favoris   │                               │              │
│  Agenda    │                               │   Activités  │
│            │             CARTE             │              │
│  Amis      │                               │              │
│            │                               │              │
│  Proposer  │                               │              │
│            │                               │              │
│ Paramètres │                               │              │
└────────────┴───────────────────────────────┴──────────────┘

La proportion exacte entre carte et liste peut être adaptée au viewport, mais la carte doit rester visuellement dominante.

La liste ne doit pas transformer l'écran en agenda classique.

---

4. Mobile

Sur mobile, la carte devient encore plus importante.

État initial

La carte doit occuper pratiquement tout l'espace disponible.

Seuls les éléments indispensables doivent rester visibles :

- menu
- recherche
- filtres
- localisation
- marqueurs d'activités

La liste complète ne doit pas occuper l'écran par défaut.

Consultation d'une activité

Lorsqu'un utilisateur sélectionne une activité :

- afficher une bottom sheet
- conserver la carte visible derrière
- afficher les informations essentielles
- permettre d'ouvrir la fiche complète

Exemple :

┌─────────────────────────────┐
│ ☰ LocalLife        🔍   ◎   │
├─────────────────────────────┤
│ Toutes Culture Sport ...    │
├─────────────────────────────┤
│                             │
│                             │
│            CARTE             │
│        📍        📍         │
│                 📍          │
│                             │
│                             │
├─────────────────────────────┤
│ Jazz au village             │
│ Aujourd'hui · 20h00         │
│ 📍 Aubagne                  │
│ ♥ 24                        │
│                             │
│       Voir l'activité →     │
└─────────────────────────────┘

La fiche complète doit être accessible sans perdre le contexte géographique.

---

5. Menu principal

Les fonctionnalités qui ne sont pas nécessaires à la découverte immédiate doivent sortir de l'écran principal.

Le menu doit notamment pouvoir contenir :

Découvrir

- Explorer
- Mes favoris
- Mon agenda

Communauté

- Amis
- Proposer une activité

Compte

- Profil
- Paramètres

Le menu doit être accessible facilement sur mobile et desktop.

Les fonctionnalités secondaires ne doivent pas être affichées en permanence sur l'écran Explorer.

---

6. Filtres

Les filtres constituent une exception.

Ils doivent rester directement accessibles car ils participent directement à l'exploration.

Les catégories existantes doivent être affichées sous forme de boutons/chips.

Exemple :

Toutes | Culture | Gastronomie | Musique | Sport | Sorties | ...

Le filtre de localisation doit également rester facilement accessible.

Exemple :

⌖ Autour de moi

Ne pas créer de nouvelles catégories uniquement pour la maquette.

La source de vérité reste le système de catégories existant.

---

7. Recherche

Une recherche doit rester accessible depuis l'écran Explorer.

Elle permet de rechercher notamment :

- une activité
- un lieu
- éventuellement une ville

Elle ne doit cependant pas prendre visuellement le dessus sur la carte.

---

8. Interaction carte ↔ activité

Les deux éléments doivent être liés.

Clic sur un marqueur

Le système doit :

1. identifier l'activité
2. afficher son aperçu
3. éventuellement recentrer la carte
4. permettre d'ouvrir la fiche complète

Clic sur une activité

Le système doit :

1. sélectionner l'activité
2. positionner la carte sur celle-ci
3. afficher le marqueur correspondant
4. afficher son aperçu

L'utilisateur doit toujours comprendre le lien entre la carte et l'activité sélectionnée.

---

9. Fiche d'activité

La fiche complète ne doit pas être affichée en permanence.

Elle doit être accessible après interaction.

La fiche doit pouvoir présenter les informations déjà disponibles dans le modèle d'activité :

- titre
- image
- date / heure
- catégorie
- lieu
- adresse
- description
- informations complémentaires
- favoris
- participation
- lien externe
- etc.

Le contenu exact dépend des données réellement disponibles.

Ne pas inventer de nouveaux champs dans le cadre de ce ticket.

---

10. Identité visuelle

L'écran Explorer doit reprendre le design défini pour la page d'accueil LocalLife.

Couleurs principales

Vert principal : #173F35
Vert secondaire : #245447
Corail :         #E86A4A
Sable :          #F3EBDD
Fond :           #FCFBF7
Texte :          #17201D

Principes

- interface chaleureuse
- moderne
- lisible
- locale
- humaine
- peu chargée
- forte place donnée aux contenus visuels

Éviter :

- interface froide de type outil cartographique
- surcharge de boutons
- multiplication des panneaux
- esthétique générique de dashboard
- dégradés "startup" sans fonction
- utilisation excessive d'icônes ou d'emojis comme éléments graphiques

---

11. Responsive

Le comportement doit être pensé séparément pour desktop et mobile.

Desktop

- menu latéral
- carte dominante
- liste secondaire
- filtres visibles
- fiche contextuelle

Mobile

- carte dominante / plein écran
- menu accessible à la demande
- filtres horizontaux
- bottom sheet pour les activités
- fiche complète accessible après interaction

Le responsive ne doit pas être simplement une réduction de la version desktop.

---

12. Architecture frontend

Avant toute modification, analyser l'architecture frontend existante.

Le ticket ne doit pas conduire à :

- réécrire inutilement l'application
- déplacer arbitrairement les responsabilités
- créer un composant monolithique
- dupliquer la logique existante
- recréer les fonctionnalités déjà présentes

Les composants doivent rester séparés selon leurs responsabilités.

Exemples de responsabilités attendues :

Explorer
 ├── ExplorerMap
 ├── ExplorerFilters
 ├── ExplorerSearch
 ├── ActivityPreview
 ├── ActivityList
 ├── ExplorerMenu
 └── ActivityDetail

Ces noms sont indicatifs et doivent être adaptés à l'architecture réelle.

---

13. Gestion de la carte

La refonte ne doit pas dégrader les fonctionnalités cartographiques existantes.

À préserver notamment :

- affichage des activités
- position utilisateur
- centrage
- interaction avec les marqueurs
- filtrage
- chargement des données
- comportement lors des déplacements de carte

Attention : ne pas réintroduire un rechargement complet de la carte à chaque déplacement/pan si ce problème a déjà été corrigé dans "evol-fix".

---

14. Critères d'acceptation

CA-01 — Carte principale

La carte constitue visuellement l'élément principal de l'écran Explorer.

CA-02 — Mobile

Sur mobile, l'écran Explorer affiche initialement principalement la carte et les contrôles indispensables.

CA-03 — Menu

Les fonctionnalités secondaires sont accessibles depuis le menu et ne surchargent plus l'écran principal.

CA-04 — Filtres

Les filtres restent accessibles directement depuis l'écran Explorer.

CA-05 — Recherche

La recherche reste accessible sans masquer inutilement la carte.

CA-06 — Marqueur

La sélection d'un marqueur affiche l'activité correspondante.

CA-07 — Activité

La sélection d'une activité permet d'identifier sa position sur la carte.

CA-08 — Mobile bottom sheet

Sur mobile, l'aperçu d'une activité s'affiche sous forme de panneau contextuel sans supprimer la carte.

CA-09 — Fiche complète

La fiche complète d'une activité est accessible depuis son aperçu.

CA-10 — Design

L'écran utilise les couleurs et principes visuels définis pour la page d'accueil LocalLife.

CA-11 — Responsive

L'expérience desktop et mobile est adaptée à leurs contraintes respectives.

CA-12 — Régression

Les fonctionnalités existantes de carte, filtrage, recherche, géolocalisation et consultation des activités continuent de fonctionner.

CA-13 — Architecture

La modification respecte l'architecture frontend existante et n'introduit pas de composant monolithique ou de duplication injustifiée.

---

15. Tests

Test 1 — Ouverture Explorer

- ouvrir LocalLife
- accéder à Explorer
- vérifier que la carte est immédiatement visible
- vérifier qu'elle constitue l'élément principal

Test 2 — Filtres

- sélectionner plusieurs catégories
- vérifier que la carte se met à jour
- vérifier que les activités correspondent au filtre

Test 3 — Marqueur

- sélectionner un marqueur
- vérifier l'affichage de l'activité correspondante
- vérifier que sa position reste identifiable

Test 4 — Activité

- sélectionner une activité depuis la liste
- vérifier le centrage correspondant sur la carte
- vérifier l'ouverture de son aperçu

Test 5 — Menu

- ouvrir le menu
- vérifier l'accès aux fonctionnalités secondaires
- revenir à Explorer
- vérifier que l'état de la carte est conservé si l'architecture actuelle le permet

Test 6 — Mobile

Tester au minimum :

- 320 px
- 375 px
- 390 px
- 430 px

Vérifier :

- carte
- filtres
- menu
- bottom sheet
- fiche activité
- recherche

Test 7 — Géolocalisation

- autoriser la géolocalisation
- vérifier la position utilisateur
- utiliser "Autour de moi"
- vérifier le centrage

Test 8 — Régression

Vérifier que la refonte ne casse pas :

- chargement des activités
- filtres
- géolocalisation
- carte
- consultation d'une activité
- authentification
- fonctionnalités déjà présentes

---

16. Hors périmètre

Ce ticket ne doit pas introduire :

- nouveau modèle de données
- nouvelle logique métier
- nouvelles catégories
- nouveau système de recherche
- nouvelle gestion de géolocalisation
- nouveau système social
- refonte de la base de données
- refonte complète de la carte
- nouvelles fonctionnalités communautaires

L'objectif est la refonte de l'expérience frontend d'Explorer.

---

17. Référence design

La maquette "LocalLife Explorer" validée avec le produit constitue la référence UX/design du ticket.

Elle doit être considérée comme une direction visuelle et comportementale, et non comme une obligation de reproduire exactement chaque élément graphique.

L'implémentation doit privilégier :

cohérence avec l'application existante > reproduction aveugle de la maquette.

---

18. Definition of Done

Le ticket est terminé lorsque :

- [ ] la carte est le cœur de l'écran Explorer
- [ ] le mobile est conçu autour de la carte
- [ ] les fonctionnalités secondaires sont déplacées dans le menu
- [ ] les filtres restent immédiatement accessibles
- [ ] la recherche reste accessible
- [ ] les interactions carte ↔ activité fonctionnent
- [ ] la bottom sheet mobile est fonctionnelle
- [ ] la fiche complète est accessible
- [ ] l'identité visuelle de la Home est appliquée
- [ ] le responsive est validé
- [ ] les tests de régression passent
- [ ] aucune régression fonctionnelle n'est constatée
- [ ] aucune dette architecturale importante n'est introduite

---

Note d'implémentation pour l'IA

Ne pas commencer par coder.

1. Analyser le frontend actuellement présent dans le dépôt Git.
2. Identifier les composants responsables de l'écran Explorer.
3. Identifier la logique existante de carte, filtres, géolocalisation et activités.
4. Identifier ce qui peut être réutilisé.
5. Proposer la structure de composants avant modification.
6. Implémenter la nouvelle hiérarchie UX.
7. Vérifier les régressions.
8. Signaler séparément toute incohérence architecturale découverte.

Le résultat attendu est une évolution du frontend existant, pas une réécriture de l'application.

---

Règles

- Réutiliser l'architecture existante.
- Ne pas recréer les mécanismes d'import déjà présents.
- Ne pas introduire de dépendance inutile.
- Toute sécurité importante doit être contrôlée côté backend.
- Les fonctionnalités doivent rester compatibles avec la future version mobile.
- Préserver la compatibilité avec les activités importées et les activités créées manuellement.

---

Definition of Done

Un ticket est terminé lorsque :

- le backend est fonctionnel ;
- le frontend est intégré ;
- les droits d'accès sont vérifiés ;
- les cas d'erreur principaux sont gérés ;
- les tests nécessaires sont présents ;
- aucune régression connue n'est introduite ;
- la documentation concernée est mise à jour si nécessaire.
