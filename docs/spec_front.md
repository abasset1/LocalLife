LocalLife — Spécification technique et fonctionnelle du lot P0 mobile

Version : 1.0
Périmètre : accueil de l’explorateur sur smartphone Android
Priorité : P0 — fiabilité et expérience initiale
Statut : prêt pour revue, sans modification du dépôt
Dépôt concerné : "abasset1/LocalLife"

1. Objectif

Améliorer l’expérience mobile existante sans réécrire l’explorateur ni introduire de régression sur les fonctions existantes.

Le lot P0 doit résoudre trois problèmes principaux :

1. La localisation initiale ne doit pas laisser croire que l’utilisateur se trouve dans une ville qu’il n’a pas choisie.
2. La découverte des activités doit rester accessible sans inscription ni configuration obligatoire.
3. Les chargements, erreurs et absences de résultats doivent être compréhensibles et permettre à l’utilisateur d’agir.

La consultation de la carte, de la liste et des détails d’activités reste gratuite.

2. Fichiers concernés

Fichiers principaux

- "frontend/src/App.tsx"
  
  - Initialisation de la localisation et des préférences.
  - Chargement des activités.
  - Gestion des états de chargement et d’erreur.
  - Affichage de l’onboarding et orchestration de l’explorateur.

- "frontend/src/components/explorer/ExplorerSearch.tsx"
  
  - Barre de recherche.
  - Suggestions de villes.
  - Recherche textuelle et interactions clavier.

- "frontend/src/components/explorer/ExplorerFilters.tsx"
  
  - Filtres de catégories, dates et rayon.
  - Accès à la géolocalisation.
  - Bascule carte/liste.

- "frontend/src/components/onboarding/OnboardingModal.tsx"
  
  - Configuration initiale des préférences.
  - Possibilité de reporter cette configuration.

- "frontend/src/styles.css"
  
  - Présentation mobile des commandes, messages et fenêtres.
  - Ajustements responsive ciblés.

Fichiers secondaires à examiner uniquement si nécessaire

- "frontend/src/components/explorer/ExplorerMap.tsx"
- "frontend/src/components/explorer/ActivityList.tsx"
- "frontend/src/components/explorer/SearchResultBanner.tsx"
- "frontend/src/components/explorer/useActivitySearch.ts"
- "frontend/src/components/explorer/constants.ts"

Ne pas modifier ces fichiers par anticipation. Vérifier d’abord si les changements peuvent être réalisés dans les composants principaux.

3. P0-A — Localisation explicite et fiable

Constat

Dans "App.tsx", la géolocalisation est actuellement demandée automatiquement dans certains parcours d’initialisation. Lorsque la position n’est pas disponible, la recherche de proximité utilise les coordonnées de Marseille comme solution de repli.

Ce comportement doit être revu : un utilisateur situé ailleurs peut recevoir des résultats qui ne correspondent pas à sa zone réelle.

Comportement attendu

1. Ne pas demander la localisation automatiquement à un visiteur qui arrive pour la première fois.
2. Proposer deux actions compréhensibles :
   - « Choisir ma ville »
   - « Autour de moi »
3. Ne déclencher la demande d’autorisation de localisation qu’à la suite d’une action explicite de l’utilisateur.
4. Si la permission est refusée, proposer la recherche par ville sans bloquer l’exploration.
5. Si la position est indisponible ou si la demande expire, afficher un message discret et proposer une autre méthode.
6. Ne jamais utiliser silencieusement Marseille comme position supposée de l’utilisateur.
7. Conserver le comportement des utilisateurs ayant déjà une ville par défaut enregistrée, sous réserve de vérifier que cette préférence est valide.

Règle technique

Ne pas remplacer simplement les coordonnées de Marseille par des coordonnées arbitraires.

Si aucune position n’est connue, l’application doit adopter un état explicite sans localisation choisie. Selon les possibilités de l’explorateur existant, elle peut proposer le choix d’une ville ou une vue générale non présentée comme une position personnelle.

Ne pas envoyer une requête de proximité centrée sur une position inventée. Ne pas modifier inutilement les contrats API existants.

Critères d’acceptation

- Permission de localisation refusée : l’application reste utilisable.
- Permission accordée après une action utilisateur : la recherche peut se recentrer sur la position obtenue.
- Position indisponible : une solution de remplacement est proposée.
- Ville choisie manuellement : la carte et la recherche utilisent cette ville.
- Aucun message ne présente une position inconnue comme étant la position réelle de l’utilisateur.
- Les préférences de ville déjà enregistrées restent opérationnelles.

4. P0-B — Première impression de l’accueil mobile

Objectif

