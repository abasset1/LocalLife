package com.locallife.backend.pro.application;

/**
 * L'accès professionnel d'un compte a pu changer : offre Pro validée, résiliée, retirée ou expirée, rôle attribué
 * ou retiré. Publié dans la transaction de ce changement ; {@link ProAccessSynchronizer} en tire les conséquences.
 */
public record ProAccessChanged(Long userId) {
}
