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

## LL-EF-005 — Gérer les agendas depuis l'interface d'administration

### Objectif

Permettre à un administrateur de gérer les agendas utilisés par LocalLife directement depuis l'interface d'administration.

### Fonctionnalités attendues

- Afficher la liste des agendas existants.
- Ajouter un nouvel agenda.
- Modifier les informations d'un agenda si nécessaire.
- Supprimer un agenda.
- Demander une confirmation avant la suppression.
- Empêcher la suppression accidentelle d'un agenda utilisé par des activités, ou gérer explicitement les activités qui lui sont associées.

### Critères d'acceptation

- [x] Un administrateur peut consulter la liste des agendas.
- [x] Un administrateur peut créer un agenda.
- [x] Un administrateur peut renseigner les informations nécessaires à un agenda.
- [x] Un administrateur peut supprimer un agenda.
- [x] Une confirmation est demandée avant toute suppression.
- [x] Les droits d'administration sont vérifiés côté backend.
- [x] La suppression d'un agenda ne provoque pas de données incohérentes.
- [x] Les modifications sont persistées correctement.

**Priorité :** Haute  
**Type :** Évolution / Administration

**Statut : Terminé côté code.** Décisions validées par Alex : un
« agenda » désigne une véritable configuration dynamique qui pilote les
collecteurs OpenAgenda (uid + filtre région), en remplacement du
système par propriétés (`OpenAgendaSourcesConfig`, supprimée) ; la
suppression d'un agenda encore lié à des activités est autorisée, ces
activités étant détachées vers la source réservée `MANUAL` plutôt que
la suppression bloquée ; le CRUD est générique sur tous les types de
source (API/RSS/MANUAL), pas réservé à OpenAgenda.

Nouvelle section « Agendas » dans `/admin` (liste, création, édition,
suppression avec confirmation `window.confirm`), consommant les
nouveaux endpoints `POST`/`PUT`/`DELETE /api/v1/sources` (rôle `ADMIN`,
`SecurityConfig`). Voir `docs/PROJECT_STATUS.md` pour le détail complet
côté backend (migration, `Source`/`SourceService`,
`OpenAgendaCollectorFactory`, `ImportService` réécrit) et
`docs/02_Architecture/SOURCE_CONTRACT.md`/`COLLECTOR_CONTRACT.md`/
`COLLECTOR_OPERATIONS.md` pour la mise à jour des contrats
d'architecture.