Permettre à un nouvel utilisateur de comprendre immédiatement comment découvrir les activités.

L’explorateur existe déjà : il ne faut pas le remplacer par une page marketing ni repousser la carte et les résultats sous un grand bandeau.

Modifications attendues

- Conserver la recherche en haut de l’écran.
- Conserver le placeholder explicite existant ou le simplifier si les tests montrent qu’il est trop long sur petit écran.
- Rendre le choix de la ville et l’action « Autour de moi » faciles à comprendre et à trouver.
- Préserver la palette visuelle existante : vert forêt, crème, sable et touches corail.
- Limiter la hauteur de l’en-tête afin de laisser un maximum d’espace à la carte ou à la liste.
- Ne pas ajouter de nouveaux raccourcis de recherche dans ce lot : ils relèvent d’une amélioration ultérieure et nécessitent de vérifier les données disponibles.

Critères d’acceptation

- L’utilisateur identifie immédiatement le champ de recherche.
- Il comprend comment choisir une ville ou utiliser sa position.
- Les commandes restent visibles et utilisables sur un petit écran.
- L’accueil ne nécessite pas de faire défiler une longue introduction avant d’accéder aux activités.
- Le style reste cohérent avec les composants actuels.

5. P0-C — Onboarding facultatif

Constat

"OnboardingModal.tsx" impose actuellement le choix d’une ville, d’un rayon et d’au moins une catégorie lors de certaines premières connexions. Le parcours ne propose pas de sortie directe sans déconnexion.

Cette obligation est contraire à l’objectif de découverte immédiate.

Modifications attendues

- Ajouter une action explicite « Plus tard » à l’étape de configuration des préférences.
- Permettre de fermer l’onboarding sans déconnecter l’utilisateur.
- Laisser l’utilisateur accéder à l’explorateur avec les critères par défaut lorsque ses préférences ne sont pas configurées.
- Conserver l’enregistrement des préférences lorsqu’il choisit de les configurer.
- Ne pas imposer la création d’une alerte e-mail.
- Ne pas effacer ou remplacer des préférences déjà enregistrées lors d’un report.

Précaution technique

Vérifier comment "needsOnboarding" est déterminé dans "App.tsx" et comment la configuration "configured" est stockée.

Le report doit au minimum fermer la fenêtre pour la session en cours. Ne pas ajouter de champ en base ou de nouvel endpoint uniquement pour mémoriser ce report sans justification et validation préalable.

Critères d’acceptation

- L’utilisateur peut reporter la configuration.
- La fenêtre ne se rouvre pas immédiatement après avoir choisi « Plus tard ».
- Il peut explorer les activités et revenir ultérieurement aux préférences.
- Le parcours existant de sauvegarde des préférences reste fonctionnel.
- Les erreurs d’enregistrement restent visibles et ne ferment pas silencieusement la fenêtre.

6. P0-D — Chargement, erreurs et absence de résultats

Constat

"App.tsx" possède déjà des états de chargement et d’erreur. Le travail consiste à clarifier les états et à préserver les mécanismes de nouvelle tentative, pas à créer un système parallèle.

Comportements attendus

Chargement

- Afficher un indicateur compréhensible.
- Éviter les messages contradictoires ou les indicateurs qui restent affichés après la fin de la requête.
- Ne pas effacer inutilement les résultats lors d’un simple déplacement de carte si le comportement actuel permet de les conserver.

Erreur réseau ou serveur

- Distinguer l’erreur d’une recherche valide qui ne renvoie aucun résultat.
- Afficher un message en français, court et compréhensible.
- Proposer une action « Réessayer » lorsque la requête peut être relancée.
- Réutiliser les mécanismes de relance existants lorsqu’ils conviennent.

Absence de résultats

- Expliquer que les critères actuels ne correspondent à aucune activité.
- Proposer des actions utiles : retirer un filtre, élargir le rayon ou choisir une autre ville.
- Ne pas afficher ces suggestions si elles ne sont pas pertinentes pour l’état courant.
- Ne pas modifier automatiquement les critères de l’utilisateur sans le prévenir.

Recherche textuelle

- Conserver la gestion actuelle des requêtes trop courtes.
- Préserver les résultats et la navigation entre pages lorsque cela correspond au comportement attendu.
- Ne pas confondre l’absence de résultats sur une page et l’absence totale de résultats.

Critères d’acceptation

- Chargement, erreur et absence de résultats sont visuellement et fonctionnellement distincts.
- Une erreur réseau ne donne pas l’impression que la zone ne contient aucune activité.
- Une nouvelle tentative fonctionne sans rechargement complet de l’application.
- Les filtres et la recherche en cours sont conservés lors d’une relance appropriée.
- Les messages restent lisibles sur smartphone et ne masquent pas les commandes essentielles.

