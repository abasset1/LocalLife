# Couverture OpenAgenda des JEP et de la Fête de la science — vérifications préparées

Décision d'Alex : OpenAgenda reste la source prioritaire ; pas de collecteur
spécifique sans preuve d'une couverture supplémentaire utile.

**Statut : non vérifié ; accès préparés, en attente de validation (§ « Avant toute connexion »).** La couverture réelle ne peut pas être mesurée
depuis l'environnement de développement (voir « Accès nécessaires »). Ce
document établit ce qui a pu l'être, et prépare la vérification.

## Ce qui est établi (13/10/2026)

| | Journées européennes du patrimoine | Fête de la science |
|---|---|---|
| Source officielle | Ministère de la Culture, jeu data.gouv.fr `5369978fa3a729239d204d11` (un CSV par édition) | MESRE, jeu `fr-esr-fete-de-la-science-26` sur data.enseignementsup-recherche.gouv.fr (un jeu par édition) |
| Dernière édition publiée | **2025** (l'édition 2026, tenue en septembre, n'est pas encore publiée) | **2026** (2 au 12 octobre 2026) |
| Plateforme d'origine | **OpenAgenda** : les 28 919 événements de 2025 ont un permalien `openagenda.com/agendas/2883956/…`, un **seul agenda national** (uid `2883956`) | **fetedelascience.fr**, pas OpenAgenda (liens `fetedelascience.fr/…`) |
| Vaucluse | 234 événements (2025) | 114 événements (2026), dont 5 sans date |
| Liste de référence | `collectors/verification/reference/jep-2025-vaucluse.csv` | `collectors/verification/reference/fete-de-la-science-2026-vaucluse.csv` |

Conséquences, à confirmer :

- **JEP** : la couverture dépend de la présence de l'agenda national
  `2883956` parmi les sources. Les deux agendas versionnés dans le dépôt
  (migration V14 : `86244142` Ministère de la Culture, `79839448` Ville
  d'Avignon) ne sont **pas** celui-là. D'autres agendas ont pu être ajoutés
  depuis l'administration en production : à vérifier.
- **Fête de la science** : n'étant pas publiée sur OpenAgenda, elle n'y
  figure que si des organisateurs la saisissent aussi dans un agenda
  OpenAgenda suivi. Une couverture faible est probable, mais pas démontrée.
- **DATAtourisme** ne remplace pas ces sources : l'export du 13/10/2026 ne
  couvre qu'**1 des 114** événements de la Fête de la science 2026 dans le
  Vaucluse (mesure avec `coverage_check.py`).

## Ce qui distingue un constat d'une mesure

Les constats ci-dessus portent sur les **sources** (où sont publiés les
événements). Ils ne disent rien de ce que LocalLife a réellement importé et
publié. **La couverture n'est vérifiée qu'après la comparaison n° 3
ci-dessous, sur les données de l'application.**

## Avant toute connexion : accès demandés (décision d'Alex du 13/10/2026)

Accès **strictement en lecture seule**, clé OpenAgenda limitée aux seuls
droits requis. **Aucune connexion n'est faite** à la base de production ni
à l'API OpenAgenda avec une vraie clé avant ta validation de ce qui suit.
(Seul appel effectué : une requête avec une clé factice, refusée par
OpenAgenda en 403, pour vérifier que le message d'erreur ne contient pas la
clé ; aucune donnée lue.)

### 1. Permissions exactes

**Base de données : deux options.**

| Option | Accès donné | Recommandation |
|---|---|---|
| A. L'administrateur exécute lui-même les deux requêtes du § « Vérification » et transmet les deux fichiers CSV | **aucun** | recommandée : aucun identifiant créé |
| B. Rôle dédié `locallife_verification` | lecture de 13 colonnes, au § ci-dessous | si l'option A n'est pas praticable |

Option B, à créer par l'administrateur (remplacer `locallife` par la valeur
de `POSTGRES_DB` ; le mot de passe est saisi de façon interactive, jamais
écrit dans un fichier ni une commande) :

