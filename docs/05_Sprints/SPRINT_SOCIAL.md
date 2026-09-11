Sprint Social

Objectif

Ajouter une dimension sociale à LocalLife autour des utilisateurs, des amis et des événements, sans transformer l'application en réseau social généraliste.

Le cœur de LocalLife reste la découverte et la participation aux événements locaux.

---

Tickets

LS-000 — Rendre le nom utilisateur unique

Priorité : Haute
Type : Utilisateur / Social

Ajouter un "username" distinct de l'adresse email.

Le username doit :

- être unique ;
- être insensible à la casse ;
- être contrôlé côté backend ;
- être garanti au niveau base de données ;
- respecter des règles de longueur et de caractères ;
- empêcher l'utilisation de noms réservés ;
- être modifiable selon les règles définies.

Le username devient l'identifiant public permettant de rechercher un utilisateur.

---

LS-001 — Rechercher et ajouter un utilisateur comme ami

Priorité : Haute
Type : Utilisateur / Social

Permettre à un utilisateur de rechercher un autre utilisateur par son username.

Fonctionnalités :

- rechercher un utilisateur ;
- envoyer une demande d'ami ;
- accepter une demande ;
- refuser une demande ;
- annuler une demande ;
- supprimer un ami ;
- consulter sa liste d'amis.

Règles :

- impossible de s'ajouter soi-même ;
- aucune relation en double ;
- les informations privées ne doivent pas être exposées.

États minimum :

- aucune relation ;
- demande envoyée ;
- demande reçue ;
- amis ;
- demande refusée/annulée.

---

LS-002 — Liker et consulter ses événements favoris

Priorité : Moyenne
Type : Utilisateur / Événements / Social

Permettre à un utilisateur connecté de liker un événement.

Fonctionnalités :

- liker un événement ;
- retirer son like ;
- empêcher les doublons ;
- consulter ses événements likés ;
- accéder au détail d'un événement depuis la liste.

Gérer correctement les événements annulés ou supprimés.

---

LS-003 — Participer à un événement

Priorité : Haute
Type : Événements / Social

Ajouter une action « Je participe » dans le détail d'un événement.

Fonctionnalités :

- participer ;
- annuler sa participation ;
- empêcher les participations en double ;
- consulter les participations nécessaires aux fonctionnalités sociales.

La participation est indépendante du like.

Modèle recommandé :

"User ↔ EventParticipation ↔ Event"

---

LS-004 — Notifier lorsqu'un ami like un événement

Priorité : Moyenne
Type : Social / Notifications

Lorsqu'un utilisateur like un événement, ses amis concernés peuvent recevoir une notification.

La notification doit identifier :

- l'utilisateur ayant effectué l'action ;
- l'événement ;
- l'action réalisée.

Créer un modèle de notification générique.

Informations recommandées :

- destinataire ;
- type ;
- acteur ;
- événement ;
- date de création ;
- date de lecture.

Type initial :

"FRIEND_LIKED_EVENT"

Ne jamais notifier l'utilisateur lui-même.

---

LS-005 — Notifier lorsqu'un ami participe à un événement

Priorité : Haute
Type : Social / Notifications / Événements

Lorsqu'un utilisateur participe à un événement, ses amis concernés peuvent être informés.

La règle exacte de notification doit être centralisée afin de pouvoir évoluer.

La fonctionnalité doit :

- créer une notification ;
- identifier l'utilisateur ;
- identifier l'événement ;
- permettre d'accéder directement au détail ;
- éviter les notifications en double.

---

LS-006 — Voir les amis participant à un événement

Priorité : Haute
Type : Social / Événements / UX

Dans le détail d'un événement, afficher les amis de l'utilisateur qui participent.

Exemple :

«3 de vos amis participent»

Permettre éventuellement de consulter la liste des amis concernés.

Respecter les règles de confidentialité définies pour les participations.

Réutiliser "EventParticipation".

---

LS-007 — Inviter un ami à un événement

Priorité : Haute
Type : Social / Événements / Notifications

Depuis le détail d'un événement, permettre à un utilisateur d'inviter un ou plusieurs amis.

Fonctionnement :

1. sélectionner un ou plusieurs amis ;
2. envoyer une invitation ;
3. notifier le destinataire ;
4. permettre d'accepter ou de refuser.

