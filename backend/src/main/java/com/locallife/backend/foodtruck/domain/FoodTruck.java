package com.locallife.backend.foodtruck.domain;

import org.springframework.data.annotation.Id;

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
 * neuve). ⚠️ Décision, voir {@code FoodTruckService#createFoodTruck} :
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
        double latitude,
        double longitude,
        String category,
        String contact,
        String status) {
}
