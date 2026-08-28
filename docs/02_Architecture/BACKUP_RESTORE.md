# Sauvegarde et restauration — bêta (LL-9004)

Répond aux deux derniers critères de la catégorie 8 (« Base de
données ») de l'audit sécurité LL-9004 : **sauvegarde minimale
disponible** et **procédure de restauration documentée**.

Décision validée par Alex le 28/08/2026 : sauvegarde locale **et**
copie hors-site sur Backblaze B2 (offre gratuite, 10 Go — largement
suffisant pour une base bêta de ce volume).

Ce document ne couvre pas le choix d'hébergement du VPS lui-même
(Oracle Cloud / Hetzner CX22 selon disponibilité, voir
`BETA_DEPLOYMENT.md`) : la procédure ci-dessous est indépendante du
fournisseur, elle ne suppose qu'un VPS avec Docker Compose et
`infra/docker-compose.beta.yml` déjà démarré.

---

## Principe

* `infra/backup.sh` : dump `pg_dump` compressé (`--clean --if-exists`,
  pour permettre une restauration directe sur une base non vide),
  écrit dans `infra/backups/` (jamais commité, voir `.gitignore`), puis
  copié vers un bucket Backblaze B2 via `rclone`.
* Rotation : 7 derniers jours conservés en local, 30 jours sur B2.
* Déclenché par une tâche cron quotidienne sur le VPS (pas de nouveau
  conteneur ni de service dédié — cohérent avec le principe « pas de
  complexité non justifiée », `docs/AI_RULES.md`).
* Les identifiants B2 (`Account ID` / `Application Key`) ne transitent
  **jamais** par `.env.beta` ni par le dépôt : ils vivent uniquement
  dans la configuration `rclone` du VPS
  (`~/.config/rclone/rclone.conf`), créée une fois manuellement à
  l'étape 2 ci-dessous.

---

## Mise en place (une seule fois, sur le VPS)

### 1. Créer le bucket B2

* Créer un compte sur https://www.backblaze.com/cloud-storage (offre
  gratuite).
* Créer un bucket **privé** dédié (ex. `locallife-beta-backups`).
* Créer une **Application Key restreinte à ce seul bucket** (pas la
  clé maître du compte) — principe de moindre privilège, cohérent avec
  le reste de l'audit LL-9004.

### 2. Installer et configurer rclone sur le VPS

```bash
curl https://rclone.org/install.sh | sudo bash
rclone config
# Choisir "n" (new remote), nom du remote : "b2"
# Type : "b2" (Backblaze B2)
# Renseigner account (Account ID) et key (Application Key) créées à l'étape 1
# Laisser les autres options par défaut
```

Vérifier :

```bash
rclone lsd b2:
# doit lister le bucket créé, sans erreur d'authentification
```

`rclone.conf` contient les identifiants B2 en clair : il n'est protégé
que par les permissions du système de fichiers. Vérifier ses
permissions :

```bash
chmod 600 ~/.config/rclone/rclone.conf
```

### 3. Renseigner le nom du bucket dans `.env.beta`

```bash
# dans infra/.env.beta, déjà présent sur le VPS depuis LL-9003
B2_BUCKET_NAME=locallife-beta-backups
```

### 4. Tester une sauvegarde manuelle

```bash
cd infra
./backup.sh
```

Vérifier la sortie (dump local créé, copie B2 confirmée), puis :

```bash
rclone ls b2:locallife-beta-backups/
# doit lister le fichier locallife-beta-<timestamp>.sql.gz tout juste créé
```

### 5. Planifier la tâche cron

```bash
crontab -e
```

Ajouter (sauvegarde quotidienne à 3h du matin, heure du VPS) :

```cron
0 3 * * * cd /home/<utilisateur>/LocalLife/infra && ./backup.sh >> /var/log/locallife-backup.log 2>&1
```

Adapter le chemin `/home/<utilisateur>/LocalLife` à l'emplacement réel
du clone sur le VPS (voir étape 5 de la procédure de déploiement dans
`BETA_DEPLOYMENT.md`).

---

## Procédure de restauration

À utiliser en cas de perte/corruption de la base bêta. **Détruit
l'état actuel de la base** (le dump contient les `DROP` nécessaires) :
ne jamais l'exécuter sans être certain de vouloir revenir à l'état du
dump choisi.

### 1. Récupérer le dump à restaurer

Depuis le VPS lui-même, si le dump local existe encore :

```bash
ls infra/backups/
```

Sinon, depuis B2 :

```bash
rclone copy b2:locallife-beta-backups/locallife-beta-<timestamp>.sql.gz infra/backups/
```

### 2. Arrêter le backend

Évite que l'application écrive dans la base pendant la restauration :

```bash
docker compose -f infra/docker-compose.beta.yml stop backend
```

### 3. Restaurer le dump

```bash
gunzip -c infra/backups/locallife-beta-<timestamp>.sql.gz \
  | docker compose -f infra/docker-compose.beta.yml exec -T postgres \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}"
```

(`POSTGRES_USER`/`POSTGRES_DB` : valeurs de `infra/.env.beta`, à
exporter au préalable ou à substituer directement.)

### 4. Redémarrer le backend

```bash
docker compose -f infra/docker-compose.beta.yml start backend
```

### 5. Vérifier

```bash
curl -i https://<domaine bêta>/actuator/health
# attendu : 200, {"status":"UP"}
```

Puis un contrôle applicatif simple (ex. recherche d'activités sur la
carte) pour confirmer que les données restaurées sont cohérentes.

---

## Non vérifié depuis cette sandbox

* Aucun accès à un VPS réel ni à un compte Backblaze B2 : le script et
  cette procédure ont été rédigés et relus avec soin, mais **jamais
  exécutés en conditions réelles** (création du bucket, `rclone
  config`, cron effectif, restauration réelle). `pg_dump --clean
  --if-exists` a été vérifié par lecture de la documentation
  PostgreSQL, pas par un test contre une base bêta réelle.
* Recommandation avant validation définitive de LL-9004 : exécuter une
  fois `./backup.sh` puis la procédure de restauration complète sur le
  VPS bêta (ou un environnement de test équivalent), pour confirmer
  que les deux fonctionnent effectivement de bout en bout.
