Objectif

Mettre en place une base de développement robuste et reproductible.

Aucune fonctionnalité métier.

Aucune API métier.

Aucune interface utilisateur.

Livrable :

Le projet compile, démarre en local avec Docker et est prêt à accueillir les développements du Sprint 1.

Tickets
LL-0001 — Initialiser le projet Spring Boot

Priorité : Haute

Objectif :

créer le projet Spring Boot ;
configurer Maven ;
Java 21 ;
structure modulaire.

Critères d'acceptation :

projet compile ;
application démarre ;
aucun warning majeur.
LL-0002 — Créer l'arborescence backend

Créer les packages principaux :

config
common
activity
place
category
source
user
contribution
admin

Aucun code métier.

LL-0003 — Configurer les profils Spring

Créer :

local
dev
test
prod

Critères :

lancement possible avec chaque profil.
LL-0004 — Docker Compose

Créer :

PostgreSQL
PostGIS

Critères :

lancement en une commande.
LL-0005 — Configuration PostgreSQL

Configurer :

datasource
connexion
paramètres Spring
LL-0006 — Installer Flyway

Créer :

première migration vide.

Valider :

migration exécutée automatiquement.

LL-0007 — Actuator

Ajouter :

Health
Info

Configurer correctement.

LL-0008 — OpenAPI

Installer Swagger.

Vérifier :

documentation accessible.

LL-0009 — Qualité de code

Configurer :

Spotless
Checkstyle

Le build doit échouer en cas de non-conformité.

LL-0010 — Logging

Configurer :

Logback
niveaux de logs
format uniforme.
LL-0011 — Gestion des erreurs

Créer :

exception globale
réponse JSON standardisée

Sans logique métier.

LL-0012 — Docker Backend

Créer le Dockerfile.

Valider :

construction de l'image.

LL-0013 — README Backend

Expliquer :

démarrage ;
profils ;
Docker ;
commandes Maven.
LL-0014 — GitHub Actions

Créer :

Pipeline :

Build
Tests
LL-0015 — Vérification finale

Contrôle :

build OK ;
Docker OK ;
PostgreSQL OK ;
Flyway OK ;
Swagger OK ;
Actuator OK.
Dépendances

LL-0001

↓

LL-0002

↓

LL-0003

↓

LL-0004

↓

LL-0005

↓

LL-0006

↓

LL-0007

↓

LL-0008

↓

LL-0009

↓

LL-0010

↓

LL-0011

↓

LL-0012

↓

LL-0013

↓

LL-0014

↓

LL-0015
