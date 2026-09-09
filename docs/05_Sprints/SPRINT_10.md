Sprint 10 — Adresses, villes et liste des activités

Statut : ✅ Terminé

---

Objectif

Faire évoluer LocalLife afin que la localisation d'une activité ne soit plus
limitée à ses coordonnées GPS.

À la fin du sprint :

- chaque activité exploitable par l'utilisateur possède une adresse
  structurée lorsque celle-ci est disponible ;
- la ville d'une activité est une donnée métier exploitable par l'API ;
- l'adresse est affichée dans le frontend ;
- les activités peuvent être consultées sous forme de liste ;
- les activités peuvent être filtrées et triées par ville ;
- le tri par date reste disponible ;
- les données issues des contributions manuelles et des collectors suivent
  les mêmes règles de normalisation ;
- l'API fournit un contrat suffisamment stable pour être réutilisé par le
  futur client mobile.

Le sprint ne doit pas créer un second modèle métier uniquement pour le
frontend.

---

Principe architectural

La localisation d'une activité doit être considérée comme une donnée métier.

Le système conserve les coordonnées GPS nécessaires à la carte et à la
recherche géographique, mais expose également les informations humaines
nécessaires à l'utilisateur.

Modèle cible minimal :

Activity
├── id
├── title
├── description
├── category
├── startDate
├── endDate
├── status
├── address
├── postalCode
├── city
├── latitude
└── longitude

"city" et "postalCode" sont des données structurées et ne doivent pas être
déduits par le frontend à partir de la chaîne "address".

Les coordonnées GPS restent la référence pour les fonctionnalités
géographiques.

---

Règles

- PostgreSQL/PostGIS reste la solution de persistance et de recherche
  géographique.
- Aucun nouveau moteur de recherche n'est introduit.
- Le frontend ne déduit pas la ville depuis l'adresse.
- Le frontend ne géocode pas les activités existantes.
- Les collectors et les contributions manuelles utilisent les mêmes règles
  de normalisation du lieu.
- Une adresse normalisée doit être persistée avec les informations
  structurées disponibles.
- Les données historiques ne doivent pas être détruites lors de la
  migration.
- Une information de localisation indisponible ne doit pas provoquer
  artificiellement une adresse incorrecte.
- Les coordonnées GPS existantes restent compatibles avec la carte et la
  recherche "nearby" / "within-bounds".
- Les filtres et tris sont exécutés côté backend/base de données, pas sur un
  jeu de données arbitrairement limité côté frontend.

---

Périmètre

Inclus

Backend

- évolution du modèle "Activity" ;
- migration Flyway ;
- normalisation de l'adresse ;
- conservation des coordonnées GPS ;
- exposition des informations d'adresse dans les DTOs ;
- filtre par ville ;
- tri par ville ;
- tri par date ;
- API de consultation sous forme de liste ;
- tests unitaires et d'intégration.

Frontend

- affichage de l'adresse d'une activité ;
- affichage de la ville ;
- vue liste des activités ;
- filtre par ville ;
- tri par ville ;
- tri par date ;
- conservation des filtres existants ;
- navigation carte ↔ liste ;
- gestion des états vide, chargement et erreur.

Documentation

- contrat API ;
- modèle de localisation ;
- règles de normalisation ;
- documentation du nouveau parcours liste/ville ;
- mise à jour du statut projet et du backlog.

---

Exclus

- nouvelle application mobile ;
- notifications ;
- favoris ;
- recommandations ;
- recherche plein texte avancée ;
- Elasticsearch ;
- référentiel national complet des communes ;
- refonte graphique globale ;
- nouvelle fonctionnalité sociale ;
- modification de la logique métier des collectors au-delà de la
  normalisation nécessaire au lieu ;
- système complexe de gestion de lieux réutilisables entre activités.

---

Tickets

LL-10001 — Définir le contrat de localisation d'une activité

Priorité : Haute

Dépendance : aucune

Objectif

Définir précisément les données de localisation exposées par une activité.

Contrat cible

Une activité doit pouvoir exposer :

{
  "address": "10 rue de la République",
  "postalCode": "84000",
  "city": "Avignon",
  "latitude": 43.9493,
  "longitude": 4.8055
}

Les champs doivent être clairement documentés comme :

- adresse ;
- code postal ;
- ville ;
- latitude ;
- longitude.

Critères d'acceptation

- le contrat est documenté ;
- les noms des champs sont définis ;
- le comportement des champs absents est défini ;
- le contrat est compatible avec les données existantes ;
- le contrat peut être consommé aussi bien par le web que par le futur
  mobile.

