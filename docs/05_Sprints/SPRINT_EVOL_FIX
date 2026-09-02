# Sprint Évol-Fix

> Sprint dédié aux évolutions fonctionnelles, améliorations UX et corrections identifiées sur LocalLife.
>
> Ce document sera alimenté progressivement au fur et à mesure de l'identification des besoins.

---

## LL-EF-001 — Revoir l'affichage de la saisie d'une activité

### Objectif

Améliorer le parcours de création d'une activité.

### Besoins

- La saisie d'une activité doit être accessible uniquement aux utilisateurs connectés.
- Le formulaire de saisie doit apparaître dans une nouvelle fenêtre.
- La saisie doit être présentée sous forme de formulaire clair et structuré.

### Critères d'acceptation

- [ ] Un utilisateur non connecté ne peut pas accéder à la saisie d'une activité.
- [ ] Un utilisateur connecté peut ouvrir le formulaire de création.
- [ ] Le formulaire s'affiche dans une nouvelle fenêtre.
- [ ] Les champs nécessaires à la création d'une activité sont présents.
- [ ] La fermeture de la fenêtre fonctionne correctement.
- [ ] Le comportement est cohérent avec le système d'authentification existant.

**Priorité :** Haute  
**Type :** Évolution / UX

---

## LL-EF-002 — Supprimer le bandeau « Utiliser la localisation »

### Objectif

Simplifier l'utilisation de la carte en utilisant directement la localisation disponible.

### Besoins

- Supprimer le bandeau demandant à l'utilisateur de cliquer pour utiliser sa localisation.
- Utiliser automatiquement la localisation par défaut lorsque celle-ci est disponible.
- Ne pas demander une action utilisateur inutile.

### Critères d'acceptation

- [ ] Le bandeau « Utiliser la localisation » n'est plus affiché.
- [ ] La localisation disponible est utilisée automatiquement.
- [ ] La carte se positionne correctement sur la localisation obtenue.
- [ ] Le fonctionnement reste correct si la localisation n'est pas disponible ou refusée.

**Priorité :** Moyenne  
**Type :** Évolution / UX

---

## LL-EF-003 — Revoir le rechargement de la carte

### Objectif

Supprimer la coupure visuelle provoquée par le rechargement de la carte lors des déplacements.

### Constat

Actuellement, lorsqu'un utilisateur déplace la carte, les données sont rechargées d'une manière qui provoque une interruption ou un rafraîchissement visuel désagréable.

### Comportement attendu

- La carte reste affichée et fluide pendant les déplacements.
- Le déplacement de la carte ne doit pas provoquer de rechargement visuel complet.
- Les activités affichées doivent être mises à jour sans faire disparaître la carte.
- Le chargement des nouvelles données doit être indépendant du rendu de la carte.
- Si de nouvelles activités doivent être récupérées après un déplacement, leur chargement doit se faire en arrière-plan.
- Éviter autant que possible les appels API inutiles pendant un déplacement continu.

### Critères d'acceptation

- [ ] Déplacer la carte ne provoque plus de coupure visuelle.
- [ ] La carte conserve sa position et son rendu pendant le chargement des données.
- [ ] Les marqueurs sont ajoutés ou mis à jour sans reconstruire inutilement la carte.
- [ ] Un déplacement continu ne déclenche pas une succession excessive d'appels API.
- [ ] Les nouvelles activités apparaissent une fois les données disponibles.
- [ ] Le comportement reste correct lors d'un déplacement rapide.
- [ ] Le comportement reste correct lors d'un zoom.

**Priorité :** Haute  
**Type :** Évolution / UX / Performance

---

## LL-EF-004 — Créer une interface d'administration

### Objectif

Créer une interface réservée aux administrateurs permettant notamment de gérer et valider les activités proposées par les utilisateurs.

### Fonctionnalités attendues

- Accès à l'interface uniquement pour les utilisateurs disposant du rôle administrateur.
- Affichage des activités en attente de validation.
- Consultation des informations d'une activité avant validation.
- Validation d'une activité.
- Refus d'une activité.
- Consultation des activités déjà validées ou refusées.
- Affichage clair du statut de chaque activité.

### Critères d'acceptation

- [ ] Un utilisateur non administrateur ne peut pas accéder à l'interface.
- [ ] L'administrateur peut voir les activités en attente.
- [ ] L'administrateur peut consulter le détail d'une activité.
- [ ] L'administrateur peut valider une activité.
- [ ] Une activité validée devient disponible dans l'application selon les règles existantes.
- [ ] L'administrateur peut refuser une activité.
- [ ] Le statut de l'activité est correctement persisté.
- [ ] Les actions d'administration sont protégées côté backend et pas uniquement dans l'interface.

### Point d'architecture

Prévoir un véritable statut de modération des activités, par exemple :

- `PENDING`
- `APPROVED`
- `REJECTED`

L'objectif est de disposer d'une base propre pour les futures fonctionnalités de modération.

**Priorité :** Haute  
**Type :** Évolution / Administration

---

# À ajouter

Les prochains besoins identifiés seront ajoutés à cette section puis transformés en tickets numérotés.

## Prochains tickets

- [ ] LL-EF-005 — À définir