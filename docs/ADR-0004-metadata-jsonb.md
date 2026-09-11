# ADR-0004 — Metadata en JSONB via convertisseurs Spring Data JDBC (LL-11009)

## Contexte

LL-11009 demande explicitement d'utiliser JSONB pour « les données
spécifiques ou évolutives ne constituant pas encore des données métier
de premier niveau », avec le critère d'acceptation « aucune table créée
inutilement pour chaque champ d'une source ».

Spring Data JDBC (utilisé dans tout le projet, pas d'ORM type JPA/
Hibernate) n'a pas de support intégré pour un type PostgreSQL `jsonb` :
sans configuration additionnelle, un champ `String` du domaine serait
envoyé au pilote JDBC comme `varchar`, que PostgreSQL refuse d'assigner
à une colonne `jsonb` (erreur de type SQL à l'écriture).

## Décision

- `ActivityMetadata.data` reste un `String` (texte JSON brut, jamais
  désérialisé vers un type Java métier — voir sa javadoc) ; c'est du
  côté SQL que le typage `jsonb` s'applique.
- Deux convertisseurs Spring Data JDBC, `StringToJsonbConverter`
  (`@WritingConverter`) et `JsonbToStringConverter`
  (`@ReadingConverter`), utilisant `org.postgresql.util.PGobject` pour
  indiquer explicitement le type `jsonb` au pilote JDBC PostgreSQL —
  approche standard documentée pour ce cas (Spring Data JDBC + colonne
  `jsonb` PostgreSQL).
- `JdbcConfig` (nouveau, `backend/src/main/java/.../config/`) enregistre
  ces convertisseurs via un bean `JdbcCustomConversions` — l'auto-
  configuration Spring Boot cède la place à un bean de ce type défini
  par l'application plutôt que d'utiliser le sien par défaut.
- `backend/pom.xml` : le driver `org.postgresql:postgresql` passe de
  `scope=runtime` à `scope` par défaut (compile) — `PGobject` doit être
  visible à la compilation des convertisseurs, une dépendance
  uniquement `runtime` ne l'expose pas sur le classpath de compilation.
- `ActivityMetadataService#create` valide que `data` est un JSON
  syntaxiquement correct (via `ObjectMapper`, déjà présent, aucune
  dépendance ajoutée) avant persistance, pour échouer avec un message
  clair plutôt qu'une erreur SQL peu lisible si le convertisseur
  échouait à la place.

## Pourquoi une table séparée plutôt qu'une colonne sur `activity`

Contrairement à LL-11006 (`longDescription`/`conditions`/`age`, ajoutés
directement sur `Activity` car jugées données métier de premier
niveau), `ActivityMetadata` vit dans sa propre table
(`activity_metadata`, `activity_id` unique). Deux raisons :

1. Le ticket lui-même oppose explicitement metadata (JSONB, pas encore
   de premier niveau) à ce que LL-11006 avait ajouté (première niveau,
   colonnes dédiées) — les traiter de la même façon (colonnes sur
   `Activity`) contredirait cette distinction.
2. Le constructeur de `Activity` compte déjà 19 champs après LL-11006,
   avec ~32 sites d'appel à travers le code (voir le patch LL-11006) —
   chaque champ supplémentaire a un coût de maintenance réel démontré
   par ce ticket. Une table séparée l'évite entièrement.

## Alternatives envisagées

| Option | Rejetée car |
| --- | --- |
| Colonne `jsonb` directement sur `activity` | Aggrave encore le problème de taille du constructeur de `Activity` (19 champs) ; contredit la distinction premier niveau/pas premier niveau du ticket (voir ci-dessus). |
| `Map<String, Object>` en Java plutôt que `String` | Nécessiterait un convertisseur plus complexe (sérialisation/désérialisation via Jackson en plus du typage `jsonb`) pour un bénéfice nul ici : le domaine ne traite jamais `data` comme une structure, seulement comme un blob à conserver/restituer tel quel. |
| Stocker en `text`/`varchar` plutôt que `jsonb` | Fonctionnerait sans convertisseur ni changement de `pom.xml`, mais PostgreSQL ne validerait plus la syntaxe JSON à l'écriture et perdrait les opérateurs/index JSON natifs — le ticket demande explicitement `jsonb`. |

## Conséquences

- Premier usage d'un type de convertisseur Spring Data JDBC personnalisé
  dans ce projet (`org.postgresql.util.PGobject`) — pattern à réutiliser
  si un futur ticket a un besoin similaire, plutôt que d'en réinventer
  un.
- `pom.xml` : le driver PostgreSQL n'est plus isolé au runtime — impact
  mineur (légion de code applicatif dépend maintenant explicitement de
  classes spécifiques à PostgreSQL, déjà le cas implicitement puisque
  le projet ne vise que PostgreSQL/PostGIS).
- ⚠️ Cette session n'a pas d'accès réseau à Maven Central : ni le
  convertisseur ni son enregistrement n'ont pu être compilés/testés.
  C'est le point le plus à risque de ce ticket, dans la même catégorie
  que l'intégration ical4j de LL-11003 (ADR-0003) — si `mvn verify`
  échoue sur `JdbcConfig`/les convertisseurs, il s'agit très
  probablement d'un détail d'API (nom exact du bean/méthode attendue
  par l'auto-configuration Spring Boot) plutôt que d'un problème
  d'approche.
