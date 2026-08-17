# Contrat du modèle Food Truck (LL-6008)

Définit le modèle minimal permettant de représenter un food truck sur la
carte existante, sans créer un second système cartographique
(`SPRINT_6.md` : « préparer l'intégration des food trucks sans créer un
second système cartographique »). Sert de base à l'implémentation
(LL-6009 — première intégration), comme `SOURCE_CONTRACT.md` l'a fait
pour LL-5002 — pas de code à ce stade, uniquement le contrat.

Ce document ne décrit ni endpoint REST, ni migration Flyway, ni entité
Java : ces sujets relèvent de LL-6009. Conformément à `AI_RULES.md` (un
ticket = une seule responsabilité).

---

## Décision structurante : module dédié, pas une extension d'`Activity`

⚠️ Décision à valider avant l'implémentation (LL-6009) : un food truck
est modélisé comme une entité **séparée** (`FoodTruck`, nouveau module
`foodtruck`, sur le même schéma que les modules `source`/`category` —
domain/application/infrastructure/api), plutôt que comme une extension
d'`Activity` (par exemple un champ discriminant `type` sur `Activity`).

Deux options envisagées :

1. **Réutiliser `Activity`** avec un discriminant (`type = "ACTIVITY"` /
   `"FOOD_TRUCK"`, ou une valeur de `category` réservée). Avantage :
   aucun nouveau module, réutilisation directe de tout ce qui existe
   déjà (recherche géographique, statuts de modération, endpoints
   d'administration LL-6005/LL-6006). Inconvénients qui ont fait
   écarter cette option : `startDate`/`endDate` n'ont pas de sens pour
   un food truck (SPRINT_6.md exclut explicitement « un module complexe
   de gestion de tournée ou de planning » — un food truck n'est pas un
   événement daté) ; `sourceId`/`importKey` n'ont pas de sens non plus
   (aucun food truck importé depuis un collecteur externe n'est prévu à
   ce stade) ; mélanger deux concepts métier distincts dans une seule
   table imposerait des colonnes nullables sans signification claire
   pour l'un ou l'autre cas, et complexifierait `ActivityService`,
   `NormalizationService` et `ImportService` avec des branches
   conditionnelles sur le type — contraire à la règle du sprint
   « pas de module complexe » et au principe « pas de changement
   spéculatif ».
2. **Module `FoodTruck` séparé**, avec son propre modèle, sa propre
   table, son propre repository/service — mais **partageant la même
   carte** côté frontend (LL-6009 ajoutera un second appel API,
   `GET /api/v1/foodtrucks` ou équivalent, dont les résultats seront
   affichés comme des marqueurs supplémentaires sur le même
   `MapContainer` Leaflet déjà utilisé pour les activités — voir
   `frontend/src/App.tsx`, LL-1008 — pas un second composant carte).
   C'est ce que « sans créer un second système cartographique » impose
   côté résultat visible (une seule carte), sans imposer une seule
   table côté modèle. Aligné avec l'architecture « monolithe modulaire »
   du projet (`AI_RULES.md`), qui favorise des modules indépendants par
   concept métier plutôt qu'une table fourre-tout.

**Option retenue : 2 (module `FoodTruck` séparé).**

---

## Modèle `FoodTruck`

| Champ         | Type          | Obligatoire | Contraintes                                    | Description                                                                 |
| ------------- | ------------- | ----------- | ----------------------------------------------- | ----------------------------------------------------------------------------- |
| `id`          | Long          | généré      | —                                                | Identifiant technique, cohérent avec `Activity.id`/`Source.id`.               |
| `name`        | String        | oui         | non vide                                        | Nom du food truck (ex. `"Le Camion qui Fume"`).                              |
| `description` | String        | non         | —                                                | Description libre, même convention que `Activity.description`.               |
| `latitude`    | double        | oui         | entre -90 et 90                                 | Position affichée sur la carte, même convention que `Activity.latitude`.      |
| `longitude`   | double        | oui         | entre -180 et 180                               | Position affichée sur la carte, même convention que `Activity.longitude`.     |
| `category`    | String        | oui         | non vide                                        | Type de cuisine (ex. `"burger"`, `"tacos"`, `"street food asiatique"`). Chaîne libre, même convention qu'`Activity.category` (LL-4004 : pas de relation avec la table `category`, qui reste réservée aux activités). |
| `contact`     | String        | non         | —                                                | URL **ou** contact (téléphone, réseau social) — voir décision ci-dessous.     |
| `status`      | String        | oui         | doit correspondre à une valeur connue (ci-dessous) | Statut de publication, même convention qu'`Activity.status` (LL-6003).      |

### `contact` : URL ou contact, pas les deux champs séparés

⚠️ Décision à valider : le ticket demande « URL ou contact ». Un champ
texte libre unique (`contact`) est retenu plutôt que deux champs
distincts (`url` + `phoneNumber`, par exemple), et sans validation
stricte de format (contrairement à `Activity.url`, dont la validation
syntaxique a été renforcée en LL-6002 — mais qui ne portait que sur une
URL, jamais un numéro de téléphone). Un champ libre reste compatible
avec les deux cas d'usage cités par le ticket (site web, page
Instagram, numéro de téléphone) sans imposer un format qui exclurait
l'un des deux. Optionnel, comme `Activity.description`.

