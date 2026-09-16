# ADR-0008 — Préparation de l'architecture Premium (LL-11014)

## Contexte

LL-11013 a introduit deux DTO pour `GET /api/v1/activities/{id}` :
`ActivitySummaryResponse` (résumé) et `ActivityDetailResponse` (fiche
détaillée) — mais l'endpoint renvoyait alors systématiquement la fiche
détaillée à tout le monde. LL-11014 (`SPRINT_11.md`, section 17) demande
de préparer l'architecture pour qu'une future offre Premium puisse
réserver la fiche détaillée aux utilisateurs qui y ont droit, avec une
règle fondamentale explicite : **le modèle de données ne doit pas
connaître le forfait** (aucun champ `premiumDescription`/`premiumImage`/
`PremiumActivity`, etc.). Paiement, abonnement, facturation et gestion
commerciale Premium sont explicitement hors périmètre.

## Décision

### Un point d'extension unique, hors du domaine : `PremiumAccessPolicy`

Une interface `PremiumAccessPolicy` (nouveau module
`com.locallife.backend.premium`, délibérément séparé du module
`activity`) porte la seule question posée par ce ticket : *cet appelant
a-t-il accès à la fiche détaillée ?* — sans jamais préciser comment il
y a droit (paiement, abonnement...), hors périmètre.

```java
public interface PremiumAccessPolicy {
    boolean hasPremiumAccess(Authentication authentication);
}
```

`ActivityController#getActivityById` est le seul point d'appel : il
retourne `ActivityDetailResponse` si `hasPremiumAccess` répond `true`,
sinon `ActivitySummaryResponse` — exactement le schéma du ticket :

```text
Utilisateur gratuit  → Résumé
Utilisateur Premium  → Fiche détaillée
```

Aucune autre couche (`ActivityService`, `Activity`, ou tout autre
domaine) n'a connaissance de cette notion : la bascule se fait
entièrement à la frontière API, dans le contrôleur, en réutilisant les
deux DTO déjà construits par LL-11013.

### Implémentation actuelle : accès complet pour tout le monde

`StubPremiumAccessPolicy` (seule implémentation à ce jour, `@Service`)
retourne `true` inconditionnellement. Décision volontaire : aucun
système Premium n'existe encore (hors périmètre du ticket), donc aucun
utilisateur ne peut aujourd'hui « devenir » Premium — gater la fiche
détaillée par défaut aurait rendu LL-11013 inutilisable en pratique pour
tout le monde, une régression que rien ne justifie tant qu'aucune voie
d'accès Premium n'existe. `GET /api/v1/activities/{id}` continue donc de
renvoyer la fiche détaillée à tout appelant, exactement comme avant ce
ticket — seul le *mécanisme* de décision change (passe désormais par
`PremiumAccessPolicy`, prêt à être inversé/affiné le jour où un vrai
système Premium existera, sans toucher au domaine ni à
`ActivityController`).

## Pourquoi ne pas plutôt ajouter un champ `premium` sur `User`

Envisagé puis écarté : bien que `User` ne soit pas `Activity` (la règle
du ticket porte explicitement sur le modèle des activités), un champ
`premium` persisté sur `User` aurait quand même introduit une notion
d'abonnement dans un module de domaine, en anticipant une mécanique
(comment ce champ serait-il positionné ? Par qui ?) explicitement hors
périmètre. Garder `PremiumAccessPolicy` comme unique porte d'entrée,
sans aucune donnée persistée nouvelle, respecte plus strictement l'esprit
du ticket : rien à modifier dans le domaine le jour où un vrai système
Premium sera conçu, seule `StubPremiumAccessPolicy` sera remplacée par
une implémentation qui consultera ce futur système.

## Conséquences

- Critères d'acceptation du ticket couverts : aucune donnée Premium dans
  le domaine (aucune entité modifiée par ce ticket) ; fiche détaillée
  disponible techniquement (déjà vrai depuis LL-11013, désormais
  conditionnée) ; séparation résumé/détail documentée (ADR-0007 puis
  celui-ci) ; architecture prête pour une future autorisation Premium
  (`PremiumAccessPolicy`, un seul point à remplacer).
- `ActivityController#getActivityById` gagne un paramètre `Authentication`
  et son type de retour redevient `ResponseEntity<Object>` (comme
  `getAllActivities`, pour les mêmes raisons : le corps peut être l'un
  ou l'autre des deux DTO selon le cas) — tests mis à jour en
  conséquence (`ActivityControllerTest`, deux nouveaux cas couvrant
  accès accordé/refusé).
- ⚠️ Cette session n'a toujours pas d'accès réseau à Maven Central ni à
  une base PostgreSQL réelle : vérifié par relecture manuelle uniquement.
  **`mvn verify` reste à exécuter par Alex avant tout merge.**
