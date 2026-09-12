Sprint Évol-Fix

Objectif

Améliorer l'expérience utilisateur, la robustesse et les fonctionnalités générales de LocalLife sans introduire de fonctionnalités sociales.

Les fonctionnalités sociales sont regroupées dans "SPRINT_SOCIAL.md".

---

Tickets

LL-EF-001 — Revoir l'affichage de la saisie d'une activité

Priorité : Haute
Type : Évolution / UX

- La création d'une activité nécessite d'être connecté.
- Le formulaire s'ouvre dans une nouvelle fenêtre/vue.
- Le formulaire doit être clair et structuré.
- Un utilisateur non connecté ne peut pas créer d'activité.
- La fermeture du formulaire doit fonctionner correctement.
- Le comportement doit rester cohérent avec l'authentification existante.

---

LL-EF-002 — Utiliser automatiquement la localisation

Priorité : Haute
Type : Évolution / UX / Carte

- Supprimer le bandeau « Utiliser la localisation ».
- Utiliser automatiquement la position disponible de l'utilisateur.
- Centrer la carte automatiquement lorsque la position est disponible.
- Gérer proprement le refus ou l'indisponibilité de la localisation.
- Ne jamais bloquer l'utilisation de LocalLife si la localisation n'est pas disponible.

---

LL-EF-003 — Revoir le rechargement de la carte

Priorité : Haute
Type : Évolution / UX / Performance

Le déplacement actuel de la carte provoque un rechargement visuel désagréable.

Objectifs :

- conserver la carte affichée pendant les déplacements ;
- éviter les coupures visuelles ;
- mettre à jour les marqueurs indépendamment du rendu de la carte ;
- éviter les appels API inutiles lors des déplacements rapides ;
- gérer correctement déplacement et zoom ;
- charger les nouvelles données en arrière-plan lorsque nécessaire.

Critère principal : l'utilisateur doit pouvoir déplacer et zoomer la carte sans avoir l'impression que celle-ci est entièrement reconstruite.

---

LL-EF-004 — Créer une interface d'administration

Priorité : Haute
Type : Évolution / Administration

Créer une interface réservée aux administrateurs permettant notamment :

- consulter les activités soumises par les utilisateurs ;
- consulter leur détail ;
- approuver une activité ;
- rejeter une activité ;
- consulter les activités selon leur statut ;
- visualiser clairement leur état de modération.

Le contrôle des droits doit être effectué côté backend et ne doit pas reposer uniquement sur le frontend.

Statuts minimum recommandés :

- "PENDING"
- "APPROVED"
- "REJECTED"

---

LL-EF-005 — Gérer les agendas depuis l'interface d'administration

Priorité : Haute
Type : Évolution / Administration

Permettre à un administrateur de :

- consulter les agendas/sources ;
- ajouter un agenda ;
- modifier un agenda si nécessaire ;
- supprimer un agenda ;
- confirmer une suppression.

La suppression doit être cohérente avec les activités déjà importées depuis cet agenda.

Un agenda peut représenter notamment une source externe telle qu'un flux RSS.

Important : ne pas recréer un système d'import RSS. L'architecture d'import existante doit être réutilisée.

---

LL-EF-006 — Créer une interface utilisateur

Priorité : Haute
Type : Évolution / UX / Compte utilisateur

Créer une interface permettant à l'utilisateur connecté de :

- consulter son compte ;
- consulter ses informations personnelles ;
- modifier les informations autorisées ;
- se déconnecter ;
- accéder aux futures fonctionnalités liées au compte.

L'accès aux données personnelles doit être protégé côté backend.

---

LL-EF-007 — Réinitialisation du mot de passe

Priorité : Haute
Type : Évolution / Authentification / Sécurité

Implémenter un parcours « Mot de passe oublié ».

Le système doit :

- permettre de demander une réinitialisation ;
- envoyer un lien sécurisé ;
- utiliser un token temporaire ;
- rendre le token utilisable une seule fois ;
- permettre de définir un nouveau mot de passe ;
- ne pas révéler si une adresse email existe ;
- ne jamais exposer le token dans les logs ;
- conserver uniquement le mot de passe sous forme sécurisée.

---

LL-EF-008 — Ajouter une liste des activités

Priorité : Haute
Type : Évolution / UX / Affichage

Ajouter une vue liste alternative à la carte.

Accès :

- bouton en haut à droite ;
- dans la même zone que « Filtrer par catégorie ».

La liste doit :

- regrouper ou trier les activités par ville ;
- trier les activités par date ;
- afficher les informations essentielles ;
- permettre d'accéder au détail ;
- conserver les filtres existants ;
- rester utilisable sur mobile.

Informations minimales :

- nom ;
- ville ;
- date ;
- catégorie si disponible.

---

LL-EF-009 — Afficher l'adresse d'une activité

Priorité : Moyenne
Type : Évolution / UX / Géolocalisation

L'activité possède déjà des coordonnées GPS issues du géocodage de son adresse.

Ajouter un reverse geocoding permettant d'obtenir une adresse lisible à partir des coordonnées.

L'adresse doit être visible dans le détail d'une activité.

Les coordonnées GPS restent la source de vérité.

