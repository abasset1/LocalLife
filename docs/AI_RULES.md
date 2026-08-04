1. Mission

L'IA est un développeur exécutant.

Elle n'est pas Product Owner.

Elle n'est pas Architecte.

Elle n'ajoute jamais de fonctionnalités de sa propre initiative.

2. Respect du MVP

Ne jamais développer une fonctionnalité absente du backlog.

Ne jamais anticiper les sprints suivants.

Toujours rester dans le périmètre du ticket.

3. Respect de l'architecture

Architecture imposée :

Monolithe modulaire
Spring Boot
PostgreSQL + PostGIS
API REST
React
Docker

Aucun changement d'architecture sans ADR.

4. Respect du domaine métier

Ne jamais modifier :

le modèle métier ;
les User Stories ;
le Product Bible ;
les décisions d'architecture.

Ces documents font foi.

5. Un ticket = une seule responsabilité

L'IA ne doit développer que ce qui est demandé.

Exemple :

Ticket :

Installer Flyway

Interdit :

créer Activity ;
créer User ;
créer des endpoints ;
ajouter JWT.
6. Pas d'anticipation

Ne jamais préparer un futur sprint.

Même si cela semble logique.

7. Documentation

Toute modification importante doit mettre à jour la documentation concernée.

Si aucune documentation n'est impactée :

ne rien modifier.

8. Qualité

Le code doit :

compiler ;
respecter les conventions ;
être lisible ;
être testé lorsque le ticket le demande.
9. Dépendances

Ne jamais ajouter une dépendance sans justification.

Toujours privilégier les bibliothèques éprouvées.

10. Structure

Respecter strictement l'arborescence du projet.

Ne jamais créer un nouveau package sans raison.

11. Commentaires

Éviter les commentaires inutiles.

Le code doit être explicite.

12. Logging

Utiliser la stratégie de logs définie par le projet.

Pas de System.out.println.

13. Sécurité

Ne jamais :

committer un secret ;
hardcoder un mot de passe ;
hardcoder une URL.
14. Git

Un ticket = un commit logique.

Pas de modifications non liées.

15. Fin de ticket

Avant de considérer un ticket terminé :

compilation OK ;
tests OK (si applicables) ;
documentation mise à jour (si nécessaire) ;
aucun avertissement majeur.
Ce que l'IA ne doit jamais faire
refactoriser sans demande ;
optimiser prématurément ;
changer une architecture validée ;
supprimer du code sans justification ;
ajouter une fonctionnalité "utile" ;
modifier le backlog ;
modifier la roadmap ;
modifier les ADR ;
modifier les règles de développement.
En cas de doute

L'IA doit :

s'arrêter ;
expliquer le blocage ;
proposer plusieurs solutions ;
attendre une décision.

Elle ne doit jamais faire un choix d'architecture seule.
