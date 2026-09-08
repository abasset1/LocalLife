il# Sprint Évol-Fix

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
## LL-EF-009 — Afficher l'adresse d'une activité

### Objectif

Afficher une adresse lisible lorsqu'un utilisateur consulte le détail d'une activité depuis la carte ou la liste.

### Principe

Les activités disposent déjà de coordonnées GPS obtenues lors de leur création à partir d'une adresse.

Le système doit pouvoir effectuer un géocodage inverse :

Coordonnées GPS → Adresse lisible

### Comportement attendu

- Lorsqu'un utilisateur clique sur une activité, son détail est affiché.
- L'adresse correspondant aux coordonnées GPS de l'activité est affichée.
- L'adresse doit être présentée sous une forme lisible pour l'utilisateur.
- Le système doit gérer le cas où aucune adresse ne peut être déterminée.
- Le chargement de l'adresse ne doit pas bloquer l'affichage du reste des informations de l'activité.

### Critères d'acceptation

- [ ] Le détail d'une activité affiche son adresse.
- [ ] L'adresse est obtenue à partir des coordonnées GPS de l'activité.
- [ ] Le géocodage inverse est effectué via le service de géocodage utilisé par LocalLife.
- [ ] Une erreur du service de géocodage inverse n'empêche pas l'affichage de l'activité.
- [ ] Un message ou une valeur de remplacement est affiché lorsqu'aucune adresse n'est disponible.
- [ ] L'adresse affichée correspond à la position GPS de l'activité.
- [ ] Le fonctionnement est identique depuis la carte et depuis la liste.

### Architecture

Les coordonnées GPS restent la donnée de référence stockée pour la localisation de l'activité.

L'adresse retournée par le géocodage inverse ne doit pas être persistée en base dans le cadre de ce ticket, sauf nécessité identifiée lors de l'implémentation.

### Performance

Éviter de déclencher plusieurs requêtes de géocodage inverse pour une même activité lors d'une consultation.

Prévoir si nécessaire un mécanisme de cache côté backend ou frontend.

**Priorité :** Moyenne  
**Type :** Évolution / UX / Géolocalisation

---

## LL-EF-010 — Identifier et annuler les activités créées manuellement

### Objectif

Permettre d'identifier les activités saisies manuellement par un utilisateur et permettre à leur créateur de les annuler.

### Périmètre

Cette fonctionnalité concerne uniquement les activités **créées manuellement par un utilisateur**.

Les activités provenant des collectors/agendas externes ne sont pas concernées.

### Affichage du créateur

Lorsqu'une activité créée manuellement est consultée, afficher l'utilisateur qui l'a créée.

L'information doit être présentée de manière claire dans le détail de l'activité.

Exemple :

> Créée par : Jean Dupont

### Annulation

L'utilisateur ayant créé l'activité doit pouvoir l'annuler.

L'annulation doit :

- être accessible depuis le détail de l'activité ;
- demander une confirmation avant l'action ;
- modifier le statut de l'activité ;
- empêcher l'activité annulée d'être considérée comme une activité active ;
- conserver l'activité en base afin de préserver l'historique.

### Droits

- [ ] Seul le créateur peut annuler son activité.
- [ ] Un administrateur peut également annuler une activité.
- [ ] Un autre utilisateur ne peut pas annuler l'activité.
- [ ] Les contrôles de permission sont effectués côté backend.

### Statut

Prévoir un statut permettant de distinguer une activité active d'une activité annulée.

Exemple :

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

Le statut exact devra être cohérent avec le système de modération existant.

### Critères d'acceptation

- [ ] Une activité saisie manuellement possède une référence vers son créateur.
- [ ] Le créateur est identifiable depuis le détail de l'activité.
- [ ] Le créateur peut annuler son activité.
- [ ] Une confirmation est demandée avant l'annulation.
- [ ] Une activité annulée n'est plus affichée comme activité active sur la carte.
- [ ] Une activité annulée n'apparaît plus dans la liste des activités actives.
- [ ] L'activité reste conservée en base.
- [ ] Un administrateur peut annuler une activité.
- [ ] Un utilisateur quelconque ne peut pas annuler l'activité d'un autre utilisateur.
- [ ] Les activités provenant des collectors ne sont pas concernées par cette fonctionnalité.

