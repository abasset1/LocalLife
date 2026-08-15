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

---

## Phase 2 — Validation du MVP
**Statut : ⏳ À planifier**

Objectif : vérifier que le produit est réellement utile avant d'ajouter de nouvelles briques.

Pistes :

- qualité des données ;
- administration ;
- amélioration des contributions ;
- food trucks ;
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
