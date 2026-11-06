package com.locallife.backend.launch;

import com.locallife.backend.auth.application.JwtAuthentication;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Interrupteurs de lancement ({@link Feature}), lus au démarrage depuis {@code locallife.features.*}
 * (variables d'environnement {@code FEATURE_PLACES}, {@code FEATURE_FOOD_TRUCKS}, {@code FEATURE_PREMIUM},
 * {@code FEATURE_PRO}). Une fonction fermée reste ouverte à l'équipe Neiri et aux administrateurs, qui peuvent la
 * vérifier en production avant de l'ouvrir à tous ; l'administration n'est jamais concernée.
 *
 * <p>Fermer une fonction ne supprime ni ne modifie aucune donnée : les lieux, food trucks et abonnements existants
 * restent en base et réapparaissent tels quels à l'ouverture.
 */
@Component
public class LaunchFeatures {

    private final Map<Feature, Boolean> open = new EnumMap<>(Feature.class);

    public LaunchFeatures(
            @Value("${locallife.features.places:true}") boolean places,
            @Value("${locallife.features.food-trucks:true}") boolean foodTrucks,
            @Value("${locallife.features.premium:true}") boolean premium,
            @Value("${locallife.features.pro:true}") boolean pro) {
        open.put(Feature.PLACES, places);
        open.put(Feature.FOOD_TRUCKS, foodTrucks);
        open.put(Feature.PREMIUM, premium);
        open.put(Feature.PRO, pro);
    }

    /** Fonction ouverte à tous. */
    public boolean isOpen(Feature feature) {
        return open.get(feature);
    }

    /** Fonction disponible pour l'appelant : ouverte à tous, ou appelant de l'équipe Neiri ou administrateur. */
    public boolean isAvailable(Feature feature, Authentication authentication) {
        return isOpen(feature) || isStaff(authentication);
    }

    /** Disponibilité de chaque fonction pour l'appelant. */
    public Map<Feature, Boolean> availability(Authentication authentication) {
        Map<Feature, Boolean> result = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            result.put(feature, isAvailable(feature, authentication));
        }
        return result;
    }

    /** Rôle relu en base par le filtre JWT à chaque requête : un changement de rôle prend effet tout de suite. */
    private static boolean isStaff(Authentication authentication) {
        return authentication instanceof JwtAuthentication jwt && jwt.getRole() != null && jwt.getRole().isStaff();
    }
}
