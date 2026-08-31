Sprint 9 — Bêta et corrections post-bêta

Statut : 🟡 En cours

---

Objectif

Sprint 8 clôturé avec une décision GO bêta conditionnel ("LL-8009",
voir "docs/PROJECT_STATUS.md").

Le Sprint 9 a deux objectifs successifs :

1. rendre LocalLife accessible dans un environnement bêta réel ;
2. permettre les premiers tests par un panel d'utilisateurs et corriger les
   problèmes réellement identifiés pendant ces tests.

Le sprint poursuit la méthode adoptée précédemment :

- chaque problème ou évolution technique est traité comme un ticket ;
- chaque ticket possède ses propres critères d'acceptation ;
- aucune nouvelle fonctionnalité métier n'est ajoutée sur simple hypothèse ;
- les corrections supplémentaires sont ajoutées au sprint lorsqu'elles sont
  réellement identifiées.

Important : aucune ouverture publique de la bêta ne doit avoir lieu avant
la validation du Security Gate "LL-9004".

---

Périmètre

Inclus

Mise à disposition de la bêta

- environnement bêta dédié ;
- backend et base de données bêta ;
- audit sécurité pré-exposition ;
- déploiement du frontend ;
- HTTPS ;
- configuration CORS ;
- configuration des secrets ;
- configuration des collecteurs Avignon ;
- exécution automatique des collectes ;
- validation du fonctionnement depuis Internet.

Bêta

- tests du MVP depuis l'environnement en ligne ;
- tests par un panel d'utilisateurs ;
- identification des erreurs ;
- identification des problèmes de données ;
- identification des problèmes UX ;
- collecte et qualification des retours.

Corrections

- correction des problèmes réellement identifiés ;
- tests de non-régression ;
- stabilisation de la version bêta.

---

Exclus

- nouveau domaine métier ;
- nouvelle fonctionnalité majeure ;
- refonte graphique importante ;
- optimisation d'architecture non justifiée par un problème réel ;
- fonctionnalité développée uniquement sur hypothèse ;
- évolution fonctionnelle décidée avant analyse des retours bêta.

---

Tickets

LL-9001 — Ne plus afficher les activités hors période sur les recherches publiques

Priorité : Haute

Statut : ✅ Traité — en attente de confirmation "mvn verify" par Alex

Dépendance : aucune

Constat

Les recherches publiques
("GET /api/v1/activities/nearby",
"GET /api/v1/activities/within-bounds")
ne filtrent actuellement que sur le statut ("PUBLISHED", LL-6004).

Une activité terminée ou prévue dans le futur peut donc être retournée par
les recherches publiques.

Objectif

Ne plus retourner par défaut une activité dont la période
"[start_date, end_date]" ne couvre pas la date du jour.

Le paramètre "date" existant ("LL-4005") doit continuer à fonctionner
lorsqu'il est explicitement fourni.

Points de décision

- portée : uniquement les recherches publiques
  "findNearby" / "findWithinBounds" ;
- les consultations administratives restent consultables sans restriction
  de date ;
- une activité est considérée comme en cours lorsque
  "start_date <= aujourd'hui <= end_date" ;
- lorsque "end_date" est absente, "start_date" est utilisé ;
- lorsqu'un paramètre "date" explicite est fourni, il conserve son
  comportement actuel.

Critères d'acceptation

- une activité dont "end_date < aujourd'hui" n'apparaît plus dans
  "/nearby" / "/within-bounds" sans paramètre "date" explicite ;
- une activité dont "start_date > aujourd'hui" n'apparaît plus dans les
  mêmes conditions ;
- une activité en cours continue de s'afficher ;
- une activité sans "end_date" est correctement traitée ;
- le paramètre "date" existant continue de fonctionner ;
- la consultation administrative n'est pas affectée ;
- les tests couvrent les cas terminée, future, en cours et sans
  "end_date".

---

LL-9002 — Préparer l'environnement de déploiement bêta

Priorité : Haute

Statut : ✅ Terminé

Dépendance : aucune

Objectif

Définir et préparer un environnement dédié à la bêta, distinct de
l'environnement de développement local.

Critères d'acceptation

- l'architecture de l'environnement bêta est documentée ;
- frontend, backend et base de données sont identifiés ;
- l'environnement bêta est distinct du développement local ;
- les secrets nécessaires sont identifiés ;
- aucun secret n'est ajouté au dépôt Git ;
- la procédure de déploiement est documentée.

---

LL-9003 — Déployer le backend et la base de données bêta

Priorité : Haute

Statut : ✅ Terminé — commit effectué

Dépendance : "LL-9002"

Objectif

