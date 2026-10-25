# RGPD — mesures techniques et durées de conservation

État au 22/10/2026. Ce document décrit ce que **le code** fait des données personnelles. Il ne remplace ni la
politique de confidentialité publiée, ni le registre des traitements, ni un avis juridique : ces documents restent à
rédiger et à faire valider (voir « Reste à faire »).

## 1. Données personnelles traitées

| Données | Où | Pourquoi |
|---|---|---|
| Nom d'utilisateur, e-mail, empreinte du mot de passe (BCrypt), rôle, statut, date d'inscription | `users` | Compte |
| Consentement à l'inscription (date, version des textes) | `users.terms_accepted_at`, `terms_version` (V74) | Preuve de l'acceptation des conditions et de l'âge minimum (15 ans) |
| Dernière activité (au jour près), avertissement d'inactivité | `users.last_active_at`, `inactivity_warned_at` (V74) | Suppression des comptes inactifs |
| Identité Google (identifiant Google) | `user_identities` | Connexion avec Google |
| Ville par défaut et ses coordonnées (jamais la position de l'appareil), catégories préférées | `user_preferences`, `user_preference_category` | Personnalisation |
| Alertes (ville, rayon, catégories, canaux), historique des alertes envoyées | `activity_alert*` | Alertes |
| Favoris | `activity_likes` | Favoris |
| Jetons de notification push (plateforme, dates) | `user_push_token` | Notifications |
| Contributions (auteur affiché publiquement par son nom d'utilisateur) | `activity.created_by` | Contribution |
| Abonnements et leur historique | `subscription`, `subscription_event` | Offres, obligations comptables |
| Espace pro : demandes de gestion (lien avec le lieu, justificatifs), droits de gestion, propositions de modification, food trucks | `place_claim*`, `place_manager`, `place_change_request`, `food_truck` | Espace professionnel |
| Journaux des comptes et des actions d'administration | `account_audit_event`, `admin_action_log` | Sécurité, traçabilité (intérêt légitime) |
| Échecs de connexion (empreinte HMAC de l'adresse, jamais l'adresse) | `login_failure` | Protection contre les attaques par essais |
| Avis aux contacts des activités importées (adresse de contact publiée par la source, date d'envoi), supprimés avec l'activité | `imported_activity_contact_notice` (V75) | Ne prévenir un organisateur qu'une fois par événement, et au plus tous les 30 jours |

**Non collecté** : adresse IP (aucun journal d'accès), position de l'appareil (utilisée pour la recherche à
proximité, jamais enregistrée), outil de mesure d'audience ou traceur.

## 2. Durées de conservation (décision du 22/10/2026, `RetentionService`, chaque nuit à 3 h 45)

| Données | Durée |
|---|---|
| Jetons de réinitialisation, de confirmation d'adresse, de changement d'adresse | 7 jours après expiration ou usage |
| Comptes jamais confirmés | 30 jours après l'inscription |
| Comptes inactifs (sauf administrateurs) | prévenus par e-mail après 3 ans sans activité, supprimés 30 jours plus tard s'ils ne sont pas revenus |
| Jetons push sans activité | 6 mois |
| Historique des alertes envoyées | 6 mois |
| Justificatifs des demandes de gestion | effacés 12 mois après la décision (`PlaceClaimRetentionJob`) |
| Demandes de gestion et propositions de modification closes | 3 ans |
| Journaux des comptes et des actions d'administration | 3 ans |
| Historique des abonnements terminés | 6 ans (obligations comptables) |
| Activités à la corbeille | 30 jours |
| Échecs de connexion | 1 jour au plus |
| Sauvegardes de la base | 7 jours (serveur) et 30 jours (Backblaze B2) |

Les journaux en ajout seul (`account_audit_event`, `admin_action_log`) refusent toute modification. Depuis V74, deux
opérations y sont possibles, et seulement depuis une transaction qui l'annonce (`SET LOCAL locallife.retention = 'on'`) :
supprimer une ligne de plus de 3 ans, et effacer l'adresse d'un compte supprimé.

## 3. Droits des personnes

| Droit | Mise en œuvre |
|---|---|
| Information (art. 13) | Case à cocher à l'inscription, liens vers les conditions et la politique de confidentialité (**textes à rédiger**) |
| Accès et portabilité (art. 15, 20) | `GET /api/v1/users/me/export` : toutes les données du compte en JSON, sans secret ni adresse d'un autre compte ; bouton « Télécharger mes données » sur le site et l'application |
| Rectification (art. 16) | Profil (nom, mot de passe), changement d'adresse confirmé depuis la nouvelle adresse |
| Effacement (art. 17) | Suppression du compte par son titulaire (ou par un administrateur, avec motif) : contributions non publiées, favoris, alertes, préférences, identités Google, demandes et propositions supprimées ; activités publiées conservées sans auteur ; **adresse effacée des journaux** ; coordonnées des food trucks effacées ; abonnements détachés du compte |
| Opposition (art. 21) | Lien de désinscription dans chaque e-mail d'alerte ; notifications push désactivables par alerte |

## 4. Sécurité (art. 32)

- Mots de passe hachés (BCrypt), sessions révocables, limitation des tentatives de connexion, session administrateur
  de 8 h, mot de passe redemandé pour les actions destructrices.
- Démarrage refusé en production avec la clé de signature des sessions par défaut ou trop courte (`JwtSecretGuard`).
- Les journaux de l'application ne contiennent jamais d'adresse e-mail complète (`EmailMask`).
- HTTPS avec HSTS (Caddy).

## 5. Reste à faire (hors code)

- Rédiger et publier la politique de confidentialité, les conditions d'utilisation et les mentions légales (pages
  `/confidentialite`, `/conditions`, `/mentions-legales` prêtes, contenu « en cours de rédaction »).
- Registre des traitements (art. 30) et procédure en cas de violation de données (art. 33-34).
- Contrats de sous-traitance (art. 28) : Hetzner (hébergement, Allemagne), Backblaze B2 (sauvegardes — choisir une
  région européenne), prestataire d'envoi d'e-mails (à désigner), Expo (notifications push, États-Unis), Google
  (connexion, cartes Android, États-Unis) ; garanties de transfert hors UE (chapitre V).
- Chiffrer les sauvegardes ; politique de sécurité du contenu (CSP) sur le site.
- Recueillir le consentement des comptes créés avant V74 (aucune case n'existait).