### Architecture

L'activité doit conserver une référence vers son créateur lorsqu'elle est créée manuellement.

Il est préférable d'utiliser une relation vers l'utilisateur (`createdBy` ou équivalent) plutôt que de simplement stocker son nom ou son adresse e-mail.

Cela permettra notamment de gérer correctement les changements d'informations du compte utilisateur.

**Priorité :** Haute  
**Type :** Évolution / Activités / Droits utilisateur

---

## LL-EF-011 — Créer un compte via Google

### Objectif

Permettre à un utilisateur de créer un compte LocalLife ou de se connecter à un compte existant en utilisant son compte Google.

### Fonctionnement attendu

Depuis l'interface de connexion / inscription :

- afficher un bouton **« Continuer avec Google »** ;
- l'utilisateur est redirigé vers Google pour s'authentifier ;
- après authentification, LocalLife récupère les informations nécessaires ;
- si aucun compte LocalLife correspondant n'existe, un compte est créé ;
- si un compte existe déjà et peut être associé de manière fiable, l'utilisateur est connecté à celui-ci.

### Création du compte

Lors de la première connexion via Google :

- récupérer uniquement les informations nécessaires ;
- utiliser l'adresse e-mail fournie et vérifiée par Google comme identifiant de compte ;
- créer l'utilisateur avec le rôle utilisateur standard ;
- ne pas demander de mot de passe LocalLife pour ce compte.

### Sécurité

- [ ] Utiliser le protocole OAuth 2.0 / OpenID Connect recommandé par Google.
- [ ] Vérifier correctement l'identité retournée par Google côté backend.
- [ ] Ne jamais considérer une simple adresse e-mail fournie par le frontend comme une preuve d'identité.
- [ ] Ne jamais stocker le mot de passe Google.
- [ ] Les tokens Google ne doivent pas être exposés au frontend ou aux logs inutilement.
- [ ] Les permissions demandées à Google doivent être limitées aux informations nécessaires.

### Compatibilité avec l'authentification existante

L'ajout de Google ne doit pas casser l'authentification classique.

Un utilisateur doit pouvoir avoir :

- une authentification LocalLife classique ;
- une authentification Google ;
- ou, si l'architecture le permet, les deux méthodes associées au même compte.

### Critères d'acceptation

- [ ] Le bouton « Continuer avec Google » est disponible sur l'inscription.
- [ ] Le bouton est également disponible sur la connexion.
- [ ] Un nouvel utilisateur peut créer son compte avec Google.
- [ ] Un utilisateur existant peut se connecter avec Google.
- [ ] Le compte créé possède les droits utilisateur standards.
- [ ] L'utilisateur est correctement authentifié dans LocalLife après le retour de Google.
- [ ] Le JWT/session LocalLife existant est utilisé après authentification Google.
- [ ] Aucun mot de passe Google n'est stocké par LocalLife.
- [ ] Les erreurs ou refus d'authentification Google sont correctement gérés.
- [ ] Le mécanisme fonctionne également sur la future version mobile.

### Architecture

Prévoir dès maintenant une architecture d'authentification permettant plusieurs providers.

Exemple conceptuel :

- `LOCAL`
- `GOOGLE`

Éviter de faire dépendre directement l'entité `User` du fonctionnement spécifique de Google.

L'association entre un utilisateur LocalLife et son identité Google doit être modélisée proprement afin de permettre l'ajout ultérieur d'autres providers sans refonte de l'authentification.

**Priorité :** Moyenne  
**Type :** Évolution / Authentification / UX / Sécurité

---

## LL-EF-012 — Liker et consulter ses événements favoris

### Objectif

Permettre à un utilisateur connecté de sauvegarder des événements qu'il apprécie afin de pouvoir les retrouver facilement ultérieurement.

### Fonctionnalités attendues

#### Liker un événement

- Afficher une action permettant de liker un événement.
- Un utilisateur connecté peut liker un événement.
- Le like doit être associé à l'utilisateur et à l'événement.
- Un utilisateur ne peut liker qu'une seule fois le même événement.
- L'utilisateur peut retirer son like.

#### Consulter ses événements likés

Ajouter dans l'interface utilisateur une section permettant de consulter les événements likés.

