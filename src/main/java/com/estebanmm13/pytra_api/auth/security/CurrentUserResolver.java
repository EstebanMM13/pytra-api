package com.estebanmm13.pytra_api.auth.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    public Long getCurrentUserId() {
        AuthenticatedUser authenticatedUser =
                (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return authenticatedUser.getUserId();
    }
}
