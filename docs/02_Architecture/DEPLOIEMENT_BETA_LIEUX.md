# Déploiement bêta — lieux permanents (migrations V61 à V63)

Guide pas à pas pour mettre en bêta tout ce qui a été fusionné autour de
l'ADR-0011 :
- précision des positions (V61) ;
- lieux permanents (V62) ;
- liens entre lieux d'événement et lieux permanents (V63) ;
- fiche et liste des lieux ;
- écrans d'administration « Lieux », « Rapprochements » et « Doublons de lieux » ;
- réutilisation des lieux d'événement à l'import (doublons, option A).

**Où taper les commandes** : dans la session SSH du serveur bêta (Linux),
depuis le dossier `infra/` du dépôt. **Pas dans PowerShell sur Windows.**
Aucune variable n'est à préparer : les commandes qui touchent la base
passent par le conteneur PostgreSQL, qui connaît déjà ses identifiants.
Aucune commande n'affiche de mot de passe ni de clé.

Durée : environ 15 minutes, dont 5 à 10 minutes de reconstruction des
images. Le site est indisponible une à deux minutes, au redémarrage du
backend.

Commandes vérifiées le 16/10/2026 sur une base locale PostgreSQL/PostGIS 16 :
diagnostic, sauvegarde, restauration, contrôle d'après déploiement. Elles
n'ont pas été exécutées sur le serveur bêta, auquel l'environnement de
développement n'a pas accès.

## 0. Se placer au bon endroit

```bash
cd ~/LocalLife/infra        # adapter si le dépôt a été cloné ailleurs
git status --short          # attendu : rien, ou seulement des fichiers non suivis
git log --oneline -1        # noter cette ligne : c'est la version actuelle
```

Si `git status` montre des fichiers modifiés et suivis, ne pas continuer :
quelqu'un a changé le code directement sur le serveur.

## 1. Vérifier la configuration

```bash
docker compose --env-file .env.beta -f docker-compose.beta.yml config -q && echo "configuration complète"
```

Attendu : `configuration complète`. Sinon, le message nomme la variable
manquante dans `.env.beta`, souvent `SMTP_HOST`, `SMTP_USERNAME`,
`SMTP_PASSWORD` ou `MAIL_FROM`. La compléter avec `nano .env.beta`, puis
relancer la commande.

`OPENAGENDA_API_KEY` : n'y mettre qu'une clé **neuve**. Les clés déjà
exposées (dans une conversation ou dans l'historique Git, y compris
`infra/.env.beta.example`) sont à désactiver dans le compte OpenAgenda.

## 2. Relever l'état actuel de la base (lecture seule)

```bash
docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < verification/v60_diagnostic.sql
```

Noter :
- le **verdict**, la marche à suivre selon le verdict étant dans
  `DATAGOUV_SOURCES.md`, « Migration V60 » ;
- le **nombre d'activités par statut**, à comparer à l'étape 5.

La source DATAtourisme de V60 doit être `INACTIVE` si vous ne voulez pas
d'import horaire depuis ce fichier. Sinon, la passer à `INACTIVE` dans
Administration > Agendas (liste des sources) avant de continuer.

## 3. Sauvegarder la base

```bash
mkdir -p backups
docker exec locallife-postgres-beta sh -c 'pg_dump --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  | gzip > backups/avant-v61-v63.sql.gz
ls -lh backups/avant-v61-v63.sql.gz
```

Attendu : un fichier de plus de quelques kilo-octets. S'il fait moins de
1 Ko, la sauvegarde a échoué : **ne pas continuer**.

Si la sauvegarde hors site est configurée (`BACKUP_RESTORE.md`),
`./backup.sh` fait la même chose et copie en plus le fichier vers B2.

## 4. Mettre à jour et redémarrer

```bash
git checkout main
git pull origin main
git log --oneline -1                                   # nouvelle version
docker compose --env-file .env.beta -f docker-compose.beta.yml up -d --build
docker compose --env-file .env.beta -f docker-compose.beta.yml ps           # tous les services « Up »
```

Au démarrage, le backend applique de lui-même les migrations V61, V62 et
V63 (Flyway). Pour suivre le démarrage :

```bash
docker compose --env-file .env.beta -f docker-compose.beta.yml logs --tail=200 backend | grep -iE "migrat|flyway|started|error"
```

Attendu : des lignes « Migrating schema … to version 61 / 62 / 63 »
(absentes si elles étaient déjà appliquées), puis « Started … ». Aucune
ligne `ERROR`.

## 5. Contrôler

