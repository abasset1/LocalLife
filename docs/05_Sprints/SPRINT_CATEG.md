SPRINT CATEG — Normalisation et référentiel des catégories
Objectif du sprint

Remplacer le système actuel de catégories libres par un référentiel métier unique et cohérent.

À la fin du sprint :

toutes les activités possèdent une catégorie référentielle ;
les catégories existantes sont normalisées ;
les événements sans catégorie sont classifiés ;
les imports externes utilisent le référentiel LocalLife ;
la saisie manuelle ne permet plus de créer de catégorie libre ;
le frontend utilise le référentiel officiel ;
aucune nouvelle catégorie incohérente ne peut être introduite.
Règle générale

Les tickets doivent être réalisés dans l'ordre.

Claude ne doit pas anticiper l'implémentation d'un ticket suivant.

Chaque ticket doit être terminé avec :

code propre ;
tests associés ;
absence de régression ;
documentation mise à jour si nécessaire.
CAT-000 — Audit du système actuel de catégories
Objectif

Analyser complètement le fonctionnement actuel des catégories avant toute modification du code.

Travail demandé

Analyser le repository et identifier précisément :

le modèle Activity ;
le modèle Category ;
les repositories ;
les services ;
les controllers ;
les DTO ;
les validateurs ;
les imports ;
les formulaires de création/modification ;
les filtres frontend ;
les fixtures ;
les seeds ;
les migrations ;
les tests ;
toutes les lectures et écritures de category.

Identifier séparément les sources suivantes :

événements OpenAgenda ;
événements créés manuellement ;
données de test ;
éventuelles autres sources.

Identifier également toutes les valeurs actuellement utilisées comme catégories.

Pour chaque valeur, déterminer :

nombre d'occurrences ;
source ;
variantes ;
catégorie métier potentielle ;
ambiguïtés éventuelles.
Livrable

Créer :

docs/CATEGORIES_AUDIT.md

Le document doit contenir :

architecture actuelle ;
flux de création d'une catégorie ;
producteurs de catégories ;
consommateurs de catégories ;
inventaire des valeurs existantes ;
problèmes constatés ;
risques de migration ;
recommandations pour CAT-001.
Critères d'acceptation
 Tous les producteurs de catégories sont identifiés.
 Tous les consommateurs sont identifiés.
 Le fonctionnement OpenAgenda est documenté.
 La saisie manuelle est documentée.
 Le modèle Category existant est documenté.
 Les valeurs existantes sont inventoriées.
 Aucun code métier n'est modifié.
 Les tests existants passent.
CAT-001 — Définition du référentiel officiel des catégories
Objectif

Définir le vocabulaire officiel des catégories LocalLife.

Travail demandé

À partir de l'audit CAT-000, définir le référentiel officiel.

Le référentiel ne doit pas être conçu uniquement à partir des catégories actuellement présentes. Il doit représenter les catégories métier que LocalLife souhaite supporter, tout en restant justifié par les données observées.

La proposition initiale à analyser est :

Code	Libellé
MUSIC	Musique
SHOW	Spectacle
CULTURE	Culture & arts
HERITAGE	Patrimoine & visites
FESTIVAL	Fête & festival
MARKET	Marché & brocante
FOOD	Gastronomie
SPORT	Sport
FAMILY	Famille & enfants
NATURE	Nature & plein air
LOCAL_LIFE	Vie locale & associations
PROFESSIONAL	Professionnel
CINEMA	Cinéma & audiovisuel
COMMERCE	Commerce & animations commerciales
TRADITION	Traditions & spiritualité
YOUTH	Jeunesse
OTHER	Autre

Cette liste est une base de travail et doit être confrontée aux données réelles découvertes dans CAT-000.

Une catégorie peut être :

conservée ;
fusionnée ;
supprimée ;
ajoutée.

Toute modification doit être justifiée.

Pour chaque catégorie

Définir :

code technique ;
nom affiché ;
description ;
règles d'utilisation ;
exemples ;
contre-exemples ;
cas ambigus.
Livrable

Créer :

docs/CATEGORIES_REFERENCE.md

Critères d'acceptation
 Chaque catégorie possède un code stable.
 Les codes sont indépendants du libellé affiché.
 Chaque catégorie possède une définition.
 Chaque catégorie possède des exemples.
 Les cas ambigus sont documentés.
 OTHER est défini.
 Le document constitue le référentiel métier officiel.
 Aucun code applicatif n'est modifié.
CAT-002 — Définition des règles de classification
Objectif

Définir précisément comment une activité est transformée en catégorie LocalLife.

Travail demandé

Créer un dictionnaire de classification permettant de mapper les données sources vers les catégories officielles.

Exemples :

concert, concerts, musique, musical, récital → MUSIC

brocante, vide-grenier, vide grenier, puces → MARKET

