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

- [x] Un utilisateur non connecté ne peut pas accéder à la saisie d'une activité.
- [x] Un utilisateur connecté peut ouvrir le formulaire de création.
- [x] Le formulaire s'affiche dans une nouvelle fenêtre.
- [x] Les champs nécessaires à la création d'une activité sont présents.
- [x] La fermeture de la fenêtre fonctionne correctement.
- [x] Le comportement est cohérent avec le système d'authentification existant.

**Priorité :** Haute  
**Type :** Évolution / UX

**Statut : Terminé.** Décisions validées par Alex : « nouvelle fenêtre »
= fenêtre modale (overlay) dans la même page ; le bouton « Proposer une
activité » reste toujours visible dans l'en-tête, y compris pour un
visiteur non connecté, et redirige vers `/login` au clic dans ce cas
(pas d'accès direct au formulaire). Fermeture de la modale possible via
le bouton ✕, un clic sur l'arrière-plan, ou la touche Échap. Voir
`docs/PROJECT_STATUS.md`, section Sprint Évol-Fix, pour le détail.

---

## LL-EF-002 — Supprimer le bandeau « Utiliser la localisation »

### Objectif

Simplifier l'utilisation de la carte en utilisant directement la localisation disponible.

### Besoins

- Supprimer le bandeau demandant à l'utilisateur de cliquer pour utiliser sa localisation.
- Utiliser automatiquement la localisation par défaut lorsque celle-ci est disponible.
- Ne pas demander une action utilisateur inutile.

### Critères d'acceptation

- [x] Le bandeau « Utiliser la localisation » n'est plus affiché.
- [x] La localisation disponible est utilisée automatiquement.
- [x] La carte se positionne correctement sur la localisation obtenue.
- [x] Le fonctionnement reste correct si la localisation n'est pas disponible ou refusée.

**Priorité :** Moyenne  
**Type :** Évolution / UX

**Statut : Terminé.** La demande de géolocalisation (`getCurrentPosition`)
se déclenche désormais automatiquement au montage du composant (une
seule fois), sans bouton ni bandeau. En cas de refus, d'indisponibilité
ou de timeout, aucun message n'est affiché : la recherche continue de
s'appuyer silencieusement sur la position par défaut (Marseille),
comportement de repli déjà en place depuis LL-4008. Le recentrage de la
carte sur la position obtenue (`MapRecenterOnUserPosition`, LL-7007)
est inchangé.

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

- [x] Déplacer la carte ne provoque plus de coupure visuelle.
- [x] La carte conserve sa position et son rendu pendant le chargement des données.
- [x] Les marqueurs sont ajoutés ou mis à jour sans reconstruire inutilement la carte.
- [x] Un déplacement continu ne déclenche pas une succession excessive d'appels API.
- [x] Les nouvelles activités apparaissent une fois les données disponibles.
- [x] Le comportement reste correct lors d'un déplacement rapide.
- [x] Le comportement reste correct lors d'un zoom.

**Priorité :** Haute  
**Type :** Évolution / UX / Performance

**Statut : Terminé.** La cause de la coupure visuelle était double :
(1) les anciens marqueurs étaient supprimés (`setActivities([])`) dès
le déclenchement de toute nouvelle recherche, avant même d'avoir reçu
la réponse — un déplacement de carte faisait donc disparaître puis
réapparaître les marqueurs à chaque geste ; (2) le texte « Chargement
des activités… » s'affichait au même moment, au-dessus de la carte
dans une mise en page en colonne, ce qui réduisait temporairement la
hauteur de la carte à chaque geste (léger « saut » visuel).

Un déplacement/zoom pur de la carte (seuls les `mapBounds` changent,
aucun filtre ni position n'a changé) est désormais traité comme un
rafraîchissement silencieux : les anciens marqueurs restent affichés
telles quels jusqu'à ce que les nouvelles données soient prêtes, sans
suppression préalable ni texte de chargement. Un changement actif
(filtre catégorie/date, position obtenue, nouvelle activité proposée)
conserve le comportement précédent (LL-4012/LL-4013) : suppression
immédiate des marqueurs et texte « Chargement » pendant que la
nouvelle recherche s'exécute — comportement jugé approprié pour une
action utilisateur explicite.

`MapContainer` n'a jamais été démonté/reconstruit à chaque recherche
(pas de prop `key` liée aux données) : ce point de l'énoncé était déjà
respecté, aucun changement nécessaire sur ce plan. Le nombre d'appels
API pendant un déplacement continu reste limité par le debounce de
400 ms déjà en place (`MAP_BOUNDS_DEBOUNCE_MS`, LL-4012), inchangé.

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