### `status` : mêmes valeurs que `Activity.status`

⚠️ Décision à valider : réutilise les trois valeurs déjà établies pour
`Activity` (`PENDING`/`PUBLISHED`/`REJECTED`, LL-6003) plutôt que
d'inventer une convention distincte pour les food trucks. Alternative
envisagée : un simple booléen `published`. Écartée pour rester cohérent
avec le mécanisme de modération déjà en place (LL-6005/LL-6006,
`AdminActivityController`) — si LL-6009 ou un ticket ultérieur souhaite
réutiliser le même flux d'administration (file d'attente, publier/
rejeter) pour les food trucks, les mêmes trois statuts évitent de
dupliquer la logique de transition. `PENDING` reste la valeur par
défaut à la création, comme pour une activité créée manuellement.

### Pas de lien avec `Source`

⚠️ Décision à valider : contrairement à `Activity` (`sourceId`
obligatoire depuis LL-5008), `FoodTruck` n'a **aucun** lien avec le
module `Source` — aucun food truck importé depuis un collecteur externe
n'est prévu par ce ticket ni par `SPRINT_6.md`. Si un import automatisé
de food trucks devenait nécessaire, ce lien serait ajouté par un ticket
dédié plutôt qu'anticipé ici (pas de changement spéculatif).

---

## Compatibilité avec la carte existante

Critère d'acceptation explicite de LL-6008 : « le modèle est [...]
compatible avec la carte existante ». `latitude`/`longitude` en `double`
reprennent exactement la même convention qu'`Activity` (mêmes bornes,
même type), ce qui permet à LL-6009 de réutiliser directement le
composant `Marker` de `react-leaflet` déjà en place (`App.tsx`,
LL-1008/LL-1009) sans transformation de coordonnées ni composant
cartographique distinct.

La distinction visuelle ou fonctionnelle entre un food truck et une
activité sur la carte (critère d'acceptation de LL-6009, pas de celui-ci)
n'est pas traitée ici — seule la compatibilité du modèle est du
périmètre de LL-6008.

---

## Hors périmètre de LL-6008

Conformément à `SPRINT_6.md` et `AI_RULES.md` (un ticket = une seule
responsabilité) :

- aucune entité Java, aucun repository, aucune migration Flyway
  (LL-6009) ;
- aucun endpoint REST (LL-6009) ;
- aucune modification du frontend, aucune distinction visuelle sur la
  carte (LL-6009) ;
- aucun système de gestion de tournée ou de planning (exclu
  explicitement par `SPRINT_6.md`) ;
- aucun système de commande ou de paiement (exclu explicitement par
  LL-6009, mentionné ici par anticipation pour éviter toute ambiguïté
  sur le périmètre du modèle lui-même : aucun champ lié à un panier, une
  commande ou un paiement n'a été inclus ci-dessus).
