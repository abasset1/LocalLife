# Se connecter à la base bêta avec DBeaver

La base PostgreSQL bêta n'est **jamais** exposée sur Internet : le
conteneur `postgres` publie son port 5432 sur `127.0.0.1` du VPS
uniquement (`infra/docker-compose.beta.yml`). On y accède depuis son
poste par un **tunnel SSH**, que DBeaver ouvre lui-même.

Ne pas ouvrir le port 5432 dans le Hetzner Cloud Firewall.

## 1. Appliquer la publication du port (une fois)

Sur le VPS, après avoir récupéré la version du dépôt contenant ce
changement :

```bash
cd ~/LocalLife/infra
docker compose --env-file .env.beta -f docker-compose.beta.yml up -d postgres
ss -ltn | grep 5432   # attendu : 127.0.0.1:5432, jamais 0.0.0.0:5432
```

## 2. Créer la connexion dans DBeaver

Nouvelle connexion → **PostgreSQL**.

Onglet **SSH** (cocher « Use SSH Tunnel ») :

| Champ | Valeur |
|---|---|
| Host/IP | IP publique du VPS (ou `locallife-beta.duckdns.org`) |
| Port | `22` |
| User Name | utilisateur SSH du VPS |
| Authentication | Public Key |
| Private key | clé privée correspondant à la clé publique installée sur le VPS |

Onglet **Main** :

| Champ | Valeur |
|---|---|
| Host | `localhost` (vu depuis le VPS, à travers le tunnel) |
| Port | `5432` |
| Database | `POSTGRES_DB` de `.env.beta` (par défaut `locallife`) |
| Username | `POSTGRES_USER` de `.env.beta` (par défaut `locallife`) |
| Password | `POSTGRES_PASSWORD` de `.env.beta` |

« Test Connection », puis « Finish ».

## Prudence

C'est la base bêta réelle : préférer des requêtes en lecture. Dans
DBeaver, on peut marquer la connexion « Production » (couleur rouge,
confirmation des écritures) dans General → Connection type.
