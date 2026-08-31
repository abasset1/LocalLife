# Sprint Mobile — Version mobile de LocalLife

**Statut :** ⏳ À faire

---

# Objectif

Créer une **version mobile de LocalLife** destinée à Android et iOS, en réutilisant
au maximum l'API et les règles métier déjà validées par la version web.

Le mobile n'est pas une nouvelle version du produit : c'est un **nouveau client
du même backend**.

À la fin du sprint, un utilisateur doit pouvoir utiliser depuis son téléphone
les parcours essentiels de LocalLife :

- découvrir les activités autour de lui ;
- explorer la carte ;
- filtrer les activités ;
- consulter une activité ;
- voir les food trucks ;
- s'inscrire et se connecter ;
- proposer une activité.

L'administration reste dans la version web pour ce sprint.

---

# Prérequis

Le sprint ne doit commencer qu'après :

- clôture du Sprint 9 ;
- validation de la bêta web ;
- validation du Security Gate `LL-9004` ;
- disponibilité d'une API bêta stable ;
- validation des contrats API existants.

Le mobile ne doit pas être utilisé pour contourner un problème fonctionnel ou
de sécurité encore présent sur le backend.

---

# Périmètre

## Inclus

- nouvelle application mobile Android/iOS ;
- TypeScript ;
- framework React Native avec Expo ;
- communication avec l'API REST existante ;
- authentification JWT ;
- stockage sécurisé du token sur le téléphone ;
- carte interactive ;
- géolocalisation utilisateur ;
- recherche géographique ;
- filtres catégorie/date ;
- affichage des activités ;
- affichage des food trucks ;
- consultation du détail d'une activité ;
- contribution d'une activité ;
- gestion des états de chargement, erreur et absence de résultat ;
- navigation mobile ;
- tests principaux ;
- documentation de lancement et de build.

## Exclus

- modification du modèle métier uniquement pour le mobile ;
- nouvelle fonctionnalité métier ;
- notifications push ;
- mode hors-ligne ;
- synchronisation locale complexe ;
- paiement ;
- marketplace ;
- recommandations ;
- chat ;
- fonctionnalités sociales ;
- administration mobile ;
- refonte complète de l'application web ;
- deuxième backend ou microservice mobile ;
- duplication de la logique métier dans l'application mobile.

---

# Architecture cible

Le mobile doit rester un **client API**.

```text
                    ┌──────────────────┐
                    │    PostgreSQL    │
                    │     + PostGIS    │
                    └────────▲─────────┘
                             │
                    ┌────────┴─────────┐
                    │   Spring Boot    │
                    │      API         │
                    └───────▲───▲───────┘
                            │   │
              ┌─────────────┘   └─────────────┐
              │                               │
      ┌───────┴────────┐              ┌───────┴────────┐
      │ Frontend Web   │              │ Mobile Expo    │
      │ React/TS       │              │ React Native   │
      └────────────────┘              └────────────────┘
```

Règles :

- le backend reste la source de vérité ;
- les règles métier restent côté backend ;
- le mobile ne communique jamais directement avec PostgreSQL ;
- les endpoints existants sont réutilisés lorsqu'ils couvrent le besoin ;
- un nouvel endpoint backend ne peut être ajouté que si un besoin mobile réel
  le justifie ;
- aucun domaine métier ne doit être dupliqué dans le mobile.

---

# Tickets

## LL-MOB-0001 — Initialiser l'application mobile

**Priorité : Haute**

**Dépendance :** Sprint 9 terminé

### Objectif

Créer le projet mobile LocalLife et établir le socle technique.

### Contraintes

- React Native ;
- Expo ;
- TypeScript ;
- structure claire par fonctionnalités ;
- configuration Android/iOS ;
- configuration des environnements API ;
- aucun secret dans le dépôt.

### Critères d'acceptation

- l'application démarre sur Android ;
- l'application démarre sur iOS ou sur un environnement de validation iOS ;
- le projet est versionné dans le dépôt ;
- TypeScript est correctement configuré ;
- l'URL de l'API est configurable par environnement ;
- aucun secret backend n'est embarqué ;
- le README mobile permet à un développeur de lancer le projet.

---

## LL-MOB-0002 — Mettre en place la navigation mobile

**Priorité : Haute**

**Dépendance :** `LL-MOB-0001`

### Objectif

Créer une navigation adaptée aux usages mobiles.

### Navigation cible

- Carte / Découvrir ;
- détail d'une activité ;
- contribution ;
- compte.

L'implémentation peut utiliser une navigation par onglets ou une combinaison
tabs + stack si cela améliore le parcours.

### Critères d'acceptation

- la navigation est utilisable au tactile ;
- le retour système Android fonctionne correctement ;
- les écrans principaux sont accessibles sans manipulation complexe ;
- aucun écran web n'est simplement encapsulé dans une WebView pour remplacer
  l'interface mobile.

