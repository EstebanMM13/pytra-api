package com.estebanmm13.pytra_api.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Public read-only demo: {@code app.demo.user-id} ({@code DEMO_USER_ID}) names the account that
 * anonymous visitors may browse. Blank = demo disabled ({@code POST /api/v1/auth/demo} answers 404
 * and any outstanding demo token stops authenticating).
 * <p>
 * While enabled, that account is demo-only: password login, Google login/exchange-code, resend-verification,
 * forgot-password and reset-password all treat it like an unknown account (see {@link #isDemoUser}).
 */
@Component
public class DemoMode {

    private final Long demoUserId;

    public DemoMode(@Value("${app.demo.user-id:}") String demoUserId) {
        this.demoUserId = demoUserId == null || demoUserId.isBlank() ? null : Long.valueOf(demoUserId.trim());
    }

    public boolean isEnabled() {
        return demoUserId != null;
    }

    /** The demo account id, or null when the demo is disabled. */
    public Long getDemoUserId() {
        return demoUserId;
    }

    public boolean isDemoUser(Long userId) {
        return demoUserId != null && Objects.equals(demoUserId, userId);
    }
}
