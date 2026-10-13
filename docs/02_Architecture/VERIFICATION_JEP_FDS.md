# Couverture OpenAgenda des JEP et de la Fête de la science — vérifications préparées

Décision d'Alex : OpenAgenda reste la source prioritaire ; pas de collecteur
spécifique sans preuve d'une couverture supplémentaire utile.

**Statut : non vérifié.** La couverture réelle ne peut pas être mesurée
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

## Accès nécessaires

Aucun secret ne doit être placé dans le dépôt, dans un ticket ou dans les
journaux. Les identifiants passent par les variables d'environnement ou les
secrets de l'environnement d'exécution.

### Base de production : lecture seule limitée (recommandé)

Un rôle dédié, en lecture seule, limité aux deux tables utiles, créé par
l'administrateur de la base (le mot de passe est saisi par lui, jamais écrit
dans un fichier versionné) :

```sql
CREATE ROLE locallife_verification LOGIN;
\password locallife_verification
GRANT CONNECT ON DATABASE locallife TO locallife_verification;
GRANT USAGE ON SCHEMA public TO locallife_verification;
GRANT SELECT ON source, activity TO locallife_verification;
ALTER ROLE locallife_verification SET default_transaction_read_only = on;
```

Aucune table d'utilisateurs n'est accessible. L'export du § 2 ne contient
que des titres, dates, codes postaux et noms de sources : pas de donnée
personnelle. Alternative sans accès : l'administrateur exécute lui-même les
deux requêtes ci-dessous et transmet les fichiers produits. Supprimer le
rôle après la vérification (`DROP ROLE locallife_verification;`).

### API OpenAgenda : clé de lecture

- **Droits nécessaires** : une **clé publique** de lecture (lecture des
  événements publiés d'agendas publics). Ni clé secrète, ni droit
  d'écriture, ni rôle sur l'agenda des JEP.
- **Obtention** : compte OpenAgenda (gratuit) au nom du projet, puis
  génération de la clé publique dans les paramètres du compte (procédure de
  la documentation développeurs d'OpenAgenda, rubrique « authentification ») ;
  c'est la même clé que celle attendue par le collecteur
  (`OPENAGENDA_API_KEY`).
- **Transmission** : variable d'environnement `OPENAGENDA_API_KEY` dans les
  secrets de l'environnement d'exécution, jamais dans le dépôt.
  `openagenda_export.py` ne l'affiche ni ne l'écrit (vérifié : avec une clé
  refusée, le message d'erreur ne la contient pas).
- **Réseau** : `api.openagenda.com` est joignable depuis l'environnement de
  développement (réponse 403 sans clé valide).

## Vérification

Trois comparaisons, avec le même script et les mêmes règles de rapprochement.

| N° | Compare | Répond à |
|---|---|---|
| 1 | liste officielle ↔ agenda OpenAgenda | OpenAgenda contient-il l'événement ? |
| 2 | agenda OpenAgenda ↔ base LocalLife | LocalLife a-t-il importé ce qu'OpenAgenda contient ? |
| 3 | liste officielle ↔ base LocalLife | **couverture réelle de l'application** |

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
OPENAGENDA_API_KEY=… python3 openagenda_export.py 2883956 84 2025-09-19 2025-09-21 jep-openagenda.csv
```

(`OPENAGENDA_API_KEY` est déjà défini dans l'environnement d'exécution : ne
pas taper la clé dans une commande partagée.)

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