---

## LL-MOB-0003 — Intégrer le client API LocalLife

**Priorité : Haute**

**Dépendance :** `LL-MOB-0001`

### Objectif

Créer une couche d'accès à l'API séparée de l'interface utilisateur.

### À couvrir

- activités ;
- recherche géographique ;
- catégories ;
- food trucks ;
- authentification ;
- contribution ;
- gestion des erreurs HTTP.

### Critères d'acceptation

- les appels API sont centralisés ;
- les composants d'interface ne construisent pas directement les URLs API
  partout dans le code ;
- les réponses sont typées ;
- les erreurs réseau et HTTP sont distinguées lorsque nécessaire ;
- l'URL de l'API n'est pas codée en dur dans les écrans.

---

## LL-MOB-0004 — Implémenter l'authentification mobile

**Priorité : Haute**

**Dépendance :** `LL-MOB-0003`

### Objectif

Permettre l'inscription et la connexion depuis le mobile avec le backend
existant.

### Contraintes

- réutiliser les endpoints existants ;
- stocker le JWT dans un stockage sécurisé adapté au mobile ;
- ne jamais stocker le mot de passe ;
- gérer l'expiration ou l'invalidation du JWT ;
- ne pas afficher de données sensibles.

### Critères d'acceptation

- inscription fonctionnelle ;
- connexion fonctionnelle ;
- session restaurée après fermeture/réouverture de l'application ;
- déconnexion fonctionnelle ;
- token absent/invalide correctement géré ;
- aucune donnée sensible n'est écrite dans les logs.

---

## LL-MOB-0005 — Implémenter la carte mobile

**Priorité : Haute**

**Dépendance :** `LL-MOB-0003`

### Objectif

Adapter le cœur de LocalLife à une interaction tactile.

### Fonctionnalités

- carte interactive ;
- déplacement ;
- zoom ;
- chargement des activités de la zone visible ;
- marqueurs d'activités ;
- clustering si nécessaire pour conserver la lisibilité ;
- marqueurs food trucks distincts ;
- consultation rapide d'un élément.

### Critères d'acceptation

- la carte est utilisable sur petit écran ;
- les activités publiées sont affichées ;
- les food trucks sont distinguables ;
- le déplacement de la carte recharge les données conformément au contrat
  `within-bounds` ;
- aucun appel API n'est effectué à chaque mouvement de doigt sans mécanisme
  raisonnable de temporisation ;
- les états chargement/erreur/aucun résultat sont visibles.

---

## LL-MOB-0006 — Ajouter la géolocalisation utilisateur

**Priorité : Haute**

**Dépendance :** `LL-MOB-0005`

### Objectif

Permettre à l'utilisateur de centrer la carte sur sa position et de
rechercher les activités proches.

### Contraintes

- demander la permission au moment où elle est nécessaire ;
- expliquer clairement l'usage de la localisation ;
- ne pas stocker la position côté serveur ;
- gérer refus, indisponibilité et erreur de localisation.

### Critères d'acceptation

- la permission est demandée explicitement ;
- l'utilisateur peut centrer la carte sur sa position ;
- la recherche autour de la position fonctionne ;
- le refus de permission n'empêche pas l'utilisation générale de la carte ;
- les erreurs de localisation sont compréhensibles.

---

## LL-MOB-0007 — Implémenter les filtres et la découverte

**Priorité : Haute**

**Dépendance :** `LL-MOB-0005`

### Objectif

Reproduire sur mobile les capacités de découverte déjà validées sur le web.

### Fonctionnalités

- filtre catégorie ;
- filtre date ;
- combinaison des filtres ;
- réinitialisation des filtres ;
- recherche dans la zone visible ;
- conservation d'un état d'interface cohérent lors des déplacements de carte.

### Critères d'acceptation

- les filtres correspondent aux contrats API existants ;
- les filtres sont utilisables au tactile ;
- la combinaison catégorie + date fonctionne ;
- la réinitialisation fonctionne ;
- aucune activité `PENDING`, `REJECTED` ou `ARCHIVED` n'est affichée par
  les recherches publiques.

---

## LL-MOB-0008 — Créer la consultation d'une activité

**Priorité : Haute**

**Dépendance :** `LL-MOB-0005`

### Objectif

Permettre de consulter une activité depuis son marqueur.

### Informations minimales

- titre ;
- description ;
- catégorie ;
- date ;
- localisation ;
- source lorsque disponible ;
- URL lorsque disponible.

### Critères d'acceptation

- un marqueur permet d'ouvrir le détail ;
- les informations correspondent à celles fournies par l'API ;
- le retour vers la carte fonctionne ;
- une activité inexistante ou indisponible est gérée proprement.

---

