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
 * Uniquement utilisée par {@code getNearbyActivities}/
 * {@code getActivitiesWithinBounds} (les deux endpoints qui alimentent la
 * carte) : {@code getAllActivities}/{@code getActivityById} continuent de
 * renvoyer {@link Activity} tel quel, hors périmètre de ce ticket
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
        String postalCode) {

    public static ActivityResponse from(Activity activity, String sourceName) {
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
                activity.postalCode());
    }
}
