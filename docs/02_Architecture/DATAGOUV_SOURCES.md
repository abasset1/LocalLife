# Sources data.gouv.fr — inventaire et plan de collecte

Demande d'Alex (13/10/2026) : récupérer les sources data.gouv.fr utiles à
LocalLife. Périmètre retenu : **jeux nationaux** (événements) et **lieux /
food trucks**. Livrable demandé : inventaire + collecteur.

Ce document est l'inventaire. Le collecteur DATAtourisme (événements)
est implémenté : voir § 4.

Toutes les métadonnées ci-dessous (identifiants, organisations,
fréquences, licences, URL) ont été relevées le 13/10/2026 via l'API
catalogue `https://www.data.gouv.fr/api/1/datasets/`. Le format CSV
DATAtourisme a été vérifié sur le fichier PACA réel du même jour.

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
| `datatourisme-fma.csv` | `2c6023f2-ffce-4c1a-9728-bdc020e463b5` | ~59 Mo | tous les événements (FMA), France entière |
| `datatourisme-place.csv` | `cf247ad9-5bcd-4c8a-8f4d-f49f0803bca1` | ~273 Mo | tous les lieux (PLACE) |
| `datatourisme-reg-pac.csv` | `83a1f131-9e23-4c3b-b1c6-e58f33fe7b80` | ~29 Mo | PACA, tous types (FMA, PLACE, PRODUCT, TOUR) |
| `datatourisme-reg-<code>.csv` | un par région | | idem par région |

Format (vérifié sur le fichier réel) : CSV UTF-8, séparateur virgule,
champs entre guillemets si besoin (descriptions sur plusieurs lignes) ;
`|` sépare plusieurs objets, `#` les propriétés d'un objet, `<>` les
valeurs d'une même propriété. En-tête :

```
Nom_du_POI,Categories_de_POI,Latitude,Longitude,Adresse_postale,
Code_postal_et_commune,Periodes_regroupees,Covid19_mesures_specifiques,
Createur_de_la_donnee,SIT_diffuseur,Date_de_mise_a_jour,Contacts_du_POI,
Classements_du_POI,Description,URI_ID_du_POI
```

Exemples : `Categories_de_POI` = URI de l'ontologie
(`https://www.datatourisme.fr/ontology/core#Festival|…`, un événement
porte `EntertainmentAndEvent`) ; `Code_postal_et_commune` =
`84000#Avignon` ; `Periodes_regroupees` =
`2026-10-24<->2026-10-24|2026-11-07<->2026-11-07` (dates seules, sans
heure) ; `Contacts_du_POI` = `libellé#url<>url|…`.

Fichier PACA du 13/10/2026 : 37 483 lignes dont 3 565 événements,
tous géolocalisés et datés ; 213 événements à venir dans le Vaucluse.

Pourquoi prioritaire :
* national, quotidien, déjà géocodé, sans clé API (contrairement à
  OpenAgenda) ;
* l'URI DATAtourisme fournit un `externalId` stable, idéal pour
  `DeduplicationService` (clé `external:`) ;
