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

## Prochaine tâche

**Sprint 7 — Validation du MVP**

### Prochain ticket

**LL-7005 — Valider le parcours utilisateur contribution / authentification**

Dépendance : LL-7004 (terminé). Détail :
`docs/05_Sprints/SPRINT_7.md`.

## Règles

- Ne pas créer de Sprint 8 avant la conclusion de Sprint 7.
- Ne pas ajouter de nouvelle fonctionnalité majeure pendant la validation.
- Toute correction doit être reliée à un scénario de validation.
- Un ticket terminé doit être vérifié dans Git, les tests ou la validation documentée.
