# NEXT_TASK.md

## État actuel

Sprint 7 terminé (`LL-7001` → `LL-7009`). MVP validé de bout en bout
avec de vraies données OpenAgenda, blocages trouvés corrigés (LL-7007),
guide de démonstration ajouté au `README.md` (LL-7008). Détail complet :
`docs/PROJECT_STATUS.md`, section Sprint 7.

Phase actuelle : **Phase 2 — Validation et préparation de la bêta**.

**Sprint 8 — Préparation de la bêta**, en cours. Huit tickets sur neuf
terminés :

`LL-8001` terminé : parcours MVP rejoué après les corrections du
Sprint 7, aucune régression, baseline figée.

`LL-8002` terminé : premier compte `ADMIN` bootstrapé automatiquement
au premier démarrage (`AdminBootstrapRunner`,
`LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL`/`LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD`),
remplace la promotion SQL manuelle utilisée jusque-là.

`LL-8003` terminé : exceptions serveur non gérées (réponse `500`)
journalisées au niveau `ERROR` par `GlobalExceptionHandler`, sans mot
de passe ni JWT dans les logs ; contrat HTTP existant inchangé.

`LL-8004` terminé : plusieurs agendas OpenAgenda configurés pour
Avignon ; pagination complète de l'API OpenAgenda (`size=300` + curseur
`after`), l'API ne renvoyant que 20 résultats par défaut.

`LL-8005` terminé : import automatique planifié (`ImportScheduler`,
toutes les heures), en plus du déclenchement manuel existant
(`POST /api/v1/admin/import`, LL-7002).

`LL-8006` terminé : affichage de bout en bout des activités vérifié sur
la carte. Écart trouvé et corrigé : le popup n'affichait ni le lieu ni
la source lisible (seul `sourceId`, un identifiant technique, était
exposé) — `ActivityResponse` résout désormais `sourceId` en
`sourceName`, popup complété. Corrections complémentaires signalées par
Alex au fil de `mvn verify` : test d'intégration dépendant d'un id
d'activité fixe (fragile sur base persistante), dépassements de la
limite Checkstyle de 120 caractères.

`LL-8007` terminé : dette technique pertinente pour une bêta traitée —
vulnérabilité `nanoid` (déjà résolue, preuve formalisée), défaut de
formatage de `ActivityController` (corrigé), duplication des deux
`ROADMAP.md` (déjà résolue avant ce ticket, preuve formalisée). Aucune
nouvelle dette bloquante découverte pendant LL-8001.
`docs/DETTE_TECHNIQUE.md` ne contient plus d'entrée « ouverte ».

`LL-8008` terminé (ce ticket) : documentation consolidée —
`README.md`, `backend/README.md`, `frontend/README.md`,
`docs/PROJECT_STATUS.md`, `docs/04_Project/ROADMAP.md`,
`docs/01_Product/BACKLOG.md`, `docs/NEXT_TASK.md` (ce fichier),
`docs/DETTE_TECHNIQUE.md`, `CHANGELOG.md` — plus aucune ne désigne le
Sprint 7 comme sprint courant ; le guide de démonstration du `README.md`
racine reflète la baseline actuelle (import automatique, popup
lieu/source, clustering).

## Prochaine tâche

**LL-8009 — Décider et documenter l'ouverture de la première bêta
contrôlée**, détail : `docs/05_Sprints/SPRINT_8.md`.

Dernier ticket du Sprint 8 : formaliser une décision go/no-go à partir
de la baseline figée en LL-8001 et des corrections apportées depuis
(LL-8002 → LL-8008), avec une checklist de bêta documentée.

## Règles

- Ne pas élargir le MVP avant les premiers retours de bêta.
- Toute correction doit être reliée à un risque de mise en bêta, une dette technique ou un critère de baseline.
- Un ticket terminé doit être vérifié dans Git, les tests ou une validation documentée.