7. P0-E — Ergonomie sur smartphone Android

Contrôles à effectuer

- Barre de recherche et suggestions de villes.
- Panneaux de filtres et choix de rayon.
- Bouton de bascule carte/liste.
- Messages flottants de chargement et d’erreur.
- Aperçu d’activité et fiche détaillée.
- Menu mobile et fenêtres modales.

Règles

- Aucun contrôle essentiel ne doit être inaccessible derrière un panneau ou une fenêtre.
- Les boutons doivent être suffisamment grands pour être touchés confortablement.
- Les suggestions de ville doivent rester visibles et sélectionnables.
- Les fenêtres doivent tenir sur les écrans bas et permettre le défilement si nécessaire.
- Le clavier virtuel ne doit pas rendre impossible l’utilisation du champ de recherche ou la sélection d’une suggestion.

Ne pas modifier le layout desktop pour satisfaire artificiellement ces exigences. Si une règle CSS est partagée, vérifier son effet sur les deux tailles d’écran.

8. Plan de tests

Tests fonctionnels

1. Première ouverture sans compte et sans permission de localisation.
2. Choix manuel d’une ville et recherche d’activités.
3. Action « Autour de moi » avec permission accordée.
4. Action « Autour de moi » avec permission refusée.
5. Position indisponible ou timeout.
6. Première connexion avec préférences non configurées.
7. Report de l’onboarding.
8. Enregistrement des préférences.
9. Erreur serveur lors du chargement des activités.
10. Perte de réseau et nouvelle tentative.
11. Recherche valide sans résultat.
12. Recherche textuelle trop courte.
13. Passage carte/liste avec recherche et filtres actifs.
14. Vérification que les résultats ne sont pas centrés silencieusement sur Marseille lorsqu’aucune position n’est connue.

Tests visuels

Tester au minimum :

- un petit smartphone Android ;
- un smartphone Android de taille courante ;
- une fenêtre mobile avec clavier virtuel ouvert ;
- une fenêtre mobile avec suggestions de villes ouvertes ;
- une fenêtre mobile avec une erreur affichée ;
- une fenêtre mobile sans résultat ;
- un écran de grande taille pour vérifier l’absence de régression desktop.

Tests de non-régression

Vérifier les fonctions déjà disponibles :

- recherche textuelle ;
- suggestions et sélection d’une ville ;
- filtres de catégories et de dates ;
- filtre de rayon ;
- carte et liste ;
- ouverture d’un aperçu et d’une fiche d’activité ;
- favoris et fonctions de compte existantes ;
- récupération et pagination des résultats.

9. Contraintes d’architecture

- Réutiliser les états, fonctions et composants existants lorsqu’ils sont adaptés.
- Ne pas dupliquer la logique de recherche ou de géolocalisation.
- Ne pas introduire de nouvelle dépendance sans besoin démontré.
- Ne pas modifier les contrats API sans nécessité documentée.
- Ne pas entreprendre de refonte générale de "styles.css".
- Ne pas ajouter de fonctionnalité commerciale dans le lot P0.
- Ne pas supprimer de fonctions existantes pour simplifier l’interface.
- Documenter toute décision qui modifie le comportement métier.

10. Définition de « terminé »

Le lot P0 est considéré comme terminé lorsque :

- la localisation est explicite et n’induit plus l’utilisateur en erreur ;
- la configuration initiale peut être reportée ;
- la découverte reste accessible sans compte ;
- les états de chargement, d’erreur et d’absence de résultats sont clairs ;
- les interactions essentielles sont utilisables sur petit écran ;
- les tests de non-régression sont exécutés et documentés ;
- aucune régression majeure n’est constatée sur desktop.

Tout critère non testé doit être déclaré comme non vérifié, et non comme validé.

11. Ordre d’exécution recommandé

1. Localisation : corriger la position de repli et l’initialisation.
2. Onboarding : rendre la configuration facultative.
3. États de recherche : fiabiliser les messages et la relance.
4. Ergonomie mobile : vérifier les superpositions et les interactions.
5. Validation : exécuter les tests fonctionnels et visuels, puis consigner les résultats.

Les modifications doivent être limitées au lot P0. Les raccourcis de découverte, les améliorations avancées des cartes d’activités et les évolutions de fidélisation restent hors de ce lot.

Consigne finale : cette spécification autorise la préparation du travail, pas la modification du dépôt. Aucun fichier ne doit être créé, modifié, commité ou poussé sans autorisation explicite.
