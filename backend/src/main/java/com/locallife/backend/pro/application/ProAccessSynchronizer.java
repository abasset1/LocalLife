package com.locallife.backend.pro.application;

import com.locallife.backend.pro.infrastructure.ProAccountRepository;
import com.locallife.backend.pro.infrastructure.ProContentRepository;
import com.locallife.backend.pro.infrastructure.ProContentRepository.Outcome;
import com.locallife.backend.user.domain.Role;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Conséquences d'un changement d'accès professionnel (décisions du 22/10/2026), dans la transaction qui l'a
 * provoqué :
 * <ol>
 *   <li>l'accès suit le type de compte : une offre Pro en cours (le rôle {@code PRO} n'existe plus depuis V81) ;</li>
 *   <li>sans accès, les food trucks publiés sont suspendus, les droits de gestion révoqués, les demandes en cours
 *       retirées ; l'ex-professionnel garde la consultation de ses food trucks et peut les archiver. Si l'accès
 *       revient, food trucks et droits de gestion reviennent.</li>
 * </ol>
 * Idempotent : appelé à chaque événement, il ne fait que rapprocher l'état de la règle.
 */
@Service
public class ProAccessSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProAccessSynchronizer.class);

    private final ProAccountRepository accountRepository;
    private final ProContentRepository contentRepository;
    private final ProAccessPolicy proAccessPolicy;

    public ProAccessSynchronizer(ProAccountRepository accountRepository, ProContentRepository contentRepository,
            ProAccessPolicy proAccessPolicy) {
        this.accountRepository = accountRepository;
        this.contentRepository = contentRepository;
        this.proAccessPolicy = proAccessPolicy;
    }

    @EventListener
    public void onProAccessChanged(ProAccessChanged event) {
        synchronize(event.userId());
    }

    public void synchronize(Long userId) {
        Optional<Role> role = accountRepository.findRole(userId);
        if (role.isEmpty() || role.get() == Role.ADMIN) {
            return;
        }
        Outcome outcome = proAccessPolicy.hasProAccess(userId)
                ? contentRepository.restore(userId)
                : contentRepository.withdraw(userId);
        if (!outcome.isEmpty()) {
            LOGGER.info("Utilisateur {} : accès professionnel {} ({} food truck(s), {} lieu(x) géré(s), "
                    + "{} demande(s) de gestion, {} proposition(s) de modification)", userId,
                    proAccessPolicy.hasProAccess(userId) ? "rétabli" : "terminé", outcome.foodTrucks(),
                    outcome.managedPlaces(), outcome.claims(), outcome.placeChanges());
        }
    }

    /** Suppression du compte : tous ses contenus professionnels sont retirés avant que le compte ne disparaisse. */
    public void withdrawForDeletedAccount(Long userId) {
        contentRepository.withdraw(userId);
    }
}
