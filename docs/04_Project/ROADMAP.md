# Roadmap — LocalLife

## Phase 0 — Cadrage
**Statut : ✅ Terminé**

- Vision produit
- MVP
- User Stories
- Backlog
- Architecture
- Modèle de données
- API MVP

---

## Phase 1 — Construction du socle et du MVP
**Statut : ✅ Terminé**

### Sprint 0 — Socle technique
**Statut : ✅ Terminé**

Backend, frontend, PostgreSQL/PostGIS, Docker, Flyway, CI.

### Sprint 1 — Première fonctionnalité visible
**Statut : ✅ Terminé**

Carte interactive, activités et données de démonstration.

### Sprint 2 — Utilisateurs, catégories et contributions
**Statut : ✅ Terminé**

Utilisateurs, catégories et première contribution d'activité.

### Sprint 3 — Authentification et géocodage
**Statut : ✅ Terminé**

JWT, BCrypt, protection des endpoints, authentification frontend et géocodage.

### Sprint 4 — Recherche et découverte géographique
**Statut : ✅ Terminé**

- Recherche par rayon PostGIS
- Recherche par zone cartographique
- Filtres catégorie/date
- Géolocalisation utilisateur
- Intégration des filtres dans la carte

### Sprint 5 — Alimentation réelle
**Statut : ✅ Terminé**

- Modèle et module `Source`, source réservée `MANUAL` pour les
  contributions manuelles.
- Contrat et interface `Collector` ; premier collecteur réel
  (OpenAgenda).
- Modèle `CollectedActivity`, pipeline de normalisation et validation.
- Détection simple des doublons (identifiant externe, ou clé composite).
- Persistance des imports (création/mise à jour/suppression douce),
  journalisation.
- Tests du pipeline (cas principaux automatisés) et vérification de
  l'intégration avec la recherche/les filtres du Sprint 4.

### Sprint 6 — Qualité des données et administration minimale
**Statut : ✅ Terminé**

- Validation renforcée des activités (`title` obligatoire, `url`
  conservée).
- Statut de modération (`PENDING`/`PUBLISHED`/`REJECTED`) ; seules les
  activités `PUBLISHED` visibles publiquement.
- Contrôle administratif minimal (consultation par statut, publier/
  rejeter une activité), réservé au rôle `ADMIN`.
- Source d'une activité identifiable via l'API (`GET /api/v1/sources`).
- Premier jalon Food Truck : modèle défini, module indépendant créé,
  création possible, visible sur la carte avec une distinction
  visuelle.
- Tests de non-régression consolidés couvrant les points ci-dessus.

Cette liste de Phase 2 comprenait déjà (« Pistes ») la qualité des
données, l'administration et les food trucks : le Sprint 6 en a livré
un premier socle minimal, mais Phase 2 elle-même (validation par de
vrais utilisateurs) reste à planifier — voir ci-dessous.

---

## Phase 2 — Validation du MVP
**Statut : ⏳ À planifier**

Objectif : vérifier que le produit est réellement utile avant d'ajouter de nouvelles briques.

Pistes :

- qualité des données (premier socle livré en Sprint 6, à approfondir) ;
- administration (contrôle minimal livré en Sprint 6, à étoffer si
  besoin réel) ;
- amélioration des contributions ;
- food trucks (premier jalon livré en Sprint 6 : modèle, création,
  visibilité sur la carte — reste à approfondir : modération dédiée,
  recherche géographique, etc.) ;
- expérience utilisateur ;
- stabilité et performance.

---

## Phase 3 — Évolution
**Statut : ⏳ À planifier**

Uniquement après validation du MVP.

Pistes :

- plusieurs sources ;
- nouveaux types de lieux ;
- collecteurs supplémentaires ;
- fonctionnalités communautaires ;
- application mobile éventuelle.

---

## Règle de roadmap

La roadmap reste volontairement simple.

Une fonctionnalité hors MVP ne doit pas être ajoutée à un sprint uniquement parce qu'elle est techniquement intéressante. Toute évolution doit être justifiée par une valeur utilisateur ou une nécessité technique.
