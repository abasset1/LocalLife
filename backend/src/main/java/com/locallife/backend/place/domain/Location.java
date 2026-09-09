package com.locallife.backend.place.domain;

import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Location (LL-11001, Sprint 11) : un lieu, indépendant
 * de toute activité. Premier bloc du nouveau modèle événementiel — voir
 * {@code docs/05_Sprints/SPRINT_11.md}, section 3 (« `city` n'appartient
 * pas à `Activity` ») : la localisation appartient à la programmation
 * ({@code Schedule}, LL-11002) et non à {@code Activity} elle-même, un
 * même lieu pouvant être partagé par plusieurs activités (ex. plusieurs
 * commerçants au même marché) et une même activité pouvant se dérouler à
 * plusieurs endroits (ex. food truck itinérant). Ce ticket ne fait que
 * poser le modèle {@code Location} lui-même ; aucun lien vers
 * {@code Activity}/{@code Schedule} n'existe encore (créé par LL-11002).
 *
 * Porté par le module {@code place}, réservé à cet effet depuis le
 * scaffolding initial du projet (LL-0002, voir {@code ARCHITECTURE.md}),
 * plutôt qu'un nouveau module {@code location} — évite un module
 * supplémentaire pour un même concept.
 *
 * Champs repris tels quels de la section « Données minimales » du
 * ticket. Tous nullables sauf {@code id} : reflète la règle explicite du
 * ticket (« les champs effectivement persistés doivent rester cohérents
 * avec les données réellement fournies par les sources ») — une source
 * peut ne fournir qu'une partie de ces informations (ex. un lieu
 * seulement géocodé n'a pas nécessairement de {@code department}/
 * {@code region}/{@code insee}), et {@code Location} ne doit pas rejeter
 * un lieu par ailleurs valide pour l'absence d'un champ secondaire. Voir
 * {@code LocationService#create} pour le seul garde-fou imposé par le
 * ticket (« aucun lieu ne doit être créé artificiellement à partir de
 * données insuffisantes »), volontairement distinct d'une contrainte
 * {@code NOT NULL} en base.
 *
 * {@code latitude}/{@code longitude} en {@link Double} (objet, pas
 * primitif) contrairement à {@code Activity.latitude}/{@code longitude}
 * (primitifs, `LL-4002` supposait déjà des coordonnées connues) : ici un
 * lieu peut exister sans coordonnées encore résolues (ex. adresse saisie
 * en attente de géocodage), cohérent avec la nullabilité de tous les
 * autres champs.
 */
public record Location(
        @Id Long id,
        String name,
        String address,
        String postalCode,
        String city,
        String department,
        String region,
        String countryCode,
        String insee,
        Double latitude,
        Double longitude,
        String timezone,
        String website,
        String email,
        String phone) {
}
