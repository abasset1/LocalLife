# Roadmap — LocalLife

## Source de vérité

Ce fichier est la **source de vérité de la roadmap**.

`docs/ROADMAP.md` est un point d'entrée historique et ne doit pas être utilisé
pour planifier le projet.

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

La construction du périmètre MVP est terminée.

---

## Phase 2 — Validation et préparation de la bêta
**Statut : 🟡 En cours — Sprint 9**

### Sprint 7 — Validation du MVP
**Statut : ✅ Terminé**

- protocole de validation ;
- déclenchement contrôlé de l'import ;
- validation des données réelles ;
- carte et recherche avec données réelles ;
- contribution et authentification ;
- validation du Food Truck ;
- correction des blocages ;
- environnement de démonstration ;
- décision : **MVP validé → préparation de la bêta**.

**Référence :** `docs/05_Sprints/SPRINT_7.md`.

### Sprint 8 — Préparation de la bêta
**Statut : ✅ Terminé — GO bêta conditionnel confirmé par Alex (26/08/2026)**

Objectif : rendre le MVP suffisamment robuste, reproductible et documenté
pour une première bêta contrôlée, sans ajouter de nouveau domaine métier.

| Ticket | Objectif | Statut |
|---|---|---|
| LL-8001 | Rejouer le parcours MVP après les corrections et figer la baseline | ✅ |
| LL-8002 | Sécuriser le bootstrap du premier compte administrateur | ✅ |
| LL-8003 | Améliorer la journalisation des erreurs serveur bloquantes | ✅ |
| LL-8004 | Configurer les sources OpenAgenda d'Avignon | ✅ |
| LL-8005 | Automatiser l'import et la persistance des données collectées | ✅ |
| LL-8006 | Vérifier l'apparition des activités de bout en bout sur la carte | ✅ |
| LL-8007 | Traiter la dette technique pertinente pour une bêta | ✅ |
| LL-8008 | Consolider la documentation et préparer la checklist de bêta | ✅ |
| LL-8009 | Décider et documenter l'ouverture de la première bêta contrôlée | ✅ GO conditionnel confirmé |

**Référence :** `docs/05_Sprints/SPRINT_8.md`.

### Sprint 9 — Corrections post-bêta
**Statut : 🟡 En cours**

Objectif : traiter, un par un, les correctifs identifiés par Alex
pendant/après la bêta. Sprint ouvert au fil de l'eau, pas de périmètre
figé à l'avance.

| Ticket | Objectif | Statut |
|---|---|---|
| LL-9001 | Ne plus afficher les activités hors période sur les recherches publiques | ✅ (en attente mvn verify) |

**Référence :** `docs/05_Sprints/SPRINT_9.md`.

---

## Phase 3 — Évolution après bêta
**Statut : 🟡 En cours — Sprint 11**

Cette phase est planifiée à partir des retours utilisateurs et des besoins
réellement observés. Pistes encore ouvertes, non engagées :

- nouveaux types de lieux ;
- amélioration des contributions ;
- fonctionnalités communautaires ;
- application mobile éventuelle.

### Sprint 11 — Modèle événementiel riche
**Statut : 🟡 Fonctionnellement complet, dernière confirmation `mvn verify`
entièrement vert en attente (Alex)**

Objectif : dépasser le modèle plat hérité du MVP (une date, un lieu par
activité) pour représenter fidèlement des programmations réelles —
récurrence, plusieurs horaires/lieux pour une même activité, exceptions
(annulation, déplacement), fiche événementielle riche (médias, liens,
accessibilité, tags), tout en préparant (sans l'implémenter) une future
distinction d'accès Basic/Premium.

| Ticket | Objectif | Statut |
|---|---|---|
| LL-11001 | Modèle `Location` | ✅ |
| LL-11002 | Modèle `Schedule` | ✅ |
| LL-11003 | Récurrence (RRULE) | ✅ |
| LL-11004 | Modèle `Occurrence` | ✅ |
| LL-11005 | Exceptions (occurrence annulée/déplacée) | ✅ |
| LL-11006 | Fiche événementielle riche (description longue, conditions, âge) | ✅ |
| LL-11007 | Médias | ✅ |
| LL-11008 | Liens/contacts | ✅ |
| LL-11009 | Accessibilité, tags, informations complémentaires | ✅ |
| LL-11010 | Conservation du payload brut | ✅ |
| LL-11011 | Adapter le pipeline d'import | ✅ |
| LL-11012 | Adapter la recherche aux occurrences | ✅ |
| LL-11013 | Séparer les DTO résumé/détail | ✅ |
| LL-11014 | Préparer la fiche Premium | ✅ |
| LL-11015 | Exploiter le nouveau modèle dans le frontend | ✅ |
| LL-11016 | Tester les cas réels de programmation | ✅ |
| LL-11017 | Documenter le nouveau modèle | Cette entrée |

**Référence :** `docs/05_Sprints/SPRINT_11.md`. Détail ticket par ticket,
décisions de conception et bugs corrigés grâce au premier vrai run
`mvn verify` en CI : `docs/PROJECT_STATUS.md` (section LL-11017) et
`docs/ADR-0005-import-pipeline-enrichment.md` à
`docs/ADR-0010-cas-reels-programmation.md`.

Pistes encore ouvertes après ce sprint, non engagées :

- application mobile éventuelle ;
- amélioration des sources et collecteurs.

Aucune de ces pistes n'est actuellement un engagement de développement.

---

## Dette technique et décisions à traiter

Les dettes restent suivies dans `docs/DETTE_TECHNIQUE.md`. Elles ne deviennent
un ticket que si leur impact est démontré ou si elles sont explicitement
retenues dans un sprint.

---

## Règles de roadmap

- La roadmap décrit les **objectifs**, pas chaque détail technique.
- Le backlog contient les tickets.
- Un sprint ne peut pas apparaître dans la roadmap sans être défini dans `docs/05_Sprints/`.
- Un ticket ne peut pas être déclaré terminé uniquement parce qu'un document le dit : une preuve dans le dépôt est requise.
- Aucune nouvelle fonctionnalité majeure ne doit être ajoutée avant les premiers retours de bêta.
- Toute nouvelle phase doit être justifiée par la valeur utilisateur ou une nécessité technique démontrée.
