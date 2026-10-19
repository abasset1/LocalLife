# ADR-0014 — Abonnements Premium et Pro sans paiement (BILLING-01)

**Statut : décisions prises par Alex le 19/10/2026 ; implémenté.**

Ticket : [#142 BILLING-01](https://github.com/abasset1/LocalLife/issues/142).

## Décisions

| # | Question | Décision |
|---|---|---|
| B1 | Passage à Premium ou Pro tant qu'aucun paiement n'est branché | **Demande validée par un administrateur.** Aucun prestataire, aucun prix dans le code. |
| B2 | Avantages Premium | **Aucun pour l'instant** : l'offre existe et s'affiche ; `PremiumAccessPolicy` reste ouverte à tous (consultation gratuite). |
| B3 | Droit Pro | Un abonnement Pro en cours ouvre l'espace professionnel, comme le rôle `PRO` (attribution directe) et le rôle `ADMIN`. |

## Modèle (`V67`)

- `subscription` : utilisateur, offre (`PREMIUM`/`PRO`), statut (`REQUESTED`,
  `ACTIVE`, `CANCELED`, `EXPIRED`, `REJECTED`, `WITHDRAWN`, `REPLACED`),
  période (`current_period_start`/`current_period_end`, fin nulle = sans
  échéance), `cancel_at_period_end`, champs prestataire (`provider` =
  `MANUAL`, identifiants client et abonnement, vides), décision (auteur,
  motif). Une demande en attente et un abonnement en cours au plus par
  utilisateur (index uniques partiels).
- `subscription_event` : journal de chaque transition (auteur, statuts, motif).

## Règles

- **Offre effective** (`Entitlements`, fonction pure) : l'abonnement en cours
  (actif ou résilié) donne son offre tant que sa période n'est pas échue ;
  sinon `FREE`. Les droits s'arrêtent donc au moment prévu, même avant le
  passage de la tâche d'expiration (toutes les heures).
- **Demande** → activation par un administrateur avec une date de fin
  facultative (dernier jour inclus, heure de Paris) ; elle remplace
  l'abonnement en cours éventuel (`REPLACED`). Refus avec motif obligatoire,
  visible de l'utilisateur. Retrait d'un accès par un administrateur.
- **Résiliation** par l'utilisateur : accès conservé jusqu'à la fin de la
  période ; sans période, fin immédiate. La date de fin d'accès est affichée.
- La carte, la liste et les fiches restent gratuites quelle que soit l'offre.

## API

- Utilisateur (connecté) : `GET /api/v1/users/me/subscription`,
  `POST .../request` (`{"plan":"PRO"}`), `POST .../withdraw`, `POST .../cancel`.
- Administration : `GET /api/v1/admin/subscriptions?status=`,
  `POST /api/v1/admin/subscriptions/{id}/approve` (`{"endsOn":"yyyy-MM-dd"}`
  facultatif), `/reject` (`{"reason":…}`), `/revoke`.

## Brancher un prestataire plus tard

Remplacer l'activation manuelle par le retour du prestataire : webhook signé
et idempotent qui fait passer la demande à `ACTIVE` (identifiants dans
`provider_*`), renouvellement qui avance `current_period_end`, échec de
paiement et annulation qui suivent les mêmes transitions. Ni
`ProAccessPolicy`, ni les écrans n'ont à changer.

## Clients

- Web : carte « Mon abonnement » de la page Profil ; Administration →
  Professionnels → Abonnements ; lien « Espace professionnel » pour tous les
  comptes, la page invitant à demander l'offre Pro si besoin.
- Mobile : écran « Mon abonnement » et espace professionnel complet (lieux,
  demandes de gestion, food trucks, emplacements et planning), accessibles
  depuis le menu et la rubrique Compte.
