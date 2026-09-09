# Backlog — LocalLife

**Source de vérité des tickets produit et des sprints.**

## Sprints terminés

- Sprint 0 : `LL-0001` → `LL-0015`
- Sprint 1 : `LL-1001` → `LL-1011`
- Sprint 2 : `LL-2001` → `LL-2013`
- Sprint 3 : `LL-3001` → `LL-3015`
- Sprint 4 : `LL-4001` → `LL-4015`
- Sprint 5 : `LL-5001` → `LL-5012`
- Sprint 6 : `LL-6001` → `LL-6011`
- Sprint 7 : `LL-7001` → `LL-7009`
- Sprint 8 : `LL-8001` → `LL-8009` (GO bêta conditionnel confirmé par Alex le 26/08/2026)
- Sprint 10 : `LL-10001` → `LL-10010` — adresses/villes structurées, filtre et
  tri par ville, vue liste. Fonctionnellement complet et documenté
  (`docs/PROJECT_STATUS.md`, section Sprint 10), **non formellement clos** :
  `mvn verify` n'a pu être exécuté dans aucune des sessions ayant traité ce
  sprint (Maven Central hors des domaines réseau autorisés en sandbox) — à
  faire par Alex avant d'ouvrir le Sprint Mobile.

## Sprint actuel / prochain sprint

### Sprint 9 — Corrections post-bêta

**Statut : En cours**

| Ticket | Objectif | Statut |
|---|---|---|
| LL-9001 | Ne plus afficher les activités hors période sur les recherches publiques | ✅ (en attente mvn verify) |

**Détail :** `docs/05_Sprints/SPRINT_9.md`. Sprint ouvert au fil de l'eau :
chaque correctif identifié par Alex devient un ticket autonome, traité un
par un.

⚠️ Cette entrée reste incomplète par rapport à `docs/PROJECT_STATUS.md`, qui
documente déjà `LL-9002` à `LL-9006` ainsi que le Sprint Évol-Fix
(`LL-EF-001` à `LL-EF-008`, `docs/05_Sprints/SPRINT_EVOL_FIX.md`) menés en
parallèle — écart de documentation antérieur au traitement du Sprint 10, non
rattrapé ici (hors périmètre de `LL-10010`, qui porte sur le Sprint 10).

Les évolutions produit de Phase 3 restent volontairement non engagées. Elles
seront priorisées à partir des retours de la bêta et des besoins réellement
observés.

## Règles

- Tout ticket doit appartenir à un sprint ou être explicitement marqué comme dette technique.
- Un ticket terminé doit être vérifiable dans Git, les tests ou une validation documentée.
- Les idées non validées restent des pistes et ne deviennent pas automatiquement des tickets.
- Aucun nouveau domaine métier majeur ne doit être ajouté avant les premiers retours de bêta.
