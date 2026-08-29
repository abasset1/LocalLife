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

* **Hébergement retenu : Hetzner CX22** (ou son équivalent renommé —
  voir note ci-dessous), ~4,35-5,99 €/mois selon la période. Décidé
  le 27/08/2026 : Oracle Cloud Free Tier, initialement retenu en
  principal, s'est révélé **indisponible** (pas de capacité ARM
  allouable à l'inscription — risque déjà identifié lors du choix
  initial). Bascule sur le plan de secours déjà validé le même jour,
  sans nouvelle décision d'architecture à prendre : Oracle n'est plus
  poursuivi pour ce déploiement.
  Architecture x86 (Intel/AMD), 2 vCPU / 4 Go RAM, 40 Go NVMe, 20 To
  de trafic inclus — largement suffisant pour la bêta, et sans
  l'incertitude de compatibilité ARM d'Oracle (voir ancien « point de
  vigilance » ci-dessous, devenu sans objet).
  ⚠️ Le nom exact du plan a pu changer entre la rédaction de ce
  document et le déploiement réel (des sources évoquent un possible
  renommage CX22 → CX23 chez Hetzner courant 2026) : vérifier le nom
  et les caractéristiques exactes dans la console Hetzner au moment
  de la création plutôt que de se fier uniquement à ce document.
* **Domaine :** sous-domaine gratuit DuckDNS (ex.
  `locallife-beta.duckdns.org`), migrable plus tard sans impact sur le
  code (voir « URLs et absence de CORS » ci-dessous).
