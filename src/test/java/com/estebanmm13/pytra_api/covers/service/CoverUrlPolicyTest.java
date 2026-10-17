package com.estebanmm13.pytra_api.covers.service;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.metadata.client.SteamCoverImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverUrlPolicyTest {

    @Test
    void acceptsTheCoverUrlsPytraStores() {
        assertThat(CoverUrlPolicy.validate(SteamCoverImages.portraitCoverUrl(620)).getHost())
                .isEqualTo("shared.akamai.steamstatic.com");
        assertThat(CoverUrlPolicy.validate("https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg").getHost())
                .isEqualTo("cdn.cloudflare.steamstatic.com");
        assertThat(CoverUrlPolicy.validate("https://SHARED.FASTLY.STEAMSTATIC.COM:443/store_item_assets/steam/apps/1/x.jpg?t=1"))
                .isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "not a url",
            "http://shared.akamai.steamstatic.com/steam/apps/620/library_600x900.jpg",
            "ftp://shared.akamai.steamstatic.com/a.jpg",
            "//shared.akamai.steamstatic.com/a.jpg",
            "https://evil.com/a.jpg",
            "https://shared.akamai.steamstatic.com.evil.com/a.jpg",
            "https://evilsteamstatic.com/a.jpg",
            "https://steamstatic.com/a.jpg",
            "https://shared.akamai.steamstatic.com@evil.com/a.jpg",
            "https://user:pass@shared.akamai.steamstatic.com/a.jpg",
            "https://shared.akamai.steamstatic.com:8443/a.jpg",
            "https://127.0.0.1/a.jpg",
            "https://169.254.169.254/latest/meta-data",
            "https://[::1]/a.jpg",
            "https://localhost/a.jpg",
            "https://shared.akamai.steamstatic.com",
            "file:///etc/passwd",
    })
    void rejectsAnythingOutsideTheSteamCdnAllowlist(String url) {
        assertThatThrownBy(() -> CoverUrlPolicy.validate(url))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_COVER_URL);
    }

    @Test
    void rejectsOverlongUrls() {
        String url = "https://shared.akamai.steamstatic.com/" + "a".repeat(600);
        assertThatThrownBy(() -> CoverUrlPolicy.validate(url)).isInstanceOf(InvalidRequestException.class);
    }
}