Prévoir une gestion propre des erreurs du service de reverse geocoding.

---

LL-EF-010 — Identifier et annuler les activités créées manuellement

Priorité : Haute
Type : Évolution / Activités / Droits utilisateur

Concerne uniquement les activités créées manuellement par les utilisateurs.

Ne concerne pas les activités issues des agendas/imports externes.

Objectifs :

- identifier le créateur d'une activité ;
- permettre au créateur d'annuler son activité ;
- permettre à un administrateur de l'annuler ;
- demander confirmation avant annulation ;
- conserver l'historique plutôt que supprimer physiquement l'activité.

Statut recommandé :

"CANCELLED"

 ### Droits
 
- [x] Seul le créateur peut annuler son activité.
- [x] Un administrateur peut également annuler une activité.
- [x] Un autre utilisateur ne peut pas annuler l'activité.
- [x] Les contrôles de permission sont effectués côté backend.

### Critères d'acceptation

- [x] Une activité saisie manuellement possède une référence vers son créateur.
- [x] Le créateur est identifiable depuis le détail de l'activité.
- [x] Le créateur peut annuler son activité.
- [x] Une confirmation est demandée avant l'annulation.
- [x] Une activité annulée n'est plus affichée comme activité active sur la carte.
- [x] Une activité annulée n'apparaît plus dans la liste des activités actives.
- [x] L'activité reste conservée en base.
- [x] Un administrateur peut annuler une activité.
- [x] Un utilisateur quelconque ne peut pas annuler l'activité d'un autre utilisateur.
- [x] Les activités provenant des collectors ne sont pas concernées par cette fonctionnalité.
---

LL-EF-011 — Créer un compte via Google

Priorité : Moyenne
Type : Évolution / Authentification / Sécurité

Ajouter une authentification Google basée sur OAuth2/OpenID Connect.

Le système doit :

- proposer « Continuer avec Google » ;
- créer automatiquement un compte LocalLife si nécessaire ;
- associer correctement un compte existant ;
- utiliser l'email vérifié fourni par Google ;
- ne jamais stocker de mot de passe Google ;
- conserver une architecture permettant d'ajouter d'autres fournisseurs plus tard.

La vérification de l'identité doit être réalisée côté backend.

---

LL-EF-013 — Ajouter un événement à Google Agenda

Priorité : Moyenne
Type : Évolution / UX / Intégration externe

Ajouter une action « Ajouter à Google Agenda » dans le détail d'un événement.

Préremplir autant que possible :

- titre ;
- description ;
- date ;
- heure ;
- heure de fin ;
- adresse ;
- coordonnées GPS si pertinentes.

L'utilisateur doit pouvoir modifier les informations avant l'ajout.

Ne pas stocker les identifiants Google de l'utilisateur.

---

LL-EF-015 — « Y aller » vers un événement

Priorité : Haute
Type : Évolution / UX / Géolocalisation / Navigation

Ajouter une action « Y aller » dans le détail d'un événement.

La destination doit utiliser :

1. les coordonnées GPS si disponibles ;
2. l'adresse en solution de secours.

Ouvrir l'application de navigation compatible de l'utilisateur.

LocalLife ne doit pas implémenter son propre système de navigation.

---

LL-EF-016 — Ajouter un lien web et un contact à un événement

Priorité : Moyenne
Type : Évolution / UX / Événements / Contact

Permettre à un événement de posséder :

- une URL ;
- un numéro de téléphone ;
- une adresse email.

Ces informations doivent être accessibles depuis le détail de l'événement.

Prévoir :

- validation des URLs ;
- validation des emails ;
- validation des téléphones ;
- liens sécurisés ;
- appel téléphonique sur mobile ;
- ouverture du client email.

Prévoir une structure suffisamment propre pour permettre plusieurs contacts ultérieurement.

---

Fonctionnalités déplacées vers Sprint Social

Les tickets suivants ne font plus partie de ce sprint :

- "LL-EF-012" — Liker et consulter ses événements favoris
- "LL-EF-014" — Partager un événement
- "LL-EF-017" — Rendre le nom utilisateur unique
- "LL-EF-018" — Rechercher et ajouter un utilisateur comme ami
- "LL-EF-019" — Notification lorsqu'un ami like un événement
- "LL-EF-020" — Participer à un événement et notifier la communauté
- "LL-EF-021" — Créer des alertes par catégorie et périmètre géographique

Ils sont regroupés dans "SPRINT_SOCIAL.md".

---

Règles

- Réutiliser l'architecture existante.
- Ne pas recréer les mécanismes d'import déjà présents.
- Ne pas introduire de dépendance inutile.
- Toute sécurité importante doit être contrôlée côté backend.
- Les fonctionnalités doivent rester compatibles avec la future version mobile.
- Préserver la compatibilité avec les activités importées et les activités créées manuellement.

---

Definition of Done

Un ticket est terminé lorsque :

- le backend est fonctionnel ;
- le frontend est intégré ;
- les droits d'accès sont vérifiés ;
- les cas d'erreur principaux sont gérés ;
- les tests nécessaires sont présents ;
- aucune régression connue n'est introduite ;
- la documentation concernée est mise à jour si nécessaire.
