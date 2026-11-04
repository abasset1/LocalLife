package com.locallife.backend.foodtruck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.pro.application.ProActionException;
import org.junit.jupiter.api.Test;

/** Liens Instagram et Facebook d'un food truck : normalisés en adresses https du réseau, le reste refusé. */
class ProFoodTruckSocialLinksTest {

    @Test
    void instagram_ShouldAcceptAccountNamesAndInstagramAddresses() {
        assertThat(ProFoodTruckService.instagramUrl("@tacos.loco")).isEqualTo("https://www.instagram.com/tacos.loco");
        assertThat(ProFoodTruckService.instagramUrl("tacos_loco")).isEqualTo("https://www.instagram.com/tacos_loco");
        assertThat(ProFoodTruckService.instagramUrl(" https://www.instagram.com/tacosloco/ "))
                .isEqualTo("https://www.instagram.com/tacosloco/");
        assertThat(ProFoodTruckService.instagramUrl("http://instagram.com/tacosloco?igsh=abc"))
                .isEqualTo("https://instagram.com/tacosloco?igsh=abc");
        assertThat(ProFoodTruckService.instagramUrl("   ")).isNull();
        assertThat(ProFoodTruckService.instagramUrl(null)).isNull();
    }

    @Test
    void facebook_ShouldAcceptPageNamesAndFacebookAddresses() {
        assertThat(ProFoodTruckService.facebookUrl("tacosloco")).isEqualTo("https://www.facebook.com/tacosloco");
        assertThat(ProFoodTruckService.facebookUrl("www.facebook.com/profile.php?id=123"))
                .isEqualTo("https://www.facebook.com/profile.php?id=123");
        assertThat(ProFoodTruckService.facebookUrl("https://m.facebook.com/tacosloco"))
                .isEqualTo("https://m.facebook.com/tacosloco");
        assertThat(ProFoodTruckService.facebookUrl("https://fb.me/tacosloco")).isEqualTo("https://fb.me/tacosloco");
    }

    @Test
    void links_ShouldRefuseOtherSitesSchemesAndBareDomains() {
        for (String value : new String[] {"https://evil.example/instagram.com/x", "javascript:alert(1)",
                "https://instagram.com.evil.example/x", "https://www.instagram.com/", "https://user@instagram.com/x",
                "https://www.facebook.com/x", "mon compte insta"}) {
            assertThatThrownBy(() -> ProFoodTruckService.instagramUrl(value)).as(value)
                    .isInstanceOf(ProActionException.class);
        }
        assertThatThrownBy(() -> ProFoodTruckService.facebookUrl("https://www.instagram.com/x"))
                .isInstanceOf(ProActionException.class);
        assertThatThrownBy(() -> ProFoodTruckService.facebookUrl("https://notfacebook.com/x"))
                .isInstanceOf(ProActionException.class);
    }
}