Définir une normalisation préalable prenant en compte :

casse ;
espaces ;
accents ;
ponctuation ;
variantes singulier/pluriel ;
synonymes connus.

La normalisation sert uniquement à la classification.

Les données originales doivent rester disponibles dans les tags.

Sources utilisées pour classifier

Définir l'ordre de priorité :

mots-clés explicites ;
type fourni par la source ;
titre ;
description ;
OTHER.
Gestion des ambiguïtés

Définir explicitement les cas dans lesquels un mot-clé ne suffit pas.

Exemple :

festival

ne doit pas automatiquement être classé MUSIC.

La classification doit utiliser le contexte disponible.

Livrable

Créer :

docs/CATEGORIES_CLASSIFICATION_RULES.md

Le document doit contenir :

dictionnaire ;
synonymes ;
variantes ;
règles de normalisation ;
priorités ;
règles de conflit ;
cas ambigus ;
règles OTHER ;
exemples de classification.
Critères d'acceptation
 Toutes les catégories possèdent des règles.
 Les synonymes principaux sont couverts.
 Les variantes de casse sont couvertes.
 Les variantes d'accents sont couvertes.
 Les principaux pluriels sont couverts.
 Les ambiguïtés sont documentées.
 Les règles de priorité sont définies.
 Le comportement OTHER est défini.
 Aucun code applicatif n'est modifié.
CAT-003 — Mise en place du modèle Category ↔ Activity
Objectif

Faire du référentiel Category le référentiel réellement utilisé par Activity.

État actuel

Le système utilise actuellement une catégorie sous forme de valeur libre.

État cible

Activity doit référencer une Category du référentiel.

La catégorie ne doit plus être stockée comme une chaîne libre.

Category

Le modèle doit permettre au minimum de gérer :

id ;
code ;
name ;
description ;
active ;
display_order.

Adapter cette structure à l'architecture existante sans introduire de complexité inutile.

Migration

Créer les migrations nécessaires pour :

créer ou adapter le référentiel ;
insérer les catégories officielles ;
créer la relation entre Activity et Category ;
préparer la migration des données existantes.
Critères d'acceptation
 Category est le référentiel officiel.
 Activity référence une catégorie.
 Une activité ne peut pas référencer une catégorie inexistante.
 Le code métier ne dépend pas du libellé affiché.
 Les migrations sont cohérentes.
 Aucun événement existant n'est supprimé.
 Les tests backend passent.
CAT-004 — Implémentation du moteur de classification
Objectif

Implémenter les règles définies dans CAT-002.

Entrées du moteur

Le moteur doit pouvoir recevoir au minimum :

titre ;
description ;
mots-clés/tags ;
source ;
autres informations structurées disponibles.
Sortie

Le moteur retourne une catégorie du référentiel LocalLife.

Exemple :

Titre :
Concert de jazz

Keywords :
concert
jazz
musique

Résultat :
MUSIC
Cas sans information suffisante
Titre :
Événement XYZ

Keywords :
aucun

Résultat :
OTHER
Contraintes

Le moteur doit être :

déterministe ;
testable ;
indépendant du frontend ;
indépendant d'OpenAgenda ;
réutilisable par les autres sources.
Tests obligatoires

Tester notamment :

casse ;
accents ;
pluriels ;
synonymes ;
plusieurs mots-clés ;
conflits entre mots-clés ;
titre sans mot-clé ;
description sans mot-clé ;
absence totale d'information ;
OTHER.
Critères d'acceptation
 Le moteur est placé dans la couche métier appropriée.
 Il ne dépend pas directement d'OpenAgenda.
 Il est déterministe.
 Les règles CAT-002 sont couvertes par des tests.
 Les tags originaux sont conservés.
 Les tests passent.
CAT-005 — Migration et reclassement des activités existantes
Objectif

Reclasser toutes les activités déjà présentes dans la base.

Travail demandé

Pour chaque activité :

récupérer les anciennes informations de catégorie ;
récupérer les informations disponibles pour la classification ;
appliquer le moteur de classification ;
affecter la catégorie correspondante ;
utiliser OTHER si aucune classification fiable n'est possible.
Règle importante

Aucune activité ne doit être supprimée uniquement parce qu'elle ne possède pas de catégorie.

Rapport de migration

Produire un rapport permettant d'identifier :

anciennes catégories ;
nouvelles catégories ;
nombre d'activités par migration ;
activités classées OTHER ;
cas ambigus éventuels.
Critères d'acceptation
 Toutes les activités possèdent une catégorie.
 Aucun category = null.
 Les variantes sont regroupées.
 Les anciennes catégories incohérentes sont supprimées du modèle métier.
 Les cas non classifiables utilisent OTHER.
 La migration est reproductible.
 Aucun événement n'est perdu.
CAT-006 — Normalisation des imports
Objectif

