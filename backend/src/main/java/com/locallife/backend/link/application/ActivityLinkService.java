package com.locallife.backend.link.application;

import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.link.infrastructure.ActivityLinkRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Service ActivityLink (LL-11008). {@link #create} implémente le critère
 * d'acceptation « validation adaptée au type » : {@link #TYPE_PHONE}/
 * {@link #TYPE_EMAIL} sont validés comme un numéro de téléphone/une
 * adresse email, tout autre {@code type} (site officiel, billetterie,
 * réservation, programme, réseau social — exemples du ticket) est
 * validé comme une URL, cohérent avec {@code value} qui reste un texte
 * libre en base (voir la javadoc de {@link ActivityLink}) — seule cette
 * méthode connaît la distinction, pas le modèle lui-même.
 *
 * Validations volontairement simples (pas de bibliothèque dédiée type
 * Google libphonenumber, non justifiée ici — AI_RULES.md, « ne jamais
 * ajouter une dépendance sans justification » — le ticket ne demande
 * pas de validation téléphonique internationale rigoureuse, seulement
 * une validation « adaptée au type », satisfaite par une forme
 * plausible).
 */
@Service
public class ActivityLinkService {

    public static final String TYPE_PHONE = "PHONE";
    public static final String TYPE_EMAIL = "EMAIL";

    /**
     * Autorise chiffres, espaces, +, -, ., parenthèses — assez permissif pour couvrir les
     * formats français et internationaux usuels sans viser une conformité E.164 stricte.
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+()0-9 .-]{6,20}$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ActivityLinkRepository activityLinkRepository;

    public ActivityLinkService(ActivityLinkRepository activityLinkRepository) {
        this.activityLinkRepository = activityLinkRepository;
    }

    public List<ActivityLink> findByActivityId(Long activityId) {
        return activityLinkRepository.findByActivityId(activityId);
    }

    public Optional<ActivityLink> findById(Long id) {
        return activityLinkRepository.findById(id);
    }

    /**
     * @throws IllegalArgumentException si {@code value} n'est pas valide pour {@code type}
     *     (voir la javadoc de la classe)
     */
    public ActivityLink create(ActivityLink activityLink) {
        validate(activityLink.type(), activityLink.value());
        return activityLinkRepository.save(activityLink);
    }

    private void validate(String type, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Un lien/contact doit avoir une valeur (value).");
        }
        if (TYPE_PHONE.equals(type)) {
            if (!PHONE_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Numéro de téléphone invalide : " + value);
            }
            return;
        }
        if (TYPE_EMAIL.equals(type)) {
            if (!EMAIL_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Adresse email invalide : " + value);
            }
            return;
        }
        validateUrl(value);
    }

    private void validateUrl(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("URL invalide (schéma ou hôte manquant) : " + value);
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("URL invalide : " + value, exception);
        }
    }
}