```sql
CREATE ROLE locallife_verification LOGIN CONNECTION LIMIT 1 VALID UNTIL '2026-11-15';
\password locallife_verification
GRANT CONNECT ON DATABASE locallife TO locallife_verification;
GRANT USAGE ON SCHEMA public TO locallife_verification;
GRANT SELECT (id, name, type, agenda_uid, status, last_sync_at) ON source TO locallife_verification;
GRANT SELECT (id, title, start_date, end_date, postal_code, status, source_id) ON activity TO locallife_verification;
ALTER ROLE locallife_verification SET default_transaction_read_only = on;
ALTER ROLE locallife_verification SET statement_timeout = '60s';
```

| Accordé | Exclu (refusé par PostgreSQL) |
|---|---|
| `source` : id, nom, type, agenda OpenAgenda, statut, dernière synchronisation | `source.url`, `source.region_filter` |
| `activity` : id, titre, dates, code postal, statut, source | `created_by` (lien vers un compte), descriptions, adresse, coordonnées, URL, toutes les autres colonnes ; `SELECT *` |
| — | toutes les autres tables, dont `users` ; toute écriture ; tables temporaires |
| une connexion simultanée au plus, rôle expirant le 15/11/2026, requêtes limitées à 60 s | — |

**Testé le 13/10/2026 sur la base locale** (jamais sur un environnement
réel) : les colonnes accordées et l'export CSV fonctionnent ; `created_by`,
`source.url`, `SELECT *` et la table `users` sont refusés ; une écriture est
refusée deux fois (transaction en lecture seule par défaut, et absence de
droit d'écriture même en forçant `BEGIN READ WRITE`) ; la création d'une
table temporaire est refusée. Le rôle de test a été supprimé.

Après la vérification :

```sql
REVOKE ALL ON source, activity FROM locallife_verification;
REVOKE ALL ON SCHEMA public FROM locallife_verification;
REVOKE ALL ON DATABASE locallife FROM locallife_verification;
DROP ROLE locallife_verification;
```

**API OpenAgenda : une clé de lecture.**

| Droit | Demandé | Pourquoi |
|---|---|---|
| Clé de lecture de compte (clé publique, préfixe `oa_pk_`) | **oui** | lire les événements publiés de l'agenda public des JEP (`2883956`) |
| Clé secrète (`oa_sk_`), clé d'écriture, jeton d'accès | **non** | aucune écriture ; le script **refuse** une clé `oa_sk_` |
| Rôle sur l'agenda des JEP ou tout autre agenda | **non** | les événements publiés d'un agenda public sont lisibles sans rôle |
| Clé de lecture d'agenda (onglet « Avancé » d'un agenda) | non | réservée au propriétaire de l'agenda ; nous ne le sommes pas |

Si l'écran des clés du compte propose déjà les droits par clé (évolution
annoncée par OpenAgenda pour septembre 2026 : expiration et opérations
autorisées ; restriction par agenda non confirmée) : créer une clé
**dédiée**, opération **lecture seule**, **expiration au 15/11/2026 au
plus tard**. Sinon, la clé de lecture du compte, révoquée ou régénérée après
la vérification. Compte : un compte au nom du projet, pas un compte
personnel. Ces droits n'ont pu être lus que dans des résultats de
recherche (le site developers.openagenda.com n'est pas joignable depuis
l'environnement de développement) : à confirmer sur l'écran des clés.

### 2. Données lues et usage

| Données | Origine | Usage | Conservation |
|---|---|---|---|
| Sources (6 colonnes ci-dessus) | base LocalLife | savoir si l'agenda des JEP `2883956`, ou un agenda lié à la Fête de la science, est suivi | affichées, non conservées |
| Activités du Vaucluse (`84…`), publiées ou archivées, du 01/09/2025 au 31/10/2026 : titre, dates, code postal, nom de la source | base LocalLife | comparaisons n° 2 et 3 | fichier `activites-vaucluse.csv`, local, **jamais versionné** (ignoré par Git), supprimé après le rapport |
| Événements de l'agenda `2883956` du 19 au 21/09/2025 : titre, code postal, premier et dernier horaire (champs par défaut de l'API, sans `detailed`) | API OpenAgenda | comparaisons n° 1 et 2 ; seuls ceux du Vaucluse sont écrits (titre, dates, code postal) | fichier `jep-openagenda.csv`, même règle |
| Listes officielles JEP 2025 et Fête de la science 2026 (Vaucluse) | data.gouv.fr, MESRE (publiques) | référence des comparaisons | déjà versionnées dans `reference/` |

