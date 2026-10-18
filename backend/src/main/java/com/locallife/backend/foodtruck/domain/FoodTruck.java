package com.locallife.backend.foodtruck.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.ReadOnlyProperty;

/**
 * Entité de domaine FoodTruck (LL-6009). Reprend exactement le modèle
 * défini par {@code FOOD_TRUCK_CONTRACT.md} (LL-6008) : module séparé
 * d'{@code Activity} (voir la décision structurante documentée dans ce
 * contrat), pas de champs de date (un food truck n'est pas un événement
 * daté), pas de lien avec {@code Source} (aucun import externe prévu).
 *
 * {@code category} : chaîne libre (type de cuisine), même convention
 * qu'{@code Activity.category} (LL-4004 : pas de relation avec la table
 * {@code category}).
 *
 * {@code contact} : URL <b>ou</b> contact (téléphone, réseau social) en
 * texte libre, sans validation de format stricte — voir
 * {@code FOOD_TRUCK_CONTRACT.md} pour le détail de cette décision.
 *
 * {@code status} : réutilise les trois valeurs déjà établies pour
 * {@code Activity.status} (LL-6003 : {@code PENDING}/{@code PUBLISHED}/
 * {@code REJECTED}), contrainte {@code CHECK} en base dès la création de
 * la table (migration {@code V12__create_food_truck_table.sql}) — pas
 * besoin d'une migration séparée comme pour {@code Activity} (V11 est
 * arrivée après coup sur une colonne déjà existante, ici la table est
 * neuve).
 *
 * {@code latitude}/{@code longitude} (ADR-0013, ARCHI-01) : plus écrites
 * depuis V65 — la position d'un food truck vit dans ses présences
 * ({@code FoodTruckPresence}), un food truck n'est pas un lieu. Gardées en
 * lecture seule pour les lignes antérieures, nulles pour les nouvelles.
 *
 * ⚠️ Décision, voir {@code FoodTruckService#createFoodTruck} :
 * contrairement à une activité créée manuellement (statut par défaut
 * {@code PENDING}), un food truck créé via ce ticket obtient directement
 * le statut {@code PUBLISHED} — aucun endpoint de modération
 * (publier/rejeter) n'existe encore pour les food trucks, un statut par
 * défaut {@code PENDING} sans aucun moyen de le faire évoluer rendrait
 * tout food truck créé invisible sur la carte, contredisant le critère
 * d'acceptation « visibilité sur la carte » de LL-6009. Si une
 * modération des food trucks est souhaitée plus tard, un ticket dédié
 * pourra réutiliser le même mécanisme que {@code AdminActivityController}
 * (LL-6005/LL-6006) — non anticipé ici.
 */
public record FoodTruck(
        @Id Long id,
        String name,
        String description,
        Double latitude,
        Double longitude,
        String category,
        String contact,
        String status,
        @ReadOnlyProperty LocalDateTime publishedAt) {

    /**
     * Constructeur canonique, utilisé par Spring Data JDBC à la lecture.
     * {@code publishedAt} : premier passage au statut {@code PUBLISHED},
     * posé par le trigger {@code trg_food_truck_set_published_at} (V55) et
     * jamais écrit par l'application ; {@code null} pour les food trucks
     * publiés avant V55.
     */
    @PersistenceCreator
    public FoodTruck {
    }

    /** Food truck à écrire : {@code publishedAt} est géré par la base. */
    public FoodTruck(
            Long id, String name, String description, Double latitude, Double longitude, String category,
            String contact, String status) {
        this(id, name, description, latitude, longitude, category, contact, status, null);
    }
}
