# Sprint 7 — Validation du MVP

**Statut :** À faire

---

# Objectif

Passer du MVP techniquement construit à un MVP **vérifiable en conditions réelles**.

À la fin du sprint, l’équipe doit pouvoir démontrer un parcours complet :

**donnée réelle → import → modération → carte publique → consultation / contribution**, avec un environnement reproductible et une documentation suffisamment claire pour une première validation utilisateur.

Le sprint ne doit pas ajouter de nouvelle fonctionnalité produit majeure. Il sert à vérifier que ce qui existe déjà fonctionne réellement ensemble.

---

# Périmètre

## Inclus

- déclenchement contrôlé du pipeline d’import OpenAgenda ;
- vérification de bout en bout des données importées ;
- vérification de la modération et de la visibilité publique ;
- vérification du parcours contribution / authentification ;
- vérification de la carte et de la recherche géographique avec des données réelles ;
- vérification du parcours Food Truck déjà livré ;
- préparation d’un environnement de démonstration reproductible ;
- tests et documentation de validation du MVP.

## Exclus

- deuxième collecteur ;
- nouveau type de lieu ;
- refonte graphique importante ;
- application mobile ;
- système de recommandation ;
- notifications ;
- paiement / marketplace ;
- back-office complet ;
- moteur de recherche avancé ;
- optimisation prématurée de l’architecture.

---

# Tickets

## LL-7001 — Définir le protocole de validation du MVP

**Priorité : Haute**

### Objectif

Définir les scénarios qui permettent de décider objectivement si le MVP est prêt pour une première bêta.

### Scénarios minimum

1. démarrer l’application ;
2. importer une source réelle ;
3. vérifier une activité importée ;
4. la publier en tant qu’administrateur ;
5. vérifier sa visibilité sur la carte ;
6. rechercher par zone/catégorie/date ;
7. créer une activité en tant qu’utilisateur ;
8. vérifier son statut `PENDING` ;
9. publier ou rejeter la contribution ;
10. créer et afficher un Food Truck.

### Critères d’acceptation

- protocole documenté ;
- critères succès/échec définis ;
- aucun scénario ne dépend d’une fonctionnalité hors MVP.

---

## LL-7002 — Ajouter un déclencheur contrôlé du pipeline d’import

**Priorité : Haute**

**Dépendance :** LL-7001

### Objectif

Permettre d’exécuter réellement le pipeline OpenAgenda dans l’application, sans introduire de scheduler complexe.

### Décision MVP

Utiliser un déclenchement manuel protégé par le rôle `ADMIN` (endpoint d’administration ou mécanisme équivalent documenté). La planification automatique est hors périmètre.

### Critères d’acceptation

- un administrateur peut déclencher un import ;
- un utilisateur non administrateur ne peut pas le déclencher ;
- le résultat de l’import est journalisé ;
- le pipeline existant `Collector → Normalisation → Validation → Persistance` est réutilisé sans duplication.

---

## LL-7003 — Valider le parcours de données réelles de bout en bout

**Priorité : Haute**

**Dépendance :** LL-7002

### Objectif

Vérifier qu’une donnée réellement collectée traverse correctement tout le système jusqu’à la carte.

### Critères d’acceptation

- donnée OpenAgenda collectée ;
- activité persistée ;
- source identifiable ;
- validation appliquée ;
- activité `PUBLISHED` visible dans la recherche publique ;
- activité `PENDING` ou `REJECTED` non visible ;
- consultation par identifiant fonctionnelle.

---

## LL-7004 — Valider la recherche et la carte avec les données réelles

**Priorité : Haute**

**Dépendance :** LL-7003

### Objectif

S’assurer que la fonctionnalité centrale de LocalLife reste cohérente avec les données réellement importées.

### À vérifier

- recherche autour d’une position ;
- recherche par zone de carte ;
- filtre catégorie ;
- filtre date ;
- géolocalisation utilisateur ;
- affichage des activités importées ;
- affichage des Food Trucks.

### Critères d’acceptation

Les scénarios définis dans LL-7001 passent sans correction fonctionnelle majeure.

---

## LL-7005 — Valider le parcours utilisateur contribution / authentification

**Priorité : Haute**

