package com.estebanmm13.pytra_api.auth.security;

import lombok.Getter;

/** Principal of a request authenticated with a personal access token (never demo, no JWT issue time). */
@Getter
public class PersonalAccessTokenPrincipal extends AuthenticatedUser {

    private final Long tokenId;

    public PersonalAccessTokenPrincipal(String username, Long userId, Long tokenId) {
        super(username, userId, null, false);
        this.tokenId = tokenId;
    }
}
