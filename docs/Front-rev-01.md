LocalLife — FRONT-REV-01

Audit de validation fonctionnelle et mobile du frontend

Priorité : P0 — Validation
Dépôt : "https://github.com/abasset1/LocalLife.git"
Branche de référence : "main"
Nature : audit, tests et corrections ciblées
Règle : ne pas réimplémenter une fonctionnalité déjà livrée sans avoir démontré une anomalie.

---

1. Objectif

Vérifier que le frontend actuel de LocalLife fonctionne conformément aux spécifications existantes, notamment "docs/spec_front.md", et que les parcours essentiels restent opérationnels après les dernières modifications.

La consultation de la carte, de la liste et des détails des activités doit rester gratuite.

Le travail doit aboutir à un état des lieux vérifiable, distinguant :

- les fonctionnalités présentes et validées ;
- les fonctionnalités présentes mais insuffisamment testées ;
- les anomalies reproductibles ;
- les contrôles impossibles à effectuer dans l'environnement disponible.

Il ne s'agit pas d'une nouvelle refonte graphique ni d'un nouveau développement fonctionnel.

2. Étape préalable obligatoire : examiner le dépôt

Avant toute modification :

1. Vérifier la branche active, le commit courant et l'état du dépôt.
2. Lire "docs/spec_front.md" et les éventuelles décisions plus récentes qui concernent les mêmes fonctionnalités.
3. Examiner "frontend/package.json" pour identifier les commandes disponibles.
4. Examiner les tests existants et les composants concernés.
5. Vérifier si les critères de la spécification sont déjà satisfaits.
6. Identifier les lacunes de couverture avant d'ajouter des tests.

Ne pas écraser les modifications locales existantes. Ne pas réinitialiser le dépôt et ne pas modifier les données de production.

3. Fichiers à examiner

Fichiers principaux

- "frontend/src/App.tsx"
- "frontend/src/styles.css"
- "frontend/src/components/explorer/ExplorerSearch.tsx"
- "frontend/src/components/explorer/ExplorerFilters.tsx"
- "frontend/src/components/onboarding/OnboardingModal.tsx"

Fichiers secondaires, uniquement si nécessaire

- "frontend/src/components/explorer/ExplorerMap.tsx"
- "frontend/src/components/explorer/ActivityList.tsx"
- "frontend/src/components/explorer/ActivityPreview.tsx"
- "frontend/src/components/explorer/ActivityDetailModal.tsx"
- "frontend/src/components/explorer/SearchResultBanner.tsx"
- "frontend/src/components/explorer/useActivitySearch.ts"
- "frontend/src/components/explorer/constants.ts"

Examiner également les tests existants associés à ces composants, ainsi que les fonctions de recherche, de géolocalisation et de gestion des préférences.

Ne pas modifier tous ces fichiers par principe : limiter les changements au strict nécessaire.

4. Scénarios de validation fonctionnelle

A. Première ouverture et localisation

Vérifier les scénarios suivants :

1. Visiteur non connecté, sans préférence de ville et sans permission de localisation.
2. Choix manuel d'une ville, puis recherche d'activités.
3. Utilisation de « Autour de moi » avec permission accordée.
4. Utilisation de « Autour de moi » avec permission refusée.
5. Position indisponible ou délai de géolocalisation dépassé.
6. Utilisateur disposant déjà d'une ville enregistrée.

Critères d'acceptation :

- aucune demande de géolocalisation inattendue à l'arrivée ;
- aucune position géographique inventée présentée comme réelle ;
- aucune utilisation silencieuse de Marseille comme position de repli ;
- une solution de remplacement reste disponible en cas d'échec ;
- les préférences enregistrées continuent de fonctionner.

B. Onboarding et préférences

Vérifier :

- l'accès à l'explorateur sans configuration obligatoire ;
- la possibilité de choisir « Plus tard » ;
- l'absence de réouverture immédiate après le report ;
- l'enregistrement normal des préférences lorsqu'elles sont configurées ;
- la conservation des préférences existantes lors d'un report ;
- la visibilité des erreurs d'enregistrement.

Ne pas ajouter de stockage backend ou de nouvel endpoint uniquement pour ce contrôle sans justification préalable.

C. Recherche et navigation

Vérifier :

- la recherche textuelle ;
- les suggestions et la sélection d'une ville ;
- les requêtes trop courtes ;
- les filtres de catégories et de dates ;
- le filtre de rayon ;
- la pagination et la récupération des résultats ;
- le passage entre carte et liste ;
- la conservation des critères de recherche lors de ce changement ;
- l'ouverture d'un aperçu depuis un marqueur ou une ligne ;
- l'accès au détail d'une activité.

Critère d'acceptation : les parcours existants fonctionnent sans perte inattendue de filtres, de résultats ou de contexte.

D. Chargement, erreurs et absence de résultats

Tester séparément :

- le chargement normal ;
- une erreur réseau ;
- une réponse serveur en erreur ;
- une recherche valide sans résultat ;
- une nouvelle tentative après erreur ;
- une réponse vide pour une page ne correspondant pas nécessairement à une absence totale de résultats.

Critères d'acceptation :

