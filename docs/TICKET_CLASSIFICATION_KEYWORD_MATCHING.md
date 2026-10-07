# Ticket — Classification par mots-clés du titre : comparaison sans altération sémantique

## Objectif
Définir une comparaison robuste des mots-clés sans modifier le sens lexical des mots. Cette PR ne doit contenir que le ticket ; Claude l'implémentera ensuite dans une PR distincte.

## Règle fondamentale
Le mot-clé stocké en base est la source de vérité. La comparaison peut ignorer la casse et les différences de présentation, mais ne doit jamais transformer un mot en un autre.

Obligatoire :
- `marché` ≠ `marche`
- `théâtre` ≠ `theatre`
- `pêche` ≠ `peche`
- `forêt` ≠ `foret`

## Normalisation autorisée
- Casse : `Marché` = `MARCHÉ` = `marché`.
- Espaces de début/fin ignorés ; espaces multiples réduits.
- Séparateurs : `vide-grenier` et `vide grenier` peuvent être considérés équivalents, sans modifier les caractères alphabétiques.
- Apostrophes typographiques `'`, `’`, `ʼ` considérées équivalentes : `d'art` = `d’art`. Mais `d'art` ≠ `dart`.

## Interdictions
Pas de suppression des accents, translittération, stemming, lemmatisation, synonymes automatiques, correction orthographique, fuzzy matching, Levenshtein, remplacement `é → e`, NLP ou IA.

## Frontières des mots
Ne pas utiliser un simple `String.contains()`.
- keyword `art`, titre `artisanat` → false
- keyword `art`, titre `atelier d'art` → true
- keyword `marche`, titre `marche nordique` → true
- keyword `marche`, titre `marché de Noël` → false

## Expressions multi-mots
`vide grenier` doit matcher `Grand vide-grenier municipal`.

## Travail demandé à Claude
1. Inspecter l'architecture et la logique de normalisation existante.
2. Réutiliser une abstraction existante si elle convient ; sinon créer une abstraction dédiée.
3. Ne pas disperser cette logique dans collectors/services.
4. Ajouter les tests unitaires couvrant casse, accents, frontières, séparateurs, apostrophes, espaces et expressions multi-mots.
5. Exécuter `mvn test`, `mvn verify` et les contrôles qualité existants.
6. Produire un rapport avec fichiers modifiés, règles retenues, tests et résultats.

## Hors périmètre
Ne pas implémenter ici : `category_keyword`, fallback sur le titre, `TitleKeywordClassifier`, classification automatique par titre, nouvelles catégories, NLP/IA.

## Critère bloquant
```text
mot-clé référentiel : "marché"
titre : "Marche nordique"
résultat attendu : aucun match
```

Le système ne doit jamais transformer `marché` en `marche`.