* un seul collecteur couvre événements **et** lieux ;
* les fichiers régionaux permettent de ne charger que les régions
  utiles (PACA d'abord, ~29 Mo au lieu de ~59 + 273 Mo).

Point d'attention : recouvrement probable avec les agendas OpenAgenda
déjà importés (certains offices de tourisme alimentent les deux). ⚠️ Le
mécanisme existant `isDuplicateOfAnotherSource` d'`ImportService` **ne
couvre pas ce cas** : il exige le même identifiant externe dans les deux
sources (slug OpenAgenda ≠ URI DATAtourisme) et la même heure de début
(DATAtourisme n'a pas d'heure). Voir `docs/ADR-0011-lieux-permanents.md`,
§ 1.3 (doublons entre sources, événements longs masqués par la fenêtre
de 7 jours).

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

## 4. Collecteur DATAtourisme (implémenté)

Même architecture que `OpenAgendaCollector` / `OpenAgendaCollectorFactory` :

* `DataTourismeCollector` (`collector/infrastructure`) : télécharge le
  CSV en flux (seuls les événements retenus restent en mémoire), garde
  les lignes `EntertainmentAndEvent` à venir, géolocalisées, dans les
  départements configurés, et produit des `CollectedActivity`. Détail
  des choix de mapping dans sa Javadoc.
* `DataTourismeCollectorFactory` : client HTTP qui suit les
  redirections. Pas de résolution par l'API catalogue : l'URL stable
  `https://www.data.gouv.fr/api/1/datasets/r/{resourceId}` redirige
  (302) vers le fichier du jour.
* Configuration par la table `source`, sans nouvelle colonne : type
  `DATATOURISME`, `url` = URL stable ci-dessus, `regionFilter` =
  départements (`84,13`…). `ImportService` choisit le collecteur selon le
  type.
* Source créée par la migration `V60`, **inactive** (voir « Activation »
  ci-dessous) : fichier PACA, filtre `84`. Pour
  élargir : modifier le filtre (ou le vider) depuis l'administration, ou
  pointer l'URL vers `datatourisme-fma.csv`
  (`2c6023f2-ffce-4c1a-9728-bdc020e463b5`) pour la France entière.
* Garde-fous et règles de qualité (détail dans la Javadoc du collecteur) :
  * fichier suspect (vide, colonne manquante, plus de 1 % de lignes mal
    formées, aucun événement, téléchargement interrompu) → échec de
    collecte, donc aucun archivage ;
  * période de plus de 31 jours pour un type non continu → période de
    validité, écartée (pas de récurrence inventée) ;
  * adresse générique → nom du lieu, sans adresse ;
  * liens : site de l'événement, puis tiers, puis billetterie.
* Garde-fou d'archivage, dans `ImportService` (toutes sources) : si au
  moins 10 activités publiées **à venir** disparaissent d'un coup et
  qu'elles représentent plus de la moitié des activités à venir de la
  source, aucune activité à venir n'est archivée pour cette exécution. Le nombre retenu est
  remonté dans le bilan d'import (`archiveWithheld`, colonne « Archivage
  suspendu » de l'administration) et journalisé en avertissement. Les
  activités passées s'archivent toujours normalement. Le garde-fou se
  relâche de lui-même quand les activités retenues deviennent passées.
* Fichier réel du 13/10/2026, Vaucluse : 239 événements, 118 retenus, 100
  périodes de validité seulement (88 marchés publiés pour toute l'année),
  21 passés.
* Tests : `DataTourismeCollectorTest`, sur un extrait réel du fichier
  (`src/test/resources/datatourisme/datatourisme-extrait.csv`) et des
  fichiers invalides construits dans le test.

Non couvert : lieux DATAtourisme, Data ES, Basilic, JEP, Fête de la
science (voir § 5).

### Activation d'une source DATAtourisme

**Constat (audit du 13/10/2026).** Dans la première version de la PR #104,
la migration V60 créait la source **active** : dès le déploiement,
l'import horaire (`ImportScheduler`) l'aurait collectée sans relecture,
avec publication directe des activités (`PUBLISHED`) et sans détection
des doublons avec OpenAgenda (ADR-0011, § 1.3.2). Rien ne vérifie non
plus la configuration d'une source avant son activation : une URL vers
une autre ressource, ou un filtre mal saisi, n'est détecté qu'au premier
import.

**Solution retenue (PR #104)** : la source est créée **inactive**. Comme
la PR n'est pas encore fusionnée, la migration V60 n'a été appliquée à
aucune base partagée, et sa modification ne pose pas de problème de
somme de contrôle Flyway. Si elle a été appliquée sur une base locale de
développement, `flyway repair` (ou la recréation de la base locale) est
nécessaire.

**Activation vérifiée (implémentée, décision d'Alex du 13/10/2026).**

- **Validation de configuration** (`SourceService`), à la création comme à
  la modification d'une source `DATATOURISME` : URL stable
  `https://www.data.gouv.fr/api/1/datasets/r/{identifiant}`, filtre vide ou
  liste de départements écrits comme des préfixes de code postal (`84`,
  `13`, `20` pour la Corse, `971`…), pas d'identifiant d'agenda. Une
  configuration invalide est refusée (400) avec la raison.
- **Jamais active sans vérification** : une source `DATATOURISME` est
  créée inactive ; une modification ne peut ni la passer à `ACTIVE`, ni
  changer son URL, son type ou son filtre en la laissant active (400). Le
  renommage et la désactivation restent libres.
- **Essai à blanc** (`POST /api/v1/admin/import/sources/{id}/dry-run`,
  `SourceDryRunService`) : collecte réelle, puis ce qu'un import ferait
  (créations, mises à jour, archivage, garde-fou), **sans aucune
  écriture**. Verdict `OK`, `FAILED` (configuration invalide, collecte en
  échec : fichier vide, colonnes absentes, lignes mal formées, aucun
  événement, téléchargement interrompu, erreur HTTP) ou `SUSPICIOUS`
  (aucun élément, plus de la moitié rejetés par la normalisation, ou
  disparition massive d'activités à venir).
- **Activation** (`POST /api/v1/admin/import/sources/{id}/activate`,
  `SourceActivationService`) : relance un essai à blanc et n'active la
  source que si le verdict est `OK` ; sinon 409 avec le bilan, source
  inchangée. C'est le seul chemin d'activation d'une source `DATATOURISME`.
- **Administration** : boutons « Essai à blanc » et « Activer après
  vérification » sur la fiche d'une source `DATATOURISME`, bilan affiché
  (verdict, volumes, problèmes, exemples de titres).
- **Archivage** : un import invalide échoue avant tout archivage ; un
  import valide mais suspect est retenu par le garde-fou (voir plus haut).

Procédure d'activation :

1. vérifier l'URL et le filtre de départements ;
2. « Essai à blanc », lire le bilan ;
3. « Activer après vérification » ;
4. lancer un import manuel et vérifier sur la carte quelques événements
   de la zone, en repérant les doublons évidents avec OpenAgenda (non
   masqués automatiquement, voir ADR-0011, § 2.9) ;
5. en cas de problème, repasser la source à `INACTIVE`.

Non retenu à ce stade : première publication en `PENDING` (modération) pour
une source nouvellement activée.

## 5. Décisions à prendre (Alex)

1. Zone DATAtourisme : par défaut PACA filtrée sur le Vaucluse (zone de
   la bêta). Élargir à la région, ou à la France entière ?
2. Lieux : tranché (réutiliser `Location`), proposition détaillée dans
   ADR-0011, § 7.
3. JEP / Fête de la science : OpenAgenda reste prioritaire ; vérification
   préparée dans `VERIFICATION_JEP_FDS.md`.
