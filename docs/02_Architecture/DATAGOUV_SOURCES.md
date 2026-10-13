# Sources data.gouv.fr — inventaire et plan de collecte

Demande d'Alex (13/10/2026) : récupérer les sources data.gouv.fr utiles à
LocalLife. Périmètre retenu : **jeux nationaux** (événements) et **lieux /
food trucks**. Livrable demandé : inventaire + collecteur.

Ce document est l'inventaire. Le collecteur n'est **pas encore codé** :
voir « Blocage » en fin de document.

Toutes les métadonnées ci-dessous (identifiants, organisations,
fréquences, licences, URL) ont été relevées le 13/10/2026 via l'API
catalogue `https://www.data.gouv.fr/api/1/datasets/`. Les colonnes
réelles des fichiers n'ont pas pu être lues (voir « Blocage ») : aucun
mapping de colonnes n'est donc proposé ici, pour ne pas en inventer.

## 1. Événements nationaux

### 1.1 DATAtourisme — source prioritaire ⭐

| | |
|---|---|
| Jeu | DATAtourisme : la plateforme nationale des données touristiques |
| Id data.gouv | `5b598be088ee387c0c353714` |
| Producteur | ADN Tourisme (agrège offices de tourisme, CRT, CDT) |
| Fréquence | quotidienne (dernière mise à jour vue : 13/10/2026) |
| Licence | Licence Ouverte (`fr-lo`) |

