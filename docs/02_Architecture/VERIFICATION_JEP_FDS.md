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

## Accès nécessaires

Une seule des deux options suffit.

1. **Lecture de la base de production** (recommandé) : liste des sources
   (`source`) et export des activités du Vaucluse sur les périodes des deux
   événements. Aucune clé ni réseau supplémentaire.
2. **API OpenAgenda depuis l'environnement de développement** : une clé de
   lecture OpenAgenda (variable `OPENAGENDA_API_KEY`), et l'accès réseau à
   `api.openagenda.com` (déjà joignable, répond 403 sans clé) et à
   `openagenda.com`. Ne mesure que ce qu'OpenAgenda contient, pas ce que
   LocalLife a réellement importé et publié.

## Vérification

### 1. Agendas suivis en production

```sql
SELECT id, name, type, agenda_uid, status, last_sync_at
FROM source
ORDER BY type, name;
```

À noter : présence de l'agenda JEP `2883956`, et de tout agenda lié à la
Fête de la science.

### 2. Export des activités à comparer

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
l'événement. Pour l'option 2 (API), produire le même format de fichier
(`title,start_date,end_date,postal_code,source`) à partir des événements
de l'agenda.

### 3. Mesure

```sh
cd collectors/verification
python3 coverage_check.py reference/fete-de-la-science-2026-vaucluse.csv activites-vaucluse.csv fds-manquants.csv
python3 coverage_check.py reference/jep-2025-vaucluse.csv activites-vaucluse.csv jep-manquants.csv
```

Le script compte, par source, les événements officiels retrouvés (titre
identique ou proche, périodes qui se chevauchent, même département) et
écrit les manquants. Contrôle effectué : la liste de la Fête de la science
comparée à elle-même (titres modifiés) donne 109 sur 109 événements datés.

### 4. Décision

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
