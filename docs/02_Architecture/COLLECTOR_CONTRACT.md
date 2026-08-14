# Contrat Collector (LL-5003)

Définit l'interface commune aux futurs collecteurs (`SPRINT_5.md` :
« définir une interface commune aux futurs collecteurs »). Comme
`SOURCE_CONTRACT.md` pour LL-5001 et `GEO_SEARCH_CONTRACT.md` pour
LL-4001 — pas de code à ce stade, uniquement le contrat.

Aucun package `collector` n'est créé par ce ticket : contrairement à
`Source` (contrat en LL-5001, module en LL-5002), aucun ticket dédié à
la création du module `Collector` n'existe dans `SPRINT_5.md`. L'
interface documentée ici sera créée en code au moment où un ticket en a
réellement besoin (au plus tard LL-5006, premier collecteur réel).

---

## Interface `Collector`

```java
public interface Collector {

    String getSourceName();

    List<CollectedActivity> collect();

}
```

Emplacement prévu : `com.locallife.backend.collector.domain.Collector`.

### `getSourceName()`

Identifie la source à laquelle appartiennent les données collectées, en
retournant le `name` de la `Source` correspondante (voir
`SOURCE_CONTRACT.md`) — pas son `id` technique.

⚠️ Décision à valider : identification par nom plutôt que par `Source.id`,
pour ne pas coupler le collecteur à la persistance (il n'a pas besoin de
connaître un identifiant généré en base). Le rapprochement entre ce nom
et la ligne `Source` existante (recherche, création si absente) est
différé à un ticket ultérieur — le pipeline (LL-5006/LL-5008), pas le
collecteur lui-même.

### `collect()`

Récupère les données depuis la source externe et les retourne sous
forme de données brutes normalisables — sans normalisation, sans
validation, sans écriture en base (interdit par les règles du sprint :
« un collecteur ne doit jamais écrire directement dans les tables
métier »).

⚠️ Décision à valider : le type de retour `CollectedActivity` est une
référence anticipée au modèle que LL-5004 doit créer (« modèle interne
représentant une donnée collectée avant conversion vers `Activity` »).
Ce contrat fixe uniquement la forme de l'interface ; la signature exacte
de `collect()` ne sera figée qu'une fois `CollectedActivity` réellement
défini en LL-5004. Si LL-5004 aboutit à un type différent de celui
imaginé ici, cette interface sera ajustée en conséquence — sans que ce
soit considéré comme une régression de LL-5003.

**Mise à jour LL-5004 :** `CollectedActivity` existe désormais
(`com.locallife.backend.collector.domain.CollectedActivity`), avec les
champs requis par `SPRINT_5.md` (titre, description, dates de
début/fin, catégorie, latitude, longitude, URL source, identifiant
externe, source). La signature `List<CollectedActivity> collect()`
ci-dessus est donc confirmée et n'a pas eu besoin d'ajustement.

### Gestion des erreurs

Non spécifiée par ce contrat : LL-5003 ne fixe pas de type d'exception.
`SPRINT_5.md` exige des « erreurs gérées » comme critère d'acceptation
de LL-5006 (premier collecteur réel) — c'est à ce moment que le
mécanisme concret (exception dédiée, valeur de retour, etc.) sera choisi,
avec la connaissance du comportement réel de la source retenue.

---

## Ce que ce contrat interdit explicitement

Conformément aux règles du sprint (`SPRINT_5.md`, section « Règles du
sprint ») :

- pas de classe abstraite, pas de registre de collecteurs, pas de
  mécanisme de découverte automatique (« ne pas créer de framework
  générique de collecte ») ;
- une seule méthode de collecte, pas de méthodes de configuration, de
  planification ou de pagination génériques (« le contrat doit rester
  minimal ») ;
- aucun accès direct aux repositories `Activity` ou `Source` depuis un
  collecteur — seul le pipeline (LL-5005 à LL-5008) écrit en base, via
  les services métier existants.

## Hors périmètre de LL-5003

- aucune implémentation de collecteur concret (LL-5006) ;
- aucun code Java créé par ce ticket (voir remarque en tête de
  document) ;
- aucune modification de `Source` ou `Activity`.
