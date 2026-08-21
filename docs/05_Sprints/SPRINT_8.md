# Sprint 8 — Préparation de la bêta

**Statut :** ⏳ À faire

---

# Objectif

Préparer une première bêta contrôlée à partir du MVP validé au Sprint 7.

Le sprint ne cherche pas à enrichir le produit. Il doit rendre la version
actuelle suffisamment reproductible, observable, documentée et sûre pour
être confiée à un petit nombre d'utilisateurs réels.

---

# Périmètre

## Inclus

- rejouer le parcours MVP après les corrections du Sprint 7 ;
- figer une baseline fonctionnelle de référence ;
- sécuriser le bootstrap opérationnel d'un premier administrateur ;
- améliorer l'observabilité des erreurs serveur importantes ;
- traiter les dettes techniques pertinentes pour une bêta ;
- vérifier les dépendances et la qualité de build ;
- consolider la documentation de démarrage et de démonstration ;
- préparer une checklist de bêta et une décision de go/no-go.

## Exclus

- nouveau domaine métier ;
- deuxième collecteur ;
- notifications ;
- recommandations ;
- paiement / marketplace ;
- application mobile ;
- back-office complet ;
- refonte graphique importante ;
- recherche avancée ;
- scheduler d'import automatique ;
- optimisation d'architecture non justifiée par un problème réel.

---

# Tickets

## LL-8001 — Rejouer le parcours MVP après les corrections et figer la baseline

**Priorité : Haute**

**Dépendance :** Sprint 7 / LL-7007, LL-7009

### Objectif

Vérifier que les corrections du Sprint 7 n'ont pas introduit de régression et
établir une baseline fonctionnelle de référence avant l'ouverture de la bêta.

### À vérifier

- démarrage backend/frontend ;
- import OpenAgenda ;
- consultation d'une activité importée ;
- recherche par rayon et bounding box ;
- filtre catégorie/date ;
- géolocalisation et recentrage de la carte ;
- contribution `PENDING` ;
- publication/rejet par `ADMIN` ;
- affichage Food Truck ;
- visibilité publique uniquement des activités publiées.

### Critères d'acceptation

- le protocole MVP est rejoué après les corrections ;
- aucune régression bloquante n'est constatée ;
- la version de référence et les commandes de validation sont documentées ;
- tout écart restant est explicitement classé en dette ou en ticket futur.

---

## LL-8002 — Sécuriser le bootstrap du premier compte administrateur

**Priorité : Haute**

**Dépendance :** LL-8001

### Objectif

Supprimer le contournement SQL manuel actuellement nécessaire pour obtenir le
premier compte `ADMIN`, ou documenter une procédure opérationnelle équivalente
et sûre si un bootstrap applicatif n'est pas retenu.

### Contraintes

- aucun compte administrateur par défaut avec mot de passe connu ;
- aucune élévation de privilège depuis un compte `USER` ;
- ne pas modifier le modèle d'autorisation existant sans nécessité ;
- la solution doit être utilisable sur une base neuve de bêta.

### Critères d'acceptation

- une procédure reproductible permet d'initialiser le premier `ADMIN` ;
- aucun secret réel n'est committé ;
- les tests de contrôle d'accès existants restent verts ;
- le README décrit la procédure retenue.

---

## LL-8003 — Améliorer la journalisation des erreurs serveur bloquantes

**Priorité : Haute**

**Dépendance :** LL-8001

### Objectif

Rendre observables les exceptions serveur qui provoquent une réponse `500`,
notamment celles rencontrées pendant la validation du pipeline d'import.

### Critères d'acceptation

- les exceptions inattendues gérées par `GlobalExceptionHandler` sont
  journalisées avec un niveau adapté ;
- les logs ne contiennent ni mot de passe, ni JWT, ni donnée sensible inutile ;
- la réponse HTTP existante reste compatible avec les clients ;
- un test vérifie au minimum que le handler conserve son contrat HTTP.

---

## LL-8004 — Traiter la dette technique pertinente pour une bêta

**Priorité : Haute**

**Dépendance :** LL-8001

### Objectif

Réduire uniquement les dettes qui présentent un risque concret pour une bêta
et éviter une campagne de refactoring générale.

### À examiner en priorité