La liste doit afficher les événements sauvegardés avec les informations essentielles :

- nom de l'événement ;
- date ;
- ville ;
- catégorie ;
- éventuellement une image.

Un clic sur un événement permet d'accéder à son détail.

### Comportement attendu

- [ ] Le bouton « J'aime » est visible sur le détail d'un événement.
- [ ] L'état du bouton indique si l'utilisateur a déjà liké l'événement.
- [ ] Cliquer sur « J'aime » ajoute l'événement aux favoris.
- [ ] Cliquer à nouveau retire le like.
- [ ] Un utilisateur ne peut pas créer plusieurs likes pour le même événement.
- [ ] Les événements likés sont accessibles depuis l'interface utilisateur.
- [ ] La liste des événements likés est persistante.
- [ ] Un événement retiré des favoris disparaît de cette liste.
- [ ] Un événement liké peut être ouvert depuis la liste.
- [ ] Un utilisateur non connecté ne peut pas enregistrer de favoris.

### Données

Créer une relation entre :

- l'utilisateur ;
- l'événement.

Exemple conceptuel :

`User ←→ Event`

via une entité/table de favoris ou de likes.

Cette relation doit être unique afin d'empêcher les doublons.

### Suppression d'un événement

Si un événement liké est annulé ou supprimé de l'affichage public :

- il ne doit plus apparaître comme événement actif ;
- la gestion de sa présence dans les favoris doit être définie proprement ;
- aucune donnée orpheline ne doit rester en base.

**Priorité :** Moyenne  
**Type :** Évolution / UX / Utilisateur / Événements

---

## LL-EF-013 — Ajouter un événement à Google Agenda

### Objectif

Permettre à un utilisateur d'ajouter facilement un événement LocalLife à son agenda Google.

### Fonctionnalité attendue

Depuis le détail d'un événement, proposer une action :

**« Ajouter à Google Agenda »**

L'action doit ouvrir Google Agenda avec les informations de l'événement préremplies.

### Informations à transmettre

Lorsque les informations sont disponibles :

- titre de l'événement ;
- description ;
- date ;
- heure de début ;
- heure de fin ;
- adresse ;
- localisation GPS si compatible ;
- informations complémentaires utiles.

### Comportement attendu

- [ ] Le bouton « Ajouter à Google Agenda » est disponible sur le détail d'un événement.
- [ ] Un clic ouvre Google Agenda.
- [ ] Les informations de l'événement sont préremplies.
- [ ] L'utilisateur conserve la possibilité de modifier les informations avant l'ajout.
- [ ] L'adresse de l'événement est correctement transmise.
- [ ] La date et les horaires sont correctement transmis.
- [ ] Le fonctionnement est compatible avec la version web.
- [ ] Le fonctionnement est prévu pour la future version mobile.

### Cas particuliers

- [ ] Si l'événement ne possède pas d'heure de fin, utiliser une durée par défaut cohérente ou ne pas renseigner l'heure de fin.
- [ ] Si l'événement ne possède pas d'adresse, ne pas générer une localisation incorrecte.
- [ ] Les événements sans informations suffisantes doivent tout de même pouvoir être ajoutés lorsque cela est possible.

### Architecture

Privilégier dans un premier temps une intégration simple via le mécanisme d'ajout d'événement de Google Agenda.

Il n'est pas nécessaire de demander l'accès au compte Google de l'utilisateur ni de stocker ses identifiants Google pour cette fonctionnalité.

L'utilisateur reste maître de l'ajout final dans son propre agenda.

**Priorité :** Moyenne  
**Type :** Évolution / UX / Intégration externe

---

## LL-EF-014 — Partager un événement

### Objectif

Permettre à un utilisateur de partager facilement un événement LocalLife avec d'autres personnes via différents moyens de communication.

### Fonctionnalité attendue

Depuis le détail d'un événement, proposer une action :

**« Partager »**

L'utilisateur peut ensuite choisir le moyen de partage disponible sur son appareil ou son navigateur.

### Moyens de partage

Prévoir notamment :

- e-mail ;
- Facebook ;
- autres réseaux sociaux compatibles ;
- applications de messagerie disponibles sur l'appareil ;
- copie du lien de l'événement.

