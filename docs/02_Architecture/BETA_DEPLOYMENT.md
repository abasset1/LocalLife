# Déploiement bêta (LL-9002)

Ce document définit l'environnement de déploiement dédié à la bêta,
distinct de l'environnement de développement local. Il couvre le
choix d'hébergement, les composants nécessaires, les variables
d'environnement, la stratégie de gestion des secrets et la procédure
de déploiement.

Il ne couvre pas le déploiement effectif (LL-9003, LL-9004), ni le
détail de la sécurisation (LL-9005) : ce document prépare le terrain,
il ne l'exécute pas.

---

## Décisions retenues (validées par Alex le 27/08/2026)

* **Hébergement principal :** Oracle Cloud Infrastructure — Always
  Free (instance Ampere A1, architecture ARM/aarch64). Gratuit sans
  échéance, région Europe disponible (contrairement à l'offre
  gratuite permanente de GCP, limitée aux régions US), ressources
  largement suffisantes pour la bêta (2 OCPU / 12 Go RAM au jour de
  cette rédaction).
* **Hébergement de secours :** Hetzner CX22 (~4-5 €/mois), si
  l'inscription Oracle échoue ou est mise en revue trop longtemps.
  Architecture x86, aucune adaptation d'image nécessaire.
* **Domaine :** sous-domaine gratuit DuckDNS (ex.
  `locallife-beta.duckdns.org`), migrable plus tard sans impact sur le
  code (voir « URLs et absence de CORS » ci-dessous).