- chargement, erreur et absence de résultats sont distingués ;
- l'erreur ne se présente pas comme une carte vide ;
- la nouvelle tentative ne nécessite pas de recharger toute l'application ;
- les critères de recherche sont conservés lorsque c'est approprié ;
- les commandes essentielles restent accessibles.

E. Compte et fonctionnalités existantes

Vérifier les parcours existants :

- inscription et connexion ;
- déconnexion ;
- accès aux fonctions de compte ;
- consultation des favoris, si disponibles dans le parcours testé ;
- contribution à une activité ;
- gestion des erreurs de validation.

Ne pas modifier les règles métier ou les droits backend dans le cadre de cet audit frontend.

5. Vérification mobile et responsive

Effectuer les contrôles sur smartphone Android, ou sur un émulateur si aucun appareil physique n'est disponible.

Tester :

- un petit écran ;
- un écran mobile courant ;
- le clavier virtuel ouvert dans la recherche ;
- les suggestions de villes ouvertes ;
- les filtres et la sélection du rayon ;
- le passage carte/liste ;
- l'aperçu d'activité et la fiche complète ;
- les fenêtres modales ;
- les messages de chargement et d'erreur ;
- l'absence de résultats ;
- le menu mobile.

Vérifier aussi le rendu desktop pour éviter les régressions CSS.

Critères d'acceptation :

- aucune commande essentielle inaccessible ;
- aucune fenêtre impossible à parcourir sur un écran bas ;
- aucune suggestion impossible à sélectionner ;
- aucune superposition bloquant l'interaction ;
- aucun débordement horizontal gênant ;
- boutons et champs utilisables au toucher.

Si un appareil réel n'est pas disponible, le signaler explicitement : un test sur émulateur ne constitue pas une validation complète sur téléphone physique.

6. Commandes de validation automatisée

Depuis le répertoire "frontend", exécuter :

npm ci
npm run test
npm run build

Précautions :

- vérifier la version de Node requise dans "frontend/package.json" ;
- ne pas modifier les dépendances uniquement pour faire passer un test ;
- ne pas masquer un test échoué ;
- conserver les sorties utiles et les erreurs ;
- ne pas annoncer de réussite sans résultat d'exécution.

"npm run test" lance les tests Vitest existants. "npm run build" vérifie notamment TypeScript et la compilation Vite.

Si "npm ci" ou une autre commande échoue pour une raison d'environnement, distinguer ce blocage d'un défaut applicatif.

7. Corrections autorisées

Après constat d'une anomalie reproductible :

1. Identifier la cause.
2. Déterminer si le défaut relève du frontend ou d'une dépendance externe.
3. Ajouter ou compléter un test de non-régression adapté.
4. Appliquer la correction minimale.
5. Relancer les tests pertinents.
6. Relancer les commandes globales de validation.

Toute modification touchant à l'API, au modèle métier, à la base de données ou aux droits backend doit être signalée et justifiée séparément. Ne pas élargir le périmètre sans validation.

Ne pas introduire de nouvelle bibliothèque, de refonte d'architecture ou de nouvelle fonctionnalité pour corriger un problème local.

8. Critères de sortie

Le lot est terminé lorsque :

- les commandes automatisées ont été exécutées, ou leurs blocages précisément documentés ;
- les scénarios critiques ont été vérifiés ;
- les défauts reproductibles sont corrigés ou transformés en tickets ;
- les tests ajoutés couvrent les régressions identifiées ;
- le rendu mobile et desktop a été contrôlé dans la mesure du possible ;
- la documentation a été mise à jour si les résultats modifient l'état du projet ;
- aucune réussite non vérifiée n'est annoncée.

Une anomalie critique non corrigée empêche de déclarer la validation complète.

9. Livrable attendu

Produire un compte rendu comprenant :

A. Résumé

État global du frontend et principaux risques constatés.

B. Tableau de validation

Pour chaque scénario :

Scénario| Résultat| Preuve| Action
Nom du scénario| PASS / FAIL / BLOCKED / NOT TESTED| Test, commande ou observation| Aucune / correction / ticket

C. Tests automatisés

Commandes exécutées, résultats et erreurs éventuelles.

D. Anomalies

Pour chaque défaut : étapes de reproduction, comportement attendu, comportement constaté, gravité et fichiers concernés.

E. Modifications

Liste exacte des fichiers modifiés, justification de chaque changement et tests associés.

F. Décision finale

Choisir explicitement :

- VALIDÉ : critères satisfaits ;
- VALIDÉ AVEC RÉSERVES : défauts non bloquants restant à traiter ;
- NON VALIDÉ : anomalie critique ou preuve insuffisante ;
- BLOQUÉ : environnement ou dépendance empêchant la validation.

G. Prochaine étape recommandée

Proposer les trois actions suivantes au maximum, classées par priorité et justifiées par les résultats observés.

Ne pas commencer le chantier suivant avant de présenter ce compte rendu.

---

Consigne finale : travailler à partir du code réellement présent dans "main". Ne pas considérer une spécification antérieure comme une preuve que le comportement est encore correct. Ne pas réécrire ce qui fonctionne. Le résultat attendu est un frontend vérifié, des anomalies identifiées et une base factuelle pour décider de la suite de LocalLife.