---

LL-10002 — Faire évoluer la persistance de la localisation

Priorité : Haute

Dépendance : LL-10001

Objectif

Ajouter à la persistance les informations structurées nécessaires à
l'affichage et au filtrage par ville.

À prévoir

- migration Flyway ;
- ajout de "address" ;
- ajout de "postal_code" ;
- ajout de "city" ;
- conservation de "latitude" et "longitude" ;
- conservation de la colonne PostGIS existante ;
- stratégie de compatibilité avec les activités déjà présentes.

Critères d'acceptation

- la migration est reproductible ;
- les données existantes ne sont pas perdues ;
- les activités existantes restent visibles sur la carte ;
- les recherches géographiques continuent de fonctionner ;
- le modèle "Activity" reflète le nouveau contrat ;
- les tests d'intégration passent.

---

LL-10003 — Normaliser la localisation lors de la création d'une activité

Priorité : Haute

Dépendance : LL-10002

Objectif

Faire en sorte qu'une activité créée manuellement dispose, lorsque le
géocodage le permet, d'une localisation structurée.

Flux cible

Adresse saisie
      ↓
Service de géocodage
      ↓
Adresse normalisée
      +
Code postal
      +
Ville
      +
Latitude / Longitude
      ↓
Activity
      ↓
Persistance

Critères d'acceptation

- une adresse valide produit une localisation structurée ;
- la ville est persistée séparément de l'adresse ;
- le code postal est persisté séparément ;
- les coordonnées GPS restent persistées ;
- une erreur de géocodage est traitée conformément au comportement
  existant ;
- aucune ville n'est inventée à partir d'une adresse non fiable ;
- les tests couvrent les cas nominal et erreur.

---

LL-10004 — Appliquer la normalisation aux données des collectors

Priorité : Haute

Dépendance : LL-10003

Objectif

Garantir que les activités importées par les collectors disposent des
mêmes informations de localisation que les activités créées manuellement
lorsque les données sources le permettent.

Critères d'acceptation

- les données importées passent par la chaîne de normalisation prévue ;
- "city" est correctement renseignée lorsqu'elle est disponible ;
- "postalCode" est correctement renseigné lorsqu'il est disponible ;
- "address" est conservée sous une forme exploitable ;
- latitude et longitude restent compatibles avec PostGIS ;
- une activité importée reste une "Activity" normale pour le frontend ;
- les tests du pipeline d'import passent.

---

LL-10005 — Exposer l'adresse et la ville dans l'API des activités

Priorité : Haute

Dépendance : LL-10002

Objectif

Rendre les informations de localisation disponibles à tous les clients
de l'API.

Les réponses d'activités doivent notamment exposer :

id
title
description
category
address
postalCode
city
latitude
longitude
startDate
endDate
status

Critères d'acceptation

- "GET /api/v1/activities" expose les nouveaux champs ;
- "GET /api/v1/activities/nearby" les expose également ;
- "GET /api/v1/activities/within-bounds" les expose également ;
- aucun endpoint existant ne perd les coordonnées GPS ;
- aucun champ sensible ou inutile n'est exposé ;
- la documentation OpenAPI est mise à jour ;
- les tests d'API couvrent les nouveaux champs.

---

LL-10006 — Ajouter le filtre et le tri par ville

Priorité : Haute

Dépendance : LL-10005

Objectif

Permettre à l'API de rechercher les activités d'une ville et de contrôler
l'ordre des résultats.

Contrat cible

Exemples :

GET /api/v1/activities?city=Avignon

GET /api/v1/activities?city=Avignon&sort=date

GET /api/v1/activities?sort=city,date

Les valeurs exactes de "sort" doivent être définies dans le contrat avant
implémentation.

Critères d'acceptation

- le filtre "city" fonctionne ;
- la comparaison de ville est déterministe ;
- le tri par ville fonctionne ;
- le tri par date fonctionne ;
- plusieurs critères de tri peuvent être combinés si le contrat le prévoit ;
- les filtres existants restent fonctionnels ;
- le tri est réalisé côté backend/base de données ;
- les tests couvrent les combinaisons pertinentes.

---

LL-10007 — Créer la vue liste des activités

Priorité : Haute

Dépendance : LL-10006

Objectif

Permettre de consulter facilement les activités sans passer exclusivement
par la carte.

Fonctionnement

Ajouter un bouton dans le bandeau supérieur de l'interface permettant de
basculer vers la liste.

La liste doit afficher au minimum :

Titre
Date
Ville
Adresse
Catégorie