## LL-MOB-0009 — Afficher les food trucks

**Priorité : Moyenne**

**Dépendance :** `LL-MOB-0005`

### Objectif

Reprendre le jalon Food Truck existant dans l'expérience mobile.

### Critères d'acceptation

- les food trucks publiés sont récupérés depuis l'API ;
- leur représentation est distincte des activités ;
- leur consultation fonctionne ;
- aucune logique métier Food Truck n'est dupliquée dans le mobile.

---

## LL-MOB-0010 — Implémenter la contribution mobile

**Priorité : Haute**

**Dépendance :** `LL-MOB-0004`, `LL-MOB-0003`

### Objectif

Permettre à un utilisateur connecté de proposer une activité depuis son
téléphone.

### Fonctionnalités

- titre ;
- description ;
- catégorie ;
- adresse ;
- validation du formulaire ;
- envoi vers l'API ;
- affichage du résultat ;
- gestion des erreurs.

### Contraintes

Le mobile doit utiliser le même contrat de contribution que le frontend web.
Le géocodage reste une responsabilité du backend.

### Critères d'acceptation

- un utilisateur connecté peut proposer une activité ;
- une adresse valide est envoyée au backend ;
- l'activité apparaît dans l'état attendu (`PENDING`) ;
- une erreur de validation est compréhensible ;
- aucune logique de géocodage n'est implémentée dans le mobile.

---

## LL-MOB-0011 — Adapter l'UX mobile et l'accessibilité

**Priorité : Haute**

**Dépendance :** `LL-MOB-0002` à `LL-MOB-0010`

### Objectif

Rendre les parcours réellement utilisables sur téléphone, et pas seulement
fonctionnels techniquement.

### Contrôles

- zones tactiles suffisamment grandes ;
- textes lisibles ;
- clavier ne masquant pas les champs ;
- gestion correcte des rotations lorsque supportées ;
- contraste suffisant ;
- labels accessibles ;
- messages d'erreur compréhensibles ;
- états de chargement visibles ;
- fonctionnement avec les tailles d'écran courantes.

### Critères d'acceptation

- les parcours principaux sont réalisables uniquement au tactile ;
- aucun champ essentiel n'est inaccessible à cause du clavier ;
- les éléments interactifs importants disposent d'un nom accessible ;
- aucune information essentielle ne dépend uniquement de la couleur.

---

## LL-MOB-0012 — Tests mobiles

**Priorité : Haute**

**Dépendance :** `LL-MOB-0011`

### Objectif

Valider les parcours principaux et éviter une divergence avec la version web.

### Tests minimum

1. démarrage de l'application ;
2. affichage de la carte ;
3. chargement des activités ;
4. déplacement de carte ;
5. géolocalisation ;
6. filtre catégorie ;
7. filtre date ;
8. consultation d'une activité ;
9. affichage des food trucks ;
10. inscription ;
11. connexion ;
12. restauration de session ;
13. déconnexion ;
14. contribution ;
15. gestion d'une erreur API ;
16. gestion d'un refus de géolocalisation.

### Critères d'acceptation

- les tests automatisés pertinents sont présents ;
- les parcours critiques sont validés sur appareil ou émulateur ;
- aucune régression backend n'est introduite ;
- les différences de comportement avec le web sont documentées lorsqu'elles
  sont volontaires.

---

## LL-MOB-0013 — Préparer le build mobile bêta

**Priorité : Haute**

**Dépendance :** `LL-MOB-0012`

### Objectif

Produire une version installable permettant un test réel par un petit panel.

### À préparer

- configuration des environnements ;
- identifiant d'application Android ;
- identifiant d'application iOS ;
- icône et éléments minimum d'identité ;
- configuration des builds ;
- connexion à l'API bêta ;
- procédure d'installation ;
- versionnement.

### Critères d'acceptation

- un build Android installable est disponible ;
- un build iOS est disponible selon les contraintes de distribution Apple ;
- les builds utilisent l'API bêta ;
- aucun secret sensible n'est embarqué ;
- la procédure de test est documentée.

---

## LL-MOB-0014 — Documentation mobile

**Priorité : Moyenne**

**Dépendance :** `LL-MOB-0013`

### Objectif

Documenter le nouveau client mobile sans dupliquer inutilement la
documentation existante.

### À documenter

- architecture mobile ;
- lancement local ;
- configuration des environnements ;
- tests ;
- builds ;
- installation de la bêta ;
- dépendances avec l'API ;
- limites connues.

### Critères d'acceptation

- un développeur peut lancer le mobile à partir du README ;
- la procédure de build est documentée ;
- les contrats API utilisés sont référencés ;
- les limitations du sprint sont explicitement indiquées.

---

# Dépendances