Non lus : comptes et données d'utilisateurs, auteurs des contributions,
descriptions, adresses, coordonnées, contacts d'organisateurs (champs
détaillés d'OpenAgenda non demandés). Le rapport ne contient que des
nombres et des titres d'événements publics.

### 3. Procédure de configuration sécurisée

1. **Clé OpenAgenda** : créer la clé de lecture comme au § 1 sur le compte
   du projet.
2. **Transmission** : l'ajouter dans les paramètres de l'environnement
   d'exécution (menu de l'environnement dans la barre de titre de la
   session, puis « Modifier »), comme variable d'environnement
   `OPENAGENDA_API_KEY`. Une **nouvelle session** la prend en compte. Ne
   jamais la coller dans la conversation, un ticket, un fichier ou une
   commande.
3. **Base** : option A (l'administrateur transmet les deux CSV) ; ou
   option B, rôle créé par l'administrateur, mot de passe transmis par le
   même canal que la clé (variable `PGPASSWORD`) et accès réseau à la base
   ouvert pour cette seule adresse, le temps de la vérification.
4. **Contrôles avant tout appel** : `python3 openagenda_export.py
   --self-test` (refus d'une clé secrète, absence de lieu détectée) ; aucune
   commande n'affiche la clé.
5. **Après le rapport** : révoquer la clé (ou la laisser expirer),
   supprimer le rôle (requêtes ci-dessus), supprimer les fichiers
   d'export.

### 4. Résultats attendus

| N° | Comparaison | Résultat attendu | Ce qui invaliderait la mesure |
|---|---|---|---|
| 0 | Contrôle préalable : activités du Vaucluse en base du 19 au 21/09/2025 (requête ci-dessous) | plusieurs dizaines | **0** : les données 2025 ont disparu ou n'ont jamais été importées ; la comparaison JEP 2025 n° 3 n'a alors pas de sens, et la mesure JEP attend l'édition 2026 |
| 1 | JEP officielle ↔ agenda `2883956` | **≥ 95 %** (la liste officielle est un export de cet agenda) | moins de 95 % : la règle de rapprochement ou l'export est en défaut ; **arrêt**, à corriger avant d'interpréter les n° 2 et 3. Cette comparaison étalonne les faux négatifs de la méthode |
| 2 | Agenda `2883956` ↔ LocalLife | agenda non suivi : faible, limité aux événements aussi publiés dans les agendas suivis (Ville d'Avignon `79839448`, Ministère de la Culture `86244142`) ; agenda suivi : ≥ 90 % | — |
| 3 | Officielle ↔ LocalLife (**couverture réelle**) | JEP : proche du n° 2 ; Fête de la science : faible (non publiée sur OpenAgenda ; DATAtourisme en couvre 1 sur 114), sur 109 événements datés | — |

**Faux positifs** : pour chaque comparaison, relecture manuelle d'un
échantillon de 20 correspondances (ou de toutes, s'il y en a moins), en
notant celles qui rapprochent deux événements différents. Le taux est
indiqué dans le rapport à côté du taux de couverture.

**Forme du rapport** : pour chaque comparaison, le nombre d'événements de
référence, le nombre couvert (au total et par source), le nombre non
couvert, le taux de faux positifs de l'échantillon, et la liste des
manquants (titres d'événements publics). Puis l'application de la règle de
décision du § 5.

**Ce qui ne sera pas affirmé** : la couverture n'est pas « mesurée » avant
la comparaison n° 3 sur les données réelles de l'application.

## Vérification

Trois comparaisons, avec le même script et les mêmes règles de rapprochement.

| N° | Compare | Répond à |
|---|---|---|
| 1 | liste officielle ↔ agenda OpenAgenda | OpenAgenda contient-il l'événement ? |
| 2 | agenda OpenAgenda ↔ base LocalLife | LocalLife a-t-il importé ce qu'OpenAgenda contient ? |
| 3 | liste officielle ↔ base LocalLife | **couverture réelle de l'application** |

### 0. Contrôle préalable : données 2025 encore en base

```sql
SELECT count(*) FROM activity
WHERE postal_code LIKE '84%'
  AND start_date < DATE '2025-09-22'
  AND COALESCE(end_date, start_date) >= DATE '2025-09-19';
```

### 1. Agendas suivis en production

```sql
SELECT id, name, type, agenda_uid, status, last_sync_at
FROM source
ORDER BY type, name;
```

À noter : présence de l'agenda JEP `2883956`, et de tout agenda lié à la
Fête de la science.

### 2. Export des activités de l'application

```sql
\copy (
  SELECT a.title,
         a.start_date::date AS start_date,
         COALESCE(a.end_date, a.start_date)::date AS end_date,
         a.postal_code,
         s.name AS source
  FROM activity a
  JOIN source s ON s.id = a.source_id
  WHERE a.postal_code LIKE '84%'
    AND a.status IN ('PUBLISHED', 'ARCHIVED')
    AND COALESCE(a.end_date, a.start_date) >= DATE '2025-09-01'
    AND a.start_date < DATE '2026-11-01'
) TO 'activites-vaucluse.csv' WITH CSV HEADER
```

`ARCHIVED` est inclus : une activité passée a pu être archivée après
l'événement.

### 3. Export de l'agenda OpenAgenda

```sh
cd collectors/verification
python3 openagenda_export.py --self-test
python3 openagenda_export.py 2883956 84 2025-09-19 2025-09-21 jep-openagenda.csv
```

(`OPENAGENDA_API_KEY` est lue dans l'environnement d'exécution : ne jamais
taper la clé dans une commande. Si le script signale l'absence de code
postal dans les champs par défaut, relancer avec `--detailed`.)

### 4. Mesures

```sh
# 1. officiel ↔ OpenAgenda
python3 coverage_check.py reference/jep-2025-vaucluse.csv jep-openagenda.csv
# 3. officiel ↔ LocalLife (couverture réelle)
python3 coverage_check.py reference/jep-2025-vaucluse.csv activites-vaucluse.csv jep-manquants.csv
python3 coverage_check.py reference/fete-de-la-science-2026-vaucluse.csv activites-vaucluse.csv fds-manquants.csv
```

La comparaison n° 2 (OpenAgenda ↔ LocalLife) s'obtient en donnant
l'export OpenAgenda comme référence, sans transformation :
`python3 coverage_check.py jep-openagenda.csv activites-vaucluse.csv`.

Le script compte, par source, les événements officiels retrouvés (titre
identique ou proche, périodes qui se chevauchent, même département) et
écrit les manquants. Contrôle effectué : la liste de la Fête de la science
comparée à elle-même (titres modifiés) donne 109 sur 109 événements datés.
Limite connue : la JEP 2026 n'est pas encore publiée par le ministère ; la
comparaison porte sur 2025, ce qui suppose des données 2025 encore en base
(archivées comprises).

### 5. Décision

Critère proposé : une source complémentaire n'est justifiée que si plus de
20 % des événements officiels du Vaucluse manquent, et que ces manquants
sont pertinents (grand public, sur place). Si c'est le cas :

- JEP : ajouter l'agenda OpenAgenda national `2883956` comme source (aucun
  nouveau collecteur : le collecteur OpenAgenda existe, avec son filtre de
  région) ;
- Fête de la science : un collecteur pour le jeu annuel du MESRE (API
  Opendatasoft, filtre `code_departement`). Mise à jour annuelle : le jeu
  change d'identifiant chaque année (`…-25`, `…-26`) ; le collecteur
  chercherait le jeu de l'année dans le catalogue plutôt qu'un identifiant
  fixe.
