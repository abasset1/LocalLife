LocalLife — FRONT-REV-02

Fluidité de l'explorateur, catégories et recherche par ville

Priorité : P0 pour les problèmes fonctionnels, P1 pour l'amélioration de navigation.

1. Objectif

Rendre l'explorateur LocalLife fluide et compréhensible sur smartphone, sans réécrire l'application ni modifier inutilement l'architecture.

2. Problèmes constatés lors des tests utilisateur

- La recherche par ville ne fonctionne pas correctement.
- L'utilisateur ne dispose pas d'une sélection de catégories opérationnelle.
- Les catégories ne sont pas correctement accessibles ou appliquées dans les vues carte et liste.
- Les onglets de navigation encombrent l'interface.
- Un menu rétractable est nécessaire.

3. Travail demandé

A. Diagnostiquer et réparer la recherche par ville

- Reproduire le dysfonctionnement avant toute modification.
- Vérifier la saisie, les suggestions, la sélection d'une ville et la mise à jour des résultats.
- Vérifier les états de chargement, les erreurs et l'absence de résultats.
- Ne pas réintroduire de ville par défaut silencieuse.
- Conserver une solution de recherche manuelle utilisable si la géolocalisation est refusée.

B. Rétablir les filtres par catégories

- Afficher un contrôle de sélection de catégories clairement identifiable.
- Permettre la sélection et la désélection d'une ou plusieurs catégories, selon les capacités du modèle existant.
- Appliquer réellement les filtres aux résultats.
- Garantir un comportement cohérent sur la carte et dans la liste.
- Conserver la ville, les filtres et l'état de recherche lors du changement de vue.
- Permettre de retirer facilement les filtres actifs.

C. Simplifier la navigation

- Remplacer les onglets encombrants par un menu rétractable adapté aux petits écrans.
- Conserver l'accès aux rubriques existantes.
- Rendre l'ouverture et la fermeture du menu évidentes.
- Éviter que le menu masque durablement la recherche ou les résultats.
- Préserver l'ergonomie sur ordinateur si les composants sont partagés.

4. Contraintes

- Auditer le code actuel avant de choisir les fichiers à modifier.
- Réutiliser la logique existante de recherche et de filtrage.
- Éviter les logiques dupliquées entre carte et liste.
- Ne pas modifier le backend, les contrats API ou le modèle de données sans justification démontrée.
- La consultation de la carte et de la liste doit rester gratuite.
- Ne pas entreprendre de refonte générale ni ajouter de fonctionnalités commerciales dans ce ticket.

5. Critères d'acceptation

1. Une ville peut être recherchée, sélectionnée et utilisée pour actualiser les résultats.
2. Les erreurs de recherche sont explicites et récupérables.
3. Les catégories peuvent être sélectionnées et désélectionnées.
4. Les résultats de la carte et de la liste respectent les mêmes filtres.
5. Les filtres persistent lors du passage de la carte à la liste et inversement.
6. L'absence de résultat est distinguée d'une erreur technique.
7. Le menu se replie et se déploie correctement sur smartphone.
8. La recherche, les filtres et l'accès aux rubriques restent utilisables sur petit écran.
9. Les fonctionnalités existantes non concernées ne régressent pas.

6. Procédure de test

- Rechercher plusieurs villes valides et sélectionner une suggestion.
- Tester une saisie invalide, une absence de résultat et une erreur réseau.
- Sélectionner une catégorie, puis plusieurs si le modèle le permet.
- Retirer une catégorie et réinitialiser les filtres.
- Comparer les résultats filtrés sur carte et liste.
- Changer plusieurs fois de vue et vérifier la persistance des critères.
- Ouvrir et fermer le menu sur smartphone et ordinateur.
- Vérifier les favoris, les détails d'activité et les autres parcours existants.
- Exécuter les tests automatisés et le build de production.

7. Compte rendu attendu

Avant modification : diagnostic des causes et liste des fichiers concernés.

Après modification : fichiers modifiés, tests exécutés avec résultats réels, régressions éventuelles et points restant à tester manuellement.

Ne pas déclarer le ticket terminé si un critère d'acceptation n'a pas été vérifié. Ne pas commencer un chantier différent avant la revue du résultat.
