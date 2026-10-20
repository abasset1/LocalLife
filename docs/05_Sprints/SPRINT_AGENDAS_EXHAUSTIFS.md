# Sprint — Découverte exhaustive et import fiable des agendas

**Statut :** spécification préparée pour implémentation par Claude  
**Périmètre :** backend, API d'administration, interface web et tests  
**Règle :** ne pas modifier le code dans ce sprint de préparation ; Claude implémentera et exécutera les validations.

## 1. Résultat attendu

LocalLife doit pouvoir découvrir les agendas accessibles auprès d'un fournisseur, puis récupérer tous les événements accessibles de chaque agenda, sans plafond arbitraire silencieux. Une limite imposée par le fournisseur, les autorisations, les quotas ou une protection technique explicite doit être signalée comme telle. L'application ne doit jamais annoncer une importation complète si elle s'est arrêtée avant la fin.

## 2. Audit ciblé du dépôt au 20 octobre 2026

Fichiers inspectés :
- `backend/src/main/java/com/locallife/backend/collector/infrastructure/OpenAgendaAgendaDirectory.java`
- `backend/src/main/java/com/locallife/backend/collector/infrastructure/OpenAgendaCollector.java`
- `backend/src/main/java/com/locallife/backend/collector/application/AgendaDiscoveryService.java`
- `backend/src/main/java/com/locallife/backend/collector/api/AdminImportController.java`
- `frontend/src/components/admin/AdminImportsSection.tsx`
- tests du répertoire OpenAgenda et `docs/02_Architecture/COLLECTOR_OPERATIONS.md`

### Constatations

1. **La découverte n'est pas plafonnée à 500 par l'endpoint actuel.** Sans paramètre `limit`, `AdminImportController` transmet `Integer.MAX_VALUE`; `OpenAgendaAgendaDirectory` parcourt les pages avec le curseur `after[]`. Un paramètre `limit` positif permet une découverte partielle.
2. **La découverte ne couvre que les agendas marqués officiels.** La requête utilise `official=1`. Elle ne démontre donc pas qu'elle couvre tous les agendas publiquement accessibles chez OpenAgenda.
3. **La découverte crée automatiquement une source pour chaque agenda inconnu**, puis l'import planifié collecte les sources actives. Une découverte exhaustive pourrait donc activer une très grande quantité de sources sans étape de sélection ni estimation de volume.
4. **La collecte d'événements est paginée par lots de 300**, mais `OpenAgendaCollector` déclare `MAX_PAGES = 500`. Il faut vérifier le comportement quand ce garde-fou est atteint : aucune importation ne doit être présentée comme exhaustive si des pages restent.
5. La pagination repose sur la forme du curseur documentée, mais les commentaires du collecteur et de l'annuaire indiquent que le comportement n'a pas été confirmé par un appel réel dans l'environnement de développement.
6. La découverte construit une liste en mémoire avant de créer les sources. Aucun mécanisme de reprise persistante de découverte n'a été établi dans les fichiers inspectés.
7. L'interface web affiche un bilan global de découverte et les noms créés, mais ne propose pas actuellement de prévisualisation/choix des agendas à activer avant création des sources.

**Limite de cet audit :** examen ciblé des fichiers listés ci-dessus, pas audit exhaustif de toutes les sources, migrations, contraintes du fournisseur ou de l'environnement de production.

## 3. Décisions de conception à conserver