La liste exacte des services proposés pourra évoluer.

### Informations partagées

Le partage doit contenir au minimum :

- nom de l'événement ;
- lien direct vers l'événement LocalLife.

Lorsque cela est pertinent, ajouter :

- date ;
- heure ;
- ville ;
- adresse ;
- courte description.

### Comportement attendu

- [ ] Un bouton « Partager » est disponible sur le détail d'un événement.
- [ ] L'utilisateur peut choisir un moyen de partage.
- [ ] Le lien partagé permet d'accéder directement à l'événement.
- [ ] Le titre de l'événement est utilisé dans le partage.
- [ ] Les informations principales sont correctement reprises.
- [ ] L'utilisateur peut copier directement le lien.
- [ ] Le partage fonctionne sur navigateur desktop.
- [ ] Le fonctionnement est adapté aux possibilités natives de la future application mobile.
- [ ] Si un service de partage n'est pas disponible, cela n'empêche pas les autres moyens de fonctionner.

### Architecture

Ne pas créer une implémentation spécifique et rigide pour chaque réseau social.

Prévoir une abstraction de partage permettant d'ajouter ultérieurement de nouveaux services sans modifier le fonctionnement principal de l'événement.

Lorsque le navigateur ou le système le permet, privilégier le mécanisme de partage natif afin de donner accès directement aux applications disponibles sur l'appareil.

### Sécurité et confidentialité

- [ ] Aucune donnée privée de l'utilisateur n'est incluse automatiquement dans le partage.
- [ ] Le partage ne nécessite pas de donner à LocalLife un accès aux comptes sociaux de l'utilisateur.
- [ ] Seules les informations publiques de l'événement sont partagées.

**Priorité :** Moyenne  
**Type :** Évolution / UX / Partage / Réseaux sociaux

---

## LL-EF-015 — « Y aller » vers un événement

### Objectif

Permettre à l'utilisateur de lancer facilement un itinéraire vers le lieu d'un événement depuis sa position actuelle.

### Fonctionnalité attendue

Depuis le détail d'un événement, proposer une action :

**« Y aller »**

L'action ouvre une application de navigation avec la destination de l'événement.

### Destination

Utiliser en priorité les coordonnées GPS de l'événement comme destination.

Lorsque disponible, transmettre également l'adresse de l'événement.

### Navigation

Le système doit pouvoir utiliser les applications de navigation disponibles sur l'appareil, notamment :

- Google Maps ;
- Apple Plans ;
- autres applications compatibles avec le système de navigation.

Sur mobile, privilégier l'ouverture de l'application de navigation installée.

Sur navigateur desktop, utiliser le service de cartographie disponible lorsque cela est possible.

### Comportement attendu

- [ ] Un bouton « Y aller » est disponible sur le détail d'un événement disposant d'une localisation.
- [ ] Un clic sur « Y aller » lance un itinéraire vers l'événement.
- [ ] Les coordonnées GPS de l'événement sont utilisées comme destination.
- [ ] L'adresse est également transmise lorsqu'elle est disponible.
- [ ] La position actuelle de l'utilisateur est utilisée comme point de départ lorsque le service de navigation le permet.
- [ ] L'utilisateur peut choisir son application de navigation lorsque le système le permet.
- [ ] Le fonctionnement est adapté à la future version mobile.
- [ ] Un événement sans localisation ne propose pas l'action « Y aller ».

### Architecture

LocalLife ne doit pas développer son propre moteur de navigation.

La fonctionnalité doit utiliser les mécanismes de deep-link / URL de navigation des services externes ou le système de partage/navigation natif de l'appareil.

Les coordonnées GPS de l'événement restent la source de vérité pour la destination.

### Confidentialité

LocalLife ne doit pas stocker la position actuelle de l'utilisateur uniquement pour permettre cette fonctionnalité.

La position peut être fournie directement au service de navigation utilisé par l'utilisateur.

**Priorité :** Haute  
**Type :** Évolution / UX / Géolocalisation / Navigation

---

## LL-EF-016 — Ajouter un lien web et un contact à un événement

### Objectif

Permettre d'associer à un événement des informations permettant à l'utilisateur d'obtenir davantage de renseignements ou de contacter directement l'organisateur.