Rendre l'API LocalLife opérationnelle dans l'environnement bêta avec sa
propre base de données.

Critères d'acceptation

- PostgreSQL/PostGIS est opérationnel ;
- les migrations du projet sont appliquées ;
- le backend démarre sans dépendance à l'environnement local ;
- l'API est accessible depuis l'environnement bêta ;
- le health check fonctionne ;
- l'authentification fonctionne ;
- aucun secret n'est exposé dans le dépôt ;
- aucun secret ou mot de passe n'apparaît dans les logs.

---

LL-9004 — Audit sécurité pré-exposition

Priorité : Bloquante

Statut : ✅ Terminé — Security Gate validé le 28/08/2026. Deux
bloquants corrigés (clé API OpenAgenda externalisée, procédure de
sauvegarde/restauration ajoutée). Cinq constats non bloquants
documentés dans `docs/DETTE_TECHNIQUE.md`, en attente d'arbitrage
d'Alex avant correction (endpoint utilisateur public exposant l'email,
messages d'exception bruts sur les 500, utilisateur PostgreSQL unique,
en-têtes de sécurité HTTP absents côté Caddy, `UnsupportedJwtException`
non capturée).

Dépendance : "LL-9003"

Objectif

Effectuer un contrôle de sécurité complet avant d'exposer LocalLife
publiquement.

"LL-9004" constitue le Security Gate du Sprint 9.

Aucun accès public destiné au panel bêta ne doit être ouvert tant que ce
ticket n'est pas validé.

Contrôles obligatoires

1. Secrets et configuration

- rechercher les secrets dans l'historique et l'état actuel du dépôt ;
- vérifier les secrets JWT ;
- vérifier les mots de passe et identifiants BDD ;
- vérifier les clés API et credentials OpenAgenda ;
- vérifier les variables d'environnement ;
- vérifier le build frontend afin qu'aucun secret backend ne soit embarqué ;
- vérifier l'absence de credentials par défaut ;
- vérifier que les secrets sont fournis uniquement par l'environnement
  sécurisé.

2. Authentification

- endpoint protégé sans JWT ;
- JWT invalide ;
- JWT expiré ;
- JWT malformé ;
- absence de token ;
- vérification de l'expiration et de la signature ;
- vérification du comportement des comptes désactivés lorsqu'applicable.

3. Autorisation

- utilisateur "USER" tentant une opération "ADMIN" ;
- utilisateur non authentifié tentant une opération protégée ;
- vérification des contrôles d'accès aux ressources ;
- vérification du bootstrap du premier "ADMIN" ;
- vérification que les endpoints administratifs ne sont pas accessibles
  sans autorisation.

4. Données sensibles

- aucune "passwordHash" dans les réponses ;
- aucune donnée sensible dans les logs ;
- aucune donnée sensible dans les erreurs ;
- aucune stack trace détaillée retournée au client ;
- vérification des DTO exposés par les endpoints publics ;
- vérification des réponses d'erreur.

5. API et validation des entrées

- paramètres invalides ;
- IDs inexistants ;
- IDs manipulés ;
- valeurs hors limites ;
- payloads invalides ;
- payloads excessivement volumineux lorsque pertinent ;
- champs inattendus ;
- méthodes HTTP non autorisées ;
- vérification correcte des réponses "400", "401", "403", "404" ;
- absence d'endpoints de debug ou de test inutiles.

6. CORS et sécurité HTTP

- CORS limité aux origines nécessaires ;
- aucune autorisation globale inutile ;
- HTTPS configuré ;
- redirection HTTP → HTTPS si applicable ;
- headers de sécurité pertinents ;
- cookies configurés correctement s'ils sont utilisés.

7. Infrastructure et réseau

- PostgreSQL/PostGIS non accessible directement depuis Internet ;
- seuls les ports nécessaires sont exposés ;
- aucun service d'administration inutilement public ;
- aucun endpoint Actuator sensible publiquement accessible ;
- comptes système et services configurés avec les privilèges minimum
  nécessaires.

8. Base de données

- utilisateur BDD avec privilèges minimaux ;
- accès réseau limité ;
- migrations contrôlées ;
- absence de données de développement sensibles ;
- sauvegarde minimale disponible ;
- procédure de restauration documentée.

9. Dépendances

- audit des dépendances backend ;
- audit des dépendances frontend ;
- vérification des vulnérabilités connues ;
- aucune vulnérabilité connue critique ou bloquante non traitée sans
  justification explicite.

10. Collecteurs

- clés et credentials non exposés ;
- données externes correctement validées ;
- erreurs de source correctement isolées ;
- un échec d'une source ne compromet pas les autres collectes ;
- aucune donnée externe ne peut contourner les contrôles métier.

