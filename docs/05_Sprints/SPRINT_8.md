# Sprint 8 — Préparation de la bêta

**Statut :** ⏳ À faire

---

# Objectif

Préparer une première bêta contrôlée à partir du MVP validé au Sprint 7.
**La bêta de LocalLife n'a pas de sens si les activités ne sont pas alimentées automatiquement par les collecteurs.**
Ce sprint vise donc à :
1. **Valider le parcours MVP** (LL-8001).
2. **Sécuriser le bootstrap opérationnel** (LL-8002).
3. **Améliorer l'observabilité** (LL-8003).
4. **Configurer les sources OpenAgenda** (LL-8004) — **Nouvelle priorité** pour tester l'agrégation automatique.
5. **Automatiser l'import et la persistance** (LL-8005).
6. **Valider l'affichage bout en bout** (LL-8006).
7. **Traiter la dette technique** (LL-8004 anciennement, maintenant renuméroté en LL-8007).
8. **Consolider la documentation** (LL-8005 anciennement, maintenant renuméroté en LL-8008).
9. **Décider l'ouverture de la bêta** (LL-8006 anciennement, maintenant renuméroté en LL-8009).

---

# Périmètre

## Inclus

- Rejouer le parcours MVP après les corrections du Sprint 7 ;
- **Configurer plusieurs agendas OpenAgenda pour Avignon (LL-8004)** ;
- **Automatiser l'import et la persistance des données (LL-8005)** ;
- **Valider l'affichage des activités sur la carte (LL-8006)** ;
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
- optimisation d'architecture non justifiée par un problème réel.

---

# Tickets

---

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

---
---

## LL-8004 — Configurer les sources OpenAgenda d’Avignon

**Priorité : Haute**

**Dépendance :** LL-8001

### Objectif
**Disposer d'un ensemble représentatif d'agendas OpenAgenda couvrant différents types d'activités à Avignon.**

### Le ticket doit prévoir :

- identifier plusieurs agendas pertinents pour Avignon ;
- sélectionner des sources suffisamment variées :
  - culture ;
  - spectacles ;
  - patrimoine ;
  - loisirs ;
  - éventuellement sport / vie locale ;
- configurer leurs identifiants dans LocalLife ;
- vérifier que chaque agenda est réellement accessible et exploitable par le collector ;
- lancer une collecte sur l'ensemble des agendas ;
- vérifier que les événements récupérés sont correctement attribués à leur source ;
- vérifier qu'il n'y a pas de doublons excessifs entre agendas ;
- documenter les agendas retenus et leur rôle.

### Critères d'acceptation
- **Une exécution du collector doit récupérer des activités provenant de plusieurs agendas Avignon, et pas uniquement d'une source de démonstration.**

---

---
---

## LL-8005 — Automatiser l'import et la persistance des données collectées

**Priorité : Haute**

**Dépendance :** LL-8004

### Objectif
**Mettre en place un pipeline automatisé pour importer et stocker les données récupérées depuis les sources OpenAgenda configurées dans LL-8004.**

### Le ticket doit prévoir :

- configurer un job planifié (cron) pour exécuter régulièrement le collector ;
- implémenter la logique de persistance des événements collectés dans la base de données ;
- gérer les mises à jour incrémentielles (éviter les doublons, gérer les modifications) ;
- valider que les données sont correctement structurées et accessibles via l'API LocalLife ;
- s'assurer que les métadonnées des sources (ex: ID de l'agenda OpenAgenda) sont conservées.

### Critères d'acceptation
- **Les données collectées doivent être automatiquement importées et persistées sans intervention manuelle.**

---

---
---
## LL-8006 — Vérifier l'apparition des activités de bout en bout sur la carte

**Priorité : Haute**

**Dépendance :** LL-8004, LL-8005

### Objectif
**S'assurer que les activités collectées et importées (LL-8004 et LL-8005) s'affichent correctement sur l'interface utilisateur, notamment sur la carte interactive.**

### Le ticket doit prévoir :

- vérifier que les événements persistés sont bien exposés via l'API utilisée par le frontend ;
- tester l'affichage des activités sur la carte (marqueurs, popups, filtres) ;
- valider que les informations clés (titre, date, lieu, source) sont correctement affichées ;
- tester les interactions utilisateur (clic sur un événement, filtrage par type/catégorie) ;
- corriger les éventuels problèmes d'affichage ou de cohérence des données ;
- documenter les tests effectués et les résultats.

### Critères d'acceptation
- **Les activités collectées doivent être visibles et interactives sur la carte, avec toutes leurs métadonnées.**

---
---
---
## LL-8007 — Traiter la dette technique pertinente pour une bêta

**Priorité : Moyenne**

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
---
---
## LL-8008 — Consolider la documentation et préparer la checklist de bêta

**Priorité : Moyenne**

**Dépendance :** LL-8002, LL-8003, LL-8007

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
---
---
## LL-8009 — Décider et documenter l'ouverture de la première bêta contrôlée

**Priorité : Haute**

**Dépendance :** LL-8001, LL-8002, LL-8003, LL-8004, LL-8005, LL-8006, LL-8007, LL-8008

### Objectif

Décider objectivement si la version préparée peut être confiée à un premier
groupe restreint d'utilisateurs.

### Critères de décision

**GO bêta** si :

- la baseline MVP passe ;
- **les activités sont alimentées automatiquement par les collecteurs (LL-8004, LL-8005, LL-8006)** ;
- aucun défaut critique ou blocage connu n'est ouvert ;
- le démarrage et le compte `ADMIN` sont reproductibles ;
- les erreurs serveur importantes sont observables ;
- la documentation de démonstration est cohérente.

**NO-GO** si :

- un parcours MVP critique régresse ;
- un problème de sécurité ou de données empêche une utilisation réelle ;
- l'installation ou le démarrage n'est pas reproductible ;
- **les activités ne sont pas alimentées automatiquement par les collecteurs** ;
- une dette considérée comme critique reste non maîtrisée.

### Livrable

Une décision explicite **GO bêta** ou **NO-GO bêta**, consignée dans
`docs/PROJECT_STATUS.md` et accompagnée des éventuels tickets de correction.

---
---
---
# Dépendances

```text
LL-8001
   ├── LL-8002 ─┐
   ├── LL-8003 ─┼──→ LL-8007 → LL-8008 → LL-8009
   ├── LL-8004 ─┼──→ LL-8005 ─┐
   └── LL-8006 ───────┘
