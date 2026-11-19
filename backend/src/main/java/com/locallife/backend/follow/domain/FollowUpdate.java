package com.locallife.backend.follow.domain;

import java.util.List;

/**
 * Mise à jour à notifier pour un suivi : changements enregistrés depuis le dernier envoi ({@code kinds}, voir
 * {@code V79__follows.sql} et {@code V91__follow_food_trucks.sql}), pour un lieu, titres des activités publiées depuis
 * ({@code newActivities}) et, pour un food truck, ses emplacements du jour pas encore annoncés ({@code presentToday} :
 * « Place du marché, 11:30–14:00 »). {@code maxChangeId} : dernier changement couvert, à mémoriser après l'envoi.
 * {@code changes} : détail des champs modifiés (V94), un par champ, de la première valeur à la dernière ; vide pour
 * les changements enregistrés avant V94 ou sans détail (planning, retour en publication).
 */
public record FollowUpdate(Long followId, FollowTargetType type, Long targetId, String title, List<String> kinds,
        List<String> newActivities, long maxChangeId, List<String> presentToday, List<FieldChange> changes) {

    /** Mise à jour sans détail des champs. */
    public FollowUpdate(Long followId, FollowTargetType type, Long targetId, String title, List<String> kinds,
            List<String> newActivities, long maxChangeId, List<String> presentToday) {
        this(followId, type, targetId, title, kinds, newActivities, maxChangeId, presentToday, List.of());
    }

    /** Mise à jour d'une activité ou d'un lieu, sans présence de food truck. */
    public FollowUpdate(Long followId, FollowTargetType type, Long targetId, String title, List<String> kinds,
            List<String> newActivities, long maxChangeId) {
        this(followId, type, targetId, title, kinds, newActivities, maxChangeId, List.of());
    }

    public boolean isEmpty() {
        return kinds.isEmpty() && newActivities.isEmpty() && presentToday.isEmpty();
    }
}