⚠️ **Non vérifié par compilation/tests** dans cette session (pas
d'accès à Maven Central, réseau restreint à GitHub/npm/pip) : `mvn
test` doit être lancé avant tout merge — voir la remarque dans
`docs/PROJECT_STATUS.md`.

---

## LL-EF-006 — Créer une interface utilisateur

### Objectif

Créer une interface dédiée permettant à l'utilisateur de gérer son compte et d'accéder facilement aux fonctionnalités liées à son profil.

### Fonctionnalités attendues

- Accéder à son profil depuis l'application.
- Consulter ses informations personnelles.
- Modifier les informations autorisées.
- Accéder aux fonctionnalités liées à son compte.
- Permettre la déconnexion.
- Prévoir une structure pouvant accueillir de futures fonctionnalités utilisateur.

### Critères d'acceptation

- [x] Un utilisateur connecté peut accéder à son interface utilisateur.
- [x] Les informations de son compte sont affichées.
- [x] Les informations modifiables peuvent être modifiées.
- [x] Les modifications sont correctement persistées.
- [x] La déconnexion est accessible.
- [x] Un utilisateur non connecté ne peut pas accéder aux données d'un autre utilisateur.
- [x] Les contrôles d'accès sont également appliqués côté backend.

**Priorité :** Haute  
**Type :** Évolution / UX / Compte utilisateur

### Note d'implémentation

Nouveaux endpoints `GET`/`PATCH /api/v1/users/me` (username/email
modifiables ; `role`/`passwordHash` jamais via ce chemin — le
changement de mot de passe reste une fonctionnalité future, voir
LL-EF-007). L'utilisateur cible est résolu exclusivement depuis le JWT
(`JwtAuthentication`), jamais depuis un paramètre de requête.

Écart de sécurité trouvé et corrigé en traitant ce ticket :
`GET /api/v1/users/{id}` n'avait **aucune** protection (accessible sans
authentification, à n'importe qui) — restreint au rôle `ADMIN`
(endpoint non consommé par le frontend ni le mobile).

---

## LL-EF-007 — Réinitialisation du mot de passe

### Objectif

Permettre à un utilisateur ayant oublié son mot de passe de récupérer l'accès à son compte de manière sécurisée.

### Parcours attendu

1. L'utilisateur sélectionne « Mot de passe oublié ».
2. Il renseigne son adresse e-mail.
3. Un mécanisme sécurisé de réinitialisation est déclenché.
4. L'utilisateur reçoit un lien ou un moyen sécurisé permettant de définir un nouveau mot de passe.
5. L'utilisateur définit son nouveau mot de passe.
6. Le nouveau mot de passe est enregistré de manière sécurisée.
7. L'ancien mot de passe n'est plus utilisable.

### Critères d'acceptation

- [ ] Un lien « Mot de passe oublié » est disponible depuis l'interface de connexion.
- [ ] L'utilisateur peut demander une réinitialisation avec son adresse e-mail.
- [ ] Un mécanisme sécurisé de réinitialisation est généré.
- [ ] Le mécanisme de réinitialisation possède une durée de validité limitée.
- [ ] Le token de réinitialisation ne peut être utilisé qu'une seule fois.
- [ ] L'utilisateur peut définir un nouveau mot de passe.
- [ ] Le nouveau mot de passe respecte les règles de sécurité existantes.
- [ ] Le nouveau mot de passe est stocké sous forme de hash.
- [ ] L'ancien mot de passe n'est plus valide après la réinitialisation.
- [ ] Une demande de réinitialisation ne révèle pas si une adresse e-mail existe dans la base.
- [ ] Les tokens de réinitialisation ne sont pas stockés en clair si l'architecture permet leur hashage.

### Sécurité

La fonctionnalité doit être conçue pour éviter notamment :

- l'énumération des comptes ;
- la réutilisation d'un token ;
- l'utilisation d'un token expiré ;
- la fuite du token dans les logs ;
- la possibilité de définir un mot de passe sans preuve de possession du mécanisme de récupération.

**Priorité :** Haute  
**Type :** Évolution / Authentification / Sécurité

---

## LL-EF-008 — Ajouter une liste des activités

### Objectif

Permettre à l'utilisateur de consulter facilement l'ensemble des activités sous forme de liste, en complément de la carte.

### Accès

Ajouter un bouton en haut à droite de l'interface, dans le même bandeau que le bouton **« Filtrer par catégorie »**.

Le bouton permet de basculer vers l'affichage en liste.

### Affichage de la liste

La liste doit présenter les activités de manière claire et lisible.

Les activités doivent être :

1. regroupées ou triées par **ville** ;
2. puis triées par **date de l'activité**.

Chaque activité doit permettre d'identifier rapidement les informations essentielles, notamment :

- nom de l'activité ;
- ville ;
- date ;
- éventuellement l'heure ;
- catégorie.

### Comportement attendu

- [x] Un bouton « Liste » est présent en haut à droite.
- [x] Le bouton est placé dans le même bandeau que « Filtrer par catégorie ».
- [x] Un clic sur le bouton affiche la liste des activités.
- [x] Les activités sont triées par ville.
- [x] À l'intérieur d'une ville, les activités sont triées par date.
- [x] Les informations essentielles sont visibles sans ouvrir chaque activité.
- [x] L'utilisateur peut consulter le détail d'une activité depuis la liste.
- [x] Le retour à l'affichage carte est possible facilement.
- [x] Les filtres par catégorie restent cohérents avec l'affichage en liste.
- [x] La liste reste utilisable sur mobile.

### UX

L'affichage en liste ne doit pas remplacer définitivement la carte. Il s'agit d'une **seconde vue** permettant de passer rapidement de :

**Carte ↔ Liste**

L'état actif de la vue doit être clairement identifiable.

**Priorité :** Haute  
**Type :** Évolution / UX / Affichage

### Note d'implémentation

Le regroupement par ville a nécessité un changement de modèle métier
(la ville n'existait nulle part en base) — voir
`docs/02_Architecture/ADR-0001-adresse-structuree-activites.md`. À la
demande d'Alex, la carte et le détail affichent désormais une adresse
lisible (`address`) au lieu des coordonnées GPS brutes, avec repli sur
`city` puis sur les coordonnées pour les activités non re-géocodées/
ré-importées.

---

# À ajouter

Les prochains besoins identifiés seront ajoutés à cette section puis transformés en tickets numérotés.

## Prochains tickets

- [ ] LL-EF-009 — À définir