Critères d'acceptation

"LL-9004" est validé uniquement si :

- aucun secret n'est exposé ;
- aucun secret backend n'est présent dans le build frontend ;
- authentification et JWT sont correctement contrôlés ;
- "USER" et "ADMIN" sont correctement séparés ;
- les endpoints protégés sont effectivement protégés ;
- aucune donnée sensible n'est exposée ;
- aucune stack trace interne n'est exposée ;
- les entrées utilisateur sont correctement validées ;
- CORS est correctement restreint ;
- HTTPS est configuré ;
- PostgreSQL/PostGIS n'est pas accessible directement depuis Internet ;
- aucun endpoint de debug dangereux n'est exposé ;
- les dépendances ne présentent pas de vulnérabilité critique/bloquante
  connue non traitée ;
- les logs ne contiennent pas de secrets ou données sensibles ;
- les collecteurs ne permettent pas de contourner les contrôles de sécurité.

Règle de blocage

Toute vulnérabilité critique ou tout problème permettant un accès non
autorisé aux données ou fonctions sensibles bloque la validation de
"LL-9004".

---

LL-9005 — Déployer le frontend et rendre LocalLife accessible en ligne

Priorité : Haute

Statut : ✅ Traité côté code (aucun changement nécessaire, vérifié) —
en attente de la checklist de vérification en conditions réelles par
Alex, voir `docs/02_Architecture/BETA_DEPLOYMENT.md`

Dépendance : "LL-9004"

Objectif

Permettre à un utilisateur extérieur à l'environnement de développement
d'accéder à LocalLife.

Critères d'acceptation

- le frontend est accessible depuis Internet ;
- l'accès utilise HTTPS ;
- le frontend utilise l'API bêta ;
- aucune URL "localhost" ne subsiste ;
- l'inscription fonctionne ;
- la connexion fonctionne ;
- la carte fonctionne ;
- les recherches fonctionnent ;
- le détail d'une activité fonctionne ;
- la contribution fonctionne ;
- aucun secret backend n'est présent dans le build frontend.

---

LL-9006 — Finaliser la sécurisation de l'exposition web

Priorité : Haute

Statut : ✅ Traité côté code — en attente de confirmation par Alex sur
les points nécessitant un accès réel (certificat HTTPS externe,
requêtes non sécurisées)

Dépendance : "LL-9005"

Objectif

Vérifier la configuration de sécurité spécifique à l'exposition réelle du
frontend et du backend sur Internet.

Critères d'acceptation

- HTTPS fonctionne depuis un navigateur externe ;
- les certificats sont valides ;
- CORS fonctionne uniquement depuis les origines autorisées ;
- les requêtes non sécurisées sont correctement gérées ;
- les endpoints administratifs restent protégés ;
- les informations techniques inutiles ne sont pas exposées ;
- le comportement des erreurs HTTP est conforme aux contrôles du
  "LL-9004".

---

LL-9007 — Activer et vérifier l'alimentation automatique Avignon en bêta

Priorité : Haute

Dépendance : "LL-9003"

Objectif

Vérifier que les agendas Avignon configurés au Sprint 8 alimentent
automatiquement l'environnement bêta.

Critères d'acceptation

- les agendas Avignon validés au Sprint 8 sont configurés ;
- plusieurs agendas sont effectivement collectés ;
- le mécanisme d'exécution automatique est actif ;
- les activités collectées sont persistées ;
- les doublons sont gérés conformément au comportement validé au Sprint 8 ;
- une erreur d'une source n'empêche pas les autres sources d'être collectées ;
- les résultats des collectes sont observables ;
- une activité collectée peut être retrouvée via l'API.

---

LL-9008 — Valider le parcours complet depuis Internet

Priorité : Haute

Dépendance : "LL-9005", "LL-9006", "LL-9007"

Objectif

Valider LocalLife de bout en bout depuis un accès Internet réel.

Parcours

Agenda Avignon
      ↓
Collector
      ↓
Base de données
      ↓
API
      ↓
Frontend
      ↓
Carte

Critères d'acceptation

- une collecte réelle est exécutée ;
- les activités collectées sont présentes en base ;
- les activités sont retournées par l'API ;
- les activités sont visibles sur la carte ;
- la recherche permet de retrouver les activités ;
- le détail d'une activité fonctionne ;
- l'inscription fonctionne ;
- la connexion fonctionne ;
- la contribution fonctionne ;
- les opérations administratives fonctionnent avec un compte autorisé ;
- aucun problème bloquant n'est identifié.

---