Règles :

- seuls les amis peuvent être invités ;
- impossible de s'inviter soi-même ;
- éviter les invitations en double ;
- gérer les événements annulés.

---

LS-008 — Créer des alertes par catégorie et périmètre géographique

Priorité : Haute
Type : Notifications / Recherche / Géolocalisation / Social

Permettre à un utilisateur de créer une alerte basée sur :

- une ou plusieurs catégories ;
- une ville de référence ;
- un rayon en kilomètres.

Exemple :

«Concerts — Avignon — 30 km»

Fonctionnalités :

- créer une alerte ;
- consulter ses alertes ;
- modifier une alerte ;
- activer/désactiver ;
- supprimer une alerte.

Le système doit comparer la position géographique des événements avec le centre et le rayon de l'alerte.

Une notification doit être générée lorsqu'un nouvel événement correspond.

Un même événement ne doit pas générer plusieurs notifications pour une même alerte.

Important : les alertes doivent fonctionner aussi bien pour :

- les événements importés depuis les agendas externes ;
- les événements créés manuellement.

Ne pas effectuer un scan de toutes les alertes à chaque affichage de la carte.

---

LS-009 — Créer un fil d'activité des amis

Priorité : Moyenne
Type : Social / UX

Créer un flux simple présentant certaines actions importantes des amis.

Exemples :

- un ami aime un événement ;
- un ami participe à un événement ;
- éventuellement un ami partage un événement.

Chaque élément doit permettre d'accéder directement à l'événement concerné.

Le système ne doit pas devenir un réseau social généraliste.

---

LS-010 — Créer un profil utilisateur public

Priorité : Moyenne
Type : Social / Utilisateur / UX

Créer un profil public minimal.

Informations possibles :

- username ;
- avatar si disponible ;
- nombre d'amis ;
- événements likés selon les règles de confidentialité ;
- événements auxquels l'utilisateur participe selon les règles de confidentialité.

Ne jamais exposer :

- email ;
- informations personnelles privées ;
- données techniques ;
- informations non nécessaires.

---

LS-011 — Partager un événement

Priorité : Moyenne
Type : Social / UX / Partage

Ajouter une action « Partager » dans le détail d'un événement.

Utiliser en priorité les mécanismes de partage disponibles sur la plateforme.

Le partage doit pouvoir transmettre au minimum :

- nom de l'événement ;
- lien direct vers LocalLife.

Lorsque possible, ajouter :

- date ;
- heure ;
- ville ;
- adresse.

Le partage ne doit jamais exposer de données privées.

---

Ordre d'implémentation

1. "LS-000" — Username unique
2. "LS-001" — Amis
3. "LS-002" — Likes
4. "LS-003" — Participations
5. "LS-004" — Notifications de likes
6. "LS-005" — Notifications de participations
7. "LS-006" — Amis participants
8. "LS-007" — Invitations
9. "LS-008" — Alertes
10. "LS-009" — Fil d'activité
11. "LS-010" — Profil public
12. "LS-011" — Partage

---

Règles d'architecture

- Le social doit rester centré sur les événements.
- Les relations entre utilisateurs doivent être modélisées explicitement.
- Les permissions doivent être contrôlées côté backend.
- Les notifications doivent utiliser un modèle générique.
- Les fonctionnalités sociales doivent rester découplées autant que possible.
- Les événements importés et les événements manuels doivent rester compatibles avec les fonctionnalités sociales.
- Prévoir la compatibilité avec la future application mobile.
- Ne pas introduire prématurément de messagerie privée.

---

Fonctionnalités exclues

Ce sprint ne doit pas introduire :

- messagerie privée ;
- chat temps réel ;
- groupes communautaires ;
- commentaires sur les événements ;
- système complexe de followers ;
- réputation sociale complexe ;
- réseau social généraliste.

---

Definition of Done

Un ticket est terminé lorsque :

- le backend est fonctionnel ;
- le frontend est intégré ;
- les droits et permissions sont contrôlés côté backend ;
- les contraintes de base de données sont correctement définies ;
- les cas d'erreur principaux sont gérés ;
- les tests nécessaires sont présents ;
- aucune régression connue n'est introduite ;
- la documentation concernée est mise à jour si nécessaire.