Faire en sorte que les imports utilisent systématiquement le moteur de classification.

OpenAgenda

Supprimer le fonctionnement consistant à utiliser directement le premier keyword comme catégorie.

Le flux cible est :

OpenAgenda
    ↓
données brutes
    ↓
keywords / titre / description
    ↓
moteur de classification LocalLife
    ↓
Category
Règles

Une source externe ne doit jamais imposer directement une catégorie LocalLife.

Les tags originaux doivent continuer à être conservés.

Les événements sans mots-clés doivent pouvoir être classifiés à partir du titre et de la description.

Les événements réellement non classifiables doivent être affectés à OTHER.

Critères d'acceptation
 OpenAgenda utilise le moteur de classification.
 Le premier keyword n'est plus utilisé directement comme catégorie.
 Les tags originaux sont conservés.
 Les événements sans keyword sont traités.
 Les événements non classifiables utilisent OTHER.
 Aucun import ne peut créer une catégorie inconnue.
 Les tests d'import passent.
CAT-007 — Suppression de la saisie libre des catégories
Objectif

Empêcher définitivement la création de catégories arbitraires par l'API ou le frontend.

Backend

Les endpoints de création et de modification d'activité doivent recevoir une référence vers une catégorie existante.

Le backend doit refuser toute catégorie inexistante.

Une catégorie inconnue doit provoquer une erreur HTTP appropriée, typiquement 400 Bad Request.

Frontend

Remplacer le champ texte libre par un sélecteur utilisant le référentiel officiel.

Le frontend ne doit pas maintenir sa propre liste de catégories.

Critères d'acceptation
 Impossible de créer une activité avec une catégorie arbitraire.
 Impossible de modifier une activité avec une catégorie arbitraire.
 Impossible de référencer une catégorie inexistante.
 Le frontend utilise le référentiel officiel.
 Les catégories sont chargées depuis l'API ou la source prévue par l'architecture.
 Les tests API passent.
 Les tests frontend passent.
CAT-008 — Refonte des filtres frontend
Objectif

Utiliser le référentiel Category comme source de vérité des filtres.

État cible

Les catégories disponibles ne doivent plus être calculées uniquement à partir des activités actuellement affichées.

Le frontend doit utiliser le référentiel officiel.

Règles

Les catégories doivent :

utiliser le libellé officiel ;
respecter display_order ;
ne jamais afficher de doublons ;
ne jamais afficher null ;
ne jamais afficher une valeur brute provenant d'OpenAgenda ;
être cohérentes entre la carte et la liste.
Critères d'acceptation
 Les filtres utilisent le référentiel officiel.
 L'ordre est maîtrisé.
 Les doublons sont impossibles.
 Les anciennes valeurs ne sont plus affichées.
 Les filtres carte/liste utilisent les mêmes catégories.
 Le comportement existant de filtrage est conservé.
CAT-009 — Mise à jour des tests, fixtures et documentation
Objectif

Supprimer toutes les anciennes hypothèses de catégories libres.

Travail demandé

Rechercher dans tout le repository :

anciennes valeurs de catégories ;
fixtures ;
mocks ;
seeds ;
tests ;
exemples API ;
documentation ;
données de démonstration.

Mettre tout en conformité avec le nouveau référentiel.

Critères d'acceptation
 Les fixtures utilisent les catégories officielles.
 Les mocks utilisent les catégories officielles.
 Les tests utilisent les catégories officielles.
 Les exemples API sont à jour.
 La documentation est cohérente.
 Les anciennes catégories libres ne sont plus utilisées.
 La suite de tests complète passe.
CAT-010 — Audit final et garde-fous
Objectif

Vérifier que la nouvelle architecture est réellement étanche.

Travail demandé

Rechercher dans l'ensemble du repository toutes les possibilités de :

créer une catégorie libre ;
affecter directement une chaîne à une activité ;
contourner le référentiel ;
créer une catégorie inconnue lors d'un import ;
afficher une catégorie provenant directement d'une source externe.

Ajouter les garde-fous nécessaires.

Tests d'intégration obligatoires

Tester au minimum :

import OpenAgenda avec un concert ;
import OpenAgenda avec un festival ;
import sans keyword ;
import avec plusieurs keywords ;
création manuelle d'un événement ;
création avec catégorie valide ;
création avec catégorie inconnue ;
modification d'une catégorie ;
affichage des catégories dans les filtres ;
activité classifiée OTHER.
Critères d'acceptation
 Aucun chemin de création ne permet une catégorie libre.
 Toutes les activités possèdent une catégorie référentielle.
 Tous les imports utilisent le classifieur.
 Le frontend utilise le référentiel.
 OTHER fonctionne correctement.
 Les tests unitaires passent.
 Les tests d'intégration passent.
 Le CI passe.
 La documentation est à jour.
 Aucun contournement évident du référentiel n'existe.
