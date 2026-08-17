# Protocole de validation du MVP

**Ticket :** LL-7001 — Définir le protocole de validation du MVP
**Sprint :** 7 — Validation du MVP (`docs/05_Sprints/SPRINT_7.md`)
**Dépendance :** aucune (premier ticket du sprint).

Ticket de conception/documentation uniquement, comme `SOURCE_CONTRACT.md`
(LL-5001) ou `FOOD_TRUCK_CONTRACT.md` (LL-6008) : aucun code, aucune
migration, aucun endpoint. Ce document définit **ce qu'il faut vérifier**
pour décider si LocalLife est prêt pour une première bêta ; l'exécution
réelle des scénarios (avec de vraies données, un vrai déclenchement
d'import, etc.) est hors périmètre de ce ticket et relève des tickets
suivants du sprint (LL-7002 à LL-7006), qui référencent chacun les
scénarios ci-dessous.

---

## 1. Objectif

Décider **objectivement**, à l'issue du Sprint 7, si le MVP construit
lors des Sprints 0 à 6 fonctionne réellement de bout en bout et peut être
proposé à une première bêta utilisateur — ou si un sprint de correction
ciblé est nécessaire avant toute évolution produit (décision prise en
LL-7009).

Ce protocole ne mesure pas la qualité du code ni la couverture de tests
unitaires (déjà couverte par `NonRegressionIntegrationTest`, LL-6010) :
il vérifie le **parcours réel**, avec de vraies données, tel qu'un
utilisateur ou un administrateur le vivrait.

## 2. Périmètre

### 2.1 Inclus

Les dix scénarios minimum listés dans `SPRINT_7.md` (section 3
ci-dessous), organisés en six familles :

1. démarrage de l'application ;
2. import d'une source réelle (OpenAgenda) ;
3. modération (vérification, publication, rejet) ;
4. visibilité publique et recherche géographique ;
5. contribution / authentification utilisateur ;
6. Food Truck.

### 2.2 Exclu

Conformément à la section « Exclus » de `SPRINT_7.md`, aucun scénario de
ce protocole ne couvre, et aucune correction déclenchée par un scénario
en échec (LL-7007) ne doit introduire :

* un deuxième collecteur ;
* un nouveau type de lieu ;
* une refonte graphique ;
* une application mobile ;
* un système de recommandation ;
* des notifications ;
* du paiement / une marketplace ;
* un back-office complet ;
* un moteur de recherche avancé ;
* une optimisation prématurée de l'architecture ;
* un scheduler automatique pour l'import (le déclenchement reste manuel,
  décision déjà actée dans `SPRINT_7.md` pour LL-7002).

Ce protocole ne couvre pas non plus les points déjà explicitement
signalés comme hors périmètre par des tickets antérieurs et non repris
par `SPRINT_7.md` : filtrage `status` par défaut sur `GET
/api/v1/activities` (liste complète, sans rapport avec la recherche
publique — voir note LL-6004 dans `PROJECT_STATUS.md`), consolidation
des deux fichiers `ROADMAP.md` (`DETTE_TECHNIQUE.md`).

### 2.3 Vérification d'indépendance vis-à-vis du hors-MVP

Critère d'acceptation explicite de LL-7001 : *aucun scénario ne dépend
d'une fonctionnalité hors MVP*. Vérifié ci-dessous scénario par
scénario — chaque scénario ne s'appuie que sur des endpoints et
fonctionnalités déjà livrés (Sprints 0 à 6) et listés dans le MVP retenu
(`PROJECT_STATUS.md`, section « MVP retenu » : carte interactive,
géolocalisation, recherche par zone/catégorie, consultation, création,
validation des contributions, import, food trucks, administration
simple).

## 3. Environnement de test

* Base PostgreSQL + PostGIS réelle (pas la sandbox de développement —
  aucun des tickets précédents n'a pu exécuter de requête réelle,
  Maven Central étant hors des domaines réseau autorisés en sandbox).
  Ce protocole doit donc être exécuté par Alex, en local ou dans
  l'environnement préparé en LL-7008.
* Backend démarré (`mvn spring-boot:run` ou équivalent), migrations
  Flyway appliquées jusqu'à `V12` inclus.
* Frontend démarré (`npm run dev` dans `frontend/`).
* Variables `OPENAGENDA_API_KEY` / `OPENAGENDA_AGENDA_UID` renseignées
  (prérequis déjà signalé en LL-5006 pour tout import réel).
* Aucun compte `ADMIN` n'existe par un mécanisme d'inscription publique
  (rappel LL-6005) : un compte administrateur doit être injecté
  directement en base pour les scénarios 4, 9 et 10 — un mécanisme de
  compte de démonstration sera documenté en LL-7008 si besoin.

## 4. Scénarios

Chaque scénario ci-dessous suit le même format : **objectif**, **étapes**,
**critère de succès**, **critère d'échec**, **dépend de (MVP)**.

