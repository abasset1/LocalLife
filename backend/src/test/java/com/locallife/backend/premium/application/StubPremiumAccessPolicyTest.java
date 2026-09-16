package com.locallife.backend.premium.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

class StubPremiumAccessPolicyTest {

    private final StubPremiumAccessPolicy policy = new StubPremiumAccessPolicy();

    @Test
    void hasPremiumAccess_ShouldReturnTrue_ForAuthenticatedCaller() {
        assertTrue(policy.hasPremiumAccess(null));
    }

    @Test
    void hasPremiumAccess_ShouldReturnTrue_ForAnonymousCaller() {
        // LL-11014 : provisoire (voir la javadoc de la classe) — accorde l'accès complet à tout le
        // monde, y compris un appelant non connecté, tant qu'aucun système Premium n'existe.
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertTrue(policy.hasPremiumAccess(anonymous));
    }
}