* **Stratégie retenue : un seul VPS, orchestré par Docker Compose**,
  dans la continuité directe de `infra/docker-compose.yml` (dev
  local) — pas de services managés séparés, pas de multi-serveur :
  complexité non justifiée pour une bêta à périmètre contrôlé (cf.
  `docs/AI_RULES.md`, point 6 : pas d'anticipation).

### Point de vigilance ARM — sans objet depuis la bascule Hetzner

Ce document ciblait initialement une instance Oracle en architecture
ARM (aarch64), ce qui aurait nécessité de vérifier la compatibilité
`arm64` des images Docker du projet. Hetzner CX22 étant en
architecture x86 standard, **ce point de vigilance ne s'applique
plus** : les images (`eclipse-temurin`, `postgis/postgis`, `nginx`,
`node`) sont utilisées sans adaptation, comme en développement local.

---

## Composants de l'environnement bêta

```text
Internet
   │
   │ HTTPS (443)
   ▼
┌─────────────────────────────────────────────┐
│  VPS unique (Hetzner CX22)                   │
│                                               │
│   Caddy (reverse proxy + HTTPS automatique)  │
│     │                                        │
│     ├── /api/*       ─────► backend (8080)   │
│     ├── /actuator/*  ─────► backend (8080)   │
│     └── /*           ─────► frontend (nginx) │
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
| `OPENAGENDA_API_KEY` | Clé API OpenAgenda | **Oui** — externalisée en LL-9004 (`application.properties` contenait auparavant cette clé en clair, exposée publiquement dans l'historique Git ; la clé doit être révoquée/régénérée sur le portail OpenAgenda avant tout déploiement bêta) |
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

### Constat signalé — données de démo (`V3__insert_demo_activities.sql`)

Les migrations Flyway s'exécutent automatiquement au premier démarrage
du backend, y compris en bêta. `V3__insert_demo_activities.sql`
(Sprint 1) insère 5 activités de démonstration situées à **Marseille**
(pas Avignon) avec des dates majoritairement déjà **passées** au
27/08/2026 — elles resteront donc invisibles des recherches publiques
grâce au filtre LL-9001, mais visibles en consultation administrative
(`findByStatus`) et présentes en base.

Non corrigé ici : modifier une migration déjà appliquée dans d'autres
environnements casserait la validation de checksum Flyway
(`docs/AI_RULES.md`, ne jamais modifier une décision déjà actée sans
raison). Une nouvelle migration (`V14__...`) pour retirer ces lignes
en bêta serait possible mais constitue un choix de contenu de données,
pas une tâche d'infrastructure — hors périmètre de LL-9003/9002. À
trancher explicitement par Alex si souhaité.

## Procédure de déploiement — LL-9003 (backend + base de données)

Mode opératoire détaillé pour ce ticket : rendre l'API et PostgreSQL/
PostGIS opérationnels sur le VPS bêta, accessibles depuis Internet en
HTTPS. Le frontend (`frontend/Dockerfile`) est construit et démarré
dans le même mouvement (`docker compose up` sur l'ensemble des
services définis dans `docker-compose.beta.yml`) car Caddy route déjà
`/*` vers lui — mais sa validation fonctionnelle (inscription,
connexion, carte, contribution) reste le périmètre de LL-9004, pas de
celui-ci.

### 1. Créer l'instance Hetzner

* Console Hetzner Cloud → New Project (si pas déjà fait) → Add
  Server.
* Location : région Europe la plus proche (ex. Falkenstein ou
  Nuremberg, Allemagne).
* Image : **Ubuntu 24.04**.
* Type : **CX22** — vérifier le nom exact dans la console au moment
  de la création (voir note ci-dessus sur un possible renommage en
  CX23), viser ~2 vCPU / 4 Go RAM.
* Ajouter une clé SSH publique à la création (pas de mot de passe).
* Activer le **Hetzner Cloud Firewall** dans le même flux de
  création : autoriser TCP 22 (SSH, idéalement restreint à l'IP
  d'Alex), 80 et 443 (0.0.0.0/0).
* Noter l'adresse IP publique attribuée.

### 2. Ouvrir les ports 80/443

Contrairement à Oracle Cloud, Hetzner n'ajoute pas de règles
`iptables` bloquantes par défaut sur les images Ubuntu standard : le
**Hetzner Cloud Firewall** configuré à l'étape 1 suffit normalement.
À vérifier tout de même après le premier démarrage :

```bash
sudo iptables -L -n   # doit être permissif (ACCEPT par défaut) ou vide
```

Si une règle bloquante apparaît malgré tout (rare, dépend de l'image),
l'ouvrir comme pour Oracle :

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

### 3. Installer Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# se reconnecter en SSH pour que le groupe soit pris en compte
docker compose version   # vérifie le plugin Compose (inclus depuis get.docker.com)
```

### 4. Configurer le sous-domaine DuckDNS

* Créer un compte sur https://www.duckdns.org, créer le sous-domaine
  (ex. `locallife-beta`), le faire pointer vers l'IP publique de
  l'instance.
* Vérifier la propagation : `dig +short locallife-beta.duckdns.org`
  doit renvoyer l'IP de l'instance.

### 5. Cloner le dépôt

```bash
git clone https://github.com/abasset1/LocalLife.git
cd LocalLife
```

(dépôt public, pas de clé de déploiement nécessaire.)

### 6. Créer les secrets bêta

```bash
cd infra
cp .env.beta.example .env.beta
chmod 600 .env.beta
# éditer .env.beta et remplacer chaque "changez_moi" par une valeur réelle :
openssl rand -base64 32   # à exécuter deux fois : POSTGRES_PASSWORD, JWT_SECRET
```

`LOCALLIFE_DOMAIN` = `locallife-beta.duckdns.org` (ou le sous-domaine
choisi). `OPENAGENDA_API_KEY` : **ne pas réutiliser l'ancienne clé**
(compromise, exposée publiquement dans l'historique Git — voir
LL-9004) — utiliser la nouvelle clé régénérée sur le portail
OpenAgenda.

### 7. Démarrer la pile

```bash
docker compose -f docker-compose.beta.yml up -d --build
docker compose -f docker-compose.beta.yml ps
```

### 8. Vérifier les critères d'acceptation LL-9003

```bash
# Health check, en HTTPS via Caddy :
curl -i https://locallife-beta.duckdns.org/actuator/health
# attendu : 200, {"status":"UP"}

# Authentification (inscription) :
curl -i -X POST https://locallife-beta.duckdns.org/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"motdepassetest"}'
# attendu : 201 Created

# Absence de secret dans les logs :
docker compose -f docker-compose.beta.yml logs backend postgres caddy | grep -iE "password|secret|jwt_secret"
# attendu : aucune valeur de secret affichée (au pire le nom de la variable, jamais sa valeur)
```

### Non réalisable depuis cette sandbox

Toutes les étapes ci-dessus nécessitent l'accès réel à l'instance
Hetzner (SSH) et à la console Hetzner Cloud, indisponibles depuis
cette sandbox. Ce mode opératoire a été rédigé et vérifié sur la base
de la documentation Hetzner/Docker/DuckDNS, mais son exécution et la
confirmation effective des critères d'acceptation restent à la charge
d'Alex.

---

## Procédure de déploiement — LL-9004 (frontend)

Sera détaillé lors du traitement de ce ticket : validation
fonctionnelle des parcours (inscription, connexion, carte, recherche,
contribution) sur le frontend déjà démarré à l'étape 7 ci-dessus, et
nettoyage de toute configuration de développement résiduelle
(`vite.config.js` proxy, notamment — actif uniquement en `npm run
dev`, sans effet sur le build de production, mais à confirmer).

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
