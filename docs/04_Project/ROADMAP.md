# Roadmap — LocalLife

## Source de vérité

Ce fichier est la **source de vérité de la roadmap**.

`docs/ROADMAP.md` est un ancien doublon historique et ne doit plus être modifié pour planifier le projet. Sa suppression pourra être faite séparément.

---

## Phase 0 — Cadrage
**Statut : ✅ Terminé**

Vision, MVP, User Stories, backlog initial, architecture, modèle de données et API MVP.

---

## Phase 1 — Construction du socle et du MVP
**Statut : ✅ Terminé**

| Sprint | Statut | Objectif |
|---|---|---|
| 0 | ✅ | Socle technique |
| 1 | ✅ | Première fonctionnalité visible |
| 2 | ✅ | Utilisateurs, catégories, contributions |
| 3 | ✅ | Authentification et géocodage |
| 4 | ✅ | Recherche et découverte géographique |
| 5 | ✅ | Alimentation réelle |
| 6 | ✅ | Qualité des données, modération minimale et Food Truck |

La construction du périmètre MVP est considérée comme terminée.

---

## Phase 2 — Validation du MVP
**Statut : 🟡 En cours — Sprint 7 à venir**

### Objectif

Vérifier que le MVP fonctionne réellement de bout en bout avant d'ajouter de nouvelles fonctionnalités.

### Sprint 7 — Validation du MVP
**Statut : ⏳ À faire**

- protocole de validation ;
- déclenchement contrôlé de l'import ;
- validation des données réelles ;
- carte et recherche avec données réelles ;
- contribution et authentification ;
- validation du Food Truck existant ;
- correction des blocages ;
- environnement de démonstration ;
- décision de sortie du MVP.

**Référence détaillée :** `docs/05_Sprints/SPRINT_7.md`.

### Règle de sortie

Aucun Sprint 8 ne doit être défini avant la conclusion de Sprint 7.

Deux résultats sont possibles :

1. **MVP validé** → préparation d'une première bêta et définition des évolutions selon les retours ;
2. **MVP non validé** → sprint de correction ciblé, sans élargissement fonctionnel.

---

## Phase 3 — Évolution après validation
**Statut : ⏳ Non démarrée**

Cette phase ne sera planifiée qu'après validation du MVP. Les possibilités restent volontairement ouvertes :

- amélioration des sources et des collecteurs ;
- nouveaux types de lieux ;
- amélioration des contributions ;
- fonctionnalités communautaires ;
- application mobile éventuelle.

Aucune de ces pistes n'est actuellement un engagement de développement.

---

## Dette technique et décisions à traiter

Les éléments suivants existent mais ne doivent pas automatiquement devenir des tickets de sprint :

- déclencheur du pipeline d'import → traité dans LL-7002 ;
- duplication des `ROADMAP.md` → décision documentaire séparée ;
- vulnérabilité transitive `nanoid` → à traiter dans un ticket technique dédié si toujours présente ;
- défaut de formatage `ActivityController` → à corriger dans un ticket qualité dédié si nécessaire.

---

## Règles de roadmap

- La roadmap décrit les **objectifs**, pas chaque détail technique.
- Le backlog contient les tickets.
- Un sprint ne peut pas apparaître dans la roadmap sans être défini dans `docs/05_Sprints/`.
- Un ticket ne peut pas être déclaré terminé uniquement parce qu'un document le dit : une preuve dans le dépôt est requise.
- Aucune fonctionnalité hors MVP ne doit être ajoutée avant validation utilisateur.
- Toute nouvelle phase doit être justifiée par la valeur utilisateur ou une nécessité technique démontrée.