```text
Sprint 9 terminé
      ↓
LL-MOB-0001
      ↓
LL-MOB-0003 ─────────────┐
      ↓                  │
LL-MOB-0004              │
      ↓                  │
LL-MOB-0010              │
                         │
LL-MOB-0005              │
      ↓                  │
LL-MOB-0006              │
      ↓                  │
LL-MOB-0007              │
      ↓                  │
LL-MOB-0008              │
      ↓                  │
LL-MOB-0009              │
      └──────────────┬───┘
                     ↓
              LL-MOB-0011
                     ↓
              LL-MOB-0012
                     ↓
              LL-MOB-0013
                     ↓
              LL-MOB-0014
```

`LL-MOB-0002` peut être réalisé en parallèle du socle API.

---

# Backend

Le backend ne doit pas être modifié par principe.

Avant de créer un endpoint mobile spécifique, vérifier :

1. si l'API existante couvre déjà le besoin ;
2. si le contrat existant peut être consommé directement ;
3. si une amélioration du contrat bénéficierait également au web.

Toute modification backend nécessaire doit faire l'objet d'un ticket séparé,
avec tests de non-régression.

---

# Décisions techniques

## Client mobile

Choix retenu : **React Native + Expo + TypeScript**.

Raison :

- cohérence avec le frontend React/TypeScript existant ;
- partage des conventions et d'une partie des modèles/types ;
- Android et iOS avec une base de code commune ;
- accès aux fonctionnalités natives nécessaires ;
- complexité limitée pour une première version mobile.

Le sprint ne cherche pas à créer deux applications natives distinctes.

## Carte

La carte mobile doit utiliser une solution compatible React Native permettant
de conserver les capacités nécessaires à LocalLife :

- affichage cartographique ;
- marqueurs ;
- déplacement/zoom ;
- géolocalisation ;
- interaction tactile.

Le choix précis de la bibliothèque doit être validé lors de `LL-MOB-0001`
selon sa compatibilité avec Expo et les plateformes ciblées.

## Stockage du JWT

Le JWT doit être stocké dans un mécanisme de stockage sécurisé fourni par
l'écosystème mobile, et non dans un stockage web de type `localStorage`.

---

# Principes UX

Le mobile doit privilégier :

- la carte comme point d'entrée ;
- les interactions tactiles ;
- peu de champs visibles simultanément ;
- des actions principales immédiatement accessibles ;
- des écrans courts ;
- un retour simple vers la carte ;
- des états réseau explicites.

Le mobile ne doit pas être une simple transposition pixel-perfect du frontend
web.

---

# Non-objectifs importants

Ce sprint ne doit pas devenir une deuxième phase de développement produit.

Il ne doit notamment pas introduire :

- un nouveau modèle d'utilisateur ;
- un nouveau système de contribution ;
- un nouveau système de recherche ;
- une nouvelle modération ;
- une nouvelle gestion des food trucks ;
- un système de notifications ;
- du offline ;
- une logique métier spécifique au mobile.

Le mobile doit **consommer le produit existant**.

---

# Definition of Done

Le Sprint Mobile est terminé lorsque :

- l'application Android fonctionne ;
- l'application iOS fonctionne ou dispose d'un build de validation adapté ;
- la carte LocalLife est utilisable au tactile ;
- les activités publiées sont visibles ;
- les food trucks sont visibles ;
- la recherche par zone fonctionne ;
- les filtres catégorie/date fonctionnent ;
- la géolocalisation fonctionne ;
- la consultation d'une activité fonctionne ;
- l'inscription fonctionne ;
- la connexion fonctionne ;
- la session peut être restaurée ;
- la déconnexion fonctionne ;
- la contribution fonctionne ;
- les erreurs principales sont gérées ;
- aucun secret n'est embarqué ;
- les tests critiques passent ;
- un build bêta mobile est disponible ;
- la documentation mobile est à jour ;
- aucune duplication du backend ou de la logique métier n'a été introduite ;
- aucune régression du frontend web ou de l'API n'est constatée.

---

# Hors périmètre pour les prochains sprints

Après ce sprint, les évolutions mobiles éventuelles devront être décidées à
partir des retours réels des utilisateurs.

Exemples possibles, sans engagement :

- notifications push ;
- favoris ;
- améliorations UX ;
- partage d'une activité ;
- itinéraire vers une activité ;
- mode hors-ligne partiel ;
- fonctionnalités communautaires.

Aucune de ces fonctionnalités n'est considérée comme acquise par ce sprint.

---

# Fin du Sprint

La clôture doit produire un bilan spécifique :

- stabilité Android ;
- stabilité iOS ;
- qualité de l'expérience tactile ;
- performance de la carte ;
- consommation réseau ;
- problèmes rencontrés ;
- différences avec le web ;
- retours des premiers utilisateurs mobiles.

Le sprint suivant doit être défini à partir de ces résultats et non avant leur
analyse.