- vulnérabilité transitive `nanoid` signalée dans `frontend/package-lock.json` ;
- défaut de formatage résiduel de `ActivityController` ;
- duplication documentaire des deux `ROADMAP.md` ;
- toute nouvelle dette découverte pendant LL-8001.

### Critères d'acceptation

Chaque dette examinée reçoit l'un des états suivants :

- corrigée avec preuve ;
- explicitement acceptée comme non bloquante pour la bêta, avec justification ;
- transformée en ticket futur clairement borné.

Aucun refactoring hors de cette liste n'est introduit par ce ticket.

---

## LL-8005 — Consolider la documentation et préparer la checklist de bêta

**Priorité : Moyenne**

**Dépendance :** LL-8002, LL-8003, LL-8004

### Objectif

Faire de la documentation actuelle une procédure unique et reproductible pour
installer, démarrer, vérifier et démontrer LocalLife.

### À mettre à jour

- `README.md` ;
- `backend/README.md` ;
- `frontend/README.md` ;
- `docs/PROJECT_STATUS.md` ;
- `docs/04_Project/ROADMAP.md` ;
- `docs/01_Product/BACKLOG.md` ;
- `docs/NEXT_TASK.md` ;
- `docs/DETTE_TECHNIQUE.md` ;
- `CHANGELOG.md`.

### Critères d'acceptation

- une personne connaissant le dépôt peut installer et démarrer le projet sans
  connaissance des décisions historiques ;
- la procédure de démonstration utilise la baseline du Sprint 8 ;
- les dettes et limitations importantes sont visibles au bon endroit ;
- aucune documentation ne désigne encore le Sprint 7 comme sprint courant.

---

## LL-8006 — Décider et documenter l'ouverture de la première bêta contrôlée

**Priorité : Haute**

**Dépendance :** LL-8001, LL-8002, LL-8003, LL-8004, LL-8005

### Objectif

Décider objectivement si la version préparée peut être confiée à un premier
groupe restreint d'utilisateurs.

### Critères de décision

**GO bêta** si :

- la baseline MVP passe ;
- aucun défaut critique ou blocage connu n'est ouvert ;
- le démarrage et le compte `ADMIN` sont reproductibles ;
- les erreurs serveur importantes sont observables ;
- la documentation de démonstration est cohérente.

**NO-GO** si :

- un parcours MVP critique régresse ;
- un problème de sécurité ou de données empêche une utilisation réelle ;
- l'installation ou le démarrage n'est pas reproductible ;
- une dette considérée comme critique reste non maîtrisée.

### Livrable

Une décision explicite **GO bêta** ou **NO-GO bêta**, consignée dans
`docs/PROJECT_STATUS.md` et accompagnée des éventuels tickets de correction.

---

# Dépendances

```text
LL-8001
   ├── LL-8002 ─┐
   ├── LL-8003 ─┼──→ LL-8005 → LL-8006
   └── LL-8004 ─┘
```

---

# Règles du sprint

- Aucun nouveau domaine métier.
- Aucun nouveau collecteur.
- Aucun élargissement fonctionnel motivé uniquement par une idée de bêta.
- Toute correction doit être liée à la baseline, à la sécurité, à
  l'observabilité, à la dette retenue ou à la reproductibilité.
- Un ticket terminé doit avoir une preuve dans le code, les tests ou la
  validation documentée.
- Les retours utilisateurs serviront à construire la Phase 3 après la bêta ;
  ils ne sont pas anticipés sous forme de fonctionnalités dans ce sprint.

---

# Definition of Done

Le Sprint 8 est terminé lorsque :

- la baseline MVP est rejouée et documentée ;
- le premier administrateur peut être initialisé de façon reproductible et sûre ;
- les erreurs serveur importantes sont observables ;
- les dettes pertinentes pour la bêta sont traitées ou explicitement acceptées ;
- la documentation est cohérente avec l'état réel du projet ;
- une checklist de bêta est disponible ;
- une décision explicite **GO bêta** ou **NO-GO bêta** est prise.

---

# Livrable du Sprint

> Une version de LocalLife prête à être soumise à une première bêta contrôlée,\> avec une baseline vérifiée, un démarrage reproductible, une observabilité
> minimale, une documentation cohérente et une décision de go/no-go explicite.