### Scénario 1 — Démarrer l'application

* **Objectif** : vérifier que le backend et le frontend démarrent sans
  intervention manuelle au-delà de la configuration documentée.
* **Étapes** : appliquer les migrations Flyway ; démarrer le backend ;
  démarrer le frontend ; ouvrir la carte dans un navigateur.
* **Succès** : le backend répond sur `/actuator/health` (`UP`) ; la carte
  s'affiche dans le frontend sans erreur bloquante en console.
* **Échec** : erreur de démarrage (contexte Spring, migration en échec)
  ou carte non affichée.
* **Dépend de** : socle technique (Sprint 0), carte (Sprint 1) — MVP.

### Scénario 2 — Importer une source réelle

* **Objectif** : vérifier qu'un import réel contre l'API OpenAgenda
  fonctionne, une fois le déclencheur ajouté par LL-7002.
* **Étapes** : déclencher un import via le mécanisme livré par LL-7002
  (rôle `ADMIN` requis) ; consulter le résultat retourné/journalisé.
* **Succès** : le résultat d'import (`ImportResult`, LL-5009) rapporte
  au moins une activité créée ou mise à jour, sans erreur (`errors = 0`
  ou justifiées) ; les logs applicatifs confirment le déroulé.
* **Échec** : `CollectorException` non résolue (configuration manquante),
  `fetched = 0` de façon inattendue, ou aucun résultat consultable.
* **Dépend de** : pipeline d'import (Sprint 5), déclencheur (LL-7002,
  Sprint 7) — MVP (« import de données externes »).

### Scénario 3 — Vérifier une activité importée

* **Objectif** : vérifier qu'une activité importée est correctement
  persistée et consultable individuellement.
* **Étapes** : à partir du résultat du scénario 2, récupérer l'`id`
  d'une activité créée ; appeler `GET /api/v1/activities/{id}`.
* **Succès** : la réponse contient un `title` non vide, une `sourceId`
  différente de la source `MANUAL`, un `status = PENDING` ou
  `PUBLISHED` selon le comportement documenté (`PUBLISHED` par défaut
  pour un import, décision LL-5005).
* **Échec** : `404`, champs manquants/incohérents, `sourceUrl` perdu
  (régression du problème résolu en LL-6002).
* **Dépend de** : consultation par id (Sprint 1), normalisation
  (Sprint 5) — MVP.

### Scénario 4 — Publier une activité en tant qu'administrateur

* **Objectif** : vérifier le parcours de modération complet.
* **Étapes** : se connecter avec un compte `ADMIN` (JWT) ; lister les
  activités `PENDING` via `GET /api/v1/admin/activities?status=PENDING` ;
  publier une activité via `PATCH
  /api/v1/admin/activities/{id}/publish`.
* **Succès** : la liste retourne l'activité attendue ; la publication
  renvoie `200` et un `status = PUBLISHED` ; une tentative avec un
  compte non-`ADMIN` renvoie `403`.
* **Échec** : `401`/`403` avec un compte `ADMIN` valide, transition non
  appliquée, ou activité déjà visible publiquement avant publication
  (régression de LL-6004).
* **Dépend de** : modération (Sprint 6) — MVP (« validation des
  contributions », « administration simple »).

### Scénario 5 — Vérifier la visibilité sur la carte publique

* **Objectif** : vérifier qu'une activité publiée devient visible sur la
  carte publique, et qu'une activité non publiée reste invisible.
* **Étapes** : appeler `GET /api/v1/activities/nearby` (ou
  `/within-bounds`) autour de la position de l'activité publiée au
  scénario 4 ; répéter pour une activité restée `PENDING`/`REJECTED`.
* **Succès** : l'activité `PUBLISHED` apparaît dans la réponse ;
  l'activité `PENDING`/`REJECTED` n'y apparaît pas ; la carte frontend
  affiche un marqueur correspondant.
* **Échec** : activité publiée absente, ou activité non publiée
  visible (régression directe de LL-6004, déjà couverte par
  `NonRegressionIntegrationTest` mais revérifiée ici avec des données
  réellement importées plutôt que des données de test).
* **Dépend de** : recherche géographique (Sprint 4), visibilité
  publique (Sprint 6) — MVP.

### Scénario 6 — Rechercher par zone/catégorie/date

* **Objectif** : vérifier que les filtres de recherche existants restent
  cohérents avec des données réellement importées.
* **Étapes** : appeler `GET /api/v1/activities/nearby` avec `category`
  renseigné (valeur présente parmi les activités importées) ; répéter
  avec `date` ; répéter avec `GET /api/v1/activities/within-bounds`
  pour une zone contenant au moins une activité.
* **Succès** : chaque appel renvoie uniquement les activités
  correspondant au filtre demandé, sans erreur `400`/`500` inattendue.
