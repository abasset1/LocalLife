# ADR-0012 — Espace professionnel : revendication de lieux, création de lieux et de food trucks (PRO-01)

**Statut : proposition, en attente de validation par Alex.** Aucune
migration ni aucun code n'est écrit avant la validation des décisions
du § 4 (même démarche que ADR-0011, phase par phase). Le SQL ci-dessous
est documentaire.

Ticket : [#144 PRO-01](https://github.com/abasset1/LocalLife/issues/144).
Tickets liés, encore ouverts : #143 ARCHI-01 (séparer lieux, food trucks
et emplacements), #141 FOODTRUCK-01 (lieux de présence des food trucks),
#142 BILLING-01 (abonnements et droits professionnels).

## 1. Audit de l'existant (18/10/2026)

| Sujet | Constat | Conséquence pour PRO-01 |
|---|---|---|
| Comptes et rôles | `user/domain/Role` : `USER`, `ADMIN` seulement. Aucune notion de compte professionnel, d'organisation ni de droit fin. `SecurityConfig` protège par `authenticated()` ou `hasRole("ADMIN")`. | « Exiger un compte professionnel » n'est pas exprimable aujourd'hui. Il faut décider ce qu'est un compte pro (§ 4, D1). |
| Lieux importés | `location` + `place_type`/`place_status` (ADR-0011, V62) ; provenance dans `location_source` ; modération admin `PlaceModerationService` (`PENDING`→`PUBLISHED`/`REJECTED`), traçant `moderated_by`/`moderated_at`. API publique `GET /api/v1/places/within-bounds` et `/{id}` (seuls les `PUBLISHED`). | Base réutilisable pour le parcours A (fiche existante) et B (nouveau lieu = `location` typée). Aucune colonne de propriétaire/gestionnaire ; l'import réécrit les champs d'un lieu suivi (`PlaceRepository#update`). |
| Propriété d'un contenu | Précédent unique : `activity.created_by` (V26) et `GET /api/v1/activities/mine`, contrôle « auteur ou admin » dans le service. | Modèle de contrôle d'accès côté serveur à reprendre (vérification dans le service, pas seulement dans l'URL). |
| Food trucks | `food_truck` (V12) : `name`, `description`, **`latitude`/`longitude` obligatoires**, `category`, `contact`, `status` ; publié directement à la création ; aucun propriétaire, aucune modification, aucune modération ; `POST /api/v1/foodtrucks` ouvert à tout utilisateur connecté. | Contredit PRO-01/ARCHI-01 (« un food truck ne doit pas être modélisé comme un lieu fixe »). La séparation profil / emplacements / planning relève d'ARCHI-01 et FOODTRUCK-01. |
| Doublons de lieux | `PlaceMatcher`, `PlaceMatchRepository`, `EventLocationDuplicateMatcher` (nom + distance, sans fusion destructive, ADR-0011 § 2.9). | Réutilisables pour la recherche préalable du parcours B. |
| Justificatifs / fichiers | `media` ne stocke que des URL publiques ; aucun stockage de fichiers privés, aucune politique de conservation. | Le dépôt de justificatifs (pièces d'identité, Kbis…) demande un stockage privé et une politique RGPD : à décider (§ 4, D4). |
| Audit | Pas de table d'audit générique ; traces ponctuelles (`moderated_by`, `import_log`). | Une table d'historique des demandes suffit (§ 3.2). |
| Interface | Ni le frontend web ni l'application mobile n'ont d'espace professionnel. Le web a une administration (modération des lieux). | Écrans à créer : espace pro (3 entrées distinctes) et file de revendications côté admin. |

## 2. Principes proposés

1. **Pas de seconde entité « lieu ».** Un lieu créé par un professionnel
   est une `location` typée, comme un lieu importé (ADR-0011 § 2.1).
2. **La gestion d'un lieu est une relation, pas un champ de la fiche.**
   Table `place_manager (location_id, user_id, granted_at, granted_by,
   revoked_at)`, ouverte seulement par l'approbation d'une revendication
   (parcours A) ou par la création du lieu (parcours B).
3. **Une fiche importée n'est jamais modifiée par une revendication.**
   L'approbation ouvre un droit de gestion ; elle ne touche ni
   l'identifiant, ni la provenance, ni l'affichage. La façon dont les
   modifications d'un gestionnaire coexistent avec les réimports est une
   question distincte (§ 4, D5) — hors du premier lot.
4. **Le food truck reste hors du modèle des lieux** (ARCHI-01). PRO-01
   n'ajoute au food truck existant que le propriétaire et le cycle de vie
   (brouillon, publication, suspension, archivage) ; ses emplacements et
   son planning appartiennent à FOODTRUCK-01.
5. **Contrôles côté serveur**, dans les services : rôle/droit pro pour
   soumettre ou créer, `place_manager`/propriétaire pour modifier, `ADMIN`
   pour statuer. Consultation publique inchangée et gratuite.
6. **Aucune facturation** (BILLING-01) : le droit « professionnel » passe
   par un point d'extension unique, sur le modèle de `PremiumAccessPolicy`
   (ADR-0008), que BILLING-01 branchera sur les abonnements.

## 3. Modèle proposé (documentaire)

### 3.1 Revendication (parcours A)

```sql
CREATE TABLE place_claim (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES location (id),
    requester_id BIGINT NOT NULL REFERENCES users (id),
    status VARCHAR(30) NOT NULL CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'INFO_REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    relationship TEXT,          -- lien du demandeur avec le lieu
    evidence TEXT,              -- éléments justificatifs (texte/lien), voir D4
    decision_reason TEXT,
    moderator_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Une seule demande active par professionnel et par fiche.
CREATE UNIQUE INDEX uq_place_claim_active ON place_claim (location_id, requester_id)
    WHERE status IN ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'INFO_REQUESTED');

CREATE TABLE place_claim_event (            -- trace d'audit, jamais modifiée
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES place_claim (id),
    actor_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Transitions : `DRAFT→SUBMITTED` (demandeur), `SUBMITTED→IN_REVIEW`
(admin), `IN_REVIEW→APPROVED|REJECTED|INFO_REQUESTED` (admin, motif
obligatoire pour refus et demande d'informations),
`INFO_REQUESTED→SUBMITTED` (demandeur), `DRAFT|SUBMITTED|INFO_REQUESTED→WITHDRAWN`
(demandeur). Toute transition écrit un `place_claim_event`.

**Conflits** : plusieurs demandeurs peuvent revendiquer la même fiche ;
l'administrateur voit les demandes concurrentes ensemble. Approuver l'une
ne refuse pas automatiquement les autres (proposition : elles restent à
traiter, le modérateur voit qu'un gestionnaire existe déjà) — voir D3.

Seules les fiches **typées** (`place_type` non nul, donc lieux
permanents) sont revendicables ; un simple lieu d'événement ne l'est pas.

### 3.2 Nouveau lieu (parcours B)

- `GET /api/v1/pro/places/similar?name=&lat=&lng=` : lieux existants
  proches (rayon de 100 m de `PlaceMatcher`, similarité de nom), quel que soit leur statut sauf
  `REJECTED`, en réutilisant `PlaceMatcher`. L'interface les montre avant
  le formulaire, avec « C'est mon lieu → demander la gestion » (bascule
  vers le parcours A) et « Signaler un lieu similaire ».
- Création : `location` avec `place_type`, `place_status = 'DRAFT'` puis
  `PENDING` à la soumission ; publication par la modération existante
  (`PlaceModerationService`). Il faut **ajouter `DRAFT`** au `CHECK` de
  V62 et une colonne `created_by` (provenance « professionnel », sans
  `location_source`). Le créateur reçoit une ligne `place_manager`.
- Une création ne réutilise ni ne modifie jamais une fiche importée ;
  `LocationService#findOrCreate` n'est pas utilisé.

### 3.3 Food truck (parcours C)

Minimum propre à PRO-01, compatible avec ARCHI-01 :

```sql
ALTER TABLE food_truck
    ADD COLUMN owner_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN updated_at TIMESTAMPTZ;
-- statut étendu : DRAFT, PENDING, PUBLISHED, SUSPENDED, ARCHIVED, REJECTED
```

Les food trucks existants gardent leur identifiant, leur statut et leurs
coordonnées (affichage inchangé) tant qu'ARCHI-01 n'a pas déplacé la
position vers les emplacements.

### 3.4 API (préfixe `/api/v1/pro`, droit pro requis)

| Méthode | Chemin | Rôle |
|---|---|---|
| `POST` | `/pro/places/{id}/claims` | créer une demande (brouillon) |
| `GET` | `/pro/claims` | mes demandes et leur statut |
| `PATCH` | `/pro/claims/{id}` · `/submit` · `/withdraw` | compléter, soumettre, retirer |
| `GET` | `/pro/places/similar` | recherche anti-doublons |
| `POST`/`PUT` | `/pro/places` · `/pro/places/{id}` | créer / modifier un lieu que je gère |
| `GET` | `/pro/places` | mes lieux |
| `POST`/`PUT`/`PATCH` | `/pro/foodtrucks` · `/{id}` · `/{id}/suspend` · `/{id}/archive` | food truck |
| `GET` | `/admin/place-claims?status=` | file de modération (ADMIN) |
| `POST` | `/admin/place-claims/{id}/start-review` · `/approve` · `/reject` · `/request-info` | décisions (ADMIN, motif) |

`POST /api/v1/foodtrucks` (ouvert à tout connecté) serait conservé
pour compatibilité puis déprécié — voir D2.

## 4. Décisions attendues

| # | Question | Proposition |
|---|---|---|
| **D1** | Qu'est-ce qu'un « compte professionnel » avant BILLING-01 ? | Nouveau rôle `PRO` attribué par un admin (`PATCH /admin/users/{id}/role`), lu à travers une interface `ProAccessPolicy` que BILLING-01 remplacera par les abonnements. Alternative : profil pro déclaratif (SIRET, raison sociale) sans vérification. |
| **D2** | Ordre avec ARCHI-01 / FOODTRUCK-01 | Livrer PRO-01 en trois lots : **A (revendication)** et **B (nouveau lieu)** d'abord, indépendants des food trucks ; **C (food truck)** après ou avec ARCHI-01, pour ne pas figer un modèle « food truck = point fixe ». |
| **D3** | Plusieurs gestionnaires pour un même lieu ? | Oui (`place_manager` multi-lignes), décision au cas par cas par le modérateur. Alternative : un seul gestionnaire, toute nouvelle demande refusée tant qu'il existe. |
| **D4** | Justificatifs | Premier lot : texte et liens seulement (pas de fichiers), données visibles du demandeur et des admins uniquement, effacées 12 mois après décision. Le dépôt de fichiers privés demande un stockage dédié (ADR à part). |
| **D5** | Que peut modifier un gestionnaire sur une fiche **importée**, et que devient sa modification au réimport ? | Hors premier lot. Proposition pour plus tard : champs gérés par le professionnel (description, contacts, horaires) prioritaires sur la source, la source continuant à alimenter les autres. |
| **D6** | Modération des créations pro | Lieu : `PENDING` puis publication admin (comme les lieux importés). Food truck : publié directement (comportement actuel) mais suspendable par un admin. |

## 5. Découpage de livraison proposé (après validation)

1. Migration + `ProAccessPolicy` + rôle (D1) ; tests d'autorisation.
2. Lot A : `place_claim`, `place_claim_event`, `place_manager`, API pro et
   admin, tests de transitions, d'unicité et d'autorisation ; écrans web
   (bouton « Demander la gestion de ce lieu » sur la fiche, « Mes
   demandes », file admin).
3. Lot B : recherche anti-doublons, création brouillon/soumission,
   aperçu ; tests ; écran « Créer un lieu ».
4. Lot C : selon D2.
5. Documentation : README, contrat d'API, `PROJECT_STATUS.md`.
