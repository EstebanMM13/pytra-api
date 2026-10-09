package com.estebanmm13.pytra_api.auth.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a brand-new account may be created for an email (invite-only registration).
 * The allowlist comes from {@code app.registration.allowed-emails} (comma-separated). An empty list
 * means registration is open to everyone. Only account creation is gated: existing users always sign in.
 */
@Slf4j
@Component
public class RegistrationPolicy {

    private final Set<String> allowedEmails;

    public RegistrationPolicy(@Value("${app.registration.allowed-emails:}") String allowedEmailsRaw) {
        this.allowedEmails = parse(allowedEmailsRaw);
        // Never log the emails themselves, only the mode and the count.
        if (allowedEmails.isEmpty()) {
            log.info("Registration mode: open (no email allowlist configured)");
        } else {
            log.info("Registration mode: invite-only ({} allowed emails)", allowedEmails.size());
        }
    }

    public boolean isOpen() {
        return allowedEmails.isEmpty();
    }

    /** True when a new account may be created for this email. The input is normalized before matching. */
    public boolean isAllowed(String email) {
        if (isOpen()) {
            return true;
        }
        String normalized = UsernamePolicy.normalizeEmail(email);
        return normalized != null && allowedEmails.contains(normalized);
    }

    static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(UsernamePolicy::normalizeEmail)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
