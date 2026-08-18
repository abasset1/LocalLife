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

## Prochaine tâche

**Sprint 7 — Validation du MVP**

### Prochain ticket

**LL-7003 — Valider le parcours de données réelles de bout en bout**

Dépendance : LL-7002 (terminé). Nécessite un import réel exécuté par
Alex (hors sandbox — pas d'accès réseau externe ici, ni d'identifiants
OpenAgenda), via `POST /api/v1/admin/import`. Détail :
`docs/05_Sprints/SPRINT_7.md`.

## Règles

- Ne pas créer de Sprint 8 avant la conclusion de Sprint 7.
- Ne pas ajouter de nouvelle fonctionnalité majeure pendant la validation.
- Toute correction doit être reliée à un scénario de validation.
- Un ticket terminé doit être vérifié dans Git, les tests ou la validation documentée.