```bash
curl -s https://locallife-beta.duckdns.org/actuator/health    # votre domaine bêta ; attendu : {"status":"UP"}
docker exec -i locallife-postgres-beta sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < verification/apres_deploiement_v61_v63.sql
```

Le script de contrôle est en lecture seule. Attendu :

| Bloc | Attendu |
|---|---|
| 1. Migrations | V60 à V63, `success = t` |
| 2. Sources | « DATAtourisme PACA — lieux », type `DATATOURISME_LIEUX`, `INACTIVE` |
| 3. Précision | lieux et activités en `UNKNOWN`, quelques-uns en `APPROXIMATE` (« Divers lieux », « Centre-ville »…) |
| 4. Lieux permanents | aucune ligne |
| 5. Liens | 0 |
| 6. Activités par statut | **mêmes nombres qu'à l'étape 2** |

Puis, dans un navigateur :
- la carte s'affiche et les activités apparaissent ;
- le bouton « 🏛 Lieux » existe ; il reste vide tant qu'aucun lieu n'est publié ;
- Administration : les onglets « Lieux », « Rapprochements » et
  « Doublons de lieux » s'ouvrent.

## 6. Importer et publier les lieux (depuis l'administration)

Aucune commande : tout se fait dans Administration > Lieux.

1. **Essai à blanc** sur la source « DATAtourisme PACA — lieux ».
   Attendu, avec le fichier du 13/10/2026 :
   - environ 32 000 lieux lus ;
   - environ 4 800 retenus par la liste blanche ;
   - verdict « Réussi » ;
   - **rien n'est écrit**.
2. **Activer après vérification** : relance un essai à blanc et n'active
   la source que s'il réussit.
3. **Importer maintenant** : les lieux arrivent tous « En attente ». Rien n'est
   publié automatiquement. Ensuite, la source se met à jour chaque nuit
   à 4 h 30.
4. **Modérer** : filtrer, par exemple département 84, sans signalement ;
   vérifier ; puis « Publier la sélection ». Les lieux signalés
   (« Itinéraire ? », « Sans adresse », « Doublon possible »…) sont à
   regarder un par un.

Les lieux publiés apparaissent alors sur la carte (bouton « 🏛 Lieux »)
et dans l'onglet « Lieux » de la liste.

## 7. Relier les lieux d'événement (facultatif, à votre rythme)

Administration > Rapprochements :
- **Certaines** : vérifier rapidement, puis « Relier la sélection » ;
- **Probables** et **Ambiguës** : une par une.

Un lien se retire à tout moment avec « Délier ». Il ne modifie ni le lieu
de l'événement ni ses dates. Les activités des lieux reliés apparaissent
sur la fiche du lieu permanent.

Administration > Doublons de lieux : lecture seule. Elle montre les
lignes en double déjà en base. Les nouveaux imports n'en créent plus pour
un texte identique à la casse, à la ponctuation ou à l'ordre des mots près.

## 8. Application mobile

L'application a une nouvelle dépendance native (mémorisation de la couche
« Lieux »). Il faut donc un **nouveau build** : une mise à jour à distance
ne suffit pas. Voir `mobile/README.md`. Le rendu n'a pas été vérifié sur
un appareil : contrôler la carte, la couche « Lieux », la liste et la
fiche d'un lieu.

## En cas de problème : revenir en arrière

Les migrations V61 à V63 ne se défont pas une à une. L'ancien code refuse
de démarrer sur une base qui les contient : Flyway signale des migrations
inconnues. Revenir en arrière, c'est donc **restaurer la sauvegarde de
l'étape 3 et revenir à l'ancienne version du code**. Cette opération
remplace toute la base par son état d'avant le déploiement.

```bash
docker compose --env-file .env.beta -f docker-compose.beta.yml stop backend
gunzip -c backups/avant-v61-v63.sql.gz \
  | docker exec -i locallife-postgres-beta sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
git checkout <la ligne notée à l'étape 0, sans le message>   # par exemple : git checkout abac271
docker compose --env-file .env.beta -f docker-compose.beta.yml up -d --build
curl -s https://locallife-beta.duckdns.org/actuator/health
```

Revenir ensuite sur `main` avec `git checkout main` une fois le problème
compris, pour le prochain essai.

Si seule une étape de l'administration pose problème (étapes 6 et 7), pas
besoin de restaurer :
- pour arrêter les imports de lieux, passer la source « lieux » à
  `INACTIVE` dans Administration > Agendas (liste des sources) ;
- un lieu publié par erreur se refuse depuis Administration > Lieux ;
- un lien se retire avec « Délier ».