- Distinguer clairement **découverte** (quels agendas existent), **sélection/activation** (quels agendas LocalLife accepte d'importer) et **collecte des événements**.
- Ne pas activer automatiquement des milliers de sources simplement parce qu'elles sont découvrables.
- Conserver le principe existant : clé API hors base et hors logs ; ne jamais exposer les secrets.
- Conserver l'idempotence : un même identifiant d'agenda ne crée pas plusieurs sources.
- Conserver les données déjà importées en cas d'échec ou de collecte incomplète. En particulier, ne pas archiver en masse des événements au seul motif que la pagination a échoué ou s'est interrompue.
- Préserver la consultation publique gratuite de la carte et de la liste : ce sprint concerne l'alimentation des données, pas la monétisation.
- Ne pas confondre « tous les agendas accessibles » avec « tous les événements visibles à l'infini » : documenter les limites réelles du fournisseur, des droits et des périodes disponibles.

## 4. Tickets du sprint

### AGD-01 — Confirmer le contrat réel de l'API OpenAgenda
- Vérifier avec la documentation actuelle et, si un environnement disposant de la clé est disponible, un appel réel, les filtres d'annuaire, le paramètre `official`, la taille maximale, la forme du curseur et la fin de pagination.
- Définir précisément ce que signifie « accessible » : agendas officiels uniquement, ou tous les agendas consultables avec la clé utilisée.
- Documenter ce qui est vérifié en test simulé et ce qui est vérifié contre l'API réelle.

**Acceptation :** contrat documenté, sans prétendre à une couverture non démontrée.

### AGD-02 — Découverte exhaustive, paginée et reprenable
- Couvrir le périmètre d'agendas décidé au ticket AGD-01.
- Traiter chaque page jusqu'au marqueur de fin du fournisseur ; détecter curseur répété, réponse incohérente et page vide.
- Éviter de charger l'intégralité de l'annuaire dans une seule réponse HTTP longue si le volume le rend fragile.
- Prévoir une opération par lots et/ou un état de progression reprenable si nécessaire ; une relance ne doit pas dupliquer les sources.
- Renvoyer un bilan indiquant au minimum : examinés, nouveaux, déjà connus, ignorés, erreurs, statut complet/incomplet et raison de l'arrêt.

**Acceptation :** une interruption ou un échec ne peut pas être présenté comme une découverte exhaustive réussie.

### AGD-03 — Prévisualiser et contrôler l'activation des sources
- Séparer la découverte de la création/activation de sources.
- Fournir un aperçu avec nom, identifiant fournisseur, URL si disponible et statut déjà connu.
- Permettre de sélectionner un ensemble pertinent avant activation/import, ou fournir un mécanisme équivalent de filtre géographique/thématique si le fournisseur le permet.
- Préserver le choix d'un administrateur : une source désactivée ne doit pas être réactivée par une redécouverte.
- Prévoir des limites de lot explicites et configurables, pas un plafond caché.

**Acceptation :** découvrir tout l'annuaire ne lance pas automatiquement une collecte massive.

### AGD-04 — Garantir l'exhaustivité de la collecte des événements
- Vérifier la pagination de `OpenAgendaCollector` pour toutes les pages jusqu'à la fin réelle du curseur.
- Remplacer le plafond fixe `MAX_PAGES = 500` par un mécanisme qui ne tronque pas silencieusement : soit pagination complète avec détection de boucle, soit arrêt explicite en statut incomplet avec possibilité de reprise.
- Conserver une limite de sécurité opérationnelle si nécessaire, mais signaler clairement son déclenchement et empêcher la déclaration de succès complet.
- Traiter les erreurs réseau, les quotas/rate limits et les réponses mal formées avec reprises bornées et temporisation appropriée.
- Préserver l'idempotence et la déduplication, sans perdre les événements déjà importés.
- Ne pas archiver des événements sur la base d'une collecte partielle ou échouée.

**Acceptation :** des milliers d'événements sont traités au-delà de la limite historique de 500 pages ; aucune troncature silencieuse et aucun archivage destructeur après import incomplet.

### AGD-05 — Observabilité et état de synchronisation
- Distinguer au minimum `SUCCESS`, `PARTIAL` et `FAILED` pour une collecte.
- Exposer dans le bilan : pages lues, événements reçus, créés/mis à jour/inchangés, ignorés, doublons, erreurs, curseur/raison d'arrêt non sensible et durée.
- Ne mettre à jour la date du dernier succès complet qu'après une collecte complète. Si le modèle actuel ne permet pas de séparer dernier essai et dernier succès, documenter puis proposer la migration minimale.
- Ne jamais journaliser la clé API ni les données sensibles inutiles.

**Acceptation :** l'admin peut distinguer un agenda synchronisé intégralement d'un agenda partiel ou en échec.

### AGD-06 — Tests et non-régression
- Tests de pagination sur plusieurs pages et plus de 500 pages simulées, curseur répété, curseur absent, page vide, échec au milieu, quota, reprise et réponse mal formée.
- Tests d'idempotence de découverte et de sélection/activation.
- Tests garantissant qu'un import partiel n'archive pas en masse les activités.
- Tests d'autorisation ADMIN pour les opérations d'administration.
- Exécuter `mvn verify` et `npm run build` ; consigner les résultats réels, sans les déclarer passants s'ils n'ont pas été exécutés.
- Mettre à jour la documentation opérationnelle et les critères d'acceptation.

## 5. Définition de terminé

- Le périmètre d'annuaire est explicitement défini et vérifié.
- Toutes les pages accessibles sont traitées ou l'incomplétude est explicitement signalée avec un moyen de reprise.
- Aucune limite de pages ou de résultats n'est silencieuse.
- La découverte n'active pas automatiquement toutes les sources trouvées sans contrôle.
- Les imports partiels/échoués ne déclenchent pas d'archivage massif.
- Les tests automatisés passent ; les contrôles nécessitant la clé réelle sont clairement identifiés.
- Les docs et l'interface admin reflètent l'état réel de l'opération.

## 6. Instructions de passation à Claude

Implémenter ce sprint dans le dépôt `abasset1/LocalLife`, sans réécrire l'architecture par défaut. Commencer par vérifier les fichiers et le comportement actuels, puis proposer un plan court avant les changements. Respecter les contrats existants, les migrations Flyway et les conventions de tests du projet. Ne pas inventer de résultats d'appel réel à OpenAgenda : si la clé ou l'accès réseau manque, le signaler. Priorité absolue à l'intégrité des données : aucun succès complet si pagination interrompue et aucun archivage à partir d'une collecte partielle. À la fin, fournir les fichiers modifiés, les migrations éventuelles, les commandes réellement exécutées et leurs résultats, les limites restant à valider, puis ouvrir une PR dédiée.