### Informations facultatives

Lors de la création ou de la modification d'un événement, permettre d'ajouter :

- un lien vers le site web de l'événement ;
- un numéro de téléphone ;
- une adresse e-mail ;
- éventuellement un ou plusieurs contacts supplémentaires selon l'évolution du besoin.

Aucune de ces informations n'est obligatoire.

### Affichage dans le détail de l'événement

Lorsque les informations sont renseignées, afficher clairement :

**Site web**
- lien cliquable ;
- ouverture dans un nouvel onglet ou dans le navigateur/app approprié.

**Téléphone**
- numéro cliquable ;
- sur mobile, possibilité de lancer directement un appel.

**E-mail**
- adresse cliquable ;
- ouverture de l'application de messagerie.

Les informations non renseignées ne doivent pas générer d'élément vide dans l'interface.

### Création / modification

- [ ] Le formulaire de création permet de renseigner un site web.
- [ ] Le formulaire permet de renseigner un numéro de téléphone.
- [ ] Le formulaire permet de renseigner une adresse e-mail.
- [ ] Ces champs sont facultatifs.
- [ ] Les informations peuvent être modifiées ultérieurement selon les droits de l'utilisateur.
- [ ] Les données sont correctement persistées.

### Validation des données

- [ ] Vérifier que le lien web possède un format valide.
- [ ] Vérifier que l'adresse e-mail possède un format valide.
- [ ] Vérifier que le numéro de téléphone possède un format acceptable.
- [ ] Ne pas accepter de contenu permettant l'injection de code ou de liens dangereux.
- [ ] Les URL affichées doivent utiliser des protocoles autorisés.

### Compatibilité

Les liens et contacts doivent être utilisables :

- sur navigateur desktop ;
- sur navigateur mobile ;
- sur la future application mobile LocalLife.

### Architecture

Les coordonnées de contact doivent être stockées comme des données structurées plutôt que dans un champ texte libre unique.

Exemple conceptuel :

- `websiteUrl`
- `contactEmail`
- `contactPhone`

Cela permettra de faire évoluer ultérieurement le système vers plusieurs contacts ou plusieurs types de liens sans refonte importante.

**Priorité :** Moyenne  
**Type :** Évolution / UX / Événements / Contact

---

## LL-EF-017 — Rendre le nom utilisateur unique

### Objectif

Permettre d'identifier un utilisateur de manière unique dans LocalLife afin de pouvoir notamment le rechercher et l'ajouter comme ami.

### Fonctionnalité attendue

Chaque utilisateur possède un **nom utilisateur (username)** unique.

Ce nom est différent de l'adresse e-mail et peut être utilisé publiquement pour identifier l'utilisateur.

### Création / modification

- [ ] Un utilisateur possède un username.
- [ ] Le username est unique dans LocalLife.
- [ ] Deux utilisateurs ne peuvent pas avoir le même username.
- [ ] Le username peut être affiché publiquement.
- [ ] L'utilisateur peut modifier son username selon les règles définies.
- [ ] Une modification vérifie immédiatement la disponibilité du nouveau username.
- [ ] L'adresse e-mail reste indépendante du username.

### Contraintes

Définir des règles cohérentes pour les usernames :

- longueur minimale et maximale ;
- caractères autorisés ;
- gestion des espaces ;
- gestion de la casse ;
- usernames réservés.

La comparaison doit empêcher deux usernames considérés comme identiques d'être enregistrés avec une différence de casse.

Exemple :

`AlexBasset` et `alexbasset` doivent être considérés comme identiques.

### Sécurité

- [ ] L'unicité est garantie côté base de données.
- [ ] L'unicité est également contrôlée côté backend.
- [ ] Un username ne peut pas être usurpé par un autre utilisateur.

**Priorité :** Haute  
**Type :** Évolution / Utilisateur / Social


---

## LL-EF-018 — Rechercher et ajouter un utilisateur comme ami

### Objectif

Permettre aux utilisateurs de retrouver d'autres utilisateurs LocalLife et de leur envoyer une demande d'ami.

### Recherche

L'utilisateur doit pouvoir rechercher un autre utilisateur à partir de son username.

La recherche doit retourner uniquement les informations publiques nécessaires à l'identification de l'utilisateur.

