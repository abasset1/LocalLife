package com.locallife.backend.activity.api;

import com.locallife.backend.activity.domain.Activity;
import java.time.LocalDateTime;

/**
 * Représentation d'une activité pour les recherches publiques
 * ({@code /nearby}, {@code /within-bounds} — LL-8006).
 *
 * Remplace {@code sourceId} (identifiant technique de {@link Activity},
 * sans valeur pour l'utilisateur final) par {@code sourceName}, le nom
 * lisible de la {@code Source} d'origine (ex. {@code "OpenAgenda —
 * Avignon"}, ou {@code "MANUAL"} pour une contribution) — critère
 * d'acceptation LL-8006 : « valider que les informations clés (titre,
 * date, lieu, source) sont correctement affichées ». Avant ce ticket,
 * le frontend n'avait accès qu'à {@code sourceId}, inexploitable
 * directement pour l'affichage (voir {@code SPRINT_8.md}, constat fait en
 * vérifiant l'affichage bout en bout des activités sur la carte).
 *
 * {@code address}/{@code city} ajoutés en LL-EF-008 : afficher des
 * coordonnées GPS brutes à l'utilisateur final n'étant pas viable (demande
 * d'Alex), ce sont désormais elles qui servent de « lieu » affiché — dans
 * le popup de la carte comme dans la nouvelle vue liste (regroupement par
 * {@code city}) — {@code latitude}/{@code longitude} restant disponibles
 * pour le seul positionnement du marqueur. Voir {@code Activity} pour la
 * javadoc détaillée de leur résolution (géocodage à l'écriture, jamais à
 * la lecture). Toutes deux nullables : activités existantes non
 * re-géocodées/ré-importées, ou source ne fournissant pas cette donnée —
 * le frontend doit prévoir un repli (voir {@code App.tsx}).
 *
 * {@code postalCode} ajouté en LL-10005 (contrat
 * {@code docs/02_Architecture/LOCATION_CONTRACT.md}, LL-10001) : jusqu'ici
 * présent sur {@code Activity} (LL-EF-008) mais absent de cette réponse,
 * seul endpoint exposant {@code address}/{@code city} sans lui — écart
 * documenté dans le contrat. Même nullabilité que {@code address}/
 * {@code city} : présent quand la donnée d'origine (géocodage ou source
 * importée) le fournit, {@code null} sinon, jamais déduit.
 *
 * {@code createdBy}/{@code createdByUsername} ajoutés en LL-EF-010, même
 * principe de résolution que {@code sourceId} → {@code sourceName}
 * ci-dessus : {@code createdBy} (id technique, déjà sur {@link Activity})
 * reste exposé pour que le frontend puisse comparer à l'utilisateur
 * connecté (bouton « Annuler » affiché uniquement au créateur ou à un
 * administrateur, voir {@code ActivityController#cancelActivity}) ;
 * {@code createdByUsername} est le nom affiché (« Créée par : ... »,
 * critère d'acceptation explicite du ticket). Tous deux {@code null} pour
 * une activité importée (pas de créateur, périmètre du ticket) ou une
 * contribution manuelle antérieure à LL-EF-010 (créateur non identifiable
 * a posteriori) — {@code createdByUsername} peut aussi être {@code null}
 * alors que {@code createdBy} ne l'est pas si le compte a depuis été
 * supprimé ({@code ON DELETE SET NULL}, voir
 * {@code V26__add_created_by_to_activity.sql} : dans ce cas précis
 * {@code createdBy} lui-même redevient {@code null} en base, donc ce cas
 * ne devrait pas se présenter en pratique — {@code createdByUsername}
 * reste néanmoins nullable indépendamment, par défense en profondeur).
 *
 * {@code likedByCurrentUser} ajouté en LS-002 : indique si l'appelant
 * (déduit du JWT quand présent — voir {@code ActivityController}, ces
 * deux endpoints restant accessibles sans authentification, LL-4001)
 * like actuellement cette activité, pour que le frontend puisse afficher
 * l'état initial du bouton « J'aime » sans requête supplémentaire
 * (critère d'acceptation explicite : « l'état du bouton indique si
 * l'utilisateur a déjà liké l'événement »). Toujours {@code false} pour
 * un appelant non authentifié — cohérent avec l'impossibilité pour un
 * visiteur non connecté d'enregistrer un like (autre critère explicite du
 * ticket).
 *
 * Uniquement utilisée par {@code getNearbyActivities}/
 * {@code getActivitiesWithinBounds} (les deux endpoints qui alimentent la
 * carte) et par {@code LikeController#getLikedActivities} (LS-002) :
 * {@code getAllActivities}/{@code getActivityById} continuent de
 * renvoyer {@link Activity} tel quel, hors périmètre de ces tickets
 * (aucun consommateur actuel n'a besoin de {@code sourceName} sur ces
 * deux endpoints).
 */
public record ActivityResponse(
        Long id,
        String title,
        String description,
        String category,
        double latitude,
        double longitude,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status,
        String sourceName,
        String url,
        String address,
        String city,
        String postalCode,
        Long createdBy,
        String createdByUsername,
        boolean likedByCurrentUser) {

    public static ActivityResponse from(Activity activity, String sourceName) {
        return from(activity, sourceName, null, false);
    }

    public static ActivityResponse from(Activity activity, String sourceName, String createdByUsername) {
        return from(activity, sourceName, createdByUsername, false);
    }

    public static ActivityResponse from(
            Activity activity, String sourceName, String createdByUsername, boolean likedByCurrentUser) {
        return new ActivityResponse(
                activity.id(),
                activity.title(),
                activity.description(),
                activity.category(),
                activity.latitude(),
                activity.longitude(),
                activity.startDate(),
                activity.endDate(),
                activity.status(),
                sourceName,
                activity.url(),
                activity.address(),
                activity.city(),
                activity.postalCode(),
                activity.createdBy(),
                createdByUsername,
                likedByCurrentUser);
    }
}