Ressources utiles (les URL `static.data.gouv.fr` changent à chaque
export quotidien — toujours les résoudre via l'API, voir § 4) :

| Ressource | Id ressource | Taille | Contenu |
|---|---|---|---|
| `datatourisme-fma.csv` | `2c6023f2-…` (à confirmer via l'API) | ~59 Mo | tous les événements (FMA), France entière |
| `datatourisme-place.csv` | `cf247ad9-5bcd-4c8a-8f4d-f49f0803bca1` | ~273 Mo | tous les lieux (PLACE) |
| `datatourisme-reg-pac.csv` | `83a1f131-9e23-4c3b-b1c6-e58f33fe7b80` | ~29 Mo | PACA, tous types (FMA, PLACE, PRODUCT, TOUR) |
| `datatourisme-reg-<code>.csv` | un par région | | idem par région |

Format documenté par le producteur : CSV UTF-8, séparateur virgule,
`|` sépare plusieurs objets, `#` les propriétés d'un objet, `<>` les
valeurs d'une même propriété. Colonnes décrites (libellés exacts non
encore vérifiés) : nom, type(s) de POI (identifiants de l'ontologie
DATAtourisme), coordonnées WGS84, adresse, commune et code postal,
périodes (événements uniquement), producteur et SIT, date de mise à
jour, URL de contact, classements, description, URI DATAtourisme.

Pourquoi prioritaire :
* national, quotidien, déjà géocodé, sans clé API (contrairement à
  OpenAgenda) ;
* l'URI DATAtourisme fournit un `externalId` stable, idéal pour
  `DeduplicationService` (clé `external:`) ;
* un seul collecteur couvre événements **et** lieux ;
* les fichiers régionaux permettent de ne charger que les régions
  utiles (PACA d'abord, ~29 Mo au lieu de ~59 + 273 Mo).

Point d'attention : recouvrement probable avec les agendas OpenAgenda
déjà importés (certains offices de tourisme alimentent les deux). Le
mécanisme existant `isDuplicateOfAnotherSource` d'`ImportService` est
censé le gérer — à vérifier sur un import réel.

Alternative plus riche si le CSV simplifié s'avère insuffisant : les
flux personnalisés de l'API DATAtourisme (diffuseur.datatourisme.fr),
qui demandent un compte et une clé — hors data.gouv, non retenu à ce
stade.

### 1.2 Événements nationaux ponctuels (annuels)

Jeux publiés une fois par édition : utiles en complément, mais un
nouvel identifiant de jeu chaque année (Fête de la science) ou une
nouvelle ressource dans le même jeu (JEP) → configuration à refaire
chaque année.

| Jeu | Id data.gouv | Producteur | Licence | Remarque |
|---|---|---|---|---|
| Journées européennes du patrimoine | `5369978fa3a729239d204d11` | Ministère de la Culture | `fr-lo` | un CSV par édition (2025 : ressource `c4362d45-a8d0-4916-baf2-197bae4b34f0`) |
| Programme de la fête de la Science 2025 | `688d8586d8aa8ef745bc20c5` | MESRE | `lov2` | exports Opendatasoft (`data.enseignementsup-recherche.gouv.fr`, jeu `fr-esr-fete-de-la-science-25`) ; un nouveau jeu chaque année |

Non trouvés sur data.gouv (recherche « rendez-vous aux jardins nuit des
musées programme » : 0 résultat) : Rendez-vous aux jardins, Nuit
européenne des musées. La plupart de ces manifestations passent aussi
par OpenAgenda, déjà intégré.

## 2. Lieux

Le modèle LocalLife actuel n'a pas d'entité « lieu permanent » publiée
sur la carte : `place/Location` n'est que la localisation d'une
activité, et une activité porte des horaires/occurrences. **Décision
produit nécessaire** avant d'importer des lieux (voir § 5).

| Jeu | Id data.gouv | Producteur | Fréquence | Licence | Hébergement |
|---|---|---|---|---|---|
| Data ES — Recensement des équipements sportifs et lieux de pratique (Complet) | `6a5036faf53d94a0ff7fa58f` | Ministère chargé des Sports | quotidienne | Licence Ouverte 2.0 | Opendatasoft `data.education.gouv.fr`, jeu `data-es-recensement-des-equipements-sportifs-et-lieux-de-pratique-complet` (filtrage côté serveur possible) |
| Base des lieux et équipements culturels (Basilic) | `61777ddaa9101d073e5506cd` | Ministère de la Culture | ponctuelle | `lov2` | CSV `static.data.gouv.fr` + dictionnaire `Métadonnées_baseDEPS.xlsx` |
| DATAtourisme — lieux | voir § 1.1 | | | | `datatourisme-place.csv` ou fichiers régionaux |

## 3. Food trucks et marchés

Aucun jeu **national** sur data.gouv. Seuls des jeux communaux, hors
zone de la bêta :

| Jeu | Id data.gouv | Producteur | Dernière MAJ |
|---|---|---|---|
| Food Truck | `6300257dde5071f7259c0166` | Mairie de Saint-Denis (La Réunion) | 2020 |
| Les Food Trucks à Évry-Courcouronnes | `6262a30cabae489e1a51fa82` | Ville d'Évry-Courcouronnes | 2022 |
| Marchés couverts et de plein air | `6a46fe2c5417274350b55a1d` | Ville de Clermont-Ferrand | 2026 |
| Marchés de plein air | `5ed98c0d9b66935f52179ef2` | Orléans Métropole | 2026 |

Conclusion : pas de source data.gouv exploitable pour les food trucks
d'Avignon/Marseille — le module `foodtruck` reste alimenté
manuellement (cohérent avec `FOOD_TRUCK_CONTRACT.md` : « aucun import
externe prévu »).

## 4. Collecteur proposé

Même architecture que `OpenAgendaCollector` / `OpenAgendaCollectorFactory` :

* `DataGouvResourceResolver` : `GET https://www.data.gouv.fr/api/1/datasets/{datasetId}/`,
  retrouve la ressource par identifiant (ou titre) et renvoie son URL
  courante — indispensable car l'URL `static.data.gouv.fr` change à
  chaque export quotidien.
* `DataTourismeCsvCollector implements Collector` : télécharge le CSV en
  flux (pas en mémoire d'un bloc : jusqu'à 273 Mo), filtre sur le type
  FMA et éventuellement un département / code postal
  (`Source.regionFilter`), découpe les champs multivalués (`|`, `#`,
  `<>`) et produit des `CollectedActivity` (une `CollectedTiming` par
  période).
* Configuration par la table `source`, sans redémarrage, comme
  OpenAgenda : une nouvelle valeur de `Source.type` (ex. `DATAGOUV`) et
  l'identifiant de ressource, avec une migration pour les colonnes
  nécessaires. `ImportService#isCollectible` et la sélection de la
  factory devront dispatcher selon le type au lieu de supposer
  OpenAgenda.
* Tests sur un extrait **réel** du CSV en fixture, avec
  `MockRestServiceServer` (comme `OpenAgendaCollectorTest`).

## 5. Décisions à prendre (Alex)

1. Région(s) DATAtourisme à importer d'abord : PACA seule
   (`datatourisme-reg-pac.csv`) ou France entière (`datatourisme-fma.csv`) ?
2. Lieux : les publier comme activités sans date, créer une entité
   « lieu » dédiée, ou les reporter ? (Impact modèle → ADR.)
3. JEP / Fête de la science : à intégrer maintenant (configuration
   annuelle) ou laisser à OpenAgenda ?

## Blocage

La politique réseau de l'environnement de développement bloque
`www.data.gouv.fr`, `static.data.gouv.fr`, `tabular-api.data.gouv.fr`,
`data.education.gouv.fr` et `data.enseignementsup-recherche.gouv.fr`.
Impossible donc de lire les en-têtes et un échantillon réel des CSV,
nécessaires pour écrire le parseur et ses fixtures. Une fois ces
domaines autorisés, le collecteur DATAtourisme (§ 4) peut être codé et
testé sur données réelles.