Exemple :

> @alexbasset

### Demande d'ami

Depuis le résultat de recherche, l'utilisateur peut envoyer une demande d'ami.

Le destinataire doit pouvoir :

- accepter la demande ;
- refuser la demande.

### États d'une relation

Prévoir un système permettant de distinguer notamment :

- aucune relation ;
- demande envoyée ;
- demande reçue ;
- amis ;
- demande refusée ou annulée.

### Comportement attendu

- [ ] Un utilisateur peut rechercher un autre utilisateur par username.
- [ ] Le résultat affiche clairement l'utilisateur trouvé.
- [ ] Un utilisateur peut envoyer une demande d'ami.
- [ ] Le destinataire peut accepter la demande.
- [ ] Le destinataire peut refuser la demande.
- [ ] Une demande déjà envoyée ne peut pas être envoyée une seconde fois.
- [ ] Deux utilisateurs ne peuvent pas avoir plusieurs relations d'amitié simultanées.
- [ ] Un utilisateur ne peut pas s'ajouter lui-même.
- [ ] Une fois la demande acceptée, les deux utilisateurs sont considérés comme amis.
- [ ] Un utilisateur peut consulter sa liste d'amis.
- [ ] Un utilisateur peut retirer un ami.

### Confidentialité

La recherche par username ne doit exposer que les informations publiques du compte.

Ne jamais exposer :

- mot de passe ;
- hash du mot de passe ;
- informations d'authentification ;
- données privées du compte.

### Architecture

Créer une relation dédiée entre utilisateurs plutôt que de stocker une simple liste d'identifiants dans `User`.

Exemple conceptuel :

`User ←→ Friendship ←→ User`

La relation doit permettre de gérer proprement son état et son historique.

Prévoir une architecture suffisamment générique pour permettre ultérieurement :

- notifications de demandes d'amis ;
- partage d'événements avec ses amis ;
- événements créés par ses amis ;
- invitations à des événements ;
- autres fonctionnalités sociales.

**Priorité :** Haute  
**Type :** Évolution / Utilisateur / Social

---

## LL-EF-019 — Notification lorsqu'un ami like un événement

### Objectif

Permettre à un utilisateur d'être informé lorsqu'un de ses amis like un événement.

### Fonctionnement

Lorsqu'un utilisateur like un événement :

1. Le système vérifie si cet utilisateur possède des amis.
2. Une notification est créée pour les amis concernés.
3. La notification indique quel ami a liké quel événement.
4. L'utilisateur peut accéder directement à l'événement depuis la notification.

### Exemple

