# Changelog

## 0.9.59 — non publiée

### Précision géographique (ADR-0011, phase A) : implémentée
- **Migration V61** : colonnes `location.coordinate_precision` et `activity.coordinate_precision` (`EXACT`, `APPROXIMATE`, `UNKNOWN`, défaut `UNKNOWN`, contrainte `CHECK`). Rattrapage ciblé : seuls les lieux au texte générique (« Centre ville », « Divers lieux »…) passent `APPROXIMATE`, ainsi que les activités dont le point affiché est celui d'un tel lieu. Aucun `EXACT` déduit. Testée sur une base en V60 avec des données.
- Import : le lieu importé reçoit sa précision (`APPROXIMATE` si son texte est générique, sinon `UNKNOWN`, jamais `EXACT`) ; l'activité prend celle de son lieu. Un lieu approximatif n'est réutilisé que par un lieu approximatif de même nom et même commune ; la précision d'un lieu réutilisé ne change pas.
- La précision de l'activité est une colonne hors du modèle `Activity` (`ActivityCoordinatePrecisionRepository`) : les copies de l'activité (changement de statut, mise à jour d'import) ne peuvent pas la remettre à `UNKNOWN` (testé sur PostgreSQL).
- API : champ `coordinatePrecision` ajouté (non cassant) aux réponses de la carte, de la liste, de la fiche (résumé et détail, et chaque lieu du détail), des activités aimées et de la recherche globale (précision du lieu effectif).
- Alertes : une activité approximative est envoyée avec la mention « (emplacement approximatif) ».
- Web : marqueur atténué avec l'info-bulle « Emplacement approximatif », mention dans la liste, la fiche et l'aperçu ; « Y aller » vise l'adresse ou la commune. Application mobile : inchangée pour l'instant (le champ est ignoré).

### Marchés et horaires (ADR-0011, phase C) : étude, rien d'intégré
- Ontologie DATAtourisme reconstituée à partir des données (sonde `ntriples_ontology_probe.py`, 17 % de l'archive, sans compte) : périodes `core:Period` / `RecurrentPeriod` (`startDate`, `endDate`, `startTime`, `endTime`, `appliesOnDay`) et horaires `schema:openingHoursSpecification` portés par le lieu.
- Jours de marché présents dans le N-Triples pour 58 à 95 des 107 marchés résolus (borne haute : recoupement non mesuré) (dates d'une séance ou `appliesOnDay`), mais perdus dans le CSV (périodes regroupées sur l'année). Part du Vaucluse non mesurée ; intérêt d'un compte diffuseur présenté.

### Doublons entre sources (ADR-0011, phase D) : mesure sans écriture
- `DuplicateReport.java` : rapport de doublons qui appelle la règle de l'application telle quelle ; classes certaines (lien distinctif partagé), probables, ambiguës ; faux positifs sûrs et échantillon reproductible à relire. Autotest de 10 vérifications.
- `events_for_matching.py` (DATAtourisme) et `infra/verification/phase_d_export_doublons.sql` (données de l'application, lecture seule, contributions exclues).
- Étalonnage sur l'export DATAtourisme PACA (producteurs comme sources) : aucune paire certaine ; 1 probable, juste ; 14 ambiguës dont 3 faux positifs (21 %). Détail : `docs/02_Architecture/VERIFICATION_DOUBLONS.md`.

### Lieux permanents (ADR-0011, phase B) : proposition, rien d'implémenté
- Mécanisme de modération proposé : `location.place_status` (`PENDING`, `PUBLISHED`, `REJECTED`, `ARCHIVED`), distinct du lieu d'événement ; tout nouveau lieu importé en attente ; aucun événement masqué par le statut d'un lieu ; refus durable ; retour en attente sur changement important.
- Liste blanche explicite mesurée sur l'export DATAtourisme PACA : 46 types proposés (418 lieux dans le Vaucluse, 4 741 en PACA), 17 à trancher, exclusions explicites. Mesure reproductible : `collectors/verification/place_types_report.py`.

### Vérification JEP / Fête de la science : accès préparés, aucune connexion
- `VERIFICATION_JEP_FDS.md` : permissions exactes (rôle en lecture seule limité à 13 colonnes, testé en local ; ou aucun accès, l'administrateur transmettant deux CSV), clé OpenAgenda de lecture uniquement, données lues et leur usage, procédure de configuration, résultats attendus de chaque comparaison avec leurs critères d'invalidation.
- `openagenda_export.py` : refuse une clé secrète (`oa_sk_`) ; ne demande plus les champs détaillés par défaut (option `--detailed`) ; s'arrête si les événements reçus n'ont pas de lieu, au lieu de produire un export vide à tort.
- Les fichiers d'export de vérification sont ignorés par Git.

### Précision géographique (ADR-0011, phase A) : conception finalisée, sans changement de schéma
- Règles en fonctions pures, testées, **non branchées** : `CoordinatePrecision` (`EXACT`, `APPROXIMATE`, `UNKNOWN`) et `CoordinatePrecisionPolicy` (un import ne pose jamais `EXACT` ; texte générique → `APPROXIMATE` ; un lieu approximatif n'est partagé qu'avec un lieu approximatif de même nom et même commune ; précision d'un lieu réutilisé inchangée).
- Le collecteur DATAtourisme délègue sa règle des adresses génériques à `CoordinatePrecisionPolicy` (comportement inchangé).
- `infra/verification/phase_a_precision_mesure.sql` : mesure en lecture seule du rattrapage (lieux et activités concernés, liste à relire), avant toute migration. Sa classification SQL est testée contre la règle Java sur PostgreSQL (`GenericPlaceSqlParityIntegrationTest`).
- Frontend : `buildDirectionsUrl` accepte une précision facultative ; pour `APPROXIMATE`, l'itinéraire vise l'adresse ou la commune. Sans ce champ, que l'API n'envoie pas encore, rien ne change.
- Migration et rattrapage : projet dans l'ADR (§ 7.1.3), testé sur une base jetable uniquement, **non créé** dans `db/migration`.

## 0.9.58 — 2026-10-13

Suite de la PR #104, fusionnée avec ses deux premiers commits seulement (0.9.57) : ces corrections n'étaient pas dans `main`.

### Import DATAtourisme : fiabilisation du collecteur
- Fichier suspect (vide, colonne manquante, plus de 1 % de lignes mal formées, aucun événement, téléchargement interrompu) : la collecte échoue, rien n'est archivé. Un fichier valide sans événement retenu reste un import vide normal.
- Période de validité : une période de plus de 31 jours pour un type non continu (marché, visite, spectacle…) décrit une saison, pas des dates. Elle est écartée plutôt que présentée comme continue, et aucune récurrence n'est déduite. Les expositions, festivals et salons gardent leur période.
- Adresse générique (« Divers lieux », « Centre ville », « Dans le village »…) : reprise comme nom du lieu, sans adresse. Les coordonnées restent celles de la source, sans précision garantie.
- Liens triés : site de l'événement, puis tiers (organisateur, lieu), puis billetterie. L'URL principale suit cet ordre.
- Vérifié sur le fichier réel du 13/10/2026, Vaucluse : 239 événements, 118 retenus, 100 écartés comme périodes de validité (dont 88 marchés), 21 passés ; 4 adresses génériques.

### Administration : essai à blanc et activation vérifiée des sources DATAtourisme
- Essai à blanc d'une source (`POST /api/v1/admin/import/sources/{id}/dry-run`) : collecte réelle et bilan de ce qu'un import ferait (créations, mises à jour, archivage), sans aucune écriture. Verdict `OK`, `FAILED` ou `SUSPICIOUS`.
- Activation vérifiée (`POST /api/v1/admin/import/sources/{id}/activate`) : la source n'est activée que si l'essai est `OK` ; sinon 409 avec le bilan. Seul chemin d'activation d'une source `DATATOURISME`.
- Configuration d'une source `DATATOURISME` validée à l'enregistrement (URL stable data.gouv.fr, départements) ; une modification ne peut pas l'activer, ni changer sa configuration en la laissant active.
- Boutons « Essai à blanc » et « Activer après vérification » dans l'administration des agendas.

### Étude de faisabilité N-Triples (marchés)
- Lecture en flux de 26 % de l'archive DATAtourisme N-Triples (480 Mo, 18,9 millions de triplets, 55 min) : 3 168 marchés, aucun relié à des jours ou horaires de présence. Pas de connecteur recommandé en l'état ; méthode, coûts, limites et étapes suivantes dans ADR-0011, § 2.8 ; script : `collectors/verification/ntriples_market_study.py`.

### Doublons entre sources : règle de rapprochement (non branchée)
- `CrossSourceDuplicateMatcher` : niveaux probable / possible / aucun (jour commun, distance de 300 m, titre identique ou proche, titres génériques jamais suffisants). Fonction pure, sans écriture ; testée sur les faux positifs mesurés dans DATAtourisme (« Fête de la musique » dans trois communes, « Octobre Rose »).

### Vérification de couverture JEP / Fête de la science (préparée)
- Listes officielles du Vaucluse (JEP 2025 : 234 événements, tous sur l'agenda OpenAgenda national `2883956` ; Fête de la science 2026 : 114) et script de comparaison `collectors/verification/coverage_check.py`. Procédure et accès nécessaires : `docs/02_Architecture/VERIFICATION_JEP_FDS.md`.
- Export d'un agenda OpenAgenda au même format (`openagenda_export.py`, clé lue dans l'environnement, jamais affichée) ; procédure en trois comparaisons (officiel ↔ OpenAgenda ↔ base LocalLife) et accès en lecture seule limité. Couverture réelle **non mesurée** à ce jour.

### Import : garde-fou contre l'archivage massif (toutes sources)
- Si au moins 10 activités publiées à venir disparaissent d'un coup d'une source, et plus de la moitié de ses activités à venir, aucune activité à venir n'est archivée pour cette exécution (fichier tronqué, filtre mal saisi, panne côté source). Le bilan d'import indique le nombre d'activités retenues (`archiveWithheld`, colonne « Archivage suspendu » de l'administration). Les activités passées s'archivent toujours.

### Carte : expositions longues de nouveau visibles
- Sans date choisie, `/nearby` et `/within-bounds` retiennent une occurrence qui chevauche les 7 prochains jours. Avant, il fallait que son début ou sa fin y tombe : une exposition commencée avant aujourd'hui et finissant après la fenêtre était masquée (9 événements DATAtourisme sur 64 dans le Vaucluse). Rien de ce qui était visible ne disparaît.
- Tests : `DataTourismeCollectorTest` (18 cas, sur un extrait réel du fichier et des fichiers invalides, dont les bornes de la règle des 31 jours), `DataTourismeImportSafetyTest` (6 cas : vrai collecteur branché sur l'import, aucun archivage sur fichier invalide), `ImportServiceTest` (5 cas : sélection du collecteur, garde-fou d'archivage), `DefaultDateWindowIntegrationTest` (2 cas, PostGIS, échouent sur l'ancienne requête).
- Bilan d'import : motif en clair d'un blocage (`blockedReason` : collecte en échec, ou archivage suspendu), affiché dans l'administration des imports. Seul le message rédigé par le collecteur est affiché (comme dans l'essai à blanc) ; celui d'une exception inattendue, qui peut contenir l'URL d'appel et donc une clé d'API, ne va que dans les journaux.
- La migration `V60` n'est pas modifiée (déjà fusionnée) : la source `DATAtourisme PACA` qu'elle crée reste **active** là où elle a été appliquée. Diagnostic en lecture seule (`infra/verification/v60_diagnostic.sql`, verdicts A, B0, B1, B2, C) et désactivation ciblée de la seule source jamais utilisée (`infra/verification/v60_desactivation_conditionnelle.sql`, sans effet sur une source ayant alimenté la base), testés sur des bases simulées ; procédure dans `DATAGOUV_SOURCES.md` (« Migration V60 »). Bêta et production non vérifiées : diagnostic à exécuter par l'administrateur.

## 0.9.57 — 2026-10-13

### Import : événements DATAtourisme depuis data.gouv.fr
- Nouveau type de source `DATATOURISME` : lit l'export CSV quotidien DATAtourisme publié par ADN Tourisme sur data.gouv.fr, sans clé API, et n'en garde que les événements à venir.
- Source ajoutée par la migration `V60` : `DATAtourisme PACA (data.gouv.fr)`, active, filtrée sur le Vaucluse (`84`). URL et départements modifiables depuis l'administration (type `DATATOURISME` ajouté au formulaire).
- Chaque période (dates seules) devient un créneau de 00:00 le premier jour à 23:59 le dernier. Les types DATAtourisme sont traduits en mots-clés français (concert, théâtre, marché…) pour la classification ; à défaut, le titre sert de repli.
- Vérifié sur le fichier réel du 13/10/2026 : 213 événements à venir dans le Vaucluse, lus en environ 4 s.
- Inventaire des sources data.gouv utiles (événements nationaux, lieux, food trucks) : `docs/02_Architecture/DATAGOUV_SOURCES.md`.
- Tests : `DataTourismeCollectorTest` (8 cas, sur un extrait réel du fichier) et `ImportServiceTest` (1 cas : sélection du collecteur). `mvn verify` passe (1209 tests, PostGIS local).

## 0.9.56 — 2026-10-11

### Import : une annulation par un administrateur n'est plus écrasée
- Un ré-import met à jour le contenu d'une activité existante, jamais son statut. Une activité importée puis annulée par un administrateur reste annulée, même si la source la modifie, la retire puis la remet. Auparavant, toute modification côté source la remettait en `PUBLISHED` : elle réapparaissait sur la carte, dans la liste et dans la recherche.
- L'archivage des activités disparues de la source, et celui des doublons entre sources, ne concerne plus que les activités `PUBLISHED`. Une activité annulée n'est donc jamais archivée puis republiée.
- Inchangé : une activité archivée par l'import (absente de la source) est republiée à son retour.
- Tests :
  - `ImportServiceTest` : 4 cas, dont 3 qui échouent sur le code antérieur ;
  - `ImportServiceIntegrationTest` : 1 scénario de bout en bout (annulée, modifiée, retirée, revenue), qui échoue sur le code antérieur.

## 0.9.54 — 2026-10-11

### Recherche V1 : tests de non-régression et audit (#93)
- Audit complet : `docs/02_Architecture/SEARCH_V1_AUDIT.md` (couverture par exigence, performance mesurée et plan SQL, sécurité, risques résiduels).
- Correctif accessibilité web : après « Suivants › » ou « ‹ Précédents » au clavier, le focus retombait sur la page entière (le bouton disparaît pendant le chargement). Il revient sur le bouton utilisé, sur l'autre en dernière page, ou sur « Réessayer » en cas d'erreur. Vérifié dans Chromium en 1280×800 et 390×844 (10 vérifications).
- Tests backend ajoutés (16) :
  - `ActivitySearchIntegrationTest` couvre la série récurrente avec séance déplacée, les résultats lointains atteints par pagination, les activités non publiées jamais comptées (même avec occurrences et filtres), les jokers `%`/`_`/`\` neutres, les tentatives d'injection SQL, la position jamais persistée (lignes comptées dans toutes les tables) et la fiche d'un résultat sans position ;
  - `GeographicEndpointsNonRegressionIntegrationTest` fige les champs JSON de `/nearby`, `/within-bounds` et `GET /api/v1/activities`, et vérifie le rayon et la zone.
- Tests web ajoutés (17) :
  - `SearchResultBanner.test.tsx` : états, rôles ARIA, pagination, focus ;
  - `ActivityList.test.tsx` : ordre de pertinence, résultat sans lieu ni date, clavier, liste de la carte inchangée ;
  - `App.search.test.tsx` : position connue envoyée, commune proposée qui reste un chemin distinct.
- Performance (20 000 activités, 30 000 occurrences, `EXPLAIN ANALYZE` de la requête réelle) : 179 à 331 ms, aucun débordement sur disque.
- Constats hors périmètre, non corrigés (décision à prendre) :
  - `GET /api/v1/activities` (liste publique) renvoie aussi les activités non publiées ;
  - `GET /api/v1/activities/{id}` répond 500 pour une activité sans coordonnées en base (cas non produit par l'application) ;
  - aucune limitation de débit sur les endpoints publics.
- Vérifié :
  - `mvn -o verify` (1189 tests, 0 violation checkstyle) ;
  - `npm test` (50 tests) et `npm run build` sous Node 22.22 et 20.20.

## 0.9.53 — 2026-10-11

### Explorer : la barre de recherche utilise la recherche globale (#92)
- « Rechercher » / Entrée (sans commune choisie) appelle `GET /api/v1/activities/search` avec le texte, les catégories et la période actives. La position de référence n'est envoyée que si elle existe déjà (géolocalisation acceptée, ville par défaut, commune choisie) ; le texte n'est **plus géocodé** (`/api/v1/geocoding/city` n'est plus appelé par la barre). Choisir une commune proposée reste le seul moyen de déplacer la carte, et efface la recherche texte.
- Résultats globaux : ni rayon ni zone visible. La liste les montre à plat, dans l'ordre de pertinence, avec la ville et la distance (« à 1,2 km ») ; la carte affiche le même ensemble (résultats situés) et se cadre dessus à chaque page. Un résultat sans lieu reste dans la liste (« Lieu non précisé ») et le bandeau l'indique. Choisir un résultat centre la carte sur son lieu effectif sans effacer la recherche ; sur mobile, la vue Carte s'affiche alors avec l'aperçu (auparavant, un choix depuis la vue Liste ne montrait rien).
- Pagination du contrat #91 (20 par page, « ‹ Précédents » / « Suivants › », « 21–26 sur 26 ») ; la recherche et la page survivent à la bascule Carte/Liste ; un changement de filtre ou de position relance en première page.
- États explicites dans le bandeau : chargement, résultats, aucun résultat (avec conseil de retirer les filtres s'il y en a), erreur avec « Réessayer », texte trop court (sans appel), effacé. Les réponses obsolètes (nouvelle recherche, autre page) sont annulées et ignorées.
- Fiche et aperçu d'un résultat sans lieu, sans date ou sans source : « Lieu non précisé », « Date non précisée », pas de ligne « Source » vide, pas de lien « Y aller » sans destination. Les favoris sont repris de `GET /api/v1/users/me/likes` (connecté).
- Le filtre texte côté client (`matchesSearchQuery`) est retiré : il ne cherchait que dans les activités déjà chargées.
- Tests : `globalSearch.test.ts` (URL, conversion, distance), `useActivitySearch.test.tsx` (annulation, réponse tardive, erreur, réessai, favoris), `App.search.test.tsx` (8 parcours dans l'écran, carte simulée). Vérifié : `npm test` (33 tests), `npm run build` ; parcours Playwright contre le backend réel en 1280×800 (avec et sans géolocalisation) et 390×844 : 51 vérifications, aucune erreur JS, aucun défilement horizontal.
- Connu, hors périmètre : `GET /api/v1/activities/{id}` répond 500 pour une activité sans coordonnées en base (constructeur `Activity` en `double`) ; la fiche affiche alors « Le détail de cette activité n'a pas pu être chargé ».

## 0.9.52 — 2026-10-11

### Recherche globale : termes répartis sur plusieurs champs (correctif post-#95)
- « concert Lyon » trouve désormais une activité dont le titre est « Concert acoustique » et la ville « Lyon » : chaque terme significatif doit se trouver dans au moins un champ (titre, description, catégorie, ville ou adresse de l'activité ou d'un lieu effectif d'occurrence), les termes pouvant se répartir entre champs. Auparavant, un même champ devait contenir tous les termes.
- Pertinence globale non trompeuse : hors titre exact ou préfixe, le niveau est le moins bon, parmi les termes, du meilleur champ de chaque terme (« concert lyon » avec « lyon » en ville → `LOCATION_MATCH`, pas `TITLE_CONTAINS`). Les 6 niveaux et les critères secondaires (champs correspondants, distance, date, identifiant) sont conservés ; inchangé pour une requête d'un terme ou dont un champ contient tous les termes.
- Décisions conservées : sans date, activités non terminées sans borne supérieure ; mots vides ignorés (repli sur tous les mots si la requête n'en contient que) ; lieu, coordonnées, dates et distance issus de la même occurrence retenue, occurrences annulées ignorées.
- SQL : 4 tests `LIKE ALL` sur des concaténations cumulées des champs normalisés (séparateur `|`), sans sous-requête par ligne. Performance mesurée inchangée (~100 à 250 ms sur 20 000 activités et 30 000 occurrences ; ~200 à 310 ms au pire cas de 40 termes).
- Tests : 9 tests d'intégration ajoutés (exemples « concert Lyon », termes répartis titre/catégorie/ville, lieu d'occurrence effectif et annulé, six niveaux avec une requête multi-termes, pertinence globale, ordre secondaire, mots vides, accents) ; 8 échouent sur le code antérieur. Vérifié : `mvn -o verify` (1173 tests, checkstyle).

## 0.9.51 — 2026-10-11

### Suggestions de ville : tests de régression (#90), accessibilité et mobile
- Tests frontend (nouveau : Vitest + jsdom + Testing Library, `npm test`) couvrant le correctif #90 : saisie modifiée pendant le debounce, saisie modifiée pendant une requête en vol (requête annulée), réponse tardive d'une ancienne saisie (avant ou après la nouvelle, y compris résolue malgré l'annulation), erreur réseau tardive, impossibilité de sélectionner une suggestion de l'ancienne saisie ; clavier (flèches, Entrée, Échap), attributs ARIA du combobox, sélection souris/toucher. Trois de ces tests échouent sur le code antérieur à #90.
- Correctif accessibilité : une suggestion activée par un simple clic (sans mousedown préalable, cas de technologies d'assistance) n'était pas sélectionnée ; le choix se fait désormais au clic, le mousedown étant seulement neutralisé pour garder le focus.
- Correctifs mobile (écrans tactiles uniquement, `pointer: coarse`) : champ de recherche en 16 px (évite le zoom automatique d'iOS Safari à la mise au point, la police était de 15,2 px) ; suggestions d'au moins 44 px de haut (36 px auparavant).
- CI : nouveau workflow `frontend-ci.yml` (`npm ci`, `npm test`, `npm run build`, Node 20) ; il n'y avait aucune CI frontend.
- Vérifié : `npm test` (12 tests) et `npm run build` sous Node 22.22 et Node 20.20 ; clavier, toucher et souris dans Chromium (suggestions simulées).

### Recherche textuelle globale : `GET /api/v1/activities/search` (#91)
- Nouvel endpoint public : recherche dans le titre, la description, les catégories (libre, codes, libellés), la ville et l'adresse (de l'activité et des lieux effectifs de ses occurrences), sans casse, accents, ponctuation ni espaces superflus ; **globale** (aucun rayon, aucune zone) ; activités `PUBLISHED` uniquement.
- Classement : niveau de pertinence (`TITLE_EXACT` > `TITLE_PREFIX` > `TITLE_CONTAINS` > `CATEGORY_MATCH` > `LOCATION_MATCH` > `DESCRIPTION_MATCH`), puis nombre de champs correspondants, puis distance à la position de référence (`latitude`/`longitude`, jamais enregistrée), puis date de début et identifiant. Filtres `category`, `date`/`dateTo` ; pagination `limit`/`offset` avec `total`. Sans `date` : activités non terminées.
- Modèle riche respecté : lieu effectif `COALESCE(occurrence.location_id, schedule.location_id)`, occurrences annulées ignorées, une seule ligne par activité ; lieu, coordonnées, dates et distance de la réponse viennent de la même occurrence retenue.
- Base : V58 `ll_search_normalize` (normalisation en SQL pur, sans extension), V59 colonnes générées `search_*` sur `activity` et `location` (textes normalisés calculés à l'écriture). Aucun index texte : non justifié par la mesure à ce volume.
- Performance mesurée (20 000 activités, 30 000 occurrences, `EXPLAIN ANALYZE`) : ~0,15 à 0,35 s par requête, contre ~50 s pour la première écriture de la requête.
- Contrat : `docs/02_Architecture/GLOBAL_SEARCH_CONTRACT.md`. Endpoints existants inchangés (contrôleur et requêtes dédiés).
- Tests : `ActivitySearchIntegrationTest` (36 cas, HTTP et PostgreSQL réels). Vérifié : `mvn verify` (1164 tests, checkstyle).


## 0.9.50 — 2026-10-11

### Recherche : villes proposées avec leur code postal pendant la saisie
- Web : dès 2 caractères, la barre de recherche de l'Explorer propose les communes correspondantes, les plus peuplées d'abord : « 📍 Lyon — 69001 – 69009 · 69 ». Taper un code postal (« 69003 ») propose sa commune avec ce code. Flèches pour parcourir, Entrée ou clic pour choisir, Échap pour fermer. Une ville choisie déplace aussitôt la carte (et le rayon actif), avec le bandeau « 📍 Lyon (69003) — N activités sur la carte ». Sans choix, « Rechercher » / Entrée gardent le comportement actuel (ville, sinon mot-clé).
- Backend : endpoint public `GET /api/v1/geocoding/city-suggestions?q=…` (`CitySuggestionService`), qui interroge l'API Découpage administratif de l'État (`geo.api.gouv.fr`) : gratuite, sans clé, faite pour l'autocomplétion, avec codes postaux et centre des communes. Nominatim interdit l'autocomplétion dans sa politique d'usage. Couverture : communes françaises. Réponses gardées en mémoire (1 000 dernières saisies) ; liste vide sous 2 caractères ; 503 si l'API est injoignable (le web n'affiche alors simplement pas de suggestions).
- Côté web, appel après 250 ms sans frappe, réponses obsolètes ignorées, suggestions précédentes conservées le temps de la réponse suivante (pas de clignotement). Liste accessible (motif ARIA « combobox »).
- Tests : `CitySuggestionServiceTest` (recherche par nom et par code postal, codes triés, communes sans centre écartées, cache, saisie trop courte, API en erreur), cas ajouté à `GeocodingControllerTest`. Vérifié : `mvn verify` (1128 tests, checkstyle), build web ; parcours dans le navigateur contre le backend réel (suggestions simulées, `geo.api.gouv.fr` étant injoignable ici) : « lyo » → Lyon et Lyons-la-Forêt (un seul appel) ; ↓ + Entrée → carte sur Lyon, activité affichée ; « 69003 » → « Lyon 69003 » ; Échap ferme, la frappe rouvre ; « concert » → aucune suggestion, Entrée → recherche par mot-clé. Affichage vérifié en 375 px. Appel réel : 503 explicite ici, liste vide pour 1 caractère.

## 0.9.49 — 2026-10-11

### Administration : suppression d'activités (toutes, par critères ou une par une)
- Nouvel onglet « Suppression » : critères cumulables (agenda / source, statut, période de début) ou « Toutes les activités ». Toujours en deux temps : « Compter les activités visées » affiche le nombre, puis « Supprimer N activités » après confirmation ; pour tout supprimer, il faut en plus taper « SUPPRIMER ». L'aperçu disparaît dès qu'un critère change, et le serveur refuse (409, rien n'est supprimé) si le nombre a changé depuis l'aperçu (import en cours…).
- Modération : bouton « Supprimer » sur chaque activité, quel que soit son statut, activités importées comprises.
- Backend : `GET /api/v1/admin/activities/deletion-preview`, `DELETE /api/v1/admin/activities` (critères + `expectedCount`), `DELETE /api/v1/admin/activities/{id}`, réservés au rôle ADMIN. `ActivityBulkDeletionService` supprime en SQL par lots de 1 000, avec tout ce qui est rattaché (horaires et occurrences, médias, liens, accessibilité, tags, métadonnées, classifications ; favoris, catégories, alertes envoyées et relectures en cascade). Sans critère, la suppression est refusée sauf `all=true`.
- Sécurité : tout chemin sous `/api/v1/admin/` est désormais réservé au rôle ADMIN (filet de sécurité si un endpoint d'administration est oublié dans `SecurityConfig`).
- Les activités importées encore présentes dans leur agenda reviennent au prochain import (rappelé dans l'interface).
- Tests : `ActivityBulkDeletionServiceIntegrationTest` (chaque critère, bornes de dates, rattachements supprimés et autres activités intactes, nombre changé → rien de supprimé, critère obligatoire, tout supprimer, une seule activité ; et un test qui échoue si une nouvelle table liée aux activités n'est pas prise en charge), cas ajoutés à `AdminActivityControllerIntegrationTest` (403 pour un utilisateur, 401 sans connexion, aperçu puis suppression, 409, 400). Vérifié : `mvn verify` (1122 tests, checkstyle), build web ; parcours dans le navigateur contre le backend réel : « Supprimer » en modération ; source + « Publiées » → 2 activités comptées puis supprimées ; « Toutes » → bouton bloqué jusqu'à la saisie de SUPPRIMER, puis base vide.

## 0.9.48 — 2026-10-11

### Administration : imports des agendas et des activités
- Nouvel onglet « Imports » dans l'administration web (les endpoints existaient, seulement accessibles par l'API) :
  - **Découvrir les agendas OpenAgenda** (`POST /api/v1/admin/import/discover-agendas`) : nombre d'agendas à examiner (1 à 500, 50 par défaut) ; bilan « N examinés : X nouveaux, Y déjà connus » et liste des agendas ajoutés ; l'onglet « Agendas » est rechargé.
  - **Importer les activités** (`POST /api/v1/admin/import`) : confirmation avant lancement, bouton « Import en cours… 1 min 05 s » avec durée, puis bilan par source (collectées, créées, mises à jour, inchangées, archivées, à relire, doublons, ignorées, erreurs) avec une ligne de total ; résumé en jaune s'il y a des erreurs. « Un import est déjà en cours » si un autre import tourne (409).
  - L'onglet reste chargé : on peut changer d'onglet pendant un import, le bilan s'affiche à la fin. Messages d'erreur du serveur affichés tels quels (ex. clé OpenAgenda manquante).
- Correctif : l'onglet actif de l'administration devenait illisible au survol (texte blanc sur fond clair).
- Vérifié dans le navigateur contre le backend réel, avec un compte administrateur : découverte → « OPENAGENDA_API_KEY doit être configurée… » (pas de clé ici) ; import réel → bilan des 2 sources actives en erreur (OpenAgenda injoignable) ; découverte réussie simulée → 2 agendas ajoutés listés ; import lent simulé (4 s) → « Import en cours… », changement d'onglet, bilan affiché au retour. Build web.

## 0.9.47 — 2026-10-11

### Recherche de l'Explorer : explicite, visible, avec un message quand rien ne correspond
- La saisie seule ne change plus la carte (auparavant chaque lettre filtrait aussitôt : taper une ville vidait la carte avant même la recherche). La recherche part avec le nouveau bouton « Rechercher » ou Entrée.
- Recherche en cours visible : bouton « ⟳ Recherche… », barre de progression dans le champ, bandeau « Recherche de « … »… ». La barre reste mise en évidence tant qu'une recherche est appliquée.
- Résultat dans un bandeau sous la barre, avec « Effacer ✕ » : « 📍 Lyon — 2 activités sur la carte » pour une ville (la carte s'y déplace), « 🔎 3 résultats pour « concert » » pour un mot, et en rouge « Aucun résultat pour « piscine » dans cette zone. Déplace la carte ou essaie un autre mot. » quand rien ne correspond (même message sur la carte et dans la liste). Le nombre suit la carte (zoom, déplacement). Si la recherche de ville est indisponible, le texte est tout de même cherché parmi les activités.
- Correspondance plus tolérante : insensible aux accents, à la casse et à l'ordre des mots (« marche croix rousse » trouve « Marché de la Croix-Rousse »).
- Un seul bouton d'effacement dans le champ (celui du navigateur est masqué) ; le bouton « Rechercher » reste visible sur téléphone.
- Vérifié dans le navigateur contre le backend réel (réponses de Nominatim simulées, avec 1,2 s de latence) : saisie seule sans effet ; état « Recherche… » ; « Lyon » → carte déplacée, 2 activités ; « marche croix rousse » → 1 résultat ; « concert » → 1 résultat ; « piscine » → message d'absence de résultat ; « Effacer » → toutes les activités. Affichage vérifié en 375 px (pas de défilement horizontal). Build web.

## 0.9.46 — 2026-10-11

### Recherche de ville depuis la carte
- Web : dans la barre de recherche de l'Explorer, Entrée cherche le texte comme une ville. Trouvée, la carte s'y déplace (au moins à l'échelle d'une ville ; avec un rayon actif, le rayon s'y recentre comme avec « Autour de moi »), le filtre texte est retiré et un message « 📍 Carte centrée sur … » s'affiche. Sinon, le texte reste un filtre sur les activités chargées, avec un message si rien ne correspond. Jusqu'ici, la recherche ne filtrait que les activités déjà chargées : chercher une autre ville ne donnait rien.
- Backend : nouvel endpoint public `GET /api/v1/geocoding/city?name=…` (200 ville et position, 400 nom vide, 404 introuvable, 503 Nominatim indisponible). `CitySearchService` protège Nominatim (1 requête/s) : cache des 500 dernières recherches (villes introuvables comprises), appels espacés d'au moins une seconde, réponse 503 plutôt qu'une attente de plus de 3 s.
- Tests : `CitySearchServiceTest` (cache insensible à la casse, villes introuvables en cache, indisponibilité non mise en cache, espacement des appels), `GeocodingControllerTest`. Vérifié : `mvn verify` (1110 tests, checkstyle), build web ; parcours dans le navigateur contre le backend réel, visiteur non connecté, réponses de Nominatim simulées (injoignable ici) : Marseille → Entrée « Lyon » → carte et liste sur l'activité de Lyon ; « Vienne » → activité de Vienne ; « zzzz » → message. Appel réel à l'endpoint : 503 explicite (Nominatim injoignable), 400 pour un nom vide.

## 0.9.45 — 2026-10-11

### Explorer : la liste suit la carte
- Web : la liste n'affiche plus que les activités visibles sur la carte. Avec un rayon, la recherche charge tout le rayon : zoomer sur une partie de ce rayon réduit désormais la liste à la zone affichée (auparavant elle gardait tout le rayon). La liste se met à jour à chaque déplacement ou zoom de la carte, et à chaque changement de rayon ou de filtre.
- `isInBounds` (`activityUtils.ts`) ; l'application mobile, qui n'a pas de vue liste, n'est pas concernée.
- Vérifié dans le navigateur contre le backend réel (Lyon 30 km favori, activités à Lyon, Meyzieu ~13 km et Vienne ~27 km) : 30 km → les trois ; zoom sur Lyon → Lyon seule (carte et liste identiques) ; dézoom → les trois ; rayon 10 km → Lyon ; 20 km → Lyon et Meyzieu. Build web.

## 0.9.44 — 2026-10-11

### Modifier une activité publiée ne la renvoie plus en validation ; délai de validation annoncé
- Une contribution modifiée par son auteur garde son statut : une activité publiée reste publiée et visible sur la carte, une activité en attente reste en attente. Jusqu'ici, toute modification par l'auteur la repassait en `PENDING` et la retirait de la carte.
- Seule exception : une activité refusée, corrigée par son auteur, est resoumise (`REJECTED → PENDING`). Un administrateur modifie toujours sans changer le statut.
- Web et mobile : le bandeau « l'activité repasse en validation » n'apparaît plus que pour une activité refusée, et le message après enregistrement suit le statut renvoyé par le serveur.
- Proposition d'une activité (web et mobile) : un encadré dans le formulaire prévient qu'elle sera soumise à validation par un modérateur et publiée d'ici 2 à 3 jours ; après l'envoi, un message de confirmation le rappelle et renvoie vers « Mes contributions » pour suivre son statut.
- Tests : `ContributionServiceTest` (publiée gardée, refusée resoumise, règle complète), `ContributionManagementIntegrationTest` (publiée modifiée → reste visible publiquement ; refusée corrigée → `PENDING`). Vérifié : `mvn verify` (1103 tests, checkstyle), build web, mobile `tsc`/`eslint`/`jest` (81) ; encadré vérifié dans le formulaire web, connecté au backend réel.

## 0.9.43 — 2026-10-10

### Mobile : « Modifier » / « Supprimer » sur la fiche d'une activité
- La fiche affiche « Modifier » et « Supprimer » à son contributeur (et aux administrateurs), jamais aux autres utilisateurs ni pour une activité importée. « Modifier » ouvre le formulaire pré-rempli ; au retour, la fiche se recharge. « Supprimer » demande confirmation puis revient en arrière (ou à la carte si la fiche a été ouverte directement, depuis une notification par exemple).
- Le contributeur voit désormais sa fiche même quand elle n'est pas publiée (en attente de validation après une modification, refusée…), avec un bandeau qui le précise ; pour les autres, elle reste « indisponible ».
- API : `GET /api/v1/activities/{id}` expose `createdBy` (fiche détaillée et résumé), déjà public sur les réponses de la carte.
- Tests : `permissions.test.ts`, cas ajouté à `ContributionManagementIntegrationTest`. Vérifié : mobile `tsc`/`eslint` (0 erreur, avertissements préexistants)/`jest` (81), `mvn verify` (1100 tests) ; parcours dans la version web de l'app contre le backend réel : un autre utilisateur ne voit pas les boutons ; l'auteur modifie le titre → fiche rechargée avec bandeau « En attente de validation » → Supprimer → retour à la carte, activité supprimée en base. ⚠️ Non testé sur un appareil iOS/Android.

## 0.9.42 — 2026-10-10

### Mobile : proposer, modifier et supprimer ses contributions
- Onglet « Contribution » (jusqu'ici vide) : formulaire de proposition — titre, description, catégories, adresse, dates, contacts — avec « Revient chaque semaine (food truck, marché…) » : jours de passage, heure d'arrivée et de départ (« 11h30 », « 11:30 » ou « 1130 » acceptés), « À partir du » / « Jusqu'au ». L'activité est créée en attente de validation.
- Nouvel écran « Mes contributions » (depuis l'onglet Contribution et l'onglet Compte) : toutes ses activités avec leur statut (en attente de validation, publiée, refusée…), « Modifier » et « Supprimer » (avec confirmation). « Modifier » ouvre le formulaire pré-rempli ; après enregistrement l'activité repasse en validation (message d'information), sauf pour un administrateur. La liste se recharge au retour sur l'écran.
- Correctif (web et mobile) : passer une activité ponctuelle en récurrente gardait sa date de fin (souvent le jour même), qui bornait la récurrence à une seule journée → « aucun jour de passage ». Activer « Revient chaque semaine » vide désormais la date de fin ; le message d'erreur du backend cite la période concernée.
- Mobile : la fiche d'une activité n'affiche pas encore « Modifier »/« Supprimer » (elle n'expose pas son créateur) : la gestion passe par « Mes contributions ».
- Tests : `features/contribution/__tests__/form.test.ts` (validation, récurrence, pré-remplissage, formats d'heure). Vérifié : mobile `tsc`/`eslint` (0 erreur, avertissements préexistants)/`jest`, build web, `mvn verify` ; parcours dans la version web de l'app (Expo) contre le backend réel : connexion → proposition d'un food truck mardi/jeudi 11h30–14h00 (corps de requête vérifié, la création réelle exigeant le géocodage, injoignable ici) → « Mes contributions » → Modifier → « tous les samedis à 8h00 » → « En attente de validation » en base (`FREQ=WEEKLY;BYDAY=SA`) → Supprimer. ⚠️ Non testé sur un appareil iOS/Android.

## 0.9.41 — 2026-10-10

### Modifier ou supprimer ses contributions (la modification repasse en validation)
- Web : nouvelle page « Mes contributions » (menu Communauté) avec toutes les activités proposées, quel que soit leur statut (en attente de validation, publiée, refusée, annulée, archivée), chacune avec « Modifier » et « Supprimer ». La fiche d'une activité propose aussi « Modifier » et « Supprimer » à son créateur (et aux administrateurs), à côté de « Annuler cette activité ».
- « Modifier » rouvre le formulaire de proposition pré-rempli (catégories, adresse, dates, contacts, récurrence). Un bandeau prévient que l'activité repassera en validation ; après enregistrement, elle passe en `PENDING` et disparaît de la carte jusqu'à ce qu'un administrateur la valide de nouveau. Une modification faite par un administrateur ne change pas le statut. Une activité refusée peut être corrigée puis resoumise ; une activité annulée ou archivée ne se modifie plus (elle peut être supprimée).
- « Supprimer » (avec confirmation) efface définitivement l'activité et ce qui y est rattaché : horaires et occurrences, liens/contacts, médias, classification ; favoris et alertes partent en cascade.
- API : `GET /api/v1/activities/mine`, `GET /api/v1/activities/{id}/contribution` (pré-remplissage), `PUT /api/v1/activities/{id}` (même corps que la création), `DELETE /api/v1/activities/{id}`. Réservés au créateur ou à un administrateur (401 sans connexion, 403 pour un autre utilisateur), refusés pour une activité importée. L'adresse n'est géocodée de nouveau que si elle change.
- Mobile : pas encore de gestion des contributions (l'onglet Contribution n'a pas de formulaire).
- Tests : `ContributionServiceTest`, cas ajoutés à `ActivityServiceTest` et `ActivityControllerTest`, `ContributionManagementIntegrationTest` (modification → `PENDING` et invisible publiquement, passage en récurrent puis retour, refus pour un autre utilisateur, suppression d'une activité likée et récurrente). Parcours web vérifié dans le navigateur contre le backend réel : « Mes contributions » → Modifier → passage en « tous les mardis et jeudis 11h30–14h00 » → statut « En attente de validation » → formulaire rouvert pré-rempli → Supprimer.

## 0.9.40 — 2026-10-10

### Proposer une activité récurrente (food truck, marché…)
- Web : le formulaire « Proposer une activité » a une case « Revient chaque semaine (food truck, marché…) ». Cochée, on choisit les jours de passage (au moins un), l'heure d'arrivée et, au besoin, l'heure de départ ; les dates deviennent « À partir du » (aujourd'hui par défaut) et « Jusqu'au » (facultatif). Une heure de départ avant l'heure d'arrivée (ex. 19h → 1h) se termine le lendemain.
- API : `POST /api/v1/activities` accepte un objet facultatif `recurrence` (`daysOfWeek` en codes `MO`…`SU`, `startTime`/`endTime` en `HH:mm`, `from`/`until` en `yyyy-MM-dd`). Il crée, comme un import récurrent, un lieu, un `Schedule` portant la règle (`FREQ=WEEKLY;BYDAY=TU,TH`, fuseau Europe/Paris) et ses occurrences. La recherche (ADR-0006) ne trouve alors l'activité que les jours de passage, et la fiche affiche « Tous les mardis et jeudis · 11h30 → 14h00 ». Les colonnes `startDate`/`endDate` vont du premier au dernier passage. Sans `recurrence`, rien ne change.
- Limite : les occurrences sont créées sur un an au plus à partir du premier jour (y compris sans date de fin) ; au-delà, il faut reproposer l'activité. Récurrence hebdomadaire uniquement (pas de « 1er dimanche du mois »). Mobile : l'onglet Contribution n'a pas encore de formulaire.
- Tests : `ContributionRecurrenceTest`, `RecurringContributionServiceTest`, cas ajoutés à `ActivityControllerTest`, `RecurringContributionIntegrationTest` (food truck du lundi trouvé un lundi, pas le mardi). Vérifié : `mvn verify` (1075 tests, checkstyle), build web. ⚠️ Formulaire web non testé dans le navigateur.

## 0.9.39 — 2026-10-10

### LS-000 — Nom d'utilisateur unique
- Règles (`UsernamePolicy`) : 3 à 30 caractères parmi lettres sans accent, chiffres, « . », « _ » et « - », en commençant par une lettre ou un chiffre ; unique sans tenir compte de la casse (« Alice » et « alice » ne peuvent pas coexister) ; noms réservés refusés (admin, support, locallife, moderateur, api…). Appliquées à l'inscription, à la modification du profil et à la création d'utilisateur par un administrateur.
- Base (migration V57) : `username` devient `NOT NULL`, `VARCHAR(30)`, avec une contrainte de format et un index unique sur `lower(username)`. Les comptes existants sont d'abord mis en conformité, du plus ancien au plus récent : nom hors format nettoyé (« Jean Dupont » → « Jean-Dupont », trop court → « membre-<id> »), doublon suffixé (« alex », « Alex » → « alex », « Alex-2 »). Chaque renommage est signalé dans les journaux de migration (`LS-000 : compte … renommé de … en …`).
- Inscription Google : le nom proposé (nom Google ou début de l'email) est rendu conforme et libre (« Élodie Durand » → « Elodie-Durand », « -2 » si déjà pris).
- Profil : le nom reste modifiable avec les mêmes règles ; un compte peut garder son nom actuel (y compris un nom réservé hérité, comme le premier administrateur « admin ») ou en changer la casse.
- Nouveau `GET /api/v1/auth/username-availability?username=…` (public) : le web (inscription, profil) et le mobile (inscription) indiquent pendant la saisie si le nom est valide et libre.
- Tests : `UsernamePolicyTest`, cas ajoutés à `AuthServiceTest`, `UserServiceTest`, `UserRepositoryIntegrationTest` (unicité et format garantis par la base) ; tests d'intégration adaptés (noms d'utilisateur uniques). Vérifié : `mvn verify` (1062 tests, checkstyle), build web, mobile `tsc`/`eslint`/`jest` ; migration rejouée sur un jeu de doublons et de noms invalides ; parcours web contre le backend réel (nom trop court, espace, réservé, pris avec une autre casse, libre ; profil : « Admin » refusé, changement de casse enregistré).

## 0.9.38 — 2026-10-10

### Rayon modifiable depuis l'écran principal + carte dézoomée à l'échelle du monde
- Web : nouvelle puce « ◎ Rayon » dans la barre de filtres de l'Explorer — choix de 5 à 50 km ou « Sans rayon ». Avec un rayon : recherche `/nearby` et cadrage dans ce rayon autour de la ville favorite / de la position (« Autour de moi »), ou du centre de la carte s'il n'y en a pas (visiteur non connecté compris) ; sans rayon : recherche par zone visible. Le rayon choisi vaut pour la session, les préférences enregistrées ne changent pas.
- Mobile : bouton « ◎ Rayon » à côté de « Ma position », mêmes choix et même règle (cercle tracé, carte cadrée sur le rayon).
- Correctif : carte dézoomée à l'échelle du monde → « Aucune activité ». Deux causes : (1) en `geography`, une enveloppe de plus de 180° de longitude est prise par son « petit côté » et ne contient rien — le backend (`ActivityRepository#findWithinBounds`) ne filtre alors que sur la latitude, le cas courant gardant la comparaison `geography` et son index ; (2) au-delà d'un tour du monde, Leaflet renvoyait des longitudes hors de [-180, 180], refusées en 400 — le web les ramène aux bornes valides (comme le mobile).
- Tests : `ActivityWithinBoundsWideIntegrationTest` (zone monde, filtre de latitude, zones habituelles). Vérifié : `mvn verify` (1023 tests, checkstyle), build web, mobile `tsc`/`eslint` (0 erreur)/`jest`, bundles Android/iOS ; parcours web contre le backend réel (Lyon 10 km favori, activités à Lyon, Meyzieu ~13 km et Vienne ~27 km) : 10 km → Lyon ; 20 km → + Meyzieu ; 30 km → + Vienne ; sans rayon → zone visible ; dézoom monde → les trois ; visiteur anonyme : rayon 10 km autour du centre de la carte. ⚠️ Bouton « Rayon » mobile non testé visuellement (carte native uniquement).

## 0.9.37 — 2026-10-10

### Rayon favori : seules les activités dans le rayon
- Web : avec un rayon favori, la recherche reste la recherche par rayon (`/nearby`) autour de la ville favorite — ou de la position après « Autour de moi » —, quels que soient les déplacements et le zoom de la carte. Auparavant, le premier geste sur la carte basculait sur la zone visible (`/within-bounds`) et faisait apparaître des activités hors du rayon. Sans rayon favori (ou visiteur non connecté) : comportement inchangé.
- Mobile : même règle — avec un rayon favori et une ville favorite (ou après « Ma position », qui recentre le rayon et cadre la carte dessus), les activités sont chargées par `/nearby` dans ce rayon, la carte ne fait que l'afficher ; le rayon est matérialisé par un cercle comme sur le site.
- Vérifié dans le navigateur contre le backend réel (rayon 10 km autour de Lyon, une activité à Lyon et une à Vienne à ~27 km) : après un dézoom, l'ancienne version affichait l'activité de Vienne (bascule sur `within-bounds`), la nouvelle n'affiche que celle de Lyon (une seule recherche `nearby`). Build web ; mobile `tsc`/`eslint` (0 erreur)/`jest`, bundles Android/iOS.

## 0.9.36 — 2026-10-10

### Mobile : accueil de première connexion
- Même parcours que le site web : à l'ouverture d'une session (connexion ou restauration), si le compte n'a jamais enregistré ses préférences (`configured: false`), l'écran plein écran `bienvenue` s'ouvre (`OnboardingGate`, vérifié une fois par utilisateur et par lancement).
  1. **Préférences obligatoires** — ville (saisie vérifiée ou « Utiliser ma position »), rayon, au moins une catégorie ; pas de geste retour ni de bouton retour Android, seule « Se déconnecter » permet d'en sortir sans enregistrer.
  2. **Alerte facultative** — préremplie avec ces préférences, food trucks en option, notification sur ce téléphone par défaut (e-mail en option), ou « Plus tard ». La carte est ensuite cadrée et filtrée sur ces préférences.
- Interrupteur de canal (`ChannelSwitch`) et activation des notifications (`enablePushOrExplain`) partagés avec l'écran « Mes alertes » ; règles pures dans `features/onboarding/rules.ts` (testées).
- Vérifié : `tsc`, `eslint` (0 erreur), `jest` (47 tests), bundles Metro Android/iOS/web ; parcours complet dans le navigateur (version web de l'application, API simulée) : accueil ouvert pour un nouveau compte et pas pour un compte configuré, ville et catégorie exigées, message clair si la notification est indisponible, alerte créée, retour à la carte. ⚠️ Non testé sur un vrai téléphone (navigation native, bouton retour Android, notifications).

## 0.9.35 — 2026-10-10

### Accueil de première connexion (web)
- Backend : `GET /api/v1/users/me/preferences` renvoie `configured` (`false` tant que l'utilisateur n'a jamais enregistré ses préférences) — `UserPreferencesService#findPreferences`.
- Web : à la première connexion (`configured: false`), une fenêtre s'ouvre sur la carte :
  1. **préférences obligatoires** — ville (saisie vérifiée ou « Ma position »), rayon, au moins une catégorie ; ni bouton de fermeture ni touche Échap, seule « Se déconnecter » permet d'en sortir sans enregistrer ; dès l'enregistrement, la carte est cadrée et filtrée sur ces préférences ;
  2. **alerte facultative** — proposition de créer une alerte e-mail préremplie (catégories choisies, ville et rayon, food trucks en option), ou « Plus tard ».
- Les utilisateurs existants qui n'ont jamais enregistré de préférences verront aussi cette fenêtre une fois.
- Composant `OnboardingModal` (réutilise `CityField` et les puces de la page profil).
- Tests : `UserPreferencesControllerTest` (`configured` vrai/faux). Vérifié : `mvn verify` (1020 tests, checkstyle), `npm run build` ; drapeau `configured` contrôlé sur le backend réel ; parcours complet dans le navigateur (desktop et téléphone) : fenêtre affichée pour un nouvel utilisateur et non pour un utilisateur configuré, Échap sans effet, ville et catégorie exigées, carte recherchée aussitôt sur les préférences choisies, alerte créée ou « Plus tard » (appels géocodant la ville simulés, Nominatim étant injoignable depuis l'environnement de développement).

## 0.9.34 — 2026-10-10

### Page profil redessinée (web)
- Même identité visuelle que l'écran Explorer (vert profond, corail, fonds sable/papier, police Inter, cartes arrondies) au lieu de l'ancien style « administration » (bandeau blanc, boutons bleus).
- En-tête avec le logo LocalLife et « Retour à la carte » ; bandeau d'identité avec avatar (initiales), nom, email, rôle et date d'inscription.
- Mise en page en deux colonnes en desktop (« Mon compte » + déconnexion / « Paramètres de recherche » + « Mes alertes »), une seule colonne sur téléphone sans défilement horizontal.
- Catégories et rayons en puces sélectionnables (comme les filtres de la carte) à la place des cases à cocher et des listes déroulantes — de vraies cases/boutons radio masqués visuellement, donc toujours utilisables au clavier et au lecteur d'écran. Alertes présentées en cartes : critères, ville et rayon, canaux (e-mail / notification).
- Composants partagés `ProfileUi.tsx` (`ProfileCard`, `Chip`, `ChipGroup`, `Notice`) ; anciens styles du profil retirés de `styles.css`. Aucune logique modifiée.
- Vérifié : `npm run build` ; page affichée dans le navigateur contre le backend réel (desktop 1366 px et téléphone 390 px, aucune erreur JS, aucun débordement horizontal) ; enregistrement des paramètres par les puces (rayon 20 km + catégorie ajoutée, bien persistés).

## 0.9.33 — 2026-10-09

### Erreurs plus explicites (route absente, profil)
- Backend : une route inexistante (`NoResourceFoundException`, levée par Spring MVC faute de contrôleur) renvoie désormais **404** « Route introuvable : GET /api/v1/… », journalisée en avertissement, au lieu d'un 500 « Erreur serveur non gérée ». Cas rencontré avec un backend pas encore relancé après les dernières fusions alors que le frontend l'était déjà.
- Web : les erreurs de chargement de la page profil (paramètres de recherche, alertes) affichent la cause renvoyée par le backend et le code HTTP (`describeErrorResponse` dans `apiClient.ts`), au lieu d'un message générique.
- Tests : `GlobalExceptionHandlerTest` (404 sur route absente). Vérifié : `mvn verify` (1004 tests, checkstyle), `npm run build` ; appel réel d'une route inexistante (404 + avertissement) ; page profil dans le navigateur avec un backend simulé pas à jour (« Impossible de charger tes paramètres : Route introuvable : GET /api/v1/users/me/preferences (HTTP 404). ») et avec le backend réel (aucune erreur).

## 0.9.32 — 2026-10-09

### Carte cadrée sur la ville, le rayon et les catégories favoris
- Web : une fois connecté, la carte n'est plus seulement centrée sur la ville favorite au zoom par défaut — elle est **cadrée sur le rayon favori** (`fitBounds`, zoom fractionnaire `zoomSnap: 0.25` pour ne pas arrondir à un cadrage jusqu'à deux fois trop large), et ce rayon est matérialisé par un cercle en pointillés. Auparavant, la recherche initiale utilisait bien le rayon, mais elle était aussitôt remplacée par celle de la zone visible (beaucoup plus petite) : le rayon favori était perdu à l'écran. Catégories favorites cochées, inchangé. « Autour de moi » garde le rayon favori. Sans rayon favori (ou visiteur non connecté) : comportement d'origine.
- Mobile : un rayon favori sans ville favorite cadre désormais la carte autour de son centre actuel (avec une ville, déjà cadrée sur le rayon).
- Vérifié dans le navigateur contre le backend réel (Lyon, 10 km, Sport) : recherche initiale `nearby` rayon 10 km + Sport, puis zone visible ≈ 20 km × 21 km en desktop (≈ 34 km × 35 km avant le zoom fractionnaire), 20,8 km de large en format téléphone ; visiteur anonyme inchangé. Build web, mobile `tsc`/`eslint`/`jest` (44 tests).

## 0.9.31 — 2026-10-09

### Alertes sur les nouveaux food trucks
- Migration `V55` : date de publication des food trucks (`food_truck.published_at`, même trigger que les activités : premier passage au statut `PUBLISHED`, figée ensuite ; `NULL` pour les food trucks existants), option `activity_alert.include_food_trucks` et table `activity_alert_food_truck_sent` (jamais deux envois).
- Une alerte couvre ses catégories d'activités et/ou les nouveaux food trucks (au moins l'un des deux) ; un food truck est « nouveau » s'il a été publié après la création de l'alerte, dans le rayon. API : `includeFoodTrucks` dans la création et la réponse des alertes ; `publishedAt` sur les food trucks (renvoyé dès la création).
- Envoi : un seul message par alerte pour les activités et les food trucks (« 1 nouveau food truck », « 3 nouveautés »…), e-mail en deux sections si besoin ; toucher la notification d'un seul food truck ouvre sa fiche dans l'application.
- Web et mobile : option « 🚚 Food trucks » à la création d'une alerte, affichée dans la liste.
- Tests : notifier (alerte mixte, food trucks seuls, option désactivée), service (alerte sans catégorie avec food trucks), intégration (rayon, nouveauté, option, déjà envoyé, trigger), `FoodTruckControllerIntegrationTest` (`publishedAt` à la création) ; mobile : `alertCriteria`, routage vers la fiche food truck.
- Vérifié : `mvn verify` (1003 tests, checkstyle), `npm run build` (web), mobile `tsc`/`eslint` (0 erreur)/`jest` (44 tests). De bout en bout : food truck créé par l'API après une alerte « Food trucks » à Lyon → e-mail « 1 nouveau food truck près de Lyon » et notification ouvrant sa fiche (faux service Expo local), food truck marqué envoyé ; alerte sans catégorie ni food trucks refusée (400).

## 0.9.30 — 2026-10-09

### Date de publication des activités
- Migration `V54` : colonne `activity.published_at`, renseignée par le trigger `trg_activity_set_published_at` au premier passage au statut `PUBLISHED` (import créant l'activité publiée, validation d'une contribution par un administrateur, SQL direct). Une fois posée, elle ne change plus : une activité archivée puis republiée garde sa première date. Activités publiées avant la migration : `NULL` (date inconnue, aucune valeur inventée).
- `Activity#publishedAt` en lecture seule côté Java (`@ReadOnlyProperty`) : l'application ne l'écrit jamais, une mise à jour ne peut pas l'effacer. Constructeur sans cette date conservé pour les créations/mises à jour (aucun appel existant modifié).
- Exposée dans `ActivityResponse` et `ActivityDetailResponse` (`publishedAt`) ; affichée « Publiée le … » dans la fiche d'une activité (site web et application mobile).
- Alertes : une activité est « nouvelle » si elle a été **publiée** après la création de l'alerte (`published_at > created_at`). Corrige le cas d'une contribution soumise avant l'alerte mais validée après, qui n'était pas envoyée. Repli sur l'ancien repère par id pour les activités publiées avant V54.
- Tests : `ActivityPublishedAtIntegrationTest` (création publiée, passage PENDING → PUBLISHED, date figée après archivage/republication ou écrasement, lecture et sauvegarde par le repository), `ActivityAlertRepositoryIntegrationTest` (contribution validée après l'alerte).
- Vérifié : `mvn verify` (997 tests, checkstyle), `npm run build` (web), mobile `tsc`/`eslint`/`jest`. De bout en bout : contribution en attente créée avant une alerte, validée par un administrateur via l'API → `publishedAt` renseigné dans la fiche, et activité reçue dans l'e-mail d'alerte.

## 0.9.29 — 2026-10-09

### Mobile : paramètres de recherche et alertes en notification
- Migration `V53` : canaux par alerte (`notify_email`, par défaut pour les alertes existantes, et `notify_push`, au moins un des deux) et table `user_push_token` (jetons Expo Push des appareils, réattribués au compte connecté, supprimés à la déconnexion ou quand Expo signale `DeviceNotRegistered`).
- Backend : `PUT`/`DELETE /api/v1/users/me/push-tokens`, `PATCH /api/v1/users/me/alerts/{id}` (canaux). `POST .../alerts` accepte `notifyEmail`/`notifyPush` (e-mail seul par défaut, comportement du site web). `ExpoPushService` (API Expo Push, lots de 100, `EXPO_ACCESS_TOKEN` optionnel). `ActivityAlertNotifier` envoie chaque récapitulatif par e-mail (adresse vérifiée) et/ou notification ; les activités sont marquées envoyées dès qu'un canal a réussi. Toucher la notification ouvre la fiche de l'activité (une seule) ou la carte.
- Mobile : onglet Compte → « Paramètres de recherche » (ville par saisie vérifiée ou « Utiliser ma position », rayon, catégories) et « Mes alertes » (liste, interrupteurs Notification / E-mail, suppression, création préremplie avec les paramètres). La carte « Découvrir » se cadre sur la ville et le rayon par défaut et filtre sur les catégories par défaut (puce « Tout afficher »), relus à chaque retour sur la carte. Notifications : `expo-notifications`, permission demandée seulement à l'activation, jeton envoyé à chaque ouverture de session et retiré à la déconnexion ; `EAS_PROJECT_ID` requis, pas de notification dans Expo Go sur Android.
- Mobile : type `Category` aligné sur `CategoryResponse` (`code`/`label`/`active`), il ne correspondait plus au backend.
- Web : la liste des alertes indique leurs canaux.
- Tests : `ExpoPushServiceTest`, `PushTokenControllerTest`, notifier (push seul, appareil désinscrit, e-mail en échec mais push réussi), service/contrôleur (canaux), intégration (destinataires push, `updateChannels`) ; mobile : `routing.test.ts`, `options.test.ts`, `regionForRadius`.
- Vérifié : `mvn verify` (992 tests, checkstyle), `npm run build` (web), mobile `tsc`, `eslint` (0 erreur), `jest` (41 tests), bundles Metro Android/iOS/web. De bout en bout avec un faux service Expo local : enregistrement des jetons par l'API (400 si invalide, 401 sans JWT), notification reçue par les deux appareils avec l'activité à ouvrir, appareil désinscrit supprimé, aucun e-mail pour une alerte notification seule, retrait du jeton à la déconnexion ; échec d'Expo → rien marqué envoyé, renvoi au passage suivant. Écrans mobiles testés dans le navigateur (version web de l'application, API simulée) — ce test a révélé un plantage sur le web (`useLastNotificationResponse`), corrigé. ⚠️ Non testé sur un vrai téléphone (pas d'appareil ni de build EAS ici) : réception réelle d'une notification, carte native et géolocalisation restent à valider sur un build de développement.

## 0.9.28 — 2026-10-09

### Alertes e-mail sur les nouvelles activités
- Migration `V52` : tables `activity_alert` (ville + coordonnées, rayon 1–50 km, jeton de désinscription), `activity_alert_category` (une ou plusieurs catégories, FK sur `category.code`) et `activity_alert_sent` (activités déjà envoyées, jamais deux fois). Suppression en cascade avec l'utilisateur.
- Endpoints `GET`/`POST /api/v1/users/me/alerts` et `DELETE /api/v1/users/me/alerts/{id}` (JWT requis), `POST /api/v1/alerts/unsubscribe` (public, lien de l'email). Au moins une catégorie, un rayon et une ville existante (par nom ou par géolocalisation, même vérification que les paramètres de recherche — logique déplacée dans `GeocodingService#resolveCity`). 10 alertes au plus par utilisateur.
- `ActivityAlertNotifier` : envoi planifié (par défaut chaque jour à 8 h, heure de Paris, `app.alerts.cron` / `ALERTS_CRON`) d'un e-mail récapitulatif par alerte, uniquement aux adresses vérifiées et seulement s'il y a du nouveau. Sont retenues les activités publiées après la création de l'alerte (`activity` n'ayant pas de date de création, repère sur l'id), à venir, dans le rayon (occurrences et lieux réels, même logique que `/nearby`), d'au moins une des catégories, et non créées par le destinataire. 20 activités détaillées au plus, 100 traitées par alerte et par envoi. Une activité n'est marquée envoyée qu'après l'envoi réussi ; un échec n'empêche pas les autres alertes.
- Frontend : section « Mes alertes e-mail » sur `/profile` (liste, suppression, création préremplie avec les paramètres de recherche) ; page `/alerts/unsubscribe`. Le champ ville (saisie vérifiée + « Utiliser ma position ») est désormais un composant partagé (`CityField`).
- Tests : `ActivityAlertServiceTest`, `ActivityAlertNotifierTest`, `ActivityAlertControllerTest`, `ActivityAlertRepositoryIntegrationTest` (requête PostGIS : catégorie, rayon, nouveauté, statut, date passée, déjà envoyé, adresse non vérifiée).
- Vérifié : `mvn verify` (976 tests, checkstyle) contre PostgreSQL/PostGIS 16, `npm run build`, et de bout en bout : backend lancé avec l'envoi toutes les minutes et un serveur SMTP local — e-mail reçu avec les 2 activités attendues (celle hors rayon exclue), aucun renvoi au passage suivant, lien de désinscription fonctionnel (et refusé une seconde fois), section du profil testée dans le navigateur. ⚠️ Nominatim injoignable depuis l'environnement de développement : la création d'une alerte via l'API (qui géocode la ville) n'a été testée qu'avec des réponses simulées.

## 0.9.27 — 2026-10-09

### Paramètres utilisateur : catégories, ville et rayon par défaut
- Migration `V51` : table `user_preferences` (une ligne par utilisateur : `default_city` + coordonnées géocodées, `default_radius_km` borné à 1–50 km) et table `user_preference_category` (codes de catégories, FK sur `category.code`). Suppression en cascade avec l'utilisateur.
- Nouveaux endpoints `GET`/`PUT /api/v1/users/me/preferences` et `GET /api/v1/users/me/preferences/city` (JWT requis, id lu dans le token). La ville est choisie **par géolocalisation** (géocodage inverse au niveau ville, seule la ville est conservée, jamais la position exacte) **ou par saisie** ; dans les deux cas elle doit exister : `GeocodingService#findCity` ne retient que les lieux de type ville/village (`featureType=city`) et enregistre leur nom officiel. Une ville inchangée garde ses coordonnées sans nouvel appel. `400` si rayon hors bornes, catégorie inconnue/inactive, position incomplète ou ville introuvable ; `503` si le géocodage est indisponible.
- Frontend : section « Paramètres de recherche » sur la page `/profile` (ville, rayon, catégories cochées). Bouton « 📍 Utiliser ma position » ; une ville saisie est vérifiée dès la sortie du champ et remplacée par son nom officiel, l'enregistrement est bloqué tant qu'elle est introuvable. À l'ouverture de la carte, un utilisateur connecté voit directement ses catégories cochées, la carte centrée sur sa ville par défaut (à la place de la géolocalisation automatique, « Autour de moi » reste disponible) et la recherche initiale faite avec son rayon. Champ vide = comportement standard inchangé.
- Tests : `UserPreferencesServiceTest`, `UserPreferencesControllerTest`, `UserPreferencesRepositoryIntegrationTest`, `GeocodingServiceTest` (`findCity`/`reverseCity`).
- Vérifié : `mvn verify` (checkstyle compris) contre PostgreSQL/PostGIS 16, `npm run build`, et parcours dans le navigateur (enregistrement des paramètres puis ouverture de la carte ; ville inexistante, ville normalisée et géolocalisation avec réponses Nominatim simulées). ⚠️ Nominatim n'était pas joignable depuis l'environnement de développement : les appels réels `featureType=city` et `/reverse` restent à valider en local.

## 0.9.26 — 2026-09-23

### Vérification de l'adresse email à l'inscription
- Migration `V31` : colonne `users.email_verified` (les comptes existants sont marqués vérifiés) et table `email_verification_token` (même patron que `password_reset_token`, seule l'empreinte SHA-256 du token est stockée).
- `EmailVerificationService` : émission d'un token à usage unique valable 24 h à l'inscription, confirmation idempotente, renvoi sans énumération de comptes (un lien par minute au plus). Un échec d'envoi SMTP n'annule pas l'inscription (journalisé, lien renvoyable).
- `AuthService` : email nettoyé (`trim`), format renforcé (partie locale ≤ 64, domaine sans point initial/final ni points consécutifs, extension ≥ 2, longueur ≤ 254). Connexion refusée en `403` tant que l'adresse n'est pas confirmée (après vérification du mot de passe, donc sans fuite d'existence du compte).
- Comptes considérés comme vérifiés d'emblée : administrateur initial (`bootstrapFirstAdmin`), comptes créés via Google, comptes ayant réinitialisé leur mot de passe via le lien reçu par email. Un compte classique non vérifié rattaché ensuite à Google est confirmé **et son mot de passe effacé** (empêche de garder l'accès d'un tiers ayant créé le compte avec l'adresse d'autrui).
- Nouveaux endpoints `POST /api/v1/auth/verify-email` et `POST /api/v1/auth/resend-verification`.
- Frontend : page `/verify-email`, message de consigne après inscription, bouton « Renvoyer l'email de confirmation » sur la page de connexion en cas de `403`.
- Tests : `EmailVerificationServiceTest` (nouveau), `AuthServiceTest`, `AuthControllerTest`, `AuthenticationFlowIntegrationTest` (parcours inscription → 403 → lien → connexion) ; les helpers d'inscription des autres tests d'intégration confirment l'adresse directement en base.
- Vérifié : `npx tsc --noEmit` et `npm run build` (frontend). ⚠️ Backend non compilé dans cette session (pas d'accès à Maven Central) : **`mvn verify` reste à lancer par Alex avant tout merge**.

## 0.9.25 — 2026-09-18

### LL-EF-016 — Ajouter un lien web et un contact à un événement
- Réutilise intégralement le modèle `ActivityLink`/`ActivityLinkService` déjà construit par LL-11008 (Sprint 11) — aucune nouvelle table, aucun nouveau mécanisme de validation (URL/email/téléphone déjà couverts).
- `CreateActivityRequest` (backend) gagne trois champs optionnels `url`/`phone`/`email`. Validés **avant** la création de l'activité (`ActivityLinkService#validateBeforeCreate`, nouvelle méthode publique) pour éviter une activité orpheline en cas de contact invalide, puis attachés comme `ActivityLink` (types `WEBSITE`/`PHONE`/`EMAIL`) une fois l'activité créée.
- Formulaire de contribution (frontend) : trois nouveaux champs facultatifs (site web, téléphone, email).
- L'affichage dans le détail d'une activité existait déjà (LL-11015) : liens sécurisés (`rel="noreferrer" target="_blank"`), appel téléphonique (`tel:`) et ouverture du client email (`mailto:`) — aucun changement nécessaire côté affichage.
- Tests ajoutés : `ActivityControllerTest` (attache les liens, aucun lien si absents, 400 + aucune activité créée si contact invalide), `ActivityLinkServiceTest` (`validateBeforeCreate`).
- Vérifié par une compilation et un build frontend réels (`npx tsc --noEmit`, `npm run build`). ⚠️ Backend non vérifiable dans cette session (pas d'accès à Maven Central ni à une base PostgreSQL réelle) : **`mvn verify` reste à lancer par Alex avant tout merge**.

## 0.9.24 — 2026-09-18

### LL-EF-015 — « Y aller » vers un événement
- Bouton « Y aller » dans le détail d'une activité (frontend uniquement, aucun changement backend), à côté du bouton « Ajouter à Google Agenda » (LL-EF-013).
- `frontend/src/utils/directions.ts` : url universelle Google Maps (`google.com/maps/dir/?api=1&destination=...`) — coordonnées GPS en priorité, adresse en secours, conformément au ticket. Ouvre l'application de navigation compatible de l'utilisateur ; LocalLife n'implémente aucun système de navigation propre.
- Vérifié par une compilation et un build réels (`npx tsc --noEmit`, `npm run build`).

## 0.9.23 — 2026-09-18

### LL-EF-013 — Ajouter un événement à Google Agenda
- Bouton « Ajouter à Google Agenda » dans le détail d'une activité (frontend uniquement, aucun changement backend).
- Préremplit titre, description, date/heure de début et de fin, adresse (repli sur les coordonnées GPS si aucune adresse), via l'url de modèle d'événement publique de Google (`calendar.google.com/calendar/render?action=TEMPLATE`) — aucun identifiant Google stocké côté serveur, aucune authentification requise (critère d'acceptation explicite du ticket).
- Date/heure de début et de fin tirées de la première occurrence non annulée quand la fiche détaillée est chargée (`frontend/src/utils/googleCalendar.ts`), avec repli sur `Activity.startDate` (+ 2h par défaut) sinon.
- L'utilisateur reste libre de modifier chaque champ avant d'enregistrer : c'est le formulaire de Google Agenda lui-même qui s'ouvre, LocalLife ne fait que le pré-remplir.
- Vérifié par une compilation et un build réels (`npx tsc --noEmit`, `npm run build`).

## 0.9.22 — 2026-09-18

### LL-11017 — Documenter le nouveau modèle (clôture Sprint 11)
- `docs/02_Architecture/ARCHITECTURE.md` : nouvelle section « Modèle événementiel » (diagramme Activity → Schedule → Recurrence → Occurrence → Location, exceptions, sources externes, résumé/détail, future distinction Basic/Premium), section « Pipeline d'import » et liste des modules mises à jour.
- `docs/02_Architecture/COLLECTOR_CONTRACT.md` : mises à jour LL-11010 (`ExternalActivity`) et LL-11011 (`CollectedActivity` restructuré).
- `docs/02_Architecture/FOOD_TRUCK_CONTRACT.md` : clarification entre le module `foodtruck` (inchangé) et une activité à plusieurs schedules (« food truck multi-lieux »).
- `docs/PROJECT_STATUS.md`, `docs/01_Product/BACKLOG.md`, `docs/04_Project/ROADMAP.md` : clôture du Sprint 11, récapitulatif ticket par ticket (LL-11001 à LL-11017).
- Voir `docs/PROJECT_STATUS.md` (section LL-11017) pour le récapitulatif complet du sprint, y compris les trois bugs découverts et corrigés grâce au premier vrai run `mvn verify` en CI (occurrences annulées exclues de la fiche détaillée, compteur `created`/`updated` d'`ImportService`, pollution de la table `source`).

## 0.9.21 — 2026-09-17

### Correctifs CI sur la PR LL-11016 (suite)
- `RealSchedulingScenariosIntegrationTest` utilisait `type="MANUAL"` pour sa propre source de test — cause réelle des 6 lignes `MANUAL` observées en CI (1 de la migration V8 + 5 committées par les 5 méthodes de ce test, non `@Transactional` par nécessité — même style que `ActivityControllerIntegrationTest`, requêtes HTTP réelles via un thread serveur séparé). Corrigé : `type="API"`.
- `ImportService#importFrom` : `created`/`updated` s'incrémentaient après `enrichActivity(...)` — une exception dans l'enrichissement faisait compter l'élément entier en erreur alors que l'`Activity` venait d'être sauvegardée avec succès. Corrigé : compteur incrémenté immédiatement après la sauvegarde, enrichissement isolé (`enrichActivitySafely`), son échec journalisé sans incrémenter `errors`. Voir l'addendum 2 de `docs/ADR-0010-cas-reels-programmation.md`.
- Test ajouté : `ImportServiceTest.importAll_ShouldStillCountCreated_WhenEnrichmentFailsAfterActivitySaved`.

## 0.9.20 — 2026-09-17

### Correctif CI sur la PR LL-11016 — validation du type de source
- Le premier run CI réel (GitHub Actions, base PostgreSQL fraîche) de la PR #14 a révélé que `source.type = 'MANUAL'` comptait 6 lignes au lieu d'une, faisant échouer `SourceRepository#findByType("MANUAL")` en cascade dans des dizaines de tests (`ActivityService#createActivity`, `SourceService#deleteSource`, tout appel à `manualSourceId()`).
- `SourceService#createSource`/`updateSource` ne rejetaient `type` que s'il était vide, jamais spécifiquement `MANUAL` — n'importe quel appel à `POST`/`PUT /api/v1/sources` avec ce type était accepté, créant ou transformant une source en une seconde source « réservée ». Corrigé : `validate()` rejette désormais explicitement ce type.
- Nouvelle migration `V30__enforce_single_manual_source.sql` (index unique partiel `WHERE type = 'MANUAL'`) en défense supplémentaire côté base.
- Tests ajoutés : `SourceServiceTest` (2 cas), `SourceControllerIntegrationTest` (1 cas HTTP). Voir l'addendum de `docs/ADR-0010-cas-reels-programmation.md`.
- Le chemin de code exact ayant produit les 6 lignes lors de ce run précis n'a pas pu être identifié avec certitude par relecture statique seule.

## 0.9.19 — 2026-09-17

### LL-11016 — Tester les cas réels de programmation
- Ticket du Sprint 11 (`docs/05_Sprints/SPRINT_11.md`) : vérifier que cinq scénarios concrets (événement ponctuel, récurrent, food truck multi-lieux, exception, fiche riche) sont effectivement représentables par le modèle événementiel riche du Sprint 11.
- **Bug corrigé** (révélé en construisant le « Cas 4 — Exception ») : `ActivityController#toDetailResponse` (LL-11013) excluait les occurrences annulées de la fiche détaillée (« occurrences pertinentes » interprété à tort comme « non annulées », voir ADR-0007) — une occurrence `CANCELLED` doit au contraire rester visible (« 15/09 → annulé »). Utilise désormais `OccurrenceService#findByScheduleId` (toutes les occurrences) plutôt que `findEffectiveByScheduleId` (non annulées) pour cette fiche — cette dernière reste utilisée telle quelle pour la recherche (LL-11012), où l'exclusion des occurrences annulées est en revanche correcte. Voir `docs/ADR-0010-cas-reels-programmation.md`.
- Nouveau test d'intégration `RealSchedulingScenariosIntegrationTest` : cinq scénarios construits directement via le modèle riche (Location/Schedule/Occurrence/Media/ActivityLink/Accessibility/ActivityTag) et vérifiés via une vraie requête HTTP sur `GET /api/v1/activities/{id}`.
- Frontend : les exceptions (occurrence annulée ou déplacée) s'affichent désormais sous la ligne d'horaire concernée dans la fiche détaillée (`ActivityDetail.occurrences`, `formatDateOfDay`) — vérifié par une compilation et un build réels (`npx tsc --noEmit`, `npm run build`).
- ⚠️ Backend non vérifiable dans cette session (pas d'accès à Maven Central ni à une base PostgreSQL réelle) : les cinq scénarios ont été construits et relus manuellement avec attention, mais **`mvn verify` reste à lancer par Alex avant tout merge**.

## 0.9.18 — 2026-09-16

### LL-11015 — Exploiter le nouveau modèle dans le frontend
- Ticket du Sprint 11 (`docs/05_Sprints/SPRINT_11.md`) : le frontend n'affichait toujours que les champs plats (titre, date, ville, adresse), sans exploiter le modèle événementiel riche exposé par LL-11013 (`GET /api/v1/activities/{id}`).
- Fiche détaillée d'une activité (carte et liste) : galerie d'images, description longue, horaires détaillés (récurrence traduite en français, jamais la chaîne RRULE brute — voir `frontend/src/utils/recurrence.ts`), lieux, liens/contacts (mailto/tel/http selon le type), accessibilité, tags, informations complémentaires — chargés à la demande (`GET /api/v1/activities/{id}`) à l'ouverture du détail, avec état de chargement/erreur explicite.
- Image principale désormais visible sur la carte (popups), la liste et les favoris : `ActivityResponse` (backend) gagne un champ `mainImageUrl` (nouvelle variante de `from()`, les précédentes préservées à l'identique — aucun endpoint existant cassé), résolu aussi dans `LikeController` (favoris).
- « Food truck multi-lieux » (plusieurs schedules pointant vers des lieux différents) affiché naturellement, une ligne par schedule dans les horaires — aucun traitement spécifique nécessaire.
- Voir `docs/ADR-0009-frontend-modele-riche.md` pour le détail des décisions de conception, notamment ce qui reste hors périmètre (occurrences annulées, changement exceptionnel de lieu par occurrence — à reconsidérer pour LL-11016).
- Vérifié par une compilation et un build frontend réels dans cette session (`npx tsc --noEmit`, `npm run build`, npm accessible contrairement à Maven) : aucune erreur. ⚠️ Le backend (`ActivityResponse`/`ActivityController`/`LikeController`) reste non vérifiable ici (pas d'accès à Maven Central ni à une base PostgreSQL réelle) : **`mvn verify` reste à lancer par Alex avant tout merge**.

## 0.9.17 — 2026-09-16

### LL-11014 — Préparer la fiche Premium
- Ticket du Sprint 11 (`docs/05_Sprints/SPRINT_11.md`) : préparer l'architecture pour qu'une future offre Premium puisse réserver la fiche détaillée (LL-11013) aux utilisateurs qui y ont droit, sans jamais faire connaître au domaine la notion de forfait (règle fondamentale explicite du ticket).
- Nouveau module `com.locallife.backend.premium` : interface `PremiumAccessPolicy` (`hasPremiumAccess(Authentication)`), seul point de décision — délibérément séparé du module `activity`, aucune entité de domaine modifiée.
- `GET /api/v1/activities/{id}` (`ActivityController#getActivityById`) retourne désormais `ActivityDetailResponse` ou `ActivitySummaryResponse` selon `PremiumAccessPolicy#hasPremiumAccess`, au lieu de toujours renvoyer la fiche détaillée.
- `StubPremiumAccessPolicy` (implémentation actuelle) accorde l'accès complet à tout le monde — aucun système Premium n'existe encore (hors périmètre du ticket), ce choix préserve le comportement observable de LL-11013 sans régression. Voir `docs/ADR-0008-preparation-premium.md` pour le détail de cette décision et l'alternative écartée (champ `premium` sur `User`).
- Tests ajoutés : `StubPremiumAccessPolicyTest`, deux nouveaux cas dans `ActivityControllerTest` (accès accordé → détail, refusé → résumé), test d'intégration confirmant la non-régression pour un appelant anonyme.
- ⚠️ Backend non compilable/testable dans cette session (pas d'accès réseau à Maven Central ni à une base PostgreSQL réelle) : vérifié par relecture manuelle attentive uniquement, **`mvn verify` reste à lancer par Alex avant tout merge**.

## 0.9.16 — 2026-09-16

### LL-11013 — Séparer les DTO résumé/détail d'activité
- Ticket du Sprint 11 (`docs/05_Sprints/SPRINT_11.md`) : `GET /api/v1/activities` et `GET /api/v1/activities/{id}` exposaient directement l'entité de domaine `Activity`, sans distinction entre les besoins d'une carte/liste et ceux d'une fiche détaillée, et sans exposer le modèle événementiel riche du Sprint 11 (LL-11001 à LL-11010).
- Nouveau DTO `ActivitySummaryResponse` pour `GET /api/v1/activities` : exactement les champs listés par le ticket (id, title, category, date, city, address, latitude, longitude, mainImageUrl) — plus léger que l'entité `Activity` complète, aucune donnée interne (sourceId, importKey, status, etc.) exposée.
- Nouveau DTO `ActivityDetailResponse` pour `GET /api/v1/activities/{id}` : description, longDescription, schedules, occurrences pertinentes (non annulées), locations (dédoublonnées), media, links, accessibility, tags, informations complémentaires — voir `docs/ADR-0007-dto-resume-detail-activite.md` pour le détail des décisions de conception (records imbriqués dédiés plutôt qu'exposition directe des entités de domaine, interprétation d'« occurrences pertinentes », dédoublonnage des locations).
- `ActivityResponse` (utilisé par `/nearby`/`/within-bounds`/les activités likées) volontairement non modifié — hors périmètre de ce ticket, aucun endpoint existant cassé.
- `ActivityController` : 9 nouvelles dépendances injectées pour assembler la fiche détaillée (ScheduleService, OccurrenceService, LocationService, MediaService, ActivityMediaService, ActivityLinkService, AccessibilityService, ActivityTagService, ActivityMetadataService).
- OpenAPI mis à jour automatiquement via les annotations Springdoc déjà en place sur ces deux endpoints.
- Tests mis à jour (`ActivityControllerTest`, `ActivityControllerIntegrationTest`) et nouveaux tests ajoutés vérifiant l'absence de champs sensibles et la présence de l'arborescence riche dans la fiche détaillée.
- ⚠️ Backend non compilable/testable dans cette session (pas d'accès réseau à Maven Central ni à une base PostgreSQL réelle) : vérifié par relecture manuelle attentive uniquement (correspondance des champs entre chaque record de domaine et les DTO), **`mvn verify` reste à lancer par Alex avant tout merge**.

## 0.9.15 — 2026-09-15

### LL-11012 — Adapter la recherche au modèle événementiel riche
- Ticket du Sprint 11 (`docs/05_Sprints/SPRINT_11.md`) : la recherche (`GET /api/v1/activities`, `/nearby`, `/within-bounds`) ne prenait en compte que les colonnes plates historiques d'`Activity` (`latitude`/`longitude`/`city`/`start_date`/`end_date`), ignorant le modèle événementiel riche introduit par le Sprint 11 (`Location`/`Schedule`/`Occurrence`) et alimenté par LL-11011 pour les activités importées.
- `ActivityRepository#findAllFiltered`/`findWithinRadius`/`findWithinBounds` réécrites : une activité correspond désormais via ses occurrences non annulées (« exceptions prises en compte ») et leur lieu effectif (`COALESCE(occurrence.location_id, schedule.location_id)`, « changement exceptionnel de lieu ») quand elle a au moins un `schedule` ; repli intégral sur les colonnes plates historiques sinon (contributions manuelles LL-2012/LL-3012, qui ne créent encore ni `Location` ni `Schedule`) — voir `docs/ADR-0006-recherche-occurrences.md` pour le détail de cette décision et la couverture de chaque cas demandé par le ticket (ponctuelle, récurrente, plusieurs schedules/villes, exceptions, changement exceptionnel de lieu).
- `GET /api/v1/activities` accepte un nouveau paramètre `date` (ISO-8601, sans fenêtre par défaut) combinable avec `city`, conformément à l'exemple du ticket (`?city=Avignon&date=2026-09-15`). `ActivityService#findAll`/`ActivityController#getAllActivities` mis à jour en conséquence.
- Recherche géographique PostGIS (`/nearby`, `/within-bounds`) préservée : tri par distance croissante toujours correct via une sous-requête corrélée (`LEAST`/`COALESCE` entre la meilleure distance occurrence et celle du repli), sans toucher à la forme du `SELECT` principal (voir « Alternative écartée » dans l'ADR).
- Fuseau horaire d'une occurrence résolu via `COALESCE(location.timezone, 'Europe/Paris')`, cohérent avec `NormalizationService#resolveZone` (LL-11011) à l'écriture.
- `docs/02_Architecture/LOCATION_CONTRACT.md` (paramètre `date`), `GEO_SEARCH_CONTRACT.md` et `BOUNDING_BOX_SEARCH_CONTRACT.md` mis à jour pour pointer vers l'ADR-0006.
- Tests d'intégration ajoutés à `ActivityRepositoryIntegrationTest` couvrant chaque cas du tableau de l'ADR-0006 (ville via occurrence, ville+date pour une activité récurrente, occurrence annulée exclue, changement exceptionnel de lieu, plusieurs schedules/villes, compatibilité activité sans schedule, recherche géographique par rayon/zone via occurrence).
- ⚠️ Backend non compilable/testable dans cette session (pas d'accès réseau à Maven Central ni à une base PostgreSQL/PostGIS réelle depuis le sandbox) : requêtes SQL vérifiées par relecture manuelle attentive uniquement (voir ADR-0006), **`mvn verify` reste à lancer par Alex avant tout merge**.

## 0.9.14 — 2026-09-13

### LL-11011 — Adapter le pipeline d'import au modèle événementiel riche
- Ticket du Sprint 11 (`docs/05_Sprints/SPRINT_11.md`) : le pipeline d'import (`ImportService`) ne persistait jusqu'ici que l'`Activity` cœur, ignorant les entités riches ajoutées par LL-11001 à LL-11010 (`Location`, `Schedule`, `Occurrence`, `Media`, `ActivityLink`, `Accessibility`, `ActivityTag`, `ActivityMetadata`).
- `CollectedActivity` restructuré : les champs plats `latitude`/`longitude`/`address`/`city`/`postalCode`/`startDate`/`endDate` sont remplacés par des sous-modèles dédiés — `CollectedLocation`, `CollectedTiming` (en liste, plusieurs horaires désormais possibles pour un même événement), `CollectedMedia`, `CollectedLink`, `CollectedAccessibility`, plus `tags` et `registrationInfo`.
- `OpenAgendaCollector` réécrit : extrait désormais `timings` (tous les créneaux, plus seulement `nextTiming`), l'image principale, les liens et informations d'inscription, l'accessibilité, et l'ensemble des mots-clés (auparavant seul le premier, utilisé comme catégorie, était conservé). ⚠️ Structures JSON (`image`/`links`/`registration`/`accessibility`) non vérifiées contre l'API réelle en sandbox (pas d'accès réseau) — voir la javadoc de la classe.
- `NormalizationService` : `normalize()` (cœur `Activity`) inchangé dans son rôle, complété par sept nouvelles méthodes pures (`normalizeLocation`, `normalizeSchedule`, `normalizeOccurrences`, `normalizeMedia`, `normalizeLinks`, `normalizeAccessibility`, `normalizeTags`, `normalizeMetadata`) ; validation étendue à `recurrenceRule` (RRULE invalide = élément rejeté avant toute persistance).
- `ImportService` orchestre désormais la persistance complète après sauvegarde de l'`Activity` (`enrichActivity`) : `Location`/`Media`, entités partageables, sont réutilisées via `findOrCreate` (nouvelles méthodes) plutôt que dupliquées ; toutes les autres entités liées sont remplacées (supprimées puis reconstruites) à chaque réimport (`clearEnrichment`), via de nouvelles méthodes `deleteByActivityId`/`deleteByScheduleId` ajoutées aux repositories/services concernés.
- Décisions de conception documentées dans `docs/ADR-0005-import-pipeline-enrichment.md` (restructuration de `CollectedActivity`, stratégie de remplacement plutôt que fusion, absence de RRULE produit par OpenAgenda).
- Tests mis à jour pour le nouveau modèle (`DeduplicationServiceTest`, `NormalizationServiceTest`, `OpenAgendaCollectorTest`, `ImportServiceTest`, `ImportServiceIntegrationTest`, `ImportedActivityVisibilityIntegrationTest`, `AdminImportControllerIntegrationTest`) et nouveaux cas couvrant timings multiples, médias, liens, accessibilité, tags et informations d'inscription.
- ⚠️ Backend non compilable dans cette session (pas d'accès réseau à Maven Central depuis le sandbox) : relecture manuelle attentive de la correspondance entre l'ordre des champs de chaque record de domaine et les appels de construction effectuée à la place (voir ADR-0005), mais **`mvn verify` reste à lancer par Alex avant tout merge**.

### Correctifs découverts par le `mvn verify` d'Alex sur le patch LL-11011 (pré-existants, hors périmètre du ticket)
- `mvn verify` (exécuté par Alex, premier passage réel depuis LL-11009/LL-11010, faute d'accès Maven Central en sandbox) a révélé des échecs dans des fichiers que le patch LL-11011 ne touchait pas à l'origine :
  - `ActivityMetadataRepositoryIntegrationTest.findByActivityId_ShouldReturnDataUnchanged_AfterRoundTripThroughJsonbColumn` : comparait une valeur `jsonb` récupérée à la chaîne JSON brute d'origine — or `jsonb` réencode la valeur dans une forme canonique à l'écriture (l'ordre des clés d'un objet n'est pas garanti préservé, contrairement au type `json`). Bug de test pré-existant (LL-11009), sans lien avec les convertisseurs eux-mêmes (ADR-0004). **Corrigé** : comparaison structurelle via Jackson (`ObjectMapper#readTree`) plutôt que chaîne brute.
  - `ExternalActivityRepositoryIntegrationTest` (les 4 tests de cette classe) : `BadSqlGrammarException` sur `INSERT INTO "external_activity"`. ⚠️ **Diagnostic initial erroné** (« migration V25 non appliquée ») corrigé après la publication par Alex du commit `541efa9` (« Fix: Scope JSONB JDBC conversions ») : la cause réelle était que `StringToJsonbConverter`/`JsonbToStringConverter` (LL-11009, ADR-0004) étaient enregistrés pour **tout** `String` du domaine, pas seulement les colonnes `jsonb` — toute valeur texte liée à une entité passant par ces convertisseurs risquait d'être (més)envoyée à PostgreSQL comme `PGobject` de type `jsonb`, d'où l'échec de type sur les colonnes `varchar` ordinaires d'`external_activity` (7 colonnes, dont plusieurs `varchar`). Alex a corrigé la cause à la racine en introduisant `JsonbData` (type dédié, scope limité aux colonnes réellement `jsonb`) et en l'appliquant à `ActivityMetadata#data`, mais pas encore à `ExternalActivity#rawPayload` (colonne `raw_payload`, elle aussi `jsonb`, voir `V25__create_external_activity_table.sql`) — **complété ici** : `ExternalActivity#rawPayload` typé `JsonbData` (même patron que `ActivityMetadata` : constructeur de commodité acceptant un `String` conservé), `ExternalActivityService#computeHash`/tests associés (`ExternalActivityServiceTest`, `ExternalActivityRepositoryIntegrationTest`) ajustés en conséquence, avec la même correction de comparaison structurelle Jackson que ci-dessus appliquée à son propre test de round-trip JSONB.

## 0.9.13 — 2026-09-10

### Évolution — Dates de début/fin à la saisie manuelle d'une activité
- Demande Alex : pouvoir saisir une date de début et une date de fin sur le formulaire « Proposer une activité » (jusqu'ici, seules `title`/`description`/`category`/`address` étaient demandées ; `startDate` valait toujours l'instant de soumission et `endDate` restait toujours `null`).
- `POST /api/v1/activities` : `CreateActivityRequest` accepte désormais deux champs optionnels `startDate`/`endDate` (chaîne ISO-8601, `yyyy-MM-dd` ou `yyyy-MM-ddTHH:mm[:ss]`).
- Valeurs par défaut, comme demandé — « date de début = sysdate » : `startDate` absent → `LocalDateTime.now()`, comportement historique inchangé. « date de fin = trunc(date de début) » : `endDate` absent → début de journée (minuit) de `startDate` résolu, au sens SQL du terme.
  - ⚠️ Point à noter, pas ajusté sans confirmation : quand `startDate` est lui-même absent (donc « maintenant », heure comprise), `endDate` par défaut (minuit ce jour-là) tombe chronologiquement **avant** `startDate`. C'est la traduction littérale de la règle donnée ; aucun garde-fou `endDate ≥ startDate` n'a été ajouté, un tel garde-fou aurait justement empêché ce cas par défaut.
- Une valeur fournie mais illisible (ni date, ni date-heure ISO-8601) renvoie `400 Bad Request` plutôt que d'être silencieusement ignorée.
- Frontend (`App.tsx`) : deux champs `<input type="date">` optionnels (« Date de début », « Date de fin ») ajoutés au formulaire de contribution, sous le champ Adresse. Laissés vides, ils ne sont pas envoyés dans la requête (`undefined`), ce qui laisse le backend appliquer ses propres valeurs par défaut — une seule définition de la règle, côté serveur.
- Tests ajoutés : `ActivityServiceTest` (défauts, troncature, formats date/date-heure acceptés, erreurs de format) et `ActivityControllerTest` (transmission de `startDate`/`endDate` au service). Tests d'intégration existants (`AuthenticationFlowIntegrationTest`, `AdminActivityControllerIntegrationTest`, `NonRegressionIntegrationTest`) mis à jour pour la nouvelle signature, sans changement de comportement.
- Vérifié côté frontend avec `npx tsc --noEmit && npm run build` (compile sans erreur). ⚠️ Backend non compilable dans cette session (`mvn` indisponible dans le sandbox) : relecture manuelle attentive (équilibre accolades/parenthèses sur tous les fichiers touchés) effectuée à la place, mais **`mvn verify` reste à lancer avant tout merge**.

## 0.9.12 — 2026-09-10

### Correctif prod — Marqueurs d'activité cassés sur la carte
- Bug signalé par Alex : en production, les icônes des marqueurs indiquant l'emplacement des activités sur la carte ne s'affichaient plus (visible uniquement en prod, pas en local).
- Cause : icône par défaut de Leaflet (`L.Icon.Default`), utilisée telle quelle pour les marqueurs d'activité (les food trucks ont leur propre `divIcon`, non concernés). Cette icône résout ses images via `L.Icon.Default.prototype._getIconUrl`, qui déduit le dossier d'images en inspectant les balises `<script src="...leaflet...">` de la page — une détection qui échoue avec un bundler comme Vite, dont le script buildé n'a jamais ce nom. Résultat : requêtes d'image relatives à la racine (`/marker-icon.png`) en 404 une fois en production. Bug classique et documenté de l'écosystème react-leaflet + bundler moderne, invisible en dev (Vite sert par coïncidence les fichiers de `node_modules`).
- Correction : import explicite des trois images d'icône (`marker-icon.png`, `marker-icon-2x.png`, `marker-shadow.png`) comme modules ES (`frontend/src/App.tsx`), puis reconfiguration de `L.Icon.Default` via `mergeOptions` avec ces imports — fix standard react-leaflet/Vite. Vite les inline désormais en base64 dans le bundle JS (images < 4 Ko), donc plus aucune requête réseau séparée ni risque de 404.
- `frontend/src/vite-env.d.ts` : ajout de `/// <reference types="vite/client" />`, nécessaire pour que TypeScript reconnaisse les imports `*.png` comme des modules valides (seul `*.css` était déclaré jusqu'ici).
- Aucun changement backend, aucun changement pour les marqueurs food truck (déjà en `divIcon`, jamais concernés par ce bug).
- Vérifié avec `npx tsc --noEmit && npm run build`, plus inspection du bundle produit (`dist/assets/*.js`) confirmant que les trois images sont bien inlinées en `data:image/png;base64,...`.

## 0.9.11 — 2026-09-09

### Décision Alex — Retrait des contrôles « Filtrer par ville » et « Trier par »
- Décision explicite d'Alex : les deux contrôles ajoutés par `LL-10008` (liste déroulante « Filtrer par ville » et liste déroulante « Trier par », bandeau de filtres de `App.tsx`) sont jugés inutiles et retirés.
- Retrait complet côté frontend : les deux `<select>`/`<label>`, l'état associé (`selectedCity`, `sortOrder`, `availableCities`), les fonctions dédiées (`filterAndSortActivities`, `buildCityOptions`), le type `SortOrder` et les constantes `ALL_CITIES`/`NO_SORT`.
- La vue liste revient à son comportement historique (`LL-EF-008`) : toujours regroupée par ville puis triée par date à l'intérieur de chaque groupe (`groupActivitiesByCity`, conservée), sans possibilité de filtrer par ville ni de choisir un tri plat. La ville n'étant plus jamais répétée par ligne (uniquement dans l'en-tête de groupe), `renderActivityListItem` perd son paramètre `showCity` et la règle CSS `.activity-list-item-city` (désormais inutilisée) est retirée de `styles.css`.
- Carte et liste continuent de partager la même source de données (`activities`, remplace `visibleActivities`) — aucun changement de comportement côté carte, filtres catégorie/date/géographiques existants inchangés.
- Aucun changement backend : `GET /api/v1/activities?city=...&sort=...` (contrat `LOCATION_CONTRACT.md`, `LL-10006`) reste disponible côté API, simplement plus consommé par ce frontend. Les documents historiques de sprint (`SPRINT_10.md`, `PROJECT_STATUS.md`, `LL-10009_VALIDATION_CARTE_LISTE_DETAIL.md`) ne sont pas réécrits : ils restent l'enregistrement fidèle de ce qui a été livré par `LL-10008`/`LL-10009` à l'époque ; ce changement en est un retrait ultérieur, documenté ici.
- Vérifié avec `npm install && npx tsc --noEmit && npm run build` : compile sans erreur.

## 0.9.10 — 2026-09-09

### Correctif — Dernière synchronisation des agendas toujours vide
- Bug signalé par Alex : le champ « Dernière synchronisation » de l'interface d'administration des agendas (`AdminPage.tsx`) affichait toujours `—`, alors que le champ existe côté modèle (`Source.lastSyncAt`), est bien exposé par l'API et bien rendu côté frontend.
- Cause : `ImportService#importFrom` calculait déjà `endedAt` pour chaque import (utilisé dans `ImportResult`, journalisé) mais ne le reportait jamais sur la `Source` elle-même — `lastSyncAt` restait donc toujours `null` en base, alors que `docs/02_Architecture/SOURCE_CONTRACT.md` et la javadoc de `SourceService#updateSource` le décrivaient déjà comme renseigné par ce service. Écart d'implémentation par rapport au contrat existant, pas un changement de contrat.
- Correction : nouvelle méthode `SourceService#recordSync(sourceId, syncedAt)`, appelée par `ImportService#importFrom` immédiatement après une collecte réussie, avec `endedAt` (date/heure de fin d'import). Non appelée en cas d'échec total de `collector.collect()` (source injoignable) — cohérent avec le contrat « date/heure du **dernier import réussi** ».
- Aucun changement d'API, de DTO, de migration ni de frontend : le champ existait déjà de bout en bout, seule l'écriture manquait.
- Tests ajoutés : `SourceServiceTest#recordSync_*` (mise à jour du seul champ `lastSyncAt`, aucun effet si la source n'existe plus) et `ImportServiceTest#importAll_ShouldRecordSync_*`/`importAll_ShouldNotRecordSync_*` (appelé sur succès, pas sur échec total de collecte).
- ⚠️ Cette session n'a pas eu accès à Maven Central (réseau restreint à GitHub/npm/pip, `mvn` non installé dans le sandbox) : le backend n'a donc pas pu être compilé ni testé. **`mvn verify` doit être lancé avant tout merge.**

## 0.9.9 — 2026-09-09

### Sprint 10 / LL-10010 — Tests et documentation du sprint (clôture)
- Ticket de clôture de sprint : `docs/PROJECT_STATUS.md` complété d'une section « Sprint 10 » couvrant `LL-10001` à `LL-10010` (chacun avec décision et source documentées) ; `docs/01_Product/BACKLOG.md` mis à jour ; `README.md` complété d'une section « Sprint 10 » ; statut de `docs/05_Sprints/SPRINT_10.md` mis à jour.
- Nouvelle entrée de dette technique (`docs/DETTE_TECHNIQUE.md`) documentant, une fois pour toutes plutôt qu'à chaque ticket, l'impossibilité récurrente d'exécuter `mvn verify` dans les sessions sandbox (Maven Central hors des domaines réseau autorisés).
- **Sprint fonctionnellement complet et documenté, non formellement clos** : `mvn verify` (critère explicite de ce ticket) n'a pu être exécuté dans aucune session ayant traité ce sprint — bloquant pour une clôture définitive, à faire par Alex. Le build frontend (`npm run build`), en revanche, a été vérifié à plusieurs reprises et passe.

## 0.9.8 — 2026-09-09

### Sprint 10 / LL-10009 — Valider le parcours carte / liste / détail
- Ticket de validation (pas de nouveau code fonctionnel) : revue de code exhaustive du parcours en 10 étapes défini par le ticket, tracée jusqu'aux lignes de `App.tsx` concernées — voir `docs/02_Architecture/LL-10009_VALIDATION_CARTE_LISTE_DETAIL.md`.
- Les 5 critères d'acceptation sont validés par le code : source de données unique (`visibleActivities`) partagée entre carte et liste, adresse/ville affichées sans transformation par rapport à l'API, aucune dépendance de l'effet de récupération des activités à `selectedCity`/`sortOrder`/`viewMode` (donc aucune régression sur `/nearby`/`/within-bounds`).
- ⚠️ Comme pour le protocole MVP (LL-7001), cette session sandbox n'a ni base PostgreSQL réelle ni navigateur pour un clic réel : la validation ci-dessus est une revue de code, pas une exécution. Une passe réelle par Alex reste nécessaire avant clôture définitive.

## 0.9.7 — 2026-09-09

### Sprint 10 / LL-10008 — Ajouter les contrôles de filtre et de tri par ville
- Deux nouveaux contrôles dans le bandeau de filtres (`App.tsx`) : « Filtrer par ville » (liste déroulante, construite depuis les activités chargées, même patron que le filtre catégorie) et « Trier par » (Par défaut / Ville / Date).
- ⚠️ Décision : filtrage et tri appliqués **côté client** (`filterAndSortActivities`), pas via l'endpoint `GET /api/v1/activities?city=...&sort=...` du contrat LL-10006, pour deux raisons documentées dans `App.tsx` :
  1. cet endpoint ne peut pas être combiné avec la recherche géographique (`nearby`/`within-bounds`) — restriction explicite du contrat LL-10006 ;
  2. plus important, cet endpoint ne filtre **aucun statut** (contrairement à `nearby`/`within-bounds`, restreints à `PUBLISHED`) : l'utiliser pour cette page publique aurait exposé des activités `PENDING`/`REJECTED`. Corriger cette lacune est un changement plus large, hors périmètre de ce ticket — signalé ici pour un futur ticket dédié si l'endpoint doit un jour devenir la source principale de navigation.
- Le tri « Par défaut » conserve le regroupement par ville historique (LL-EF-008) ; un tri explicite (Ville ou Date) bascule la liste sur un affichage plat dans l'ordre choisi, avec la ville rappelée sur chaque ligne (plus d'en-tête de groupe dans ce mode).
- Le filtre ville s'applique aussi à la carte (mêmes données affichées, critère d'acceptation « cohérent entre carte et liste »).
- Compatible avec les filtres catégorie/date existants par construction (appliqué en aval de leur résultat déjà filtré côté serveur).
- Vérifié avec `npm install && npx tsc --noEmit && npm run build` : compile sans erreur.

## 0.9.6 — 2026-09-09

### Sprint 10 / LL-10007 — Créer la vue liste des activités
- La vue liste, livrée par `LL-EF-008`, couvrait déjà la quasi-totalité du périmètre de ce ticket (bouton de bascule, activités récupérées depuis l'API, filtres catégorie/date actifs respectés, états chargement/erreur/aucun résultat, clic → détail). Seul écart identifié par rapport au critère d'acceptation explicite « la ville et l'adresse sont visibles » : l'adresse n'apparaissait que dans la modale de détail, pas dans la liste elle-même (la ville, elle, était déjà visible via l'en-tête de groupe).
- Ajout de l'adresse (`activity.address`, repli « Adresse non renseignée » si absente — jamais de valeur déduite, cohérent avec `LOCATION_CONTRACT.md`) sur chaque ligne de la liste, entre le titre et la ligne date/catégorie.
- Aucun changement côté API/backend pour ce ticket (les quatre endpoints exposent déjà `address`/`city` depuis LL-10005) ; aucun changement côté mobile (fonctionnalité non présente sur ce client, hors périmètre).

## 0.9.5 — 2026-09-08

### Sprint 10 / LL-10006 — Ajouter le filtre et le tri par ville
- `GET /api/v1/activities` accepte désormais deux paramètres optionnels : `city` (filtre exact, insensible à la casse) et `sort` (une ou plusieurs clés parmi `city`/`date`, séparées par une virgule, ex. `sort=city,date`) — contrat détaillé dans `docs/02_Architecture/LOCATION_CONTRACT.md`.
- Filtrage et tri réalisés côté base de données (requête SQL unique couvrant toutes les combinaisons de tri via des expressions `CASE WHEN`), pas en mémoire côté application.
- Sans `city` ni `sort`, le comportement historique de l'endpoint est strictement inchangé (aucun `ORDER BY` ajouté).
- `sort` contenant une valeur inconnue ou une clé en double renvoie `400 Bad Request`. `city` ne correspondant à aucune activité renvoie une liste vide (`200 OK`), pas une erreur.
- `nearby`/`within-bounds` ne sont pas concernés par ce ticket (leurs contrats respectifs restent inchangés).

## 0.9.4 — 2026-09-06

### Sprint Évol-Fix / LL-EF-006 — Créer une interface utilisateur
- Nouvelle page `/profile` : consultation (rôle, date d'inscription) et modification (username/email) du profil de l'utilisateur connecté, déconnexion accessible depuis cette page. Lien « Mon profil » ajouté dans l'en-tête, pour tout utilisateur connecté.
- Nouveaux endpoints `GET`/`PATCH /api/v1/users/me` : l'utilisateur cible est résolu exclusivement depuis le JWT (`JwtAuthentication`), jamais depuis un paramètre de requête — impossible de consulter/modifier le profil d'un autre utilisateur par ce chemin.
- `role`/`passwordHash` ne sont jamais modifiables via `PATCH /api/v1/users/me` (changement de mot de passe hors périmètre, prévu pour LL-EF-007).
- Écart de sécurité trouvé et corrigé en traitant ce ticket : `GET /api/v1/users/{id}` n'avait aucune protection (accessible à quiconque, sans authentification) — restreint au rôle `ADMIN`, cet endpoint n'étant consommé par aucun client (frontend ni mobile).

## 0.9.3 — 2026-09-06

### Sprint Évol-Fix / LL-EF-008 — Ajouter une liste des activités
- Bouton « Liste » ajouté dans le bandeau de filtres, à côté de « Filtrer par catégorie » ; bascule Carte ↔ Liste avec état actif visible (`aria-pressed`).
- Vue liste : activités regroupées par ville, triées par date à l'intérieur de chaque ville ; chaque ligne affiche titre, date (+ heure si non nulle), catégorie. Clic sur une ligne : détail complet dans une modale (adresse, date/heure, catégorie, source).
- Écart trouvé en traitant ce ticket : aucune notion de ville n'existait dans le modèle métier (seules `latitude`/`longitude` étaient stockées) — changement de modèle documenté dans `docs/02_Architecture/ADR-0001-adresse-structuree-activites.md`. Trois colonnes nullables ajoutées à `activity` (`address`, `city`, `postal_code`, migration `V15` — `V14` déjà pris par LL-EF-005), résolues une seule fois à l'écriture (contribution manuelle : réutilisation de l'appel Nominatim existant avec `addressdetails=1` ; import : champs déjà présents dans l'objet `location` d'OpenAgenda, jusqu'ici ignorés). Aucun appel réseau supplémentaire.
- Demande complémentaire d'Alex traitée dans le même changement : la carte et le détail affichent désormais une adresse lisible (`address`, repli sur `city` puis sur les coordonnées) au lieu des coordonnées GPS brutes.
- `LL-EF-006` et `LL-EF-007` restent non traités (ticket pris hors ordre à la demande d'Alex).
- Deux correctifs de compilation post-livraison (sites de construction `Activity`/`Coordinates` manqués lors du premier passage) : `SourceService#withSourceId`, puis 4 tests d'intégration (`AdminActivityControllerIntegrationTest`, `AuthenticationFlowIntegrationTest`, `NonRegressionIntegrationTest`).

## 0.9.2 — 2026-09-02

### Sprint Évol-Fix / LL-EF-001 — Revoir l'affichage de la saisie d'une activité
- Le formulaire de saisie d'une activité (bandeau permanent auparavant) devient une fenêtre modale, ouverte depuis un bouton « Proposer une activité » toujours visible dans l'en-tête.
- Un visiteur non connecté cliquant sur ce bouton est redirigé vers `/login` ; seul un utilisateur connecté peut ouvrir la modale.
- Champs du formulaire présentés avec des libellés visibles (`<label>`), disposition verticale plus lisible.
- Fermeture de la modale via bouton ✕, clic sur l'arrière-plan, ou touche Échap.
- Aucun changement backend.

### Sprint Évol-Fix / LL-EF-002 — Supprimer le bandeau « Utiliser la localisation »
- Le bandeau et le bouton « Utiliser ma position » sont supprimés.
- La géolocalisation navigateur est désormais demandée automatiquement, une seule fois au chargement de la page, sans action utilisateur.
- En cas de refus, d'indisponibilité ou de timeout, plus aucun message affiché : repli silencieux sur la position par défaut (Marseille), comportement inchangé depuis LL-4008.
- État React devenu inutile (`GeolocationStatus`, message d'erreur) supprimé.

### Sprint Évol-Fix / LL-EF-003 — Revoir le rechargement de la carte
- Un déplacement/zoom pur de la carte (aucun filtre ni position n'a changé) n'entraîne plus la suppression immédiate des marqueurs ni l'affichage d'un texte « Chargement » : les anciens marqueurs restent visibles jusqu'à ce que les nouvelles données soient prêtes, puis sont remplacés en une seule fois.
- Un changement actif (filtre catégorie/date, position obtenue, nouvelle activité proposée) conserve le comportement précédent (suppression immédiate + texte de chargement).
- Aucun changement sur le debounce des appels API (400 ms, déjà en place depuis LL-4012) ni sur le cycle de vie de `MapContainer` (jamais démonté/reconstruit).

### Sprint Évol-Fix / LL-EF-004 — Créer une interface d'administration
- Nouvelle page `/admin`, réservée aux utilisateurs avec le rôle `ADMIN` (lien dans l'en-tête + garde de redirection côté page ; protection réelle déjà assurée côté backend, `SecurityConfig`, depuis LL-6005/LL-6006).
- Trois onglets (En attente / Publiées / Rejetées) listent les activités par statut, avec le détail complet de chacune (titre, description, catégorie, dates, position).
- Boutons Valider/Refuser sur les activités en attente, qui appellent les endpoints de modération existants (`PATCH /api/v1/admin/activities/{id}/publish` et `.../reject`).
- Aucun changement backend : le statut de modération `PENDING`/`PUBLISHED`/`REJECTED` et ses endpoints existaient déjà depuis le Sprint 6.

### Sprint Évol-Fix / LL-EF-005 — Gérer les agendas depuis l'interface d'administration
- Nouvelle section « Agendas » dans `/admin` : liste, création, modification, suppression (avec confirmation) des sources/agendas.
- Les agendas OpenAgenda sont désormais configurés dynamiquement en base (uid + filtre région par agenda), en remplacement du système par propriétés d'environnement — un ajout/modification/suppression prend effet dès le prochain import, sans redémarrage.
- Suppression d'un agenda encore lié à des activités : autorisée, les activités sont détachées vers la source réservée « Saisie manuelle » plutôt que la suppression bloquée.
- CRUD générique sur tous les types de source (API/RSS/MANUAL), pas réservé à OpenAgenda.
- Nouveaux endpoints protégés (rôle ADMIN) : `POST`/`PUT`/`DELETE /api/v1/sources[/{id}]`.

## 0.9.1 — 2026-08-25

### Sprint 8 / LL-8009 — Décision go/no-go de la bêta
- Écart trouvé en vérifiant le critère « plusieurs agendas Avignon » (LL-8004) : les propriétés `openagenda.avignon-*-uid` étaient définies mais jamais lues par aucun bean — un seul agenda (la démonstration historique, pas même Avignon-spécifique) était réellement collecté, quel que soit le nombre d'agendas configurés en propriétés.
- Corrigé : `OpenAgendaSourcesConfig` (nouvelle classe) enregistre désormais un collecteur par agenda dont l'uid est réellement configuré ; `OpenAgendaCollector` n'est plus un `@Component` auto-détecté.
- Trois tests d'intégration ajustés (`SingleMockCollectorConfig`, nouvelle configuration de test partagée) pour rester isolés d'un appel réseau réel malgré plusieurs collecteurs désormais actifs.
- Limite résiduelle documentée (`docs/DETTE_TECHNIQUE.md`) : seul l'agenda Avignon Culture a un uid réel, les trois autres (spectacles/patrimoine/loisirs) restent à identifier par Alex.
- Décision : **GO bêta conditionnel**, voir `docs/PROJECT_STATUS.md` (section LL-8009) pour les conditions précises avant ouverture effective.

## 0.9.0 — 2026-08-25

### Sprint 8 / Préparation de la bêta (LL-8001 → LL-8008)
- `LL-8001` : parcours MVP rejoué après les corrections du Sprint 7, aucune régression, baseline figée.
- `LL-8002` : premier compte `ADMIN` bootstrapé automatiquement au premier démarrage (`AdminBootstrapRunner`), remplace la promotion SQL manuelle.
- `LL-8003` : exceptions serveur non gérées journalisées (niveau `ERROR`, sans donnée sensible) par `GlobalExceptionHandler`.
- `LL-8004` : plusieurs agendas OpenAgenda configurés pour Avignon ; pagination complète de l'API OpenAgenda (`size=300` + curseur `after`).
- `LL-8005` : import automatique planifié (`ImportScheduler`, toutes les heures), en complément du déclenchement manuel existant.
- `LL-8006` : affichage de bout en bout vérifié sur la carte ; popup enrichi avec le lieu (coordonnées) et la source lisible (`ActivityResponse`, résolution de `sourceId` en `sourceName`).
- `LL-8007` : dette technique pertinente pour une bêta traitée (vulnérabilité `nanoid`, formatage de `ActivityController`, duplication des `ROADMAP.md`) — `docs/DETTE_TECHNIQUE.md` sans entrée ouverte.
- `LL-8008` : documentation consolidée sur l'ensemble des fichiers de référence (`README.md`, `docs/PROJECT_STATUS.md`, `docs/04_Project/ROADMAP.md`, `docs/01_Product/BACKLOG.md`, `docs/NEXT_TASK.md`, etc.), plus aucune ne désigne le Sprint 7 comme sprint courant.
- Reste à faire avant la clôture du Sprint 8 : `LL-8009` (décision go/no-go de la première bêta contrôlée).

### Fixed
- Correctifs de qualité signalés par `mvn verify` au fil du sprint : test d'intégration dépendant d'un id d'activité fixe (fragile sur base persistante), dépassements de la limite Checkstyle de 120 caractères, échappement `*/` dans un Javadoc (`ImportScheduler`) provoquant une erreur de compilation.

## 0.8.0 — 2026-08-21

### Sprint 7 / Validation
- Sprint 7 terminé (`LL-7001` → `LL-7009`).
- MVP validé après exécution des parcours réels et correction des blocages identifiés.
- Environnement de démonstration documenté.
- Phase suivante : préparation d'une première bêta contrôlée.

### Sprint 8 / Planification
- Sprint 8 défini pour consolider la baseline, l'opérationnel, la qualité et la documentation avant bêta.


## 0.7.0 — 2026-08-17

### Documentation / Planification
- Sprint 6 confirmé terminé après LL-6011.
- Phase 2 de validation du MVP ouverte.
- Sprint 7 défini pour valider le MVP de bout en bout avant toute nouvelle évolution.
- Backlog, roadmap et NEXT_TASK réalignés sur une source de vérité explicite.

## 0.6.0 - Sprint 6

### Added

* Validation renforcée des activités : `title` obligatoire (≤ 255
  caractères) ; `url` de l'activité conservée après normalisation
  (colonne `url`, migration `V10`).
* Statut de modération sur `Activity`
  (`PENDING`/`PUBLISHED`/`REJECTED`, contrainte `CHECK` en base,
  migration `V11`).
* `GET /api/v1/activities/nearby` et `/within-bounds` ne retournent
  désormais que les activités `PUBLISHED` — `status` retiré des
  paramètres de ces endpoints publics.
* Contrôle administratif minimal (rôle `ADMIN` requis) :
  `GET /api/v1/admin/activities?status=...`,
  `PATCH /api/v1/admin/activities/{id}/publish`,
  `PATCH /api/v1/admin/activities/{id}/reject`.
* `GET /api/v1/sources` et `GET /api/v1/sources/{id}` (public) : une
  activité importée est désormais identifiable via l'API, pas
  seulement en base.
* Premier jalon Food Truck : nouveau module `foodtruck` indépendant,
  `GET`/`POST /api/v1/foodtrucks`, migration `V12` ; affiché sur la
  carte avec une icône dédiée, distincte de celle des activités.
* Suite de tests de non-régression consolidée
  (`NonRegressionIntegrationTest`) couvrant explicitement la
  visibilité publique par statut, le contrôle d'accès administrateur/
  utilisateur standard, la visibilité des food trucks et la recherche
  géographique.

### Fixed

* `README.md` : mention obsolète du paramètre `status` sur `/nearby`/
  `/within-bounds` (retiré depuis ce sprint) corrigée.

## 0.5.0 - Sprint 5

### Added

* Pipeline d'import de données externes : collecte → déduplication →
  normalisation/validation → persistance → journalisation.
* Premier collecteur réel (OpenAgenda).
* Modèle et module `Source` (`GET` non exposé à ce stade — ajouté en
  Sprint 6), source réservée `MANUAL` pour les contributions
  manuelles.
* Détection simple des doublons (identifiant externe, ou clé
  composite).
* Persistance des imports (création/mise à jour/archivage doux),
  journalisation.

## 0.4.0 - Sprint 4

### Added

* Recherche géographique par rayon avec PostGIS.
* Recherche par zone cartographique (bounding box).
* Filtres par catégorie et par date.
* Géolocalisation utilisateur côté frontend.
* Chargement des activités selon les critères géographiques.
* Contrats d'API et documentation OpenAPI associés.

### Fixed

* Correction du filtre SQL par date nécessitant un cast explicite du paramètre PostgreSQL.
* Correction de sécurité empêchant l'exposition de `passwordHash` dans les réponses de `UserController`.

0.3.0 - 2026-08-11

### Added

* Authentification par JWT :
  * Inscription (`POST /api/v1/auth/register`) et connexion
    (`POST /api/v1/auth/login`, retourne un token JWT valable 24h).
  * Mots de passe hachés avec BCrypt (`PasswordHashingService`), jamais
    stockés ni renvoyés en clair.
  * Filtre JWT (`JwtFilter`) et configuration Spring Security
    (`SecurityConfig`) : `POST /api/v1/activities` requiert désormais un
    utilisateur authentifié, `POST /api/v1/users` est réservé au rôle
    `ADMIN`. Réponses JSON standardisées en cas de `401`/`403`.
  * Frontend : pages `/login` et `/register`, en-tête affichant
    l'utilisateur connecté avec déconnexion, et envoi automatique du JWT
    sur les appels protégés.
* Géocodage d'adresse (`GeocodingService`, API Nominatim) : le formulaire
  de contribution envoie désormais une adresse texte au lieu de
  latitude/longitude ; le backend géocode côté serveur et ne conserve que
  les coordonnées obtenues.
* Tests d'intégration bout en bout couvrant inscription → connexion →
  accès à un endpoint protégé, création d'activité géocodée, et refus
  d'accès sur JWT expiré ou absent.

## 0.2.0 - 2026-08-08

### Added

* Module User : entité, migration Flyway (table `users`), repository,
  service, API REST (`POST /api/v1/users`, `GET /api/v1/users/{id}`).
* Module Category : entité, migration Flyway (table `category`),
  repository, service, API REST (`GET /api/v1/categories`).
* Endpoint de création d'activité (`POST /api/v1/activities`), ajouté
  hors périmètre initial du Sprint 2 pour débloquer le formulaire de
  contribution.
* Formulaire de contribution côté frontend : proposer une activité
  (titre, description, catégorie, localisation) directement depuis la
  carte.

## 0.1.0 - 2026-08-07

### Added

* API REST de consultation des activités (`GET /api/v1/activities` et
  `GET /api/v1/activities/{id}`).
* Frontend React + TypeScript avec une carte Leaflet centrée sur Marseille,
  zoomable et déplaçable.
* Affichage des activités de l'API sous forme de marqueurs sur la carte.
* Popup au clic sur un marqueur, affichant le titre, la catégorie et la date
  de l'activité.