* **Échec** : filtre ignoré, résultats incohérents, erreur serveur.
* **Dépend de** : filtres catégorie/date (Sprint 4) — MVP (« recherche
  par zone », « recherche par catégorie »).

### Scénario 7 — Créer une activité en tant qu'utilisateur

* **Objectif** : vérifier le parcours de contribution complet, incluant
  l'authentification.
* **Étapes** : s'inscrire via `POST /api/v1/auth/register` ; se
  connecter via `POST /api/v1/auth/login` ; créer une activité via
  `POST /api/v1/activities` avec le JWT obtenu.
* **Succès** : inscription et connexion renvoient `200`/`201` avec un
  JWT exploitable ; la création renvoie `201` avec l'activité créée.
* **Échec** : `401` avec un JWT valide, ou création réussie sans
  authentification (régression de la protection ajoutée en LL-3008).
* **Dépend de** : authentification (Sprint 3), contribution (Sprint 2)
  — MVP.

### Scénario 8 — Vérifier le statut `PENDING` de la contribution

* **Objectif** : vérifier qu'une activité créée manuellement n'est pas
  visible publiquement avant modération.
* **Étapes** : à partir de l'activité créée au scénario 7, vérifier son
  `status` via `GET /api/v1/activities/{id}` ; vérifier son absence de
  `GET /api/v1/activities/nearby` autour de sa position.
* **Succès** : `status = PENDING` ; absente de la recherche publique.
* **Échec** : `status` différent de `PENDING`, ou visible publiquement
  avant modération.
* **Dépend de** : statuts de modération (Sprint 6) — MVP.

### Scénario 9 — Publier ou rejeter la contribution

* **Objectif** : vérifier que le cycle de modération se termine
  correctement, dans les deux issues possibles.
* **Étapes** : avec un compte `ADMIN`, publier l'activité du scénario 7
  via `PATCH .../publish` ; créer une seconde contribution utilisateur et
  la rejeter via `PATCH .../reject`.
* **Succès** : la première devient visible publiquement (revérifier le
  scénario 5 sur ce cas précis) ; la seconde passe à `status =
  REJECTED` et reste absente de toute recherche publique.
* **Échec** : transition refusée, statut incorrect, activité rejetée
  visible publiquement.
* **Dépend de** : modération (Sprint 6) — MVP.

### Scénario 10 — Créer et afficher un Food Truck

* **Objectif** : vérifier le premier jalon Food Truck (Sprint 6) sans
  élargir son périmètre, conformément à LL-7006.
* **Étapes** : créer un food truck via `POST /api/v1/foodtrucks`
  (authentifié) ; vérifier sa présence via `GET /api/v1/foodtrucks` ;
  vérifier son affichage sur la carte frontend avec une icône
  visuellement distincte d'une activité.
* **Succès** : le food truck est immédiatement visible (statut
  `PUBLISHED` par défaut, décision LL-6009) ; distinction visuelle
  présente sur la carte ; aucune tentative de modération/tournée n'est
  requise (hors périmètre).
* **Échec** : food truck invisible, aucune distinction visuelle,
  `401` alors que le compte est authentifié.
* **Dépend de** : Food Truck (Sprint 6) — MVP (« gestion des food
  trucks »).

## 5. Critères de succès / échec globaux

* **Scénario individuel réussi** : le critère de succès défini section 4
  est atteint, sans contournement ni correction manuelle en base pendant
  l'exécution.
* **Scénario individuel en échec** : le critère d'échec est observé, ou
  le critère de succès n'est pas atteignable tel quel. Chaque échec doit
  être documenté (scénario concerné, comportement observé, comportement
  attendu) pour alimenter LL-7007 — aucune correction ne doit être
  entreprise en dehors d'un échec explicitement rattaché à un scénario
  de ce protocole (règle du sprint, `SPRINT_7.md`).
* **Protocole global réussi (« MVP validé »)** : les dix scénarios
  passent, ou ne passent qu'après des corrections strictement limitées
  aux blocages identifiés (LL-7007), sans introduction de fonctionnalité
  hors périmètre (section 2.2).
* **Protocole global en échec (« MVP non validé »)** : au moins un
  scénario reste en échec après LL-7007, ou une correction nécessaire
  dépasserait le périmètre autorisé par les règles du sprint (auquel
  cas la correction est reportée à un sprint dédié plutôt
  qu'improvisée). La décision explicite (validé / non validé) est prise
  en LL-7009, sur la base des résultats consignés ici.

## 6. Traçabilité

Les résultats d'exécution de ce protocole (par Alex, hors sandbox) seront
consignés dans `PROJECT_STATUS.md` au fil des tickets LL-7003 à LL-7006,
puis synthétisés dans le document de fin de sprint (LL-7009), conformément
à la règle générale du projet (`AI_RULES.md`, section 15) : un ticket
n'est terminé qu'avec une preuve dans le code, les tests, ou — pour ce
sprint spécifiquement — la validation documentée ici.
