# Ticket — Classification par mots-clés du titre lorsque les keywords source sont absents

## Objectif
Lorsque les keywords source sont absents ou vides, analyser le titre et rechercher des mots-clés dans le référentiel category_keyword.

Règle : keywords source présents → logique actuelle. Keywords source null/vides → fallback titre.

## Règles
- Ne jamais utiliser le titre pour remplacer ou contredire un keyword source existant.
- Fallback si keywords = null, collection vide, ou uniquement valeurs null/blanches.
- Ne pas analyser la description en V1.
- Les mots-clés métier doivent être stockés en base, jamais codés en Java.
- Utiliser la table category existante ; ne pas créer de nouvelle taxonomie.

## category_keyword
Créer une table category_keyword avec au minimum : id, category_id, keyword, active. category_id référence la table category existante. Respecter les conventions Flyway et les contraintes du projet.

## Comparaison du titre
Réutiliser les règles de la PR #38 : casse/espaces/séparateurs/apostrophes tolérés, mais accents conservés. Pas de suppression des accents, stemming, lemmatisation, synonymes, fuzzy matching, Levenshtein ou IA.

Cas bloquant : keyword = marché, titre = Marche nordique → aucun match.

Respecter les frontières de mots : art ne matche pas artisanat ; art matche atelier d'art.

Les expressions multi-mots doivent fonctionner : vide grenier doit matcher Grand vide-grenier municipal.

## Décision
Un seul keyword ou plusieurs keywords appartenant à la même catégorie → AUTO_CLASSIFIED.
Des keywords appartenant à plusieurs catégories → NEEDS_REVIEW, idéalement CONFLICTING_SIGNALS si ce reason existe déjà. Ne jamais choisir arbitrairement.
Aucun match → UNCLASSIFIED:NO_SIGNAL.

Ne pas utiliser priority pour masquer une ambiguïté en V1.

## Architecture
Le ClassificationService reste le point central de décision. Le collector fournit title et les données source ; il ne décide pas de la catégorie. Utiliser une abstraction repository/port/service adaptée à l'architecture existante.

## Traceabilité
Une classification issue du titre doit être traçable, idéalement avec source = TITLE_KEYWORD et le keyword reconnu. Réutiliser ClassificationRecord si possible.

## Données initiales
Inspecter les 9 catégories V1 réellement présentes et leurs codes avant de créer les seeds. Ajouter seulement un petit jeu initial de keywords précis et auditables. Ne pas inventer de taxonomie et ne pas ajouter des centaines de keywords.

## Tests obligatoires
- keywords null + titre avec keyword → classification par titre.
- keywords [] + titre avec keyword → classification par titre.
- keywords source présents + titre contradictoire → le titre n'est pas utilisé.
- marché vs Marche nordique → aucun match.
- marché dans Grand marché de Noël → match.
- plusieurs keywords même catégorie → AUTO_CLASSIFIED.
- keywords de catégories différentes → NEEDS_REVIEW / CONFLICTING_SIGNALS.
- aucun match → UNCLASSIFIED:NO_SIGNAL.
- art vs artisanat → aucun match.
- vide grenier vs Grand vide-grenier municipal → match.

Ajouter des tests d'intégration couvrant le flux CollectedActivity → ImportService → ClassificationService → classification → ClassificationRecord.

## Hors périmètre
Pas de description/longDescription, NLP, IA, synonymes, fuzzy matching, scoring sémantique, apprentissage automatique, nouvelle taxonomie, ni modification de la priorité keywords source > titre.

## Documentation et validation
Mettre à jour la documentation de classification avec le fallback titre.
Exécuter mvn test, mvn verify et les contrôles qualité existants.

## Rapport Claude
Indiquer fichiers modifiés, migration, structure category_keyword, abstraction utilisée, règles de fallback, keywords initiaux, gestion des ambiguïtés, traçabilité, tests, résultats mvn test / mvn verify et points nécessitant une décision humaine.

## Critères d'acceptation
1. keywords source null + titre Grand marché de Noël + keyword marché → catégorie correspondante et AUTO_CLASSIFIED.
2. keywords source [culture] + titre Marche nordique → le titre ne remplace pas culture.
3. keyword marché + titre Marche nordique → aucun match.
4. Deux catégories candidates → NEEDS_REVIEW, jamais de choix arbitraire.
5. Tous les keywords métier sont en base, pas dans le code Java.

## Décisions complémentaires (Alex, 07/10/2026)

Ces décisions s'ajoutent aux contraintes ci-dessus et complètent le ticket.

1. **Jeu initial validé, mais prudence sur les faux positifs.** Les 13 mots-clés initiaux sont validés. Un mot-clé isolé n'est pas automatiquement assez fiable : `marché` peut apparaître dans « Marché de la poésie » sans que l'activité relève de MARKETS. Si le jeu initial révèle une ambiguïté réelle, la signaler et conserver une classification prudente (`NEEDS_REVIEW` ou `UNCLASSIFIED:NO_SIGNAL`) plutôt que de forcer une catégorie.
2. **Pas de reclassement rétroactif.** Un ré-import ne reclasse pas une activité existante (comportement de la PR #35). Ajouter ou modifier un mot-clé dans `category_keyword` ne modifie donc aucune activité déjà importée/classifiée. Aucun mécanisme de reclassement automatique des données existantes dans ce ticket.

### Application dans l'implémentation

* Audit des 13 mots-clés : seul `marché` isolé présente une ambiguïté réelle. Il n'est plus seedé seul mais en 5 expressions sans ambiguïté (`marché de Noël`, `marché de producteurs`, `marché artisanal`, `marché paysan`, `marché nocturne`). Un titre qui ne contient que « marché » reste `UNCLASSIFIED:NO_SIGNAL`. Le critère d'acceptation 1 (« Grand marché de Noël » → MARKETS, `AUTO_CLASSIFIED`) reste satisfait.
* Vérifié dans le code : `category_keyword` n'est lue que par `ClassificationService#decide`, appelée par `ImportService` uniquement pour un élément sans activité existante. Aucune migration, tâche ou endpoint ne reclasse l'existant. Tests : `reimport_ShouldKeepTheExistingCategory_...` et `reimport_ShouldNotReclassifyOrRewriteTheRecord_...` (activité et `ClassificationRecord` identiques après modification des mots-clés).