LL-9009 — Préparer et lancer le panel bêta

Priorité : Haute

Dépendance : "LL-9008"

Objectif

Permettre à un premier panel d'utilisateurs réels de tester LocalLife
dans des conditions normales.

Préparation

- sélectionner un petit panel de testeurs ;
- transmettre l'URL bêta ;
- définir les consignes générales ;
- préparer les missions de test ;
- préparer le moyen de remontée des problèmes ;
- définir les informations à recueillir.

Principe

Les utilisateurs ne doivent pas être guidés bouton par bouton.

Les missions doivent permettre d'observer leur comportement réel.

Exemples de missions

- trouver une activité intéressante à Avignon ;
- explorer la carte ;
- rechercher une activité ;
- consulter le détail d'une activité ;
- créer une activité ;
- revenir ultérieurement vérifier les nouvelles activités.

Critères d'acceptation

- le panel est constitué ;
- chaque testeur dispose d'un accès à la bêta ;
- les consignes sont disponibles ;
- les missions sont définies ;
- le mécanisme de remontée des problèmes fonctionne ;
- les premiers tests utilisateurs ont été réalisés.

---

LL-9010 — Suivre et qualifier les problèmes bêta

Priorité : Haute

Dépendance : "LL-9009"

Objectif

Centraliser les problèmes et retours identifiés pendant la bêta afin de
permettre leur traitement méthodique.

Chaque problème doit être qualifié

- description du constat ;
- contexte ;
- étapes de reproduction lorsque pertinentes ;
- comportement attendu ;
- comportement observé ;
- impact ;
- priorité ;
- ticket associé lorsqu'une correction est nécessaire.

Classification

Bloquant

Empêche l'utilisation d'une fonctionnalité essentielle ou de l'application.

Critique

Dégrade fortement un parcours essentiel.

Important

Problème réel avec un impact significatif mais non bloquant.

Mineur

Problème de faible impact.

Amélioration

Suggestion ou besoin fonctionnel ne constituant pas un bug.

Critères d'acceptation

- tous les problèmes identifiés sont enregistrés ;
- chaque problème possède une priorité ;
- les doublons sont regroupés ;
- les problèmes nécessitant une correction possèdent un ticket ;
- les suggestions d'évolution sont placées dans le backlog ;
- aucune amélioration fonctionnelle n'est développée sans décision
  explicite.

---

Tickets correctifs supplémentaires

Les tickets "LL-9011" et suivants sont créés uniquement lorsqu'un problème
réel est identifié pendant la bêta.

Ils suivent la structure :

LL-90XX — <problème>

Priorité :
Statut :
Dépendance :

Constat

Objectif

Critères d'acceptation

Tests nécessaires

Ils ne doivent pas être pré-remplis avec des problèmes hypothétiques.

---

Dépendances

LL-9002
   ↓
LL-9003
   ↓
LL-9004  ← SECURITY GATE
   ↓
LL-9005
   ↓
LL-9006
   ↓
LL-9008 ← LL-9007
   ↓
LL-9009
   ↓
LL-9010
   ↓
LL-9011+ corrections réelles

"LL-9001" reste indépendant de cette chaîne.

---

Definition of Done

Le Sprint 9 est terminé lorsque :

- LocalLife est accessible sur Internet ;
- l'environnement bêta est distinct du développement ;
- frontend et backend sont opérationnels ;
- PostgreSQL/PostGIS est opérationnel ;
- le Security Gate "LL-9004" est validé ;
- HTTPS est actif ;
- les secrets sont correctement externalisés ;
- l'authentification fonctionne ;
- les autorisations fonctionnent ;
- les collecteurs Avignon fonctionnent automatiquement ;
- plusieurs agendas Avignon alimentent la base ;
- le parcours "collector → base → API → carte" est validé ;
- le panel bêta a pu utiliser l'application ;
- les problèmes rencontrés ont été qualifiés ;
- les problèmes bloquants et critiques retenus ont été corrigés ;
- les corrections ont été testées ;
- aucun problème bloquant connu ne subsiste.

---

Fin du Sprint

La clôture du Sprint 9 doit donner lieu à un bilan de la bêta.

Deux situations sont possibles.

Bêta satisfaisante

Les problèmes restants sont mineurs et aucune évolution majeure n'est
nécessaire.

→ Préparation de la suite vers une V1.

Bêta nécessitant des évolutions

Des problèmes importants ou des besoins fonctionnels significatifs sont
identifiés.

→ Ils sont priorisés dans le backlog et servent de base au sprint suivant.

Le sprint suivant ne doit pas être défini avant l'analyse des résultats
réels de la bêta.