> **Alex a aimé un événement**
>
> Alex a aimé « Concert au Palais des Papes ».
>
> [Voir l'événement]

### Conditions

Une notification doit être créée uniquement lorsque :

- l'utilisateur qui like est effectivement ami avec le destinataire ;
- l'événement est accessible au destinataire.

Les utilisateurs qui ne sont pas amis ne doivent pas recevoir cette notification.

### Gestion des notifications

Prévoir une interface permettant à l'utilisateur de :

- consulter ses notifications ;
- distinguer les notifications lues et non lues ;
- ouvrir l'événement concerné depuis une notification ;
- marquer une notification comme lue.

### Comportement attendu

- [ ] Un like effectué par un utilisateur génère une notification pour ses amis.
- [ ] La notification identifie l'ami ayant effectué le like.
- [ ] La notification identifie l'événement concerné.
- [ ] Cliquer sur la notification ouvre le détail de l'événement.
- [ ] Une notification possède un état lu/non lu.
- [ ] Le nombre de notifications non lues peut être affiché dans l'interface utilisateur.
- [ ] Un utilisateur ne reçoit pas de notification pour son propre like.
- [ ] Un utilisateur qui n'est plus ami avec l'auteur du like ne reçoit pas de nouvelle notification.
- [ ] Les notifications sont persistées.

### Cas particuliers

#### Retrait du like

Le retrait d'un like ne doit pas nécessairement générer une nouvelle notification.

Si la notification existante n'a pas encore été consultée, son comportement devra être défini lors de l'implémentation.

#### Événement annulé

Si l'événement est annulé après la création de la notification :

- la notification peut rester dans l'historique ;
- le lien vers l'événement doit gérer correctement son statut.

### Architecture

Créer un système de notifications générique plutôt qu'une fonctionnalité entièrement spécifique aux likes.

Exemple conceptuel :

`Notification`

avec notamment :

- `recipient`
- `type`
- `actor`
- `event`
- `createdAt`
- `readAt`

Le type pourrait être :

`FRIEND_LIKED_EVENT`

Cette architecture permettra d'ajouter ultérieurement d'autres notifications :

- demande d'ami ;
- demande d'ami acceptée ;
- ami participant à un événement ;
- nouvel événement créé par un ami ;
- etc.

### Confidentialité

Les notifications ne doivent être envoyées qu'aux utilisateurs autorisés à connaître la relation entre l'ami et l'événement.

Aucune information privée du compte ne doit être exposée.

**Priorité :** Moyenne  
**Type :** Évolution / Social / Notifications / Événements

---

## LL-EF-020 — Participer à un événement et notifier la communauté

### Objectif

Permettre à un utilisateur d'indiquer qu'il participe à un événement et informer les utilisateurs concernés de cette participation.

### Fonctionnalité attendue

Depuis le détail d'un événement, proposer une action :

**« Je participe »**

L'utilisateur peut activer ou désactiver sa participation.

### États

Un utilisateur peut avoir au minimum les états suivants pour un événement :

- aucune participation ;
- participe.

La participation doit être indépendante du like.

Un utilisateur peut donc :

- liker un événement sans y participer ;
- participer sans le liker ;
- liker et participer.

### Notification de la communauté

Lorsqu'un utilisateur indique qu'il participe à un événement, une notification doit être générée pour les utilisateurs de sa communauté, selon les règles de visibilité définies par LocalLife.

Exemple :

> **Alex participe à un événement**
>
> Alex participe à « Concert au Palais des Papes ».
>
> [Voir l'événement]

Le fonctionnement doit être similaire au système de notification mis en place pour les likes.

### Comportement attendu

- [ ] Un utilisateur connecté peut indiquer « Je participe ».
- [ ] L'utilisateur peut annuler sa participation.
- [ ] L'état « Je participe » est clairement visible sur l'événement.
- [ ] La participation est persistée.
- [ ] Un utilisateur ne peut pas enregistrer plusieurs participations pour le même événement.
- [ ] Une participation est indépendante du like.
- [ ] Lorsqu'un utilisateur participe, la communauté concernée reçoit une notification.
- [ ] La notification identifie l'utilisateur participant.
- [ ] La notification identifie l'événement.
- [ ] Cliquer sur la notification ouvre le détail de l'événement.
- [ ] Un utilisateur ne reçoit pas de notification pour sa propre participation.
- [ ] Le retrait d'une participation ne génère pas nécessairement une notification.

### Visibilité

La notion de « communauté » devra être définie précisément lors de l'implémentation.

Elle pourra notamment correspondre :

- aux amis de l'utilisateur ;
- aux utilisateurs qui le suivent ;
- ou à une communauté LocalLife plus large.

L'implémentation devra utiliser le mécanisme retenu par le projet plutôt que de coder une règle difficilement modifiable.

### Architecture

Créer une relation dédiée entre l'utilisateur et l'événement.

Exemple conceptuel :

`User ←→ EventParticipation ←→ Event`

Ne pas réutiliser la relation de like.

Prévoir un type de notification dédié :

`FRIEND_GOING_TO_EVENT`

ou un nom équivalent adapté au modèle de notification retenu.

Le système de notification doit rester générique afin de pouvoir gérer les différents événements sociaux.

### Évolutions possibles

Cette fonctionnalité pourra ultérieurement permettre :

- de voir combien de personnes participent à un événement ;
- de voir lesquels de ses amis participent ;
- de filtrer les événements auxquels ses amis participent ;
- d'inviter des amis à participer ;
- de recevoir une notification lorsqu'un ami participe au même événement.

**Priorité :** Haute  
**Type :** Évolution / Social / Notifications / Événements

---


# À ajouter

Les prochains besoins identifiés seront ajoutés à cette section puis transformés en tickets numérotés.

## Prochains tickets

- [ ] LL-EF-017 — À définir