Une activité sélectionnée depuis la liste doit permettre de consulter son
détail et/ou de la retrouver sur la carte.

Critères d'acceptation

- la liste est accessible depuis l'interface principale ;
- les activités sont récupérées depuis l'API ;
- la ville et l'adresse sont visibles ;
- la date est visible ;
- la liste respecte les filtres actifs ;
- un état "aucun résultat" est prévu ;
- un état de chargement est prévu ;
- un état d'erreur est prévu ;
- le clic sur une activité permet d'accéder à son détail.

---

LL-10008 — Ajouter les contrôles de filtre et de tri par ville

Priorité : Moyenne

Dépendance : LL-10007

Objectif

Permettre à l'utilisateur de sélectionner une ville et de contrôler
l'ordre d'affichage.

Critères d'acceptation

- la ville peut être sélectionnée ;
- les activités sont filtrées par la ville sélectionnée ;
- le tri par ville est disponible ;
- le tri par date est disponible ;
- le changement de filtre recharge les données nécessaires ;
- les contrôles sont compatibles avec les filtres de catégorie et de date
  existants ;
- le comportement est cohérent entre carte et liste.

---

LL-10009 — Valider le parcours carte / liste / détail

Priorité : Haute

Dépendance : LL-10007, LL-10008

Objectif

Valider que la nouvelle vue liste complète réellement la carte au lieu de
créer deux parcours indépendants.

Scénarios

1. ouvrir LocalLife ;
2. afficher les activités sur la carte ;
3. passer en liste ;
4. filtrer par ville ;
5. trier par date ;
6. ouvrir une activité ;
7. vérifier son adresse ;
8. revenir à la liste ;
9. revenir à la carte ;
10. vérifier que l'activité correspond toujours aux mêmes données.

Critères d'acceptation

- les données affichées dans la carte et la liste sont cohérentes ;
- l'adresse affichée correspond à celle de l'API ;
- la ville affichée correspond à celle de l'API ;
- les filtres ne produisent pas de divergence entre les vues ;
- aucune régression n'est introduite sur la recherche géographique.

---

LL-10010 — Tests et documentation du sprint

Priorité : Haute

Dépendance : LL-10009

Objectif

Garantir que la nouvelle architecture de localisation est documentée et
stable avant le Sprint Mobile.

Critères d'acceptation

- tests backend passants ;
- tests frontend passants ;
- tests d'intégration passants ;
- tests du pipeline collector passants ;
- documentation API mise à jour ;
- documentation architecture mise à jour ;
- "PROJECT_STATUS.md" mis à jour ;
- backlog mis à jour ;
- aucun ticket du sprint ne reste sans décision documentée ;
- "mvn verify" passe ;
- le build frontend passe.

---

Dépendances

LL-10001
    │
    ▼
LL-10002
    │
    ├──────────────► LL-10005 ──► LL-10006
    │                              │
    │                              ▼
    │                           LL-10007
    │                              │
    │                              ▼
    │                           LL-10008
    │                              │
    │                              ▼
    │                           LL-10009
    │                              │
    │                              ▼
    │                           LL-10010
    │
    └──────────────► LL-10003 ──► LL-10004

---

Definition of Done

Le Sprint 10 est terminé lorsque :

- les activités possèdent une localisation structurée lorsque les données
  sont disponibles ;
- l'adresse est affichable dans le frontend ;
- la ville est une donnée indépendante de l'adresse ;
- les contributions et collectors utilisent une logique cohérente de
  localisation ;
- l'API expose adresse, ville, code postal et coordonnées ;
- les activités peuvent être filtrées par ville ;
- les activités peuvent être triées par ville et par date ;
- la vue liste est fonctionnelle ;
- carte, liste et détail utilisent les mêmes données ;
- les recherches géographiques existantes fonctionnent toujours ;
- les tests passent ;
- la documentation est à jour.

---

Livrable du Sprint

Une version web de LocalLife dans laquelle un utilisateur peut :

                LOCAL LIFE
                    │
          ┌─────────┴─────────┐
          │                   │
        CARTE                LISTE
          │                   │
          │             Filtrer par ville
          │                   │
          │             Trier par date
          │                   │
          └─────────┬─────────┘
                    │
                 ACTIVITÉ
                    │
          ┌─────────┴─────────┐
          │                   │
        Ville              Adresse
          │                   │
          └─────────┬─────────┘
                    │
              Coordonnées GPS

Cette version constitue le socle de données et d'API qui sera ensuite
réutilisé par le Sprint Mobile, sans duplication de la logique métier.