* **Stratégie retenue : un seul VPS, orchestré par Docker Compose**,
  dans la continuité directe de `infra/docker-compose.yml` (dev
  local) — pas de services managés séparés, pas de multi-serveur :
  complexité non justifiée pour une bêta à périmètre contrôlé (cf.
  `docs/AI_RULES.md`, point 6 : pas d'anticipation).

### Point de vigilance signalé (non un blocage)

L'instance Oracle ciblée est en **architecture ARM (aarch64)**, alors
que le développement local est fait en x86. Les images de base
utilisées par le projet (`eclipse-temurin`, `postgis/postgis`,
`nginx`) publient toutes des variantes `arm64` — aucun changement de
Dockerfile n'est donc a priori nécessaire, mais ce point n'a pas pu
être vérifié par un build réel depuis cette sandbox (pas d'accès à
une machine ARM). À confirmer par Alex au premier déploiement réel
(LL-9003).

---

## Composants de l'environnement bêta

```text
Internet
   │
   │ HTTPS (443)
   ▼
┌─────────────────────────────────────────────┐
│  VPS unique (Oracle Cloud Free Tier)         │
│                                               │
│   Caddy (reverse proxy + HTTPS automatique)  │
│     │                                        │
│     ├── /api/*  ──────────► backend (8080)   │
│     └── /*      ──────────► frontend (nginx) │
│                                               │
│   backend ─────────► postgres (PostGIS)      │
│                                               │
│  Docker Compose orchestre les 4 conteneurs   │
└─────────────────────────────────────────────┘
```

* **postgres** — `postgis/postgis:16-3.4` (même image qu'en local),
  volume nommé pour la persistance, **non exposé publiquement** (pas
  de mapping de port vers l'hôte, accessible uniquement par les
  autres conteneurs du réseau Docker Compose).
* **backend** — build de `backend/Dockerfile` (déjà existant),
  profil Spring `prod`.
* **frontend** — build statique (`npm run build`) servi par un
  conteneur Nginx minimal. **Nouveau composant**, absent du dépôt à
  ce jour : un `frontend/Dockerfile` est ajouté par ce ticket pour
  que la définition de l'environnement soit complète, mais sa
  construction/son test réels sont hors périmètre de LL-9002 et
  relèvent de LL-9004 (déploiement du frontend).
* **Caddy** — reverse proxy unique, point d'entrée HTTPS. Obtient et
  renouvelle automatiquement le certificat Let's Encrypt pour le
  domaine configuré (aucune configuration manuelle de certificat,
  aucun secret à gérer pour le TLS).

### URLs et absence de CORS (décision d'architecture)

Caddy sert le frontend et l'API **sous le même domaine** :
`https://<domaine>/` pour le frontend, `https://<domaine>/api/...`
pour l'API — exactement le même découpage que le proxy Vite en local
(`vite.config.js`, `"/api": "http://localhost:8080"`).

Conséquence directe : le frontend et le backend étant vus comme la
même origine par le navigateur, **aucune configuration CORS n'est
nécessaire** en bêta. Ce point, prévu comme « à traiter » dans
LL-9005, est donc résolu par ce choix d'architecture plutôt que par
du code — cohérent avec le principe « pas de complexité non
justifiée » (`docs/AI_RULES.md`).

Le frontend n'a pas non plus besoin de connaître son propre domaine :
`apiFetch`/`fetch` du frontend appellent déjà des chemins relatifs
(`/api/v1/...`), jamais d'URL absolue. Aucune variable d'environnement
de build n'est donc nécessaire côté frontend pour l'URL de l'API.

---

## Variables d'environnement

Toutes les variables ci-dessous sont fournies via un fichier `.env`
**local au VPS, jamais commité** (voir stratégie des secrets). Un
template sans valeur réelle est fourni dans
`infra/.env.beta.example`.

| Variable | Rôle | Sensible ? |
| --- | --- | --- |
| `LOCALLIFE_DOMAIN` | Domaine utilisé par Caddy pour le certificat HTTPS | Non |
| `POSTGRES_DB` / `POSTGRES_USER` | Identifiants de la base bêta | Non (identifiants), voir mot de passe ci-dessous |
| `POSTGRES_PASSWORD` | Mot de passe de la base bêta | **Oui** |
| `JWT_SECRET` | Secret de signature des JWT (`SecurityConfig`) | **Oui** |
| `LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL` | Email du premier compte ADMIN (LL-8002) | Non (mais lié au secret suivant) |
| `LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD` | Mot de passe du premier compte ADMIN | **Oui** |
| `OPENAGENDA_API_KEY` | Clé API OpenAgenda | Déjà présente en clair dans `application.properties` (LL-5006) — à sortir en variable d'environnement en bêta plutôt que reconduire cette exception |
| `OPENAGENDA_AVIGNON_CULTURE_UID` et variables `OPENAGENDA_AVIGNON_*` | Agendas Avignon configurés au Sprint 8 | Non |

Ce tableau définit les variables ; leur branchement effectif dans
`application-prod.properties` (aujourd'hui vide) est laissé à LL-9003,
pour rester dans le périmètre strictement documentaire de ce ticket.

---

## Stratégie de gestion des secrets

* **Aucun secret n'est ni n'a été ajouté au dépôt Git** — `.env.beta`
  réel n'existe que sur le VPS, créé manuellement par Alex lors du
  déploiement (LL-9003), à partir du template
  `infra/.env.beta.example` (valeurs placeholder uniquement).
* `infra/.env.beta.example` est ajouté à `.gitignore` sous son nom
  réel (`infra/.env.beta`) pour empêcher un commit accidentel, sur le
  même principe que `backend/.env.example` existant.
* Permissions restrictives recommandées sur le VPS :
  `chmod 600 .env.beta` (lecture/écriture propriétaire uniquement).
* `JWT_SECRET` et `POSTGRES_PASSWORD` : générés pour la bêta
  (`openssl rand -base64 32`, déjà la méthode documentée dans
  `backend/.env.example`), distincts de toute valeur utilisée en
  local — un secret de dev n'est jamais réutilisé en bêta.
* `LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD` : défini une seule fois au
  premier démarrage (le bootstrap ne recrée jamais de compte ADMIN
  existant, cf. `AdminBootstrapRunner`), peut être retiré du `.env`
  après la création du premier compte.
* Aucun secret dans les logs applicatifs : comportement déjà en place
  côté backend (aucun mot de passe ni token n'est journalisé par le
  code existant, ni par Caddy par défaut) — à vérifier concrètement
  en conditions réelles lors de LL-9005.

---

## Procédure de déploiement (résumé)

Le détail opérationnel (commandes exactes, dépannage) est développé
lors de l'exécution réelle en LL-9003/LL-9004. Ce résumé fixe la
séquence attendue :

1. Créer l'instance Oracle Cloud Free Tier (Ampere A1, Ubuntu, région
   Europe), ouvrir les ports 80/443 dans les règles réseau (Security
   List / NSG).
2. Installer Docker et Docker Compose sur l'instance.
3. Pointer le sous-domaine DuckDNS vers l'IP publique de l'instance.
4. Cloner le dépôt sur le VPS (lecture seule, pas de clé de
   déploiement à privilèges élevés).
5. Créer `infra/.env.beta` sur le VPS à partir du template, avec des
   valeurs générées pour la bêta (jamais copiées depuis le dev
   local).
6. Lancer `docker compose -f infra/docker-compose.beta.yml up -d
   --build`.
7. Vérifier le health check backend (`GET /actuator/health`, déjà
   exposé via `management.endpoints.web.exposure.include=health,info`)
   et l'accès HTTPS au frontend.

---

## Environnement distinct du développement local

* Base de données bêta séparée (volume Docker dédié sur le VPS,
  jamais partagé avec le Postgres local).
* Secrets bêta générés spécifiquement, jamais réutilisés du `.env`
  local.
* Profil Spring dédié : `prod` (déjà prévu dans
  `application-prod.properties`, actuellement vide — sera complété en
  LL-9003 avec les seules différences nécessaires par rapport au
  profil `local`).
* Agendas OpenAgenda : mêmes agendas Avignon qu'en local (validés au
  Sprint 8), mais alimentant la base bêta, pas la base locale.