**Dépendance :** LL-7001

### Objectif

Vérifier que le parcours utilisateur principal fonctionne de bout en bout.

### Critères d’acceptation

- inscription ;
- connexion ;
- maintien de l’authentification ;
- création d’une activité ;
- activité créée en `PENDING` ;
- retour utilisateur compréhensible en cas d’erreur ;
- déconnexion.

---

## LL-7006 — Validation du parcours Food Truck

**Priorité : Moyenne**

**Dépendance :** LL-7001

### Objectif

Vérifier le premier jalon Food Truck livré au Sprint 6 sans élargir son périmètre.

### Critères d’acceptation

- création fonctionnelle ;
- position correctement affichée ;
- distinction visuelle conservée ;
- présence sur la carte cohérente avec les activités ;
- aucune nouvelle logique de tournée ou de commande.

---

## LL-7007 — Corriger uniquement les blocages de validation

**Priorité : Haute**

**Dépendance :** LL-7003, LL-7004, LL-7005, LL-7006

### Objectif

Corriger les problèmes qui empêchent les scénarios de validation du MVP de passer.

### Règle

Aucune amélioration esthétique ou fonctionnalité nouvelle ne doit entrer dans ce ticket.

### Critères d’acceptation

- chaque correction est liée à un scénario LL-7001 en échec ;
- tests ajoutés ou adaptés lorsque nécessaire ;
- aucune régression du périmètre existant.

---

## LL-7008 — Préparer l’environnement de démonstration

**Priorité : Moyenne**

**Dépendance :** LL-7007

### Objectif

Permettre de reproduire facilement une démonstration du MVP avec une configuration connue.

### À documenter

- démarrage backend/frontend ;
- base de données ;
- configuration nécessaire ;
- compte administrateur de démonstration si le mécanisme existe ;
- déclenchement d’un import ;
- vérification de la carte.

### Critères d’acceptation

Une personne connaissant le dépôt mais n’ayant pas suivi le développement peut reproduire le parcours de démonstration à partir de la documentation.

---

## LL-7009 — Documentation et décision de sortie du MVP

**Priorité : Haute**

**Dépendance :** tous les tickets précédents

### Objectif

Documenter les résultats du sprint et décider si LocalLife peut passer à une première bêta utilisateur.

### Mettre à jour

- `PROJECT_STATUS.md` ;
- `ROADMAP.md` ;
- `BACKLOG.md` ;
- `NEXT_TASK.md` ;
- `CHANGELOG.md` ;
- documentation de validation du MVP si nécessaire.

### Critères d’acceptation

Le document de fin de sprint doit conclure explicitement par l’un des deux états :

- **MVP validé → préparation de la bêta** ;
- **MVP non validé → sprint de correction ciblé avant toute évolution produit.**

---

# Dépendances

```text
LL-7001
   ├── LL-7002 → LL-7003 → LL-7004
   ├── LL-7005
   └── LL-7006
              ↓
           LL-7007
              ↓
           LL-7008
              ↓
           LL-7009
```

---

# Règles du sprint

- Aucun nouveau domaine métier majeur.
- Aucun deuxième collecteur.
- Aucun scheduler complexe.
- Aucun changement d’architecture majeur.
- Toute correction doit être reliée à un scénario de validation.
- Une tâche n’est terminée qu’avec une preuve dans le code, les tests ou la validation documentée.
- La réussite du sprint ne se mesure pas au nombre de tickets mais à la capacité de démontrer le MVP de bout en bout.

---

# Definition of Done

Le Sprint 7 est terminé lorsque :

- le protocole de validation est défini ;
- un import réel peut être déclenché ;
- les données importées traversent correctement le système ;
- la modération fonctionne sur le parcours testé ;
- la carte et la recherche fonctionnent avec des données réelles ;
- la contribution utilisateur fonctionne ;
- le Food Truck déjà livré est vérifié ;
- les blocages de validation sont corrigés ;
- une démonstration reproductible est documentée ;
- une décision explicite sur la validation du MVP est prise.

---

# Livrable du Sprint

> Un MVP démontrable et vérifié de bout en bout, permettant de décider objectivement si LocalLife est prêt pour une première validation auprès d’utilisateurs réels.
