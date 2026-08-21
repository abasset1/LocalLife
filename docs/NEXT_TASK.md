# NEXT_TASK.md

## État actuel

Sprint 6 terminé (`LL-6001` → `LL-6011`).

Phase actuelle : **Phase 2 — Validation du MVP**.

`LL-7001` terminé : protocole de validation documenté dans
`docs/02_Architecture/MVP_VALIDATION_PROTOCOL.md` (dix scénarios,
critères de succès/échec, vérification d'indépendance vis-à-vis du
hors-MVP).

`LL-7002` terminé : `POST /api/v1/admin/import` (rôle `ADMIN`), déclenche
`ImportService#importAll()` sans dupliquer le pipeline existant. Aucun
scheduler, conformément à la décision MVP du sprint.

`LL-7003` terminé : parcours de bout en bout validé avec de vraies
données OpenAgenda (import réel, persistance, visibilité publique,
consultation par id). Blocage réel trouvé et documenté pour LL-7007 :
contrainte `chk_activity_status` n'autorisant pas `ARCHIVED` (utilisé
par `ImportService` depuis LL-5008) → tout second import échoue en
`500` tant que non corrigé. Voir `PROJECT_STATUS.md`, section Sprint 7.

`LL-7004` terminé : recherche par zone, filtre catégorie, filtre date
et affichage des Food Trucks validés. Deux blocages réels trouvés et
documentés pour LL-7007 : `buildCategoryOptions` (`App.tsx`) plante sur
une catégorie `null` (fréquent avec de vraies données OpenAgenda),
cassant la vue par défaut ; la carte ne se recentre pas visuellement
après géolocalisation (`useMap()` manquant dans `App.tsx`).

`LL-7005` terminé : parcours utilisateur contribution / authentification
validé de bout en bout (inscription, connexion, maintien de la session,
création d'une activité en `PENDING`, publication/rejet, déconnexion,
retour d'erreur compréhensible). Aucun blocage réel trouvé — un `500`
rencontré en test s'est avéré être un artefact d'encodage UTF-8 côté
`Invoke-RestMethod`/PowerShell (non reproductible via le formulaire
frontend), sans lien avec le backend. Voir `PROJECT_STATUS.md`, section
Sprint 7.

`LL-7006` terminé : parcours Food Truck (Sprint 6) revérifié — création,
position, distinction visuelle, cohérence avec les activités sur la
carte et protection par JWT tous validés. Aucun blocage réel trouvé
(un test initial sans `401` s'est avéré être un `$headers` résiduel
d'une session PowerShell précédente, reconfirmé `401` avec un terminal
neuf). Voir `PROJECT_STATUS.md`, section Sprint 7.

`LL-7007` terminé : trois corrections apportées, liées aux blocages
trouvés en LL-7003/LL-7004 — contrainte `chk_activity_status`
autorisant désormais `ARCHIVED` (migration `V13`) ; `buildCategoryOptions`
(`App.tsx`) ne plante plus sur une catégorie `null` ; carte Leaflet
recentrée après géolocalisation (`MapRecenterOnUserPosition`, `useMap()`).
Voir `PROJECT_STATUS.md`, section Sprint 7, pour le détail et la note
de clarification de périmètre (recentrage de carte confirmé inclus par
Alex, malgré une liste « et rien d'autre » incomplète dans une
précédente version de ce fichier).

`LL-7008` terminé : guide de démonstration ajouté au `README.md`
racine (base de données, configuration, démarrage, compte admin,
déclenchement d'un import, vérification de la carte). Lacune relevée :
aucun mécanisme applicatif pour créer un premier compte `ADMIN`
(contournement documenté, lacune tracée dans `DETTE_TECHNIQUE.md`).
Documentation obsolète corrigée au passage
(`COLLECTOR_OPERATIONS.md`/`DETTE_TECHNIQUE.md` décrivaient encore
l'absence de déclencheur d'import, alors que LL-7002 l'a ajouté). Voir
`PROJECT_STATUS.md`, section Sprint 7.

## Prochaine tâche

**Sprint 8 — Préparation de la bêta**

### Prochain ticket

**LL-8001 — Rejouer le parcours MVP après les corrections et figer la baseline**,
détail : `docs/05_Sprints/SPRINT_8.md`.

Le Sprint 7 est conclu avec la décision **MVP validé → préparation de la
bêta**. Le Sprint 8 ne doit pas ajouter de nouveau domaine métier : il doit
consolider le produit existant avant les premiers retours utilisateurs.

## Règles

- Ne pas élargir le MVP avant les premiers retours de bêta.
- Toute correction doit être reliée à un risque de mise en bêta, une dette technique ou un critère de baseline.
- Un ticket terminé doit être vérifié dans Git, les tests ou une validation documentée.
